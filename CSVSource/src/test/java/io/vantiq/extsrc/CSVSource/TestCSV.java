/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.vantiq.client.Vantiq;
import io.vantiq.extsrc.CSVSource.exception.VantiqCSVException;

public class TestCSV extends TestCSVBase {
    
    static final int CORE_START_TIMEOUT = 10;
    static CSVCore core;
    static CSV csv;
    static Vantiq vantiq;
    static Map<String,Object> config ;
    static Map<String,Object> options ;

    @Before
    public void setup() {
        config = new HashMap<String,Object>(); 
        
        TestCSVConfig o = new TestCSVConfig();
        config = o.minimalConfig();
        options = o.createMinimalOptions();

        csv = new CSV();

        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }
    
    @Test
    public void testCorrectErrors() throws VantiqCSVException {
        assumeTrue(testFileFolderPath != null && testFullFilePath != null && IsTestFileFolderExists()) ;
        csv.setupCSV(null,testFileFolderPath, testFullFilePath,config,options);
    }
    // ================================================= Helper functions =================================================
}