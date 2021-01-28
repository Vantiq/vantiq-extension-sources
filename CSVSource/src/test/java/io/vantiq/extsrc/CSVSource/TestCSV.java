/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.Before;
import org.junit.Test;

import io.vantiq.client.Vantiq;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extsrc.CSVSource.exception.VantiqCSVException;

public class TestCSV extends TestCSVBase {

    static final int CORE_START_TIMEOUT = 10;
    static CSVCore core;
    static CSV csv;
    static Vantiq vantiq;
    static Map<String, Object> config;
    static Map<String, Object> options;

    @Before
    public void setup() {
        config = new HashMap<String, Object>();

        TestCSVConfig o = new TestCSVConfig();
        config = o.minimalConfig();
        options = o.createMinimalOptions();

        csv = new CSV();

        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }

    @Test
    public void testCorrectErrors() throws VantiqCSVException {
        assumeTrue(testFileFolderPath != null && testFullFilePath != null && IsTestFileFolderExists());
        csv.setupCSV(null, testFileFolderPath, testFullFilePath, config, options);
    }

    @Test
    public void testCreateAppendAndDeleteFile() {

        createBasicMessage();
        ExtensionServiceMessage message = createBasicMessage();
        try {
            File f = new File("c:\\tmp\\t.txt");
            f.delete(); // ensore the file not exists 

            HashMap[] response = csv.processCreate(message);
            assertTrue("Action wasn't successful", response[0].get("code").equals("io.vantiq.extsrc.csvsource.success"));

            response = csv.processAppend(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals("io.vantiq.extsrc.csvsource.success"));

            response = csv.processDelete(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals("io.vantiq.extsrc.csvsource.success"));

        } catch (VantiqCSVException ex) {
            assertFalse("Exception",true);
        }

    }
    @Test
    public void testAppendFileNotExistFile() {

        createBasicMessage();
        ExtensionServiceMessage message = createBasicMessage();
        try {
            File f = new File("c:\\tmp\\t.txt");
            f.delete(); // ensore the file not exists 

            HashMap[] response = csv.processAppend(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals("io.vantiq.extsrc.csvsource.nofile"));

            response = csv.processDelete(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals("io.vantiq.extsrc.csvsource.nofile"));

            response = csv.processCreate(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals("io.vantiq.extsrc.csvsource.success"));

            response = csv.processCreate(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals("io.vantiq.extsrc.csvsource.fileexists"));


        } catch (Exception ex) {
            assertFalse("Exception",true);
        }

    }

    public ExtensionServiceMessage createBasicMessage(){

        ExtensionServiceMessage message = new ExtensionServiceMessage("127.0.0.1");
        Map<String, Object> content1 = new HashMap<String, Object>();
        content1.put("text", "test line 1");

        List<Object> content = new ArrayList();
        content.add(content1);

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("path", "c:\\tmp");
        body.put("file", "t.txt");
        body.put("content", content);

        Map<String, Object>[] request = new HashMap[1];
        request[0] = new HashMap<String, Object>() {
            {
                put("body", body);
            }
        };

        message.object = request[0];

        return message; 

    }
}
