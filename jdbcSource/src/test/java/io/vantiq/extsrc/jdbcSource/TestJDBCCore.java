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


public class TestJDBCCore {
    
    NoSendJDBCCore core;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    
    JDBC jdbc;
    
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
        
        jdbc = new JDBC();
        core = new NoSendJDBCCore(sourceName, authToken, targetVantiqServer);
        core.jdbc = jdbc;
        core.start(10);
    }
    
    @After
    public void tearDown() {
        core.stop();
    }
    
    @Test
    public void testPublishQuery() throws SQLException, LinkageError, ClassNotFoundException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
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
    public void testExecuteQuery() throws SQLException, LinkageError, ClassNotFoundException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
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
    public void testExitIfConnectionFails() throws SQLException, LinkageError, ClassNotFoundException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        core.start(3);
        assertTrue("Should have succeeded", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Success means it shouldn't be closed", core.isClosed());
        
        
        core.close();
        core = new NoSendJDBCCore(sourceName, authToken, targetVantiqServer);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to authentication failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendJDBCCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to WebSocket failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendJDBCCore(sourceName, authToken, targetVantiqServer);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
    }
}
