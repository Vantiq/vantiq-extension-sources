
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

import okhttp3.Request;
import okhttp3.WebSocket;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;


public class TestExtensionWebSocketClient extends ExtjsdkTestBase {
    
    // Note: testing of connections occurs in TestExtensionWebSocketListener, as more of the relevant interactions occur
    // through ExtensionWebSocketListener

    OpenExtensionWebSocketClient client; // OpenExtensionWebSocketClient just makes a few functions public
    String srcName;
    String queryAddress;
    FalseWebSocket socket;
    File serverConfigFile;
    
    @Before
    public void setup() throws IOException {
        srcName = "src";
        socket = new FalseWebSocket();
        client = new OpenExtensionWebSocketClient(srcName); // OpenExtensionWebSocketClient just makes a few functions public
        client.webSocket = socket;
        queryAddress = "gobbledygook";

        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        serverConfigFile = new File("server.config");
        serverConfigFile.createNewFile();
        serverConfigFile.deleteOnExit();
        Utils.obtainServerConfig();
    }
    
    @After
    public void tearDown() {
        srcName = null;
        client = null;
        socket = null;
        queryAddress = null;
        serverConfigFile.delete();
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

        markWsConnected(true);
        markAuthSuccess(true);
        markSourceConnected(true);
        client.sendQueryResponse(200, queryAddress, queryData);
        
        assert socket.compareData("body", queryData);
        assert socket.compareData("headers." + ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, queryAddress);
        assert socket.compareData("status", 200);
    }
    
