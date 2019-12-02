
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
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
@SuppressWarnings({"WeakerAccess"})
public class ObjectRecognitionConfigHandler extends Handler<ExtensionServiceMessage> {
    
    Logger                  log;
    String                  sourceName;
    ObjectRecognitionCore   source;
    boolean                 configComplete = false;
    
    // Constants for Source Configuration
    private static final String CONFIG = "config";
    private static final String OBJ_REC_CONFIG = "objRecConfig";
    private static final String GENERAL = "general";
    private static final String DATA_SOURCE = "dataSource";
    private static final String FILE = "file";
    private static final String CAMERA = "camera";
    private static final String NETWORK = "network";
    private static final String FTP = "ftp";
    private static final String NEURAL_NET = "neuralNet";
    private static final String YOLO = "yolo";
    private static final String NONE = "none";
    private static final String DEFAULT = "default";
    private static final String TEST = "test";
    private static final String POLL_TIME = "pollTime";
    private static final String POLL_RATE = "pollRate";
    private static final String ALLOW_QUERIES = "allowQueries";
    private static final String MAX_RUNNING_THREADS = "maxRunningThreads";
    private static final String MAX_QUEUED_TASKS = "maxQueuedTasks";
    private static final String SUPPRESS_EMPTY_NEURAL_NET_RESULTS = "suppressEmptyNeuralNetResults";

    public static final String POST_PROCESSOR = "postProcessor";
    public static final String LOCATION_MAPPER = "locationMapper";
    public static Integer REQUIRED_MAPPING_COORDINATES = 4;
    public static final String IMAGE_COORDINATES = "imageCoordinates";
    public static final String MAPPED_COORDINATES = "mappedCoordinates";
    public static final String COORDINATE_X = "x";
    public static final String COORDINATE_Y = "y";
    public static final String COORDINATE_LATITUDE = "lat";  // Alternative for y value in GPS cases
    public static final String COORDINATE_LONGITUDE = "lon"; // Alternative for x value in GPS cases
    public static final String RESULTS_AS_GEOJSON = "resultsAsGeoJSON"; // (boolean) return results as geojson
                                                                        // Implies that mapping is for GPS


    // Constants for Query Parameters
    private static final String OPERATION = "operation";
    private static final String PROCESS_NEXT_FRAME = "processnextframe";
    private static final String UPLOAD = "upload";
    private static final String DELETE = "delete";
    
    Map<String, ?> lastDataSource = null;
    Map<String, ?> lastNeuralNet = null;
    Map<String, ?> lastPostProcessor = null;
    
    Handler<ExtensionServiceMessage> queryHandler;
    
    private static final int MAX_RUNNING_THREADS_DEFAULT = 10;
    private static final int MAX_QUEUED_TASKS_DEFAULT = 20;
    
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
                
                Map<String, ?> request = (Map<String, ?>) message.getObject();
                String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                
                // Get value of operation if it was set, otherwise set to default
                String operation;
                if (request.get(OPERATION) instanceof String) {
                    operation = request.get(OPERATION).toString().toLowerCase();
                } else {
                    operation = PROCESS_NEXT_FRAME;
                }
                
                // Check value of operation, proceed accordingly
                if (operation.equals(UPLOAD)) {
                    source.uploadLocalImages(request, replyAddress);
                } else if (operation.equals(DELETE)) {
                    source.deleteLocalImages(request, replyAddress);
                } else if (operation.equals(PROCESS_NEXT_FRAME)) {
                    // Read, process, and send the image
                    ImageRetrieverResults data = source.retrieveImage(message);
                    if (data != null) {
                        source.sendDataFromImage(data, message);
                    }
                } else {
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.invalidImageRequest", 
                            "Request specified an invalid 'operation'. The 'operation' value can be set to 'upload', 'delete', "
                            + "or 'proccessNextFrame'.", null);
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
    final String NO_PROCESSOR_FQCN          = "io.vantiq.extsrc.objectRecognition.neuralNet.NoProcessor";
    final String DEFAULT_NEURAL_NET         = "io.vantiq.extsrc.objectRecognition.neuralNet.DefaultProcessor";
    final String TEST_PROCESSOR_FQCN        = "io.vantiq.extsrc.objectRecognition.neuralNet.TestProcessor";
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the neural network and data retriever.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> config = (Map) message.getObject();
        Map<String, Object> general;
        Map<String, Object> dataSource;
        Map<String, Object> neuralNetConfig;
        Map<String, ?> postProcessorConfig;
        
