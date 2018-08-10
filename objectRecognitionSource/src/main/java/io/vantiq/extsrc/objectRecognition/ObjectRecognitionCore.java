package io.vantiq.extsrc.objectRecognition;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;

public class ObjectRecognitionCore {
    // vars for server configuration
    String sourceName            = "Camera1";
    String authToken             = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    String targetVantiqServer    = "ws://localhost:8080";
    String modelDirectory        = "models/";
    
    
    // vars for source configuration
    boolean      constantPolling = false;
    int          pollRate        = 0;
    Timer        pollTimer       = null;
    String       imageLocation   = null;
    int          cameraNumber    = 0;
    FrameCapture frameCapture    = null;
    
    
    ObjectRecognitionConfigHandler objRecConfigHandler;
    
    // vars for internal use
    public static CompletableFuture<Void>   stop        = new CompletableFuture<>();
    ExtensionWebSocketClient         client      = null;
    NeuralNetInterface               neuralNet  = null;
    
    // final vars
    final Logger log;
    
    public Handler<Response> httpHandler = new Handler<Response>() {

        @Override
        public void handleMessage(Response message) {
            System.out.println(message);
        }
        
    };
    
    
    
    public ObjectRecognitionCore(String sourceName, String authToken, String targetVantiqServer, String modelDirectory) {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + '#' + sourceName);
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
        this.modelDirectory = modelDirectory;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    /**
     * Processes the image then sends the results to the Vantiq source
     * @param image An OpenCV Mat representing the image to be translated
     */
    protected void sendDataFromImage(byte[] image) {
        List<Map> imageResults = neuralNet.processImage(image);
        client.sendNotification(imageResults);
    }
    
    /**
     * Closes all resources held by this program and then closes the connection. 
     */
    protected void close() {
        if (client != null && client.isOpen()) {
            client.stop();
        }
        if (pollTimer != null) {
            pollTimer.cancel();
        }
        if (frameCapture != null) {
            frameCapture.close();
        }
        if (neuralNet != null) {
            neuralNet.close();
        }
        
        System.exit(0);
    }

    /**
     * Obtains an image from either a camera or file. 
     * @return  An OpenCV Mat containing the image specified.
     */
    public byte[] getImage() {
        return frameCapture.captureSnapShot();
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within 10 seconds.
     *
     * @param client    The client to watch for success or failure.
     */
    public void exitIfConnectionFails(ExtensionWebSocketClient client) {
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
            close();
        }
    }
    
    
    
    
    

    
    
    /**
     * Sets up the defaults for the server based on the configuration file
     *
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
            close();
        }
        
        if (config.get("source") instanceof String) {
            sourceName = (String) config.get("source");
        } else {
            log.error("No valid source in server settings");
            log.error("Exiting...");
            close();
        }
        
        if (config.get("modelDirectory") instanceof String) {
            modelDirectory = (String) config.get("modelDirectory");
        }
    }
}
