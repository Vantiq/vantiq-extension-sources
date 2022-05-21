/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.vantiq.client.Vantiq;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.FTPClientSource.exception.VantiqFTPClientException;

public class TestFTPClient extends TestFTPClientBase {

    static final int CORE_START_TIMEOUT = 10;
    static FTPClientCore core;
    static FTPClient FTPClient;
    static Vantiq vantiq;
    static Map<String, Object> config;
    static Map<String, Object> options;

    @Before
    public void setup() {
        config = new HashMap<String, Object>();

        TestFTPClientConfig o = new TestFTPClientConfig();
        config = o.minimalConfig();
        options = o.createMinimalOptions();
/*
        ExtensionServiceMessage m = new ExtensionServiceMessage("");

        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ftpClientConfig", config);
        config.put("options", options);
        obj.put("config", config);
        m.object = obj;
*/
        FTPClient = new FTPClient();


        try{
        FTPClient.setupFTPClient((ExtensionWebSocketClient)null,config,options);
        } catch(VantiqFTPClientException exv){

        }



        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }

    @Test
    public void testCorrectErrors() throws VantiqFTPClientException {
//        assumeTrue(testFileFolderPath != null && testFullFilePath != null && IsTestFileFolderExists());
        FTPClient.setupFTPClient(null,  config, options);
    }


    @Test
    public void testCreanUploadAndDownloadFile() {

        createBasicMessage();
        ExtensionServiceMessage message = createBasicMessage();
        try {
            File f = new File("c:\\tmp\\t.txt");
            f.createNewFile();
            
            HashMap[] response;


            response = FTPClient.processClean(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals(FTPClient.FTPClient_SUCCESS_CODE));

            response = FTPClient.processUpload(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals(FTPClient.FTPClient_SUCCESS_CODE));

            response = FTPClient.processDownload(message);
            assertTrue("Action wasn't successfull", response[0].get("code").equals(FTPClient.FTPClient_SUCCESS_CODE));
        } catch (Exception ex) {
            assertFalse("Exception", true);
        }
    }

    public ExtensionServiceMessage createBasicMessage() {

        ExtensionServiceMessage message = new ExtensionServiceMessage("127.0.0.1");

        List<Object> content = new ArrayList();

        Map<String, Object> body = new HashMap<String, Object>();
        String tmp = System.getProperty("java.io.tmpdir");
        body.put("local", "c:\\tmp");
        body.put("remote", "t.txt");
        body.put("ageInDays", 10);

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
