/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.llrpConnector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//import io.vantiq.extsrc.llrpConnector.LLRPConnectorHandleConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestLLRPConnectorConfig {

    LLRPConnectorHandleConfiguration handler;

    NoSendLLRPConnectorCore nCore;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    Map<String, Object> general;

    @Before
    public void setup() {
//        sourceName = "zebraRFID";   // "source";
//        authToken = "rco8Tn6Db433m8BmdyKQ4UABVnKUBc0Ds_mtSBbCiE4="; // "token";
//        targetVantiqServer = "internal.vantiq.com";    // "dev.vantiq.com";
        sourceName = "source";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";

        nCore = new NoSendLLRPConnectorCore(sourceName, authToken, targetVantiqServer);
        handler = new LLRPConnectorHandleConfiguration(nCore);
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
        nCore.start(5); // Need a client to avoid NPEs on sends

        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }

// ================================================= Helper functions =================================================

    public void sendConfig(Map<String, ?> llrpConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");

        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("llrpConfig", llrpConfig);
        obj.put("config", config);
        m.object = obj;

        handler.handleMessage(m);
    }

    public Map<String, Object> minimalConfig() {
        createMinimalGeneral();
        Map<String, Object> ret = new LinkedHashMap<>();
//        Map<String, Object> general = new LinkedHashMap<>();
        ret.put("general", general);

        return ret;
    }
    public void createMinimalGeneral() {
        general = new LinkedHashMap<>();
        general.put("hostname", "fx7500fcc3e9");    // "hostname";
        general.put("readerPort", 5084);  // "readerPort";
    }


    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}