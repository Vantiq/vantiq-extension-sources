package io.vantiq.extsrc.objectRecognition;

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

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it and call start() and it will run 
 * itself. start() will return a boolean describing whether or not it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
public class ObjectRecognitionCore {
    // vars for server configuration
    String sourceName           = "Camera1";
    String authToken            = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    String targetVantiqServer   = "ws://localhost:8080";
    String modelDirectory       = "";
    
    
    // vars for source configuration
    boolean                 constantPolling = false;
    boolean                 stopPolling     = false;
    Timer                   pollTimer       = null;
    ImageRetrieverInterface imageRetriever  = null;
    
    ObjectRecognitionConfigHandler objRecConfigHandler;
    
    // vars for internal use
    ExtensionWebSocketClient    client      = null;
    NeuralNetInterface          neuralNet   = null;
    
    // final vars
    final Logger log;
    
    public Handler<Response> httpHandler = new Handler<Response>() {
        @Override
        public void handleMessage(Response message) {
            System.out.println(message);
        }
    };
    
    public Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.info("Reconnect message received. Reinitializing configuration");
            
            // Do partial close to preserve states of imageRetriever and neuralNet
            if (constantPolling) {
                stopPolling = true;
                constantPolling = false;
            }
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
    
    public Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.info("Websocket closed unexpectedly. Attempting to reconnect");

            // Do partial close to preserve states of imageRetriever and neuralNet
            if (constantPolling) {
                stopPolling = true;
                constantPolling = false;
            }
            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }
            
            objRecConfigHandler.configComplete = false;
            
            client.setQueryHandler(defaultQueryHandler);
            
            client.initiateFullConnection(targetVantiqServer, authToken);
            exitIfConnectionFails(client, 10);
        }
    };
    
    Handler<ExtensionServiceMessage> defaultQueryHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage msg) {
            log.warn("Query received with no user-set handler");
            log.debug("Full message: " + msg);
            // Prepare a response with an empty body, so that the query doesn't wait for a timeout
            Object[] body = {msg.getSourceName()};
            client.sendQueryError(ExtensionServiceMessage.extractReplyAddress(msg),
                    "io.vantiq.extsrc.objectRecognition.UnexpectedQuery",
                    "No handler has been set for source {0}",
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
     * Tries to connect to a source and waits up to timeout seconds for it to succeed or fail.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     * @return          true if the source connection succeeds, false if it fails.
     */
    public boolean start(int timeout) {
        client = new ExtensionWebSocketClient(sourceName);
        objRecConfigHandler = new ObjectRecognitionConfigHandler(this);
        
        client.setConfigHandler(objRecConfigHandler);
        client.setReconnectHandler(reconnectHandler);
        client.setCloseHandler(closeHandler);
        client.setQueryHandler(defaultQueryHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);
        
        return exitIfConnectionFails(client, timeout);
    }
    
    /**
     * Continuously retrieves and image and sends the result until close() or stop() is called.
     */
    void startContinuousRetrievals() {
        while (!stopPolling) {
            byte[] image = retrieveImage();
            sendDataFromImage(image);
        }
        stopPolling = false;
    }
    
    /**
     * Retrieves an image using the Core's image retriever. Calls stop() if a FatalImageException was received.
     * @return  The image retrieved in jpeg format, or null if a problem occurred.
     */
    public synchronized byte[] retrieveImage() {
        if (imageRetriever == null) { // Should only happen if close() was called immediately before retreiveImage()
            return null;
        }
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
        return null;
    }
    
    /**
     * Retrieves an image using the Core's image retriever. Calls stop() if a FatalImageException is received.
     * @param message   The Query message.
     * @return          The image retrieved in jpeg format, or null if a problem occurred.
     */
    public synchronized byte[] retrieveImage(ExtensionServiceMessage message) {
        Map<String,?> request = (Map<String,?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        if (imageRetriever == null) { // Should only happen if close() was called immediately before retreiveImage()
            if (client != null) {
                client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                        "The source closed mid message", null);
            }
            return null;
        }
        
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
                    "Unexpected exception when obtaining an image for reason {0}. Exception was {1}. Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
            stop();
        }
        return null;
    }
    
    /**
     * Processes the image then sends the results to the Vantiq source. Calls stop() if a FatalImageException is
     * received.
     * @param image An OpenCV Mat representing the image to be translated
     */
    public void sendDataFromImage(byte[] image) {
        if (image == null || image.length == 0) {
            return;
        }
        try {
            synchronized (this) {
                if (neuralNet == null) { // Should only happen when close() runs just before sendDataFromImage()
                    return;
                }
                List<Map> imageResults = neuralNet.processImage(image);
                client.sendNotification(imageResults);
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
    * Processes the image then sends the results to the Vantiq source. Calls stop() if a FatalImageException is
    * received.
    * @param image      An OpenCV Mat representing the image to be translated
    * @param message    The Query message
    */
   public void sendDataFromImage(byte[] image, ExtensionServiceMessage message) {
       Map<String,?> request = (Map<String,?>) message.getObject();
       String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
       if (image == null || image.length == 0) {
           if (client != null) {
               client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                       "The source closed mid message", null);
           }
           return;
       }
       try {
           synchronized (this) {
               if (neuralNet == null) { // Should only happen when close() runs just before sendDataFromImage()
                   if (client != null) {
                       client.sendQueryError(replyAddress, this.getClass().getPackage().getName() + ".closed",
                               "The source closed mid message", null);
                   }
                   return;
               }
               List<Map> data = neuralNet.processImage(image, request);
               if (data.isEmpty()) {
                   client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
               } else {
                   client.sendQueryResponse(200, replyAddress, data.toArray(new Map[0]));
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
                   "Uncaught exception when processing image for reason {0}. Exception was {1}. Request was {2}"
                   , new Object[] {e.getMessage(), e, request});
           stop();
       }
   }
    
    /**
     * Closes all resources held by this program except for the client. 
     */
    public void close() {
        if (constantPolling) {
            stopPolling = true;
            constantPolling = false;
        }
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
    protected void stop() {
        close();
        if (client != null && client.isOpen()) {
            client.stop();
            client = null;
        }
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within 10 seconds.
     *
     * @param client    The client to watch for success or failure.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping
     * @return          true if the connection succeeded, false if it failed to connect within 10 seconds.
     */
    public boolean exitIfConnectionFails(ExtensionWebSocketClient client, int timeout) {
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = client.getSourceConnectionFuture().get(timeout, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            log.error("Timeout: not all WebSocket connections succeeded within 10 seconds.");
        }
        catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources. Exiting...");
            if (!client.isOpen()) {
                log.error("Failed to connect to '" + targetVantiqServer + "' for source '" + sourceName + "'");
            }
            else if (!client.isAuthed()) {
                log.error("Failed to auth within " + timeout + " seconds using the given auth data for source '"
                            + sourceName + "'");
            }
            else {
                log.error("Failed to connect to '" + sourceName + "' within 10 seconds");
            }
            stop();
            return false;
        }
        return true;
    }
}
