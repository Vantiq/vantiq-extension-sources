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
     * @throws NamingException
     * @throws JMSException
     */
    public void setupMessageProducer(String connectionFactoryName, String dest, boolean isQueue) throws NamingException, JMSException {
        this.destName = dest;
        connectionFactory = (ConnectionFactory) context.lookup(connectionFactoryName);
        connection = connectionFactory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        if (isQueue) {
            destination = session.createQueue(dest);
        } else {
            destination = session.createTopic(dest);
        }
        producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        connection.start();
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
                // Handle it
                BytesMessage byteMessage = session.createBytesMessage();
                break;
            case TEXT:
                TextMessage textMessage = session.createTextMessage();
                textMessage.setText(message);
                producer.send(textMessage);
                break;
            case STREAM:
                // Handle it
                StreamMessage streamMessage = session.createStreamMessage();
                break;
            case MAP:
                // Handle it
                MapMessage mapMessage = session.createMapMessage();
                break;
            case OBJECT:
                // Handle it
                ObjectMessage objectMessage = session.createObjectMessage();
                break;
        }
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
