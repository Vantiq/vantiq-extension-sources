/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extsrc.HikVisionSource.exception.VantiqHikVisionException;

public class TestHikVisionCore extends TestHikVisionBase {

    NoSendHikVisionCore core;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    HikVision hikVision;

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "internal.vantiq.com";

        hikVision = new HikVision();
        core = new NoSendHikVisionCore(sourceName, authToken, targetVantiqServer);
        core.hikVision = hikVision;
        core.hikVision.oClient = core.client;
        core.start(10);
    }

    @After
    public void tearDown() {
        core.stop();
    }

    @Test
    public void testPublish() {

        ExtensionServiceMessage msg = CreateTestedMsgObject();

        core.executePublish(msg);

        assertTrue("No Error Should take place (beside connection error)", core.hikVision.iLastErr == 3);
    }

    @Test
    public void testPublishOnDisabledCamera() {

        ExtensionServiceMessage msg = CreateTestedMsgObject();

        core.hikVision.cameras.get(0).Enable = false;

        core.executePublish(msg);
        assertTrue("Error Should take place", core.hikVision.iLastErr == -1);
    }
    // -------------------------------Utilities---------------------------------

    private ExtensionServiceMessage CreateTestedMsgObject() {
        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        core.hikVision.iLastErr = 0; // reset the lastError field.
        core.hikVision.oClient = core.client;
        request = new LinkedHashMap<>();

        request.put("cameraId", "TestId");
        request.put("command", 1);
        request.put("state", 0);
        msg.object = request;

        setTestingHikVisionAttributes();
        return msg;
    }

    private void setTestingHikVisionAttributes() {
        CameraEntry ce = new CameraEntry();
        ce.CameraId = "TestId";
        ce.DVRIPAddress = "127.0.0.1";
        ce.Enable = true;
        core.hikVision.cameras = new ArrayList<CameraEntry>();
        core.hikVision.cameras.add(ce);
    }
}
