package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;

public class ObjectRecognitionConfigHandler extends Handler<ExtensionServiceMessage>{
    // TODO make ObjectRecognitionCore instanced so each handler can refer to 'core' instead of 'ObjectRecognitionCore'
    
    
    Logger log;
    String sourceName;
    ObjectRecognitionCore source;
    boolean configComplete = false;
    
    /**
     * Initializes the Handler for a source 
     * @param sourceName    The source that this handler is attached to
     */
    public ObjectRecognitionConfigHandler(ObjectRecognitionCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }
    
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the neural network and data stream.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String,Object> config = (Map) message.getObject();
        Map<String,Object> dataSource;
        Map<String,Object> neuralNetConfig; // TODO rename
        
        // Obtain the Maps for each object
        if ( !(config.get("config") instanceof Map && ((Map)config.get("config")).get("extSrcConfig") instanceof Map) ) {
            log.error("No configuration received for source ' " + sourceName + "'. Exiting...");
            source.close();
        }
        config = (Map) ((Map) config.get("config")).get("extSrcConfig");
        if ( !(config.get("dataSource") instanceof Map)) {
            log.error("No data source specified for source ' " + sourceName + "'. Exiting...");
            source.close();
        }
        dataSource = (Map) config.get("dataSource");
        if ( !(config.get("neuralNet") instanceof Map)) {
            log.error("No neural net specified for source ' " + sourceName + "'. Exiting...");
            source.close();
        }
        neuralNetConfig = (Map) config.get("neuralNet");
        
        // Identify and setup the neural net
//        String neuralNetClassName;
//        if (neuralNet.get("className") instanceof String) {
//            neuralNetClassName = (String) neuralNet.get("className");
//        } else {
//            log.error("No class specified for the neural net of source ' " + sourceName + "'. Exiting...");
//            source.close();
//        }
//        NeuralNetInterface tfInterface = getNeuralNet(neuralNetClassName);
        NeuralNetInterface neuralNet = new YoloProcessor(); // TODO replace with generic version
        source.neuralNet = neuralNet;
        try {
            neuralNet.setupImageProcessing(neuralNetConfig, source.modelDirectory);
        } catch (Exception e) {
            log.error("Exception occurred while setting up neural net for source '" + sourceName + "'", e);
            source.close();
        }
        
        
        // Figure out where to receive the data from
        nu.pattern.OpenCV.loadShared();
        if (dataSource.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSource.get("fileLocation");
            File imageFile = new File(imageLocation);
            if (imageFile.exists() && !imageFile.isDirectory() && imageFile.canRead()) {
                source.imageFile = imageFile;
            } else {
                log.error("Could not read file at '" + imageFile.getAbsolutePath() + "'");
                log.error("Exiting...");
                source.close();
            }
        } else if (dataSource.get("camera") instanceof Integer && (int) dataSource.get("camera") >= 0) {
            int cameraNumber = (int) dataSource.get("camera");
            source.cameraNumber =  cameraNumber;
            
            source.frameCapture = new FrameCapture(cameraNumber); // Can add API preferences, found in Videoio
        } else {
            log.error("No valid polling target");
            log.error("Exiting...");
            source.close();
        }
        
        if (dataSource.get("polling") instanceof Integer) {
            int polling = (int) dataSource.get("polling");
            if (polling > 0) {
                int pollRate = polling;
                source.pollRate = pollRate;
                TimerTask task = new TimerTask() {
                    boolean isRunning = false;
                    @Override
                    public void run() {
                        if (!isRunning) {
                            isRunning = true;
                            byte[] image = source.getImage();
                            source.sendDataFromImage(image);
                            isRunning = false;
                        }
                    }
                };
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.scheduleAtFixedRate(task, 0, pollRate);
            } else if (polling == 0) {
                new Thread(() -> source.startContinuousRetrievals()).start();
            } else {
                // TODO snapshot on publish/query choice TBD
            }
        } else {
            log.error("No valid polling rate");
            log.error("Exiting...");
            source.close();
        }
        
        configComplete = true;
    }
    
    public boolean isComplete() {
        return configComplete;
    }
    
    NeuralNetInterface getNeuralNet(String className) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        Object object = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("Could not find requested class '" + className + "'", e);
            source.close();
        }
        
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + className + "'", e);
            source.close();
        }

        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + className + "'", e);
            source.close();
        }
        
        if ( !(object instanceof NeuralNetInterface) )
        {
            log.error("Class '" + className + "' is not an implementation of NeuralNetInterface");
            source.close();
        }
        
        return (NeuralNetInterface) object;
    }
}
