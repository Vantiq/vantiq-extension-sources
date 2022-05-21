/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.BeforeClass;

public class TestFTPClientBase {
    static String testVantiqServer;
    static String testAuthToken;

    static String testLocalFolderPath;
    static String testRemoteFolderPath;
    static String testServerIPAddress;
    static int testServerIPPort;
    static String testUsername ; 
    static String testPassword ; 
    
    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);

        testLocalFolderPath = System.getProperty("EntLocalFolderPath", "c:/tmp/in");
        testRemoteFolderPath = System.getProperty("EntRemoteFolderPath", "/bizerba/etc");
        testServerIPAddress = System.getProperty("EntServerIPAddress", "192.168.1.187");
        testServerIPPort = Integer.parseInt(System.getProperty("EntIPPort", "21"));
        testUsername= System.getProperty("EntUsername", "bizuser");
        testPassword = System.getProperty("EntPassword", "pbizerba");
    }

    public static boolean IsTestFileFolderExists() {
        Path p = Paths.get(testLocalFolderPath);
        return Files.exists(p);
    }
}
