package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;

public class ObjectRecognitionCore {
    // vars for server configuration
    static String sourceName            = "Camera1";
    static String authToken             = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    static String targetVantiqServer    = "ws://localhost:8080";
    
    
    // vars for source configuration
    static String       pbFile          = null;
    static String       metaFile        = null;
    static String       cfgFile         = null;
    static String       weightsFile     = null;
    static boolean      constantPolling = false;
    static int          pollRate        = 0;
    static Timer        pollTimer       = null;
    static String       imageLocation   = null;
    static int          cameraNumber    = 0;
    static VideoCapture vidCapture      = null;
    
    
    static Handler<ExtensionServiceMessage> objRecConfigHandler;
    
    // vars for internal use
    public static boolean           stop    = false;
    static ExtensionWebSocketClient client  = null;
    
    // final vars
    static final Logger         log     = LoggerFactory.getLogger(ObjectRecognitionCore.class);
    static final ObjectMapper   mapper  = new ObjectMapper();
    
    public static Handler<Response> httpHandler = new Handler<Response>() {

        @Override
        public void handleMessage(Response message) {
            System.out.println(message);
        }
        
    };
    
    public static void main(String[] args) {
        setup(args);
        
        client = new ExtensionWebSocketClient(sourceName);
        objRecConfigHandler = new ObjectRecognitionConfigHandler(sourceName);
        
        client.setConfigHandler(objRecConfigHandler);
        //client.setHttpHandler(httpHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);
        
        exitIfConnectionFails(client);
        
        if (constantPolling) {
            while (!stop) {
                Mat image = getImage();
                sendDataFromImage(image);
            }
        } else {
            while (!stop);// let the thread do its thing
            // TODO implement non-busy wait (CDLatch? CF? something else?)
        }
    }
    
    protected static void sendDataFromImage(Mat image) {
        try {
            ArrayList<Map> imageResults= processImage(image);
            client.sendNotification(imageResults);
        } catch (IOException e) {
            log.error("Could not process image", e);
        }
    }
    
    protected static void exit() {
        if (client != null && client.isOpen()) {
            client.stop();
        }
        if (pollTimer != null) {
            pollTimer.cancel();
        }
        if (vidCapture != null) {
            vidCapture.release();
        }
        
        System.exit(0);
    }

    public static ArrayList<Map> processImage(Mat image) throws IOException{
        // TODO do image processing with tensorflow/darkflow
        return null;
    }

    public static Mat getImage() {
        Mat mat = new Mat();
        if (vidCapture != null) {
            // Sets mat to the image
            vidCapture.read(mat);
        } else if (imageLocation == null) {
            mat = Imgcodecs.imread(imageLocation);
        }
        return mat;
    }

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
    }
}
