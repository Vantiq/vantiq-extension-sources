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

        assert fakeSocket.compareData(expectedData);
        assert fakeSocket.compareAddress(expectedAddress);
        assert fakeSocket.comparePort(expectedPort);
    }
    
    @Test
    public void testPassBytesOutFrom() {
        outgoing.put("passBytesOutFrom", "byteLoc");
        
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);
        
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("byteLoc", "msg");
        testMap.put("not","relevant");
        
        byte[] expectedData = "msg".getBytes();
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
    }
    
    @Test
    public void testFormatParser() {
        Map<String,Object> formatParser = new LinkedHashMap<>();
        formatParser.put("pattern", "str:%s num:%d");
        String[] locations = {"msg", "port"};
        formatParser.put("locations", Arrays.asList(locations));
        formatParser.put("altPatternLocation", "altPattern");
        formatParser.put("altLocations", "altLocations");
        
        outgoing.put("formatParser", formatParser);
        
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);
        
        // ============ Testing standard locations and pattern ============
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("msg", "string");
        testMap.put("port",1253);
        
        String expectedData = "str:string num:1253";
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
        
        
        // ============ Testing alternate locations ============
        testMap = new LinkedHashMap<>();
        testMap.put("message", "msg");
        testMap.put("harbor", 2718);
        locations[0] = "message";
        locations[1] = "harbor";
        testMap.put("altLocations", Arrays.asList(locations));
        
        expectedData = "str:msg num:2718";
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
        
        // ============ Testing alternate pattern and locations ============
        testMap = new LinkedHashMap<>();
        testMap.put("newNum", 6283);
        testMap.put("newStr", "Hello World");
        testMap.put("altPattern", "#:%d txt:%s");
        locations[0] = "newNum";
        locations[1] = "newStr";
        testMap.put("altLocations", Arrays.asList(locations));
        
        expectedData = "#:6283 txt:Hello World";
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
    }
    
    @Test
    public void testXml() {
        outgoing.put("sendXmlRoot", "route");
        outgoing.put("passPureMapOut", true);
        
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);
        
        Map<String,Object> testMap = new LinkedHashMap<>();
        testMap.put("Hello", "World");
        testMap.put("Goodbye","Cruel World");
        
        String expectedData = "<route><Hello>World</Hello><Goodbye>Cruel World</Goodbye></route>";
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
    }
    
    @Test
    public void testCsvInitialSchema() {
        outgoing.put("passCsvOutFrom", "csv");
        String[] schema = {"name","id#"}; 
        outgoing.put("useCsvSchema", Arrays.asList(schema));
        
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);
        
        Map<String,Object> testMap = new LinkedHashMap<>();
        Map<String,Object> obj1 = new LinkedHashMap<>();
        obj1.put("name","bob newhart");
        obj1.put("id#","12345");
        Map<String,Object> obj2 = new LinkedHashMap<>();
        obj2.put("name","raynor, jim");
        obj2.put("id#","67892");
        obj2.put("notInSchema","ignored");
        Map[] objects = {obj1, obj2};
        
        testMap.put("csv", Arrays.asList(objects));
        
        String expectedData = "name,id#\nbob newhart,12345\n\"raynor, jim\",67892\n";
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
    }
    
    @Test
    public void testCsvAlternateSchema() {
        outgoing.put("passCsvOutFrom", "csv");
        outgoing.put("useCsvSchema", "schemaLoc");
        
        pHandler = new UDPPublishHandler(outgoing, fakeSocket);
        
        Map<String,Object> testMap = new LinkedHashMap<>();
        Map<String,Object> obj1 = new LinkedHashMap<>();
        obj1.put("name","bob newhart");
        obj1.put("score",26);
        Map<String,Object> obj2 = new LinkedHashMap<>();
        obj2.put("name","raynor, jim");
        obj2.put("notInSchema","ignored");
        Map[] objects = {obj1, obj2};
        String[] schema = {"name","score"};
        
        testMap.put("csv", Arrays.asList(objects));
        testMap.put("schemaLoc", Arrays.asList(schema));
        
        String expectedData = "name,score\nbob newhart,26\n\"raynor, jim\",\n";
        
        // Test UUT
        createMessage(testMap);
        pHandler.handleMessage(extMsg);
        
        assert fakeSocket.compareData(expectedData);
    }

// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
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
        public boolean compareData(byte[] expectedData) {
            return Arrays.equals(expectedData, latestPacket.getData());
        }
        public boolean compareData(String expectedData) {
            return expectedData.equals(new String(latestPacket.getData()));
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
