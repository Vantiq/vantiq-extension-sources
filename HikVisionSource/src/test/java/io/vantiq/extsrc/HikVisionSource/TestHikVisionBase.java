/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import org.junit.BeforeClass;

public class TestHikVisionBase {
    static String testVantiqServer;
    static String testAuthToken;
    static String testSourceName ; 
    static String testTopicName;
    static String testProcedureName;
    static String testRuleName;
    static String testTypeName;

    static String testFileFolderPath;
    static String testFilePrefix;
    static String testFileExtension;

    
    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
        testTopicName = System.getProperty("EntConTestTopicName", "testTopicName");
        testProcedureName = System.getProperty("EntConTestProcedureName", "testProcedureName");
        testRuleName = System.getProperty("EntConTestRuleName", "testRuleName");
        testTypeName = System.getProperty("EntConTestTypeName", "testTypeName");
    
        testFileFolderPath = System.getProperty("EntFileFodlerPath", "testFileFolderPath");
        testFilePrefix = System.getProperty("EntFilePrefix", "testFilePrefix");
        testFileExtension = System.getProperty("EntFileExtension", "testFileExtension");
            
    }
}
