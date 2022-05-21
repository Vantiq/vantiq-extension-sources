/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestFTPClientConfig extends TestFTPClientBase {

    FTPClientHandleConfiguration handler;

    NoSendFTPClientCore nCore;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    Map<String, Object> schema;

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        nCore = new NoSendFTPClientCore(sourceName, authToken, targetVantiqServer);
        handler = new FTPClientHandleConfiguration(nCore);
    }

    @After
    public void tearDown() {
        nCore.stop();
    }

    @Test
    public void testMissingDefaultServer() {

        Map<String, Object> conf = minimalConfig();
        Map<String, Object> vantiqConf = createMinimalOptions();
       
        conf.remove("server");
        conf.remove("port");
        sendConfig(conf, vantiqConf);
        assertTrue("Should fail when missing 'schema' configuration", configIsFailed());
    }

    @Test
    public void testMissingOptions() {
        Map<String, Object> conf = minimalConfig();
        sendConfig(conf, null);
        assertTrue("Should fail when missing 'Options' configuration", configIsFailed());
    }

    @Test
    public void testMinimalConfig() {
        assumeTrue(testLocalFolderPath != null && IsTestFileFolderExists());

        nCore.start(5); // Need a client to avoid NPEs on sends

        Map<String, Object> conf = minimalConfig();
        Map<String, Object> vantiqConf = createMinimalOptions();
        sendConfig(conf, vantiqConf);
        boolean r = configIsFailed();
        assertFalse("Should not fail with minimal configuration", r);// configIsFailed());
    }

    // ================================================= Helper functions
    // =================================================
    public void sendConfig(Map<String, ?> FTPClientConfig, Map<String, ?> options) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");

        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ftpClientConfig", FTPClientConfig);
        config.put("options", options);
        obj.put("config", config);
        m.object = obj;

        handler.handleMessage(m);
    }

    public Map<String, Object> minimalConfig() {
        Map<String, Object> ret = new LinkedHashMap<>();
        createMinimalConfig(ret);

        return ret;
    }

    public void createMinimalConfig(Map<String, Object> config) {
        config.put("name", "default");
        config.put("server", testServerIPAddress);
        config.put("port", testServerIPPort);
        config.put("username", testUsername);
        config.put("password", testPassword);
        config.put("remoteFolderPath", testRemoteFolderPath);
        config.put("localFolderPath", testLocalFolderPath);
    }

    public Map<String, Object> createMinimalOptions() {
        Map<String, Object> vantiq = new LinkedHashMap<>();
        vantiq.put("maxActiveTasks", 2);
        vantiq.put("maxQueuedTasks", 4);
        vantiq.put("processExistingFiles", true);
        vantiq.put("deleteAfterProcessing", false);
        vantiq.put("extensionAfterProcessing", "FTPClient.done");
        vantiq.put("pollTime", 30000);

        return vantiq;
    }

    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
