package io.vantiq.extjsdk;

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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
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
