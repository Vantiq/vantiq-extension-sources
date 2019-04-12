/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.communication;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MapMessage;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;
import io.vantiq.extsrc.jmsSource.exceptions.FailedInterfaceSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.FailedJMSSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

public class JMSMessageListener implements MessageListener {
    
    Logger log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    
    public String destName;
    
    private ExtensionWebSocketClient client;
    private Context context;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Destination destination;
    private MessageConsumer consumer;
    
    private MessageHandlerInterface messageHandler;
    
    private static final String SYNCH_KEY = "synchKey";
    
    public static final String MESSAGE = "Message";
    public static final String TEXT = "TextMessage";
    public static final String MAP = "MapMessage";
    
    public JMSMessageListener(Context context, ExtensionWebSocketClient client, String messageHandlerName) throws FailedInterfaceSetupException {
        this.context = context;
        this.client = client;
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        Object object = null;
        
        // Try to find the intended class, fail if it can't be found
        try {
            clazz = Class.forName(messageHandlerName);
        } catch (ClassNotFoundException e) {
            log.error("Could not find requested class '" + messageHandlerName + "'", e);
            throw new FailedInterfaceSetupException();
        }
        
        // Try to find a public no-argument constructor for the class, fail if none exists
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            log.error("Could not find public no argument constructor for '" + messageHandlerName + "'", e);
            throw new FailedInterfaceSetupException();
        }

        // Try to create an instance of the class, fail if it can't
        try {
            object = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            log.error("Error occurred trying to instantiate class '" + messageHandlerName + "'", e);
            throw new FailedInterfaceSetupException();
        }
        
        // Fail if the created object is not a MessageHandlerInterface
        if ( !(object instanceof MessageHandlerInterface) )
        {
            log.error("Class '" + messageHandlerName + "' is not an implementation of MessageHandlerInterface");
            throw new FailedInterfaceSetupException();
        }
        
        // Interface was successfully instantiated and can be used to handle messages
        messageHandler = (MessageHandlerInterface) object;
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
    public void open(String connectionFactoryName, String dest, boolean isQueue, String username, String password) throws NamingException, JMSException, FailedJMSSetupException {
        synchronized (SYNCH_KEY) {
            this.destName = dest;
            
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
            
            consumer = session.createConsumer(destination);
            if (consumer == null) {
                throw new FailedJMSSetupException("A Message Producer for the Destination with name " + dest + " was unable to be created.");
            }
            
            consumer.setMessageListener(this);
            connection.start();
        }
    }
    
    /**
     * The method used to handle incoming messages. Sends the formatted message to VANTIQ as a Source Notification.
     */
    @Override
    public void onMessage(Message msg) {
        try {
            Map<String, Object> msgMap = messageHandler.parseIncomingMessage(msg, destName);
            client.sendNotification(msgMap);
        } catch (JMSException e) {
            log.error("An error occured while parsing the received message. No message will be sent back to VANTIQ.", e);
        } catch (UnsupportedJMSMessageTypeException e) {
            log.error("The incoming JMS Message Type was: " + e.getMessage() + ". This type is not currently supported. "
                    + "No message will be sent back to VANTIQ.", e);
        } catch (Exception e) {
            log.error("An unexpected error occured while parsing the received message. No message will be sent back to VANTIQ.", e);
        }
    }
    
    /**
     * A method used to close the JMS Session and Connection
     * @throws JMSException
     */
    public void close() throws JMSException {
        synchronized (SYNCH_KEY) {
            // Closing the session and connection
            session.close();
            connection.close();
        }
    }
}
