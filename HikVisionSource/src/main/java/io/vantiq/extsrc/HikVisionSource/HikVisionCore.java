/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.HikVisionSource.exception.*;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it
 * and call start() and it will run itself. start() will return a boolean
 * describing whether or not it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
public class HikVisionCore {

    private static final String SYNCH_LOCK = "synchLock";

    String sourceName;
    String authToken;
    String targetVantiqServer;

    HikVisionHandleConfiguration oConfigHandler;

    Timer pollTimer = null;
    ExtensionWebSocketClient client = null;
    HikVision hikVision = null;

    ExecutorService publishPool = null;

    final Logger log;
    final static int RECONNECT_INTERVAL = 5000;

    /**
     * Executes the query that is provided in the Publish Message. If query is an
     * Array of Strings, then it is executed as a Batch request. If the query is a
     * single String, then it is executed normally.
     * 
     * @param message The Query message.
     */
    public void executePublish(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();

        // Getting local copy of HikVision class
        HikVision localHikVision = null;
        synchronized (SYNCH_LOCK) {
            localHikVision = hikVision;
        }

        if (localHikVision == null) {
            log.error("EasyModbus connection closed before operation could complete");
        }

        // Gather query results, or send a query error if an exception is caught
        try {
            int data = localHikVision.hanldeUpdateCommand(message);

        } catch (VantiqHikVisionException e) {
            log.error("Could not execute requested query.", e);
            log.error("Request was: {}", request);
        } catch (ClassCastException e) {
            log.error(
                    "Could not execute requested query. This is most likely because the query list did not contain Strings.",
                    e);
            log.error("Request was: {}", request);
        } catch (Exception e) {
            log.error("An unexpected error occurred when executing the requested query.", e);
            log.error("Request was: {}", request);
        }
    }

    /**
     * Stops sending messages to the source and tries to reconnect, closing on a
     * failure
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Reconnect message received. Reinitializing configuration");

            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }

            oConfigHandler.configComplete = false;

            CompletableFuture<Boolean> success = client.connectToSource();

            try {
                if (!success.get(10, TimeUnit.SECONDS)) {
                    if (!client.isOpen()) {
                        log.error("Failed to connect to server url '" + targetVantiqServer + "'.");
                    } else if (!client.isAuthed()) {
                        log.error("Failed to authenticate within 10 seconds using the given authentication data.");
                    } else {
                        log.error("Failed to connect within 10 seconds");
                    }
                    close();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("Could not reconnect to source within 10 seconds: ", e);
                close();
            }
        }
    };

    /**
     * Stops sending messages to the source and tries to reconnect indefinitely
     */
    public final Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.trace("WebSocket closed unexpectedly. Attempting to reconnect");

            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }

            oConfigHandler.configComplete = false;

            boolean sourcesSucceeded = false;
            while (!sourcesSucceeded) {
                client.initiateFullConnection(targetVantiqServer, authToken);
                sourcesSucceeded = exitIfConnectionFails(client, 10);
                if (!sourcesSucceeded) {
                    try {
                        Thread.sleep(RECONNECT_INTERVAL);
                    } catch (InterruptedException e) {
                        log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                    }
                }
            }
        }
    };

    /**
     * Creates a new HikVisionCore with the settings given.
     * 
     * @param sourceName         The name of the source to connect to.
     * @param authToken          The authentication token to use to connect.
     * @param targetVantiqServer The url to connect to.
     */
    public HikVisionCore(String sourceName, String authToken, String targetVantiqServer) {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + '#' + sourceName);
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
    }

    /**
     * Returns the name of the source that it is connected to.
     * 
     * @return The name of the source that it is connected to.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds before
     * failing and trying again.
     * 
     * @param timeout The maximum number of seconds to wait before assuming failure
     *                and stopping.
     * @return true if the source connection succeeds, (will retry indefinitely and
     *         never return false).
     */
    public boolean start(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            oConfigHandler = new HikVisionHandleConfiguration(this);

            client.setConfigHandler(oConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            client.initiateFullConnection(targetVantiqServer, authToken);

            sourcesSucceeded = exitIfConnectionFails(client, timeout);
            if (!sourcesSucceeded) {
                try {
                    Thread.sleep(RECONNECT_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                }
            }
        }
        return true;
    }

    public void close() {
        // stop();

        if (hikVision != null)
            hikVision.close();
    }

    /**
     * Closes all resources held by this program and then closes the connection.
     */
    public void stop() {
        if (client != null && client.isOpen()) {
            client.stop();
            client = null;
        }
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection
     * does not succeed within {@code timeout} seconds.
     *
     * @param client  The client to watch for success or failure.
     * @param timeout The maximum number of seconds to wait before assuming failure
     *                and stopping
     * @return true if the connection succeeded, false if it failed to connect
     *         within {@code timeout} seconds.
     */
    public boolean exitIfConnectionFails(ExtensionWebSocketClient client, int timeout) {
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = client.getSourceConnectionFuture().get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout: full connection did not succeed within {} seconds: {}", timeout, e);
        } catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources.");
            if (!client.isOpen()) {
                log.error("Failed to connect to server url '" + targetVantiqServer + "'.");
            } else if (!client.isAuthed()) {
                log.error("Failed to authenticate within " + timeout + " seconds using the given authentication data.");
            } else {
                log.error("Failed to connect within 10 seconds");
            }
            return false;
        }
        return true;
    }
}
