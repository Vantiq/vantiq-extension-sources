/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import org.junit.BeforeClass;

public class TestJDBCBase {
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    static String testAuthToken;
    static String testVantiqServer;
    static String jdbcDriverLoc;
    static String testSourceName;
    static String testTypeName;
    static String testProcedureName;
    static String testRuleName;
    static String testTopicName;
    
    @BeforeClass
    public static void getProps() {
        testDBUsername = System.getProperty("EntConJDBCUsername", null);
        testDBPassword = System.getProperty("EntConJDBCPassword", null);
        testDBURL = System.getProperty("EntConJDBCURL", null);
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        jdbcDriverLoc = System.getenv("JDBC_DRIVER_LOC");
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
        testTypeName = System.getProperty("EntConTestTypeName", "testTypeName");
        testProcedureName = System.getProperty("EntConTestProcedureName", "testProcedureName");
        testRuleName = System.getProperty("EntConTestRuleName", "testRuleName");
        testTopicName = System.getProperty("EntConTestTopicName", "/test/topic/name");
    }
}
