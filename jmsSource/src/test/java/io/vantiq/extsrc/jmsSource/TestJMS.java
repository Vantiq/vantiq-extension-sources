/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.naming.NamingException;

import org.junit.Before;
import org.junit.Test;

import io.vantiq.client.Vantiq;

public class TestJMS extends TestJMSBase {
    
    static JMSCore core;
    static JMS jms;
    static Vantiq vantiq;
    
    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    
    @Before
    public void setup() {
        vantiq = new Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
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
        } catch (NamingException e) {
            fail("Should not throw a NamingException when creating message producers/consumers/listeners.");
        } catch (JMSException e) {
            fail("Should not throw a JMSException when creating message producers/consumers/listeners.");
        }
        
        // Sending message to the queue
        Date date = new Date();
        String message = "A message sent at time: " + dateFormat.format(date);
        try {
            jms.produceMessage(message, testJMSQueue, "text", true);
        } catch (JMSException e) {
            fail("Should not throw a JMSException when sending message to queue.");
        }
        
        // Reading message from the queue, and checking that it is equal to the message that was sent
        try {
            Map<String, Object> queueMessage = jms.consumeMessage(testJMSQueue);
            assert ((String) queueMessage.get("message")).equals(message);
        } catch (JMSException e) {
            fail("Should not throw a JMSException when consuming message from queue.");
        }
        
        jms.close();
    }
    
//    @Test
//    public void testMessageListener() {
//        
//    }
}
