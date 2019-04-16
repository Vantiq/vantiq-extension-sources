/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;

import io.vantiq.extsrc.jmsSource.communication.*;
import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;
import io.vantiq.extsrc.jmsSource.exceptions.DestinationNotConfiguredException;
import io.vantiq.extsrc.jmsSource.exceptions.FailedInterfaceSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.FailedJMSSetupException;
import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

/**
 * Handles all of the interactions between the Extension Source and the JMS Server. Used to create and manage all of the
 * JMS Message Producers/Consumers/Listeners. Initialized and setup according to the source configuration, which specifies
 * the queues and/or topics to connect to, as well as the connection factory to use when connecting.
 */
public class JMS {
    
    private static final String MESSAGE_HANDLER_FQCN = "io.vantiq.extsrc.jmsSource.communication.messageHandler.BaseMessageHandler";
    private static final String SYNCH_KEY = "synchKey";
    
    Logger log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    ExtensionWebSocketClient client;
    InitialContext context;
    String connectionFactory;
    
    Map<String, JMSMessageProducer>         queueMessageProducers   = new LinkedHashMap<String, JMSMessageProducer>();
    Map<String, JMSQueueMessageConsumer>    queueMessageConsumers   = new LinkedHashMap<String, JMSQueueMessageConsumer>();
    Map<String, JMSMessageListener>         queueMessageListener    = new LinkedHashMap<String, JMSMessageListener>();
    Map<String, JMSMessageProducer>         topicMessageProducers   = new LinkedHashMap<String, JMSMessageProducer>();
    Map<String, JMSMessageListener>         topicMessageConsumers   = new LinkedHashMap<String, JMSMessageListener>();
    
    Map<String, MessageHandlerInterface>    messageHandlers         = new LinkedHashMap<String, MessageHandlerInterface>();
    
    public JMS(ExtensionWebSocketClient client, String connectionFactory) {
        this.client = client;
        this.connectionFactory = connectionFactory;
    }
    
    /**
     * Method used to set the InitialContext which will be used to lookup the ConnectionFactory in the JNDI.
     * @param jndiFactory       The FQCN of the InitialContextFactory to be used
     * @param providerURL       The URL of the JMS Server
     * @throws NamingException
     */
    public void setupInitialContext(String jndiFactory, String providerURL) throws NamingException {
        Properties properties = new Properties();
        properties.setProperty(Context.INITIAL_CONTEXT_FACTORY, jndiFactory);
        properties.setProperty(Context.PROVIDER_URL, providerURL);
        context = new InitialContext(properties);
    }
    
    public void acquireMessageHandler(String messageHandlerName) throws FailedInterfaceSetupException {
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
        MessageHandlerInterface messageHandler = (MessageHandlerInterface) object;
        messageHandlers.put("base", messageHandler);
    }
    
    /**
     * Method used to create all the Message Consumers/Producers/Listeners for the queues and topics specified 
     * in the source configuration
     * @param sender            The "sender" portion of the source configuration
     * @param receiver          The "receiver" portion of the source configuration
     * @param username          The username used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @param password          The oassword used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @throws NamingException
     * @throws JMSException
     * @throws FailedJMSSetupException
     */
    public void createProducersAndConsumers(Map<String, ?> sender, Map<String, ?> receiver, String username, String password) throws NamingException, JMSException, FailedJMSSetupException {
        List<?> senderQueues = null;
        List<?> senderTopics = null;
        List<?> receiverQueues = null;
        List<?> receiverQueueListeners = null;
        List<?> receiverTopics = null;
        
        
        // Temporary way to initialize message handler. Will change once we allow for user-created message handlers.
        try {
            acquireMessageHandler(MESSAGE_HANDLER_FQCN);
        } catch (FailedInterfaceSetupException e) {
            log.error("Unable to create a Message Handler with name: " + MESSAGE_HANDLER_FQCN);
        }
        
        // Get the queues and topics from the sender configuration
        if (sender.get("queues") instanceof List) {
            senderQueues = (List<?>) sender.get("queues");
        }
        if (sender.get("topics") instanceof List) {
            senderTopics = (List<?>) sender.get("topics");
        }
        
        // Get the queues, queueListeners and topics from the receiver configuration
        if (receiver.get("queues") instanceof List) {
            receiverQueues = (List<?>) receiver.get("queues");
        }
        if (receiver.get("queueListeners") instanceof List) {
            receiverQueueListeners = (List<?>) receiver.get("queueListeners");
        }
        if (receiver.get("topics") instanceof List) {
            receiverTopics = (List<?>) receiver.get("topics");
        }
        
        
        // Iterating through topic/queue lists and creating message producers/consumers/listeners
        synchronized(SYNCH_KEY) {
            if (senderQueues != null) {
                for (int i = 0; i < senderQueues.size(); i++) {
                    String queue = (String) senderQueues.get(i);
                    JMSMessageProducer msgProducer = new JMSMessageProducer(context, messageHandlers.get("base"));
                    msgProducer.open(connectionFactory, queue, true, username, password);
                    queueMessageProducers.put(queue, msgProducer);
                }
            }
            
            if (senderTopics != null) {
                for (int i = 0; i < senderTopics.size(); i++) {
                    String topic = (String) senderTopics.get(i);
                    JMSMessageProducer msgProducer = new JMSMessageProducer(context, messageHandlers.get("base"));
                    msgProducer.open(connectionFactory, topic, false, username, password);
                    topicMessageProducers.put(topic, msgProducer);
                }
            }
            
            if (receiverQueues != null) {
                for (int i = 0; i < receiverQueues.size(); i++) {
                    String queue = (String) receiverQueues.get(i);
                    JMSQueueMessageConsumer msgConsumer = new JMSQueueMessageConsumer(context, messageHandlers.get("base"));
                    msgConsumer.open(connectionFactory, queue, username, password);
                    queueMessageConsumers.put(queue, msgConsumer);
                }
            }
            
            if (receiverQueueListeners != null) {
                for (int i = 0; i < receiverQueueListeners.size(); i++) {
                    String queue = (String) receiverQueueListeners.get(i);
                    JMSMessageListener msgListener = new JMSMessageListener(context, client, messageHandlers.get("base"));
                    msgListener.open(connectionFactory, queue, true, username, password);
                    queueMessageListener.put(queue, msgListener);
                }
            }
            
            if (receiverTopics != null) {
                for (int i = 0; i < receiverTopics.size(); i++) {
                    String topic = (String) receiverTopics.get(i);
                    JMSMessageListener msgListener = new JMSMessageListener(context, client, messageHandlers.get("base"));
                    msgListener.open(connectionFactory, topic, false, username, password);
                    topicMessageConsumers.put(topic, msgListener);
                }
            }
        }
    }
    
