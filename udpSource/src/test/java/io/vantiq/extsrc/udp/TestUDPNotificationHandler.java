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
import java.util.regex.Pattern;

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
    public void testRegexSpeed() {
        incoming.put("passAllAddresses", true);
        Map<String, Object> regexParser = new LinkedHashMap<>();
        String pattern = "<(\\d{1,3})>([1-9]\\d{0,2}) (?:(-)|(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\."
                + "\\d{1,6})?(?:Z|[-+]\\d{2}:\\d{2}))) (-|[!-~]{1,255}) (-|[!-~]{1,48}) (-|[!-~]{1,128}) (-|[!-~]{1,32}"
                + ") (?:(-)|((?:\\[[!-~&&[^ =\\]]]{1,32}(?: [!-~&&[^ =\\]]]{1,32}=\"(?:[\\u0000-\\uffff&&[^\"\\\\\\]]]|"
                + "(?:\\\\[\\\\\"\\]]))*\")*])+))(?: (?:((?!\\xef\\xbb\\xbf).*)|(?:\\xef\\xbb\\xbf([\\u0000-\\uffff]*))"
                + "))?";
        regexParser.put("pattern", pattern);
        String[] locations = {"priority", "version", "noTimestamp", "timestamp", "hostName", "appName", "processId", 
                              "messageId", "noStructuredData", "structuredData", "standardMessage", "utfMessage"};
        regexParser.put("locations", Arrays.asList(locations));
        incoming.put("regexParser", regexParser);
        String testMessage = "<0>198 2018-07-23T15:26:20-07:00 remote iApp user branch [name] Here be a message for ya!";
        byte[] packetBytes = testMessage.getBytes();
        DatagramPacket packet= new DatagramPacket(packetBytes, packetBytes.length);
        
        assert Pattern.matches(pattern, testMessage);
        
        nHandler = new UDPNotificationHandler(incoming, fakeClient);
        nHandler.handleMessage(packet);
        int runCount = 100 * 1000;
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < runCount; i ++) {
            packetBytes = testMessage.getBytes();
            packetBytes[1] = (byte) ((i & 7) + 48);
            packet = new DatagramPacket(packetBytes, packetBytes.length);
            nHandler.handleMessage(packet);
        }
        
        long timeTaken = System.currentTimeMillis() - currentTime;
        
        
        System.out.println("Time taken for " + runCount + " messages was " + timeTaken + " ms");
        System.out.println("This is " + ((double)timeTaken) / runCount + " ms per message and "
                    + (int) (1.0 / timeTaken * runCount) + " thousand messages per second");
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


        assert fakeClient.compareData(expectedData);
        assert fakeClient.compareSource(sourceName);
    }
    
    @Test
    public void testXML() {
        incoming.put("expectXMLIn", true);
        incoming.put("passXmlRootNameIn", "root");
        incoming.put("passPureMapIn", true);
        
        nHandler = new UDPNotificationHandler(incoming, fakeClient);
        
        String address = "localhost";
        int port = 1234;
        String testStr = "<rootElem><front>frontVal</front><back>backVal</back></rootElem>";
        
        Map<String,Object> expectedData = new LinkedHashMap<>();
        expectedData.put("front", "frontVal");
        expectedData.put("back", "backVal");
        expectedData.put("root", "rootElem");
        
        createPacket(testStr, address, port);
        nHandler.handleMessage(pack);
        
        assert fakeClient.compareData(expectedData);
    }
    
    @Test
    public void testCSV() {
        incoming.put("expectCsvIn", true);
        
        nHandler = new UDPNotificationHandler(incoming, fakeClient);
        
        String address = "localhost";
        int port = 1234;
        String testStr = "first,second,last\na,b,c\nd,e,f";
        
        Map<String,Object> subMap1 = new LinkedHashMap<>();
        subMap1.put("first", "a");
        subMap1.put("second", "b");
        subMap1.put("last", "c");
        Map<String,Object> subMap2 = new LinkedHashMap<>();
        subMap2.put("first", "d");
        subMap2.put("second", "e");
        subMap2.put("last", "f");
        Map[] expectedData = {subMap1,subMap2}; 
        
        createPacket(testStr, address, port);
        nHandler.handleMessage(pack);
        
        assert fakeClient.compareData(Arrays.asList(expectedData));
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
