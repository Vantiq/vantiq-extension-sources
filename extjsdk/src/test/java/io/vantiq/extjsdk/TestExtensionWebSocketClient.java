
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

//Author: Alex Blumer
//Email: alex.j.blumer@gmail.com

import okhttp3.ws.WebSocket;
import okio.Buffer;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;


public class TestExtensionWebSocketClient extends ExtjsdkTestBase{
    
    // Note: testing of connections occurs in TestExtensionWebSocketListener, as more of the relevant interactions occur
    // through ExtensionWebSocketListener

    OpenExtensionWebSocketClient client; // OpenExtensionWebSocketClient just makes a few functions public
    String srcName;
    String queryAddress;
    FalseWebSocket socket;
    
    @Before
    public void setup() {
        srcName = "src";
        socket = new FalseWebSocket();
        client = new OpenExtensionWebSocketClient(srcName); // OpenExtensionWebSocketClient just makes a few functions public
        client.webSocket = socket;
        queryAddress = "gobbledygook";
    }
    
    @After
    public void tearDown() {
        srcName = null;
        client = null;
        socket = null;
        queryAddress = null;
    }
    
    @Test
    public void testValidifyUrl() {
        String url = "ws://cba.com/api/v1/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("ws://cba.com/api/v1/wsock/websocket");
        
        url = "http://prod.vantiq.com/api/v1/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("ws://prod.vantiq.com/api/v1/wsock/websocket");

        url = "http://prod.vantiq.com/";
        url = client.validifyUrl(url);
        assert url.equals("ws://prod.vantiq.com/api/v1/wsock/websocket");

        url = "http://prod.vantiq.com/api/v/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("ws://prod.vantiq.com/api/v/wsock/websocket/api/v1/wsock/websocket");

        url = "http://prod.vantiq.com/api/v47/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("ws://prod.vantiq.com/api/v47/wsock/websocket");
        
        url = "https://dev.vantiq.com";
        url = client.validifyUrl(url);
        assert url.equals("wss://dev.vantiq.com/api/v1/wsock/websocket");
        
        url = "https://dev.vantiq.com/";
        url = client.validifyUrl(url);
        assert url.equals("wss://dev.vantiq.com/api/v1/wsock/websocket");
        
        url = "dev.vantiq.com";
        url = client.validifyUrl(url);
        assert url.equals("wss://dev.vantiq.com/api/v1/wsock/websocket");

        url = "https://prod.vantiq.com/api/v1/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("wss://prod.vantiq.com/api/v1/wsock/websocket");

        url = "https://prod.vantiq.com/";
        url = client.validifyUrl(url);
        assert url.equals("wss://prod.vantiq.com/api/v1/wsock/websocket");

        url = "https://prod.vantiq.com/api/v/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("wss://prod.vantiq.com/api/v/wsock/websocket/api/v1/wsock/websocket");

        url = "https://prod.vantiq.com/api/v47/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("wss://prod.vantiq.com/api/v47/wsock/websocket");
    }
    
    @Test
    public void testAuthenticateWithUsername() throws InterruptedException {
        String user = "myName";
        String pass = "p@s$w0rd";
        
        client.webSocketFuture = CompletableFuture.completedFuture(true);
        
        client.authenticate(user, pass);
        // Wait up to 5 seconds for asynchronous action to complete
        waitUntilTrue(5 * 1000, () -> socket.receivedMessage());
        
        assert socket.compareData("object.username", user);
        assert socket.compareData("object.password", pass);
        assert socket.compareData("op", "authenticate");
        assert socket.compareData("resourceName", "system.credentials");
    }
    
    @Test
    public void testAuthenticateWithToken() throws InterruptedException {
        client.webSocketFuture = CompletableFuture.completedFuture(true);
        
        String token = "ajeoslvkencmvkejshegwt=";
        client.authenticate(token);
        // Wait up to 5 seconds for asynchronous action to complete
        waitUntilTrue(5 * 1000, () -> socket.receivedMessage());
        
        assert socket.compareData("object", token);
        assert socket.compareData("op", "validate");
        assert socket.compareData("resourceName", "system.credentials");
    }
    
