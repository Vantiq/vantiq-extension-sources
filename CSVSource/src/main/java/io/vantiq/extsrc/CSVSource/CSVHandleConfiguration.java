/*
 * Copyright (c) 2018 Vantiq, Inc.
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
 * 
 * The options for general are as follows. At least one must be valid for the source to function:
 * <ul>
 *      <li>{@code username}: The username to log into the SQL Database.
 *      <li>{@code password}: The password to log into the SQL Database.
 *      <li>{@code dbURL}: The URL of the SQL Database to be used. *                      
 * </ul>
 */

public class CSVHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    CSVCore                 source;
    boolean                 configComplete = false; // Not currently used
    boolean                 asynchronousProcessing = false;
    
        
    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    // Constants for getting config options
    private static final String CONFIG = "config";

    private static final String FILE_FOLDER_PATH = "fileFolderPath";
    private static final String FILE_PREFIX = "filePrefix";
    private static final String FILE_EXTENSION = "fileExtension";
    private static final String MAX_LINES_IN_EVENT = "maxLinesInEvent";
    private static final String MAX_ACTIVE = "maxActiveTasks";
    private static final String MAX_QUEUED = "maxQueuedTasks";
    


    public CSVHandleConfiguration(CSVCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the CSV Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, String> schema;
        Map<String, Object> csvConfig;
        Map<String, Object> general;
        String fileFolderPath ; 
        String filePrefix; 
        String fileExtension;
        
        // Obtain entire config from the message object
        if ( !(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for CSV Source.");
            failConfig();
            return;
        }
        config = (Map) configObject.get(CONFIG);
        
        // Retrieve the csvConfig and the vantiq config
        if ( !(config.get(FILE_FOLDER_PATH) instanceof String && config.get(FILE_EXTENSION) instanceof String) ) {
                log.error("Configuration failed. Configuration must contain 'fileFolderPath' and 'filePrefix' fields.");
            failConfig();
            return;
        }
        
        fileFolderPath = (String) config.get(FILE_FOLDER_PATH);
        filePrefix = "";
        if (config.get(FILE_PREFIX) != null)
        {
            filePrefix = (String) config.get(FILE_PREFIX);
        }
        fileExtension = (String) config.get(FILE_EXTENSION);


        schema = (Map<String, String>) config.get("schema");
        

        String fullFilePath = String.format("%s/%s*.%s",fileFolderPath,filePrefix,fileExtension);

        boolean success = createCSVConnection(config ,fileFolderPath,fullFilePath,source.client);
        if (!success) {
            failConfig();
            return;
        }
        
        log.trace("Setup complete");
        configComplete = true;
    }
    
    
     boolean createCSVConnection(Map<String, Object> config ,String FileFolderPath,String fullFilePath,ExtensionWebSocketClient oClient) {
        
        int size = 1;

        if (config.get(MAX_LINES_IN_EVENT) instanceof Integer) {
                size = (int) config.get(MAX_LINES_IN_EVENT);
        } else {
            log.error("Configuration failed. No maxLinesInEvents was specified");
            return false;
        }
        

        // Initialize CSV Source with config values
        try {
            if (source.csv != null) {
                source.csv.close();
            }
            CSV csv = new CSV();
       
            csv.setupCSV(oClient,FileFolderPath,fullFilePath,config,asynchronousProcessing);
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
//        source.close();
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
