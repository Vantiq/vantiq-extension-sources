package io.vantiq.extsrc.udp;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import io.vantiq.extjsdk.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.*;

public class TestUDPNotificationHandler {

    // Objects used by test objects
    FalseClient fakeClient;
    String sourceName;
    DatagramPacket pack;

    // Objects being tested
    UDPNotificationHandler nHandler;

    // Create the three parts of a config document
    Map<String,Object> incoming = new LinkedHashMap<>();

    @After
    public void tearDown() {
        fakeClient = null;
        incoming = null;
        sourceName = null;
        pack = null;
    }

    @Before
    public void setUp() {
        incoming = new LinkedHashMap<>();
        sourceName = "src";
        fakeClient = new FalseClient(sourceName);
    }

    @Test
    public void testEmptyConfig() {
        // Fill the three parts of a config document

        // Create the UUT
        nHandler = new UDPNotificationHandler(incoming, fakeClient);

        String address = "localhost";
        int port = 1234;

        // Test UUT
        String testStr = "{\"str\":\"msg\"}";
        createPacket(testStr, address, port);
        nHandler.handleMessage(pack);
        assert fakeClient.compareData(new LinkedHashMap());
        assert fakeClient.compareSource(sourceName);
    }

    @Test
    public void testPassReceive() {

        // Fill the three parts of a config document
        incoming.put("passRecAddress", "Address");
        incoming.put("passRecPort", "port#");

        // Create the UUT
        nHandler = new UDPNotificationHandler(incoming, fakeClient);

        String address = "localhost";
        String expectedAddress = "127.0.0.1";
        int port = 1234;
        Map<String,Object> expectedData = new LinkedHashMap<>();

        expectedData.put("Address", expectedAddress);
        expectedData.put("port#", port);

        // Test UUT
        String testStr = "{\"str\":\"msg\"}";
        createPacket(testStr, address, port);
        nHandler.handleMessage(pack);
        System.out.println("Expected Data: " + expectedData);
        System.out.println("Actual Data: " + fakeClient.latestData);
        assert fakeClient.compareData(expectedData);
        assert fakeClient.compareSource(sourceName);
    }

    @Test
    public void testPassPure() {
        // Fill the three parts of a config document
        incoming.put("passPureMapIn", true);

        // Create the UUT
        nHandler = new UDPNotificationHandler(incoming, fakeClient);

        String address = "localhost";
        int port = 1234;
        Map<String,Object> expectedData = new LinkedHashMap<>();

        expectedData.put("str","msg");

        // Test UUT
        String testStr = "{\"str\":\"msg\"}";
        createPacket(testStr, address, port);

        nHandler.handleMessage(pack);
        System.out.println("Expected Data: " + expectedData);
        System.out.println("Actual Data: " + fakeClient.latestData);
        assert fakeClient.compareData(expectedData);
        assert fakeClient.compareSource(sourceName);
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
        incoming.put("transformations", transformations);

        // Create the UUT
        nHandler = new UDPNotificationHandler(incoming, fakeClient);

        // Create sample data
        String address = "localhost";
        int port = 1234;
        Map<String,Object> expectedData = new LinkedHashMap<>();
        String testStr = "{\"str\":\"msg\"}";

        // Manage expectations
        Map<String,Object> subMap = new LinkedHashMap<>();
        subMap.put("loc","msg");
        expectedData.put("place",subMap);
        expectedData.put("message","msg");


        // Test UUT
        createPacket(testStr, address, port);

        nHandler.handleMessage(pack);
        System.out.println("Expected Data: " + expectedData);
        System.out.println("Actual Data: " + fakeClient.latestData);
        assert fakeClient.compareData(expectedData);
        assert fakeClient.compareSource(sourceName);
    }

    @Test
    public void testPassUnspecifiedWithTransform() {
        // Fill the three parts of a config document
        incoming.put("passUnspecifiedIn", true);
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
        incoming.put("transformations", transformations);

        // Create the UUT
        nHandler = new UDPNotificationHandler(incoming, fakeClient);

        // Create sample data
        String address = "localhost";
        int port = 1234;
        Map<String,Object> expectedData = new LinkedHashMap<>();
        String testStr = "{\"str\":\"msg\",\"stay\":\"here\"}";

        // Manage expectations
        Map<String,Object> subMap = new LinkedHashMap<>();
        subMap.put("loc","msg");
        expectedData.put("place",subMap);
        expectedData.put("message","msg");
        expectedData.put("stay","here");

        // Test UUT
        createPacket(testStr, address, port);
        nHandler.handleMessage(pack);


        System.out.println("Expected Data: " + expectedData);
        System.out.println("Actual Data: " + fakeClient.latestData);
        assert fakeClient.compareData(expectedData);
        assert fakeClient.compareSource(sourceName);
    }

    private void createPacket(String testStr, String address, int port) {
        byte[] msg;
        try {
            msg = testStr.getBytes();
            pack = new DatagramPacket(msg, msg.length, InetAddress.getByName(address), port);
        }catch (Exception e){}
    }

    private class FalseClient extends ExtensionWebSocketClient {
        Object latestData = null;
        String sourceName;

        FalseClient(String sourceName) {
            super(sourceName);
            this.sourceName = sourceName;
        }

        @Override
        public void sendNotification(Object data) {
            latestData = data;
        }

        boolean compareSource(String expectedSource) {
            return expectedSource.equals(sourceName);
        }

        boolean compareData(Object expectedData) {
            return expectedData.equals(latestData);
        }
    }
}
