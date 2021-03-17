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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.FalseClient;

public class TestLLRPConnectorCore {

    NoSendLLRPConnectorCore core;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    @Before
    public void setup() {
        sourceName = "source";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";

        core = new NoSendLLRPConnectorCore(sourceName, authToken, targetVantiqServer);
        core.start(10);
    }

    @After
    public void tearDown() {
        core.stop();
    }


    @Test
    public void testExitIfConnectionFails() {
        core.start(3);
        assertTrue("Should have succeeded", core.exitIfConnectionFails(3));
        assertFalse("Success means it shouldn't be closed", core.isClosed());


        core.close();
        core = new NoSendLLRPConnectorCore(sourceName, authToken, targetVantiqServer);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to authentication failing", core.exitIfConnectionFails(3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());

        core.close();
        core = new NoSendLLRPConnectorCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to WebSocket failing", core.exitIfConnectionFails(3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());

        core.close();
        core = new NoSendLLRPConnectorCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
    }
}