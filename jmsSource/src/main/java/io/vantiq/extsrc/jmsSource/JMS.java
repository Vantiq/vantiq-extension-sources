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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

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
    
    private static final String BASE_MESSAGE_HANDLER_FQCN = "io.vantiq.extsrc.jmsSource.communication.messageHandler.BaseMessageHandler";

    Logger log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    ExtensionWebSocketClient client;
    InitialContext context;
    String connectionFactory;
    
    Map<String, JMSMessageProducer>         queueMessageProducers   = new ConcurrentHashMap<String, JMSMessageProducer>();
    Map<String, JMSQueueMessageConsumer>    queueMessageConsumers   = new ConcurrentHashMap<String, JMSQueueMessageConsumer>();
    Map<String, JMSMessageListener>         queueMessageListener    = new ConcurrentHashMap<String, JMSMessageListener>();
    Map<String, JMSMessageProducer>         topicMessageProducers   = new ConcurrentHashMap<String, JMSMessageProducer>();
    Map<String, JMSMessageListener>         topicMessageConsumers   = new ConcurrentHashMap<String, JMSMessageListener>();
        
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
    
    /**
     * Method used to initialize the given implementation of the MessageHandlerInterface, and store it in the messageHandlers Map
     * @param messageHandlerName    The fully qualified class name of the MessageHandlerInterface implementation
     * @throws FailedInterfaceSetupException
     */
    public MessageHandlerInterface acquireMessageHandler(String messageHandlerName) throws FailedInterfaceSetupException {
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
        return (MessageHandlerInterface) object;
    }
    
    /**
     * Method used to create all the Message Consumers/Producers/Listeners for the queues and topics specified 
     * in the source configuration
     * @param sender            The "sender" portion of the source configuration
     * @param receiver          The "receiver" portion of the source configuration
     * @param username          The username used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @param password          The password used to create the JMS Connection, (or null if JMS Server does not require auth)
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
        
        Map senderQueueMessageHandlers = null;
        Map senderTopicMessageHandlers = null;
        Map receiverQueueMessageHandlers = null;
        Map receiverQueueListenerMessageHandlers = null;
        Map receiverTopicMessageHandlers = null;
        
        // Get the queues and topics from the sender configuration
        if (sender.get("queues") instanceof List) {
            senderQueues = (List<?>) sender.get("queues");
        }
        if (sender.get("topics") instanceof List) {
            senderTopics = (List<?>) sender.get("topics");
        }
        
        // Get message handlers from sender configuration
        if (sender.get("messageHandler") instanceof Map) {
            Map senderMessageHandlers = (Map) sender.get("messageHandler");
            
            if (senderMessageHandlers.get("queues") instanceof Map) {
                senderQueueMessageHandlers = (Map) senderMessageHandlers.get("queues");
            }
            if (senderMessageHandlers.get("topics") instanceof Map) {
                senderTopicMessageHandlers = (Map) senderMessageHandlers.get("topics");
            }
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
        
        // Get message handlers from receiver configuration
        if (receiver.get("messageHandler") instanceof Map) {
            Map receiverMessageHandlers = (Map) receiver.get("messageHandler");
            
            if (receiverMessageHandlers.get("queues") instanceof Map) {
                receiverQueueMessageHandlers = (Map) receiverMessageHandlers.get("queues");
            }
            if (receiverMessageHandlers.get("queues") instanceof Map) {
                receiverQueueListenerMessageHandlers = (Map) receiverMessageHandlers.get("queueListeners");
            }
            if (receiverMessageHandlers.get("topics") instanceof Map) {
                receiverTopicMessageHandlers = (Map) receiverMessageHandlers.get("topics");
            }
        }
        
        // Iterating through topic/queue lists and creating message producers/consumers/listeners
        if (senderQueues != null) {
            for (int i = 0; i < senderQueues.size(); i++) {
                String queue = (String) senderQueues.get(i);
                
                // Get the Message Handler, or use base if no custom Message Handler was specified
                String messageHandlerFQCN = BASE_MESSAGE_HANDLER_FQCN;
                MessageHandlerInterface messageHandler;
                if (senderQueueMessageHandlers != null &&  senderQueueMessageHandlers.get(queue) instanceof String) {
                    messageHandlerFQCN = (String) senderQueueMessageHandlers.get(queue);
                }
                
                try {
                    messageHandler = acquireMessageHandler(messageHandlerFQCN);
                } catch (FailedInterfaceSetupException e) {
                    log.error("An error occured while trying to initialize the custom MessageHandlerInterface implementation"
                            + " named: " + messageHandlerFQCN + ". No Message Producer will be made for queue: " + queue);
                    continue;
                }
                
                JMSMessageProducer msgProducer = new JMSMessageProducer(context, messageHandler);
                msgProducer.open(connectionFactory, queue, true, username, password);
                queueMessageProducers.put(queue, msgProducer);
            }
        }
            
        if (senderTopics != null) {
            for (int i = 0; i < senderTopics.size(); i++) {
                String topic = (String) senderTopics.get(i);
                
                // Get the Message Handler, or use base if no custom Message Handler was specified
                String messageHandlerFQCN = BASE_MESSAGE_HANDLER_FQCN;
                MessageHandlerInterface messageHandler;
                if (senderTopicMessageHandlers != null && senderTopicMessageHandlers.get(topic) instanceof String) {
                    messageHandlerFQCN = (String) senderTopicMessageHandlers.get(topic);
                }
                
                try {
                    messageHandler = acquireMessageHandler(messageHandlerFQCN);
                } catch (FailedInterfaceSetupException e) {
                    log.error("An error occured while trying to initialize the custom MessageHandlerInterface implementation"
                            + " named: " + messageHandlerFQCN + ". No Message Producer will be made for topic: " + topic);
                    continue;
                }
                
                JMSMessageProducer msgProducer = new JMSMessageProducer(context, messageHandler);
                msgProducer.open(connectionFactory, topic, false, username, password);
                topicMessageProducers.put(topic, msgProducer);
            }
        }
            
        if (receiverQueues != null) {
            for (int i = 0; i < receiverQueues.size(); i++) {
                String queue = (String) receiverQueues.get(i);
                
                // Get the Message Handler, or use base if no custom Message Handler was specified
                String messageHandlerFQCN = BASE_MESSAGE_HANDLER_FQCN;
                MessageHandlerInterface messageHandler;
                if (receiverQueueMessageHandlers != null &&  receiverQueueMessageHandlers.get(queue) instanceof String) {
                    messageHandlerFQCN = (String) receiverQueueMessageHandlers.get(queue);
                }
                
                try {
                    messageHandler = acquireMessageHandler(messageHandlerFQCN);
                } catch (FailedInterfaceSetupException e) {
                    log.error("An error occured while trying to initialize the custom MessageHandlerInterface implementation"
                            + " named: " + messageHandlerFQCN + ". No Message Consumer will be made for queue: " + queue);
                    continue;
                }
                
                JMSQueueMessageConsumer msgConsumer = new JMSQueueMessageConsumer(context, messageHandler);
                msgConsumer.open(connectionFactory, queue, username, password);
                queueMessageConsumers.put(queue, msgConsumer);
            }
        }
            
        if (receiverQueueListeners != null) {
            for (int i = 0; i < receiverQueueListeners.size(); i++) {
                String queue = (String) receiverQueueListeners.get(i);
                
                // Get the Message Handler, or use base if no custom Message Handler was specified
                String messageHandlerFQCN = BASE_MESSAGE_HANDLER_FQCN;
                MessageHandlerInterface messageHandler;
                if (receiverQueueListenerMessageHandlers != null &&  receiverQueueListenerMessageHandlers.get(queue) instanceof String) {
                    messageHandlerFQCN = (String) receiverQueueListenerMessageHandlers.get(queue);
                }
                
                try {
                    messageHandler = acquireMessageHandler(messageHandlerFQCN);
                } catch (FailedInterfaceSetupException e) {
                    log.error("An error occured while trying to initialize the custom MessageHandlerInterface implementation"
                            + " named: " + messageHandlerFQCN + ". No Message Listener will be made for queue: " + queue);
                    continue;
                }
                
                JMSMessageListener msgListener = new JMSMessageListener(context, client, messageHandler);
                msgListener.open(connectionFactory, queue, true, username, password);
                queueMessageListener.put(queue, msgListener);
            }
        }
        
        if (receiverTopics != null) {
            for (int i = 0; i < receiverTopics.size(); i++) {
                String topic = (String) receiverTopics.get(i);
                
                // Get the Message Handler, or use base if no custom Message Handler was specified
                String messageHandlerFQCN = BASE_MESSAGE_HANDLER_FQCN;
                MessageHandlerInterface messageHandler;
                if (receiverTopicMessageHandlers != null &&  receiverTopicMessageHandlers.get(topic) instanceof String) {
                    messageHandlerFQCN = (String) receiverTopicMessageHandlers.get(topic);
                }
                
                try {
                    messageHandler = acquireMessageHandler(messageHandlerFQCN);
                } catch (FailedInterfaceSetupException e) {
                    log.error("An error occured while trying to initialize the custom MessageHandlerInterface implementation"
                            + " named: " + messageHandlerFQCN + ". No Message Listener will be made for topic: " + topic);
                    continue;
                }
                
                JMSMessageListener msgListener = new JMSMessageListener(context, client, messageHandler);
                msgListener.open(connectionFactory, topic, false, username, password);
                topicMessageConsumers.put(topic, msgListener);
            }
        }
    }
    
    /**
     * Called by the JMSCore, and used to read the most recent message from a given queue.
     * @param queue         Name of the queue from which to read.
     * @return              A map containing the message, queue name, message headers and properties
     * @throws JMSException
     * @throws DestinationNotConfiguredException
     * @throws UnsupportedJMSMessageTypeException
     */
    public Map<String, Object> consumeMessage(String queue) throws Exception {
        JMSQueueMessageConsumer msgConsumer = queueMessageConsumers.get(queue);
        
        // To avoid getting a NullPointerException
        if (msgConsumer == null) {
            throw new DestinationNotConfiguredException();
        }
        
        return msgConsumer.consumeMessage();
    }
    
    /**
     * Called  by the JMSCore, and used to send a message to the given destination
     * @param messageMap    A map containing the message headers, properties, and body
     * @param destination   The destination to which the message will be sent, (topic or queue)
     * @param isQueue       A boolean flag used to get the correct message producer
     * @throws DestinationNotConfiguredException
     * @throws UnsupportedJMSMessageTypeException
     */
    public void produceMessage(Map<String, Object> messageMap, String destination, boolean isQueue) throws Exception {
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
        
        msgProducer.produceMessage(messageMap);
    }
    
    /**
     * A method used to close all of the resources being used by the message producers/consumers/listeners
     */
    public void close() {
        for (JMSMessageProducer producer : queueMessageProducers.values()) {
            try {
                producer.close();
            } catch (JMSException e) {
                log.error("An error occured while attempting to close the JMS Session or Connection associated with the "
                        + "MessageProducer for queue: " + producer.destName + ". ", e);
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
