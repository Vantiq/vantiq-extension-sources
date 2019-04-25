/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import org.junit.BeforeClass;

public class TestJMSBase {
    static String testJMSUsername;
    static String testJMSPassword;
    static String testJMSURL;
    static String testJMSConnectionFactory;
    static String testJMSInitialContext;
    static String testJMSTopic;
    static String testJMSQueue;
    static String testAuthToken;
    static String testVantiqServer;
    static String jmsDriverLoc;
    static String testSourceName;
    static String testTypeName;
    
    @BeforeClass
    public static void getProps() {
        jmsDriverLoc = System.getenv("JMS_DRIVER_LOC");
        testJMSUsername = System.getProperty("EntConJMSUsername", null);
        testJMSPassword = System.getProperty("EntConJMSPassword", null);
        testJMSURL = System.getProperty("EntConJMSURL", null);
        testJMSConnectionFactory = System.getProperty("EntConJMSConnectionFactory", null);
        testJMSInitialContext = System.getProperty("EntConJMSInitialContext", null);
        testJMSTopic = System.getProperty("EntConJMSTopic", null);
        testJMSQueue = System.getProperty("EntConJMSQueue", null);
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
    }
}
