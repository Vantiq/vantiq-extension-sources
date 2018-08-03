package io.vantiq.extsrc.udp;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtjsdkTestBase;

public class TestConfigurableUDPSource extends ExtjsdkTestBase{
    FalseClient client;
    String sourceName;
    
    @Before
    public void setup() {
        sourceName = "src";
        client = new FalseClient(sourceName);
        ConfigurableUDPSource.clients.put(sourceName, client);
    }
    
    @After
    public void tearDown() {
        sourceName = null;
        client = null;
        ConfigurableUDPSource.clients.clear();
        ConfigurableUDPSource.notificationHandlers.clear();
        
        for (DatagramSocket s : ConfigurableUDPSource.udpSocketToSources.keySet()) {
            s.close();
        }
        ConfigurableUDPSource.udpSocketToSources.clear();
    }
    
    @Test
    public void testCreateSocket() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 3141;

        // should only be created once per port/address pair
        DatagramSocket socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
        assert socket != null;
        socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
        assert socket == null;
        socket = ConfigurableUDPSource.createUDPSocket(port, address, "other source");
        assert socket == null;
        
        port = 2000;
        socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
        assert socket != null;
    }
    
    @Test
    public void testListenSocket() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 15;
        String firstSource = "first";
        String secondSource = "second";
        
        DatagramSocket socket = ConfigurableUDPSource.listenOnUDPSocket(port, address, firstSource);
        assert socket == null; // shouldn't be able to find one that already exists, since it doesn't
        socket = ConfigurableUDPSource.createUDPSocket(port, address, firstSource);
        
        DatagramSocket socket2 = ConfigurableUDPSource.listenOnUDPSocket(port, address, secondSource);
        assert socket == socket2;
        
        assert ConfigurableUDPSource.udpSocketToSources.get(socket) instanceof List;
        
        List l = (List) ConfigurableUDPSource.udpSocketToSources.get(socket);
        assert l.contains(firstSource);
        assert l.contains(secondSource);
    }
    
    @Test
    public void testClearSourceHandlers() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 15;
        String firstSource = "first";
        client = new FalseClient(firstSource);
        ConfigurableUDPSource.notificationHandlers.put(firstSource,
                new UDPNotificationHandler(new LinkedHashMap<>(), client));
        ConfigurableUDPSource.clients.put(firstSource, client);
        String secondSource = "second";
        client = new FalseClient(secondSource);
        ConfigurableUDPSource.notificationHandlers.put(secondSource,
                new UDPNotificationHandler(new LinkedHashMap<>(), client));
        ConfigurableUDPSource.clients.put(secondSource, client);
        
        DatagramSocket socket = ConfigurableUDPSource.createUDPSocket(port, address, firstSource);
        ConfigurableUDPSource.listenOnUDPSocket(port, address, secondSource);
        
        Map m = ConfigurableUDPSource.udpSocketToSources;
        assert ConfigurableUDPSource.udpSocketToSources.get(socket) instanceof List;
        List<String> l = (List) ConfigurableUDPSource.udpSocketToSources.get(socket);
        assert l.contains(firstSource);
        assert l.contains(secondSource);
        
        assert ConfigurableUDPSource.notificationHandlers.containsKey(firstSource);
        ConfigurableUDPSource.clearSourceHandlers(firstSource);
        l = (List) ConfigurableUDPSource.udpSocketToSources.get(socket);
        assert !l.contains(firstSource);
        assert l.contains(secondSource);
        assert !ConfigurableUDPSource.notificationHandlers.containsKey(firstSource);
        assert !socket.isClosed();
        
        assert ConfigurableUDPSource.notificationHandlers.containsKey(secondSource);
        ConfigurableUDPSource.clearSourceHandlers(secondSource);
        assert !ConfigurableUDPSource.udpSocketToSources.containsKey(socket);
        assert !ConfigurableUDPSource.notificationHandlers.containsKey(secondSource);
        assert socket.isClosed();
        
        
        
    }
    
    
// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
    
}
