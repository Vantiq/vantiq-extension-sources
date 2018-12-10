
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverResults;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

/**
 * Sets up the source using the configuration document, which looks as below.
 *<pre> {
 *      objRecConfig: {
 *          general: {
 *              &lt;general options&gt;
 *          },
 *          dataSource: {
 *              &lt;image retriever options&gt;
 *          },
 *          neuralNet: {
 *              &lt;neural net options&gt;
 *          }
 *      }
 * }</pre>
 * 
 * The options for general are as follows. At least one must be valid for the source to function:
 * <ul>
 *      <li>{@code pollRate}: This indicates how often an image should be captured. A positive number
 *                      represents the number of milliseconds between captures. If the specified time is less than
 *                      the amount of time it takes to process the image then images will be taken as soon as the
 *                      previous finishes. If this is set to ero, the next image will be captured as soon as the
 *                      previous is sent.
 *      <li>{@code allowQueries}: This option allows Queries to be received when set to {@code true}
 *                      
 * </ul>
 * 
 * Most options for dataSource and neuralNet are dependent on the implementation of {@link ImageRetrieverInterface} and
 * {@link NeuralNetInterface} specified through the {@code type} option. {@code type} is the fully qualified class name
 * of the implementation. It can also be unset, in which case it will attempt to find {@code DefaultRetriever} and 
 * {@code DefaultProcessor}, which will be written by you, for your specific needs. {@code type} can also be set to the
 * implementations included in the standard package: {@code file} for FileRetriever, {@code camera} for 
 * CameraRetriever, {@code ftp} for FtpRetriever, and {@code network} for NetworkStreamRetriever for the dataSource
 * config; and {@code yolo} for YoloProcessor for the neuralNet config.
 */
public class ObjectRecognitionConfigHandler extends Handler<ExtensionServiceMessage> {
    
    Logger                  log;
    String                  sourceName;
    ObjectRecognitionCore   source;
    boolean                 configComplete = false;
    
    Map<String, ?> lastDataSource = null;
    Map<String, ?> lastNeuralNet = null;
    
    Handler<ExtensionServiceMessage> queryHandler;
    
    /**
     * Initializes the Handler for a source. The source name will be used in the logger's name.
     * @param source    The source that this handler is attached to
     */
    public ObjectRecognitionConfigHandler(ObjectRecognitionCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
        queryHandler = new Handler<ExtensionServiceMessage>() {
            ExtensionWebSocketClient client = source.client;
            
            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.invalidImageRequest", 
                            "Request must be a map", null);
                }
                
