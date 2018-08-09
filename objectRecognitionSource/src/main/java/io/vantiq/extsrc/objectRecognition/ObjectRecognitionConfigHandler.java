package io.vantiq.extsrc.objectRecognition;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;

public class ObjectRecognitionConfigHandler extends Handler<ExtensionServiceMessage>{
    // TODO make ObjectRecognitionCore instanced so each handler can refer to 'core' instead of 'ObjectRecognitionCore'
    
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    String sourceName;
    boolean configComplete = false;
    
    public ObjectRecognitionConfigHandler(String sourceName) {
        this.sourceName = sourceName;
    }
    
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String,Object> config = (Map) message.getObject();
        Map<String,Object> dataSource;
        Map<String,Object> neuralNetConfig; // TODO rename
        
        // Obtain the Maps for each object
        if ( !(config.get("config") instanceof Map && ((Map)config.get("config")).get("extSrcConfig") instanceof Map) ) {
            log.error("No configuration received for source ' " + sourceName + "'. Exiting...");
            ObjectRecognitionCore.exit();
        }
        config = (Map) ((Map) config.get("config")).get("extSrcConfig");
        if ( !(config.get("dataSource") instanceof Map)) {
            log.error("No data source specified for source ' " + sourceName + "'. Exiting...");
            ObjectRecognitionCore.exit();
        }
        dataSource = (Map) config.get("dataSource");
        if ( !(config.get("neuralNet") instanceof Map)) {
            log.error("No neural net specified for source ' " + sourceName + "'. Exiting...");
            ObjectRecognitionCore.exit();
        }
        neuralNetConfig = (Map) config.get("neuralNet");
        
        // Identify and setup the neural net
//        String neuralNetClassName;
//        if (neuralNet.get("className") instanceof String) {
//            neuralNetClassName = (String) neuralNet.get("className");
//        } else {
//            log.error("No class specified for the neural net of source ' " + sourceName + "'. Exiting...");
//            ObjectRecognitionCore.exit();
//        }
//        NeuralNetInterface tfInterface = getNeuralNet(neuralNetClassName);
        NeuralNetInterface neuralNet = new DarkflowProcessor(); // TODO replace with generic version
        ObjectRecognitionCore.neuralNet = neuralNet;
        neuralNet.setupImageProcessing(neuralNetConfig, ObjectRecognitionCore.modelDirectory);
        
        
        // Figure out where to receive the data from
        if (dataSource.get("fileLocation") instanceof String) {
            ObjectRecognitionCore.imageLocation = (String) dataSource.get("fileLocation");
        } else if (dataSource.get("camera") instanceof Integer && (int) dataSource.get("camera") >= 0) {
            int cameraNumber = (int) dataSource.get("camera");
            ObjectRecognitionCore.cameraNumber =  cameraNumber;
            
            nu.pattern.OpenCV.loadShared();
            ObjectRecognitionCore.vidCapture = new VideoCapture(cameraNumber); // Can add API preferences, found in Videoio
        } else {
            log.error("No valid polling target");
            log.error("Exiting...");
            ObjectRecognitionCore.exit();
        }
        
        if (dataSource.get("polling") instanceof Integer) {
            int polling = (int) dataSource.get("polling");
            if (polling > 0) {
                int pollRate = polling;
                ObjectRecognitionCore.pollRate = pollRate;
                TimerTask task = new TimerTask() {
                    boolean isRunning = false;
                    @Override
                    public void run() {
                        if (!isRunning) {
                            isRunning = true;
                            Mat image = ObjectRecognitionCore.getImage();
                            ObjectRecognitionCore.sendDataFromImage(image);
                            isRunning = false;
                        }
                    }
                };
                ObjectRecognitionCore.pollTimer = new Timer("dataCapture");
                ObjectRecognitionCore.pollTimer.scheduleAtFixedRate(task, 0, pollRate);
            } else if (polling == 0) {
                ObjectRecognitionCore.constantPolling = true;
            } else {
                // TODO snapshot on publish/query choice TBD
            }
        } else {
            log.error("No valid polling rate");
            log.error("Exiting...");
            ObjectRecognitionCore.exit();
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
            ObjectRecognitionCore.exit();
        }
        
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + className + "'", e);
            ObjectRecognitionCore.exit();
        }

        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + className + "'", e);
            ObjectRecognitionCore.exit();
        }
        
        if ( !(object instanceof NeuralNetInterface) )
        {
            log.error("Class '" + className + "' is not an implementation of NeuralNetInterface");
            ObjectRecognitionCore.exit();
        }
        
        return (NeuralNetInterface) object;
    }
}
