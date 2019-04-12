/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.vantiq.client.SubscriptionCallback;
import io.vantiq.client.SubscriptionMessage;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;

public class TestJMS extends TestJMSBase {
    
    static final int CORE_START_TIMEOUT = 10;
    
    static JMSCore core;
    static JMS jms;
    static Vantiq vantiq;
    
    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    String sourceNotificationMessage;
    
    StandardOutputCallback sourceNotificationCallback = new StandardOutputCallback();
    
    @Before
    public void setup() {
        vantiq = new Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
        
        // Create a subscription to source in order to check if message was received as a notification
        vantiq.subscribe("sources", testSourceName, null, sourceNotificationCallback);
    }
    
    @After
    public void unsubscribe() {
        vantiq.unsubscribeAll();
    }
        
    @AfterClass
    public static void tearDown() {
        // Delete source from VANTIQ
        deleteSource();
        
        // Close JMSCore if still open
        if (core != null) {
            core.close();
            core = null;
        }
        // Close JMS if still open
        if (jms != null) {
            jms.close();
            jms = null;
        }
    }
    
    @Test
    public void testProduceAndConsumeQueueMessage() {
        assumeTrue(jmsDriverLoc != null && testJMSURL != null && testJMSConnectionFactory != null && testJMSInitialContext != null
                && testJMSQueue != null);
        
        // Setting up sender configuration to initialize the JMS Class
        Map<String, List> sender = new LinkedHashMap<>();
        Map<String, List> receiver = new LinkedHashMap<>();
        List<String> queues = new ArrayList<>();
        
        queues.add(testJMSQueue);
        
        sender.put("queues", queues);
        receiver.put("queues", queues);
        
        // Construction the JMS Class
        jms = new JMS(null, testJMSConnectionFactory);
        try {
            jms.setupInitialContext(testJMSInitialContext, testJMSURL);
        } catch (NamingException e) {
            fail("Should not throw a NamingException when setting up JMS Context. " + e.getMessage());
        }
        
        try {
            jms.createProducersAndConsumers(sender, receiver, null, null);
        } catch (Exception e) {
            fail("Should not throw an Exception when creating message producers/consumers/listeners.");
        }
        
        // Sending message to the queue
        Date date = new Date();
        String message = "A message sent at time: " + dateFormat.format(date);
        try {
            jms.produceMessage(message, testJMSQueue, "TextMessage", true);
        } catch (Exception e) {
            fail("Should not throw a Exception when sending message to queue.");
        }
        
        // Reading message from the queue, and checking that it is equal to the message that was sent
        try {
            Map<String, Object> queueMessage = jms.consumeMessage(testJMSQueue);
            assert ((String) queueMessage.get("message")).equals(message);
        } catch (Exception e) {
            fail("Should not throw an Exception when consuming message from queue.");
        }
        
        jms.close();
    }
    
    @Test
    public void testTopicMessageListener() throws InterruptedException {
        checkAllJMSProperties();
        testMessageListenerHelper("topic", testJMSTopic);
    }
    
    @Test
    public void testQueueMessageListener() throws InterruptedException {
        checkAllJMSProperties();
        testMessageListenerHelper("queue", testJMSQueue);
    }
    
    public void testMessageListenerHelper(String dest, String destName) throws InterruptedException {
        // Check that Source and Type do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
        
        // Setup a VANTIQ JMS Source, and start running the core
        setupSource(createSourceDef(true));
        
        // Create message to send
        Date date = new Date();
        String message = "A message sent at time: " + dateFormat.format(date);
        
        // Publish message to the source (send to queue)
        Map<String,Object> sendToTopicParams = new LinkedHashMap<String,Object>();
        sendToTopicParams.put("message", message);
        sendToTopicParams.put(dest, destName);
        sendToTopicParams.put("JMSFormat", "TextMessage");
        vantiq.publish("sources", testSourceName, sendToTopicParams);
        
        // Wait to make sure message has been parsed by subscription callback
        Thread.sleep(2000);
        
        // Check that the message was received and is correct
        assert sourceNotificationMessage.equals(message);
        
        // Delete the Source from VANTIQ
        deleteSource();
        core.stop();       
    }
    