                // Read, process, and send the image
                ImageRetrieverResults data = source.retrieveImage(message);
                if (data != null) {
                    source.sendDataFromImage(data, message);
                }
            }
        };
    }
    
    /*
     * The fully qualified class names of the standard image retrievers. They are placed in strings instead of using
     * getCanonicalName() so that the classes can be removed without requiring code edits
     */
    final String FILE_RETRIEVER_FQCN        = "io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever";
    final String CAMERA_RETRIEVER_FQCN      = "io.vantiq.extsrc.objectRecognition.imageRetriever.CameraRetriever";
    final String NETWORK_RETRIEVER_FQCN     = "io.vantiq.extsrc.objectRecognition.imageRetriever.NetworkStreamRetriever";
    final String FTP_RETRIEVER_FQCN         = "io.vantiq.extsrc.objectRecognition.imageRetriever.FtpRetriever";
    final String DEFAULT_IMAGE_RETRIEVER    = "io.vantiq.extsrc.objectRecognition.imageRetriever.DefaultRetriever";
    
    /*
     * The fully qualified class names of the standard neural nets. They are placed in strings instead of using
     * getCanonicalName() so that the classes can be removed without requiring code edits
     */
    final String YOLO_PROCESSOR_FQCN        = "io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor";
    final String DEFAULT_NEURAL_NET         = "io.vantiq.extsrc.objectRecognition.neuralNet.DefaultProcessor";
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the neural network and data retriever.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> config = (Map) message.getObject();
        Map<String, Object> general;
        Map<String, Object> dataSource;
        Map<String, Object> neuralNetConfig;
        
        // Obtain the Maps for each object
        if ( !(config.get("config") instanceof Map && ((Map)config.get("config")).get("objRecConfig") instanceof Map) ) {
            log.error("No configuration suitable for an objectRecognition. Waiting for valid config...");
            failConfig();
            return;
        }
        config = (Map) ((Map) config.get("config")).get("objRecConfig");
        
        if ( !(config.get("general") instanceof Map)) {
            log.error("No general options specified. Waiting for valid config...");
            failConfig();
            return;
        }
        general = (Map) config.get("general");
        if ( !(config.get("dataSource") instanceof Map)) {
            log.error("No data source specified. Waiting for valid config...");
            failConfig();
            return;
        }
        dataSource = (Map) config.get("dataSource");
        if ( !(config.get("neuralNet") instanceof Map)) {
            log.error("No neural net specified. Waiting for valid config...");
            failConfig();
            return;
        }
        neuralNetConfig = (Map) config.get("neuralNet");
        
        
        // Only create a new data source if the config changed, to save time and state
        if (lastDataSource != null && dataSource.equals(lastDataSource)) {
            log.info("dataSource unchanged, keeping previous");
        } else {
            boolean success = createImageRetriever(dataSource);
            if (!success) {
                return; // Exit if the image retriever could not be created. Closing taken care of by createImageRetriever()
            }
        }
        
        // Only create a new neural net if the config changed, to save time and state
        if (lastNeuralNet != null && neuralNetConfig.equals(lastNeuralNet)) {
            log.info("neuralNet unchanged, keeping previous");
        } else {
            boolean success = createNeuralNet(neuralNetConfig);
            if (!success) {
                return; // Exit if the neural net could not be created. Closing taken care of by createNeuralNet()
            }
        }
        
        // Start listening for queries and/or polling as requested by the config
        boolean success = prepareCommunication(general);
        if (!success) {
            return; // Exit if the settings were invalid. Closing taken care of by prepareCommunication()
        }
        
        log.info("Setup complete");
        
        configComplete = true;
    }
    
    /**
     * Attempts to create the neural net specified by the configuration document.
     * @param neuralNetConfig   The configuration for the neural net
     * @return                  true if the neural net could be created, false otherwise
     */
    boolean createNeuralNet(Map<String, ?> neuralNetConfig ) {
        // Null the last config so if it fails it will know the last failed
        lastNeuralNet = null;
        
        // Identify the type of neural net
        String neuralNetType = DEFAULT_NEURAL_NET;
        if (neuralNetConfig.get("type") instanceof String) {
            neuralNetType = (String) neuralNetConfig.get("type");
            if (neuralNetType.equals("yolo")) {
                neuralNetType = YOLO_PROCESSOR_FQCN;
            } else if (neuralNetType.equals("default")) {
                neuralNetType = DEFAULT_NEURAL_NET;
            }
        } else {
            log.debug("No neural net type specified. Trying for default of '{}'", DEFAULT_NEURAL_NET);
        }
        
        // Create the neural net
        NeuralNetInterface neuralNet = getNeuralNet(neuralNetType);
        if (neuralNet == null) {
            return false; // Error message and exiting taken care of by getNeuralNet()
        }
        
        // Setup the neural net
        try {
            neuralNet.setupImageProcessing(neuralNetConfig, source.modelDirectory, source.authToken, source.targetVantiqServer);
            source.neuralNet = neuralNet;
        } catch (Exception e) {
            log.error("Exception occurred while setting up neural net.", e);
            failConfig();
            return false;
        }
        
        // Only save the last config if the creation succeeded.
        lastNeuralNet = neuralNetConfig;
        
        log.info("Neural net created");
        log.debug("Neural net class is {}", neuralNet);
        return true;
    }
    
    /**
     * Attempts to create an image retriever based on the configuration document.
     * @param dataSourceConfig  The configuration for the image retriever
     * @return                  true if the requested image retriever could be created, false otherwise
     */
    boolean createImageRetriever(Map<String, ?> dataSourceConfig) {
        // Null the last config so if it fails it will know the last failed
        lastDataSource = null;
        
        // Figure out where to receive the data from
        // Initialize to default in case no type was given
        String retrieverType = DEFAULT_IMAGE_RETRIEVER; 
        if (dataSourceConfig.get("type") instanceof String) {
            retrieverType = (String) dataSourceConfig.get("type");
            // Translate simple types into FQCN
            if (retrieverType.equals("file")) {
                retrieverType = FILE_RETRIEVER_FQCN;
            } else if (retrieverType.equals("camera")) {
                retrieverType = CAMERA_RETRIEVER_FQCN;
            } else if (retrieverType.equals("network")) {
                retrieverType = NETWORK_RETRIEVER_FQCN;
            } else if (retrieverType.equals("ftp")) {
                retrieverType = FTP_RETRIEVER_FQCN;
            } else if (retrieverType.equals("default")) {
                retrieverType = DEFAULT_IMAGE_RETRIEVER;
            }
        } else {
            log.debug("No image retriever type specified. Trying for default of '{}'", DEFAULT_IMAGE_RETRIEVER);
        }
        
        // Create the image retriever
        ImageRetrieverInterface ir = getImageRetriever(retrieverType);
        if (ir == null) {
            return false; // Error message and exiting taken care of by getImageRetriever()
        }
        
        // Setup the image retriever
        try {
            ir.setupDataRetrieval(dataSourceConfig, source);
            source.imageRetriever = ir;
        } catch (Exception e) {
            log.error("Exception occurred while setting up image retriever.", e);
            failConfig();
            return false;
        }
        
        // Only save the last config if the creation succeeded.
        lastDataSource = dataSourceConfig;
        
        log.info("Image retriever created");
        log.debug("Image retriever class is {}", retrieverType);
        return true;
    }
    
    /**
     * Sets up the the communication method, one of timed notifications, continuous notifications, or Query responses
     * @param general   The general portion of the configuration document
     * @return          true if the communication method could be setup, false otherwise
     */
    private boolean prepareCommunication(Map<String, ?> general) {
        int polling = -1; // initializing to an invalid input
        boolean queryable = false;
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                ImageRetrieverResults image = source.retrieveImage();
                source.sendDataFromImage(image);
            }
        };
        
        // Start polling if pollRate is non-negative
        if (general.get("pollTime") instanceof Integer) {
            polling = (Integer) general.get("pollTime");
            if (polling > 0) {
                int pollRate = polling;
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.schedule(task, 0, pollRate);
            } else if (polling == 0) {
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.scheduleAtFixedRate(task, 0, 1);
                // 1 ms will be fast enough unless image gathering, image processing, and data sending combined are
                // sub millisecond
            }
        } else if (general.get("pollRate") instanceof Integer) {
            polling = (Integer) general.get("pollRate");
            if (polling > 0) {
                int pollRate = polling;
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.schedule(task, 0, pollRate);
            } else if (polling == 0) {
                source.pollTimer = new Timer("dataCapture");
                source.pollTimer.scheduleAtFixedRate(task, 0, 1);
                // 1 ms will be fast enough unless image gathering, image processing, and data sending combined are
                // sub millisecond
            }
        }
        
        // Setup queries if requested
        if (general.get("allowQueries") instanceof Boolean && (Boolean) general.get("allowQueries")) {
            queryable = true;
            source.client.setQueryHandler(queryHandler);
        }
        
        // Fail if the config doesn't setup any method to send data
        if (polling < 0 && !queryable) {
            log.error("No queries allowed and no valid pollRate (should be non-negative)");
            log.error("Waiting for valid config...");
            failConfig();
            return false;
        }
        
        return true;
    }
    
    /**
     * Closes the source {@link ObjectRecognitionCore} and marks the configuration as completed. The source will
     * be reactivated when the source reconnects, due either to a Reconnect message (likely created by an update to the
     * configuration document) or to the WebSocket connection crashing momentarily.
     */
    private void failConfig() {
        source.close();
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
        
        // Try to find the intended class, fail if it can't be found
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("Could not find requested class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        // Try to find a public no-argument constructor for the class, fail if none exists
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + className + "'", e);
            failConfig();
            return null;
        }

        // Try to create an instance of the class, fail if it can't
        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        // Fail if the created object is not a NeuralNetInterface
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
        
        // Try to find the intended class, fail if it can't be found
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("Could not find requested class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        // Try to find a public no-argument constructor for the class, fail if none exists
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + className + "'", e);
            failConfig();
            return null;
        }

        // Try to create an instance of the class, fail if it can't
        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + className + "'", e);
            failConfig();
            return null;
        }
        
        // Fail if the created object is not a NeuralNetInterface
        if ( !(object instanceof ImageRetrieverInterface) )
        {
            log.error("Class '" + className + "' is not an implementation of ImageRetriever");
            failConfig();
            return null;
        }
        
        return (ImageRetrieverInterface) object;
    }
}
