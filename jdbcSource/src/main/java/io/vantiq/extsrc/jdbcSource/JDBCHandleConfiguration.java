/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.jdbcSource.JDBCCore;
import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

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
 *      <li>{@code dbURL}: The URL of the SQL Database to be used. *                      
 * </ul>
 */

public class JDBCHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    JDBCCore                source;
    int                     polling = 0;
    boolean                 configComplete = false; // Not currently used
        
    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;
    
    public JDBCHandleConfiguration(JDBCCore source) {
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
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBCHandleConfiguration.invalidQueryRequest", 
                            "Request must be a map", null);
                }
                
                // Process query and send the results
                source.executeQuery(message);
            }
        };
        publishHandler = new Handler<ExtensionServiceMessage>() {
            ExtensionWebSocketClient client = source.client;
            
            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBCConfigHandler.invalidPublishRequest", 
                            "Request must be a map", null);
                }
                source.executePublish(message);
            }
        };
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the JDBC Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, Object> vantiq;
        Map<String, Object> jdbcConfig;
        Map<String, Object> general;
        
        // Obtain entire config from the message object
        if ( !(configObject.get("config") instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for JDBC Source.");
            failConfig();
            return;
        }
        config = (Map) configObject.get("config");
        
        // Retrieve the jdbcConfig and the vantiq config
        if ( !(config.get("jdbcConfig") instanceof Map && config.get("vantiq") instanceof Map) ) {
            log.error("Configuration failed. Configuration must contain 'jdbcConfig' and 'vantiq' fields.");
            failConfig();
            return;
        }
        jdbcConfig = (Map) config.get("jdbcConfig");
        vantiq = (Map) config.get("vantiq");
        
        // Get the general options from the jdbcConfig
        if ( !(jdbcConfig.get("general") instanceof Map)) {
            log.error("Configuration failed. No general options specified.");
            failConfig();
            return;
        }
        general = (Map) jdbcConfig.get("general");
        
        boolean success = createDBConnection(general, vantiq);
        if (!success) {
            failConfig();
            return;
        }
        
        log.trace("Setup complete");
        configComplete = true;
    }
    
    /**
     * Attempts to create the JDBC Source based on the configuration document.
     * @param generalConfig     The general configuration for the JDBC Source
     * @param vantiq            The vantiq configuration for the JDBC Source
     * @return                  true if the JDBC source could be created, false otherwise
     */
    boolean createDBConnection(Map<String, ?> generalConfig, Map<String, ?> vantiq) {
        // Ensuring that packageRows is set to be true, and failing the configuration otherwise
        if (!(vantiq.get("packageRows") instanceof String) || !(vantiq.get("packageRows").toString().equalsIgnoreCase("true"))) {
            log.error("Configuration failed. The packageRows field must be set to true.");
            return false;
        }
        
        // Get Username/Password, DB URL, and DB Driver
        String username;
        String password;
        String dbURL;
        
        if (generalConfig.get("username") instanceof String) {
            username = (String) generalConfig.get("username");
        } else {
            log.error("Configuration failed. No db username was specified");
            return false;
        }
        
        if (generalConfig.get("password") instanceof String) {
            password = (String) generalConfig.get("password");
        } else {
            log.error("Configuration failed. No db password was specified");
            return false;
        }
        
        if (generalConfig.get("dbURL") instanceof String) {
            dbURL = (String) generalConfig.get("dbURL");
        } else {
            log.error("Configuration failed. No db URL was specified");
            return false;
        }
        
        // Initialize JDBC Source with config values
        try {
            if (source.jdbc != null) {
                source.jdbc.close();
            }
            JDBC jdbc = new JDBC();
            jdbc.setupJDBC(dbURL, username, password);
            source.jdbc = jdbc; 
        } catch (VantiqSQLException e) {
            log.error("Configuration failed. Exception occurred while setting up JDBC Source: ", e);
            return false;
        }
        
        // Create polling query if specified
        if (generalConfig.get("pollTime") instanceof Integer) {
            if (generalConfig.get("pollQuery") instanceof String) {
                int pollTime = (Integer) generalConfig.get("pollTime");
                if (pollTime > 0) {
                    String pollQuery = (String) generalConfig.get("pollQuery"); 
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
        
        log.trace("JDBC source created");
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
