package io.vantiq.extjsdk;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
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
    

    void print(String str) {
        System.out.println(str);
    }
    ObjectMapper mapper = new ObjectMapper();
    
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
