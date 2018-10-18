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
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestJDBCConfig {

    JDBCHandleConfiguration handler;
    
    NoSendJDBCCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    static String testDBDriver;
    
    Map<String, Object> general;
    Map<String, Object> dataSource;
    Map<String, Object> neuralNet;
    
    @BeforeClass
    public static void getProps() {
        testDBUsername = System.getProperty("EntConJDBCUsername", null);
        testDBPassword = System.getProperty("EntConJDBCPassword", null);
        testDBURL = System.getProperty("EntConJDBCURL", null);
    }
    
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
        sendConfig(conf);
        assertTrue("Should fail on empty configuration", configIsFailed());
    }
    
    @Test
    public void testMissingGeneral() {
        Map conf = minimalConfig();
        conf.remove("general");
        sendConfig(conf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    
    @Test
    public void testMinimalConfig() {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null);
        nCore.start(5); // Need a client to avoid NPEs on sends
        
        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String, ?> ORConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("jdbcConfig", ORConfig);
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
        general.put("driver", testDBDriver);
    }
    
    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
