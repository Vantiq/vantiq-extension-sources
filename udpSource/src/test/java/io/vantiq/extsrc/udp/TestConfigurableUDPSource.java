
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.udp;

import static org.junit.Assume.assumeTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.FalseClient;

public class TestConfigurableUDPSource extends UDPTestBase {
    FalseClient client;
    String sourceName;
    InetAddress invalidAddress;
    
    @Before
    public void setup() {
        sourceName = "src";
        client = new FalseClient(sourceName);
        ConfigurableUDPSource.clients.put(sourceName, client);
        try {
            invalidAddress = InetAddress.getByAddress(new byte[] {1,2,3,4});
        } catch (UnknownHostException e) {
            // All tests that use invalidAddress should assumeTrue(invalidAddress != null) 
        }
    }
    
    @After
    public void tearDown() {
        sourceName = null;
        client = null;
        invalidAddress = null;
        ConfigurableUDPSource.clients.clear();
        ConfigurableUDPSource.notificationHandlers.clear();
        ConfigurableUDPSource.sourcePorts.clear();
        ConfigurableUDPSource.sourceAddresses.clear();
        ConfigurableUDPSource.sourceServers.clear();
        
        for (DatagramSocket s : ConfigurableUDPSource.udpSocketToSources.keySet()) {
            s.close();
        }
        ConfigurableUDPSource.udpSocketToSources.clear();
    }
    
    @Test
    public void testCreateSocket() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 10213;

        assumeTrue("Cannot perform test. Address at port:" + port + " and IP:" + address + "already bound"
                , socketCanBind(port, address));
        // should only be created once per port/address pair
        DatagramSocket socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
        assert socket != null;
        socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
        assert socket == null;
        socket = ConfigurableUDPSource.createUDPSocket(port, address, "other source");
        assert socket == null;
        