        // Obtain the Maps for each object
        if ( !(config.get(CONFIG) instanceof Map && ((Map)config.get(CONFIG)).get(OBJ_REC_CONFIG) instanceof Map) ) {
            log.error("No configuration suitable for an objectRecognition. Waiting for valid config...");
            failConfig();
            return;
        }
        config = (Map) ((Map) config.get(CONFIG)).get(OBJ_REC_CONFIG);
        
        if ( !(config.get(GENERAL) instanceof Map)) {
            log.error("No general options specified. Waiting for valid config...");
            failConfig();
            return;
        }
        general = (Map) config.get(GENERAL);
        if ( !(config.get(DATA_SOURCE) instanceof Map)) {
            log.error("No data source specified. Waiting for valid config...");
            failConfig();
            return;
        }
        dataSource = (Map) config.get(DATA_SOURCE);
        if ( !(config.get(NEURAL_NET) instanceof Map)) {
            log.error("No neural net specified. Waiting for valid config...");
            failConfig();
            return;
        }
        neuralNetConfig = (Map) config.get(NEURAL_NET);

        Object ppconf = config.get(POST_PROCESSOR);
        if (ppconf != null && !(ppconf instanceof Map)) {
            log.error("Post processor configuration invalid.  Ignoring this configuration change.");
            failConfig();
            return;
        } else {
            postProcessorConfig = (Map) ppconf;
        }
        
        
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

