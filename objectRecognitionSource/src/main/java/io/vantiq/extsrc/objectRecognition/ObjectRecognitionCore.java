package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;

public class ObjectRecognitionCore {
    // TODO make configurable serverside
    static String sourceName            = "Camera1";
    static String authToken             = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    static String targetVantiqServer    = "ws://localhost:8080";
    
    
    // vars for source configuration
    static String   pbFile          = null;
    static String   metaFile        = null;
    static String   cfgFile         = null;
    static String   weightsFile     = null;
    static boolean  constantPolling = false;
    static int      pollRate        = 0;
    static Timer    pollTimer       = null;
    static String   imageLocation   = null;
    static int      cameraNumber    = 0;
    
    
    static Handler<ExtensionServiceMessage> objRecConfigHandler = new Handler<ExtensionServiceMessage>() {

        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            Map<String,Object> config = (Map) message.getObject();
            Map<String,Object> dataSource;
            Map<String,Object> neuralNet; // TODO rename
            
            // Obtain the Maps for each object
            if ( !(config.get("extSrcConfig") instanceof Map) ) {
                log.error("No configuration received for source ' " + sourceName + "'. Exiting...");
                exit();
            }
            config = (Map) config.get("extSrcConfig");
            if ( !(config.get("dataSource") instanceof Map)) {
                log.error("No data source specified for source ' " + sourceName + "'. Exiting...");
                exit();
            }
            dataSource = (Map) config.get("dataSource");
            if ( !(config.get("neuralNet") instanceof Map)) {
                log.error("No neural net specified for source ' " + sourceName + "'. Exiting...");
                exit();
            }
            neuralNet = (Map) config.get("neuralNet");
            
            // Obtain the files for the net
            if (neuralNet.get("pbFile") instanceof String && neuralNet.get("metaFile") instanceof String) {
                pbFile = (String) neuralNet.get("pbFile");
                metaFile = (String) neuralNet.get("metaFile");
            } else if (neuralNet.get("cfgFile") instanceof String && neuralNet.get("weightsFile") instanceof String) {
                cfgFile = (String) neuralNet.get("cfgFile");
                weightsFile = (String) neuralNet.get("weightsFile");
            } else {
                log.error("No valid neural net combination of either pbFile and metaFile or cfgFile and weightsFile");
                log.error("Exiting...");
                exit();
            }
            
            // Figure out where to receive the data from
            if (dataSource.get("fileLocation") instanceof String) {
                imageLocation = (String) dataSource.get("fileLocation");
            } else if (dataSource.get("camera") instanceof Integer && (int) dataSource.get("camera") >= 0) {
                cameraNumber =  (int) dataSource.get("camera");
                // TODO Setup openCV
            } else {
                log.error("No valid polling target");
                log.error("Exiting...");
                exit();
            }
            
            if (dataSource.get("polling") instanceof Integer) {
                int polling = (int) dataSource.get("polling");
                if (polling > 0) {
                    pollRate = polling;
                    TimerTask task = new TimerTask() {
                        boolean isRunning = false;
                        @Override
                        public void run() {
                            if (!isRunning) {
                                isRunning = true;
                                Mat image = getImage(); // TODO unwritten
                                sendDataFromImage(image);
                                isRunning = false;
                            }
                        }
                    };
                    pollTimer = new Timer("dataCapture");
                    pollTimer.scheduleAtFixedRate(task, 0, pollRate);
                } else if (polling == 0) {
                    constantPolling = true;
                } else {
                    // TODO snapshot on publish/query choice TBD
                }
            } else {
                log.error("No valid polling rate");
                log.error("Exiting...");
                exit();
            }
        }
        
    };
    
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
        setup(); // TODO unwritten
        
        client = new ExtensionWebSocketClient(sourceName);
        
        client.setConfigHandler(objRecConfigHandler);
        //client.setHttpHandler(httpHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);
        
        exitIfConnectionFails(client);
        
        String sampleData = "["
                + "{'label': 'tvmonitor', 'confidence': 0.80728817, 'topleft': {'x': 165, 'y': 91}, 'bottomright': {'x': 349, 'y': 275}}, "
                + "{'label': 'tvmonitor', 'confidence': 0.18708503, 'topleft': {'x': 319, 'y': 132}, 'bottomright': {'x': 422, 'y': 311}}, "
                + "{'label': 'mouse', 'confidence': 0.5298999, 'topleft': {'x': 422, 'y': 308}, 'bottomright': {'x': 485, 'y': 361}}, "
                + "{'label': 'mouse', 'confidence': 0.30950272, 'topleft': {'x': 354, 'y': 341}, 'bottomright': {'x': 434, 'y': 374}}, "
                + "{'label': 'keyboard', 'confidence': 0.83932924, 'topleft': {'x': 121, 'y': 255}, 'bottomright': {'x': 349, 'y': 372}}, "
                + "{'label': 'refrigerator', 'confidence': 0.59843427, 'topleft': {'x': 0, 'y': 15}, 'bottomright': {'x': 121, 'y': 364}}"
                + "]";
        sampleData = sampleData.replace('\'', '"');
        try {
            List l = mapper.readValue(sampleData, List.class);
            client.sendNotification(l);
        } catch (IOException e) {
            log.error("Could not send message", e);
            System.exit(0);
        }
        
        if (constantPolling) {
            while (!stop) {
                Mat image = getImage(); // TODO unwritten
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
            //client.sendNotification(imageResults);
        } catch (IOException e) {
            log.error("Could not process image", e);
            imageProcessingErrorHandling(); // TODO unwritten
        }
    }
    
    protected static void exit() {
        if (client != null && client.isOpen()) {
            client.stop();
        }
        if (pollTimer != null) {
            pollTimer.cancel();
        }
        
        System.exit(0);
    }

    public static void imageProcessingErrorHandling() {
        // TODO
    }

    public static ArrayList<Map> processImage(Mat image) throws IOException{
        // TODO do image processing with tensorflow/darkflow
        return null;
    }

    public static Mat getImage() {
        // TODO Use Namir's code
        return null;
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
            client.stop();
            System.exit(0);
        }
    }
    
    public static void setup() {
        // TODO
    }
    
}
