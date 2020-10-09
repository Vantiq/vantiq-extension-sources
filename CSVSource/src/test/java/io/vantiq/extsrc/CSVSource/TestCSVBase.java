/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import org.junit.BeforeClass;

public class TestCSVBase {
    static String testVantiqServer;
    static String testAuthToken;
    static String testSourceName ; 

    static String testTopicName;
    static String testProcedureName;
    static String testRuleName;
    static String testTypeName;

//    static String testIPAddress ; 
//    static int testIPPort; 

    static String testFileFolderPath;
    static String testFilePrefix;
    static String testFileExtension;
    static String testFullFilePath ; 
    static int testMaxLinesInEvent;
    
    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
        testTopicName = System.getProperty("EntConTestTopicName", "testTopicName");
        testProcedureName = System.getProperty("EntConTestProcedureName", "testProcedureName");
        testRuleName = System.getProperty("EntConTestRuleName", "testRuleName");
        testTypeName = System.getProperty("EntConTestTypeName", "testTypeName");
    
        testFileFolderPath = System.getProperty("EntFileFolderPath", "c:/tmp/csvTest/in");
        testFilePrefix = System.getProperty("EntFilePrefix", "csvt");
        testFileExtension = System.getProperty("EntFileExtension", "csv");
        testFullFilePath = System.getProperty("EntFileExtension", "c:/tmp/csvTest/in/csvt*.csv");
        testMaxLinesInEvent=Integer.parseInt( System.getProperty("EntMaxLinesInEvent", "200"));
    }
}
