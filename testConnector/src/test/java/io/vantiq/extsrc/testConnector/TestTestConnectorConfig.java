/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.testConnector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vantiq.extjsdk.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestTestConnectorConfig {

    TestConnectorHandleConfiguration handler;

    NoSendTestConnectorCore nCore;

    String sourceName;
    String authToken;
    String targetVantiqServer;
    File serverConfigFile;

    @Before
    public void setup() throws IOException {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";

        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        serverConfigFile = new File("server.config");
        serverConfigFile.createNewFile();
        serverConfigFile.deleteOnExit();
        Utils.obtainServerConfig();

        nCore = new NoSendTestConnectorCore(sourceName, authToken, targetVantiqServer);
        handler = new TestConnectorHandleConfiguration(nCore);
    }

    @After
    public void tearDown() {
        nCore.stop();
        serverConfigFile.delete();
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

    @Test
    public void testPollingConfig() {
        nCore.start(5);

        List<String> filenames = new ArrayList<>();
        filenames.add("build.gradle");

        Map conf = minimalConfig();
        conf.put("pollingInterval", 5000);
        conf.put("filenames", "SELECT * FROM Test");
        sendConfig(conf);
        assertFalse("Should not fail with polling configuration", configIsFailed());

        conf.remove("pollingInterval");
        sendConfig(conf);
        assertFalse("Should not fail with no pollingInterval configuration", configIsFailed());
    }

// ================================================= Helper functions =================================================

    public void sendConfig(Map<String, ?> testConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");

        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("testConfig", testConfig);
        obj.put("config", config);
        m.object = obj;

        handler.handleMessage(m);
    }

    public Map<String, Object> minimalConfig() {
        Map<String, Object> ret = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        ret.put("general", general);

        return ret;
    }

    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
