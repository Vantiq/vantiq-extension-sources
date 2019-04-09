/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

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
 *  The main class of this program. It connects to sources using the specified configuration. All sources must belong to
 *  the same VANTIQ Server & Namespace (i.e. same authtoken). Options are in a file where each option has its own line,
 *  and is in the form {@code property=value} with no quotes required for Strings. Options are:
 *  <ul>
 *      <li>{@code authToken}: Required. The authentication token to connect with. These can be obtained from the
 *                      namespace admin.
 *      <li>{@code sources}: Required. A comma separated list of the sources to which you wish to connect. Whitespace
 *                      around each name will be removed when read.
 *      <li>{@code targetServer}: Required. The Vantiq server hosting the sources.
 *  </ul>
 */

public class JMSMain {

    static final Logger             log = LoggerFactory.getLogger(JMSMain.class);
    static       List<JMSCore>     sources;

    static String authToken;
    static String targetVantiqServer;

    // Exit Error Codes
    static final int NO_AUTH_EXIT = 1;
    static final int NO_SOURCE_EXIT = 2;
    static final int NO_SERVER_EXIT = 3;
    static final int NO_CONFIG_EXIT = 4;

    /**
     * Connects to the Vantiq source and starts polling for data. Exits when all sources are done running.
     * @param args  Should be either null or the first argument as a config file
     */
    public static void main(String[] args) {
        Properties config = null;
        if (args == null) {
            config = obtainServerConfig("server.config");
        } else if (args != null && args.length == 1) {
            config = obtainServerConfig(args[0]);
        } else {
            log.error("An incorrect number of command line arguments were specified. There can only be exactly 1 command line"
                    + " argument, or exactly 0 command line arguments.");
            exit(NO_CONFIG_EXIT);
        }

        sources = createSources(config);

        startSources(sources);

        // Can leave now because the threads created by the sources' WebSocket connections will keep the JVM alive
    }

    /**
     * Starts every source, giving each up to 10 seconds to connect. Starts them in new threads so they don't block.
     * @param sources   The list of sources which should be started.
     */
    private static void startSources(List<JMSCore> sources) {
        for (JMSCore source : sources) {
            // Starting in threads so they can all connect at once
            new Thread( () -> {source.start(10);} ).start();;
        }
    }

    /**
     * Turn the given configuration file into a {@link Map}.
     *
     * @param fileName  The name of the configuration file holding the server configuration.
     * @return          The properties specified in the file.
     */
    static Properties obtainServerConfig(String fileName) {
        File configFile = new File(fileName);
        Properties properties = new Properties();

        try {
            properties.load(new FileReader(fileName));
        } catch (IOException e) {
            throw new RuntimeException("Could not find valid server configuration file. Expected location: '"
                    + configFile.getAbsolutePath() + "'", e);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when trying to read the server configuration file. "
                    + "Please ensure it is formatted properly.", e);
        }

        return properties;
    }

    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The Properties obtained from the configuration file
     */
    static List<JMSCore> createSources(Properties config) {
        authToken = config.getProperty("authToken");
        if (authToken == null) {
            log.error("No valid authentication token in server settings");
            log.error("Exiting...");
            exit(NO_AUTH_EXIT);
        }

        String sourceStr = config.getProperty("sources");
        if (sourceStr == null || sourceStr.equals("")) {
            log.error("No sources in server settings");
            log.error("Exiting...");
            exit(NO_SOURCE_EXIT);
        }

        targetVantiqServer = config.getProperty("targetServer");
        if (targetVantiqServer == null || targetVantiqServer.equals("")) {
            log.error("No server URL specified in server settings");
            log.error("Exiting...");
            exit(NO_SERVER_EXIT);
        }

        // Obtain potentially multiple sources from a comma delimited string of sources
        String[] sourceNames = sourceStr.split(",");
        sources = new ArrayList<>();
        for (String sourceName : sourceNames) {
            sourceName = sourceName.trim(); // remove any spacing from the name

            JMSCore source;
            source = new JMSCore(sourceName, authToken, targetVantiqServer);
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
            for (JMSCore source : sources) {
                source.stop();
            }
        }
        System.exit(code);
    }
}
