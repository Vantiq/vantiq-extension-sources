/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.connector;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.camel.utils.ClientRegistry;
import io.vantiq.extsrc.camelconn.discover.CamelRunner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it and call start() and it will run 
 * itself. start() will return a boolean describing whether it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
@Slf4j
public class CamelCore {

    String sourceName;
    String authToken;
    String targetVantiqServer;    
    
    CamelHandleConfiguration camelConfigHandler;
    
    Timer                       pollTimer = null;
    ExtensionWebSocketClient    client  = null;
    final static int RECONNECT_INTERVAL = 5000;
    private static final String SYNCH_LOCK = "synchLock";

    /**
     * Stops sending messages to the source and tries to reconnect, closing on a failure
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Reconnect message received. Reinitializing configuration");

            // Do connector-specific stuff here
            camelConfigHandler.configComplete = false;

            // Boilerplate reconnect method -- if reconnect fails then we call close(). The code in this reconnect
            // handler must finish executing before we can process another message from Vantiq, meaning the
            // reconnectResult will not complete until after we have exited the handler.
            
            // FIXME: Determine if the semantics here apply to us
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
            
            // FIXME:  Should these shut down the camelRunner?
            // camelConfigurationHandler.getCurrentCamelRunner().close()??
            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }
   
            camelConfigHandler.configComplete = false;
            
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
     * Creates a new CamelCore with the settings given.
     * @param sourceName            The name of the source to which to connect.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     */
    public CamelCore(String sourceName, String authToken, String targetVantiqServer) {
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
    }
    
    /**
     * Returns the name of the source to which this instance is connected.
     * @return  The name of the source this instance represents.
     */
    public String getSourceName() {
        return sourceName;
    }
    
    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds before failing and trying again.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     * @return          true if the source connection succeeds, (will retry indefinitely and never return false).
     */
    public boolean start(int timeout) {
        boolean sourceSucceeded = false;
        while (!sourceSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            ClientRegistry.registerClient(sourceName, targetVantiqServer, client);
            camelConfigHandler = new CamelHandleConfiguration(this);
            
            client.setConfigHandler(camelConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            client.initiateFullConnection(targetVantiqServer, authToken);
            
            sourceSucceeded = exitIfConnectionFails(client, timeout);
            if (!sourceSucceeded) {
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
     * Executes the query that is provided as a String in the options specified by the "query" key, as part of the
     * object of the Query message. Calls sendDataFromQuery() if the query is executed successfully, otherwise sends
     * a query error using sendQueryError()
     * @param message   The Query message.
     */
    @SuppressWarnings({"PMD.CognitiveComplexity"})
    public void executeQuery(ExtensionServiceMessage message) {
        // FIXME: What should a query do?  Not clear it has a place here, at least not that this level.
        // TODO: Could be used in a round trip type case where a message comes into Vantiq then does something &
        //  returns the value.  Publish & notify are fire & forget, so it makes sense to support it.  However, it'd
        //  be nice to see if we can figure that out by looking at the route(s).
    }
    
    /**
     * Closes all resources held by this program except for the {@link ExtensionWebSocketClient}. 
     */
    public void close() {
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
        if (camelConfigHandler != null) {
            CamelRunner runner = camelConfigHandler.getCurrentCamelRunner();
            if (runner != null) {
                Thread camelThread = runner.getCamelThread();
    
                synchronized (SYNCH_LOCK) {
                    runner.close();
                    if (camelThread != null) {
                        try {
                            camelThread.join(TimeUnit.SECONDS.toMillis(10));
                        } catch (Exception e) {
                            // Here, if things go awry, it's not clear what we could do.  Just log it.
                            log.error("Exception awaiting Camel thread completion", e);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Closes all resources held by this program and then closes the connection. 
     */
    public void stop() {
        close();
        ClientRegistry.removeClient(sourceName, targetVantiqServer, client);
        if (client != null && client.isOpen()) {
            client.stop();
            client = null;
        }
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within
     * {@code timeout} seconds.
     *
     * @param client    The client to watch for success or failure.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping
     * @return          true if the connection succeeded, false if it failed to connect within {@code timeout} seconds.
     */
    public boolean exitIfConnectionFails(ExtensionWebSocketClient client, int timeout) {
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
}
