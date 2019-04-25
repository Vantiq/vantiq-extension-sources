/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.communication;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;
import io.vantiq.extsrc.jmsSource.exceptions.FailedJMSSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

public class JMSMessageListener implements MessageListener {
    
    Logger log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    
    public String destName;
    private boolean isQueue;
    
    private boolean closing = false;
    
    private ExtensionWebSocketClient client;
    private Context context;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Destination destination;
    
    private MessageHandlerInterface messageHandler;
        
    public JMSMessageListener(Context context, ExtensionWebSocketClient client, MessageHandlerInterface messageHandler) {
        this.context = context;
        this.client = client;
        this.messageHandler = messageHandler;
    }
    
    /**
     * A method used to setup the MessageListener for the given destination (topic or queue)
     * @param connectionFactoryName     The name of the connection factory used to connect to the JMS Server
     * @param dest                      The name of the destination to connect to (topic or queue)
     * @param isQueue                   A boolean flag used to create the appropriate type of destination (queue or topic)
     * @param username                  The username used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @param password                  The password used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @throws NamingException
     * @throws JMSException
     * @throws FailedJMSSetupException
     */
    public synchronized void open(String connectionFactoryName, String dest, boolean isQueue, String username, String password) throws NamingException, JMSException, FailedJMSSetupException {
        this.destName = dest;
        this.isQueue = isQueue;
        
        connectionFactory = (ConnectionFactory) context.lookup(connectionFactoryName);
        if (connectionFactory == null) {
            throw new FailedJMSSetupException("The Connection Factory named " + connectionFactoryName + " was unable to be found.");
        }
        
        connection = connectionFactory.createConnection(username, password);
        if (connection == null) {
            throw new FailedJMSSetupException("A Connection was unable to be created using the Connection Factory named " + connectionFactoryName + ".");
        }
        
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        if (session == null) {
            throw new FailedJMSSetupException("A Session was unable to be created.");
        }
        
        if (isQueue) {
            destination = session.createQueue(dest);
        } else {
            destination = session.createTopic(dest);
        }
        if (destination == null) {
            throw new FailedJMSSetupException("A Destination with name " + dest + " was unable to be created.");
        }
        
        MessageConsumer consumer = session.createConsumer(destination);
        if (consumer == null) {
            throw new FailedJMSSetupException("A Message Producer for the Destination with name " + dest + " was unable to be created.");
        }
        
        consumer.setMessageListener(this);
        connection.start();
    }
    
    /**
     * The method used to handle incoming messages. Sends the formatted message to VANTIQ as a Source Notification.
     */
    @Override
    public void onMessage(Message msg) {
        try {
            Map<String, Object> msgMap = messageHandler.parseIncomingMessage(msg, destName, isQueue);
            
            // Making sure msgMap has the appropriate data
            if (msgMap != null && msgMap.get("headers") instanceof Map && 
                    (msgMap.get("queue") instanceof String || msgMap.get("topic") instanceof String)) {
                client.sendNotification(msgMap);
            } else {
                log.error("The JMS Message Handler {} incorrectly formatted the incoming message. No Message will be sent "
                        + "back to VANTIQ.", messageHandler.getClass().getName());
            }
        } catch (JMSException e) {
            if (!closing) {
                log.error("An error occured while parsing the received message. No message will be sent back to VANTIQ.", e);
            }
        } catch (UnsupportedJMSMessageTypeException e) {
            if (!closing) {
                log.error("The incoming JMS Message Type was: " + e.getMessage() + ". This type is not currently supported. "
                        + "No message will be sent back to VANTIQ.", e);
            }
        } catch (Exception e) {
            if (!closing) {
                log.error("An unexpected error occured while parsing the received message. No message will be sent back to VANTIQ.", e);
            }
        }
    }
    
    /**
     * A method used to close the JMS Session and Connection
     * @throws JMSException
     */
    public synchronized void close() throws JMSException {
        // Closing the session and connection
        closing = true;
        session.close();
        connection.close();
    }
}
