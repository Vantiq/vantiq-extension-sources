/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import org.junit.After;
import org.junit.Before;


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
