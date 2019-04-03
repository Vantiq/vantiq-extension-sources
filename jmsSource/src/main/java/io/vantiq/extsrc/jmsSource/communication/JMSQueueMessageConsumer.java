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
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;

public class JMSQueueMessageConsumer {
    
    public String destName;
    
    private Context context;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Destination destination;
    private MessageConsumer consumer;
    
    public static final String MESSAGE = "Message";
    public static final String BYTES = "BytesMessage";
    public static final String TEXT = "TextMessage";
    public static final String STREAM = "StreamMessage";
    public static final String MAP = "MapMessage";
    public static final String OBJECT = "ObjectMessage";
    
    public JMSQueueMessageConsumer(Context context) {
        this.context = context;
    }
    
    /**
     * A method used to setup the MessageConsumer for the given queue
     * @param connectionFactoryName     The name of the connection factory used to connect to the JMS Server
     * @param queue                     The name of the queue to connect to
     * @param username                  The username used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @param password                  The password used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @throws NamingException
     * @throws JMSException
     */
    public void setupQueueConsumer(String connectionFactoryName, String queue, String username, String password) throws NamingException, JMSException {
        this.destName = queue;
        connectionFactory = (ConnectionFactory) context.lookup(connectionFactoryName);
        connection = connectionFactory.createConnection(username, password);
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        destination = session.createQueue(queue);
        consumer = session.createConsumer(destination);
        connection.start();
    }
    
    /**
     * Called by the JMS Class, and used to read the most recent JMS Message from the associated queue
     * @return A map containing the message, as well as the queue name and the JMS Message Type
     * @throws JMSException
     */
    public Map<String, Object> consumeMessage() throws JMSException {
        Message message = consumer.receive(1000);
        return formatMessage(message);
    }
    
    /**
     * A helper function to consumeMessage(), used to parse the incoming message and format it according to its message type
     * @param message           The retrieved message from the queue
     * @return                  A map containing the message, as well as the queue name and the JMS Message Type
     * @throws JMSException
     */
    public Map<String, Object> formatMessage(Message message) throws JMSException {
        Map<String, Object> msgMap = new LinkedHashMap<String, Object>();
        
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
            
        } else if (message instanceof StreamMessage) {
            
        } else if (message instanceof BytesMessage) {
            
        } else {
            
        }
                
        return msgMap;
    }
    
    /**
     * A method used to close the JMS Session and Connection
     * @throws JMSException
     */
    public void close() throws JMSException {
        // Closing the session and connection
        session.close();
        connection.close();
    }
}