    @Test
    public void testConnectToSource() throws InterruptedException {
        client.webSocketFuture = CompletableFuture.completedFuture(true);
        client.authFuture = CompletableFuture.completedFuture(true);
        
        client.connectToSource();
        // Wait up to 5 seconds for asynchronous action to complete
        waitUntilTrue(5 * 1000, () -> socket.receivedMessage());
        
        assert socket.compareData("op", ExtensionServiceMessage.OP_CONNECT_EXTENSION);
        assert socket.compareData("resourceName", ExtensionServiceMessage.RESOURCE_NAME_SOURCES);
        assert socket.compareData("resourceId", srcName);
    }
    
    @Test
    public void testQueryResponseSingleMap() {
        Map<String, Object> queryData = new LinkedHashMap<>();
        queryData.put("msg", "val");
        queryData.put("val", "msg");
        
        client.webSocketFuture = CompletableFuture.completedFuture(true);
        client.sendQueryResponse(200, queryAddress, queryData);
        
        assert socket.compareData("body", queryData);
        assert socket.compareData("headers." + ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, queryAddress);
        assert socket.compareData("status", 200);
    }
    
    @Test
    public void testQueryResponseMapArray() {
        Map<String, Object>[] queryData = new Map[2];
        queryData[0] = new LinkedHashMap<>();
        queryData[0].put("msg", "val");
        queryData[0].put("val", "msg");
        queryData[1] = new LinkedHashMap<>();
        queryData[1].put("message", "value");
        queryData[1].put("value", "message");
        
        markWsConnected(true);
        client.sendQueryResponse(200, queryAddress, queryData);
        
        // The ArrayList creation is necessary since JSON interprets arrays as ArrayList
        assert socket.compareData("body", new ArrayList<Map>(Arrays.asList(queryData)));
        assert socket.compareData("headers." + ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, queryAddress);
        assert socket.compareData("status", 200);
    }
    
    @Test
    public void testQueryError() {
        String[] params = {"p1", "param2"};
        String errorMessage = "Message with params {}='p1' {}='param2'.";
        String errorCode = "io.vantiq.extjsdk.ExampleErrorName";
        
        markWsConnected(true);
        client.sendQueryError(queryAddress, errorCode, errorMessage, params);
        
        assert socket.compareData("headers." + ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, queryAddress);
        assert socket.compareData("status", 400);
        // The ArrayList creation is necessary since JSON interprets arrays as ArrayList
        assert socket.compareData("body.parameters", new ArrayList<String>(Arrays.asList(params)));
        assert socket.compareData("body.messageTemplate", errorMessage);
        assert socket.compareData("body.messageCode", errorCode);
    }
    
    @Test
    public void testStop() {
        markSourceConnected(true);
        
        assert !client.getListener().isClosed();
        
        client.stop();
        
        assert client.getListener().isClosed();
    }
    
    @Test
    public void testClose() {
        markSourceConnected(true);
        
        assert !client.getListener().isClosed();
        
        CompletableFuture<Boolean> wsFut = client.getWebsocketConnectionFuture();
        CompletableFuture<Boolean> aFut = client.getAuthenticationFuture();
        CompletableFuture<Boolean> sFut = client.getSourceConnectionFuture();
        
        assert wsFut.getNow(false);
        assert aFut.getNow(false);
        assert sFut.getNow(false);
        
        ExtensionWebSocketListener oldListen = client.getListener();
        client.close();
        
        assert !client.getListener().isClosed();
        assert oldListen.isClosed();
        
        ExtensionWebSocketListener newListen = client.getListener();
        
        // '==' and not '.equals()' because they should be the exact same object
        assert newListen.authHandler == oldListen.authHandler;
        assert newListen.httpHandler == oldListen.httpHandler;
        assert newListen.configHandler == oldListen.configHandler;
        assert newListen.reconnectHandler == oldListen.reconnectHandler;
        assert newListen.publishHandler == oldListen.publishHandler;
        assert newListen.queryHandler == oldListen.queryHandler;
        
        // Should be set to false now
        assert !wsFut.getNow(true);
        assert !aFut.getNow(true);
        assert !sFut.getNow(true);
        
        // The old Futures should have been removed from client
        assert client.getWebsocketConnectionFuture() == null;
        assert client.getAuthenticationFuture() == null;
        assert client.getSourceConnectionFuture() == null;
    }
    
