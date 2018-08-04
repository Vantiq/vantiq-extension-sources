package io.vantiq.extjsdk;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TestExtensionWebSocketListener extends ExtjsdkTestBase{

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
        
        listener.onMessage(errorMessage());
        
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
        
        listener.onMessage(errorMessage());
        
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
        
        ResponseBody body = createPublishMessage(publishMessage, srcName);
        listener.onMessage(body);
        
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
        
        ResponseBody body = createQueryMessage(queryMessage, srcName);
        listener.onMessage(body);
        
        assert qHandler.compareOp(ExtensionServiceMessage.OP_QUERY);
        assert qHandler.compareSourceName( srcName);
        assert qHandler.compareValue(key, val);
    }
    
    @Test 
    public void testHttp() {
        connectToSource(srcName, null);
        
        Response resp = new Response().status(200);
        ResponseBody body = createHttpMessage(resp);
        
        listener.onMessage(body);
        
        assert hHandler.compareStatus(200);
    }
    
    @Test 
    public void testHttpHandlerOnFailedConnection() {
        authenticate(true);
        
        Response resp = new Response().status(403);
        ResponseBody body = createHttpMessage(resp);
        
        listener.onMessage(body);
        
        assert !client.getSourceConnectionFuture().getNow(true); // It should be set to false now
        assert hHandler.compareStatus(403);
    }
    
    @Test 
    public void testReconnectHandler() {
        connectToSource(srcName, null);
        
        ResponseBody body = createReconnectMessage(srcName);
        
        listener.onMessage(body);
        
        assert !client.isConnected();
        assert rHandler.compareOp(ExtensionServiceMessage.OP_RECONNECT_REQUIRED);
    }
    
    @Test
    public void testClose() {
        connectToSource(srcName, null);
        
        listener.close();
        
        ResponseBody body = createReconnectMessage(srcName);
        listener.onMessage(body);
        assert rHandler.compareMessage(null);
        
        body = createHttpMessage(new Response().status(200));
        listener.onMessage(body);
        assert hHandler.compareMessage(null);
        
        aHandler.lastMessage = null; // Resetting from auth in connectToSource
        body = createAuthenticationResponse(true);
        listener.onMessage(body);
        assert aHandler.compareMessage(null);
        
        body = createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        assert qHandler.compareMessage(null);
        
        body = createPublishMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        assert pHandler.compareMessage(null);
        
        cHandler.lastMessage = null; // Resetting from message in connectToSource
        body = createConfigResponse(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        assert cHandler.compareMessage(null);
    }
    
    @Test
    public void testDefaultQuery() {
        listener = new ExtensionWebSocketListener(client);
        client.listener = listener;
        
        connectToSource(srcName, null);
        
        ResponseBody body = createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        Response resp = null;
        try {
            resp = mapper.readValue(client.getLastMessage(), Response.class);
        } catch(Exception e) { e.printStackTrace();}
        
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
        
        authenticate(false);

        // Every message should not error out due to EWSL catching and logging the error
        // Every message should be saved before the error occurs.
        ResponseBody body = createAuthenticationResponse(true);
        listener.onMessage(body);
        assert client.isAuthed();
        assert aHandler.compareStatus(200);

        body = createHttpMessage(new Response().status(200));
        listener.onMessage(body);
        assert hHandler.compareStatus(200);

        body = createConfigResponse(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);

        body = createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        assert qHandler.compareOp(ExtensionServiceMessage.OP_QUERY);

        body = createPublishMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);
        assert pHandler.compareOp(ExtensionServiceMessage.OP_PUBLISH);
        
        body = createReconnectMessage(srcName);
        listener.onMessage(body);
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
        ResponseBody body = createAuthenticationResponse(true);
        listener.onMessage(body);

        body = createHttpMessage(new Response().status(200));
        listener.onMessage(body);

        body = createConfigResponse(new LinkedHashMap(), srcName);
        listener.onMessage(body);

        body = createQueryMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);

        body = createPublishMessage(new LinkedHashMap(), srcName);
        listener.onMessage(body);

        body = createReconnectMessage(srcName);
        listener.onMessage(body);
        
    }
    
    @Test
    public void testWsFailure() {
        connectToSource(srcName, null);

        assert client.isOpen();
        assert client.isAuthed();
        assert client.isConnected();
        
        listener.onFailure(new IOException(), null);

        assert listener.isClosed();
        assert !client.isOpen();
        assert !client.isAuthed();
        assert !client.isConnected();
    }

// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
    private void open() {
        client.webSocketFuture.complete(true);
    }
    private void authenticate(boolean success) {
        if (!client.isOpen()) {
            open();
        }
        client.authenticate("unused");
        listener.onMessage(createAuthenticationResponse(success));
    }
    private void connectToSource(String sourceName, Map config) {
        if (!client.isAuthed()) {
            authenticate(true);
        }
        client.connectToSource();
        listener.onMessage(createConfigResponse(config, sourceName));
    }

    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private ResponseBody errorMessage() {
        return ResponseBody.create(JSON, "{\"status\":400}");
    }
    
    private ResponseBody createAuthenticationResponse(boolean success) {
        if (success) {
            return ResponseBody.create(JSON, sampleAuthResponseBody);
        }
        else {
            return errorMessage();
        }
    }
    private ResponseBody createConfigResponse(Map<String,Object> config, String sourceName) {
        try {
            Map<String,Object> body = mapper.readValue(sampleConfigBody, Map.class);
            Map<String,Object> c = new LinkedHashMap<>();
            c.put("config", config);
            body.put("resourceId", sourceName);
            body.put("object", c);
            return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            print("Error processing Map for createConfigResponse");
            return null;
        }
    }
    private ResponseBody createPublishMessage(Map<String,Object> message, String sourceName) {
        try {
            Map<String,Object> body = mapper.readValue(samplePublishBody, Map.class);
            body.put("resourceId", sourceName);
            body.put("object", message);
            return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            print("Error processing Map for createPublishMessage");
            return null;
        }
    }
    private ResponseBody createQueryMessage(Map<String,Object> message, String sourceName) {
        try {
            Map<String,Object> body = mapper.readValue(sampleQueryBody, Map.class);
            body.put("resourceId", sourceName);
            body.put("object", message);
            return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            print("Error processing Map for createQueryMessage\n" +  e);
            return null;
        }
    }
    

    private ResponseBody createHttpMessage(Response resp) {
        try {
            return ResponseBody.create(JSON, mapper.writeValueAsBytes(resp));
        }
        catch (Exception e) {
            print("Error processing Map for createPublishMessage");
            return null;
        }
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

    private String sampleAuthResponseBody = "{\"status\":200, \"contentType\":\"application/json\", \"body\":{\"anonymous\":false, " +
            "\"userInfo\":{\"accessToken\":\"1c55ag9pyVZFa8k9BTGgI7XiI7nHTLTtLMRKBZ0=\", " +
            "\"idToken\":\"yJ0eXAiOiJKV1QLCJhbGcOiJIUzI1NiJ9.eyJzdWIiOiJ1ZHBzb3VyY2V0b2tlbl9fc3lzdGVtIiwicHJlZmVycmVkVX" +
            "lcm5hbWUiOiJ1ZHBzb3VyY2V0b2tlbl9fc3lzdGVtIiwiaXNzIjoiaHR0cHM6Ly92YW50aXEuY29tL2F1dGhlbnRpY2F0ZSIsInBy" +
            "ZpbGVzIjpbInN5c3RlbS51c2VyIl0sInByZWZlcnJlZF91c2VybmFtZSI6InVkcHNvdXJZXRva2Vu19zeXN0ZW0iLCJob21lTmFtZNw" +
            "YWNlIjoic3lzdGVtIiwiWRlbnRpdHlQcm92aWRlciI6IlZhbnRpcSIsImF1ZCI6InZhnRpcS1jbllbnQiLCJjcmVhdGVkQnkiOiJze" +
            "XN0ZW0iLCJuYW1lc3BhY2UiOiJzeXN0ZW0iLCJ1c2VyVHlwZSI6Im5vZGUiLCJleHAiOj1OTM2NDYwNTAsImlhdCI6MTUzMTg2ODI0Cw" +
            "ianRpIjoiYzRkMU0NDAtOGExNC0xMWU4LWI4ODAtNDgxNTJkNDRhNTg5IiwidXNlcm5hbWUiOiJ1ZHBzb3VyY20b2tlbl9fc3lzdGVtI" +
            "n0.h9EVfLQcxVVfpuPKkr8bwcXuCrF2k8wgdkavfs-M\", " +
            "\"username\":\"udpsourcetoken__system\", \"preferredUsername\":\"udpsourcetoken__system\", \"namespace\":\"system\", " +
            "\"homeNamespace\":\"system\", \"createdBy\":\"system\", \"userType\":\"node\", \"profiles\":[\"system.user\"]}}}";
    private String sampleConfigBody = "{\"op\":\"configureExtension\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
            "\"parameters\":{}, \"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
            "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", \"messageHeaders\":{}}";
    private String samplePublishBody = "{\"op\":\"publish\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
            "\"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
            "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", \"messageHeaders\":{}}";
    private String sampleQueryBody = "{\"op\":\"query\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
            "\"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
            "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", " +
            "\"messageHeaders\":{\"REPLY_ADDR_HEADER\":\"d15cf6b0-8a1f-11e8-b880-48152d44a589\"}}";
}
