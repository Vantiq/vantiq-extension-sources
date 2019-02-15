
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.util.ImageUtil;
import io.vantiq.client.Vantiq;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverResults;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetResults;
import io.vantiq.extsrc.objectRecognition.query.DateRangeFilter;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it and call start() and it will run 
 * itself. start() will return a boolean describing whether or not it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
public class ObjectRecognitionCore {
    // vars for server configuration
    String sourceName;
    String authToken;
    String targetVantiqServer;
    String modelDirectory;
    
    
    // vars for source configuration
    Timer                   pollTimer       = null;
    ImageRetrieverInterface imageRetriever  = null;
    
    ObjectRecognitionConfigHandler objRecConfigHandler;
    
    // vars for internal use
    ExtensionWebSocketClient    client      = null;
    NeuralNetInterface          neuralNet   = null;
    SimpleDateFormat            format      = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");
    
    public String outputDir;
    public String lastQueryFilename;
    
    // final vars
    final Logger log;
    final static int    RECONNECT_INTERVAL = 5000;
    
    /**
     * Logs http messages at the debug level 
     */
    public final Handler<Response> httpHandler = new Handler<Response>() {
        @Override
        public void handleMessage(Response message) {
            log.debug(message.toString());
        }
    };
    
    /**
     * Stops sending messages to the source and tries to reconnect, closing on a failure
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.info("Reconnect message received. Reinitializing configuration");
            
            // Do partial close to preserve states of imageRetriever and neuralNet
            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }
            
            objRecConfigHandler.configComplete = false;
            
            client.setQueryHandler(defaultQueryHandler);
            
            CompletableFuture<Boolean> success = client.connectToSource();
            
            try {
                if ( !success.get(10, TimeUnit.SECONDS) ) {
                    log.error("Source reconnection failed");
                    close();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("Could not reconnect to source within 10 seconds");
                close();
            }
        }
    };
    
    /**
     * Stops sending messages to the source and tries to reconnect, closing on a failure
     */
    public final Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.info("WebSocket closed unexpectedly. Attempting to reconnect");

