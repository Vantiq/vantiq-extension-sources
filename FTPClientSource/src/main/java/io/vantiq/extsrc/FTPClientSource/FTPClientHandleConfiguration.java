/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

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
 * 
 * <pre>
 *  {
 *      FTPClientConfig: {
 *          general: {
 *              &lt;general options&gt;
 *          }
 *      }
 * }
 * </pre>
 */
public class FTPClientHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger log;
    String sourceName;
    FTPClientCore source;
    boolean configComplete = false; // Used for autotesting support.
    boolean asynchronousProcessing = false;

    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    // Constants for getting config options
    private static final String CONFIG = "config";
    public static final String FTPClientCONFIG = "ftpClientConfig";
    private static final String OPTIONS = "options";

    public static final String DELETE_AFTER_PROCCESING_KEYWORD = "deleteAfterProcessing";
    public static final String DELETE_AFTER_DOWNLOAD = "deleteAfterDownload";
    public static final String REMOTE_FOLDER_PATH = "remoteFolderPath";
    public static final String LOCAL_FOLDER_PATH = "localFolderPath";
    public static final String BASE_DOCUMENT_PATH = "baseDocumentPath";
    public static final String SERVER_LIST = "servers";
    public static final String SERVER_NAME = "name";
    public static final String SERVER_ENABLE = "enable";
    public static final String ADD_PRRFIX_TO_DOWNLOAD = "addPrefixToDownload";
    public static final String SERVER_IP = "server";
    public static final String SERVER_PORT = "port";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String AGE_IN_DAYS_KEYWORD = "ageInDays";
    public static final String CONNECT_TIMEOUT ="connectTimeout"; 

    private static final String ASYNCH_PROCESSING = "asynchronousProcessing";
    private static final String MAX_ACTIVE = "maxActiveTasks";
    private static final String MAX_QUEUED = "maxQueuedTasks";

    public FTPClientHandleConfiguration(FTPClientCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }

    /**
     * Interprets the configuration message sent by the Vantiq server and sets up
     * the FTPClient Source.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map<String, Object>) message.getObject();
        Map<String, Object> config;
        Map<String, Object> options;
        Map<String, Object> FTPClientConfig;

        // Obtain entire config from the message object
        if (!(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for FTPClient Source.");
            failConfig();
            return;
        }

        config = (Map<String, Object>) configObject.get(CONFIG);

        if (!(config.get(OPTIONS) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for FTPClient Source: No OPTIONS ");
            failConfig();
            return;
        }
        options = (Map<String, Object>) config.get(OPTIONS);

        if (!(config.get(FTPClientCONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for FTPClient Source: No FTPClientConfig");
            failConfig();
            return;
        }

        FTPClientConfig = (Map<String, Object>) config.get(FTPClientCONFIG);

        // Retrieve the FTPClientConfig and the vantiq config
        if (!(FTPClientConfig.get(SERVER_IP) instanceof String && FTPClientConfig.get(SERVER_PORT) instanceof Integer
            && FTPClientConfig.get(USERNAME) instanceof String && FTPClientConfig.get(PASSWORD) instanceof String
            && FTPClientConfig.get(AGE_IN_DAYS_KEYWORD) instanceof Integer  && FTPClientConfig.get(CONNECT_TIMEOUT) instanceof Integer
            && FTPClientConfig.get(LOCAL_FOLDER_PATH) instanceof String && FTPClientConfig.get(REMOTE_FOLDER_PATH) instanceof String
            && FTPClientConfig.get(ADD_PRRFIX_TO_DOWNLOAD) instanceof Boolean)) {
                log.error(
                    "Configuration failed. Configuration must contain 'server', 'port', 'username', 'password', 'AgeInDays', 'connetTimeout', 'remoteFolderPath', 'addPrefixToDownload' and 'localFolderPath' fields.");
            failConfig();
            return;
        }

        boolean success = createFTPClientConnection(FTPClientConfig, options, source.client);
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
     * @param generalConfig The general configuration of the FTPClient Source
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
                                "io.vantiq.extsrc.FTPClientHandleConfiguration.queryHandler.queuedTasksFull",
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
     * implement Singleton for FTPClient class
     * 
     * @param config
     * @param options
     * @param FileFolderPath
     * @param fullFilePath
     * @param oClient
     * @return
     */
    boolean createFTPClientConnection(Map<String, Object> config, Map<String, Object> options, ExtensionWebSocketClient oClient) {

        // Creating the publish and query handlers
        int maxPoolSize = createQueryAndPublishHandlers(config);

        // Initialize FTPClient Source with config values
        try {
            if (source.FTPClient != null) {
                source.FTPClient.close();
            }
            FTPClient FTPClient = new FTPClient();

            FTPClient.setupFTPClient(oClient, config, options);
            source.FTPClient = FTPClient;
        } catch (Exception e) {
            log.error("Configuration failed. Exception occurred while setting up FTPClient Source: ", e);
            return false;
        }

        // Start listening for queries and publishes
        source.client.setQueryHandler(queryHandler);
        source.client.setPublishHandler(publishHandler);

        log.trace("FTPClient source created");
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
            client.sendQueryError(replyAddress, "io.vantiq.extsrc.FTPClientHandleConfiguration.invalidQueryRequest",
                    "Request must be a map", null);
        }

        // Process query and send the results
        source.executeQuery(message);
    }

    /**
     * Closes the source {@link FTPClientCore} and marks the configuration as completed.
     * The source will be reactivated when the source reconnects, due either to a
     * Reconnect message (likely created by an update to the configuration document)
     * or to the WebSocket connection crashing momentarily.
     */
    private void failConfig() {
        source.close();
        configComplete = true;
    }

    /**
     * Returns whether the configuration handler has completed. Necessary since the
     * sourceConnectionFuture is completed before the configuration can complete, so
     * a program may need to wait before using configured resources.
     * 
     * @return true when the configuration has completed (successfully or not),
     *         false otherwise
     */
    public boolean isComplete() {
        return configComplete;
    }
}