    @Test
    public void testIsOpenOnNull() {
        assert !client.isOpen();
        assert !client.isAuthed();
        assert !client.isConnected();
    }
    
    @Test
    public void testAutoReconnect() {
        markSourceConnected(true);
        
        assert client.getSourceConnectionFuture().getNow(false);
        
        // Should make sourceConnection be recreated
        client.setAutoReconnect(true);
        client.getListener().onMessage(TestListener.createReconnectMessage(""));

        assert !client.isConnected();
        assert !client.getSourceConnectionFuture().isDone(); 
    }
    
    @Test
    public void testNotification() {
        markSourceConnected(true);

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("msg", "str");

        client.sendNotification(m);

        assert socket.compareData("op", ExtensionServiceMessage.OP_NOTIFICATION);
        assert socket.compareData("object.msg", "str");
        assert socket.compareData("resourceId", srcName);
    }

    @Test
    public void testBadNotificationArguments() {
        markSourceConnected(true);

        Integer[] intArray = new Integer[3];
        intArray[0] = 1;
        intArray[1] = 2;
        intArray[2] = 3;

        try {
            client.sendNotification(intArray);
            fail("Send of Array should fail.");
        } catch (IllegalArgumentException iae) {
            // Expected
        } // Other invalid exceptions will escape & cause failure.

        List<String> stringList = new ArrayList<>();
        stringList.add("This ");
        stringList.add("should ");
        stringList.add("not ");
        stringList.add("work.");

        try {
            client.sendNotification(stringList);
            fail("Send of List should fail.");
        } catch (IllegalArgumentException iae) {
            // Expected
        } // Other invalid exceptions will escape & cause failure.
    }
    
// ============================== Helper functions ==============================
    private void markWsConnected(boolean success) {
        client.webSocketFuture = CompletableFuture.completedFuture(success);
    }
    private void markAuthSuccess(boolean success) {
        markWsConnected(true);
        client.authFuture = CompletableFuture.completedFuture(success);
    }
    private void markSourceConnected(boolean success) {
        markAuthSuccess(true);
        client.sourceFuture = CompletableFuture.completedFuture(success);
    }
    
    // Merely makes several private functions public
    private class OpenExtensionWebSocketClient extends ExtensionWebSocketClient{
        public OpenExtensionWebSocketClient(String sourceName) {
            super(sourceName);
        }
        
        @Override
        public String validifyUrl(String url) {
            return super.validifyUrl(url);
        }
    }
    
    private class FalseWebSocket implements WebSocket {
        
        Map<String,Object> lastData = null;
        boolean messageReceived = false;
        
        
        @Override
        public void sendMessage(RequestBody body) {
            Buffer buf = new Buffer();
            try {
                body.writeTo(buf);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                lastData = mapper.readValue(buf.inputStream(), Map.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            messageReceived = true;
        }
        
        public boolean compareData(String key, Object expectedVal) {
            Object actualVal = getTransformVal(lastData, key);
            return expectedVal.equals(actualVal);
        }
        
        public boolean receivedMessage() {
            return messageReceived;
        }

        @Override
        public void sendPing(Buffer payload) throws IOException {}
        @Override
        public void close(int code, String reason) throws IOException {client.getListener().onClose(code,reason);}
    }
    
    public static Object getTransformVal(Map map, String loc) {
        if (map == null) {
            return null;
        }
        Object result;
        Map currentLvl = map;
        String[] levelNames = loc.split("\\.");

        int level;
        for (level = 0; level < levelNames.length - 1; level++) {
            if (!(currentLvl.get(levelNames[level]) instanceof Map)) {
                return null;
            }
            currentLvl = (Map) currentLvl.get(levelNames[level]);
        }
        result = currentLvl.get(levelNames[level]);
        return result;
    }
}
