/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import java.util.Map;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;

/**
 * Sets up the source using the configuration document, which looks as below.
 * 
 * <pre>
 *  {
 *      hikVisionConfig: {
 *          general: {
 *              &lt;general options&gt;
 *          }
 *      }
 * }
 * </pre>
 * 
 * The options for general are as follows. At least one must be valid for the
 * source to function:
 * <ul>
 * <li>{@code username}: The username to log into the SQL Database.
 * <li>{@code password}: The password to log into the SQL Database.
 * <li>{@code dbURL}: The URL of the SQL Database to be used. *
 * </ul>
 */

public class HikVisionHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger log;
    String sourceName;
    HikVisionCore source;
    boolean configComplete = false; // Not currently used
    boolean asynchronousProcessing = false;

    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    // Constants for getting config options
    private static final String OPTIONS = "options";
    private static final String CONFIG = "config";
    private static final String CAMERAS = "cameras";
    private static final String GENERAL = "general";

    private static final String SDK_LOG_FOLDER_PATH = "sdkLogPath";
    String sdkLogPath = "c:/tmp";
    private static final String DRV_IMAGE_FOLDER_PATH = "DVRImageFolderPath";
    String dvrImageFolderPath = "c:/tmp";
    private static final String VANTIQ_DOCUMENT_PATH = "VantiqDocumentPath";
    String vantiqDocumentPath = "public/image";
    private static final String VANTIQ_RESOURCE_PATH = "VantiqResourcePath";
    String vantiqResourcePath = "/resources/documents";

    private static final String MAX_ACTIVE = "maxActiveTasks";
    private static final String MAX_QUEUED = "maxQueuedTasks";
    private static final String ASYNCH_PROCESSING = "asynchronousProcessing";

    public HikVisionHandleConfiguration(HikVisionCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }

    /**
     * Interprets the configuration message sent by the Vantiq server and sets up
     * the HikVision Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, String> general;
        Map<String, Object> options;
        List<Map<String, Object>> cameras;
        List<CameraEntry> cameraList;

        // Obtain entire config from the message object
        if (!(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for HikVision Source.");
            failConfig();
            return;
        }
        config = (Map) configObject.get(CONFIG);

        if (!(config.get(OPTIONS) instanceof Map)) {
            log.error("Configuration failed. No OPTIONS in configuration for HikVision Source.");
            failConfig();
            return;
        }

        options = (Map) config.get(OPTIONS);
        if (options.size() == 0) {
            log.error("Configuration failed. No 'options' in configuration for HikVision Source.");
            failConfig();
            return;
        }

        if (config.get(CAMERAS) == null) {
            log.error("Configuration failed. Configuration must contain 'cameras' for list of cameras");
            failConfig();
            return;
        }

        cameras = (List<Map<String, Object>>) config.get(CAMERAS);

        if (cameras.size() < 1) {
            log.error("Configuration failed. Configuration must contain at least 1 entry in 'cameras'");
            failConfig();
            return;
        }

        cameraList = new ArrayList<>();

        // cameraList = (List<CameraEntry>) cameras.toArray();

        int numEnabledCamera = 0;
        for (int i = 0; i < cameras.size(); i++) {
            Map<String, Object> o = cameras.get(i);
            Boolean Enable = Boolean.parseBoolean((String) o.get("Enable"));
            if (Enable) {
                numEnabledCamera++;
                cameraList.add(new CameraEntry(cameras.get(i), numEnabledCamera));
            }
        }

        if (numEnabledCamera == 0) {
            log.error("Configuration failed. Atleast One camera in 'enable' in 'cameras'");
            failConfig();
            return;

        }
        /*
         * private static final String SDK_LOG_FOLDER_PATH = "sdkLogPath"; String
         * sdkLogPath = "c:/tmp";
         * 
         * private static final String DRV_IMAGE_FOLDER_PATH = "DVRImageFolderPath";
         * private static final String VANTIQ_DOCUMENT_PATH = "VantiqDocumentPath";
         */
        // Retrieve the hikVisionConfig and the vantiq config

        if (!(config.get(GENERAL) instanceof Map)) {
            log.error(
                    "Configuration failed. Configuration must contain 'general' for infiormation about the extension source");
            failConfig();
            return;
        }

        general = (Map) config.get(GENERAL);

        if (!(general.get(SDK_LOG_FOLDER_PATH) instanceof String)) {
            log.warn("Configuration doesn't include {} . User default value {}", SDK_LOG_FOLDER_PATH, sdkLogPath);
            general.put(SDK_LOG_FOLDER_PATH, sdkLogPath);
        } else
            sdkLogPath = (String) general.get(SDK_LOG_FOLDER_PATH);

        File f = new File(sdkLogPath);
        if (!f.isDirectory()) {
            log.error("(sdkLogPath) folder () is not exist", sdkLogPath);
            failConfig();
            return;
        }

        if (!(general.get(DRV_IMAGE_FOLDER_PATH) instanceof String)) {
            log.warn("Configuration doesn't include {} . User default value {}", DRV_IMAGE_FOLDER_PATH,
                    dvrImageFolderPath);
            general.put(DRV_IMAGE_FOLDER_PATH, dvrImageFolderPath);
        } else
            dvrImageFolderPath = (String) general.get(DRV_IMAGE_FOLDER_PATH);

        File f1 = new File(sdkLogPath);
        if (!f1.isDirectory()) {
            log.error("(dvrImageFolderPath) folder () is not exist", sdkLogPath);
            failConfig();
            return;
        }

        if (!(general.get(VANTIQ_DOCUMENT_PATH) instanceof String)) {
            log.warn("Configuration doesn't include {} . User default value {}", VANTIQ_DOCUMENT_PATH,
                    vantiqDocumentPath);
            general.put(VANTIQ_DOCUMENT_PATH, vantiqDocumentPath);
        } else
            vantiqDocumentPath = (String) general.get(VANTIQ_DOCUMENT_PATH);

        if (!(general.get(VANTIQ_RESOURCE_PATH) instanceof String)) {
            log.warn("Configuration doesn't include {} . User default value {}", VANTIQ_RESOURCE_PATH,
                    vantiqResourcePath);
            general.put(VANTIQ_RESOURCE_PATH, vantiqResourcePath);
        } else
            vantiqResourcePath = (String) general.get(VANTIQ_RESOURCE_PATH);

        config.put("vantiqServer", source.targetVantiqServer);
        config.put("authToken", source.authToken);
        config.put("cameraList", cameraList);

        boolean success = createHikVisionConnection(config, source.client);
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
    private int createQueryAndPublishHandlers(Map<String, ?> generalConfig, ExtensionWebSocketClient oClient) {
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
            maxPoolSize = 2 * maxActiveTasks;

            // Creating the thread pool executors with Queue
            source.publishPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

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
            publishHandler = new Handler<ExtensionServiceMessage>() {
                @Override
                public void handleMessage(ExtensionServiceMessage message) {
                    source.executePublish(message);
                }
            };
        }

        return maxPoolSize;
    }

    boolean createHikVisionConnection(Map<String, Object> config, ExtensionWebSocketClient oClient) {

        Map<String,Object> general = (Map) config.get(OPTIONS);

        Boolean isInTest = false;
        if (general.get("IsInTest") != null) {
            isInTest = Boolean.parseBoolean((String) general.get("IsInTest"));
        }

        int maxPoolSize = createQueryAndPublishHandlers(general, oClient);

        // Initialize HikVision Source with config values
        try {
            if (source.hikVision != null) {
                source.hikVision.close();
            }
            HikVision hikVision = new HikVision();

            hikVision.setupHikVision(oClient, config, asynchronousProcessing, isInTest);

            source.hikVision = hikVision;
        } catch (Exception e) {
            log.error("Configuration failed. Exception occurred while setting up HikVision Source: ", e);
            return false;
        }

        if (source.client != null) {
            source.client.setPublishHandler(publishHandler);
        } else {
            log.error(
                    "Configuration failed. Exception occurred while setting up HikVision Source: source.client == null");
        }

        log.trace("HikVision source created");
        return true;
    }

    /**
     * Closes the source {@link HikVisionCore} and marks the configuration as
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
