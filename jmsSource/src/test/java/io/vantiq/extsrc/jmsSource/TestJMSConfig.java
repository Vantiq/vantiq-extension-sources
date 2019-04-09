/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestJMSConfig extends TestJMSBase {

    JMSHandleConfiguration handler;
    
    NoSendJMSCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    Map<String, Object> general;
    Map<String, Object> sender;
    Map<String, Object> receiver;
        
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        nCore = new NoSendJMSCore(sourceName, authToken, targetVantiqServer);
        handler = new JMSHandleConfiguration(nCore);
    }
    
    @After
    public void tearDown() {
        nCore.stop();
    }
    
    @Test
    public void testEmptyConfig() {
        Map conf = new LinkedHashMap<>();
        sendConfig(conf);
        assertTrue("Should fail on empty configuration", configIsFailed());
    }
    
    @Test
    public void testMissingGeneral() {
        Map conf = minimalConfig();
        conf.remove("general");
        sendConfig(conf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    @Test
    public void testMissingSender() {
        checkMinimalJMSProperties();
        
        Map conf = minimalConfig();
        conf.remove("sender");
        sendConfig(conf);
        assertTrue("Should fail if missing 'sender' configuration", configIsFailed());
    }
    
    @Test
    public void testMissingReceiver() {
        checkMinimalJMSProperties();
        
        Map conf = minimalConfig();
        conf.remove("receiver");
        sendConfig(conf);
        assertTrue("Should fail if missing 'receiver' configuration", configIsFailed());
    }
    
    @Test
    public void testEmptyGeneral() {
        Map conf = minimalConfig();
        conf.put("general", null);
        sendConfig(conf);
        assertTrue("Should fail on empty 'general' configuration", configIsFailed());
    }

    @Test
    public void testMissingURL() {
        checkMinimalJMSProperties();
        
        Map conf = partialGeneralConfig("providerURL");
        sendConfig(conf);
        assertTrue("Should fail if missing the providerURL", configIsFailed());
    }
    
    @Test
    public void testMissingConnectionFactory() {
        checkMinimalJMSProperties();
        
        Map conf = partialGeneralConfig("connectionFactory");
        sendConfig(conf);
        assertTrue("Should fail if missing the connectionFactory", configIsFailed());
    }
    
    @Test
    public void testMissingInitialContext() {
        checkMinimalJMSProperties();
        
        Map conf = partialGeneralConfig("initialContext");
        sendConfig(conf);
        assertTrue("Should fail if missing the initialContext", configIsFailed());
    }
    
    @Test
    public void testMissingUsernameAndPassword() {
        checkMinimalJMSProperties();
        nCore.start(5); // Need a client to avoid NPEs on setting query/publish handler
        
        Map conf = partialGeneralConfig("username");
        sendConfig(conf);
        assertFalse("Should not fail if missing the username", configIsFailed());
        
        conf = partialGeneralConfig("password");
        sendConfig(conf);
        assertFalse("Should not fail if missing the password", configIsFailed());
    }
    
    @Test
    public void testPartialSender() {
        checkMinimalJMSProperties();
        nCore.start(5); // Need a client to avoid NPEs on setting query/publish handler
        
        Map conf = partialSenderConfig("queues");
        sendConfig(conf);
        assertFalse("Should not fail if sender is missing queues", configIsFailed());
        
        conf = partialSenderConfig("topics");
        sendConfig(conf);
        assertFalse("Should not fail if sender is missing topics", configIsFailed());
    }
    
    @Test
    public void testPartialReceiver() {
        checkMinimalJMSProperties();
        nCore.start(5); // Need a client to avoid NPEs on setting query/publish handler
        
        Map conf = partialReceiverConfig("queues");
        sendConfig(conf);
        assertFalse("Should not fail if receiver is missing queues", configIsFailed());
        
        conf = partialReceiverConfig("queueListeners");
        sendConfig(conf);
        assertFalse("Should not fail if receiver is missing queueListeners", configIsFailed());
        
        conf = partialReceiverConfig("topics");
        sendConfig(conf);
        assertFalse("Should not fail if receiver is missing topics", configIsFailed());
    }
      
    @Test
    public void testMinimalConfig() {
        checkMinimalJMSProperties();
        nCore.start(5); // Need a client to avoid NPEs on setting query/publish handler
        
        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }
    
// ================================================= Helper functions =================================================
    
    public static void checkMinimalJMSProperties() {
        assumeTrue(testJMSURL != null && testJMSConnectionFactory != null && testJMSInitialContext != null && jmsDriverLoc != null);

    }
    
    public void sendConfig(Map<String, ?> jmsConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("jmsConfig", jmsConfig);
        obj.put("config", config);
        m.object = obj;
        
        handler.handleMessage(m);
    }
    
    public Map<String, Object> minimalConfig() {
        createMinimalGeneral();
        createMinimalSender();
        createMinimalReceiver();
        
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("general", general);
        ret.put("sender", sender);
        ret.put("receiver", receiver);
        
        return ret;
    }
    
    public Map<String, Object> partialGeneralConfig(String key) {
        createMinimalGeneral();
        createMinimalSender();
        createMinimalReceiver();
        
        general.remove(key);
        
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("general", general);
        ret.put("sender", sender);
        ret.put("receiver", receiver);
        
        return ret;
    }
    
    public Map<String, Object> partialSenderConfig(String key) {
        createMinimalGeneral();
        createMinimalSender();
        createMinimalReceiver();
        
        sender.remove(key);
        
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("general", general);
        ret.put("sender", sender);
        ret.put("receiver", receiver);
        
        return ret;
    }
    
    public Map<String, Object> partialReceiverConfig(String key) {
        createMinimalGeneral();
        createMinimalSender();
        createMinimalReceiver();
        
        receiver.remove(key);
        
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("general", general);
        ret.put("sender", sender);
        ret.put("receiver", receiver);
        
        return ret;
    }
    
    public void createMinimalGeneral() {
        general = new LinkedHashMap<>();
        general.put("providerURL", testJMSURL);
        general.put("connectionFactory", testJMSConnectionFactory);
        general.put("initialContext", testJMSInitialContext);
        general.put("username", testJMSUsername);
        general.put("password", testJMSPassword);
    }
    
    public void createMinimalSender() {
        sender = new LinkedHashMap<>();
        
        List queues = new ArrayList();
        List topics = new ArrayList();
        
        sender.put("queues", queues);
        sender.put("topics", topics);
    }
    
    public void createMinimalReceiver() {
        receiver = new LinkedHashMap<>();
        
        List queues = new ArrayList();
        List queueListeners = new ArrayList();
        List topics = new ArrayList();
        
        receiver.put("queues", queues);
        receiver.put("queueListeners", queueListeners);
        receiver.put("topics", topics);
    }
    
    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