            // Do partial close to preserve states of imageRetriever and neuralNet
            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }
   
            objRecConfigHandler.configComplete = false;
            
            boolean sourcesSucceeded = false;
            while (!sourcesSucceeded) {
                client.setQueryHandler(defaultQueryHandler);
                
                client.initiateFullConnection(targetVantiqServer, authToken);
                sourcesSucceeded = exitIfConnectionFails(client, 10);
                
                try {
                    Thread.sleep(RECONNECT_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                }
            }
        }
    };
    
    /**
     * Sends back an error when no query handler has been set by the config
     */
    Handler<ExtensionServiceMessage> defaultQueryHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage msg) {
            log.warn("Query received with no user-set handler");
            log.debug("Full message: " + msg);
            // Prepare a response with an empty body, so that the query doesn't wait for a timeout
            Object[] body = {msg.getSourceName()};
            client.sendQueryError(ExtensionServiceMessage.extractReplyAddress(msg),
                    "io.vantiq.extsrc.objectRecognition.noQueryConfigured",
                    "Source '{0}' is not configured for Queries. Queries require objRecConfig.general.pollRate < 0",
                    body);
        }
    };
    
    
    /**
     * Creates a new ObjectRecognitionCore with the settings given.
     * @param sourceName            The name of the source to connect to.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     * @param modelDirectory        The directory in which the model files for the neural net will be stored.
     */
    public ObjectRecognitionCore(String sourceName, String authToken, String targetVantiqServer, String modelDirectory) {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + '#' + sourceName);
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
        this.modelDirectory = modelDirectory;
    }
    
    /**
     * Returns the name of the source that it is connected to.
     * @return  The name of the source that it is connected to.
     */
    public String getSourceName() {
        return sourceName;
    }
    
    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds for it to succeed or fail.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     * @return          true if the source connection succeeds, false if it fails.
     */
    public boolean start(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            objRecConfigHandler = new ObjectRecognitionConfigHandler(this);
            
            client.setConfigHandler(objRecConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            client.setQueryHandler(defaultQueryHandler);
            client.initiateFullConnection(targetVantiqServer, authToken);
            
            sourcesSucceeded = exitIfConnectionFails(client, timeout);
            try {
                Thread.sleep(RECONNECT_INTERVAL);
            } catch (InterruptedException e) {
                log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
            }
        }
        return true;
    }
    
    /**
     * Retrieves an image using the Core's image retriever. Calls {@code stop()} if a FatalImageException was received.
     * @return  The image retrieved in jpeg format, or null if a problem occurred.
     */
    public synchronized ImageRetrieverResults retrieveImage() {
        if (imageRetriever == null) { // Should only happen if close() was called immediately before retreiveImage()
            return null;
        }
        
        // Return the retriever's results, or return null on an exception
        try {
            return imageRetriever.getImage();
        } catch (ImageAcquisitionException e) {
            log.warn("Could not obtain requested image.", e);
        } catch (FatalImageException e) {
            log.error("Image retriever of type '" + imageRetriever.getClass().getCanonicalName() 
                    + "' failed unrecoverably"
                    , e);
            stop();
        } catch (RuntimeException e) {
            log.error("Image retriever had an uncaught runtime exception", e);
            log.error("Please ask the developer of the image retriever to check for the exception. Exiting...");
            stop();
        }
        return null; // This will keep the program from trying to do anything with an image when retrieval fails
    }
    
    /**
     * Retrieves an image using the Core's image retriever using the options specified in the object of the Query
     * message. Calls {@code stop()} if a FatalImageException is received.
     * @param message   The Query message.
     * @return          The image retrieved in jpeg format, or null if a problem occurred.
     */
    public synchronized ImageRetrieverResults retrieveImage(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        if (imageRetriever == null) { // Should only happen if close() was called immediately before retreiveImage()
            if (client != null) {
                client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                        "The source closed mid message", null);
            }
            return null;
        }
        
        // Return the retriever's results, or send a query error and return null on an exception
        try {
            return imageRetriever.getImage(request);
        } catch (ImageAcquisitionException e) {
            log.warn("Could not obtain requested image.", e);
            log.debug("Request was: {}", request);
            client.sendQueryError(replyAddress, ImageAcquisitionException.class.getCanonicalName(), 
                    "Failed to obtain an image for reason '{0}'. Exception was {1}. Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
        } catch (FatalImageException e) {
            log.error("Image retriever of type '" + imageRetriever.getClass().getCanonicalName() 
                    + "' failed unrecoverably"
                    , e);
            log.debug("Request was: {}", request);
            client.sendQueryError(replyAddress, FatalImageException.class.getCanonicalName() + ".acquisition", 
                    "Fatally failed to obtain an image for reason '{0}'. Exception was {1}. Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
            stop();
        } catch (RuntimeException e) {
            log.error("Image retriever had an uncaught runtime exception", e);
            log.debug("Request was: {}", request);
            log.error("Please ask the developer of the image retriever to check for the exception. Exiting...");
            client.sendQueryError(replyAddress, FatalImageException.class.getPackage().getName() 
                    + ".uncaughtAcquisitionException", 
                    "Unexpected runtime exception when obtaining an image for reason {0}. Exception was {1}. "
                    + "Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
            stop();
        }
        return null; // This will keep the program from trying to do anything with an image when retrieval fails
    }
    
    /**
     * Processes the image then sends the results to the Vantiq source. Calls {@code stop()} if a FatalImageException is
     * received.
     * @param imageResults An {@link ImageRetrieverResults} containing the image to be translated
     */
    public void sendDataFromImage(ImageRetrieverResults imageResults) {
        
        if (imageResults == null) {
            return;
        }
        byte[] image = imageResults.getImage();
        
        if (image == null || image.length == 0) {
            return;
        }
        
        // Send the results of the neural net if it doesn't error out
        try {
            synchronized (this) {
                if (neuralNet == null) { // Should only happen when close() runs just before sendDataFromImage()
                    return;
                }
                NeuralNetResults results = neuralNet.processImage(image);
                
                // Don't send any data if using NoProcessor
                if (!neuralNet.getClass().toString().contains("NoProcessor")) {
                    // Translate the results from the neural net and image into a message to send back 
                    Map message = createMapFromResults(imageResults, results);
                    client.sendNotification(message);
                }
            }
        } catch (ImageProcessingException e) {
            log.warn("Could not process image", e);
        } catch (FatalImageException e) {
            log.error("Image processor of type '" + neuralNet.getClass().getCanonicalName() + "' failed unrecoverably"
                    , e);
            log.error("Stopping");
            stop();
        } catch (RuntimeException e) {
            log.error("Neural net had an uncaught runtime exception", e);
            log.error("Please ask the developer of the neural net to check for the exception. Exiting...");
            stop();
        }
    }
    
   /**
    * Processes the image using the options specified in the Query message then sends a Query response containing the
    * results. Calls {@code stop()} if a FatalImageException is received.
    * @param imageResults   An {@link ImageRetrieverResults} containing the image to be translated
    * @param message        The Query message
    */
   public void sendDataFromImage(ImageRetrieverResults imageResults, ExtensionServiceMessage message) {
       Map<String, ?> request = (Map<String, ?>) message.getObject();
       String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
       byte[] image = imageResults.getImage();
       
       if (image == null || image.length == 0) {
           if (client != null) {
               client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                       "The source closed mid message", null);
           }
           return;
       }
       
       // Send the results of the neural net, or send a Query error if an exception occurs
       try {
           synchronized (this) {
               if (neuralNet == null) { // Should only happen when close() runs just before sendDataFromImage()
                   if (client != null) {
                       client.sendQueryError(replyAddress, this.getClass().getPackage().getName() + ".closed",
                               "The source closed mid message", null);
                   }
                   return;
               }
               NeuralNetResults results = neuralNet.processImage(image, request);
               lastQueryFilename = results.getLastFilename();
               
               // Send the normal message as the response if requested, otherwise just send the data 
               if (request.get("sendFullResponse") instanceof Boolean && (Boolean) request.get("sendFullResponse")) {
                   Map response = createMapFromResults(imageResults, results);
                   client.sendQueryResponse(200, replyAddress, response);
               } else if (results.getResults().isEmpty()) {
                   client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
               } else {
                   client.sendQueryResponse(200, replyAddress, results.getResults().toArray(new Map[0]));
               }
           }
       } catch (ImageProcessingException e) {
           log.warn("Could not process image", e);
           log.debug("Request was: " + request);
           client.sendQueryError(replyAddress, ImageProcessingException.class.getCanonicalName(), 
                   "Failed to process the image obtained for reason '{0}'. Exception was {1}. Request was {2}"
                   , new Object[] {e.getMessage(), e, request});
       } catch (FatalImageException e) {
           log.error("Image processor of type '" + neuralNet.getClass().getCanonicalName() + "' failed unrecoverably"
                   , e);
           log.debug("Request was: " + request);
           client.sendQueryError(replyAddress, FatalImageException.class.getCanonicalName() + ".processing", 
                   "Fatally failed to process the image obtained for reason '{0}'. Exception was {1}. Request was {2}"
                   , new Object[] {e.getMessage(), e, request});
           log.error("Stopping");
           stop();
       } catch (RuntimeException e) {
           log.error("Neural net had an uncaught runtime exception", e);
           log.debug("Request was: " + request);
           log.error("Please ask the developer of the neural net to check for the exception. Exiting...");
           client.sendQueryError(replyAddress, FatalImageException.class.getPackage().getName() 
                   + ".uncaughtProcessingException", 
                   "Uncaught runtime exception when processing image for reason {0}. Exception was {1}. Request was {2}"
                   , new Object[] {e.getMessage(), e, request});
           stop();
       }
   }
   
   /**
    * Uploads requested images to VANTIQ. Called when query parameter "operation" is set to "upload".
    * @param request        The parameters sent with the query.
    * @param replyAddress   The replyAddress used to send a query response.
    */
   public void uploadLocalImages(Map<String, ?> request, String replyAddress) {       
       Map<String,Object> parsedParameterResult = handleQueryParameters(request, replyAddress, "upload");
       if (parsedParameterResult == null) {
           return;
       }
       
       ImageUtil imageUtil = setupQueryImageUtil(request);
       
       if (parsedParameterResult.get("imageName") != null) {
           String imageName = (String) parsedParameterResult.get("imageName");
           uploadOne(imageName, outputDir, imageUtil);
       } else {
           FilenameFilter filter = (FilenameFilter) parsedParameterResult.get("filter");
           uploadMany(outputDir, imageUtil, filter);
       }
       
       // Send nothing back as query response
       client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
   }
   
   /**
    * A helper method called by uploadLocalImages and deleteLocalImages, used to check the query parameters
    * @param request        The parameters sent with the query.
    * @param replyAddress   The replyAddress used to send a query response.
    * @param operation      The operation calling this method, either "upload" or "delete"
    * @return
    */
   public Map<String, Object> handleQueryParameters(Map<String, ?> request, String replyAddress, String operation) {
       String imageName = null;
       List<String> imageDate = null;
       List<Date> dateRange = new ArrayList<Date>();
       boolean useFilter = false;
           
       // Checking if "imageName" option was used to specify the file(s) to upload.
       if (request.get("imageName") instanceof String) {
           imageName = (String) request.get("imageName");
           
       // Checking if "imageDate" option was used as a list of two dates to specify the files to upload.
       } else if (request.get("imageDate") instanceof List) {
           imageDate = (List<String>) request.get("imageDate");
           if (imageDate.size() != 2) {
               client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.invalidQueryRequest", 
                       "The imageDate value did not contain exactly two elements. Must be a list containing only "
                       + "[<yourStartDate>, <yourEndDate>].", null);
               return null;
           }
       } else {
           client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.invalidQueryRequest", 
                   "No imageName or imageDate was specified, or they were incorrectly specified. "
                   + "Cannot select image(s) to " + operation + ".", null);
           return null;
       }
       
       
       // Examining parameters, and creating filters if necessary
       
       if (imageName != null) {
           useFilter = false;
       } else if (imageDate != null) {
           for (String date : imageDate) {
               if (date.equals("-")) {
                   dateRange.add(null);
               } else {
                   try {
                       dateRange.add(format.parse(date));
                   } catch (ParseException e) {
                       log.error("An error occurred while parsing the imageDate");
                       client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.invalidQueryRequest", 
                               "One of the dates in the imageDate list could not be parsed. Please be sure that both "
                               + "dates are in the following format: yyyy-MM-dd--HH-mm-ss", null);
                       return null;
                   }
               }
           }
           useFilter = true;
       }
       
       // Return map with filter if we are selecting multiple files, otherwise return map with imageName
       Map<String, Object> parsedParameterResults = new LinkedHashMap<String,Object>();
       if (useFilter) {
           FilenameFilter filter = new DateRangeFilter(dateRange);
           parsedParameterResults.put("filter", filter);
       } else {
           parsedParameterResults.put("imageName", imageName);
       }
       
       return parsedParameterResults;
   }
   
   /**
    * A helper function called by uploadLocalImages, used to set up the ImageUtil class
    * @param request    The parameters sent with the query.
    * @return           The instantiated ImageUtil class, setup with properties based on the request
    */
   public ImageUtil setupQueryImageUtil(Map<String, ?> request) {   
       String imageDir = (String) request.get("imageDir");
       ImageUtil imageUtil = new ImageUtil();
       Vantiq vantiq = new io.vantiq.client.Vantiq(targetVantiqServer);
       vantiq.setAccessToken(authToken);
       imageUtil.vantiq = vantiq;
       imageUtil.outputDir = imageDir;
       imageUtil.sourceName = sourceName;
       // Checking if additional image resizing has been requested
       if (request.get("savedResolution") instanceof Map) {
           Map savedResolution = (Map) request.get("savedResolution");
           if (savedResolution.get("longEdge") instanceof Integer) {
               int longEdge = (Integer) savedResolution.get("longEdge");
               if (longEdge < 0) {
                   log.error("The config value for longEdge must be a non-negative integer. Saved image resolution will not be changed.");
               } else {
                   imageUtil.longEdge = longEdge;
                   imageUtil.queryResize = true;
               }
           }
       }
       
       return imageUtil;
   }
   
   /**
    * A helper function called by uploadLocalImages, used to upload one specific image
    * @param name       The name of the image to be uploaded
    * @param imageDir   The name of the image directory
    * @param imageUtil  The instantiated ImageUtil class containing the method to upload
    */
   public void uploadOne(String name, String imageDir, ImageUtil imageUtil) {
       if (!name.endsWith(".jpg")) {
           name = name + ".jpg";
       }
       File imgFile = new File(imageDir + File.separator + name);
       imageUtil.uploadImage(imgFile, imgFile.getName());
   }
   
   /**
    * A helper function called by uploadLocalImages, used to upload multiple images to VANTIQ
    * @param imageDir    The name of the image directory
    * @param imageUtil   The instantiated ImageUtil class containing the method to upload
    * @param filter      The filter used if a dateRange was selected
    */
   public void uploadMany(String imageDir, ImageUtil imageUtil, FilenameFilter filter) {
       File imgDirectory = new File(imageDir);
       File[] directoryListing;
       directoryListing = imgDirectory.listFiles(filter);
       if (directoryListing != null) {
           for (File fileToUpload : directoryListing) {
               imageUtil.uploadImage(fileToUpload, fileToUpload.getName());
           }
       }
   }
   
   /**
    * Deletes requested locally-saved images. Called when query parameter "operation" is set to "delete".
    * @param request        The parameters sent with the query.
    * @param replyAddress   The replyAddress used to send a query response.
    */
   public void deleteLocalImages(Map<String, ?> request, String replyAddress) {  
       Map<String,Object> parsedParameterResult = handleQueryParameters(request, replyAddress, "delete");
       if (parsedParameterResult == null) {
           return;
       }
       
       ImageUtil imageUtil = new ImageUtil();
       
       if (parsedParameterResult.get("imageName") != null) {
           String imageName = (String) parsedParameterResult.get("imageName");
           deleteOne(imageName, outputDir, imageUtil);
       } else {
           FilenameFilter filter = (FilenameFilter) parsedParameterResult.get("filter");
           deleteMany(outputDir, imageUtil, filter);
       }
       
       // Send nothing back as query response
       client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
   }
   
   /**
    * A helper function called by deleteLocalImages, used to delete one specific image
    * @param name       The name of the image to be uploaded
    * @param imageDir   The name of the image directory
    * @param imageUtil  The instantiated ImageUtil class containing the method to delete
    */
   public void deleteOne(String name, String imageDir, ImageUtil imageUtil) {
       if (!name.endsWith(".jpg")) {
           name = name + ".jpg";
       }
       File imgFile = new File(imageDir + File.separator + name);
       imageUtil.deleteImage(imgFile);
   }
   
   /**
    * A helper function called by deleteLocalImages, used when multiple files need to be deleted
    * @param imageDir    The name of the image directory
    * @param imageUtil   The instantiated ImageUtil class containing the method to delete
    * @param filter      The filter used if a dateRange was selected, otherwise null
    */
   public void deleteMany(String imageDir, ImageUtil imageUtil, FilenameFilter filter) {
       File imgDirectory = new File(imageDir);
       File[] directoryListing;
       directoryListing = imgDirectory.listFiles(filter);
       if (directoryListing != null) {
           for (File fileToDelete : directoryListing) {
               imageUtil.deleteImage(fileToDelete);
           }
       }
   }
   
   Map<String, Object> createMapFromResults(ImageRetrieverResults imageResults, NeuralNetResults neuralNetResults) {
       Map<String, Object> map = new LinkedHashMap<>();
       
       // Check if images are being saved, otherwise don't add "filename" field
       if (neuralNetResults.getLastFilename() != null) {
           map.put("filename", neuralNetResults.getLastFilename());
       }
       
       map.put("sourceName", sourceName);
       map.put("timestamp", imageResults.getTimestamp());
       map.put("results", neuralNetResults.getResults());
       
       if (imageResults.getOtherData() != null) {
           map.put("dataSource", imageResults.getOtherData());
       } else {
           map.put("dataSource", new LinkedHashMap<>());
       }
       
       if (neuralNetResults.getOtherData() != null) {
           map.put("neuralNet", neuralNetResults.getOtherData());
       } else {
           map.put("neuralNet", new LinkedHashMap<>());
       }
       
       
       return map;
   }
    
    /**
     * Closes all resources held by this program except for the {@link ExtensionWebSocketClient}. 
     */
    public void close() {
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
        synchronized (this) {
            if (imageRetriever != null) {
                imageRetriever.close();
                imageRetriever = null;
            }
            if (neuralNet != null) {
                neuralNet.close();
                neuralNet = null;
            }
        }
    }
    
    /**
     * Closes all resources held by this program and then closes the connection. 
     */
    public void stop() {
        close();
        if (client != null && client.isOpen()) {
            client.stop();
            client = null;
        }
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within
     * {@code timeout} seconds.
     *
     * @param client    The client to watch for success or failure.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping
     * @return          true if the connection succeeded, false if it failed to connect within {@code timeout} seconds.
     */
    public boolean exitIfConnectionFails(ExtensionWebSocketClient client, int timeout) {
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = client.getSourceConnectionFuture().get(timeout, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            log.error("Timeout: full connection did not succeed within {} seconds.", timeout);
        }
        catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources. Retrying...");
            if (!client.isOpen()) {
                log.error("Failed to connect to server url '" + targetVantiqServer + "'.");
            } else if (!client.isAuthed()) {
                log.error("Failed to authenticate within " + timeout + " seconds using the given authentication data.");
            } else {
                log.error("Failed to connect within 10 seconds");
            }
            return false;
        }
        return true;
    }
}
