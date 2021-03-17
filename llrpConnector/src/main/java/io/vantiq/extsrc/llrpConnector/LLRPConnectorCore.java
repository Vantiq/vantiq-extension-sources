/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */
package io.vantiq.extsrc.llrpConnector;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class is used to manage the connection with Vantiq.
 *
 */
public class LLRPConnectorCore {

    String sourceName;
    String authToken;
    String targetVantiqServer;

    LLRPConnectorHandleConfiguration llrpConnectorHandleConfiguration;
    ExtensionWebSocketClient client  = null;
    LLRPConnector            llrp    = null;
    int                      sourceTimeout = 10; // connection to Vantiq source timeout (from call to start)

    final Logger log;
    final static int RECONNECT_INTERVAL = 5000;  // milliseconds to wait before reconnecting to Vantiq source

    /**
     * Creates a new LLRPConnectorCore with the settings given from the server.config file.
     *
     * @param sourceName            The name of the source to connect to.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     */
    public LLRPConnectorCore(String sourceName, String authToken, String targetVantiqServer) {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + '#' + sourceName);
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
    }

    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds before failing and trying again.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     */
    public void start(int timeout) {
        // Save the timeout duration that will be used in the handlers when reconnecting
        sourceTimeout = timeout;

        // Create a web socket client to communicate with the Vantiq source
        client = new ExtensionWebSocketClient(sourceName);
        // Create a handler class for messages sent/received by Vantiq source, referencing this class
        llrpConnectorHandleConfiguration = new LLRPConnectorHandleConfiguration(this);

        // Associate the handler methods for various events (connection closing, reconnecting, etc.)
        client.setConfigHandler(llrpConnectorHandleConfiguration);
        client.setReconnectHandler(reconnectHandler);
        client.setCloseHandler(closeHandler);

        // Looping call to client.InitiateFullConnection()
        doFullClientConnection(timeout);
    }

    /**
     * Helper method to do the work of either initiating new connection, or doing a reconnect. This is done in a loop
     * until we get a successful connection.
     * @param timeout The maximum number of seconds to wait before assuming failure and stopping.
     */
    public void doFullClientConnection(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            // Either try to reconnect, or initiate a new full connection
            if (client.isOpen() && client.isAuthed()) {
                client.doCoreReconnect();
            } else {
                client.initiateFullConnection(targetVantiqServer, authToken);
            }

            // Now check the result
            sourcesSucceeded = exitIfConnectionFails(timeout);
            if (!sourcesSucceeded) {
                try {
                    Thread.sleep(RECONNECT_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                }
            } else {
                if (llrp != null)
                    llrp.vantiqSourceConnectionOnline();
            }
        }
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within
     * {@code timeout} seconds.
     *
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping
     * @return          true if the connection succeeded, false if it failed to connect within {@code timeout} seconds.
     */
    public boolean exitIfConnectionFails(int timeout) {
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = client.getSourceConnectionFuture().get(timeout, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            log.error("Timeout: full connection did not succeed within {} seconds: {}", timeout, e);
        }
        catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources.");
            if (!client.isOpen()) {
                log.error("Failed to connect to server url '" + targetVantiqServer + "'.");
            } else if (!client.isAuthed()) {
                log.error("Failed to authenticate within " + timeout + " seconds using the given authentication data.");
            } else {
                log.error("Failed to connect within " + timeout + " seconds");
            }
            return false;
        }
        return true;
    }

    /**
     * Stops sending messages to the source and tries to reconnect indefinitely
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Reconnect message received. Reinitializing configuration");

            // Do connector-specific stuff here
            llrpConnectorHandleConfiguration.configComplete = false;
            // Stop connection with the reader, letting it buffer reads
            if (llrp != null)
                llrp.close();

            // Looping call to client.doCoreReconnection()
            Thread reconnectThread = new Thread(() -> doFullClientConnection(sourceTimeout));
            reconnectThread.start();
        }
    };

    /**
     * Stops sending messages to the source and tries to reconnect indefinitely
     */
    public final Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.trace("WebSocket closed unexpectedly. Attempting to reconnect");

            // Do connector-specific stuff here
            llrpConnectorHandleConfiguration.configComplete = false;
            // Stop connection with the reader, letting it buffer reads
            if (llrp != null)
                llrp.close();

            // Looping call to client.initiateFullConnection()
            Thread connectThread = new Thread(() -> doFullClientConnection(sourceTimeout));
            connectThread.start();
        }
    };

    /**
     * Returns the name of the source that it is connected to.
     * @return  The name of the source that it is connected to.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Send message from the reader to VANTIQ server
     * @param msg
     */
    public void sendMessage(Map<String, Object> msg) {

        log.trace("Sending message: " + msg);
        try {
            client.sendNotification(msg);
        } catch (Exception e) {
            log.error("sendMessage: unexpected error. ", e);
        }
    }

    /**
     * Closes all resources held by this program except for the {@link ExtensionWebSocketClient}.
     */
    public void close() {
        log.info("LLRPConnectorCore: close all resources");
        // Do connector-specific stuff here
        // Stop connection with the reader, letting it buffer reads
        if (llrp != null)
            llrp.close();

    }

    /**
     * Closes all resources held by this program and then closes the connection.
     */
    public void stop() {
        close();
        if (client != null && client.isOpen()) {
            client.stop();
            client = null;
        }
    }
}