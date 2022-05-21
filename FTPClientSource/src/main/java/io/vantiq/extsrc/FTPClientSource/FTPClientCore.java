/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.FTPClientSource.exception.VantiqFTPClientException;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it
 * and call start() and it will run itself. start() will return a boolean
 * describing whether or not it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
public class FTPClientCore {

    String sourceName;
    String authToken;
    String targetVantiqServer;

    FTPClientHandleConfiguration oConfigHandler;

    Timer pollTimer = null;
    ExtensionWebSocketClient client = null;
    FTPClient FTPClient = null;

    final Logger log;
    final static int RECONNECT_INTERVAL = 5000;
    final static int DEFAULT_BUNDLE_SIZE = 500;
    final static String SELECT_STATEMENT_IDENTIFIER = "select";

    // Used to check row bundling in tests
    public HashMap[] lastRowBundle = null;

    ExecutorService queryPool = null;
    ExecutorService publishPool = null;

    private static final String SYNCH_LOCK = "synchLock";

    /**
     * Creates a new FTPClientCore with the settings given.
     * 
     * @param sourceName         The name of the source to connect to.
     * @param authToken          The authentication token to use to connect.
     * @param targetVantiqServer The url to connect to.
     */
    public FTPClientCore(String sourceName, String authToken, String targetVantiqServer) {
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

            // Do connector-specific stuff here
            oConfigHandler.configComplete = false;

            // Boiler-plate reconnect method- if reconnect fails then we call close(). The
            // code in this reconnect
            // handler must finish executing before we can process another message from
            // Vantiq, meaning the
            // reconnectResult will not complete until after we have exited the handler.
            CompletableFuture<Boolean> reconnectResult = client.doCoreReconnect();
            reconnectResult.thenAccept(success -> {
                if (!success) {
                    close();
                }
            });
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
            if (FTPClient != null) {
                FTPClient.close();
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
     * Executes the query that is provided as a String in the options specified by
     * the "query" key, as part of the object of the Query message. Calls
     * sendDataFromQuery() if the query is executed successfully, otherwise sends a
     * query error using sendQueryError()
     * 
     * @param message The Query message.
     */
    public void executeQuery(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);

        // Getting local copy of FTPClient class
        FTPClient localFTPClient = null;
        synchronized (SYNCH_LOCK) {
            localFTPClient = FTPClient;
        }
        if (localFTPClient == null) {
            if (client != null) {
                client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                        "FTPClient connection closed before operation could complete.", null);
            }
        }

        // Gather query results and send the appropriate response, or send a query error
        // if an exception is caught
        try {
            if (request.get("op") instanceof String) {
                String opString = (String) request.get("op");

                switch (opString.toLowerCase()) {
                    case "checkcomm": {
                        HashMap[] queryArray = localFTPClient.processCheckComm(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "upload": {
                        HashMap[] queryArray = localFTPClient.processUpload(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "clean": {
                        HashMap[] queryArray = localFTPClient.processClean(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "download": {
                        HashMap[] queryArray = localFTPClient.processDownload(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "downloadimage": {
                        HashMap[] queryArray = localFTPClient.processDownloadImage(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "uploadimage": {
                        HashMap[] queryArray = localFTPClient.processUploadImage(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "importdocument": {
                        HashMap[] queryArray = localFTPClient.processImportDocument(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    case "httpdownload": {
                        HashMap[] queryArray = localFTPClient.processHttpDownload(message);
                        sendDataFromQuery(queryArray, message);
                    }
                        break;
                    default:
                        log.error("Unrecognized op :" + opString);
                        client.sendQueryError(replyAddress, this.getClass().getName() + ".opNotSupported",
                                "The Request could not be executed because the op property is " + opString
                                        + " not supported.",
                                null);
                }

            } else {
                log.error("Query could not be executed because query was not a String.");
                client.sendQueryError(replyAddress, this.getClass().getName() + ".queryNotString",
                        "The Query Request could not be executed because the query property is not a string.", null);
            }
        } catch (VantiqFTPClientException e) {
            log.error("Could not execute requested query.", e);
            log.error("Request was: {}", request);
            client.sendQueryError(replyAddress, VantiqFTPClientException.class.getCanonicalName(),
                    "Failed to execute query for reason: " + e.getMessage() + ". Exception was: "
                            + e.getClass().getName() + ". Request was: " + request.get("op"),
                    null);
        } catch (Exception e) {
            log.error("An unexpected error occurred when executing the requested query.", e);
            log.error("Request was: {}", request);
            client.sendQueryError(replyAddress, Exception.class.getCanonicalName(),
                    "Failed to execute query for reason: " + e.getMessage() + ". Exception was: "
                            + e.getClass().getName() + ". Request was: " + request.get("query"),
                    null);
        }
    }

    /**
     * Called by executeQuery() once the query has been executed, and sends the
     * retrieved data back to VANTIQ.
     * 
     * @param queryArray A HashMap Array containing the retrieved data from
     *                   processQuery().
     * @param message    The Query message
     */
    public void sendDataFromQuery(HashMap[] queryArray, ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);

        int bundleFactor = DEFAULT_BUNDLE_SIZE;
        if (request.get("bundleFactor") instanceof Integer && (Integer) request.get("bundleFactor") > -1) {
            bundleFactor = (Integer) request.get("bundleFactor");
        }

        // Send the results of the query
        if (queryArray.length == 0) {
            // If data is empty send empty map with 204 code
            client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
            lastRowBundle = null;
        } else if (bundleFactor == 0) {
            // If the bundleFactor was specified to be 0, then we sent the entire array
            client.sendQueryResponse(200, replyAddress, queryArray);
            lastRowBundle = queryArray;
        } else {
            // Otherwise, send messages containing 'bundleFactor' number of rows
            int len = queryArray.length;
            for (int i = 0; i < len; i += bundleFactor) {
                HashMap[] rowBundle = Arrays.copyOfRange(queryArray, i, Math.min(queryArray.length, i + bundleFactor));

                // If we reached the last row, send with 200 code
                if (i + bundleFactor >= len) {
                    client.sendQueryResponse(200, replyAddress, rowBundle);
                } else {
                    // Otherwise, send row with 100 code signifying more data to come
                    client.sendQueryResponse(100, replyAddress, rowBundle);
                }
                lastRowBundle = rowBundle;
            }
        }
    }

    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds before
     * failing and trying again. This one should run under thread as it should
     * continue until the process exits.
     * 
     * @param timeout The maximum number of seconds to wait before assuming failure
     *                and retrying.
     * @return true if the source connection succeeds, (will retry indefinitely and
     *         never return false).
     */
    public boolean start(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            oConfigHandler = new FTPClientHandleConfiguration(this);

            client.setConfigHandler(oConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            // client.setAutoReconnect(true);
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

    /**
     * Closes all resources held by this program
     */
    public void close() {
        if (FTPClient != null) {
            FTPClient.close();
        }
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
                log.error("Failed to connect to server url '{}'.", targetVantiqServer);
            } else if (!client.isAuthed()) {
                log.error("Failed to authenticate within {} seconds using the given authentication data.", timeout);
            } else {
                log.error("Failed to connect within {} seconds", timeout);
            }
            return false;
        }
        return true;
    }
}
