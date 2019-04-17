/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.communication;


import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;
import io.vantiq.extsrc.jmsSource.exceptions.FailedJMSSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

public class JMSMessageProducer {
    
    Logger log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    
    public String destName;
    
    private Context context;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Destination destination;
    private MessageProducer producer;
    
    private MessageHandlerInterface messageHandler;
    
    public JMSMessageProducer(Context context, MessageHandlerInterface messageHandler) {
        this.context = context;
        this.messageHandler = messageHandler;
    }
    
    /**
     * A method used to setup the MessageProducer for the given destination (topic or queue)
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
        
        producer = session.createProducer(destination);
        if (producer == null) {
            throw new FailedJMSSetupException("A Message Producer for the Destination with name " + dest + " was unable to be created.");
        }
        
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        connection.start();
    }
    
    /**
     * Called by the JMS Class, and used to send the provided message to the associated destination, (topic or queue),
     * using whatever format was specified
     * @param message           The message to be sent, either a string or a map
     * @param messageFormat     The format (JMS Message Type) of the message to be sent
     * @throws JMSException
     * @throws UnsupportedJMSMessageTypeException
     */
    public void produceMessage(Object message, String messageFormat) throws Exception {
        Message jmsMessage = messageHandler.formatOutgoingMessage(message, messageFormat, session);
        producer.send(jmsMessage);
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
