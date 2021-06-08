
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

//Authors: Alex Blumer, Namir Fawaz, Fred Carter
//Email: support@vantiq.com

import okhttp3.ResponseBody;

import okio.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestExtensionWebSocketListener extends ExtjsdkTestBase {

    ExtensionWebSocketListener listener;
    FalseClient client;
    TestHandlerESM pHandler;
    TestHandlerResp aHandler;
    TestHandlerESM cHandler;
    TestHandlerESM qHandler;
    TestHandlerESM rHandler;
    TestHandlerResp hHandler;
    String srcName;

    @Before
    public void setUp() {
        srcName = "src";
        client = new FalseClient(srcName);
        // Create the connected Futures
        client.initiateWebsocketConnection("unused");
        client.authenticate("");
        client.connectToSource();
        
        listener = new ExtensionWebSocketListener(client);
        client.listener = listener;
        pHandler = new TestHandlerESM();
        aHandler = new TestHandlerResp();
        cHandler = new TestHandlerESM();
        qHandler = new TestHandlerESM();
        rHandler = new TestHandlerESM();
        hHandler = new TestHandlerResp();

        client.setPublishHandler(pHandler);
        client.setAuthHandler(aHandler);
        client.setConfigHandler(cHandler);
        client.setQueryHandler(qHandler);
        client.setHttpHandler(hHandler);
        client.setReconnectHandler(rHandler);
    }

    @After
    public void tearDown() {
        client = null;
        srcName = null;
        listener = null;
        pHandler = null;
        aHandler = null;
        cHandler = null;
        qHandler = null;
        hHandler = null;
        rHandler = null;
    }

    @Test
    public void testAuthenticateSuccess() {
        authenticate(true);

        assert client.isAuthed();
        assert aHandler.compareStatus(200);
    }
    
    @Test
    public void testAuthenticateFailFirst() {
        authenticate(false); // Create failing message

        assert aHandler.compareStatus(400);
        assert !client.getAuthenticationFuture().getNow(true); // It should be set to false now
        
        authenticate(true);
        
        assert client.isAuthed();
        assert aHandler.compareStatus(200);
    }
    
    @Test
    public void testSourceConnection() {
        Map<String,Object> config = new LinkedHashMap<>();
        String key = "key";
        String val = "value";
        config.put(key, val);
        
        connectToSource(srcName, config);

        assert client.isConnected();
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);
        assert cHandler.compareSourceName(srcName);
        assert cHandler.compareValue("config", config);
    }
    
    @Test
    public void testSourceConnectionFailFirst() {
        authenticate(true);
        
        listener.onMessage(client.webSocket, TestListener.errorMessage());
        
        assert !client.getSourceConnectionFuture().getNow(true); // It should be set to false now
        
        Map<String,Object> config = new LinkedHashMap<>();
        String key = "key";
        String val = "value";
        config.put(key, val);
        connectToSource(srcName, config);
        
        assert client.isConnected();
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);
        assert cHandler.compareSourceName(srcName);
        assert cHandler.compareValue("config",config);
    }
    
    @Test
    public void testSourceConnectionFailFirstAndAuthFailFirst() {
        authenticate(false);
        
        // Wait for the Async on the sourceConnectionFuture to complete
        waitUntilTrue(5 * 1000, () -> client.getSourceConnectionFuture().isDone());
        
        assert !client.getSourceConnectionFuture().getNow(true); // It should be set to false now

        
        authenticate(true);
        
        listener.onMessage(client.webSocket, TestListener.errorMessage());
        
        assert !client.getSourceConnectionFuture().getNow(true); // It should be set to false now
        
        Map<String,Object> config = new LinkedHashMap<>();
        String key = "key";
        String val = "value";
        config.put(key, val);
        connectToSource(srcName, config);
        
        assert client.isConnected();
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);
        assert cHandler.compareSourceName(srcName);
        assert cHandler.compareValue("config", config);
    }
    
    @Test
    public void testPublish() {
        connectToSource(srcName, null);
        
        Map<String,Object> publishMessage = new LinkedHashMap<>();
        String key = "publish";
        String val = "info";
        publishMessage.put(key, val);
        
        ByteString body = TestListener.createPublishMessage(publishMessage, srcName);
        listener.onMessage(client.webSocket, body);
        
        assert pHandler.compareOp(ExtensionServiceMessage.OP_PUBLISH);
        assert pHandler.compareSourceName(srcName);
        assert pHandler.compareValue( key, val);
    }
    
    @Test
    public void testQuery() {
        connectToSource(srcName, null);
        
        Map<String,Object> queryMessage = new LinkedHashMap<>();
        String key = "query";
        String val = "request";
        queryMessage.put(key, val);
        
        ByteString body = TestListener.createQueryMessage(queryMessage, srcName);
        listener.onMessage(client.webSocket, body);
        
        assert qHandler.compareOp(ExtensionServiceMessage.OP_QUERY);
        assert qHandler.compareSourceName( srcName);
        assert qHandler.compareValue(key, val);
    }
    
    @Test 
    public void testHttp() {
        connectToSource(srcName, null);
        
        Response resp = new Response().status(200);
        ByteString body = TestListener.createHttpMessage(resp);
        
        listener.onMessage(client.webSocket, body);
        
        assert hHandler.compareStatus(200);
    }
    
    @Test 
    public void testHttpHandlerOnFailedConnection() {
        authenticate(true);
        
        Response resp = new Response().status(403);
        ByteString body = TestListener.createHttpMessage(resp);
        
        listener.onMessage(client.webSocket, body);
        
        assert !client.getSourceConnectionFuture().getNow(true); // It should be set to false now
        assert hHandler.compareStatus(403);
    }
    
    @Test 
    public void testReconnectHandler() {
        connectToSource(srcName, null);
        
        ByteString body = TestListener.createReconnectMessage(srcName);
        
        listener.onMessage(client.webSocket, body);
        
        assert !client.isConnected();
        assert rHandler.compareOp(ExtensionServiceMessage.OP_RECONNECT_REQUIRED);
    }
    
    @Test
    public void testClose() {
        connectToSource(srcName, null);
        
        listener.close();
        
        ByteString body = TestListener.createReconnectMessage(srcName);
        listener.onMessage(client.webSocket, body);
        assert rHandler.compareMessage(null);
        
        body = TestListener.createHttpMessage(new Response().status(200));
        listener.onMessage(client.webSocket, body);
        assert hHandler.compareMessage(null);
        
        aHandler.lastMessage = null; // Resetting from auth in connectToSource
        body = TestListener.createAuthenticationResponse(true);
        listener.onMessage(client.webSocket, body);
        assert aHandler.compareMessage(null);
        
        body = TestListener.createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        assert qHandler.compareMessage(null);
        
        body = TestListener.createPublishMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        assert pHandler.compareMessage(null);
        
        cHandler.lastMessage = null; // Resetting from message in connectToSource
        body = TestListener.createConfigResponse(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        assert cHandler.compareMessage(null);
    }
    
    @Test
    public void testDefaultQuery() {
        listener = new ExtensionWebSocketListener(client);
        client.listener = listener;
        
        connectToSource(srcName, null);

        ByteString body = TestListener.createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        Response resp = null;
        try {
            resp = client.getLastMessageAsResponse();
        } catch(Exception e) { 
            e.printStackTrace();
            fail("Could not interpret the default query handler response\n");
        }
        
        assert resp.getBody() instanceof Map;
        
        Map<String,Object> m = (Map) resp.getBody();
        
        assert "io.vantiq.extjsdk.unsetQueryHandler".equals(m.get("messageCode"));
        assert m.get("messageTemplate") instanceof String;
        assert m.get("parameters") instanceof List;
    }
    
    @Test
    public void testExceptionThrowingHandlers() {
        aHandler = new ExceptionalHandlerResp();
        hHandler = new ExceptionalHandlerResp();
        cHandler = new ExceptionalHandlerEsm();
        pHandler = new ExceptionalHandlerEsm();
        qHandler = new ExceptionalHandlerEsm();
        rHandler = new ExceptionalHandlerEsm();

        listener.setAuthHandler(aHandler);
        listener.setHttpHandler(hHandler);
        listener.setConfigHandler(cHandler);
        listener.setPublishHandler(pHandler);
        listener.setQueryHandler(qHandler);
        listener.setReconnectHandler(rHandler);
        
        open();

        // Every message should not error out due to EWSL catching and logging the error
        // Every message should be saved before the error occurs.
        ByteString body = TestListener.createAuthenticationResponse(true);
        listener.onMessage(client.webSocket, body);
        assert client.isAuthed();
        assert aHandler.compareStatus(200);

        body = TestListener.createHttpMessage(new Response().status(200));
        listener.onMessage(client.webSocket, body);
        assert hHandler.compareStatus(200);

        body = TestListener.createConfigResponse(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);

        body = TestListener.createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        assert qHandler.compareOp(ExtensionServiceMessage.OP_QUERY);

        body = TestListener.createPublishMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);
        assert pHandler.compareOp(ExtensionServiceMessage.OP_PUBLISH);
        
        body = TestListener.createReconnectMessage(srcName);
        listener.onMessage(client.webSocket, body);
        assert rHandler.compareOp(ExtensionServiceMessage.OP_RECONNECT_REQUIRED);
    }
    
    @Test
    public void testNullHandlers() {
        listener.setAuthHandler(null);
        listener.setHttpHandler(null);
        listener.setConfigHandler(null);
        listener.setPublishHandler(null);
        listener.setQueryHandler(null);
        listener.setReconnectHandler(null);

        open();

        // All that is expected is that no NPEs are thrown
        ByteString body = TestListener.createAuthenticationResponse(true);
        listener.onMessage(client.webSocket, body);

        body = TestListener.createHttpMessage(new Response().status(200));
        listener.onMessage(client.webSocket, body);

        body = TestListener.createConfigResponse(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);

        body = TestListener.createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);

        body = TestListener.createPublishMessage(new LinkedHashMap(), srcName);
        listener.onMessage(client.webSocket, body);

        body = TestListener.createReconnectMessage(srcName);
        listener.onMessage(client.webSocket, body);
        
    }
    
    @Test
    public void testWsFailure() {
        connectToSource(srcName, null);

        assert client.isOpen();
        assert client.isAuthed();
        assert client.isConnected();
        
        listener.onFailure(null, new IOException(), null);

        assert listener.isClosed();
        assert !client.isOpen();
        assert !client.isAuthed();
        assert !client.isConnected();
    }

// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
    private void open() {
        client.completeWebSocketConnection(true);
    }
    private void authenticate(boolean success) {
        if (!client.isOpen()) {
            open();
        }
        client.authenticate("unused");
        listener.onMessage(client.webSocket, TestListener.createAuthenticationResponse(success));
    }
    private void connectToSource(String sourceName, Map config) {
        if (!client.isAuthed()) {
            authenticate(true);
        }
        client.connectToSource();
        listener.onMessage(client.webSocket, TestListener.createConfigResponse(config, sourceName));
    }

    
    

    private class TestHandlerESM extends Handler<ExtensionServiceMessage> {
        public String lastOp = "";
        public ExtensionServiceMessage lastMessage = null;
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            lastOp = message.getOp();
            lastMessage = message;
        }

        public boolean compareOp(String expectedOp) {
            return lastOp.equals(expectedOp);
        }
        public boolean compareMessage(ExtensionServiceMessage expectedMessage) {
            if (lastMessage == expectedMessage) return true; // null case
            return lastMessage.equals(expectedMessage);
        }
        public boolean compareSourceName(String sourceName) {
            return lastMessage.getSourceName().equals(sourceName);
        }
        public boolean compareValue(String key, Object expectedVal) {
            Object actualVal = ((Map)lastMessage.getObject()).get(key);
            return expectedVal.equals(actualVal);
        }
    }
    
    private class TestHandlerResp extends Handler<Response> {
        public Response lastMessage = null;
        @Override
        public void handleMessage(Response message) {
            lastMessage = message;
        }

        public boolean compareMessage(Response expectedMessage) {
            if (lastMessage == expectedMessage) {return true;} // null case
            return expectedMessage.equals(lastMessage);
        }
        public boolean compareStatus(Integer expectedStatus) {
            return expectedStatus.equals(lastMessage.getStatus());
        }
        public boolean compareBody(Object expectedBody) {
            return lastMessage.getBody().equals(expectedBody);
        }
        public boolean compareHeader(String headerName, String expectedVal) {
            Object actualVal = lastMessage.getHeader(headerName);
            return expectedVal.equals(actualVal);
        }
    }

    private class ExceptionalHandlerResp extends TestHandlerResp {
        @Override
        public void handleMessage(Response message) {
            super.handleMessage(message);
            throw new RuntimeException("This should be caught and logged.");
        }
    }
    
    private class ExceptionalHandlerEsm extends TestHandlerESM {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            super.handleMessage(message);
            throw new RuntimeException("This should be caught and logged.");
        }
    }

    
}
