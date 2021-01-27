/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestCSVConfigFixedLength extends TestCSVBase {

    CSVHandleConfiguration handler;

    NoSendCSVCore nCore;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    Map<String, Object> schema;

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        nCore = new NoSendCSVCore(sourceName, authToken, targetVantiqServer);
        handler = new CSVHandleConfiguration(nCore);
    }

    @After
    public void tearDown() {
        nCore.stop();
    }

    @Test
    public void testMissingSchema() {

        Map<String, Object> conf = minimalConfig();
        Map<String, Object> vantiqConf = createMinimalOptions();
        conf.remove("schema");
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
        assumeTrue(testFileFolderPath != null && testFullFilePath != null && IsTestFileFolderExists());

        nCore.start(5); // Need a client to avoid NPEs on sends

        Map<String, Object> conf = minimalConfig();
        Map<String, Object> vantiqConf = createMinimalOptions();
        sendConfig(conf, vantiqConf);
        boolean r = configIsFailed();
        assertFalse("Should not fail with minimal configuration", r);// configIsFailed());
    }

    // ================================================= Helper functions
    // =================================================
    public void sendConfig(Map<String, ?> csvConfig, Map<String, ?> options) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");

        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("csvConfig", csvConfig);
        config.put("options", options);
        obj.put("config", config);
        m.object = obj;

        handler.handleMessage(m);
    }

    public Map<String, Object> minimalConfig() {
        createTestSchema();
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("schema", schema);
        createMinimalConfig(ret);

        return ret;
    }

    public void createTestSchema() {
        schema = new LinkedHashMap<>();
        schema.put("code", createFixedLengthTestSchemaField(0, 13, "string"));
        schema.put("name", createFixedLengthTestSchemaField(14, 20, "string", "Cp862", true));
        schema.put("weighted", createFixedLengthTestSchemaField(35, 1, "string"));
        schema.put("price", createFixedLengthTestSchemaField(37, 6, "string"));
        schema.put("cost", createFixedLengthTestSchemaField(44, 6, "string"));
        schema.put("department", createFixedLengthTestSchemaField(51, 2, "string"));
    }

    public void createMinimalConfig(Map<String, Object> config) {
        config.put("fileFolderPath", testFileFolderPath);
        config.put("filePrefix", testFilePrefix);
        config.put("fileExtension", testFileExtension);
        config.put("maxLinesInEvent", testMaxLinesInEvent);
        config.put("delimiter", testDelimiter);
        config.put("FileType", "FixedLength");
    }

    public Map<String, Object> createMinimalOptions() {
        Map<String, Object> vantiq = new LinkedHashMap<>();
        vantiq.put("maxActiveTasks", 2);
        vantiq.put("maxQueuedTasks", 4);
        vantiq.put("processExistingFiles", true);
        vantiq.put("deleteAfterProcessing", false);
        vantiq.put("extensionAfterProcessing", "csv.done");

        return vantiq;
    }

    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }

    // *** Helper funciton for support fixed len configyration files
    public Map<String, Object> createFixedLengthTestSchemaField(int offset, int length, String type) {
        Map<String, Object> fieldAttr = new LinkedHashMap<>();
        fieldAttr.put("offset", offset);
        fieldAttr.put("length", length);
        fieldAttr.put("type", type);

        return fieldAttr;
    }

    public Map<String, Object> createFixedLengthTestSchemaField(int offset, int length, String type, String charSet,
            boolean reversed) {
        Map<String, Object> fieldAttr = createFixedLengthTestSchemaField(offset, length, type);
        fieldAttr.put("charset", charSet);
        fieldAttr.put("reversed", reversed);
        return fieldAttr;
    }
}
