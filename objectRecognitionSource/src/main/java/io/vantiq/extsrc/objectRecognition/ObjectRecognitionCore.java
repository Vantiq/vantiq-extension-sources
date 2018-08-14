package io.vantiq.extsrc.objectRecognition;

import java.util.List;
import java.util.Map;
import java.util.Timer;
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
 * Controls the connection and interaction with the Vantiq server. 
 */
public class ObjectRecognitionCore {
    // vars for server configuration
    String sourceName           = "Camera1";
    String authToken            = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    String targetVantiqServer   = "ws://localhost:8080";
    String modelDirectory       = "models/";
    
    
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
            close();
            objRecConfigHandler.configComplete = false;
            
            client.setQueryHandler(defaultQueryHandler);
            
            client.connectToSource();
        }
    };
    
    public Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.info("Websocket closed unexpectedly. Attempting to reconnect");
            close();
            objRecConfigHandler.configComplete = false;
            
            client.setQueryHandler(defaultQueryHandler);
            
            client.initiateFullConnection(targetVantiqServer, authToken);
            exitIfConnectionFails(client);
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
     * Tries to connect to a source and waits up to 10 seconds for it to succeed or fail.
     * @return  true if the source connection succeeds, false if it fails.
     */
    public boolean start() {
        client = new ExtensionWebSocketClient(sourceName);
        objRecConfigHandler = new ObjectRecognitionConfigHandler(this);
        
        client.setConfigHandler(objRecConfigHandler);
        client.setReconnectHandler(reconnectHandler);
        client.setCloseHandler(closeHandler);
        client.setQueryHandler(defaultQueryHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);
        
        return exitIfConnectionFails(client);
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
        }
        return null;
    }
    
    /**
     * Retrieves an image using the Core's image retriever. Calls stop() if a FatalImageException is received.
     * @param request   The request sent with a Query message.
     * @return          The image retrieved in jpeg format, or null if a problem occurred.
     */
    public synchronized byte[] retrieveImage(Map<String,?> request) {
        if (imageRetriever == null) { // Should only happen if close() was called immediately before retreiveImage()
            return null;
        }
        try {
            return imageRetriever.getImage(request);
        } catch (ImageAcquisitionException e) {
            log.warn("Could not obtain requested image.", e);
        } catch (FatalImageException e) {
            log.error("Image retriever of type '" + imageRetriever.getClass().getCanonicalName() 
                    + "' failed unrecoverably"
                    , e);
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
        }
    }
    
    /**
     * Closes all resources held by this program except for the client. 
     */
    protected void close() {
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
     * @return          true if the connection succeeded, false if it failed to connect within 10 seconds.
     */
    public boolean exitIfConnectionFails(ExtensionWebSocketClient client) {
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = client.getSourceConnectionFuture().get(10, TimeUnit.SECONDS);
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
                log.error("Failed to auth within 10 seconds using the given auth data for source '" + sourceName + "'");
            }
            else {
                log.error("Failed to connect to '" + sourceName + "' within 10 seconds");
            }
            stop();
            return false;
        }
        return true;
    }
    
    /**
     * Sets up the defaults for the server based on the configuration file
     * @param config    The Properties obtained from the config file
     */
    void setup(Map<String,?> config) {
        targetVantiqServer = config.get("targetServer") instanceof String ? (String) config.get("targetServer")
                : "wss://dev.vantiq.com/api/v1/wsock/websocket";
        
        if (config.get("authToken") instanceof String) {
            authToken = (String) config.get("authToken");
        } else {
            log.error("No valid authentication token in server settings");
            log.error("Exiting...");
            stop();
        }
        
        if (config.get("source") instanceof String) {
            sourceName = (String) config.get("source");
        } else {
            log.error("No valid source in server settings");
            log.error("Exiting...");
            stop();
        }
        
        if (config.get("modelDirectory") instanceof String) {
            modelDirectory = (String) config.get("modelDirectory");
        }
    }
}
