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
import static org.junit.Assume.assumeTrue;

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

public class TestTestConnectorCore {

    NoSendTestConnectorCore core;

    String sourceName;
    String authToken;
    String targetVantiqServer;

    private static String environmentVariable;
    private static String filename;

    private static final String NONEXISTENT_ENV_VAR = "anEnvironmentVariableThatIsUnlikelyToExist";

    @BeforeClass
    public static void checkFilesAndEnvVars() {
        environmentVariable = System.getProperty("TestConnectorEnvVarName", null);
        filename = System.getProperty("TestConnectorFilename", null);
    }

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";

        core = new NoSendTestConnectorCore(sourceName, authToken, targetVantiqServer);
        core.start(10);
    }

    @After
    public void tearDown() {
        core.stop();
    }

    @Test
    public void testExecutePublish() {
        assumeTrue(environmentVariable != null && filename != null);
        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        request = new LinkedHashMap<>();
        msg.object = request;
        core.executePublish(msg);
        assertFalse("Core should not be closed", core.isClosed());
    }

    @Test
    public void testExecuteQuery() {
        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        request = new LinkedHashMap<>();
        msg.object = request;
        core.executeQuery(msg);
        assertFalse("Core should not be closed", core.isClosed());
    }

    @Test
    public void testProcessRequest() {
        assumeTrue(environmentVariable != null && filename != null);

        // Put data in the request and call the process method
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> filenames = new ArrayList<>();
        filenames.add(filename);
        request.put("filenames", filenames);
        List<String> environmentVariables = new ArrayList<>();
        environmentVariables.add(environmentVariable);
        request.put("environmentVariables", environmentVariables);
        Map result = core.processRequest(request, null);

        // Check file portion of result
        assert result.get("files") instanceof Map;
        Map resultFiles = (Map) result.get("files");
        assert resultFiles.get(filename) instanceof String;
        assert !((String) resultFiles.get(filename)).isEmpty();

        // Check env var portion of result
        assert result.get("environmentVariables") instanceof Map;
        Map resultEnvVars = (Map) result.get("environmentVariables");
        assert resultEnvVars.get(environmentVariable) instanceof String;
        assert !((String) resultEnvVars.get(environmentVariable)).isEmpty();

        // Now add a request that contains nonexistent environmentVariables. This shouldn't fail and should return the
        // expected map with a null value for the env var. For nonexistent files, we'd log/return an error if the file
        // wasn't found.
        environmentVariables.clear();
        environmentVariables.add(NONEXISTENT_ENV_VAR);
        request.clear();
        request.put("environmentVariables", environmentVariables);
        result.clear();
        resultFiles.clear();
        resultEnvVars.clear();
        result = core.processRequest(request, null);

        // Check env var portion of result
        assert result.get("environmentVariables") instanceof Map;
        resultEnvVars = (Map) result.get("environmentVariables");
        assert resultEnvVars.get(NONEXISTENT_ENV_VAR) == null;
    }

    @Test
    public void testProcessInvalidRequest() {
        // Put invalid arguments into request to make sure it returns null (if this is a query, we would have sent a
        // query error)
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("filenames", "aString");
        Map result = core.processRequest(request, null);
        assert result == null;

        // Now lets try with an int for the env var
        request.clear();
        request.put("environmentVariables", 100);
        result = core.processRequest(request, null);
        assert result == null;

        // Why not try with a boolean! You get the point...
        request.clear();
        request.put("filenames", true);
        result = core.processRequest(request, null);
        assert result == null;

        // Lets try using a list of empty Strings for the filenames
        request.clear();
        List<String> filenames = new ArrayList<>();
        filenames.add("");
        request.put("filenames", filenames);
        result = core.processRequest(request, null);
        assert result == null;

        // Now lets do the same for the environmentVariables, but include one valid string
        request.clear();
        List<String> environmentVariables = new ArrayList<>();
        environmentVariables.add("aValidString");
        environmentVariables.add("");
        request.put("environmentVariables", environmentVariables);
        result = core.processRequest(request, null);
        assert result == null;

        // Now lets provide nothing in the request and make sure that fails.
        request.clear();
        result = core.processRequest(request, null);
        assert result == null;
    }

    @Test
    public void testExitIfConnectionFails() {
        core.start(3);
        assertTrue("Should have succeeded", core.exitIfConnectionFails(3));
        assertFalse("Success means it shouldn't be closed", core.isClosed());


        core.close();
        core = new NoSendTestConnectorCore(sourceName, authToken, targetVantiqServer);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to authentication failing", core.exitIfConnectionFails(3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());

        core.close();
        core = new NoSendTestConnectorCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to WebSocket failing", core.exitIfConnectionFails(3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());

        core.close();
        core = new NoSendTestConnectorCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
    }
}
