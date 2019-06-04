/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

/**
 * Sets up the source using the configuration document, which looks as below.
 *<pre> {
 *      jdbcConfig: {
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

public class JDBCHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    JDBCCore                source;
    boolean                 configComplete = false; // Not currently used
    boolean                 asynchronousProcessing = false;
        
    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    private static final int MAX_RUNNING_THREADS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    // Constants for getting config options
    private static final String CONFIG = "config";
    private static final String JDBC_CONFIG = "jdbcConfig";
    private static final String VANTIQ = "vantiq";
    private static final String GENERAL = "general";
    private static final String PACKAGE_ROWS = "packageRows";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String DB_URL = "dbURL";
    private static final String POLL_TIME = "pollTime";
    private static final String POLL_QUERY = "pollQuery";
    private static final String ASYNCH_PROCESSING = "asynchronousProcessing";
    private static final String MAX_RUNNING = "maxRunningThreads";
    private static final String MAX_QUEUED = "maxQueuedTasks";

    public JDBCHandleConfiguration(JDBCCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the JDBC Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, Object> vantiq;
        Map<String, Object> jdbcConfig;
        Map<String, Object> general;
        
        // Obtain entire config from the message object
        if ( !(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for JDBC Source.");
            failConfig();
            return;
        }
        config = (Map) configObject.get(CONFIG);
        
        // Retrieve the jdbcConfig and the vantiq config
        if ( !(config.get(JDBC_CONFIG) instanceof Map && config.get(VANTIQ) instanceof Map) ) {
            log.error("Configuration failed. Configuration must contain 'jdbcConfig' and 'vantiq' fields.");
            failConfig();
            return;
        }
        jdbcConfig = (Map) config.get(JDBC_CONFIG);
        vantiq = (Map) config.get(VANTIQ);
        
        // Get the general options from the jdbcConfig
        if ( !(jdbcConfig.get(GENERAL) instanceof Map)) {
            log.error("Configuration failed. No general options specified.");
            failConfig();
            return;
        }
        general = (Map) jdbcConfig.get(GENERAL);
        
        boolean success = createDBConnection(general, vantiq);
        if (!success) {
            failConfig();
            return;
        }
        
        log.trace("Setup complete");
        configComplete = true;
    }
    
    /**
     * Attempts to create the JDBC Source based on the configuration document.
     * @param generalConfig     The general configuration for the JDBC Source
     * @param vantiq            The vantiq configuration for the JDBC Source
     * @return                  true if the JDBC source could be created, false otherwise
     */
    boolean createDBConnection(Map<String, ?> generalConfig, Map<String, ?> vantiq) {
        // Ensuring that packageRows is set to be true, and failing the configuration otherwise
        if (!(vantiq.get(PACKAGE_ROWS) instanceof String) || !(vantiq.get(PACKAGE_ROWS).toString().equalsIgnoreCase("true"))) {
            log.error("Configuration failed. The packageRows field must be set to true.");
            return false;
        }
        
        // Get Username/Password, DB URL, and DB Driver
        String username;
        String password;
        String dbURL;
        
        if (generalConfig.get(USERNAME) instanceof String) {
            username = (String) generalConfig.get(USERNAME);
        } else {
            log.error("Configuration failed. No db username was specified");
            return false;
        }
        
        if (generalConfig.get(PASSWORD) instanceof String) {
            password = (String) generalConfig.get(PASSWORD);
        } else {
            log.error("Configuration failed. No db password was specified");
            return false;
        }
        
        if (generalConfig.get(DB_URL) instanceof String) {
            dbURL = (String) generalConfig.get(DB_URL);
        } else {
            log.error("Configuration failed. No db URL was specified");
            return false;
        }

        // Creating the publish and query handlers
        createQueryAndPublishHandlers(generalConfig);
        
        // Initialize JDBC Source with config values
        try {
            if (source.jdbc != null) {
                source.jdbc.close();
            }
            JDBC jdbc = new JDBC();
            jdbc.setupJDBC(dbURL, username, password, asynchronousProcessing);
            source.jdbc = jdbc; 
        } catch (VantiqSQLException e) {
            log.error("Configuration failed. Exception occurred while setting up JDBC Source: ", e);
            return false;
        }
        
        // Create polling query if specified
        if (generalConfig.get(POLL_TIME) instanceof Integer) {
            if (generalConfig.get(POLL_QUERY) instanceof String) {
                int pollTime = (Integer) generalConfig.get(POLL_TIME);
                if (pollTime > 0) {
                    String pollQuery = (String) generalConfig.get(POLL_QUERY);
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            source.executePolling(pollQuery);
                        }
                    };
                    // Create new Timer, and schedule the task according to the pollTime
                    source.pollTimer = new Timer("executePolling");
                    source.pollTimer.schedule(task, 0, pollTime);
                } else {
                    log.error("Poll time must be greater than 0.");
                }
            } else {
                log.error("A pollQuery must be specified along with the pollTime.");
            }
            
        }
        
        // Start listening for queries and publishes
        source.client.setQueryHandler(queryHandler);
        source.client.setPublishHandler(publishHandler);
        
        log.trace("JDBC source created");
        return true;
    }

    /**
     * Method used to create the query and publish handlers
     * @param generalConfig     The general configuration of the JDBC Source
     */
    private void createQueryAndPublishHandlers(Map<String, ?> generalConfig) {
        // Checking if asynchronous processing was specified in the general configuration
        if (generalConfig.get(ASYNCH_PROCESSING) instanceof Boolean && (Boolean) generalConfig.get(ASYNCH_PROCESSING)) {
            asynchronousProcessing = true;
            int maxRunningThreads = MAX_RUNNING_THREADS;
            int maxQueuedTasks = MAX_QUEUED_TASKS;

            if (generalConfig.get(MAX_RUNNING) instanceof Integer && (Integer) generalConfig.get(MAX_RUNNING) > 0) {
                maxRunningThreads = (Integer) generalConfig.get(MAX_RUNNING);
            }

            if (generalConfig.get(MAX_QUEUED) instanceof Integer && (Integer) generalConfig.get(MAX_QUEUED) > 0) {
                maxQueuedTasks = (Integer) generalConfig.get(MAX_QUEUED);
            }

            // Creating the thread pool executors
            source.queryPool = new ThreadPoolExecutor(maxRunningThreads, maxRunningThreads, 0l, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(maxQueuedTasks));
            source.publishPool = new ThreadPoolExecutor(maxRunningThreads, maxRunningThreads, 0l, TimeUnit.MILLISECONDS,
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
                        log.error("The queue of tasks has filled, and as a result the request was unable to be processed.", e);
                        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                        source.client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBCHandleConfiguration.queryHandler.queuedTasksFull",
                                "The queue of tasks has filled, and as a result the request was unable to be processed.", null);
                    }
                }
            };
            publishHandler = new Handler<ExtensionServiceMessage>() {
                @Override
                public void handleMessage(ExtensionServiceMessage message) {
                    try {
                        source.publishPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                source.executePublish(message);
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        log.error("The queue of tasks has filled, and as a result the request was unable to be processed.", e);
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
            publishHandler = new Handler<ExtensionServiceMessage>() {
                @Override
                public void handleMessage(ExtensionServiceMessage message) {
                    source.executePublish(message);
                }
            };
        }
    }

    /**
     * Method called by the query handler to process the request
     * @param client    The ExtensionWebSocketClient used to send a query response error if necessary
     */
    private void handleQueryRequest(ExtensionWebSocketClient client, ExtensionServiceMessage message) {
        // Should never happen, but just in case something changes in the backend
        if ( !(message.getObject() instanceof Map) ) {
            String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
            client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBCHandleConfiguration.invalidQueryRequest",
                    "Request must be a map", null);
        }

        // Process query and send the results
        source.executeQuery(message);
    }
    
    /**
     * Closes the source {@link JDBCCore} and marks the configuration as completed. The source will
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
