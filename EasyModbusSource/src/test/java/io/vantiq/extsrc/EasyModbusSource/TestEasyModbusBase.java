/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import org.junit.BeforeClass;

public class TestEasyModbusBase {
    static String testIPAddress;
    static int testIPPort;
    static int testSize;
    static String testVantiqServer;
    static String testAuthToken;
    static String testSourceName;
    static String testTopicName;
    static String testTypeName;
    static String testProcedureName;
    static String testRuleName;
    


    @BeforeClass
    public static void getProps() {
        testIPAddress =  System.getProperty("EntConIPAddress", "127.0.0.1");
        testIPPort=  Integer.parseInt(System.getProperty("EntConIPPort", "502"));
        testSize=  Integer.parseInt(System.getProperty("EntBufferSize", "20"));
    }
}