    @Test
    public void testVantiqQuery() {
        checkAllJMSProperties();
        
        // Check that Source and Type do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
        
        // Setup a VANTIQ JMS Source, and start running the core
        setupSource(createSourceDef(false));
        
        // Create message to send
        Date date = new Date();
        String message = "A message sent at time: " + dateFormat.format(date);
        
        // Publish message to the source (send to queue)
        Map<String,Object> sendToQueueParams = new LinkedHashMap<String,Object>();
        sendToQueueParams.put("message", message);
        sendToQueueParams.put("queue", testJMSQueue);
        sendToQueueParams.put("JMSFormat", "TextMessage");
        vantiq.publish("sources", testSourceName, sendToQueueParams);
        
        // Query with no operation set
        Map<String,Object> queryParams = new LinkedHashMap<String,Object>();
        queryParams.put("queue", testJMSQueue);
        VantiqResponse queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should not query successfully
        assert queryResponse.hasErrors();
        
        // Query with a jibberish operation set
        queryParams.put("operation", "jibberish");
        queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should not query successfully
        assert queryResponse.hasErrors();
        
        // Query with a valid operation set
        queryParams.put("operation", "read");
        queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should query successfully
        assert !queryResponse.hasErrors();
        JsonObject responseBody = (JsonObject) queryResponse.getBody();
        String responseMessage = (String) responseBody.get("message").getAsString();
        assert responseMessage.equals(message);
        
        // Delete the Source from VANTIQ
        deleteSource();
        core.stop();  
    }
    
    @Test
    public void testOddballJMSMessageTypes() {
        checkAllJMSProperties();
        
        // Check that Source and Type do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
        
        // Setup a VANTIQ JMS Source, and start running the core
        setupSource(createSourceDef(false));
        
        // Create message to send (MapMessage)
        Date date = new Date();
        String message = dateFormat.format(date);
        Map<String, String> messageMap = new LinkedHashMap<String,String>();
        messageMap.put("date", message);
        
        // Publish message to the source, (send to queue), as a MapMessage
        Map<String,Object> sendToQueueParams = new LinkedHashMap<String,Object>();
        sendToQueueParams.put("message", messageMap);
        sendToQueueParams.put("queue", testJMSQueue);
        sendToQueueParams.put("JMSFormat", "MapMessage");
        vantiq.publish("sources", testSourceName, sendToQueueParams);
        
        // Query the source
        Map<String,Object> queryParams = new LinkedHashMap<String,Object>();
        queryParams.put("queue", testJMSQueue);
        queryParams.put("operation", "read");
        VantiqResponse queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should query successfully
        assert !queryResponse.hasErrors();
        JsonObject responseBody = (JsonObject) queryResponse.getBody();
        JsonObject responseMessage = responseBody.get("message").getAsJsonObject();
        assert responseMessage.get("date").getAsString().equals(message);
        
        // Create message to send (Message)
        sendToQueueParams.put("message", "jibberish");
        sendToQueueParams.put("JMSFormat", "Message");
        vantiq.publish("sources", testSourceName, sendToQueueParams);
        
        // Query the source
        queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should query successfully
        assert !queryResponse.hasErrors();
        responseBody = (JsonObject) queryResponse.getBody();
        JsonElement responseMessageElement = responseBody.get("message");
        assertTrue(responseMessageElement.isJsonNull());
        
        // Delete the Source from VANTIQ
        deleteSource();
        core.stop();  
    }
    
