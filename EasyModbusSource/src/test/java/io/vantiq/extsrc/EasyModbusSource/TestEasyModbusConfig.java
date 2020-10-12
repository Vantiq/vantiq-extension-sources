/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestEasyModbusConfig extends TestEasyModbusBase {

    EasyModbusHandleConfiguration handler;
    
    NoSendEasyModbusCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    Map<String, Object> general;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        nCore = new NoSendEasyModbusCore(sourceName, authToken, targetVantiqServer);
        handler = new EasyModbusHandleConfiguration(nCore);
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
    public void testMinimalConfig() {
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        nCore.start(5); // Need a client to avoid NPEs on sends
        
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }
    
    @Test
    public void testPollingConfig() {
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
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

    @Test
    public void testAsynchronousProcessing() {
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        nCore.start(5);

        // Setting asynchronousProcessing incorrectly
        Map conf = minimalConfig();
        conf.put("asynchronousProcessing", "jibberish");
        Map vantiqConf = createMinimalVantiq();
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with invalid asynchronousProcessing value", configIsFailed());

        // Setting asynchronousProcessing to false (same as not including it)
        conf.put("asynchronousProcessing", false);
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with asynchronousProcessing set to false", configIsFailed());

        // Setting asynchronousProcessing to true
        conf.put("asynchronousProcessing", true);
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail with asynchronousProcessing set to true", configIsFailed());

        // Setting maxRunningThreads and maxQueuedTasks incorrectly
        conf.put("maxActiveTasks", "jibberish");
        conf.put("maxQueuedTasks", "moreJibberish");
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail when maxActiveTasks and maxQueuedTasks are set incorrectly", configIsFailed());

        // Setting maxRunningThreads and maxQueuedTasks correctly
        conf.put("maxActiveTasks", 10);
        conf.put("maxQueuedTasks", 20);
        sendConfig(conf, vantiqConf);
        assertFalse("Should not fail when maxActiveTasks and maxQueuedTasks are set correctly", configIsFailed());
    }
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String, ?> easyModbusConfig, Map<String, ?> vantiqConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("easyModbusConfig", easyModbusConfig);
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
        general.put("TCPAddress", testIPAddress);
        general.put("TCPPort", testIPPort);
        general.put("Size", testSize);
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
