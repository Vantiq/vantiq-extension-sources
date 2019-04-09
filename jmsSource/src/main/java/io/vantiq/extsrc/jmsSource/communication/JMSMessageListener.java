/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.communication;

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
    
    private static final String SYNCH_KEY = "synchKey";
    
    public static final String MESSAGE = "Message";
    public static final String BYTES = "BytesMessage";
    public static final String TEXT = "TextMessage";
    public static final String STREAM = "StreamMessage";
    public static final String MAP = "MapMessage";
    public static final String OBJECT = "ObjectMessage";
    
    public JMSMessageListener(Context context, ExtensionWebSocketClient client) {
        this.context = context;
        this.client = client;
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
     * @throws Exception
     */
    public void open(String connectionFactoryName, String dest, boolean isQueue, String username, String password) throws NamingException, JMSException, Exception {
        synchronized (SYNCH_KEY) {
            this.destName = dest;
            
            connectionFactory = (ConnectionFactory) context.lookup(connectionFactoryName);
            if (connectionFactory == null) {
                throw new Exception("The Connection Factory named " + connectionFactoryName + " was unable to be found.");
            }
            
            connection = connectionFactory.createConnection(username, password);
            if (connection == null) {
                throw new Exception("A Connection was unable to be created using the Connection Factory named " + connectionFactoryName + ".");
            }
            
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            if (session == null) {
                throw new Exception("A Session was unable to be created.");
            }
            
            if (isQueue) {
                destination = session.createQueue(dest);
            } else {
                destination = session.createTopic(dest);
            }
            if (destination == null) {
                throw new Exception("A Destination with name " + dest + " was unable to be created.");
            }
            
            consumer = session.createConsumer(destination);
            if (consumer == null) {
                throw new Exception("A Message Producer for the Destination with name " + dest + " was unable to be created.");
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
        Map<String, Object> msgMap = formatMessage(msg);
        client.sendNotification(msgMap);
    }
    
    /**
     * A helper function to onMessage(), used to parse the incoming message and format it according to its message type
     * @param message           The retrieved message from the queue
     * @return                  A map containing the message, as well as the queue name and the JMS Message Type
     * @throws JMSException
     */
    public Map<String, Object> formatMessage(Message message) {
        Map<String, Object> msgMap = new LinkedHashMap<String, Object>();
        
        try {
            if (message instanceof TextMessage) {
                String msgText = ((TextMessage) message).getText();
                msgMap.put("message", msgText);
                msgMap.put("JMSFormat", TEXT);
                msgMap.put("destination", this.destName);
            } else if (message instanceof ObjectMessage) {
                Object msgObject = ((ObjectMessage) message).getObject();
                msgMap.put("message", msgObject);
                msgMap.put("JMSFormat", OBJECT);
                msgMap.put("destination", this.destName);
            } else if (message instanceof MapMessage) {
                // FIXME
            } else if (message instanceof StreamMessage) {
                // FIXME
            } else if (message instanceof BytesMessage) {
                // FIXME
            } else {
                // FIXME
            }
        } catch (JMSException e) {
            log.error("An error occured while parsing the received message. No message will be sent back to VANTIQ.");
        }
                
        return msgMap;
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
