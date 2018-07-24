package io.vantiq.extjsdk;

import okhttp3.ws.WebSocket;
import okio.Buffer;
import okio.BufferedSink;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestExtensionWebSocketClient {
    
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
        assert url.equals("wss://prod.vantiq.com/api/v1/wsock/websocket");
        
        url = "http://prod.vantiq.com/api/v/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("wss://prod.vantiq.com/api/v/wsock/websocket/api/v1/wsock/websocket");
        
        url = "http://prod.vantiq.com/api/v47/wsock/websocket";
        url = client.validifyUrl(url);
        assert url.equals("wss://prod.vantiq.com/api/v47/wsock/websocket");
        
        url = "https://dev.vantiq.com";
        url = client.validifyUrl(url);
        assert url.equals("wss://dev.vantiq.com/api/v1/wsock/websocket");
        
        url = "https://dev.vantiq.com/";
        url = client.validifyUrl(url);
        assert url.equals("wss://dev.vantiq.com/api/v1/wsock/websocket");
        
        url = "dev.vantiq.com";
        url = client.validifyUrl(url);
        assert url.equals("wss://dev.vantiq.com/api/v1/wsock/websocket");
    }
    
    @Test
    public void testAuthenticateWithUsername() throws InterruptedException {
        String user = "myName";
        String pass = "p@s$w0rd";
        
        client.webSocketFuture.complete(true);
        
        client.authenticate(user, pass);
        // do twice because the initial attempt is asynchronous, so the data will not be ready for the asserts 
        client.authenticate(user, pass);  
        
        assert socket.compareData("object.username", user);
        assert socket.compareData("object.password", pass);
        assert socket.compareData("op", "authenticate");
        assert socket.compareData("resourceName", "system.credentials");
    }
    
    @Test
    public void testAuthenticateWithToken() throws InterruptedException {
        client.webSocketFuture.complete(true);
        
        String token = "ajeoslvkencmvkejshegwt=";
        client.authenticate(token);
        
        // do twice because the initial attempt is asynchronous, so the data will not be ready for the asserts 
        client.authenticate(token);  
        
        assert socket.compareData("object", token);
        assert socket.compareData("op", "validate");
        assert socket.compareData("resourceName", "system.credentials");
    }
    
    @Test
    public void testConnectToSource() throws InterruptedException {
        client.authFuture.complete(true);
        client.authSuccess.complete(null);
        
        client.connectToSource();
        // do twice because the initial attempt is asynchronous, so the data will not be ready for the asserts 
        client.connectToSource();  
        
        assert socket.compareData("op", ExtensionServiceMessage.OP_CONNECT_EXTENSION);
        assert socket.compareData("resourceName", ExtensionServiceMessage.RESOURCE_NAME_SOURCES);
        assert socket.compareData("resourceId", srcName);
    }
    
    @Test
    public void testQueryResponseSingleMap() {
        Map<String, Object> queryData = new LinkedHashMap<>();
        queryData.put("msg", "val");
        queryData.put("val", "msg");
        
        client.sendQueryResponse(200, queryAddress, queryData);
        
        assert socket.compareData("body", queryData);
        assert socket.compareData("headers." + ExtensionServiceMessage.REPLY_ADDRESS, queryAddress);
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
        
        client.sendQueryResponse(200, queryAddress, queryData);
        
     // The ArrayList creation is necessary since JSON interprets arrays as ArrayList
        assert socket.compareData("body", new ArrayList<Map>(Arrays.asList(queryData)));
        assert socket.compareData("headers." + ExtensionServiceMessage.REPLY_ADDRESS, queryAddress);
        assert socket.compareData("status", 200);
    }
    
    @Test
    public void testQueryError() {
        String[] params = {"p1", "param2"};
        String errorMessage = "Message with params {}='p1' {}='param2'.";
        String errorCode = "io.vantiq.extjsdk.ExampleErrorName";
        
        client.sendQueryError(queryAddress, errorCode, errorMessage, params);
        
        assert socket.compareData("headers." + ExtensionServiceMessage.REPLY_ADDRESS, queryAddress);
        assert socket.compareData("status", 400);
        // The ArrayList creation is necessary since JSON interprets arrays as ArrayList
        assert socket.compareData("body.parameters", new ArrayList<String>(Arrays.asList(params)));
        assert socket.compareData("body.messageTemplate", errorMessage);
        assert socket.compareData("body.messageCode", errorCode);
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
        
        @Override
        public void doAuthentication() {
            super.doAuthentication();
        }
        
        @Override
        public void doConnectionToSource() {
            super.doConnectionToSource();
        }
    }
    
    
    ObjectMapper mapper = new ObjectMapper();
    private class FalseWebSocket implements WebSocket {
        
        RequestBody lastBody = null;
        Map<String,Object> lastData = null;
        
        
        @Override
        public void sendMessage(RequestBody body) {
            lastBody = body;
            Buffer buf = new Buffer();
            try {
                body.writeTo(buf);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            try {
                lastData = mapper.readValue(buf.inputStream(), Map.class);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        public boolean compareData(String key, Object expectedVal) {
            Object actualVal = getTransformVal(lastData, key);
            return expectedVal.equals(actualVal);
        }

        @Override
        public void sendPing(Buffer payload) throws IOException {}
        @Override
        public void close(int code, String reason) throws IOException {}
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
