/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.communication;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;

public class JMSMessageProducer {
    
    public String destName;
    
    private Context context;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Destination destination;
    private MessageProducer producer;
    
    private static final String SYNCH_KEY = "synchKey";
    
    public static final String MESSAGE = "message";
    public static final String BYTES = "bytes";
    public static final String TEXT = "text";
    public static final String STREAM = "stream";
    public static final String MAP = "map";
    public static final String OBJECT = "object";
    
    public JMSMessageProducer(Context context) {
        this.context = context;
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
            
            producer = session.createProducer(destination);
            if (producer == null) {
                throw new Exception("A Message Producer for the Destination with name " + dest + " was unable to be created.");
            }
            
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            connection.start();
        }
    }
    
    /**
     * Called by the JMS Class, and used to send the provided message to the associated destination, (topic or queue),
     * using whatever format was specified
     * @param message           The message to be sent
     * @param messageFormat     The format (JMS Message Type) of the message to be sent
     * @throws JMSException
     */
    public void produceMessage(String message, String messageFormat) throws JMSException {
        switch(messageFormat) {
            case MESSAGE:
                Message baseMessage = session.createMessage();
                producer.send(baseMessage);
                break;
            case BYTES:
                // FIXME
                BytesMessage byteMessage = session.createBytesMessage();
                break;
            case TEXT:
                TextMessage textMessage = session.createTextMessage();
                textMessage.setText(message);
                producer.send(textMessage);
                break;
            case STREAM:
                // FIXME
                StreamMessage streamMessage = session.createStreamMessage();
                break;
            case MAP:
                // FIXME
                MapMessage mapMessage = session.createMapMessage();
                break;
            case OBJECT:
                // FIXME
                ObjectMessage objectMessage = session.createObjectMessage();
                break;
            default:
                // FIXME
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
