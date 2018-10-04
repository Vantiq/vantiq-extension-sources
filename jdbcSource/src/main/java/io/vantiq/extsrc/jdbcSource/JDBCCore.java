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
import java.util.Timer;
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
    
    JDBCConfigHandler jdbcConfigHandler;
    
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
            log.info("Reconnect message received. Reinitializing configuration");
            
            jdbcConfigHandler.configComplete = false;
            
            client.setQueryHandler(defaultQueryHandler);
            
            CompletableFuture<Boolean> success = client.connectToSource();
            
            try {
                if ( !success.get(10, TimeUnit.SECONDS) ) {
                    log.error("Source reconnection failed");
                    close();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("Could not reconnect to source within 10 seconds");
                close();
            }
        }
    };
    
    /**
     * Stops sending messages to the source and tries to reconnect, closing on a failure
     */
    public final Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.info("WebSocket closed unexpectedly. Attempting to reconnect");
   
            jdbcConfigHandler.configComplete = false;
            
            boolean sourcesSucceeded = false;
            while (!sourcesSucceeded) {
                client.setQueryHandler(defaultQueryHandler);
                
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
     * Sends back an error when no query handler has been set by the config
     */
    Handler<ExtensionServiceMessage> defaultQueryHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage msg) {
            log.warn("Query received with no user-set handler");
            log.debug("Full message: " + msg);
            // Prepare a response with an empty body, so that the query doesn't wait for a timeout
            Object[] body = {msg.getSourceName()};
            client.sendQueryError(ExtensionServiceMessage.extractReplyAddress(msg),
                    "io.vantiq.extsrc.objectRecognition.noQueryConfigured",
                    "Source '{0}' is not configured for Queries. Queries require objRecConfig.general.pollRate < 0",
                    body);
        }
    };
    
    
    /**
     * Creates a new ObjectRecognitionCore with the settings given.
     * @param sourceName            The name of the source to connect to.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     * @param modelDirectory        The directory in which the model files for the neural net will be stored.
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
     * Tries to connect to a source and waits up to {@code timeout} seconds for it to succeed or fail.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     * @return          true if the source connection succeeds, false if it fails.
     */
    public boolean start(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            jdbcConfigHandler = new JDBCConfigHandler(this);
            
            client.setConfigHandler(jdbcConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            client.setQueryHandler(defaultQueryHandler);
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
     * Retrieves an image using the Core's image retriever using the options specified in the object of the Query
     * message. Calls {@code stop()} if a FatalImageException is received.
     * @param message   The Query message.
     * @return          The image retrieved in jpeg format, or null if a problem occurred.
     */
    public ResultSet executeQuery(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        if (jdbc == null) { // Should only happen if close() was called immediately before retreiveImage()
            if (client != null) {
                client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                        "The source closed mid message", null);
            }
            return null;
        }
        
        // Return the retriever's results, or send a query error and return null on an exception
        try {
            if (request.get("query") instanceof String) {
                String queryString = (String) request.get("query");
                return jdbc.processQuery(queryString);
            } else {
                log.error("Query could not be executed because query was not a String");
            }
        } catch (SQLException e) {
            log.warn("Could not execute requested query.", e);
            log.debug("Request was: {}", request);
            client.sendQueryError(replyAddress, SQLException.class.getCanonicalName(), 
                    "Failed to execute query for reason '{0}'. Exception was {1}. Request was {2}"
                    , new Object[] {e.getMessage(), e, request});
        }
        return null; // This will keep the program from trying to do anything with an image when retrieval fails
    }
    
   /**
    * Processes the image using the options specified in the Query message then sends a Query response containing the
    * results. Calls {@code stop()} if a FatalImageException is received.
    * @param imageResults   An {@link ImageRetrieverResults} containing the image to be translated
    * @param message        The Query message
    */
   public void sendDataFromQuery(ResultSet queryResults, ExtensionServiceMessage message) {
       Map<String, ?> request = (Map<String, ?>) message.getObject();
       String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
       
       if (queryResults == null) {
           if (client != null) {
               client.sendQueryError(replyAddress, this.getClass().getName(),
                       "The JDBC source could not complete the query", null);
           }
           return;
       }
       
       // Send the results of the query
       try {
           // If data is empty send empty list with 204 code
           if (!queryResults.next()) { 
               client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());    
               queryResults.close();
           } else {
               queryResults.beforeFirst();
               Map<String, ArrayList<HashMap>> queryResultsMap = createMapFromResults(queryResults);
               client.sendQueryResponse(200, replyAddress, queryResultsMap);
               queryResults.close();
           }
       } catch (SQLException e) {
           log.error("An error occured when processing the query results: ", e);
       }
   }
   
   Map<String, ArrayList<HashMap>> createMapFromResults(ResultSet queryResults) throws SQLException{
       Map<String, ArrayList<HashMap>> map = new LinkedHashMap<>();
       ArrayList<HashMap> rows = new ArrayList<HashMap>();
       ResultSetMetaData md = queryResults.getMetaData(); 
       int columns = md.getColumnCount();
       
       // Iterate over rows of Result Set and create a map for each row
       while(queryResults.next()) {
           HashMap row = new HashMap(columns);
           for (int i=1; i<=columns; ++i) {
               row.put(md.getColumnName(i), queryResults.getObject(i));
           }
           // Add each row map to the list of rows
           rows.add(row);
       }
       
       // Put list of maps as value to the key "queryResult"
       map.put("queryResult", rows);
       return map;
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
            log.error("Timeout: full connection did not succeed within {} seconds.", timeout);
        }
        catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources. Retrying...");
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
