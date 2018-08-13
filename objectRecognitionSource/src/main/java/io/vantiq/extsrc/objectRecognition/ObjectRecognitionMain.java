package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectRecognitionMain {
    static final Logger                         log         = LoggerFactory.getLogger(ObjectRecognitionMain.class);
    static       List<ObjectRecognitionCore>    sources;
    
    static String authToken             = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
    static String targetVantiqServer    = "ws://localhost:8080";
    static String modelDirectory        = "models/";
    
    public static CompletableFuture<Void> stop = new CompletableFuture<>();
    
    /**
     * Connects to the Vantiq source and starts polling for data. Exits if 
     * @param args  Should be either null or the first argument as a config file
     */
    public static void main(String[] args) {
        Properties config;
        if (args != null && args.length > 0) {
            config = obtainServerConfig(args[0]);
        } else {
            config = obtainServerConfig("server.config");
        }
        
        sources = createSources(config);
        
        startSources(sources);
        
        try {
            stop.get();
        } catch(InterruptedException | ExecutionException e) {
            log.error("Exception occurred while waiting on the 'stop' Future", e);
        }
        
        log.info("Closing...");
        
        for(ObjectRecognitionCore source : sources) {
            source.stop();
        }
    }
    
    private static void startSources(List<ObjectRecognitionCore> sources) {
        for (ObjectRecognitionCore source : sources) {
            source.start();
        }
    }

    /**
     * Turn the given config file into a {@link Map}. 
     * 
     * @param fileName  The name of the config file holding the server configuration.
     * @return          The properties specified in the file.
     */
    static Properties obtainServerConfig(String fileName) {
        File configFile = new File(fileName);
        Properties properties = new Properties();
        
        try {
            properties.load(new FileReader(fileName));
        } catch (IOException e) {
            throw new RuntimeException("Could not find valid server config file. Expected location: '" 
                    + configFile.getAbsolutePath() + "'", e);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when trying to read the server config file. "
                    + "Please ensure it is formatted properly.", e);
        }

        return properties;
    }
    
    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The Properties obtained from the config file
     */
    static List<ObjectRecognitionCore> createSources(Properties config) {
        targetVantiqServer = config.getProperty("targetServer", "wss://dev.vantiq.com/api/v1/wsock/websocket");
        
        authToken = config.getProperty("authToken");
        if (authToken == null) {
            log.error("No valid authentication token in server settings");
            log.error("Exiting...");
            exit(0);
        }
        
        modelDirectory = config.getProperty("modelDirectory", "");
        
        String sourceStr = config.getProperty("sources");
        if (sourceStr == null || sourceStr.equals("")) {
            log.error("No sources in server settings");
            log.error("Exiting...");
            exit(0);
        }
        
        String[] sourceNames = sourceStr.split(",");
        sources = new ArrayList<>();
        for (String sourceName : sourceNames) {
            sourceName = sourceName.trim(); // remove any spacing from the file
            
            ObjectRecognitionCore source;
            source = new ObjectRecognitionCore(sourceName, authToken, targetVantiqServer, modelDirectory);
            sources.add(source);
        }
        
        return sources;
    }
    
    public static void exit(int code) {
        if (sources != null) {
            for (ObjectRecognitionCore source : sources) {
                source.stop();
            }
        }
        System.exit(code);
    }
}
