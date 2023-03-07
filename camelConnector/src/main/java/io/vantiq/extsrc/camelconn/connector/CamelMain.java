/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.connector;

import static io.vantiq.extjsdk.Utils.AUTH_TOKEN_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.SERVER_CONFIG_FILENAME;
import static io.vantiq.extjsdk.Utils.SOURCES_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.TARGET_SERVER_PROPERTY_NAME;

import io.vantiq.extjsdk.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *  The main class of this program. It connects to sources using the specified configuration. All sources must belong to
 *  the same VANTIQ Server & Namespace (i.e. same authtoken). Options are in a file where each option has its own line,
 *  and is in the form {@code property=value} with no quotes required for Strings. Options are:
 *  <ul>
 *      <li>{@code accessToken}: Required. The authentication token to connect with. These can be obtained from the
 *                      namespace admin.
 *      <li>{@code sources}: Required. A comma separated list of the sources to which you wish to connect. Whitespace 
 *                      around each name will be removed when read.
 *      <li>{@code targetServer}: Required. The Vantiq server hosting the sources.
 *  </ul>
 */

@Slf4j
public class CamelMain {

    static List<CamelCore> sources;
        
    static String accessToken;
    static String targetVantiqServer;
    
    // Exit Error Codes
    static final int NO_AUTH_EXIT = 1;
    static final int NO_SOURCE_EXIT = 2;
    static final int NO_SERVER_EXIT = 3;
    
    /**
     * Connects to the Vantiq source and starts polling for data. Exits when all sources are done running.
     * @param args  Should be either null or the first argument as a config file
     */
    public static void main(String[] args) {
        Properties config;
        if (args != null && args.length > 0) {
            config = Utils.obtainServerConfig(args[0]);
        } else {
            config = Utils.obtainServerConfig(SERVER_CONFIG_FILENAME);
        }
        
        sources = createSources(config);
        
        startSources(sources);
        
        // Can leave now because the threads created by the sources' WebSocket connections will keep the JVM alive
    }
    
    /**
     * Starts every source, giving each up to 10 seconds to connect. Starts them in new threads so they don't block.
     * @param sources   The list of sources which should be started.
     */
    private static void startSources(List<CamelCore> sources) {
        for (CamelCore source : sources) {
            // Starting in threads so they can all connect at once
            // FIXME -- this should just use camel start up...
            new Thread( () -> {source.start(10);} ).start();;
        }
    }
    
    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The Properties obtained from the configuration file
     */
    static List<CamelCore> createSources(Properties config) {
        accessToken = config.getProperty(AUTH_TOKEN_PROPERTY_NAME);
        if (accessToken == null) {
            log.error("No valid access/authorization token in server settings");
            log.error("Exiting...");
            exit(NO_AUTH_EXIT);
        }
        
        String sourceStr = config.getProperty(SOURCES_PROPERTY_NAME);
        if (StringUtils.isEmpty(sourceStr)) {
            log.error("No sources in server settings");
            log.error("Exiting...");
            exit(NO_SOURCE_EXIT);
        }
    
        // Obtain potentially multiple sources from a comma delimited string of sources
        String[] sourceNames = sourceStr.split(",");
        // TODO: Don't know that we really care here -- though I don't know how many Camels fit in a JVM.  So may be
        //  better to stick to a single one.  We can extend this later if we needed.
        if (sourceNames.length != 1) {
            log.error("The Camel connector supports operation as only a single source.");
            log.error("Exiting...");
            exit(NO_SOURCE_EXIT);
        }
        
        targetVantiqServer = config.getProperty(TARGET_SERVER_PROPERTY_NAME);
        if (StringUtils.isEmpty(targetVantiqServer)) {
            log.error("No server URL specified in server settings");
            log.error("Exiting...");
            exit(NO_SERVER_EXIT);
        }
        
        sources = new ArrayList<>();
        for (String sourceName : sourceNames) {
            sourceName = sourceName.trim(); // remove any spacing from the name
            
            CamelCore source;
            source = new CamelCore(sourceName, accessToken, targetVantiqServer);
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
            for (CamelCore source : sources) {
                source.stop();
            }
        }
        System.exit(code);
    }
}
