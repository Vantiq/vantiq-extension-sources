/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.jdbcSource.JDBCCore;

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
 *      <li>{@code dbURL}: The URL of the SQL Database to be used.
 *      <li>{@code driver}: The JDBC Driver that will be used to connect to the SQL Database.
 *                      
 * </ul>
 */

public class JDBCConfigHandler extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    JDBCCore                source;
    boolean                 configComplete = false;
    
    Map<String, ?> lastGeneral = null;
    
    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;
    
    public JDBCConfigHandler(JDBCCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
        queryHandler = new Handler<ExtensionServiceMessage>() {
            ExtensionWebSocketClient client = source.client;
            
            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBC.invalidQueryRequest", 
                            "Request must be a map", null);
                }
                
                // Process query and send the results
                ResultSet data = source.executeQuery(message);
                if (data != null) {
                    source.sendDataFromQuery(data, message);
                }
            }
        };
        publishHandler = new Handler<ExtensionServiceMessage>() {
            ExtensionWebSocketClient client = source.client;
            
            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBC.invalidPublishRequest", 
                            "Request must be a map", null);
                }
                
                // Process query and send the results
                int data = source.executePublish(message);
                log.trace("The returned integer value from Publish Query is the following: ", data);
            }
        };
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the JDBC Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> config = (Map) message.getObject();
        Map<String, Object> general;
        
        // Obtain the Maps for each object
        if ( !(config.get("config") instanceof Map && ((Map)config.get("config")).get("jdbcConfig") instanceof Map) ) {
            log.error("No configuration suitable for an objectRecognition. Waiting for valid config...");
            failConfig();
            return;
        }
        config = (Map) ((Map) config.get("config")).get("jdbcConfig");
        
        if ( !(config.get("general") instanceof Map)) {
            log.error("No general options specified. Waiting for valid config...");
            failConfig();
            return;
        }
        general = (Map) config.get("general");
        
        // Only create a new data source if the config changed, to save time and state
        if (lastGeneral != null && general.equals(lastGeneral)) {
            log.info("config unchanged, keeping previous");
        } else {
            boolean success = createDBConnection(general);
            if (!success) {
                failConfig();
                return;
            }
        }
        
        log.info("Setup complete");
        
        configComplete = true;
    }
    
    /**
     * Attempts to create the JDBC Source based on the configuration document.
     * @param generalConfig     The general configuration JDBC Source
     * @return                  true if the JDBC source could be created, false otherwise
     */
    boolean createDBConnection(Map<String, ?> generalConfig) {
        // Null the last config so if it fails it will know the last failed
        lastGeneral = null;
        
        // Get Username/Password, DB URL, and DB Driver
        String username;
        String password;
        String dbURL;
        String dbDriver; 
        
        if (generalConfig.get("username") instanceof String) {
            username = (String) generalConfig.get("username");
        } else {
            log.debug("No db username was specified");
            failConfig();
            return false;
        }
        
        if (generalConfig.get("password") instanceof String) {
            password = (String) generalConfig.get("password");
        } else {
            log.debug("No db password was specified");
            failConfig();
            return false;
        }
        
        if (generalConfig.get("dbURL") instanceof String) {
            dbURL = (String) generalConfig.get("dbURL");
        } else {
            log.debug("No db URL was specified");
            failConfig();
            return false;
        }
        
        if (generalConfig.get("driver") instanceof String) {
            dbDriver = (String) generalConfig.get("driver");
        } else {
            log.debug("No db driver was specified");
            failConfig();
            return false;
        }
        
        // Initialize JDBC Source with config values
        try {
            JDBC jdbc = new JDBC();
            jdbc.setupJDBC(dbDriver, dbURL, username, password);
            source.jdbc = jdbc; 
        } catch (SQLException e) {
            log.error("Exception occured while setting up JDBC Source: ", e);
            failConfig();
            return false;
        } catch (LinkageError e) {
            log.error("Exception occured while setting up JDBC Source: ", e);
            failConfig();
            return false;
        } catch (ClassNotFoundException e) {
            log.error("Exception occured while setting up JDBC Source: ", e);
            failConfig();
            return false;
        }
        
        // Start listening for queries and publishes
        source.client.setQueryHandler(queryHandler);
        source.client.setPublishHandler(publishHandler);

        log.info("JDBC source created");
        return true;
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
