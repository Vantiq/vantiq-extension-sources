package io.vantiq.extsrc.udp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestUDPConfigHandler {
    UDPConfigHandler udpConfig;
    String srcName;
    
    @Before
    public void setup() {
        udpConfig = new UDPConfigHandler();
        srcName = "src";
    }
    
    @After
    public void tearDown() {
        udpConfig = null;
        srcName = null;
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
}
