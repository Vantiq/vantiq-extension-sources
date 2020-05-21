/*
 * Copyright (c) 2018 Vantiq, Inc.
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
        core.start(10);
    }
    
    @After
    public void tearDown() {
        core.stop();
    }
    
    
}
