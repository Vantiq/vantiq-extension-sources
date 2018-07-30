package io.vantiq.extsrc.udp;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.extjsdk.ExtensionServiceMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.*;

public class TestUDPPublishHandler {

    // Objects used by test objects
    FalseSocket fakeSocket;
    String sourceName;
    ExtensionServiceMessage extMsg;
    ObjectMapper mapper = new ObjectMapper();
    int port;
    String address;

    // Objects being tested
    UDPPublishHandler pHandler;

    // Create the three parts of a config document
    Map<String,Object> outgoing = new LinkedHashMap<>();


    @After
    public void tearDown() {
        fakeSocket = null;
        outgoing = null;
        sourceName = null;
        extMsg = null;
        address = null;
    }

    @Before
    public void setUp() {
        try {
            fakeSocket = new FalseSocket();
        }
        catch (Exception e) {
            System.out.println(e);
            assert false;
        }
        outgoing = new LinkedHashMap<>();
        sourceName = "src";
        port = 1234;
        address = "localhost";

        outgoing.put("targetAddress", address);
        outgoing.put("targetPort", port);
    }

    @Test
    public void testEmptyConfig() {
        // Fill the three parts of a config document

        // Create the UUT
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);

        // Create sample data
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("str", "msg");

        // Create expected data
        String expectedAddress = "127.0.0.1";
        int expectedPort = port;
        Map<String,Object> expectedData = new LinkedHashMap<>();

        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        assert fakeSocket.compareData(expectedData);
        assert fakeSocket.compareAddress(expectedAddress);
        assert fakeSocket.comparePort(expectedPort);
    }


    @Test
    public void testPassPure() {
        // Fill the three parts of a config document
        outgoing.put("passPureMapOut", true);

        // Create the UUT
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);

        // Create sample data
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("str", "msg");

        // Create expected data
        String expectedAddress = "127.0.0.1";
        int expectedPort = port;
        Map<String,Object> expectedData = new LinkedHashMap<>();
        expectedData.put("str", "msg");

        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        assert fakeSocket.compareData(expectedData);
        assert fakeSocket.compareAddress(expectedAddress);
        assert fakeSocket.comparePort(expectedPort);
    }

    @Test
    public void testTransform() {
        // Fill the three parts of a config document
        String[][] transforms = {
                {"str","message"},
                {"str", "place.loc"},
                {"str","invalid","nope"},
                {"str"}
        };
        List<List> transformations = new ArrayList<>();
        for (String[] t : transforms) {
            List list = new ArrayList(Arrays.asList(t));
            transformations.add(list);

        }
        outgoing.put("transformations", transformations);

        // Create the UUT
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);

        // Create sample data
        String expectedAddress = "127.0.0.1";
        int expectedPort = port;
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("str", "msg");

        // Manage expectations
        Map<String,Object> expectedData = new LinkedHashMap<>();
        Map<String,Object> subMap = new LinkedHashMap<>();
        subMap.put("loc","msg");
        expectedData.put("place",subMap);
        expectedData.put("message","msg");


        // Test UUT
        createMessage(testMap);

        pHandler.handleMessage(extMsg);
        System.out.println("Expected Data: " + expectedData);
        try {
            System.out.println("Actual Data: " + mapper.readValue(fakeSocket.latestPacket.getData(), Map.class));
        }
        catch (Exception e) {System.out.println("Failed to translate packet data" + e);}
        assert fakeSocket.compareData(expectedData);
        assert fakeSocket.compareAddress(expectedAddress);
        assert fakeSocket.comparePort(expectedPort);
    }


    @Test
    public void testPassUnspecifiedWithTransform() {
        // Fill the three parts of a config document
        outgoing.put("passUnspecifiedOut", true);
        String[][] transforms = {
                {"str","message"},
                {"str", "place.loc"},
                {"str","invalid","nope"},
                {"str"}
        };
        List<List> transformations = new ArrayList<>();
        for (String[] t : transforms) {
            List list = new ArrayList(Arrays.asList(t));
            transformations.add(list);

        }
        outgoing.put("transformations", transformations);

        // Create the UUT
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);

        // Create sample data
        String expectedAddress = "127.0.0.1";
        int expectedPort = port;
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("str", "msg");
        testMap.put("stay","here");

        // Manage expectations
        Map<String,Object> expectedData = new LinkedHashMap<>();
        Map<String,Object> subMap = new LinkedHashMap<>();
        subMap.put("loc","msg");
        expectedData.put("place",subMap);
        expectedData.put("message","msg");
        expectedData.put("stay","here");

        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);


        System.out.println("Expected Data: " + expectedData);
        try {
            System.out.println("Actual Data: " + mapper.readValue(fakeSocket.latestPacket.getData(), Map.class));
        }
        catch (Exception e) {System.out.println("Failed to translate packet data" + e);}
        assert fakeSocket.compareData(expectedData);
        assert fakeSocket.compareAddress(expectedAddress);
        assert fakeSocket.comparePort(expectedPort);
    }

    private void createMessage(Map data) {
        Map m = new LinkedHashMap<>();
        m.put("op", ExtensionServiceMessage.OP_PUBLISH);
        m.put("resourceId", sourceName);
        m.put("resourceName", "sources");
        m.put("object", data);
        
        extMsg = new ExtensionServiceMessage("").fromMap(m);
    }


    private class FalseSocket extends DatagramSocket {
        DatagramPacket latestPacket;

        FalseSocket() throws SocketException {
            latestPacket = null;

        }

        @Override
        public void send(DatagramPacket packet) {
            latestPacket = packet;
        }

        public boolean comparePort(int expectedPort) {
            return expectedPort == latestPacket.getPort();
        }
        public boolean compareAddress(String expectedAddress) {
            return expectedAddress.equals(latestPacket.getAddress().getHostAddress());
        }
        public boolean compareData(Map expectedData) {
            try {
                return expectedData.equals(mapper.readValue(latestPacket.getData(), Map.class));
            }
            catch (Exception e) {
                return false;
            }
        }
    }
}
