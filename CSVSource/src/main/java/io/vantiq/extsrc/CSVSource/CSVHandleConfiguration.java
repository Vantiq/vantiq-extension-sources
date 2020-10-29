/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;

/**
 * Sets up the source using the configuration document, which looks as below.
 *<pre> {
 *      csvConfig: {
 *          general: {
 *              &lt;general options&gt;
 *          }
 *      }
 * }</pre>
 */
public class CSVHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    CSVCore                 source;
    boolean                 configComplete = false; // Used for autotestign support.
        
    // Constants for getting config options
    private static final String CONFIG = "config";
    private static final String CSVCONFIG = "csvConfig";
    private static final String OPTIONS = "options";
    
    private static final String SCHEMA = "schema";
    private static final String FILE_FOLDER_PATH = "fileFolderPath";
    private static final String FILE_PREFIX = "filePrefix";
    private static final String FILE_EXTENSION = "fileExtension";
    private static final String MAX_LINES_IN_EVENT = "maxLinesInEvent";

    public CSVHandleConfiguration(CSVCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the CSV Source.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map<String, Object>) message.getObject();
        Map<String, Object> config;
        Map<String, Object> options;
        Map<String, Object> csvConfig;
        String fileFolderPath ; 
        String filePrefix; 
        String fileExtension;

        // Obtain entire config from the message object
        if ( !(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for CSV Source.");
            failConfig();
            return;
        }

        config = (Map<String,Object>) configObject.get(CONFIG);

        if ( !(config.get(OPTIONS) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for CSV Source: No OPTIONS ");
            failConfig();
            return;
        }
        options = (Map<String,Object>) config.get(OPTIONS);

        if ( !(config.get(CSVCONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for CSV Source: No CSVConfig");
            failConfig();
            return;
        }

        csvConfig = (Map<String,Object>) config.get(CSVCONFIG);

        // Retrieve the csvConfig and the vantiq config
        if ( !(csvConfig.get(FILE_FOLDER_PATH) instanceof String 
                && csvConfig.get(FILE_EXTENSION) instanceof String 
                && csvConfig.get(FILE_PREFIX) instanceof String) ) {
                log.error("Configuration failed. Configuration must contain 'fileFolderPath' , 'filePrefix' and 'fileExtension' fields.");
            failConfig();
            return;
        }
        fileFolderPath = (String) csvConfig.get(FILE_FOLDER_PATH);
        filePrefix = "";
        if (csvConfig.get(FILE_PREFIX) != null) {
            filePrefix = (String) csvConfig.get(FILE_PREFIX);
        }
        fileExtension = (String) csvConfig.get(FILE_EXTENSION);

        if ( !(csvConfig.get(SCHEMA) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for CSV Source. )NO SCHEMA");
            failConfig();
            return;
        }

        String fullFilePath = String.format("%s/%s*.%s", fileFolderPath, filePrefix, fileExtension);

        boolean success = createCSVConnection(csvConfig, options, fileFolderPath, fullFilePath, source.client);
        if (!success) {
            failConfig();
            return;
        }
        
        log.trace("Setup complete");
        configComplete = true;
    }

    /**
     * implement Singleton for CSV class
     * @param config
     * @param options
     * @param FileFolderPath
     * @param fullFilePath
     * @param oClient
     * @return
     */
    boolean createCSVConnection(Map<String, Object> config, Map<String, Object> options , String FileFolderPath, String fullFilePath, ExtensionWebSocketClient oClient) {
        if ( !(config.get(MAX_LINES_IN_EVENT) instanceof Integer)) {
            log.error("Configuration failed. No maxLinesInEvents was specified or it is not Integer");
            return false;
        }

        // Initialize CSV Source with config values
        try {
            if (source.csv != null) {
                source.csv.close();
            }
            CSV csv = new CSV();
       
            csv.setupCSV(oClient, FileFolderPath, fullFilePath, config, options);
            source.csv = csv; 
        } catch (Exception e) {
            log.error("Configuration failed. Exception occurred while setting up CSV Source: ", e);
            return false;
        }
        
        log.trace("CSV source created");
        return true;
    }

    /**
     * Closes the source {@link CSVCore} and marks the configuration as completed. The source will
     * be reactivated when the source reconnects, due either to a Reconnect message (likely created by an update to the
     * configuration document) or to the WebSocket connection crashing momentarily.
     */
    private void failConfig() {
        source.close();
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
