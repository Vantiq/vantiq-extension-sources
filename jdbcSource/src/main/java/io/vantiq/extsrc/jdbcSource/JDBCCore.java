/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.Response;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it and call start() and it will run 
 * itself. start() will return a boolean describing whether or not it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
public class JDBCCore {

    // vars for server configuration
    String sourceName;
    String authToken;
    String targetVantiqServer;    
    
    JDBCHandleConfiguration jdbcConfigHandler;
    
    // vars for internal use
    ExtensionWebSocketClient    client  = null;
    JDBC                        jdbc    = null;
    
    // final vars
    final Logger log;
    final static int    RECONNECT_INTERVAL = 5000;
    
    /**
     * Logs http messages at the debug level 
     */
    public final Handler<Response> httpHandler = new Handler<Response>() {
        @Override
        public void handleMessage(Response message) {
            log.debug(message.toString());
        }
    };
    
    /**
     * Stops sending messages to the source and tries to reconnect, closing on a failure
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Reconnect message received. Reinitializing configuration");
            
            jdbcConfigHandler.configComplete = false;
                        
            CompletableFuture<Boolean> success = client.connectToSource();
            
            try {
                if ( !success.get(10, TimeUnit.SECONDS) ) {
                    log.error("Source reconnection failed");
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
   
            jdbcConfigHandler.configComplete = false;
            
            boolean sourcesSucceeded = false;
            while (!sourcesSucceeded) {
                client.initiateFullConnection(targetVantiqServer, authToken);
                sourcesSucceeded = exitIfConnectionFails(client, 10);
                
                try {
                    Thread.sleep(RECONNECT_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                }
            }
        }
    };    
    
    /**
     * Creates a new JDBCCore with the settings given.
     * @param sourceName            The name of the source to connect to.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     */
    public JDBCCore(String sourceName, String authToken, String targetVantiqServer) {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + '#' + sourceName);
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
    }
    
    /**
     * Returns the name of the source that it is connected to.
     * @return  The name of the source that it is connected to.
     */
    public String getSourceName() {
        return sourceName;
    }
    
    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds before failing and trying again.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     * @return          true if the source connection succeeds, false if it fails.
     */
    public boolean start(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            jdbcConfigHandler = new JDBCHandleConfiguration(this);
            
            client.setConfigHandler(jdbcConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            client.initiateFullConnection(targetVantiqServer, authToken);
            
            sourcesSucceeded = exitIfConnectionFails(client, timeout);
            try {
                Thread.sleep(RECONNECT_INTERVAL);
            } catch (InterruptedException e) {
                log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
            }
        }
        return true;
    }
    
    /**
     * Executes the query that is provided as a String in the options specified by the "query" key in the
     * object of the Query message.
     * @param message   The Query message.
     * @return          The ResultSet that is obtained from executing the Query.
     */
    public void executeQuery(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        if (jdbc == null) {
            if (client != null) {
                client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                        "JDBC connection closed before operation could complete", null);
            }
        }
        
        // Gather query results and send the appropriate response, or send a query error if an exception is caught
        try {
            if (request.get("query") instanceof String) {
                String queryString = (String) request.get("query");
                Map<String, ArrayList<HashMap>> queryMap = jdbc.processQuery(queryString);
                sendDataFromQuery(queryMap, message);
            } else {
                log.error("Query could not be executed because query was not a String");
                client.sendQueryError(replyAddress, this.getClass().getName() + ".queryNotString", 
                        "The Publish Request could not be executed because the query property is"
                        + "not a string", null);
            }
        } catch (SQLException e) {
            log.warn("Could not execute requested query.", e);
            log.debug("Request was: {}", request);
            client.sendQueryError(replyAddress, SQLException.class.getCanonicalName(), 
                    "Failed to execute query for reason '{0}'. Exception was {1}. Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
        }
    }
    
    /**
     * Executes the query that is provided as a String in the options specified by the "query" key in the
     * object of the Publish message.
     * @param message   The Query message.
     * @return          The int value that is obtained from executing the Query. A value of -1 indicates 
     *                  something went wrong.
     */
    public void executePublish(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        if (jdbc == null) {
            if (client != null) {
                client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                        "JDBC connection closed before operation could complete", null);
            }
            log.error("JDBC connection closed before operation could complete");
        }
        
        // Gather query results, or send a query error if an exception is caught
        try {
            if (request.get("query") instanceof String) {
                String queryString = (String) request.get("query");
                int data = jdbc.processPublish(queryString);
                log.trace("The returned integer value from Publish Query is the following: ", data);
            } else {
                log.error("Query could not be executed because query was not a String");
                client.sendQueryError(replyAddress, this.getClass().getName() + ".queryNotString", 
                        "The Publish Request could not be executed because the query property is"
                        + "not a string", null);
            }
        } catch (SQLException e) {
            log.error("Could not execute requested query.", e);
            log.debug("Request was: {}", request);
            client.sendQueryError(replyAddress, SQLException.class.getCanonicalName(), 
                    "Failed to execute query for reason '{0}'. Exception was {1}. Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
        }
    }
    
   /**
    * Takes the data returned from executeQuery() and calls a helper function, createMapFromResults(), to convert
    * data into a list of maps for each row, and send this all together as a map.
    * @param queryResults   A ResultSet containing return value from executeQuery()
    * @param message        The Query message
    */
   public void sendDataFromQuery(Map<String, ArrayList<HashMap>> queryMap, ExtensionServiceMessage message) {
       String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
       
       // Send the results of the query
       if (queryMap == null) {
           // If data is empty send empty list with 204 code
           client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
       } else {
           client.sendQueryResponse(200, replyAddress, queryMap);
       }
   }
   
    
    /**
     * Closes all resources held by this program except for the {@link ExtensionWebSocketClient}. 
     */
    public void close() {
        if (jdbc != null) {
            jdbc.close();
            jdbc = null;
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