    boolean sawCloseHandler = false;
    public Handler<ExtensionWebSocketClient> errorCloseHandler =  new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            sawCloseHandler = true;
        }
    };
    
    @Test
    public void testLostConnection() {
        Map<String, Object> queryData = new LinkedHashMap<>();
        queryData.put("msg", "val");
        queryData.put("val", "msg");
        
        markWsConnected(true);
        markAuthSuccess(true);
        markSourceConnected(true);
        client.sendQueryResponse(200, queryAddress, queryData);
        
        assert socket.compareData("body", queryData);
        assert socket.compareData("headers." + ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, queryAddress);
        assert socket.compareData("status", 200);
        
        client.setCloseHandler(errorCloseHandler);
        socket.setClosedByRemote(true);
        sawCloseHandler = false;
        try {
            client.sendQueryResponse(200, queryAddress, queryData);
        } catch (RuntimeException re) {
            assert re.getCause() instanceof IllegalStateException;
            assert !client.isOpen();
            assert sawCloseHandler;
        }
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
        markAuthSuccess(true);
        markSourceConnected(true);
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
        markAuthSuccess(true);
        markSourceConnected(true);
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
        client.getListener().onMessage(client.webSocket, TestListener.createReconnectMessage(""));

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
    public void testOpenAndClose() {
        // Setup a client and listener and mark things "connected"
        FalseClient newClient = new FalseClient(srcName);
        TestListener testListener = new TestListener(newClient);
        newClient.listener = testListener;

        // Lets initiate the full connection and send a notification
        newClient.initiateFullConnection("doesn't matter", "also doesn't matter");
        newClient.webSocketFuture = CompletableFuture.completedFuture(true);
        newClient.authFuture = CompletableFuture.completedFuture(true);
        newClient.sourceFuture = CompletableFuture.completedFuture(true);
        newClient.sendNotification("blah");

        // Now we close the client
        newClient.close();

        // Now reopen and try to send a notification again
        newClient.initiateFullConnection("doesn't matter", "also doesn't matter");
        newClient.webSocketFuture = CompletableFuture.completedFuture(true);
        newClient.authFuture = CompletableFuture.completedFuture(true);
        newClient.sourceFuture = CompletableFuture.completedFuture(true);
        newClient.sendNotification("blah2");
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

    @Test
    public void testFailedMessageQueue() {
        // Setup a client and listener and mark things "connected"
        FalseClient newClient = new FalseClient(srcName);
        TestListener testListener = new TestListener(newClient);
        newClient.listener = testListener;

        newClient.initiateWebsocketConnection("unused");
        newClient.authenticate("");
        newClient.connectToSource();

        markSourceConnected(true);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("msg", "str");

        // Now break the connection, and while it's down lets try to send a notification
        newClient.close();
        assert !newClient.isOpen();
        assert !newClient.isAuthed();
        assert !newClient.isConnected();

        // We should see it get put in the queue
        newClient.sendNotification(m);
        assert newClient.failedMessageQueue.size() == 1;

        // Upon a "reconnection" (here we're just forcing the issue by sending a connectExtension message), we should
        // see that the queue was flushed
        newClient.webSocketFuture = CompletableFuture.completedFuture(true);
        newClient.authFuture = CompletableFuture.completedFuture(true);
        newClient.sourceFuture = CompletableFuture.completedFuture(false);
        newClient.listener.onMessage(client.webSocket, testListener.createConfigResponse(new LinkedHashMap<>(), srcName));
        assert newClient.failedMessageQueue.size() == 0;

        // Now lets do the same thing with a query
        newClient.close();
        assert !newClient.isOpen();
        assert !newClient.isAuthed();
        assert !newClient.isConnected();

        Map<String, Object> queryData = new LinkedHashMap<>();
        queryData.put("msg", "val");
        queryData.put("val", "msg");
        newClient.sendQueryResponse(200, queryAddress, queryData);
        assert newClient.failedMessageQueue.size() == 1;

        newClient.webSocketFuture = CompletableFuture.completedFuture(true);
        newClient.authFuture = CompletableFuture.completedFuture(true);
        newClient.sourceFuture = CompletableFuture.completedFuture(false);
        newClient.listener.onMessage(client.webSocket, testListener.createConfigResponse(new LinkedHashMap<>(), srcName));
        assert newClient.failedMessageQueue.size() == 0;
    }

    @Test
    public void testReconnect() {
        client.webSocketFuture = CompletableFuture.completedFuture(true);
        client.authFuture = CompletableFuture.completedFuture(true);

        client.doCoreReconnect();
        // Wait up to 5 seconds for asynchronous action to complete
        waitUntilTrue(5 * 1000, () -> socket.receivedMessage());

        assert socket.compareData("op", ExtensionServiceMessage.OP_CONNECT_EXTENSION);
        assert socket.compareData("resourceName", ExtensionServiceMessage.RESOURCE_NAME_SOURCES);
        assert socket.compareData("resourceId", srcName);
    }

    @Test
    public void testTCPProbeListener() {
        // Setup a client and listener and mark things "connected"
        FalseClient newClient = new FalseClient(srcName);
        TestListener testListener = new TestListener(newClient);
        newClient.listener = testListener;

        newClient.initiateWebsocketConnection("unused");
        newClient.authenticate("");
        newClient.connectToSource();
        newClient.webSocketFuture = CompletableFuture.completedFuture(true);
        newClient.authFuture = CompletableFuture.completedFuture(true);
        newClient.sourceFuture = CompletableFuture.completedFuture(true);


        // Now lets try initialize the TCP Probe Listener, and make sure things still look alright
        try {
            newClient.declareHealthy();
        } catch (Exception e) {
            fail("Initializing TCP Probe should not throw exception.");
        }
        assert newClient.isOpen();
        assert newClient.isAuthed();
        assert newClient.isConnected();

        // Now we'll cancel the listener, and again check that we didn't mess up anything else
        newClient.declareUnhealthy();
        assert newClient.isOpen();
        assert newClient.isAuthed();
        assert newClient.isConnected();

        // Finally, lets initialize a new one
        try {
            newClient.declareHealthy();
        } catch (Exception e) {
            fail("Initializing TCP Probe should not throw exception.");
        }
        assert newClient.isOpen();
        assert newClient.isAuthed();
        assert newClient.isConnected();

        // And cancel it to be complete
        newClient.declareUnhealthy();
        assert newClient.isOpen();
        assert newClient.isAuthed();
        assert newClient.isConnected();

        // One last test to prove that we don't throw exceptions when declaring healthy/unhealthy multiple times
        try {
            newClient.declareHealthy();
            newClient.declareHealthy();
            newClient.declareHealthy();
            newClient.declareUnhealthy();
            newClient.declareUnhealthy();
            newClient.declareUnhealthy();
        } catch (Exception e) {
            fail("No exceptions should be thrown regardless of when or how the healthy/unhealthy methods are called.");
        }
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
    private static class OpenExtensionWebSocketClient extends ExtensionWebSocketClient {
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
    
        private boolean closedByRemote = false;
    
        public void setClosedByRemote(boolean newState) {
            closedByRemote = newState;
        }
        @Override
        public boolean send(ByteString bytes) {
            if (closedByRemote) {
                throw new IllegalStateException("Asked to emulate unexpected closure of websocket");
            }
            try {
                lastData = mapper.readValue(bytes.toByteArray(), Map.class);
            } catch (IOException e) {
                e.printStackTrace();
            }

            messageReceived = true;
            return messageReceived;
        }
        
        public boolean compareData(String key, Object expectedVal) {
            Object actualVal = getTransformVal(lastData, key);
            return expectedVal.equals(actualVal);
        }
        
        public boolean receivedMessage() {
            return messageReceived;
        }

        @Override
        public boolean close(int code, String reason) {
            client.getListener().onClosed(client.webSocket, code, reason);
            return true;
        }

        //================================ Necessary to implement WebSocket ================================

        @Override
        public void cancel() {

        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public boolean send(@NotNull String s) {
            return false;
        }

        @NotNull
        @Override
        public Request request() {
            return new Request.Builder().build();
        }
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
