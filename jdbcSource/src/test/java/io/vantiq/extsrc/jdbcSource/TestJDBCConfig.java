/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestJDBCConfig extends TestJDBCBase {

    JDBCHandleConfiguration handler;
    
    NoSendJDBCCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    Map<String, Object> general;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        nCore = new NoSendJDBCCore(sourceName, authToken, targetVantiqServer);
        handler = new JDBCHandleConfiguration(nCore);
    }
    
    @After
    public void tearDown() {
        nCore.stop();
    }
    
    @Test
    public void testEmptyConfig() {
        Map conf = new LinkedHashMap<>();
        Map vantiqConf = new LinkedHashMap<>();
        sendConfig(conf, vantiqConf);
        assertTrue("Should fail on empty configuration", configIsFailed());
    }
    
    @Test
    public void testMissingGeneral() {
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        conf.remove("general");
        sendConfig(conf, vantiqConf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    @Test 
    public void testMissingVantiq() {
        Map conf = minimalConfig();
        Map vantiqConf = new LinkedHashMap<>();
        sendConfig(conf, vantiqConf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    @Test 
    public void testMissingPackageRows() {
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        vantiqConf.remove("packageRows");
        sendConfig(conf, vantiqConf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    @Test
    public void testPackageRowsFalse() {
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        vantiqConf.put("packageRows","false");
        sendConfig(conf, vantiqConf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    @Test
    public void testMinimalConfig() {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        nCore.start(5); // Need a client to avoid NPEs on sends
        
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }
    
    @Test
    public void testPollingConfig() {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        nCore.start(5);
        
        Map conf = minimalConfig();
        conf.put("pollTime", 3000);
        conf.put("pollQuery", "SELECT * FROM Test");
        Map vantiqConf = createMinimalVantiq();
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with polling configuration", configIsFailed());
        
        conf.remove("pollQuery");
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with missing pollQuery configuration", configIsFailed());
        
        conf.remove("pollTime");
        conf.put("pollQuery", "SELECT * FROM Test");
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with missing pollTime configuration", configIsFailed());
    }
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String, ?> jdbcConfig, Map<String, ?> vantiqConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("jdbcConfig", jdbcConfig);
        config.put("vantiq", vantiqConfig);
        obj.put("config", config);
        m.object = obj;
        
        handler.handleMessage(m);
    }
    
    public Map<String, Object> minimalConfig() {
        createMinimalGeneral();
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("general", general);
        
        return ret;
    }
    
    public void createMinimalGeneral() {
        general = new LinkedHashMap<>();
        general.put("username", testDBUsername);
        general.put("password", testDBPassword);
        general.put("dbURL", testDBURL);
    }
    
    public Map<String, String> createMinimalVantiq() {
        Map<String, String> vantiq = new LinkedHashMap<>();
        vantiq.put("packageRows", "true");
        return vantiq;
    }
    
    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
