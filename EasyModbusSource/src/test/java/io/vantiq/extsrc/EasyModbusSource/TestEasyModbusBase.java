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

    @BeforeClass
    public static void getProps() {
        testIPAddress = System.getProperty("EntConIPAddress");
        String temp = System.getProperty("EntConIPPort");
        if (temp != null) {
            testIPPort = Integer.parseInt(temp);
        }
        temp = System.getProperty("EntBufferSize");
        if (temp != null) {
            testSize = Integer.parseInt(temp);
        }
    }
}
