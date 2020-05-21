/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

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
import io.vantiq.extsrc.CSVSource.exception.VantiqCSVException;


public class TestCSVCore extends TestCSVBase {
    
    NoSendCSVCore core;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    CSV csv;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "internal.vantiq.com";
        
        csv = new CSV();
        core = new NoSendCSVCore(sourceName, authToken, targetVantiqServer);
        core.csv = csv;
        core.start(10);
    }
    
    @After
    public void tearDown() {
        core.stop();
    }
    
    
}
