/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import java.util.Map;

import javax.jms.JMSException;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;

/**
 * Sets up the source using the configuration document, which looks like the following.
 *<pre> {
 *      jmsConfig: {
 *          general: {
 *              &lt;general options&gt;
 *          },
 *          sender: {
 *              &lt;sender options&gt;
 *          },
 *          receiver: {
 *  *           &lt;receiver options&gt;
 *  *       }
 *      }
 * }</pre>
 *
 * The options for general are as follows:
 * <ul>
 *      <li>{@code username}: The username to authenticate the JMS Server connection.
 *      <li>{@code password}: The password to authenticate the JMS Server connection.
 *      <li>{@code providerURL}: The URL used to connect to the JMS Server.
 *      <li>{@code connectionFactory}: The name of the JMS ConnectionFactory used to
 *      connect to the JMS Destinations (topics/queues).
 *      <li>{@code initialContext}: The FQCN of the InitialContextFactory used by the
 *      given JMS Server in order to access the JMS ConnectionFactory using the server's JNDI.
 * </ul>
 *
 * The options for sender are as follows:
 * <ul>
 *      <li>{@code queues}: The list of queues for which to create MessageProducers.
 *      <li>{@code topics}: The list of topics for which to create MessageProducers.
 * </ul>
 *
 * The options for receiver are as follows:
 * <ul>
 *      <li>{@code queues}: The list of queues for which to create MessageConsumers.
 *      <li>{@code queueListeners}: The list of queues for which to create MessageListeners.
 *      <li>{@code topics}: The list of topics for which to create MessageConsumers.
 * </ul>
 */

public class JMSHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    JMSCore                 source;
    boolean                 configComplete = false; // Not currently used
    
    private static final String READ_OPERATION = "read";

    Handler<ExtensionServiceMessage> queryHandler;
    Handler<ExtensionServiceMessage> publishHandler;

    public JMSHandleConfiguration(JMSCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
        queryHandler = new Handler<ExtensionServiceMessage>() {
            ExtensionWebSocketClient client = source.client;

            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.JMSHandleConfiguration.invalidQueryRequest",
                            "Request must be a map", null);
                } else {
                    Map<String, ?> request = (Map<String, ?>) message.getObject();
                    if (request.get("operation") instanceof String) {
                        String operation = (String) request.get("operation");
                        switch (operation) {
                            case READ_OPERATION:
                                // Process query and return the most recent message from queue
                                source.readQueueMessage(message);
                                break;
                            default:
                                client.sendQueryError(replyAddress, "io.vantiq.extsrc.JMSHandleConfiguration.invalidQueryOperation",
                                        "The requested operation does not exist, or is not yet supported.", null);
                        }
                    } else {
                        client.sendQueryError(replyAddress, "io.vantiq.extsrc.JMSHandleConfiguration.invalidQueryRequest",
                                "No query operation was specified. A supported operation must be specified as a String.", null);
                    }
                }
            }
        };
        publishHandler = new Handler<ExtensionServiceMessage>() {
            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    log.error("Invalid Publish Request: Request must be a map.");
                }
                // Process publish and send message to the destination (queue or topic)
                source.sendJMSMessage(message);
            }
        };
    }

    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the JMS Source.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, Object> jmsConfig;
        Map<String, Object> general;
        Map<String, Object> sender;
        Map<String, Object> receiver;

        // Obtain entire config from the message object
        if ( !(configObject.get("config") instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for JMS Source.");
            failConfig();
            return;
        }
        config = (Map) configObject.get("config");

        // Retrieve the jmsConfig and the vantiq config
        if (!(config.get("jmsConfig") instanceof Map)) {
            log.error("Configuration failed. Configuration must contain 'jmsConfig' field.");
            failConfig();
            return;
        }
        jmsConfig = (Map) config.get("jmsConfig");

        // Get the general options from the jmsConfig
        if ( !(jmsConfig.get("general") instanceof Map)) {
            log.error("Configuration failed. No general options specified.");
            failConfig();
            return;
        }
        general = (Map) jmsConfig.get("general");

        // Get the sender options from the jmsConfig
        if ( !(jmsConfig.get("sender") instanceof Map)) {
            log.error("Configuration failed. No sender options specified.");
            failConfig();
            return;
        }
        sender = (Map) jmsConfig.get("sender");

        // Get the receiver options from the jmsConfig
        if ( !(jmsConfig.get("receiver") instanceof Map)) {
            log.error("Configuration failed. No receiver options specified.");
            failConfig();
            return;
        }
        receiver = (Map) jmsConfig.get("receiver");

        boolean success = createJMSConnection(general, sender, receiver);
        if (!success) {
            failConfig();
            return;
        }

        log.trace("Setup complete");
        configComplete = true;
    }

    /**
     * Attempts to create the JMS Connection based on the configuration document.
     * @param generalConfig     The general configuration for the JMS Source
     * @param sender            The sender configuration for the JMS Source
     * @param receiver          The receiver configuration for the JMS Source
     * @return                  true if the JMS connections could be created, false otherwise
     */
    boolean createJMSConnection(Map<String, ?> generalConfig, Map<String, ?> sender, Map<String, ?> receiver) {
        // Get Provider URL, Connection Factory, and Initial Context from general config
        String providerURL;
        String connectionFactory;
        String initialContext;
        String username;
        String password;
        
        if (generalConfig.get("providerURL") instanceof String) {
            providerURL = (String) generalConfig.get("providerURL");
        } else {
            log.error("Configuration failed. No providerURL was specified");
            return false;
        }

        if (generalConfig.get("connectionFactory") instanceof String) {
            connectionFactory = (String) generalConfig.get("connectionFactory");
        } else {
            log.error("Configuration failed. No connectionFactory was specified");
            return false;
        }

        if (generalConfig.get("initialContext") instanceof String) {
            initialContext = (String) generalConfig.get("initialContext");
        } else {
            log.error("Configuration failed. No initialContext was specified");
            return false;
        }
        
        if (generalConfig.get("username") instanceof String) {
            username = (String) generalConfig.get("username");
        } else {
            username = null;
        }
        
        if (generalConfig.get("password") instanceof String) {
            password = (String) generalConfig.get("password");
        } else {
            password = null;
        }

        // Initialize JMS Source InitialContext with config values
        try {
            if (source.jms != null) {
                source.jms.close();
            }
            JMS jms = new JMS(source.client, connectionFactory);
            
            // Creating InitialContext
            jms.setupInitialContext(initialContext, providerURL);
            
            // Create Message Producers and Consumers
            jms.createProducersAndConsumers(sender, receiver, username, password);
            source.jms = jms;
        } catch (NamingException e) {
            log.error("Configuration failed. Exception occurred while setting up JMS Source InitialContext, or while "
                    + "looking up Connection Factory: ", e);
            return false;
        } catch (JMSException e) {
            log.error("Configuration failed. Exception occured while creating JMS Message Consumer or Producer: ", e);
        } catch (Exception e) {
            log.error("Configuration failed. Unexpected exception occured while setting up JMS Source: ", e);
            return false;
        }
        
        // Start listening for queries and publishes
        source.client.setQueryHandler(queryHandler);
        source.client.setPublishHandler(publishHandler);

        log.trace("JMS source created");
        return true;
    }

    /**
     * Closes the source {@link JMSCore} and marks the configuration as completed. The source will
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
