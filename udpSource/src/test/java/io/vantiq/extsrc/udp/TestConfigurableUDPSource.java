package io.vantiq.extsrc.udp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtjsdkTestBase;

public class TestConfigurableUDPSource extends ExtjsdkTestBase{
    FalseClient client;
    String srcName;
    
    @Before
    public void setup() {
        srcName = "src";
        client = new FalseClient(srcName);
        
    }
    
    @After
    public void tearDown() {
        srcName = null;
        client = null;
    }
    
    
    
    
// ====================================================================================================================
// --------------------------------------------------- Test Helpers ---------------------------------------------------
    
}
