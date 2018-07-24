package io.vantiq.extjsdk;

import okhttp3.ws.WebSocket;
import okio.Buffer;
import okhttp3.RequestBody;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestExtensionWebSocketClient {
    
    // Note: testing of connections occurs in TestExtensionWebSocketListener, as more of the relevant interactions occur
    // through ExtensionWebSocketListener
    
    OpenExtensionWebSocketClient client; // OpenExtensionWebSocketClient just makes a few functions public
    String srcName;
    
    @Before
    public void setup() {
        srcName = "src";
        client = new OpenExtensionWebSocketClient(srcName); // OpenExtensionWebSocketClient just makes a few functions public
    }
    
    @After
    public void tearDown() {
        srcName = null;
        client = null;
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
    
    private class FalseWebSocket implements WebSocket {
        
        RequestBody lastBody = null;
        byte[] lastData = null;
        
        
        @Override
        public void sendMessage(RequestBody body) {
            lastBody = body;
            
        }

        @Override
        public void sendPing(Buffer payload) throws IOException {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void close(int code, String reason) throws IOException {
            // TODO Auto-generated method stub
            
        }
    }
}
