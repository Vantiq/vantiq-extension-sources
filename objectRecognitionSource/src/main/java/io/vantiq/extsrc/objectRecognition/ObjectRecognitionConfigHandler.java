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
import io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.DarkflowProcessor;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor;

/**
 * Sets up the source using the configuration document, which looks as below.
 *<pre> {
 *      extSrcConfig: {
 *          type: "objectRecognition",
 *          general: {
 *              &lt:general options&lt;
 *          },
 *          dataSource: {
 *              &lt:image retriever options&lt;
 *          },
 *          neuralNet: {
 *              &lt:neural net options&lt;
 *          }
 *      }
 * }</pre>
 * 
 * The options for general are as follows:
 * <ul>
 *      <li>{@code pollRate}: Required. This indicates how often an image should be captured. A positive number
 *                      represents the number of milliseconds between captures. If the specified time is less than
 *                      the amount of time it takes to process the image then images will be taken as soon as the
 *                      previous finishes. If this is set to 0, the next image will be captured as soon as the previous
 *                      is sent. If this is set to a negative number, then images will be captured and processed only
 *                      when a Query message is received.
 * </ul>
 * 
 * Most options for dataSource and neuralNet are dependent on the implementation of {@link ImageRetrieverInterface} and
 * {@link NeuralNetInterface} specified through the {@code type} option. {@code type} is the fully qualified class name
 * of the implementation. It can also be unset, in which case it will attempt to find {@code DefaultRetriever} and 
 * {@code DefaultProcessor}, which will be written by you, for your specific needs. {@code type} can also be set to the
 * implementations included in the standard package, {@link FileRetriever file} and {@link CameraRetriever camera} for
 * dataSource and {@link YoloProcessor yolo} and {@link DarkflowProcessor darkflow} for neuralNet.
 */
public class ObjectRecognitionConfigHandler extends Handler<ExtensionServiceMessage>{
    
    Logger                  log;
    String                  sourceName;
    ObjectRecognitionCore   source;
    boolean                 configComplete = false;
    
    /**
     * Initializes the Handler for a source 
     * @param sourceName    The source that this handler is attached to
     */
    public ObjectRecognitionConfigHandler(ObjectRecognitionCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }
    
    /*
     * The fully qualified class names of the standard image retrievers. They are placed in strings instead of using
     * getCanonicalName() so that the classes can be removed without requiring code edits
     */
    final String FILE_RETRIEVER_FQCN        = "io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever";
    final String CAMERA_RETRIEVER_FQCN      = "io.vantiq.extsrc.objectRecognition.imageRetriever.CameraRetriever";
    final String DEFAULT_IMAGE_RETRIEVER    = "io.vantiq.extsrc.objectRecognition.imageRetriever.DefaultRetriever";
    
    /*
     * The fully qualified class names of the standard neural nets. They are placed in strings instead of using
     * getCanonicalName() so that the classes can be removed without requiring code edits
     */
    final String YOLO_PROCESSOR_FQCN        = "io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor";
    final String DARKFLOW_PROCESSOR_FQCN    = "io.vantiq.extsrc.objectRecognition.neuralNet.DarkflowProcessor";
    final String DEFAULT_NEURAL_NET         = "io.vantiq.extsrc.objectRecognition.neuralNet.DefaultProcessor";
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the neural network and data stream.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String,Object> config = (Map) message.getObject();
        Map<String,Object> general;
        Map<String,Object> dataSource;
        Map<String,Object> neuralNetConfig;
        
        // Obtain the Maps for each object
        if ( !(config.get("config") instanceof Map && ((Map)config.get("config")).get("extSrcConfig") instanceof Map) ) {
            log.error("No configuration received for source ' " + sourceName + "'. Exiting...");
            failConfig();
            return;
        }
        config = (Map) ((Map) config.get("config")).get("extSrcConfig");
        
        if ( !(config.get("type") instanceof String) || !(config.get("type").equals("objectRecognition"))) {
            log.error("Source is not an object recognition source. Exiting...");
            failConfig();
            return;
        }
        
        if ( !(config.get("general") instanceof Map)) {
            log.error("No general options specified. Exiting...");
            failConfig();
            return;
        }
        general = (Map) config.get("general");
        if ( !(config.get("dataSource") instanceof Map)) {
            log.error("No data source specified. Exiting...");
            failConfig();
            return;
        }
        dataSource = (Map) config.get("dataSource");
        if ( !(config.get("neuralNet") instanceof Map)) {
            log.error("No neural net specified. Exiting...");
            failConfig();
            return;
        }
        neuralNetConfig = (Map) config.get("neuralNet");
        
