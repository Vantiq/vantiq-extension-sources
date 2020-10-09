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

        CreateFileForTest("a.csv","s;1;2");
        ArrayList<Map<String,String>> content = CSVReader.execute("a.csv", config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 1);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        String v = content.get(0).get("value");
        assertTrue("Unexpected field value in first of line of csv file in array", v.equals("s"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("2"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("1"));


    }
    @Test
    public void testToManyEventsForSingleSegment() {

        CreateFileForTest("a.csv","s;1;2");
        AppendFileForTest("a.csv","s1;11;21");
        AppendFileForTest("a.csv","s1;11;21");
        AppendFileForTest("a.csv","s1;11;21");
        AppendFileForTest("a.csv","s1;11;21");
        AppendFileForTest("a.csv","s1;11;21");
        AppendFileForTest("a.csv","s1;11;21");
        AppendFileForTest("a.csv","s1;11;21");

        config.put("maxLinesInEvent",3);
        config.put("delimiter",";");

        ArrayList<Map<String,String>> content = CSVReader.execute("a.csv", config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 3);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

    }

    @Test
    public void testReadSimpleFile2Record() {

        CreateFileForTest("a.csv","s;1;2");
        AppendFileForTest("a.csv","s1;11;21");
        config.put("delimiter",";");
        
        ArrayList<Map<String,String>> content = CSVReader.execute("a.csv", config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 2);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        String v = content.get(0).get("value");
        assertTrue("Unexpected field value in first of line of csv file in array", v.equals("s"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("2"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("1"));


    }

    @Test
    public void testNotEnoughFieldsInSchema() {

        CreateFileForTest("a.csv","s,1,2,3");
        AppendFileForTest("a.csv","s1,11,21,31");
        
        ArrayList<Map<String,String>> content = CSVReader.execute("a.csv", config, null);
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

        CreateFileForTest("a.csv","fred;namir;marty");
        AppendFileForTest("a.csv","s1;11;21");
        config.put("delimiter",";");
        
        ArrayList<Map<String,String>> content = CSVReader.execute("a.csv", config, null);
        assertTrue("Unexpected lines of csv file in array", content.size() == 2);
        assertTrue("Unexpected values in first of line of csv file in array", content.get(0).size() == 3);

        assertTrue("Unexpected field value in first of line of csv file in array", content.get(0).get("value").equals("fred"));
        assertTrue("Unexpected field YScale in first of line of csv file in array", content.get(0).get("YScale").equals("namir"));
        assertTrue("Unexpected field flag in first of line of csv file in array", content.get(0).get("flag").equals("marty"));


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
