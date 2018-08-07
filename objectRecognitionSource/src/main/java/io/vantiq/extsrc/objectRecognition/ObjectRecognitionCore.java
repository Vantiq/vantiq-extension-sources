package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;

public class ObjectRecognitionCore {
    // TODO make configurable serverside
    static String sourceName = "Camera1";
    static String authToken = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    static String targetVantiqServer = "ws://localhost:8080";
    static Handler<ExtensionServiceMessage> objRecConfigHandler;
    
    // vars for internal use
    static boolean stop = false;
    
    // final vars
    static final Logger log = LoggerFactory.getLogger(ObjectRecognitionCore.class);
    static final ObjectMapper mapper = new ObjectMapper();
    
    public static Handler<Response> httpHandler = new Handler<Response>() {

        @Override
        public void handleMessage(Response message) {
            System.out.println(message);
            
            
        }
        
    };
    
    public static void main(String[] args) {
        setup(); // TODO unwritten
        
        ExtensionWebSocketClient client = new ExtensionWebSocketClient(sourceName);
        
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
        
        while (!stop) {
            File image = getImage(); // TODO unwritten
            
            try {
                ArrayList<Map> imageResults= processImage(image);
                //client.sendNotification(imageResults);
            } catch (IOException e) {
                log.error("Could not process image", e);
                imageProcessingErrorHandling(); // TODO unwritten
            }
            
        }
    }
    
    public static void imageProcessingErrorHandling() {
        // TODO
        
    }

    public static ArrayList<Map> processImage(File image) throws IOException{
        // Could be String instead. probably should be
        String results = resultsAsString(image); // TODO unwritten
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } // TODO remove
        if (results == null) return null;
        return mapper.readValue(results, ArrayList.class);
    }

    public static String resultsAsString(File image) {
        // TODO Do darkflow stuff. Use Namir's code. Add --json
        return null;
    }

    public static File getImage() {
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
