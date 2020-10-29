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

public class TestCSVReader extends TestCSVBase{
    
    static Map<String,Object> config ;
    static Map<String,Object> options ;
    
    @Before
    public void setup() {
        config = new HashMap<String,Object>(); 
        
        TestCSVConfig o = new TestCSVConfig();
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
        CreateFileForTest(testFullFilePath,"s,1,2");
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        String v = content.get(0).get("value");
        assertTrue("Unexpected field value in first of line of csv file in array", v.equals("s"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("2"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("1"));
    }

    @Test
    public void testToManyEventsForSingleSegment() {
        CreateFileForTest(testFullFilePath,"s;1;2");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        AppendFileForTest(testFullFilePath,"s11;111;211");
        AppendFileForTest(testFullFilePath,"s1;11;21");

        config.put("maxLinesInEvent",3);
        config.put("delimiter",";");

        ArrayList<Map<String,String>> lastSegment = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected number of segments", CSVReader.segmentList.size() == 3);

        assertTrue("Unexpected lines of csv file in array", lastSegment.size() == 2);
        assertTrue("Unexpected values in first of line of csv file in array", lastSegment.get(0).size() == 3);

        String v = lastSegment.get(0).get("value");
        assertTrue("Unexpected field value in first of line of csv file in array", v.equals("s11"));
        assertTrue("Unexpected field flag in first of line of csv file in array", lastSegment.get(0).get("flag").equals("211"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", lastSegment.get(0).get("YScale").equals("111"));

        java.util.LinkedHashMap firstSegment = (java.util.LinkedHashMap )CSVReader.segmentList.get(0) ; 
        ArrayList<Map<String,String>> firstSegmentLines = (ArrayList<Map<String,String>>) firstSegment.get("lines");

        assertTrue("Unexpected lines of csv file in array", firstSegment.size() == 3);
        assertTrue("Unexpected values in first of line of csv file in array", firstSegmentLines.size() == 3);

        assertTrue("Unexpected field value in first of line of csv file in array", firstSegmentLines.get(0).get("value").equals("s"));
        assertTrue("Unexpected field flag in first of line of csv file in array", firstSegmentLines.get(0).get("flag").equals("2"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", firstSegmentLines.get(0).get("YScale").equals("1"));
    }

    @Test
    public void testReadSimpleFile2Record() {
        CreateFileForTest(testFullFilePath,"s;1;2");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        config.put("delimiter",";");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected number of segments", CSVReader.segmentList.size() == 1);
        assertTrue("Unexpected lines of csv file in array", content.size() == 2);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        String v = content.get(0).get("value");
        assertTrue("Unexpected field value in first of line of csv file in array", v.equals("s"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("2"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("1"));
    }

    @Test
    public void testNotEnoughFieldsInSchema() {
        CreateFileForTest(testFullFilePath,"s,1,2,3");
        AppendFileForTest(testFullFilePath,"s1,11,21,31");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 2);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 4);

        String v = content.get(0).get("value");
        assertTrue("Unexpected field value in first of line of csv file in array", v.equals("s"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("2"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("1"));
        assertTrue("Unexpected field field3 in first of line of csv file in array", content.get(0).get("field3").equals("3"));
    }

    @Test
    public void testFredTest() {
        CreateFileForTest(testFullFilePath,"fred;namir;marty");
        AppendFileForTest(testFullFilePath,"s1;11;21");
        
        config.put("delimiter",";");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 2);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("fred"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("namir"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("marty"));
    }

    @Test
    public void testFredTest2() {
        CreateFileForTest(testFullFilePath,"fred12345namir33marty");
        config.put("delimiter","[0-9]");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("fred"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("namir"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("marty"));
    }

    @Test
    public void testnoSkipNullValuesWithCommaDelimiter() {
        CreateFileForTest(testFullFilePath,"s1,,s3");
        config.put("processNullValues","true");

        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 2);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("s1"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("s3"));
    }

    @Test
    public void testSkipNullValuesWithCommaDelimiter() {
        CreateFileForTest(testFullFilePath,"s1,,s3");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 2);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("s1"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("s3"));
    }

    @Test
    public void testSkipNullValues() {
        CreateFileForTest(testFullFilePath,"fred12345namir33marty");
        config.put("delimiter","[0-9]");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("fred"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("namir"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("marty"));
    }

    @Test
    public void testProcessedNullValues() {
        CreateFileForTest(testFullFilePath,"fred12345namir33marty");
        config.put("delimiter","[0-9]");
        config.put("processNullValues","true");
        
        ArrayList<Map<String,String>> content = CSVReader.execute(testFullFilePath, config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("fred"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("field5").equals("namir"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("field7").equals("marty"));
    }
    
// ================================================= Helper functions =================================================
    void CreateFileForTest(String fileName,String content)    {
        try
        {
            FileWriter write = new FileWriter( fileName , false);
            PrintWriter print = new PrintWriter(write) ;
            print.printf("%s\n",content);
            print.close();
        }
        catch(IOException ex) {
            assert ex.getMessage().equals("CreateFileForTest failed");
        }
    }

    void AppendFileForTest(String fileName,String content)    {
        try
        {
            FileWriter write = new FileWriter( fileName , true);
            PrintWriter print = new PrintWriter(write) ;
            print.printf("%s\n",content);
            print.close();
        }
        catch(IOException ex) {
            assert ex.getMessage().equals("CreateFileForTest failed");
        }
    }
}
