package io.vantiq.extsrc.udp;

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

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtjsdkTestBase;
import io.vantiq.extjsdk.FalseClient;

public class TestUDPConfigHandler extends ExtjsdkTestBase{
    UDPConfigHandler udpConfig;
    String sourceName;
    FalseClient client;
    ExtensionServiceMessage msg;
    
    @Before
    public void setup() {
        udpConfig = new UDPConfigHandler();
        sourceName = "src";
        client = new FalseClient(sourceName);
        msg = new ExtensionServiceMessage("");
        msg.resourceId = sourceName;
        ConfigurableUDPSource.clients.put(sourceName, client);
    }
    
    @After
    public void tearDown() {
        udpConfig = null;
        sourceName = null;
        client = null;
        msg = null;
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
    public void testIsConfiguredToReceive() {
        Map<String,Object> incoming = new LinkedHashMap<>();
        
        // First, use the right names but wrong types or invalid values
        incoming.put("receiveAllPorts", false);
        incoming.put("receiveAllAddresses", false);
        incoming.put("passPureMapIn", "not a bool");
        incoming.put("passUnspecifiedIn", new Object()); // That's enough of the vals that should be a boolean
        incoming.put("passRecAddress", 1357);
        incoming.put("passRecPort", new ArrayList().add("not actually a string"));
        List<Object> l = new ArrayList<>(); l.add(1234567); l.add(-1); l.add("2000"); l.add("not a num");
        incoming.put("receivePorts", l);
        l = new ArrayList<>(); l.add(153); l.add(new Object()); l.add("not a valid address"); l.add("1.2.3.4.5");
        incoming.put("receiveAddresses", l);
        l = new ArrayList<>();
        List<Object> subl = new ArrayList<>(); subl.add("invalid address"); subl.add(2000); l.add(subl); // bad address
        subl = new ArrayList<>(); subl.add("localhost"); subl.add(-1); l.add(subl); // bad port
        subl = new ArrayList<>(); subl.add(2000); subl.add("localhost"); l.add(subl); // wrong order
        incoming.put("receiveServers", l);
        l = new ArrayList<>();
        subl = new ArrayList<>(); subl.add(123); subl.add("location"); l.add(subl);
        subl = new ArrayList<>(); subl.add("location"); l.add(subl);
        subl = new ArrayList<>(); subl.add(123); subl.add("location"); subl.add("location2"); l.add(subl);
        subl = new ArrayList<>(); subl.add("123"); subl.add(new Object()); l.add(subl);
        incoming.put("transformations", l);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("pattern", "valid regex");
        m.put("locations", "not a list");
        incoming.put("regexParser", m);
        
        assert !udpConfig.isConfiguredToReceive(incoming);
        
        // now set one to a valid setting and try again
        m = new LinkedHashMap<>();
        m.put("pattern", "valid regex with (two) (capture groups)");
        l = new ArrayList(); l.add("firstLoc"); l.add("secondLocs");
        m.put("locations", l);
        incoming.put("regexParser", m);
        
        assert udpConfig.isConfiguredToReceive(incoming);
    }
    
    @Test
    public void testIsConfiguredToSend() {
        Map<String,Object> outgoing = new LinkedHashMap<>();
        
        outgoing.put("passPureMapOut", true);
        outgoing.put("targetAddress","notValidAddress");
        outgoing.put("targetPort", -100);
        
        assert !udpConfig.isConfiguredToSend(outgoing);
        
        outgoing.put("targetPort", 100);
        
        assert !udpConfig.isConfiguredToSend(outgoing); // targetAddress still invalid
        
        outgoing.put("targetAddress","localhost");
        
        assert udpConfig.isConfiguredToSend(outgoing);
        
        outgoing.put("passPureMapOut", "not a bool");
        
        assert udpConfig.isConfiguredToSend(outgoing); // other options don't matter
        
        outgoing.put("targetPort", -100);
        
        assert !udpConfig.isConfiguredToSend(outgoing);
    }
    
    @Test
    public void testHandleMessage() throws UnknownHostException {
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        msg.resourceId = sourceName;
        Map<String,Object> object = new LinkedHashMap<>();
        msg.object = object;
        Map<String,Object> config = new LinkedHashMap<>();
        object.put("config", config);
        Map<String,Object> extSrcConfig = new LinkedHashMap<>();
        config.put("extSrcConfig", extSrcConfig);
        Map<String,Object> general = new LinkedHashMap<>();
        Map<String,Object> incoming = new LinkedHashMap<>();
        Map<String,Object> outgoing = new LinkedHashMap<>();
        extSrcConfig.put("general", general);
        extSrcConfig.put("incoming", incoming);
        extSrcConfig.put("outgoing", outgoing);
        
        udpConfig.handleMessage(msg);
        
        assert !ConfigurableUDPSource.notificationHandlers.containsKey(sourceName);
        assert ConfigurableUDPSource.udpSocketToSources.isEmpty();
        
        incoming.put("passPureMapIn", true);
        outgoing.put("targetPort", 123);
        outgoing.put("targetAddress", "localAddress");
        
        // initialize default address
        ConfigurableUDPSource.LISTENING_ADDRESS = InetAddress.getLocalHost();
        
        udpConfig.handleMessage(msg);
        
        assert ConfigurableUDPSource.notificationHandlers.containsKey(sourceName);
        DatagramSocket socket = (DatagramSocket) ConfigurableUDPSource.udpSocketToSources.keySet().toArray()[0];
        assert socket.getLocalPort() == 3141;
        print(socket.getLocalAddress().toString());
        assert socket.getLocalAddress().equals(InetAddress.getLocalHost());
        
        int port = 123;
        general.put("listenPort", port);
        general.put("listenAddress", "localhost");
        
        udpConfig.handleMessage(msg);
        
        assert ConfigurableUDPSource.udpSocketToSources.keySet().size() == 2;
    }
}