package io.vantiq.extsrc.camel;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.camel.utils.ClientRegistry;
import org.apache.camel.CamelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

public class ClientRegistryTest {
    
    private final static String SOURCE_NAME = "aRandomSourceName";
    private final static String SOURCE_NAME_2 = "anotherRandomeSourcename";
    private final static String SERVER_URL = "https://testjunk.vantiq.com";
    private final static String SERVER_URL_2 = "https://teststuff.vantiq.com";
    
    private final static String SERVER_URL_W_SPACES = "   " + SERVER_URL + "/  ";
    
    
    @AfterEach
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
        assertNull(client, "Client should be null");
    }
    
    @Test
    public void testSimpleRegister() {
        try {
            ExtensionWebSocketClient client =
                    new ExtensionWebSocketClient(SOURCE_NAME, 15, null);
            ClientRegistry.registerClient(SOURCE_NAME, SERVER_URL, client);
            ExtensionWebSocketClient foundClient =
                    ClientRegistry.fetchClient("bar", SERVER_URL);
            assertNull(foundClient, "Client should be null");
            foundClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
            assertNotNull(foundClient, "Client should be found");
            assertEquals(client, foundClient);
    
            foundClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
            assertNotNull(foundClient, "Client should be found");
            assertEquals(client, foundClient);
    
            foundClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL + "/");
            assertNotNull(foundClient, "Normalized URL-based client should be found");
            assertEquals(client, foundClient);
        } finally {
            // Verify remove functionality
            ExtensionWebSocketClient removedClient = ClientRegistry.removeClient(SOURCE_NAME, SERVER_URL, null);
            assertNotNull(removedClient, "Client should exist before removal");
            removedClient = ClientRegistry.fetchClient(SOURCE_NAME, SERVER_URL);
            assertNull(removedClient, "Client should be null after remove");
        }
    }
    
    @Test
    public void testClientConstruction() {
        buildAndVerify(SOURCE_NAME, SERVER_URL,true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME, SERVER_URL_2, true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL_2, true);
        
        assertEquals(4, ClientRegistry.getKnownClientCount(), "Known client count");
    }
    
    @Test
    public void testClientDuplicates() {
        buildAndVerify(SOURCE_NAME, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME, SERVER_URL_2, true);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL_2,true);
    
        assertEquals(4, ClientRegistry.getKnownClientCount(), "Known client count");
    
        buildAndVerify(SOURCE_NAME_2, SERVER_URL_2, false);
        buildAndVerify(SOURCE_NAME, SERVER_URL_2, false);
        buildAndVerify(SOURCE_NAME_2, SERVER_URL, false);
        buildAndVerify(SOURCE_NAME, SERVER_URL, false);
        
        assertEquals(4, ClientRegistry.getKnownClientCount(), "Known client count after duplicate creates");
    }
    
    @Test
    public void testServerUrlWSpaces() {
        buildAndVerify(SOURCE_NAME, SERVER_URL, true);
        buildAndVerify(SOURCE_NAME, SERVER_URL_W_SPACES, false);
        
        assertEquals(1, ClientRegistry.getKnownClientCount(), "Known client count");
    }
    
    @Test
    public void testUriAdjustment() {
        String newUrl = null;
        try {
            newUrl = VantiqEndpoint.adjustVantiqTarget("vantiq://somewhere", false);
            assert newUrl.equals("https://somewhere");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("vantiq://somewhere", false);
            assert newUrl.equals("https://somewhere");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("https://internal.vantiq.com", false);
            assert newUrl.equals("https://internal.vantiq.com");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("https://internal.vantiq.com", true);
            assert newUrl.equals("https://internal.vantiq.com");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("vantiq://internal.vantiq.com", false);
            assert newUrl.equals("https://internal.vantiq.com");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("vantiq://internal.vantiq.com", true);
            assert newUrl.equals("http://internal.vantiq.com");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("https://vantiqnode:8080", false);
            assert newUrl.equals("https://vantiqnode:8080");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("https://vantiqnode:8080", true);
            assert newUrl.equals("https://vantiqnode:8080");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("vantiq://vantiqnode:8080", false);
            assert newUrl.equals("https://vantiqnode:8080");
    
            newUrl = VantiqEndpoint.adjustVantiqTarget("vantiq://vantiqnode:8080", true);
            assert newUrl.equals("http://vantiqnode:8080");
        } catch (CamelException ce) {
            fail("Could not complete adjustment for URI: " + newUrl, ce);
        }
    }
    
    public void buildAndVerify(String srcName, String target, boolean shouldBeCreated) {
        // Lambda's can only reference "effectively final" values, so we'll wrap in an AtomicReference
        AtomicReference<ExtensionWebSocketClient> client = new AtomicReference<>();
        ClientRegistry.registerClient(srcName, target, (src, url) -> {
            client.set(new ExtensionWebSocketClient(srcName, 15, null));
            return client.get();
        });
    
        if (shouldBeCreated) {
            assertNotNull(client.get(), "Client should've been created");
        } else {
            assertNull(client.get(), "Should not be created");
        }
        ExtensionWebSocketClient foundClient = ClientRegistry.fetchClient(srcName, target);
        assertNotNull(foundClient, "Client should be registered");
        if (shouldBeCreated) {
            assertEquals(foundClient, client.get(), "Registered client should be what we created");
        }
        assertEquals(srcName, foundClient.getSourceName(), "Source name match");
    }
    
    private void removeClient(String srcName, String target) {
        ClientRegistry.removeClient(srcName, target, null);
        ExtensionWebSocketClient removedClient = ClientRegistry.fetchClient(srcName, target);
        assertNull(removedClient, "Client should be null after remove");
    }
}
