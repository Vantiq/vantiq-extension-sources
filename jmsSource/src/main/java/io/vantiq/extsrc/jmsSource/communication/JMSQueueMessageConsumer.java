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
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;
import io.vantiq.extsrc.jmsSource.exceptions.FailedJMSSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

public class JMSQueueMessageConsumer {
    
    Logger log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    
    public String destName;
    
    private Context context;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Destination destination;
    private MessageConsumer consumer;
    
    private MessageHandlerInterface messageHandler;
        
    public JMSQueueMessageConsumer(Context context, MessageHandlerInterface messageHandler) {
        this.context = context;
        this.messageHandler = messageHandler;
    }
    
    /**
     * A method used to setup the MessageConsumer for the given queue
     * @param connectionFactoryName     The name of the connection factory used to connect to the JMS Server
     * @param queue                     The name of the queue to connect to
     * @param username                  The username used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @param password                  The password used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @throws NamingException
     * @throws JMSException
     * @throws FailedJMSSetupException
     */
    public synchronized void open(String connectionFactoryName, String queue, String username, String password) throws NamingException, JMSException, FailedJMSSetupException {
        this.destName = queue;
        
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
        
        destination = session.createQueue(queue);
        if (destination == null) {
            throw new FailedJMSSetupException("A Destination with name " + queue + " was unable to be created.");
        }
        
        consumer = session.createConsumer(destination);
        if (consumer == null) {
            throw new FailedJMSSetupException("A Message Producer for the Destination with name " + queue + " was unable to be created.");
        }
        
        connection.start();
    }
    
    /**
     * Called by the JMS Class, and used to read the next available JMS Message from the associated queue
     * @return A map containing the message, as well as the queue name and the JMS Message Type
     * @throws JMSException
     * @throws UnsupportedJMSMessageTypeException
     */
    public Map<String, Object> consumeMessage() throws Exception {
        Message message = consumer.receive(1000);
        return messageHandler.parseIncomingMessage(message, destName);
    }
    
    /**
     * A method used to close the JMS Session and Connection
     * @throws JMSException
     */
    public synchronized void close() throws JMSException {
        // Closing the session and connection
        session.close();
        connection.close();
    }
}
