/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */
package io.vantiq.extsrc.testConnector;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class TestConnectorCore {

    String sourceName;
    String authToken;
    String targetVantiqServer;

    TestConnectorHandleConfiguration testConnectorHandleConfiguration;
    ExtensionWebSocketClient client  = null;

    List<String> filenames = new ArrayList<>();

    final Logger log;
    final static int RECONNECT_INTERVAL = 5000;

    static final String ENVIRONMENT_VARIABLES = "environmentVariables";
    static final String FILENAMES = "filenames";
    static final String UNHEALTHY = "unhealthy";

    // Timer used if source is configured to poll from files
    Timer pollingTimer;

    /**
     * Stops sending messages to the source and tries to reconnect indefinitely
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Reconnect message received. Reinitializing configuration");

            if (pollingTimer != null) {
                pollingTimer.cancel();
                pollingTimer = null;
            }

            // Do connector-specific stuff here
            testConnectorHandleConfiguration.configComplete = false;

            // Looping call to client.doCoreReconnection()
            Thread reconnectThread = new Thread(() -> doFullClientConnection(10));
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

            if (pollingTimer != null) {
                pollingTimer.cancel();
                pollingTimer = null;
            }

            testConnectorHandleConfiguration.configComplete = false;

            // Looping call to client.initiateFullConnection()
            Thread connectThread = new Thread(() -> doFullClientConnection(10));
            connectThread.start();
        }
    };

    /**
     * Publish handler that forwards publish requests to the executePublish() method
     */
    public final Handler<ExtensionServiceMessage> publishHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Publish request received.");
            executePublish(message);
        }
    };

    /**
     * Query handler that forwards query requests to the executeQuery() method
     */
    public final Handler<ExtensionServiceMessage> queryHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Query request received.");
            executeQuery(message);
        }
    };


    /**
     * Creates a new TestConnectorCore with the settings given.
     * @param sourceName            The name of the source to connect to.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     */
    public TestConnectorCore(String sourceName, String authToken, String targetVantiqServer) {
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
        client = new ExtensionWebSocketClient(sourceName);
        testConnectorHandleConfiguration = new TestConnectorHandleConfiguration(this);

        client.setConfigHandler(testConnectorHandleConfiguration);
        client.setReconnectHandler(reconnectHandler);
        client.setCloseHandler(closeHandler);
        client.setPublishHandler(publishHandler);
        client.setQueryHandler(queryHandler);

        // Looping call to client.InitiateFullConnection()
        doFullClientConnection(timeout);

        // Setup the TCP Probe Listener
        try {
            client.initializeTCPProbeListener();
        } catch (Exception e) {
            log.error("An exception occurred while trying to setup the TCP Probe Listener");
        }
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
            }
        }
    }

    /**
     * Method that sets up timer to poll for data from file if that is included in source configuration.
     * @param files The list of filenames to read from
     * @param pollingInterval The interval on which we will read data from the files (as a String) and send it back as a
     *                        notification to the source
     */
    public void pollFromFiles(List<String> files, int pollingInterval) {
        filenames.addAll(files);
        pollingTimer = new Timer("pollingTimer");
        TimerTask pollingTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    Map responseObject = readFromFiles(filenames);
                    client.sendNotification(responseObject);
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        };
        pollingTimer.schedule(pollingTimerTask, 0, pollingInterval);
    }

    /**
     * Helper method that reads the data from files as a String, and then creates a map of filenames and their data.
     * @param filenames The list of filenames from which to read data
     * @return A map of filenames to their data (as a String)
     * @throws Exception
     */
    public Map readFromFiles(List<String> filenames) throws Exception {
        Map<String, String> fileData = new LinkedHashMap<>();
        for (String filename : filenames) {
            String data = new String(Files.readAllBytes(Paths.get(filename)));
            fileData.put(filename, data);
        }
        return fileData;
    }

    /**
     * Helper method that reads the data from environment variables as a String, and then creates a map of their names
     * and data.
     * @param environmentVariables The list of environment variable names
     * @return A map of environment variable names and their data
     */
    public Map readFromEnvironmentVariables(List<String> environmentVariables) {
        Map<String, String> envVarData = new LinkedHashMap<>();
        for (String envVarName : environmentVariables) {
            String data = System.getenv(envVarName);
            envVarData.put(envVarName, data);
        }
        return envVarData;
    }

    /**
     * Method called by the publishHandler. Processes the request and sends response if needed.
     * @param message The publish message
     */
    public void executePublish(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        Map<String, Map> responseMap = processRequest(request, null);
        if (responseMap != null) {
            client.sendNotification(responseMap);
        }
    }

    /**
     * Method called by the queryHandler. Processes the request and sends query response or query error.
     * @param message The query message
     */
    public void executeQuery(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        Map<String, Map> responseMap = processRequest(request, replyAddress);
        if (responseMap != null) {
            client.sendQueryResponse(200, replyAddress, responseMap);
        }
    }

    /**
     * Helper method called by executePublish and executeQuery that parses the request and fetches the appropriate data
     * @param request The publish or query request
     * @param replyAddress The replyAddress if this is a query. If the value is non-null, then we can send query errors
     * @return The map of filenames and/or environment variables, depending on what was included in the request.
     */
    public Map<String, Map> processRequest(Map<String, ?> request, String replyAddress) {
        Map<String, Map> responseMap = new LinkedHashMap<>();

        // First we check to make sure that at least one parameter was included, and return an error if not
        if (!(request.get(ENVIRONMENT_VARIABLES) instanceof List) && !(request.get(FILENAMES) instanceof List) &&
                !(request.get(UNHEALTHY) instanceof Boolean)) {
            log.error("The request cannot be processed because it does not contain a valid list of filenames or " +
                    "environmentVariables, nor does it contain an '{}' flag. At least one parameter must be provided.",
                    UNHEALTHY);
            if (replyAddress != null) {
                client.sendQueryError(replyAddress, Exception.class.getCanonicalName(),
                        "The request cannot be processed because it does not contain a valid list of " +
                                "filenames or environmentVariables, nor does it contain an '" + UNHEALTHY + "' flag. " +
                                "At least one parameter must be provided.", null);
            }
            return null;
        }

        // Before we check for the request parameters that actually do work, lets first see if this request is
        // supposed to set the connector to an "unhealthy" state
        if (request.get(UNHEALTHY) instanceof Boolean) {
            Boolean unhealthyState = (Boolean) request.get(UNHEALTHY);
            // If true, set to unhealthy
            if (unhealthyState) {
                client.cancelTCPProbeListener();
            } else {
                // Otherwise, reinitialize the listener (i.e. we're back to a healthy state)
                try {
                    client.initializeTCPProbeListener();
                } catch (Exception e) {
                    log.error("An error occurred while trying to initialize the TCP Listener, this could be because it" +
                            " was already initialized.");
                    if (replyAddress != null) {
                        client.sendQueryError(replyAddress, Exception.class.getCanonicalName(),
                                "An error occurred while trying to initialize the TCP Listener, this " +
                                        "could be because it was already initialized.", null);
                    }
                    return  null;
                }
            }

            if (replyAddress != null) {
                client.sendQueryResponse(204, replyAddress, new LinkedHashMap());
            }
            return null;
        }

        // Next we check for environment variables in the request and grab their data
        if (request.get(ENVIRONMENT_VARIABLES) instanceof List) {
            List environmentVariables = (List) request.get(ENVIRONMENT_VARIABLES);
            if (TestConnectorHandleConfiguration.checkListValues(environmentVariables)) {
                responseMap.put("environmentVariables", readFromEnvironmentVariables(environmentVariables));
            } else {
                log.error("The request was unable to be processed because the 'environmentVariables' list contained " +
                        "either non-String values, or empty Strings. Request: {}", request);
                if (replyAddress != null) {
                    client.sendQueryError(replyAddress, Exception.class.getCanonicalName(),
                            "The request was unable to be processed because the 'environmentVariables'" +
                                    " list contained either non-String values, or empty Strings.", null);
                }
                return null;
            }
        }

        // Finally we get the data from the files if they were specified
        if (request.get(FILENAMES) instanceof List) {
            List filenames = (List) request.get(FILENAMES);
            if (TestConnectorHandleConfiguration.checkListValues(filenames)) {
                try {
                    Map fileData = readFromFiles(filenames);
                    responseMap.put("files", fileData);
                } catch (Exception e) {
                    log.error("An exception occurred while processing the filenames provided in the request", e);
                    if (replyAddress != null) {
                        client.sendQueryError(replyAddress, Exception.class.getCanonicalName(),
                                "An exception occurred while processing the filenames provided in the" +
                                        " request. Exception: " + e.getClass() + ":" + e.getMessage(), null);
                    }
                    return null;
                }
            } else {
                log.error("The request was unable to be processed because the 'filenames' list contained either " +
                        "non-String values, or empty Strings. Request: {}", request);
                if (replyAddress != null) {
                    client.sendQueryError(replyAddress, Exception.class.getCanonicalName(),
                            "The request was unable to be processed because the 'filenames' list " +
                                    "contained either non-String values, or empty Strings.", null);
                }
                return null;
            }
        }

        if (responseMap.isEmpty()) {
            return null;
        } else {
            return responseMap;
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
                log.error("Failed to connect within 10 seconds");
            }
            return false;
        }
        return true;
    }

    /**
     * Returns the name of the source that it is connected to.
     * @return  The name of the source that it is connected to.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Closes all resources held by this program except for the {@link ExtensionWebSocketClient}.
     */
    public void close() {
        if (pollingTimer != null) {
            pollingTimer.cancel();
            pollingTimer = null;
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
}
