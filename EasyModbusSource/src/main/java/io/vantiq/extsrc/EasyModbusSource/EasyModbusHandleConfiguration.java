/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

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

/**
 * Sets up the source using the configuration document, which looks as below.
 * 
 * <pre>
 *  {
 *      "easyModbusConfig":{
 *   	    "general": {
 *               "TCPAddress": "127.0.0.1",
 *               "TCPPort": 502,
 *               "Size": 20,
 *               "pollTime": 1000,
 *               "pollQuery": "select * from coils"
 *           }
 *	    }
 *  }
 * </pre>
 */
public class EasyModbusHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger log;
    String sourceName;
    EasyModbusCore source;
    boolean configComplete = false; // Not currently used
    boolean asynchronousProcessing = false;

    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    // Constants for getting config options
    private static final String CONFIG = "config";

    private static final String EASYMODBUS_CONFIG = "easyModBus";
    private static final String GENERAL_CONFIG = "general";
    private static final String TCPADDRESS_CONFIG = "TCPAddress";
    private static final String TCPPORT_CONFIG = "TCPPort";
    private static final String BUFFER_SIZE = "Size";
    private static final String POLL_TIME = "pollTime";
    private static final String POLL_QUERY = "pollQuery";
    private static final String ASYNCH_PROCESSING = "asynchronousProcessing";
    private static final String MAX_ACTIVE = "maxActiveTasks";
    private static final String MAX_QUEUED = "maxQueuedTasks";

    public EasyModbusHandleConfiguration(EasyModbusCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }

    /**
     * Interprets the configuration message sent by the Vantiq server and sets up
     * the EasyModbus Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, Object> vantiq;
        Map<String, Object> easyModbusConfig;
        Map<String, Object> general;
        String tcpAddress;
        int tcpPort;

        // Obtain entire config from the message object
        if (!(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for EasyModbus Source.");
            failConfig();
            return;
        }
        config = (Map) configObject.get(CONFIG);

        if (!(config.get(EASYMODBUS_CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for EasyModbus Source.(easyModbusConfig)");
            failConfig();
            return;
        }
        easyModbusConfig = (Map) config.get(EASYMODBUS_CONFIG);

        if (!(easyModbusConfig.get(GENERAL_CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for EasyModbus Source.(general)");
            failConfig();
            return;
        }
        general = (Map) easyModbusConfig.get(GENERAL_CONFIG);

        // Retrieve the easyModbusConfig and the vantiq config
        if (!(general.get(TCPADDRESS_CONFIG) instanceof String && general.get(TCPPORT_CONFIG) instanceof Integer)) {
            log.error(
                    "Configuration failed. Configuration (easyModbusConfig.general) must contain 'TCPAddress' and 'TCPPort' fields.");
            failConfig();
            return;
        }

        tcpAddress = (String) general.get(TCPADDRESS_CONFIG);
        tcpPort = (int) general.get(TCPPORT_CONFIG);

        boolean success = createEasyModbusConnection(general, tcpAddress, tcpPort);
        if (!success) {
            failConfig();
            return;
        }

        log.trace("Setup complete");
        configComplete = true;
    }

    /**
     * Attempts to create the EasyModbus Source based on the configuration document.
     *
     * @param config     The configuration for the EasyModbus Source
     * @param tcpAddress Ip Address of the EasyModbus server
     * @param TcpPort    Port of the EasyModbus server
     * @return true if the EasyModbus source could be created, false otherwise
     */
    boolean createEasyModbusConnection(Map<String, Object> config, String tcpAddress, int TcpPort) {
        int size ;

        if (config.get(BUFFER_SIZE) instanceof Integer) {
            size = (int) config.get(BUFFER_SIZE);
        } else {
            log.error("Configuration failed. No Size was specified");
            return false;
        }

        // Creating the publish and query handlers
        int maxPoolSize = createQueryAndPublishHandlers(config);

        // Initialize EasyModbus Source with config values
        try {
            if (source.easyModbus != null) {
                source.easyModbus.close();
            }
            EasyModbus easyModbus = new EasyModbus();

            easyModbus.setupEasyModbus(tcpAddress, TcpPort, asynchronousProcessing, maxPoolSize);
            source.easyModbus = easyModbus;
        } catch (Exception e) {
            log.error("Configuration failed. Exception occurred while setting up EASYModbus Source: ", e);
            return false;
        }

        // Create polling query if specified
        if (config.get(POLL_TIME) instanceof Integer) {
            if (config.get(POLL_QUERY) instanceof String) {
                int pollTime = (Integer) config.get(POLL_TIME);
                if (pollTime > 0) {
                    String pollQuery = (String) config.get(POLL_QUERY);
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

        log.trace("EasyModbus source created");
        return true;
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
                        log.error(
                                "The queue of tasks has filled, and as a result the request was unable to be processed.",
                                e);
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

        return maxPoolSize;
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
     * Closes the source {@link EasyModbusCore} and marks the configuration as
     * completed. The source will be reactivated when the source reconnects, due
     * either to a Reconnect message (likely created by an update to the
     * configuration document) or to the WebSocket connection crashing momentarily.
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
