package io.vantiq.extjsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TestExtensionWebSocketListener {

    ExtensionWebSocketListener listener;
    FalseClient client;
    TestHandler pHandler;
    TestHandler aHandler;
    TestHandler cHandler;
    TestHandler qHandler;
    TestHandler rHandler;
    String srcName;
    @Before
    public void setUp() {
        srcName = "src";
        client = new FalseClient(srcName);
        listener = new ExtensionWebSocketListener(client);
        pHandler = new TestHandler();
        aHandler = new TestHandler();
        cHandler = new TestHandler();
        qHandler = new TestHandler();
        rHandler = new TestHandler();

        listener.setPublishHandler(pHandler);
        listener.setAuthHandler(aHandler);
        listener.setConfigHandler(cHandler);
        listener.setQueryHandler(qHandler);
        listener.setHttpHandler(rHandler);
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
        rHandler = null;
    }

    @Test
    public void testAuthenticateSuccess() {
        authenticate(true);

        assert client.isAuthed();
        assert aHandler.compareOp("");
        assert aHandler.compareValue("status", 200);
    }
    
    @Test
    public void testAuthenticateFailFirst() {
        authenticate(false); // Create failing message

        assert aHandler.compareOp("");
        assert aHandler.compareValue("status", 400);
        assert !client.authenticate("").getNow(true); // It should be set to false now
        
        authenticate(true);
        
        assert client.isAuthed();
        assert aHandler.compareOp("");
        assert aHandler.compareValue("status", 200);
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
        assert cHandler.compareValue("resourceId", srcName);
        assert cHandler.compareValue("object.config." + key, val);
    }
    
    @Test
    public void testSourceConnectionFailFirst() {
        authenticate(true);
        
        listener.onMessage(errorMessage());
        
        assert !client.connectToSource().getNow(true); // It should be set to false now
        
        Map<String,Object> config = new LinkedHashMap<>();
        String key = "key";
        String val = "value";
        config.put(key, val);
        connectToSource(srcName, config);
        
        assert client.isConnected();
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);
        assert cHandler.compareValue("resourceId", srcName);
        assert cHandler.compareValue("object.config." + key, val);
    }
    
    @Test
    public void testSourceConnectionFailFirstAndAuthFailFirst() {
        authenticate(false);
        try {
            // Give it a little bit of time to run asynchronously
            assert !client.connectToSource().get(5, TimeUnit.MILLISECONDS); // It should be set to false now
        }
        catch (Exception e) {
            print("ConnectToSource took to long to fail");
            assert false;
        }
        
        authenticate(true);
        
        listener.onMessage(errorMessage());
        
        assert !client.connectToSource().getNow(true); // It should be set to false now
        
        Map<String,Object> config = new LinkedHashMap<>();
        String key = "key";
        String val = "value";
        config.put(key, val);
        connectToSource(srcName, config);
        
        assert client.isConnected();
        assert cHandler.compareOp(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION);
        assert cHandler.compareValue("resourceId", srcName);
        assert cHandler.compareValue("object.config." + key, val);
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
        assert pHandler.compareValue("resourceId", srcName);
        assert pHandler.compareValue("object." + key, val);
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
        assert qHandler.compareValue("resourceId", srcName);
        assert qHandler.compareValue("object." + key, val);
    }
    
    @Test public void testHttp() {
        connectToSource(srcName, null);
        
        Response resp = new Response().status(200);
        ResponseBody body = createHttpMessage(resp);
        
        listener.onMessage(body);
        
        assert rHandler.compareOp("");
        assert rHandler.compareValue("status", 200);
    }
    
    @Test public void testHttpHandlerOnFailedConnection() {
        authenticate(true);
        
        Response resp = new Response().status(403);
        ResponseBody body = createHttpMessage(resp);
        
        listener.onMessage(body);
        
        assert !client.connectToSource().getNow(true); // It should be set to false now
        assert rHandler.compareOp("");
        assert rHandler.compareValue("status", 403);
    }

// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
    private void open() {
        listener.onOpen(null, null);
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
    ObjectMapper mapper = new ObjectMapper();
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

    private void print(String str) {
        System.out.println(str);
    }


    private class FalseClient extends ExtensionWebSocketClient {
        FalseClient(String sourceName) {
            super(sourceName);
        }

        @Override
        public CompletableFuture<Boolean> initiateWebsocketConnection(String url) {
            // do nothing
            return null;
        }

        @Override
        public void send(Object obj) {
            // Do nothing
        }
    }

    private class TestHandler extends Handler<Map> {
        public String lastOp = "";
        public Map lastMessage = new LinkedHashMap();
        @Override
        public void handleMessage(Map message) {
            if (message.get("op") instanceof String) {
                lastOp = (String) message.get("op");
            }
            else {
                lastOp = "";
            }
            lastMessage = message;
        }

        public boolean compareOp(String expectedOp) {
            return lastOp.equals(expectedOp);
        }
        public boolean compareMessage(Map expectedMessage) {
            return lastMessage.equals(expectedMessage);
        }
        public boolean compareValue(String key, Object expectedVal) {
            Object actualVal = getTransformVal(lastMessage, key);
            return expectedVal.equals(actualVal);
        }
    }
    
    public static Object getTransformVal(Map map, String loc) {
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