    /**
     * Called by the JMSCore, and used to read the most recent message from a given queue.
     * @param queue         Name of the queue from which to read.
     * @return              A map containing the message, as well as the queue name and the JMS Message Type
     * @throws JMSException
     * @throws DestinationNotConfiguredException
     * @throws UnsupportedJMSMessageTypeException
     */
    public Map<String, Object> consumeMessage(String queue) throws Exception {
        synchronized(SYNCH_KEY) {
            JMSQueueMessageConsumer msgConsumer = queueMessageConsumers.get(queue);
            
            // To avoid getting a NullPointerException
            if (msgConsumer == null) {
                throw new DestinationNotConfiguredException();
            }
            
            return msgConsumer.consumeMessage();
        }
    }
    
    /**
     * Called  by the JMSCore, and used to send a message to the given destination
     * @param message       The message to be sent, either a string or a map
     * @param destination   The destination to which the message will be sent, (topic or queue)
     * @param messageFormat The format of the message to be sent
     * @param isQueue       A boolean flag used to get the correct message producer
     * @throws DestinationNotConfiguredException
     * @throws UnsupportedJMSMessageTypeException
     */
    public void produceMessage(Object message, String destination, String messageFormat, boolean isQueue) throws Exception {
        synchronized(SYNCH_KEY) {
            JMSMessageProducer msgProducer;
            if (isQueue) {
                msgProducer = queueMessageProducers.get(destination);
            } else {
                msgProducer = topicMessageProducers.get(destination);
            }
            
            // To avoid getting a NullPointerException
            if (msgProducer == null) {
                throw new DestinationNotConfiguredException();
            }
            
            msgProducer.produceMessage(message, messageFormat);
        }
    }
    
    /**
     * A method used to close all of the resources being used by the message producers/consumers/listeners
     */
    public void close() {
        synchronized(SYNCH_KEY) {
            for (JMSMessageProducer producer : queueMessageProducers.values()) {
                try {
                    producer.close();
                } catch (JMSException e) {
                    log.error("An error occured while attempting to close the JMS Session or Connection associated with the "
                            + "MessageProducer for queue: " + producer.destName + ". ", e);
                }
            }
            
            for (JMSQueueMessageConsumer consumer : queueMessageConsumers.values()) {
                try {
                    consumer.close();
                } catch (JMSException e) {
                    log.error("An error occured while attempting to close the JMS Session or Connection associated with the "
                            + "MessageConsumer for queue: " + consumer.destName + ". ", e);
                }
            }
            
            for (JMSMessageListener listener : queueMessageListener.values()) {
                try {
                    listener.close();
                } catch (JMSException e) {
                    log.error("An error occured while attempting to close the JMS Session or Connection associated with the "
                            + "MessageListener for queue: " + listener.destName + ". ", e);
                }
            }
            
            for (JMSMessageProducer producer : topicMessageProducers.values()) {
                try {
                    producer.close();
                } catch (JMSException e) {
                    log.error("An error occured while attempting to close the JMS Session or Connection associated with the "
                            + "MessageProducer for topic: " + producer.destName + ". ", e);
                }
            }
            
            for (JMSMessageListener listener : topicMessageConsumers.values()) {
                try {
                    listener.close();
                } catch (JMSException e) {
                    log.error("An error occured while attempting to close the JMS Session or Connection associated with the "
                            + "MessageListener for topic: " + listener.destName + ". ", e);
                }
            }
        }
    }
    
}
