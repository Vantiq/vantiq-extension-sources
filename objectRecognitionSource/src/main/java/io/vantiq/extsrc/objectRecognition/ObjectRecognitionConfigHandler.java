package io.vantiq.extsrc.objectRecognition;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;
import io.vantiq.extsrc.objectRecognition.imageRetriever.CameraRetriever;
import io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.DarkflowProcessor;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor;

public class ObjectRecognitionConfigHandler extends Handler<ExtensionServiceMessage>{
    
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
    
    final String DEFAULT_IMAGE_RETRIEVER = "io.vantiq.extsrc.objectRecognition.imageRetriever.DefaultRetriever";
    final String DEFAULT_NEURAL_NET = "io.vantiq.extsrc.objectRecognition.neuralNet.DefaultProcessor";
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the neural network and data stream.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String,Object> config = (Map) message.getObject();
        Map<String,Object> dataSource;
        Map<String,Object> neuralNetConfig;
        
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
        String neuralNetType = DEFAULT_NEURAL_NET;
        if (neuralNetConfig.get("type") instanceof String) {
            neuralNetType = (String) neuralNetConfig.get("type");
            if (neuralNetType.equals("yolo")) {
                neuralNetType = YoloProcessor.class.getCanonicalName();
            } else if (neuralNetType.equals("darkflow")) {
                neuralNetType = DarkflowProcessor.class.getCanonicalName();
            } else if (neuralNetType.equals("default")) {
                neuralNetType = DEFAULT_NEURAL_NET;
            }
        } else {
            log.debug("No neural net type specified. Trying for default of '{}'", DEFAULT_NEURAL_NET);
        }
        NeuralNetInterface neuralNet = getNeuralNet(neuralNetType);
        
        try {
            neuralNet.setupImageProcessing(neuralNetConfig, source.modelDirectory);
            source.neuralNet = neuralNet;
        } catch (Exception e) {
            log.error("Exception occurred while setting up neural net.", e);
            source.close();
        }
        
        
        // Figure out where to receive the data from
        // Initialize to default in case no type was given
        String retrieverType = DEFAULT_IMAGE_RETRIEVER; 
        if (dataSource.get("type") instanceof String) {
            retrieverType = (String) dataSource.get("type");
            // Translate simple types into FQCN
            if (retrieverType.equals("file")) {
                retrieverType = FileRetriever.class.getCanonicalName();
            } else if (retrieverType.equals("camera")) {
                retrieverType = CameraRetriever.class.getCanonicalName();
            } else if (retrieverType.equals("default")) {
                retrieverType = DEFAULT_IMAGE_RETRIEVER;
            }
        } else {
            log.debug("No image retriever type specified. Trying for default of '{}'", DEFAULT_IMAGE_RETRIEVER);
        }
        ImageRetrieverInterface ir = getImageRetriever(retrieverType);
        try {
            ir.setupDataRetrieval(dataSource, source);
            source.imageRetriever = ir;
        } catch (Exception e) {
            log.error("Exception occurred while setting up image retriever.", e);
            source.close();
        }
        
        if (dataSource.get("pollRate") instanceof Integer) {
            int polling = (int) dataSource.get("pollRate");
            if (polling > 0) {
                int pollRate = polling;
                source.pollRate = pollRate;
                TimerTask task = new TimerTask() {
                    boolean isRunning = false;
                    @Override
                    public void run() {
                        if (!isRunning) {
                            isRunning = true;
                            try {
                                byte[] image = source.imageRetriever.getImage();
                                source.sendDataFromImage(image);
                            } catch (ImageAcquisitionException e) {
                                log.warn("Could not obtain requested image.", e);
                            } catch (FatalImageException e) {
                                log.error("Image acquisition failed unrecoverably", e);
                                source.close();
                            }
                            isRunning = false;
                        }
                    }
                };
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.schedule(task, 0, pollRate);
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
    
    ImageRetrieverInterface getImageRetriever(String className) {
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
        
        if ( !(object instanceof ImageRetrieverInterface) )
        {
            log.error("Class '" + className + "' is not an implementation of ImageRetriever");
            source.close();
        }
        
        return (ImageRetrieverInterface) object;
    }
}