    @Test
    public void testIncorrectOddballJMSTypes() {
        // Check that Source and Type do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
        
        // Setup a VANTIQ JMS Source, and start running the core
        setupSource(createSourceDef(false));
        
        // Create message to send
        Date date = new Date();
        String message = dateFormat.format(date);
        Map<String, String> messageMap = new LinkedHashMap<String,String>();
        messageMap.put("date", message);
        
        // Publish message to the source, (send to queue), as a TextMessage (wrong type)
        Map<String,Object> sendToQueueParams = new LinkedHashMap<String,Object>();
        sendToQueueParams.put("message", messageMap);
        sendToQueueParams.put("queue", testJMSQueue);
        sendToQueueParams.put("JMSFormat", "TextMessage");
        vantiq.publish("sources", testSourceName, sendToQueueParams);
        
        // Query the source
        Map<String,Object> queryParams = new LinkedHashMap<String,Object>();
        queryParams.put("queue", testJMSQueue);
        queryParams.put("operation", "read");
        VantiqResponse queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should query successfully, but message should be null since it was unable to send
        assert !queryResponse.hasErrors();
        JsonObject responseBody = (JsonObject) queryResponse.getBody();
        JsonElement responseMessageElement = responseBody.get("message");
        assertTrue(responseMessageElement.isJsonNull());
        
        // Create message to send (MapMessage, when it should be a TextMessage)
        sendToQueueParams.put("message", "some text");
        sendToQueueParams.put("JMSFormat", "MapMessage");
        vantiq.publish("sources", testSourceName, sendToQueueParams);
        
        // Query the source
        queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should query successfully, but message should be null since it was unable to send
        assert !queryResponse.hasErrors();
        responseBody = (JsonObject) queryResponse.getBody();
        responseMessageElement = responseBody.get("message");
        assertTrue(responseMessageElement.isJsonNull());
        
        // Create message to send (unsupported JMSFormat)
        sendToQueueParams.put("message", "jibberish");
        sendToQueueParams.put("JMSFormat", "BytesMessage");
        vantiq.publish("sources", testSourceName, sendToQueueParams);
        
        // Query the source
        queryResponse = vantiq.query(testSourceName, queryParams);
        
        // Should query successfully, but message should be null since it was unable to send
        assert !queryResponse.hasErrors();
        responseBody = (JsonObject) queryResponse.getBody();
        responseMessageElement = responseBody.get("message");
        assertTrue(responseMessageElement.isJsonNull());
        
        // Delete the Source from VANTIQ
        deleteSource();
        core.stop();  
    }
     
    // ================================================= Helper functions =================================================
    
    public static void checkAllJMSProperties() {
        assumeTrue(jmsDriverLoc != null && testJMSURL != null && testJMSConnectionFactory != null && testJMSInitialContext != null
                && testJMSQueue != null && testJMSTopic != null && testAuthToken != null && testVantiqServer != null);
    }
    
    public static boolean checkSourceExists() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.select("system.sources", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }
    
    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new JMSCore(testSourceName, testAuthToken, testVantiqServer);
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static Map<String,Object> createSourceDef(boolean queueListener) {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> jmsConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        Map<String,Object> sender = new LinkedHashMap<String,Object>();
        Map<String,Object> receiver = new LinkedHashMap<String,Object>();
        
        // Setting up general config options
        general.put("username", testJMSUsername);
        general.put("password", testJMSPassword);
        general.put("providerURL", testJMSURL);
        general.put("connectionFactory", testJMSConnectionFactory);
        general.put("initialContext", testJMSInitialContext);
        
        // Setting up lists for sender/receiver configs
        List<String> queues = new ArrayList<String>();
        List<String> topics = new ArrayList<String>();
        queues.add(testJMSQueue);
        topics.add(testJMSTopic);
        
        // Setting up sender config options
        sender.put("queues", queues);
        sender.put("topics", topics);
        
        // Setting up receiver config options
        if (queueListener) {
            receiver.put("queueListeners", queues);
        } else {
            receiver.put("queues", queues);
        }
        receiver.put("topics", topics);
        
        // Placing general config options in "jmsConfig"
        jmsConfig.put("general", general);
        jmsConfig.put("sender", sender);
        jmsConfig.put("receiver", receiver);
        
        // Putting jmsConfig in the source configuration
        sourceConfig.put("jmsConfig", jmsConfig);
        
        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", "JMS");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
    }
    
    public static void deleteSource() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testSourceName);
        vantiq.delete("system.sources", where);
    }
    
    public class StandardOutputCallback implements SubscriptionCallback {

        @Override
        public void onConnect() {}

        @Override
        public void onMessage(SubscriptionMessage message) {
            Map <String, Object> messageBody = (Map<String, Object>) message.getBody();
            Map <String, Object> sourceNotification = (Map<String, Object>) messageBody.get("value");
            sourceNotificationMessage = (String) sourceNotification.get("message");
        }

        @Override
        public void onError(String error) {}

        @Override
        public void onFailure(Throwable t) {}
        
    }
}