        // Only create a new neural net if the config changed, to save time and state
        if (postProcessorConfig != null) {
            if (postProcessorConfig.equals(lastPostProcessor)) {
                log.info("post processor unchanged, keeping previous");
            } else {
                boolean success = createPostProcessor(postProcessorConfig);
                if (!success) {
                    return; // Exit if the neural net could not be created. Closing taken care of by createNeuralNet()
                }
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
    boolean createNeuralNet(Map<String, ?> neuralNetConfig) {
        // Null the last config so if it fails it will know the last failed
        lastNeuralNet = null;
        
        // Identify the type of neural net
        String neuralNetType = DEFAULT_NEURAL_NET;
        if (neuralNetConfig.get(NeuralNetInterface.TYPE_ENTRY) instanceof String) {
            neuralNetType = (String) neuralNetConfig.get(NeuralNetInterface.TYPE_ENTRY);
            if (neuralNetType.equals(YOLO)) {
                neuralNetType = YOLO_PROCESSOR_FQCN;
            } else if (neuralNetType.equals(NONE)) {
                neuralNetType = NO_PROCESSOR_FQCN;
            } else if (neuralNetType.equals(TEST)) {
                neuralNetType = TEST_PROCESSOR_FQCN;
            } else if (neuralNetType.equals(DEFAULT)) {
                neuralNetType = DEFAULT_NEURAL_NET;
            }
        } else {
            log.debug("No neural net type specified. Trying for default of '{}'", DEFAULT_NEURAL_NET);
        }
        
        // Setting the outputDir value for the core, (null if it does not exist)
        source.outputDir = (String) neuralNetConfig.get(NeuralNetInterface.OUTPUT_DIRECTORY_ENTRY);
        
        // Create the neural net
        NeuralNetInterface neuralNet = getNeuralNet(neuralNetType);
        if (neuralNet == null) {
            return false; // Error message and exiting taken care of by getNeuralNet()
        }
        
        // Setup the neural net
        try {
            neuralNet.setupImageProcessing(neuralNetConfig, source.sourceName, source.modelDirectory, source.authToken, source.targetVantiqServer);
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
        if (dataSourceConfig.get(ImageRetrieverInterface.TYPE_ENTRY) instanceof String) {
            retrieverType = (String) dataSourceConfig.get(ImageRetrieverInterface.TYPE_ENTRY);
            // Translate simple types into FQCN
            if (retrieverType.equals(FILE)) {
                retrieverType = FILE_RETRIEVER_FQCN;
            } else if (retrieverType.equals(CAMERA)) {
                retrieverType = CAMERA_RETRIEVER_FQCN;
            } else if (retrieverType.equals(NETWORK)) {
                retrieverType = NETWORK_RETRIEVER_FQCN;
            } else if (retrieverType.equals(FTP)) {
                retrieverType = FTP_RETRIEVER_FQCN;
            } else if (retrieverType.equals(DEFAULT)) {
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
     * Create post processor for images processed by neural net
     *
     * At present, the only post processor we support is the location mapper.  But we'll leave
     * the door open for further things in the future.
     *
     * @param ppConf    Map<String,?> holding post processor portion of the configuration
     * @return boolean indicating success or failure
     */
    private boolean createPostProcessor(Map<String, ?> ppConf) {
        try {
            lastPostProcessor = null;
            boolean convertToGeoJSON = false;

            Object unknown = ppConf.get(LOCATION_MAPPER);
            if (unknown == null) {
                // nothing to do here, but no failure...
                return true;
            } else if (!(unknown instanceof Map)){
                log.error("{} should be a Map but encountered {}", LOCATION_MAPPER, unknown.getClass().getName());
                failConfig();
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, ?> locMapCfg = (Map) unknown;
            List<Map> imageCoords = null;
            unknown = locMapCfg.get(IMAGE_COORDINATES);
            if (unknown instanceof List) {
                imageCoords = (List) unknown;
            }
            List<Map> mappedCoords = null;
            unknown = locMapCfg.get(MAPPED_COORDINATES);
            if (unknown instanceof List) {
                mappedCoords = (List) unknown;
            }
            if (imageCoords == null && mappedCoords == null) {
                // Then we're done.
                return true;
            } else if (imageCoords == null || mappedCoords == null) {
                log.error("Image and Mapped coordinates are both required. " +
                                "{} configuration is invalid and, thus, ignored.", LOCATION_MAPPER);
                failConfig();
                return false;
            } else if (imageCoords.size() != REQUIRED_MAPPING_COORDINATES || mappedCoords.size() != imageCoords.size()) {
                log.error("Image and Mapped coordinates must come as {} pairs.  Found {} for image, {} for mapped. " +
                                "{} configuration is invalid and, thus, ignored.",
                        REQUIRED_MAPPING_COORDINATES, imageCoords.size(), mappedCoords.size(), LOCATION_MAPPER);
                failConfig();
                return false;
            }
            unknown = locMapCfg.get(RESULTS_AS_GEOJSON);
            if (unknown != null) {  // Missing ==> false
                if (unknown instanceof Boolean) {
                    convertToGeoJSON = (Boolean) unknown;
                } else if (unknown instanceof String) {
                    convertToGeoJSON = Boolean.valueOf((String) unknown);
                } else {
                    log.error("Configuration element {}.{} must be a boolean or String representing a boolean.  Found {}.",
                            LOCATION_MAPPER, RESULTS_AS_GEOJSON, unknown.getClass().getName());
                    failConfig();
                    return false;
                }
            }

            // OK, now we have the basics handled.  Create the pairs of lists (validating as we go) and create
            // the actual post processor.

            Float[][] src = fetchCoordList(imageCoords);
            Float[][] target = fetchCoordList(mappedCoords);

            lastPostProcessor = ppConf;
            source.createLocationMapper(src, target, convertToGeoJSON);
        } catch (Exception e) {
            log.error("Exception occurred while setting up the post processor.", e);
            failConfig();
            return false;
        }
        // Got here, then things worked.  Signal that.
        return true;
    }

    /**
     * Convert our list of coordinates into a 2D array of Floats.
     *
     * Assumed that list size is checked by the caller
     * @param clist List<Map<String, Float>> representing a set of coordinates
     * @return Float[][] created from  said list.
     * @throws IllegalArgumentException if the list contains things other than numbers.
     */
    private Float[][] fetchCoordList(List clist)  throws IllegalArgumentException {
        Float[][] retVal = new Float[REQUIRED_MAPPING_COORDINATES][2];
        for (int i = 0; i < REQUIRED_MAPPING_COORDINATES; i++) {
            Object unknown = clist.get(i);
            Float xValue, yValue;
            if (unknown instanceof Map) {
                Map coord = (Map) unknown;
                Object maybeNum = coord.get(COORDINATE_X);
                if (maybeNum == null) {
                    maybeNum = coord.get(COORDINATE_LONGITUDE);
                }
                if (maybeNum instanceof Number) {
                    xValue = ((Number) maybeNum).floatValue();
                } else if (maybeNum instanceof String) {
                    xValue = Float.valueOf((String) maybeNum);
                } else {
                    throw new IllegalArgumentException("No suitable X coordinate found in list.");
                }

                maybeNum = coord.get(COORDINATE_Y);
                if (maybeNum == null) {
                    maybeNum = coord.get(COORDINATE_LATITUDE);
                }
                if (maybeNum instanceof Number) {
                    yValue = ((Number) maybeNum).floatValue();
                } else if (maybeNum instanceof String) {
                    yValue = Float.valueOf((String) maybeNum);
                } else {
                    throw new IllegalArgumentException("No suitable Y coordinate found in list.");
                }
                retVal[i] = new Float[] { xValue, yValue};
            }
        }
        return retVal;
    }


    /**
     * Sets up the the communication method, one of timed notifications, continuous notifications, or Query responses
     * @param general   The general portion of the configuration document
     * @return          true if the communication method could be setup, false otherwise
     */
    private boolean prepareCommunication(Map<String, ?> general) {
        int polling = -1; // initializing to an invalid input
        boolean queryable = false;
        int maxRunningThreads = MAX_RUNNING_THREADS_DEFAULT;
        int maxQueuedTasks = MAX_QUEUED_TASKS_DEFAULT;

        // First, we'll check the suppressNullValues option
        if (general.get(SUPPRESS_EMPTY_NEURAL_NET_RESULTS) instanceof Boolean && (Boolean) general.get(SUPPRESS_EMPTY_NEURAL_NET_RESULTS)) {
            source.suppressEmptyNeuralNetResults = (Boolean) general.get(SUPPRESS_EMPTY_NEURAL_NET_RESULTS);
        }

        // Next, we'll check the parallel image processing options
        if (general.get(MAX_RUNNING_THREADS) instanceof Integer && (Integer) general.get(MAX_RUNNING_THREADS) > 0) {
            maxRunningThreads = (Integer) general.get(MAX_RUNNING_THREADS);
        }
        
        if (general.get(MAX_QUEUED_TASKS) instanceof Integer && (Integer) general.get(MAX_QUEUED_TASKS) > 0) {
            maxQueuedTasks = (Integer) general.get(MAX_QUEUED_TASKS);
        }
                
        source.pool = new ThreadPoolExecutor(maxRunningThreads, maxRunningThreads, 0l, TimeUnit.MILLISECONDS, 
                new LinkedBlockingQueue<Runnable>(maxQueuedTasks), new ThreadPoolExecutor.DiscardOldestPolicy());
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                ImageRetrieverResults image = source.retrieveImage();
                // Send data from image in a new thread
                // Note: source.pool can be null of things are closed before we run.
                // This check (mostly) avoids extraneous & worrisome NPEs in log.
                if (source.pool != null) {
                    source.pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            source.sendDataFromImage(image);
                        }
                    });
                }
            }
        };
        
        // Start polling if pollTime is non-negative
        if (general.get(POLL_TIME) instanceof Integer) {
            polling = (Integer) general.get(POLL_TIME);  
        } else if (general.get(POLL_RATE) instanceof Integer) {
            // Old, deprecated setting. Use "pollTime" instead of "pollRate".
            log.warn("Deprecated config option, please use \"pollTime\" instead of \"pollRate\".");
            polling = (Integer) general.get(POLL_RATE);
        }

        if (polling > 0) {
            // Scheduling tasks to run <pollTime> milliseconds apart from each other
            int pollRate = polling;
            source.pollTimer = new Timer("dataCapture");
            source.pollTimer.schedule(task, 0, pollRate);
        } else if (polling == 0) {
            // Scheduling tasks to run 1 millisecond apart from each other
            source.pollTimer = new Timer("dataCapture");
            source.pollTimer.schedule(task, 0, 1);
            // 1 ms will be fast enough unless image gathering, image processing, and data sending combined are
            // sub millisecond
        }
        
        // Setup queries if requested
        if (general.get(ALLOW_QUERIES) instanceof Boolean && (Boolean) general.get(ALLOW_QUERIES)) {
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
