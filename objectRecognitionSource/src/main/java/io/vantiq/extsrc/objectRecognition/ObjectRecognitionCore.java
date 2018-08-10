package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ml.tensorflow.FrameCapture;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;

public class ObjectRecognitionCore {
    // vars for server configuration
    static String sourceName            = "Camera1";
    static String authToken             = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    static String targetVantiqServer    = "ws://localhost:8080";
    static String modelDirectory        = "models/";
    
    
    // vars for source configuration
    static boolean      constantPolling = false;
    static int          pollRate        = 0;
    static Timer        pollTimer       = null;
    static String       imageLocation   = null;
    static int          cameraNumber    = 0;
    static FrameCapture frameCapture    = null;
    
    
    static ObjectRecognitionConfigHandler objRecConfigHandler;
    
    // vars for internal use
    public static CompletableFuture<Void>   stop        = new CompletableFuture<>();
    static ExtensionWebSocketClient         client      = null;
    static NeuralNetInterface               neuralNet  = null;
    
    // final vars
    static final Logger         log     = LoggerFactory.getLogger(ObjectRecognitionCore.class);
    static final ObjectMapper   mapper  = new ObjectMapper();
    
    public static Handler<Response> httpHandler = new Handler<Response>() {

        @Override
        public void handleMessage(Response message) {
            System.out.println(message);
        }
        
    };
    
    /**
     * Connects to the Vantiq source and starts polling for data. Exits if 
     * @param args  Should be either null or the first argument as a config file
     */
    public static void main(String[] args) {
        // setup(args); // TODO uncomment
        
        client = new ExtensionWebSocketClient(sourceName);
        objRecConfigHandler = new ObjectRecognitionConfigHandler(sourceName);
        
        client.setConfigHandler(objRecConfigHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);
        
        exitIfConnectionFails(client);
        
        while (!objRecConfigHandler.isComplete()) {
            Thread.yield();
        }
        
        if (constantPolling) {
            while (!stop.isDone()) {
                byte[] image = getImage();
                sendDataFromImage(image);
            }
        } else {
            try {
                stop.get();
            } catch(InterruptedException | ExecutionException e) {
                log.error("Exception occurred while waiting on the 'stop' Future", e);
            }
        }
        log.info("Closing...");
        
        exit();
    }
    
    /**
     * Processes the image then sends the results to the Vantiq source
     * @param image An OpenCV Mat representing the image to be translated
     */
    protected static void sendDataFromImage(byte[] image) {
        List<Map> imageResults = neuralNet.processImage(image);
        client.sendNotification(imageResults);
    }
    
    /**
     * Closes all resources held by this program and then closes the connection. 
     */
    protected static void exit() {
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
    public static byte[] getImage() {
        return frameCapture.capureSnapShot();
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within 10 seconds.
     *
     * @param client    The client to watch for success or failure.
     */
    public static void exitIfConnectionFails(ExtensionWebSocketClient client) {
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
            exit();
        }
    }
    
    /**
     * Obtains and uses the configuration file specified in args.
     * 
     * @param args  The args for the program. Expected to be either null, or the first arg is the file path
     */
    public static void setup(String[] args) {
        Map<String, Object> config = null;
        if (args != null) {
            obtainServerConfig(args[0]);
        } else {
            obtainServerConfig("config.json");
        }
        setupServer(config);
    }
    
    
    /**
     * Turn the given JSON file into a {@link Map}. 
     * 
     * @param fileName  The name of the JSON file holding the server configuration.
     * @return          A {@link Map} that holds the contents of the JSON file.
     */
    static Map<String, Object> obtainServerConfig(String fileName) {
        File configFile = new File(fileName);
        log.debug(configFile.getAbsolutePath());
        Map<String, Object>  config = new LinkedHashMap();
        ObjectMapper mapper = new ObjectMapper();
        try {
            config = mapper.readValue(configFile, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not find valid server config file. Expected location: '" 
                    + configFile.getAbsolutePath() + "'", e);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when trying to read the server config file. "
                    + "Please ensure it is proper JSON.", e);
        }

        return config;

    }

    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The {@link Map} obtained from the config file
     */
    static void setupServer(Map config) {
        targetVantiqServer = config.get("targetServer") instanceof String ? (String) config.get("targetServer") :
                "wss://dev.vantiq.com/api/v1/wsock/websocket";
        if (config.get("authToken") instanceof String) {
            authToken = (String) config.get("authToken") ;
        } else {
            log.error("No valid authentication token in server settings");
            log.error("Exiting...");
            exit();
        }
        if (config.get("source") instanceof String) {
            sourceName = (String) config.get("source");
        } else {
            log.error("No valid source in server settings");
            log.error("Exiting...");
            exit();
        }
        if (config.get("modelDirectory") instanceof String) {
            modelDirectory = (String) config.get("modelDirectory");
        }
    }
}
