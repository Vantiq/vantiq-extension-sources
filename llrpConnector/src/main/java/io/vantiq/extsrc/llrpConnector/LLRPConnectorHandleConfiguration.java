/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.llrpConnector;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;

import io.vantiq.extsrc.llrpConnector.exception.VantiqLLRPException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Sets up the source using the configuration document, which looks as below.
 *{
 *      llrpConfig: {
 *          general: {
 *              <general options>
 *          }
 *      }
 * }
 *
 * The options for 'general' are as follows. At least the Hostname and Reader Port MUST
 * be specified for the source to function:
 *
 *  - hostname: The hostname or ipAddress to connect to the Reader.
 *  - readerPort: The readerPort to connect to the Reader.
 *  - tagReadInterval: Interval in milliseconds to receive Tag Data*
 *  - logLevel: Indicates log messages to send to Vantiq ('info', 'warn', 'error')
 *
 */
public class LLRPConnectorHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger log;
    String sourceName;
    LLRPConnectorCore core;

    boolean configComplete = false;

    private static final int DEFAULT_TAGREAD_INTERVAL = 2000;
    private static final String DEFAULT_LOG_LEVEL = "None";

    // Constants for getting config options
    private static final String CONFIG = "config";
    private static final String LLRP_CONFIG = "llrpConfig";
    private static final String GENERAL = "general";
    private static final String HOSTNAME = "hostname";
    private static final String READERPORT = "readerPort";
    private static final String TAGREAD_INTERVAL = "tagReadInterval";  // if not found use default
    private static final String LOG_LEVEL = "logLevel";  // if not found use default

    public LLRPConnectorHandleConfiguration(LLRPConnectorCore core) {
        this.core = core;
        this.sourceName = core.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }

    /**
     * Interprets the configuration message sent by the VANTIQ server and sets up the LLRP Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, Object> llrpConfig;
        Map<String, Object> general;

        // Obtain entire config from the message object
        if (!(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for LLRP Connector.");
            System.out.println("Configuration failed. No configuration suitable for LLRP Connector.");
            failConfig();
            return;
        }
        config = (Map) configObject.get(CONFIG);

        // Retrieve the llrpConfig and the vantiq config
        if (!(config.get(LLRP_CONFIG) instanceof Map)) {
            log.error("Configuration failed. Configuration must contain 'llrpConfig' field.");
            System.out.println("Configuration failed. Configuration must contain 'llrpConfig' field.");
            failConfig();
            return;
        }
        llrpConfig = (Map) config.get(LLRP_CONFIG);

        // Get the general options from the llrpConfig
        if (!(llrpConfig.get(GENERAL) instanceof Map)) {
            log.error("Configuration failed. No general options specified.");
            System.out.println("Configuration failed. No general options specified.");
            failConfig();
            return;
        }
        general = (Map) llrpConfig.get(GENERAL);

        // Call method to setup the connector with the source configuration
        boolean success = setupLLRPConnector(general);
        if (!success) {
            System.out.println("Failed during createLLRPConnection.");
            failConfig();
            return;
        }

        log.trace("SetupLLRPConnector complete");
        System.out.println("SetupLLRPConnector complete");
        configComplete = true;
    }

    /**
     * Attempts to create the LLRP Connector based on the configuration document.
     * @param generalConfig     The general configuration for the LLRP Connector
     * @return                  true if the LLRP source could be created, false otherwise
     */
    boolean setupLLRPConnector(Map generalConfig) {

        // Get Hostname/IP Address and Port
        String hostname;
        int readerPort;
        int tagReadInterval;
        String logLevel;

        if (generalConfig.get(HOSTNAME) instanceof String) {
           hostname = (String) generalConfig.get(HOSTNAME);
        } else {
           log.error("Configuration failed. No hostname was specified");
           return false;
        }

        if (generalConfig.get(READERPORT) instanceof Integer) {
           readerPort = (int) generalConfig.get(READERPORT);
        } else {
           log.error("Configuration failed. No RFID Reader Port was specified");
           return false;
        }

        if (generalConfig.get(TAGREAD_INTERVAL) instanceof Integer) {
           tagReadInterval = (int) generalConfig.get(TAGREAD_INTERVAL);
        } else {
           log.error("Configuration for "+TAGREAD_INTERVAL+" not found, using "
                   +DEFAULT_TAGREAD_INTERVAL+" (milliseconds)");
           tagReadInterval = DEFAULT_TAGREAD_INTERVAL;
        }

        if (generalConfig.get(LOG_LEVEL) instanceof String) {
            logLevel = (String) generalConfig.get(LOG_LEVEL);
        } else {
            log.error("Configuration for "+LOG_LEVEL+" not found, using "
                    +DEFAULT_LOG_LEVEL);
            logLevel = DEFAULT_LOG_LEVEL;
        }

        // Initialize LLRP Connector with config values
        try {
           if (core.llrp != null) {
               core.llrp.close();
           }
           LLRPConnector llrp = new LLRPConnector();
           llrp.setupLLRPConnector(core, hostname, readerPort, tagReadInterval, logLevel);
           core.llrp = llrp;
        } catch (VantiqLLRPException | IOException e) {
           log.error("Configuration failed. Exception occurred while setting up LLRP Connector: {}", e);
           System.out.println("Configuration failed. Exception occurred while setting up LLRP Connector: " + e.getStackTrace());
           return false;
        }

        log.trace("LLRP Connector created");
        System.out.println("LLRP Connector created");
        return true;
    }

    /**
     * Closes the source {@link LLRPConnectorCore} and marks the configuration as completed. The source will
     * be reactivated when the source reconnects, due either to a Reconnect message (likely created by an update to the
     * configuration document) or to the WebSocket connection crashing momentarily.
     */
    private void failConfig() {
        core.close();
        configComplete = true;
    }

    /**
     * Returns whether the configuration handler has completed. Necessary since the sourceConnectionFuture is completed
     * before the configuration can complete, so a program may need to wait before using configured resources.
     * @return  true when the configuration has completed (successfully or not), false otherwise
     */
    public boolean isComplete() {
        return configComplete;
    }
}