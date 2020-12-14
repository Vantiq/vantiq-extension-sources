
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import io.vantiq.extjsdk.Utils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  The main of this program. It connects to sources using the specified configuration. Options are in a file where each
 *  option has its own line, and is in the form {@code property=value} with no quotes required for Strings. Options are:
 *  <ul>
 *      <li>{@code authToken}: Required. The authentication token to connect with. These can be obtained from the 
 *                      namespace admin.
 *      <li>{@code sources}: Required. A comma separated list of the sources to which you wish to connect. Whitespace 
 *                      around each name will be removed when read.
 *      <li>{@code targetServer}: Required. The Vantiq server hosting the sources.
 *      <li>{@code modelDirectory}: Optional. The directory in which the files for your neural networks will be.
 *                      Defaults to the working directory.
 *  </ul>
 */
public class ObjectRecognitionMain {
    static final Logger                         log         = LoggerFactory.getLogger(ObjectRecognitionMain.class);
    static       List<ObjectRecognitionCore>    sources;
    
    public static final String DEFAULT_MODEL_DIRECTORY = "";
    
    static String authToken;
    static String targetVantiqServer;
    static String modelDirectory;
    
    /**
     * Connects to the Vantiq source and starts polling for data. Exits when all sources are done running.
     * @param args  Should be either null or the first argument as a config file
     */
    public static void main(String[] args) {
        Properties config;
        if (args != null && args.length > 0) {
            config = Utils.obtainServerConfig(args[0]);
        } else {
            config = Utils.obtainServerConfig("server.config");
        }
        
        sources = createSources(config);
        
        startSources(sources);
        
        // Can leave now because the threads created by the sources' WebSocket connections will keep the JVM alive
    }
    
    /**
     * Starts every source, giving each up to 10 seconds to connect. Starts them in new threads so they don't block.
     * @param sources   The list of sources which should be started.
     */
    private static void startSources(List<ObjectRecognitionCore> sources) {
        for (ObjectRecognitionCore source : sources) {
            // Starting in threads so they can all connect at once
            new Thread( () -> {source.start(10);} ).start();;
        }
    }
    
    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The Properties obtained from the configuration file
     */
    static List<ObjectRecognitionCore> createSources(Properties config) {
        authToken = config.getProperty("authToken");
        if (authToken == null) {
            log.error("No valid authentication token in server settings");
            log.error("Exiting...");
            exit(0);
        }
        
        String sourceStr = config.getProperty("sources");
        if (sourceStr == null || sourceStr.equals("")) {
            log.error("No sources in server settings");
            log.error("Exiting...");
            exit(0);
        }
        
        targetVantiqServer = config.getProperty("targetServer");
        if (targetVantiqServer == null || targetVantiqServer.equals("")) {
            log.error("No server URL specified in server settings");
            log.error("Exiting...");
            exit(0);
        }
        
        modelDirectory = config.getProperty("modelDirectory", DEFAULT_MODEL_DIRECTORY);
        
        // Obtain potentially multiple sources from a comma delimited string of sources 
        String[] sourceNames = sourceStr.split(",");
        sources = new ArrayList<>();
        for (String sourceName : sourceNames) {
            sourceName = sourceName.trim(); // remove any spacing from the name
            
            ObjectRecognitionCore source;
            source = new ObjectRecognitionCore(sourceName, authToken, targetVantiqServer, modelDirectory);
            sources.add(source);
        }
        
        return sources;
    }
    
    /**
     * Closes all sources then orders the JVM to exit
     * @param code  The exit code
     */
    public static void exit(int code) {
        if (sources != null) {
            for (ObjectRecognitionCore source : sources) {
                source.stop();
            }
        }
        System.exit(code);
    }
}