        // Identify and setup the neural net
        String neuralNetType = DEFAULT_NEURAL_NET;
        if (neuralNetConfig.get("type") instanceof String) {
            neuralNetType = (String) neuralNetConfig.get("type");
            if (neuralNetType.equals("yolo")) {
                neuralNetType = YOLO_PROCESSOR_FQCN;
            } else if (neuralNetType.equals("darkflow")) {
                neuralNetType = DARKFLOW_PROCESSOR_FQCN;
            } else if (neuralNetType.equals("default")) {
                neuralNetType = DEFAULT_NEURAL_NET;
            }
        } else {
            log.debug("No neural net type specified. Trying for default of '{}'", DEFAULT_NEURAL_NET);
        }
        
        NeuralNetInterface neuralNet = getNeuralNet(neuralNetType);
        if (neuralNet == null) {
            return; // Error message and exiting taken care of by getNeuralNet()
        }
        
        try {
            neuralNet.setupImageProcessing(neuralNetConfig, source.modelDirectory);
            source.neuralNet = neuralNet;
        } catch (Exception e) {
            log.error("Exception occurred while setting up neural net.", e);
            failConfig();
            return;
        }
        
        
        // Figure out where to receive the data from
        // Initialize to default in case no type was given
        String retrieverType = DEFAULT_IMAGE_RETRIEVER; 
        if (dataSource.get("type") instanceof String) {
            retrieverType = (String) dataSource.get("type");
            // Translate simple types into FQCN
            if (retrieverType.equals("file")) {
                retrieverType = FILE_RETRIEVER_FQCN;
            } else if (retrieverType.equals("camera")) {
                retrieverType = CAMERA_RETRIEVER_FQCN;
            } else if (retrieverType.equals("default")) {
                retrieverType = DEFAULT_IMAGE_RETRIEVER;
            }
        } else {
            log.debug("No image retriever type specified. Trying for default of '{}'", DEFAULT_IMAGE_RETRIEVER);
        }
        
        ImageRetrieverInterface ir = getImageRetriever(retrieverType);
        if (ir == null) {
            return; // Error message and exiting taken care of by getImageRetriever()
        }
        
        try {
            ir.setupDataRetrieval(dataSource, source);
            source.imageRetriever = ir;
        } catch (Exception e) {
            log.error("Exception occurred while setting up image retriever.", e);
            failConfig();
            return;
        }
        
        if (general.get("pollRate") instanceof Integer) {
            int polling = (int) general.get("pollRate");
            if (polling > 0) {
                int pollRate = polling;
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        byte[] image = source.retrieveImage();
                        source.sendDataFromImage(image);
                    }
                };
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.schedule(task, 0, pollRate);
            } else if (polling == 0) {
                // Wait for the previous iteration of continuous retrievals to finish, if any
                while (source.stopPolling) Thread.yield();
                
                source.constantPolling = true;
                new Thread(() -> source.startContinuousRetrievals()).start();
            } else {
                source.client.setQueryHandler(new ObjectRecognitionQueryHandler(source, source.client));
            }
        } else {
            log.error("No valid polling rate");
            log.error("Exiting...");
            failConfig();
            return;
        }
        
        configComplete = true;
    }
    
    /**
     * Stops the host {@link ObjectRecognitionCore} and marks the configuration as completed.
     */
    private void failConfig() {
        source.stop();
        configComplete = true;
    }
    
    /**
     * Returns whether the configuration handler has completed. Necessary since the sourceConnectionFuture is completed
     * before the configuration can complete, so a program may need to wait before using configured resources.
     * @return  true when the configuration has completed (successfully or not), false otherwise
     */
    public boolean isComplete() {
        return configComplete;
    }
    
    /**
     * Obtains the neural net specified by the given class. If the class cannot be found or properly created, then an
     * error is logged and the configuration exits.
     * @param className The fully-qualified class name of the implementation to be obtained.
     * @return          An instance of the specified implementation, or null if the class could not be found.
     */
    NeuralNetInterface getNeuralNet(String className) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        Object object = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("Could not find requested class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + className + "'", e);
            failConfig();
            return null;
        }

        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        if ( !(object instanceof NeuralNetInterface) )
        {
            log.error("Class '" + className + "' is not an implementation of NeuralNetInterface");
            failConfig();
            return null;
        }
        
        return (NeuralNetInterface) object;
    }
    
    /**
     * Obtains the image retriever specified by the given class. If the class cannot be found or properly created, then
     * an error is logged and the configuration exits.
     * @param className The fully-qualified class name of the implementation to be obtained.
     * @return          An instance of the specified implementation, or null if the class could not be found.
     */
    ImageRetrieverInterface getImageRetriever(String className) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        Object object = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("Could not find requested class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + className + "'", e);
            failConfig();
            return null;
        }

        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        if ( !(object instanceof ImageRetrieverInterface) )
        {
            log.error("Class '" + className + "' is not an implementation of ImageRetriever");
            failConfig();
            return null;
        }
        
        return (ImageRetrieverInterface) object;
    }
}
