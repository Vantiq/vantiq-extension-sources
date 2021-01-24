/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    boolean asynchronousProcessing = false;

    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    // Constants for getting config options
    private static final String CONFIG = "config";
    private static final String CSVCONFIG = "csvConfig";
    private static final String OPTIONS = "options";
    
    private static final String SCHEMA = "schema";
    private static final String FILE_FOLDER_PATH = "fileFolderPath";
    private static final String FILE_PREFIX = "filePrefix";
    private static final String FILE_EXTENSION = "fileExtension";
    private static final String MAX_LINES_IN_EVENT = "maxLinesInEvent";
    private static final String ASYNCH_PROCESSING = "asynchronousProcessing";
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
     * Method used to create the query and publish handlers
     * 
     * @param generalConfig The general configuration of the EasyModbus Source
     * @return Returns the maximum pool size, equal to twice the number of active
     *         tasks. If default active tasks is used, then returns 0.
     */
    private int createQueryAndPublishHandlers(Map<String, ?> generalConfig) {
        int maxPoolSize = 0;

        // Checking if asynchronous processing was specified in the general
        // configuration
        if (generalConfig.get(ASYNCH_PROCESSING) instanceof Boolean && (Boolean) generalConfig.get(ASYNCH_PROCESSING)) {
            asynchronousProcessing = true;
            int maxActiveTasks = MAX_ACTIVE_TASKS;
            int maxQueuedTasks = MAX_QUEUED_TASKS;

            if (generalConfig.get(MAX_ACTIVE) instanceof Integer && (Integer) generalConfig.get(MAX_ACTIVE) > 0) {
                maxActiveTasks = (Integer) generalConfig.get(MAX_ACTIVE);
            }

            if (generalConfig.get(MAX_QUEUED) instanceof Integer && (Integer) generalConfig.get(MAX_QUEUED) > 0) {
                maxQueuedTasks = (Integer) generalConfig.get(MAX_QUEUED);
            }

            // Used to set the max pool size for connection pool
            maxPoolSize = maxActiveTasks;

            // Creating the thread pool executors with Queue
            source.queryPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(maxQueuedTasks));
            source.publishPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

            // Creating query/publish handlers with asynchronous processing
            queryHandler = new Handler<ExtensionServiceMessage>() {
                @Override
                public void handleMessage(ExtensionServiceMessage message) {
                    try {
                        source.queryPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                handleQueryRequest(source.client, message);
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        log.error(
                                "The queue of tasks has filled, and as a result the request was unable to be processed.",
                                e);
                        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                        source.client.sendQueryError(replyAddress,
                                "io.vantiq.extsrc.EasyModbusHandleConfiguration.queryHandler.queuedTasksFull",
                                "The queue of tasks has filled, and as a result the request was unable to be processed.",
                                null);
                    }
                }
            };
        } else {
            // Otherwise, creating query/publish handlers with synchronous processing
            queryHandler = new Handler<ExtensionServiceMessage>() {
                @Override
                public void handleMessage(ExtensionServiceMessage message) {
                    handleQueryRequest(source.client, message);
                }
            };
       
        }

        return maxPoolSize;
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

        log.error("Enter createCSVConnection");

        if ( !(config.get(MAX_LINES_IN_EVENT) instanceof Integer)) {
            log.error("Configuration failed. No maxLinesInEvents was specified or it is not Integer");
            return false;
        }

        // Creating the publish and query handlers
        int maxPoolSize = createQueryAndPublishHandlers(config);

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

        // Start listening for queries and publishes
        source.client.setQueryHandler(queryHandler);
        source.client.setPublishHandler(publishHandler);
        
        log.trace("CSV source created");
        return true;
    }
    /**
     * Method called by the query handler to process the request
     * 
     * @param client  The ExtensionWebSocketClient used to send a query response
     *                error if necessary
     * @param message The message sent to the Extension Source
     */
    private void handleQueryRequest(ExtensionWebSocketClient client, ExtensionServiceMessage message) {
        // Should never happen, but just in case something changes in the backend
        if (!(message.getObject() instanceof Map)) {
            String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
            client.sendQueryError(replyAddress, "io.vantiq.extsrc.EasyModbusHandleConfiguration.invalidQueryRequest",
                    "Request must be a map", null);
        }

        // Process query and send the results
        source.executeQuery(message);
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
