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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;


public class TestJMSCore extends TestJMSBase {
    
    NoSendJMSCore core;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    JMS jms;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        
        core = new NoSendJMSCore(sourceName, authToken, targetVantiqServer);
        jms = new JMS(core.client, "fakeConnectionFactory");
        core.jms = jms;
        core.start(10);
    }
    
    @After
    public void tearDown() {
        core.stop();
    }
    
    @Test
    public void testIncorectPublishRequests() {        
        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        
        // Sending an empty publish request
        request = new LinkedHashMap<>();
        msg.object = request;
        core.sendJMSMessage(msg);
        assertFalse("Core should not be closed", core.isClosed());
        
        // Sending jibberish as publish request
        request = new LinkedHashMap<>();
        request.put("publish", "jibberish");
        msg.object = request;
        core.sendJMSMessage(msg);
        assertFalse("Core should not be closed", core.isClosed());
    }
    
    @Test
    public void testIncorrectQueryRequests() {        
        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        
        // Sending empty query request
        request = new LinkedHashMap<>();
        msg.object = request;
        core.readQueueMessage(msg);
        assertFalse("Core should not be closed", core.isClosed());
        
        // Sending jibberish as query request
        request = new LinkedHashMap<>();
        request.put("query", "jibberish");
        msg.object = request;
        core.readQueueMessage(msg);
        assertFalse("Core should not be closed", core.isClosed());
    }
    
    @Test
    public void testExitIfConnectionFails() {        
        core.start(3);
        assertTrue("Should have succeeded", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Success means it shouldn't be closed", core.isClosed());
        
        
        core.close();
        core = new NoSendJMSCore(sourceName, authToken, targetVantiqServer);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to authentication failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendJMSCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to WebSocket failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendJMSCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
    }
}
