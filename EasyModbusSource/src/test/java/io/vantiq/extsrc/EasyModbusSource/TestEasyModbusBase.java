/*
 * Copyright (c) 2018 Vantiq, Inc.
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
    static String testVantiqServer;
    static String testAuthToken;
    static String testSourceName;
    static String testTopicName;
    static String testTypeName;
    static String testProcedureName;
    static String testRuleName;
    


    @BeforeClass
    public static void getProps() {
        testIPAddress =  System.getProperty("EntConIPAddress", null);
        testIPPort=  Integer.parseInt(System.getProperty("EntConIPPort", null));
    }
}
