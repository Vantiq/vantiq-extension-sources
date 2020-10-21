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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extsrc.EasyModbusSource.exception.VantiqEasyModbusException;

public class TestEasyModbusCore extends TestEasyModbusBase {

    NoSendEasyModbusCore core;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    EasyModbus easyModbus;

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";

        easyModbus = new EasyModbus();
        core = new NoSendEasyModbusCore(sourceName, authToken, targetVantiqServer);
        core.easyModbus = easyModbus;
        core.start(10);
        assumeTrue("Simulation is not running", TestEasyModbusBase.isSimulationRunning());
    }

    @After
    public void tearDown() {
        core.stop();
    }

    @Test
    public void testPublishQuery() throws VantiqEasyModbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0);
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);

        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;

        request = new LinkedHashMap<>();
        msg.object = request;
        core.executePublish(msg);
        assertFalse("Core should not be closed", core.isClosed());

        request = new LinkedHashMap<>();
        request.put("query", "jibberish");
        msg.object = request;
        core.executePublish(msg);
        assertFalse("Core should not be closed", core.isClosed());
    }

    @Test
    public void testExecuteQuery() throws VantiqEasyModbusException {

        assumeTrue(testIPAddress != null && testIPPort != 0);
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);

        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;

        request = new LinkedHashMap<>();
        msg.object = request;
        core.executeQuery(msg);
        assertFalse("Core should not be closed", core.isClosed());

        request = new LinkedHashMap<>();
        request.put("query", "jibberish");
        msg.object = request;
        core.executeQuery(msg);
        assertFalse("Core should not be closed", core.isClosed());
    }

    @Test
    public void testExitIfConnectionFails() throws VantiqEasyModbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0);
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);

        core.start(3);
        assertTrue("Should have succeeded", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Success means it shouldn't be closed", core.isClosed());

        core.close();
        core = new NoSendEasyModbusCore(sourceName, authToken, targetVantiqServer);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to authentication failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());

        core.close();
        core = new NoSendEasyModbusCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to WebSocket failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());

        core.close();
        core = new NoSendEasyModbusCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
    }
}
