/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.BeforeClass;

public class TestCSVBase {
    static String testVantiqServer;
    static String testAuthToken;

    static String testDelimiter;
    static String testFileFolderPath;
    static String testFilePrefix;
    static String testFileExtension;
    static String testFullFilePath ; 
    static int testMaxLinesInEvent;
    static boolean testprocessNullValues ;
    
    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);

        testFileFolderPath = System.getProperty("EntFileFolderPath", "c:/tmp/csvTest/in");
        testFilePrefix = System.getProperty("EntFilePrefix", "csvt");
        testFileExtension = System.getProperty("EntFileExtension", "csv");
        testFullFilePath = System.getProperty("EntFullFilePath", "a.csv");
        testMaxLinesInEvent=Integer.parseInt( System.getProperty("EntMaxLinesInEvent", "200"));
        testDelimiter = System.getProperty("EntDelimiter", ",");
        testprocessNullValues = Boolean.parseBoolean(System.getProperty("EntProcessNullValue", "false"));
    }

    public static boolean IsTestFileFolderExists() {
        Path p = Paths.get(testFileFolderPath);
        return Files.exists(p);
    }
}
