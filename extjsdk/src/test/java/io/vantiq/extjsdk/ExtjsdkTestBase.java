package io.vantiq.extjsdk;

//Author: Alex Blumer
//Email: alex.j.blumer@gmail.com

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import okio.BufferedSink;
import okio.ByteString;
import okio.Source;
import okio.Timeout;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.ws.WebSocket;
import okio.Buffer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ExtjsdkTestBase {

    
    int WAIT_PERIOD = 10; // Milliseconds to wait between checks on async actions
    public void waitUntilTrue(int msTimeout, Supplier<Boolean> condition) {
        for (int i = 0; i < msTimeout / WAIT_PERIOD; i++) {
            if (condition.get() == true) {
                return;
            }
            
            try {
                Thread.sleep(WAIT_PERIOD);
            } catch (InterruptedException e) {}
        }
    }
    
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    ResponseBody createReconnectMessage(String sourceName) {
        try {
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("resourceId", sourceName);
            body.put("op", ExtensionServiceMessage.OP_RECONNECT_REQUIRED);
            return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            print("Error processing Map for createQueryMessage\n" +  e);
            return null;
        }
    }
    

    public void print(String str) {
        System.out.println(str);
    }
    ObjectMapper mapper = new ObjectMapper();
    
    public class FalseClient extends ExtensionWebSocketClient {
        public FalseClient(String sourceName) {
            super(sourceName);
        }

        @Override
        public CompletableFuture<Boolean> initiateWebsocketConnection(String url) {
            webSocketFuture = new CompletableFuture<Boolean>();
            webSocket = new FalseWebSocket();
            return null;
        }
        
        /**
         * Tries to signal that the webSocket connection completed with status {@success}. Returns true if this action
         * could be completed successfully. 
         * 
         * @param success   Whether to mark the operation as a success.
         * @return          True if it was completed successfully. False if one of: intiateWebSocketConnection() or
         *                  initiateFullConnection() hasn't 
         *                  been called since either initialization or the last close() call; the webSocket connection
         *                  has already been completed.
         */
        public boolean completeWebSocketConnection(boolean success) {
            if (webSocketFuture != null && !webSocketFuture.isDone())
            {
                webSocketFuture.complete(success);
                return true;
            } else {
                return false;
            }
        }
        /**
         * Tries to signal that the authentication completed with status {@success}. Returns true if this action
         * could be completed successfully. 
         * 
         * @param success   Whether to mark the operation as a success.
         * @return          True if it was completed successfully. False if one of: initiateWebSocket()->authenticate()
         *                  or initiateFullConnection() hasn't 
         *                  been called since either initialization or the last close() call; theauthentication
         *                  has already been completed.
         */
        public boolean completeAuthentication(boolean success) {
            if (authFuture != null && !authFuture.isDone())
            {
                authFuture.complete(success);
                return true;
            } else {
                return false;
            }
        }
        /**
         * Tries to signal that the source connection completed with status {@success}. Returns true if this action
         * could be completed successfully. 
         * 
         * @param success   Whether to mark the operation as a success.
         * @return          True if it was completed successfully. False if one of: initiateWebSocket()->authenticate()
         *                  ->sourceConnection() or initiateFullConnection() hasn't 
         *                  been called since either initialization or the last close() call; the source connection
         *                  has already been completed.
         */
        public boolean completeSourceConnection(boolean success) {
            if (sourceFuture != null && !sourceFuture.isDone())
            {
                sourceFuture.complete(success);
                return true;
            } else {
                return false;
            }
        }
        
        /**
         * Returns the last message sent by the client as a JSON object represented by bytes.
         *
         * @return  The last message sent by the client as a JSON object represented by a byte array or null if no 
         *          message has been sent.
         */
        public byte[] getLastMessageAsBytes() {
            return ((FalseWebSocket) webSocket).getMessage();
        }
        
        /**
         * Returns the last message sent by the client as a Map. Note that all arrays will have been turned into
         * ArrayLists and internal byte arrays are Base-64 strings.
         * 
         * @return  The last message sent by the client as a Map, or null if no message has been sent yet.
         */
        public Map getLastMessageAsMap() {
            try {
                return mapper.readValue(((FalseWebSocket) webSocket).getMessage(), Map.class);
            } catch(IOException e) {
                return null;
            }
        }
        
        /**
         * Returns the last message sent by the client as a {@link Response}. Note that all arrays will have been turned
         * into ArrayLists and byte arrays are Base-64 strings.
         * 
         * @return  The last message sent by the client as a Response, or null if no message has been sent yet.
         * @throws  IOException if the message could not be interpreted as a Response. This is most likely to occur if
         *          the last message was not a Response.
         */
        public Response getLastMessageAsResponse() throws IOException{
            byte[] bytes = ((FalseWebSocket) webSocket).getMessage();
            if (bytes == null) {
                return null;
            }
            return mapper.readValue(bytes, Response.class);
        }
        
        /**
         * Returns the last message sent by the client as a {@link ExtensionServiceMessage}. Note that all arrays will
         * have been turned into ArrayLists and byte arrays are Base-64 strings.
         * 
         * @return  The last message sent by the client as a ExtensionServiceMessage, or null if no message has been 
         *          sent yet.
         * @throws  IOException if the message could not be interpreted as a ExtensionServiceMessage. This is most
         *          likely to occur if the last message was not a ExtensionServiceMessage.
         */
        public ExtensionServiceMessage getLastMessageAsExtSvcMsg() throws IOException{
            byte[] bytes = ((FalseWebSocket) webSocket).getMessage();
            if (bytes == null) {
                return null;
            }
            return mapper.readValue(bytes, ExtensionServiceMessage.class);
        }
    }
    
    /**
     * A listener that contains methods to create sample responses and send those messages to itself, and getters for
     * its handlers. 
     */
    public static class TestListener extends ExtensionWebSocketListener {
        public TestListener(ExtensionWebSocketClient client) {
            super(client);
        }
        
        public Handler<Response> getAuthHandler() {
            return authHandler;
        }
        public Handler<Response> getHttpHandler() {
            return httpHandler;
        }
        public Handler<ExtensionServiceMessage> getConfigHandler() {
            return configHandler;
        }
        public Handler<ExtensionServiceMessage> getPublishHandler() {
            return publishHandler;
        }
        public Handler<ExtensionServiceMessage> getQueryHandler() {
            return queryHandler;
        }
        public Handler<ExtensionServiceMessage> getReconnectHandler() {
            return reconnectHandler;
        }
        
        /**
         * Makes the listener receive a response specifying either a successful or failed authentication
         * @param success   Whether the authentication response should respond as a success 
         */
        public void receiveAuthenticationResponse(boolean success) {
            this.onMessage(createAuthenticationResponse(success));
        }
        /**
         * Makes the listener receive a configuration message, signifying a successful source connection. A failed
         * connection is sent using {@link #sendErrorMessage}.
         * @param config        The configuration document that will be received
         * @param sourceName    The name of the source for which the connection succeeded.
         */
        public void receiveConfigResponse(Map<String,Object> config, String sourceName) {
            this.onMessage(createConfigResponse(config, sourceName));
        }
        /**
         * Makes the listener receive a Publish message
         * @param message       The object sent with the Publish
         * @param sourceName    The name of the source that sent the message
         */
        public void receivePublishMessage(Map<String,Object> message, String sourceName) {
            this.onMessage(createPublishMessage(message, sourceName));
        }
        /**
         * Makes the listener receive a Query message
         * @param message       The data to be received along with the Query message
         * @param sourceName    The name of the source that sent the message
         */
        public void receiveQueryMessage(Map<String,Object> message, String sourceName) {
            this.onMessage(createQueryMessage(message, sourceName));
        }
        /**
         * Makes the listener receive an HTTP message.
         * @param resp The {@link Response} that the listener will receive
         */
        public void receiveHttpMessage(Response resp) {
            this.onMessage(createHttpMessage(resp));
        }
        /**
         * Makes the listener receive a simple HTTP error message. This is a {@link Response} with status code 400.
         */
        public void receiveErrorMessage() {
            this.onMessage(errorMessage());
        }

        public static MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        public static final ObjectMapper mapper = new ObjectMapper();
        
        /**
         * Create a ResponseBody with a simple error. This is a {@link Response} with status code 400.
         */
        public static ResponseBody errorMessage() {
            return ResponseBody.create(JSON, "{\"status\":400}");
        }
        /**
         * Creates a response specifying either a successful or failed authentication
         * @param success   Whether the authentication response should respond as a success 
         */
        public static ResponseBody createAuthenticationResponse(boolean success) {
            if (success) {
                return ResponseBody.create(JSON, sampleAuthResponseBody);
            }
            else {
                return errorMessage();
            }
        }
        /**
         * Creates a configuration message, signifying a successful source connection. A failed
         * connection is sent using {@link #sendErrorMessage}.
         * @param config        The configuration document that will be received
         * @param sourceName    The name of the source for which the connection succeeded.
         */
        public static ResponseBody createConfigResponse(Map<String,Object> config, String sourceName) {
            try {
                Map<String,Object> body = mapper.readValue(sampleConfigBody, Map.class);
                Map<String,Object> c = new LinkedHashMap<>();
                c.put("config", config);
                body.put("resourceId", sourceName);
                body.put("object", c);
                return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
            }
            catch (Exception e) {
                return null;
            }
        }
        /**
         * Creates a Publish message
         * @param message       The object sent with the Publish
         * @param sourceName    The name of the source that sent the message
         */
        public static ResponseBody createPublishMessage(Map<String,Object> message, String sourceName) {
            try {
                Map<String,Object> body = mapper.readValue(samplePublishBody, Map.class);
                body.put("resourceId", sourceName);
                body.put("object", message);
                return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
            }
            catch (Exception e) {
                return null;
            }
        }
        /**
         * Creates a Query message
         * @param message       The data to be received along with the Query message
         * @param sourceName    The name of the source that sent the message
         */
        public static ResponseBody createQueryMessage(Map<String,Object> message, String sourceName) {
            try {
                Map<String,Object> body = mapper.readValue(sampleQueryBody, Map.class);
                body.put("resourceId", sourceName);
                body.put("object", message);
                return ResponseBody.create(JSON, mapper.writeValueAsBytes(body));
            }
            catch (Exception e) {
                return null;
            }
        }
        /**
         * Creates an HTTP message.
         * @param resp The {@link Response} that the listener will receive
         */
        public static ResponseBody createHttpMessage(Response resp) {
            try {
                return ResponseBody.create(JSON, mapper.writeValueAsBytes(resp));
            }
            catch (Exception e) {
                return null;
            }
        }
        
        public static final String sampleAuthResponseBody = "{\"status\":200, \"contentType\":\"application/json\", \"body\":{\"anonymous\":false, " +
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
        public static final String sampleConfigBody = "{\"op\":\"configureExtension\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
                "\"parameters\":{}, \"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
                "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", \"messageHeaders\":{}}";
        public static final String samplePublishBody = "{\"op\":\"publish\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
                "\"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
                "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", \"messageHeaders\":{}}";
        public static final String sampleQueryBody = "{\"op\":\"query\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
                "\"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
                "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", " +
                "\"messageHeaders\":{\"REPLY_ADDR_HEADER\":\"d15cf6b0-8a1f-11e8-b880-48152d44a589\"}}";
    }
    
    public class FalseWebSocket implements WebSocket {
        FalseBufferedSink s = new FalseBufferedSink();
        
        public byte[] getMessage() {
            return s.retrieveSentBytes();
        }
        
        @Override
        public void sendMessage(RequestBody message) throws IOException {
            message.writeTo(s);
        }

        @Override
        public void sendPing(Buffer payload) throws IOException {}
        @Override
        public void close(int code, String reason) throws IOException {}
    }
    
    public class FalseBufferedSink implements BufferedSink {
        byte[] savedBytes = null;
        
        public byte[] retrieveSentBytes() {
            return savedBytes;
        }
        
        // Called by RequestBody.writeTo
        @Override
        public BufferedSink write(byte[] source, int offset, int byteCount) throws IOException {
            savedBytes = source;
            return null;
        }
        
// ================================ Necessary to implement BufferedSink ================================
        @Override
        public void write(Buffer source, long byteCount) throws IOException {}
        @Override
        public Timeout timeout() {return null;}
        @Override
        public void close() throws IOException {}
        @Override
        public Buffer buffer() {return null;}
        @Override
        public BufferedSink write(ByteString byteString) throws IOException {return null;}
        @Override
        public BufferedSink write(byte[] source) throws IOException {return null;}
        @Override
        public long writeAll(Source source) throws IOException {return 0;}
        @Override
        public BufferedSink write(Source source, long byteCount) throws IOException {return null;}
        @Override
        public BufferedSink writeUtf8(String string) throws IOException {return null;}
        @Override
        public BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException {return null;}
        @Override
        public BufferedSink writeUtf8CodePoint(int codePoint) throws IOException {return null;}
        @Override
        public BufferedSink writeString(String string, Charset charset) throws IOException {return null;}
        @Override
        public BufferedSink writeString(String string, int beginIndex, int endIndex, Charset charset)
                throws IOException {return null;}
        @Override
        public BufferedSink writeByte(int b) throws IOException {return null;}
        @Override
        public BufferedSink writeShort(int s) throws IOException {return null;}
        @Override
        public BufferedSink writeShortLe(int s) throws IOException {return null;}
        @Override
        public BufferedSink writeInt(int i) throws IOException {return null;}
        @Override
        public BufferedSink writeIntLe(int i) throws IOException {return null;}
        @Override
        public BufferedSink writeLong(long v) throws IOException {return null;}
        @Override
        public BufferedSink writeLongLe(long v) throws IOException {return null;}
        @Override
        public BufferedSink writeDecimalLong(long v) throws IOException {return null;}
        @Override
        public BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException {return null;}
        @Override
        public void flush() throws IOException {}
        @Override
        public BufferedSink emit() throws IOException {return null;}
        @Override
        public BufferedSink emitCompleteSegments() throws IOException {return null;}
        @Override
        public OutputStream outputStream() {return null;}
    }
}
