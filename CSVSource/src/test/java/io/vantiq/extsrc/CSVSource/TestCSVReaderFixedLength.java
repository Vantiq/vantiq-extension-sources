/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import static org.junit.Assert.assertTrue;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestCSVReaderFixedLength extends TestCSVConfigFixedLength {

    static Map<String, Object> config;
    static Map<String, Object> options;

    @Before
    public void setup() {
        config = new HashMap<String, Object>();

        TestCSVConfigFixedLength o = new TestCSVConfigFixedLength();
        config = o.minimalConfig();
        options = o.createMinimalOptions();
        CSVReader.segmentList.clear();
    }

    @After
    public void tearDown() {
        List<CSVCore> sources = CSVMain.sources;
        System.setSecurityManager(null);
        if (sources != null) {
            for (CSVCore s : sources) {
                s.stop();
            }
            CSVMain.sources = null;
        }
    }

    @Test
    public void testReadSimpleFile1Record() {
        CreateFileForTest(testFullFilePath, "0000000000006 itemName123412341234 1 100000 200000 01");

        try {
            ArrayList<Map<String, String>> content = CSVReader.executeFixedRecord(testFullFilePath, config, null);
            assertTrue("Unexpected lines of csv file in array", content.size() == 1);
            assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 6);

            String v = content.get(0).get("code");
            assertTrue("Unexpected field code in first of line of csv file in array", v.equals("0000000000006"));
            v = content.get(0).get("name");
            assertTrue("Unexpected field name in first of line of csv file in array",
                    content.get(0).get("name").equals("432143214321emaNmeti"));
            assertTrue("Unexpected field price in first of line of csv file in array",
                    content.get(0).get("price").equals("100000"));
        } catch (InterruptedException ex) {
            assertTrue("Exception " + ex, false);
        }
    }

    @Test
    public void testToManyEventsForSingleSegment() {
        CreateFileForTest(testFullFilePath, "0000000000006 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000007 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000008 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000009 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000010 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000011 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000012 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000013 itemName123412341234 1 100000 200000 01");

        config.put("maxLinesInEvent", 3);
        try {
            ArrayList<Map<String, String>> lastSegment = CSVReader.executeFixedRecord(testFullFilePath, config, null);
            assertTrue("Unexpected number of segments", CSVReader.segmentList.size() == 3);

            assertTrue("Unexpected lines of csv file in array", lastSegment.size() == 2);
            assertTrue("Unexpected values in first of line of csv file in array", lastSegment.get(0).size() == 6);

            String v = lastSegment.get(0).get("code");
            assertTrue("Unexpected field code in first of line of csv file in array", v.equals("0000000000012"));
            v = lastSegment.get(0).get("name");
            assertTrue("Unexpected field name in first of line of csv file in array",
                    lastSegment.get(0).get("name").equals("432143214321emaNmeti"));
            assertTrue("Unexpected field price in first of line of csv file in array",
                    lastSegment.get(0).get("price").equals("100000"));

            java.util.LinkedHashMap firstSegment = (java.util.LinkedHashMap) CSVReader.segmentList.get(0);
            ArrayList<Map<String, String>> firstSegmentLines = (ArrayList<Map<String, String>>) firstSegment
                    .get("lines");

            assertTrue("Unexpected lines of csv file in array", firstSegment.size() == 3);
            assertTrue("Unexpected values in first of line of csv file in array", firstSegmentLines.size() == 3);

            String v1 = firstSegmentLines.get(0).get("code");
            assertTrue("Unexpected field code in first of line of csv file in array", v1.equals("0000000000006"));
            v = firstSegmentLines.get(0).get("name");
            assertTrue("Unexpected field name in first of line of csv file in array",
                    firstSegmentLines.get(0).get("name").equals("432143214321emaNmeti"));
            assertTrue("Unexpected field price in first of line of csv file in array",
                    firstSegmentLines.get(0).get("price").equals("100000"));
        } catch (InterruptedException ex) {
            assertTrue("Exception " + ex, false);
        }
    }

    @Test
    public void testReadSimpleFile2Record() {
        CreateFileForTest(testFullFilePath, "0000000000006 itemName123412341234 1 100000 200000 01");
        AppendFileForTest(testFullFilePath, "0000000000007 itemName123412341234 1 100000 200000 01");
        try {
            ArrayList<Map<String, String>> content = CSVReader.executeFixedRecord(testFullFilePath, config, null);
            assertTrue("Unexpected number of segments", CSVReader.segmentList.size() == 1);

            assertTrue("Unexpected lines of csv file in array", content.size() == 2);
            assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 6);
            assertTrue("Unexpected number of segments", CSVReader.segmentList.size() == 1);

            String v = content.get(0).get("code");
            assertTrue("Unexpected field code in first of line of csv file in array", v.equals("0000000000006"));
            v = content.get(0).get("name");
            assertTrue("Unexpected field name in first of line of csv file in array",
                    content.get(0).get("name").equals("432143214321emaNmeti"));
            assertTrue("Unexpected field price in first of line of csv file in array",
                    content.get(0).get("price").equals("100000"));
        } catch (InterruptedException ex) {
            assertTrue("Exception " + ex, false);
        }
    }




    // ================================================= Helper functions
    // =================================================
    void CreateFileForTest(String fileName, String content) {
        try {
            FileWriter write = new FileWriter(fileName, false);
            PrintWriter print = new PrintWriter(write);
            print.printf("%s\n", content);
            print.close();
        } catch (IOException ex) {
            assert ex.getMessage().equals("CreateFileForTest failed");
        }
    }

    void AppendFileForTest(String fileName, String content) {
        try {
            FileWriter write = new FileWriter(fileName, true);
            PrintWriter print = new PrintWriter(write);
            print.printf("%s\n", content);
            print.close();
        } catch (IOException ex) {
            assert ex.getMessage().equals("CreateFileForTest failed");
        }
    }
}
