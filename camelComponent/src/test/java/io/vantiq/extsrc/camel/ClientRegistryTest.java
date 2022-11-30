package io.vantiq.extsrc.camel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.camel.utils.ClientRegistry;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class ClientRegistryTest {
    
    private final static String SOURCE_NAME = "aRandomSourceName";
    private final static String SOURCE_NAME_2 = "anotherRandomeSourcename";
    private final static String SERVER_URL = "https://testjunk.vantiq.com";
    private final static String SERVER_URL_2 = "https://teststuff.vantiq.com";
    
    
    @After
    public void removeDetritus() {
        removeClient(SOURCE_NAME, SERVER_URL);
        removeClient(SOURCE_NAME_2, SERVER_URL);
        removeClient(SOURCE_NAME, SERVER_URL_2);
        removeClient(SOURCE_NAME_2, SERVER_URL_2);
    }
    @Test
    public void testNotThere() {
        ExtensionWebSocketClient client =
                ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
        assertNull("Client should be null", client);
    }
    
    @Test
    public void testSimpleRegister() {
        try {
            ExtensionWebSocketClient client =
                    new ExtensionWebSocketClient(SOURCE_NAME, 15, null);
            ClientRegistry.registerClient(SOURCE_NAME, SERVER_URL, client);
            ExtensionWebSocketClient foundClient =
                    ClientRegistry.fetchClient("bar", SERVER_URL);
            assertNull("Client should be null", foundClient);
            foundClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
            assertNotNull("Client should be found", foundClient);
            assertEquals(client, foundClient);
    
            foundClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
            assertNotNull("Client should be found", foundClient);
            assertEquals(client, foundClient);
    
            foundClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL + "/");
            assertNotNull("Normalized URL-based client should be found", foundClient);
            assertEquals(client, foundClient);
        } finally {
            // Verify remove functionality
            ExtensionWebSocketClient removedClient = ClientRegistry.removeClient(SOURCE_NAME, SERVER_URL, null);
            assertNotNull("Client should exist before removal", removedClient);
            removedClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
            assertNull("Client should be null after remove", removedClient);
        }
    }
    
    @Test
    public void testClientConstruction() {
        buildAndVerify(SOURCE_NAME, SERVER_URL,true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME, SERVER_URL_2, true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL_2, true);
        
        assertEquals("Known client count", 4, ClientRegistry.getKnownClientCount());
    }
    
    @Test
    public void testClientDuplicates() {
        buildAndVerify(SOURCE_NAME, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME, SERVER_URL_2, true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL_2,true);
    
        assertEquals("Known client count", 4, ClientRegistry.getKnownClientCount());
    
        buildAndVerify(SOURCE_NAME_2, SERVER_URL_2, false);
        buildAndVerify(SOURCE_NAME, SERVER_URL_2, false);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL, false);
        buildAndVerify(SOURCE_NAME, SERVER_URL, false);
        
        assertEquals("Known client count after duplicate creates", 4, ClientRegistry.getKnownClientCount());
    }
    
    public void buildAndVerify(String srcName, String target, boolean shouldBeCreated) {
        // Lambda's can only reference "effectively final" values, wo we'll wrap in an AtomicReference
        AtomicReference<ExtensionWebSocketClient> client = new AtomicReference<>();
        ClientRegistry.registerClient(srcName, target, (src, url) -> {
            client.set(new ExtensionWebSocketClient(srcName, 15, null));
            return client.get();
        });
    
        if (shouldBeCreated) {
            assertNotNull("Client should've been created", client.get());
        } else {
            assertNull("Should not be created", client.get());
        }
        ExtensionWebSocketClient foundClient = ClientRegistry.fetchClient(srcName, target);
        assertNotNull("Client should be registered", foundClient);
        if (shouldBeCreated) {
            assertEquals("Registered client should be what we created", foundClient, client.get());
        }
        assertEquals("Source name match", srcName, foundClient.getSourceName());
    }
    
    private void removeClient(String srcName, String target) {
        ClientRegistry.removeClient(srcName, target, null);
        ExtensionWebSocketClient removedClient = ClientRegistry.fetchClient(srcName, target);
        assertNull("Client should be null after remove", removedClient);
    }
}