        port = 2005;
        assumeTrue("Cannot perform test. Address at port:" + port + " and IP:" + address + "already bound"
                , socketCanBind(port, address));     
        socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
        assert socket != null;
    }
    
    @Test
    public void testListenSocket() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 10213;
        String firstSource = "first";
        String secondSource = "second";
        
        DatagramSocket socket = ConfigurableUDPSource.listenOnUDPSocket(port, address, firstSource);
        assert socket == null; // shouldn't be able to find one that already exists, since it doesn't
        
        assumeTrue("Cannot perform test. Address at port:" + port + " and IP:" + address + "already bound"
                , socketCanBind(port, address));
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
        int port = 10213;
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
        
        assumeTrue("Cannot perform test. Address at port:" + port + " and IP:" + address + "already bound"
                , socketCanBind(port, address));             
        DatagramSocket socket = ConfigurableUDPSource.createUDPSocket(port, address, firstSource);
        ConfigurableUDPSource.listenOnUDPSocket(port, address, secondSource);
        
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

    @Test
    public void testSetNotificationHandler() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 10213;
        
        // ============== Use a map where all each has one valid entry ===============
        Map<String,Object> incoming = new LinkedHashMap<>();
        List<Object> l = new ArrayList<>(); l.add("localhost"); l.add("notValidAddress"); l.add(1354);
        incoming.put("receiveAddresses", l);
        l = new ArrayList<>(); l.add(port); l.add(-1); l.add(123456789);
        incoming.put("receivePorts", l);
        l = new ArrayList<>();
        List<Object> subl = new ArrayList<>(); subl.add("invalid address"); subl.add(2000); l.add(subl); // bad address
        subl = new ArrayList<>(); subl.add("localhost"); subl.add(-1); l.add(subl); // bad port
        subl = new ArrayList<>(); subl.add(2000); subl.add("localhost"); l.add(subl); // wrong order
        subl = new ArrayList<>(); subl.add("localhost"); subl.add(port); l.add(subl);
        incoming.put("receiveServers", l);
        
        UDPNotificationHandler handler = new UDPNotificationHandler(incoming, client);
        
        ConfigurableUDPSource.setNotificationHandler(handler, sourceName, incoming);
        
        assert ConfigurableUDPSource.notificationHandlers.get(sourceName) == handler;
        l = (List) ConfigurableUDPSource.sourcePorts.get(sourceName);
        assert l.contains(port);
        assert l.size() == 1;
        l = (List) ConfigurableUDPSource.sourceAddresses.get(sourceName);
        assert l.contains(address);
        assert l.size() == 1;
        subl = new ArrayList<>(); subl.add(address); subl.add(port);
        l = (List) ConfigurableUDPSource.sourceServers.get(sourceName);
        assert l.contains(subl);
        assert l.size() == 1;
        
        // =================== Use a map with ALL_ADDR/PORT set =================
        ConfigurableUDPSource.sourceServers.clear(); // Don't need to clear the rest, they will be overwritten
        incoming = new LinkedHashMap<>();
        incoming.put("receiveAllAddresses", true);
        incoming.put("receiveAllPorts", true);
        
        handler = new UDPNotificationHandler(incoming, client);
        ConfigurableUDPSource.setNotificationHandler(handler, sourceName, incoming);
        
        assert ConfigurableUDPSource.notificationHandlers.get(sourceName) == handler;
        String addr = (String) ConfigurableUDPSource.sourceAddresses.get(sourceName);
        assert addr.equals(ConfigurableUDPSource.ALL_ADDR);
        int prt = (Integer) ConfigurableUDPSource.sourcePorts.get(sourceName);
        assert prt == ConfigurableUDPSource.ALL_PORTS;
        
        // =================== Use a map with only Servers set ==========================
        incoming = new LinkedHashMap<>();
        subl = new ArrayList<>(); subl.add("localhost"); subl.add(port); l.add(subl);
        incoming.put("receiveServers", l);
        
        handler = new UDPNotificationHandler(incoming, client);
        ConfigurableUDPSource.setNotificationHandler(handler, sourceName, incoming);
        
        assert ConfigurableUDPSource.notificationHandlers.get(sourceName) == handler;
        addr = (String) ConfigurableUDPSource.sourceAddresses.get(sourceName);
        assert addr.equals(ConfigurableUDPSource.NO_ADDR);
        prt = (Integer) ConfigurableUDPSource.sourcePorts.get(sourceName);
        assert prt == ConfigurableUDPSource.NO_PORTS;
        subl = new ArrayList<>(); subl.add(address); subl.add(port);
        l = (List) ConfigurableUDPSource.sourceServers.get(sourceName);
        assert l.contains(subl);
        assert l.size() == 1;
    }
    
    @Test
    public void testReceivingFromServer() throws UnknownHostException {
        assumeTrue(invalidAddress != null);
        InetAddress address = InetAddress.getByName("localhost");
        int port = 10213;
        
        ConfigurableUDPSource.sourceAddresses.put(sourceName, ConfigurableUDPSource.ALL_ADDR);
        ConfigurableUDPSource.sourcePorts.put(sourceName, ConfigurableUDPSource.ALL_PORTS);
        assert ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        List<Object> l = new ArrayList<>(); l.add(123);
        ConfigurableUDPSource.sourcePorts.put(sourceName, l);
        assert !ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        l.add(port);
        ConfigurableUDPSource.sourcePorts.put(sourceName, l);
        assert ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        l = new ArrayList<>(); l.add(invalidAddress);
        ConfigurableUDPSource.sourceAddresses.put(sourceName, l);
        assert !ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        l.add(address);
        ConfigurableUDPSource.sourceAddresses.put(sourceName, l);
        assert ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        ConfigurableUDPSource.sourcePorts.put(sourceName, ConfigurableUDPSource.NO_PORTS);
        assert !ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        List<List> list = new ArrayList<>();
        List<Object> subl = new ArrayList<>(); subl.add(invalidAddress); subl.add(port); list.add(subl);
        subl = new ArrayList<>(); subl.add(invalidAddress); subl.add(123); list.add(subl);
        subl = new ArrayList<>(); subl.add(address); subl.add(123); list.add(subl);
        ConfigurableUDPSource.sourceServers.put(sourceName, list);
        assert !ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
        
        subl = new ArrayList<>(); subl.add(address); subl.add(port); list.add(subl);
        ConfigurableUDPSource.sourceServers.put(sourceName, list);
        assert ConfigurableUDPSource.receivingFromServer(sourceName, port, address);
    }
    
    @Test
    public void testSendFromDatagram() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("localhost");
        int port = 10213;
        
        // Handler that only records the data given
        UDPNotificationHandler firstHandler = new UDPNotificationHandler(new LinkedHashMap<>(), client) {
            @Override
            public void handleMessage(DatagramPacket packet) {
                variable.put("message", packet);
                variable.put("data", packet.getData());
            }
        };
        // New instance of the same handler as firstHandler
        UDPNotificationHandler secondHandler = new UDPNotificationHandler(new LinkedHashMap<>(), client) {
            @Override
            public void handleMessage(DatagramPacket packet) {
                variable.put("message", packet);
                variable.put("data", packet.getData());
            }
        };
        
        String firstSource = "first";
        client = new FalseClient(firstSource);
        ConfigurableUDPSource.notificationHandlers.put(firstSource, firstHandler);
        ConfigurableUDPSource.clients.put(firstSource, client);
        String secondSource = "second";
        client = new FalseClient(secondSource);
        ConfigurableUDPSource.notificationHandlers.put(secondSource, secondHandler);
        ConfigurableUDPSource.clients.put(secondSource, client);
        
        // firstSource set up to receive the packet, secondSource is not
        ConfigurableUDPSource.sourceAddresses.put(firstSource, ConfigurableUDPSource.ALL_ADDR);
        List l = new ArrayList<>(); l.add(port);
        ConfigurableUDPSource.sourcePorts.put(firstSource, l);
        
        ConfigurableUDPSource.sourceAddresses.put(secondSource, ConfigurableUDPSource.ALL_ADDR);
        l = new ArrayList<>(); l.add(123);
        ConfigurableUDPSource.sourcePorts.put(secondSource, l);
        
        byte[] data = {10,53,27,-100};
        DatagramPacket p = new DatagramPacket(data, data.length, address, port); 
        
        l = new ArrayList(); l.add(firstSource); l.add(secondSource);
        ConfigurableUDPSource.sendFromDatagram(p, l);
        
        assert firstHandler.getVariable().get("message") == p;
        assert firstHandler.getVariable().get("data") == data;
        assert secondHandler.getVariable().get("message") == null;
        assert secondHandler.getVariable().get("data") == null;
        
    }
    
    @Test
    public void testSetupServer() throws UnknownHostException {
        Map config = new LinkedHashMap<>();
        
        config.put("authToken", new Object());
        
        try {
            ConfigurableUDPSource.setupServer(config);
            assert false; // Should throw RTE for an invalid or missing authToken
        } catch (RuntimeException e) {}
        
        config.put("authToken", "token");
        config.put("targetServer", "ws://localhost:8080");
        
        ConfigurableUDPSource.setupServer(config); // Should not throw RTE
        
        // check defaults
        assert ConfigurableUDPSource.targetVantiqServer.equals("ws://localhost:8080");
        assert ConfigurableUDPSource.MAX_UDP_DATA == 1024;
        assert ConfigurableUDPSource.LISTENING_PORT == 3141;
        assert ConfigurableUDPSource.LISTENING_ADDRESS.equals(InetAddress.getLocalHost());
        assert ConfigurableUDPSource.authToken.equals("token");
        
        
        config.put("maxPacketSize", 2048);
        config.put("defaultBindPort", 10213);
        config.put("defaultBindAddress", "localhost");
        config.put("authToken", "new token");
        
        ConfigurableUDPSource.setupServer(config);
        
        assert ConfigurableUDPSource.targetVantiqServer.equals("ws://localhost:8080");
        assert ConfigurableUDPSource.MAX_UDP_DATA == 2048;
        assert ConfigurableUDPSource.LISTENING_PORT == 10213;
        assert ConfigurableUDPSource.LISTENING_ADDRESS.equals(InetAddress.getByName("localhost"));
        assert ConfigurableUDPSource.authToken.equals("new token");
        
        // reset it back to defaults
        config = new LinkedHashMap<>();
        config.put("authToken", "token");
        config.put("targetServer", "ws://localhost:8080");
        
        ConfigurableUDPSource.setupServer(config);
    }
    
// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
}
