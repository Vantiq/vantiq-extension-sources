/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

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

/**
 * Handles all of the interactions between the Extension Source and the JMS Server. Used to create and manage all of the
 * JMS Message Producers/Consumers/Listeners. Initialized and setup according to the source configuration, which specifies
 * the queues and/or topics to connect to, as well as the connection factory to use when connecting.
 */
public class JMS {
    
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
     * Method used to create all the Message Consumers/Producers/Listeners for the queues and topics specified 
     * in the source configuration
     * @param sender            The "sender" portion of the source configuration
     * @param receiver          The "receiver" portion of the source configuration
     * @param username          The username used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @param password          The oassword used to create the JMS Connection, (or null if JMS Server does not require auth)
     * @throws NamingException
     * @throws JMSException
     * @throws Exception
     */
    public void createProducersAndConsumers(Map<String, ?> sender, Map<String, ?> receiver, String username, String password) throws NamingException, JMSException, Exception {
        List<?> senderQueues = null;
        List<?> senderTopics = null;
        List<?> receiverQueues = null;
        List<?> receiverQueueListeners = null;
        List<?> receiverTopics = null;
        
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
                    JMSMessageProducer msgProducer = new JMSMessageProducer(context);
                    msgProducer.open(connectionFactory, queue, true, username, password);
                    queueMessageProducers.put(queue, msgProducer);
                }
            }
            
            if (senderTopics != null) {
                for (int i = 0; i < senderTopics.size(); i++) {
                    String topic = (String) senderTopics.get(i);
                    JMSMessageProducer msgProducer = new JMSMessageProducer(context);
                    msgProducer.open(connectionFactory, topic, false, username, password);
                    topicMessageProducers.put(topic, msgProducer);
                }
            }
            
            if (receiverQueues != null) {
                for (int i = 0; i < receiverQueues.size(); i++) {
                    String queue = (String) receiverQueues.get(i);
                    JMSQueueMessageConsumer msgConsumer = new JMSQueueMessageConsumer(context);
                    msgConsumer.open(connectionFactory, queue, username, password);
                    queueMessageConsumers.put(queue, msgConsumer);
                }
            }
            
            if (receiverQueueListeners != null) {
                for (int i = 0; i < receiverQueueListeners.size(); i++) {
                    String queue = (String) receiverQueueListeners.get(i);
                    JMSMessageListener msgListener = new JMSMessageListener(context, client);
                    msgListener.open(connectionFactory, queue, true, username, password);
                    queueMessageListener.put(queue, msgListener);
                }
            }
            
            if (receiverTopics != null) {
                for (int i = 0; i < receiverTopics.size(); i++) {
                    String topic = (String) receiverTopics.get(i);
                    JMSMessageListener msgListener = new JMSMessageListener(context, client);
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
     */
    public Map<String, Object> consumeMessage(String queue) throws JMSException {
        synchronized(SYNCH_KEY) {
            JMSQueueMessageConsumer msgConsumer = queueMessageConsumers.get(queue);
            return msgConsumer.consumeMessage();
        }
    }
    
    /**
     * Called  by the JMSCore, and used to send a message to the given destination
     * @param message       The message to be sent
     * @param destination   The destination to which the message will be sent, (topic or queue)
     * @param messageFormat The format of the message to be sent
     * @param isQueue       A boolean flag used to get the correct message producer
     * @throws JMSException
     */
    public void produceMessage(String message, String destination, String messageFormat, boolean isQueue) throws JMSException {
        synchronized(SYNCH_KEY) {
            JMSMessageProducer msgProducer;
            if (isQueue) {
                msgProducer = queueMessageProducers.get(destination);
            } else {
                msgProducer = topicMessageProducers.get(destination);
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
