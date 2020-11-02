/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;

public class TestHikVisionConfig extends TestHikVisionBase {

    HikVisionHandleConfiguration handler;
    
    NoSendHikVisionCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    
    Map<String, String> general;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        nCore = new NoSendHikVisionCore(sourceName, authToken, targetVantiqServer);
        handler = new HikVisionHandleConfiguration(nCore);
    }
    
    @After
    public void tearDown() {
        nCore.stop();
    }
    
    @Test
    public void testEmptyConfig() {
        Map conf = new LinkedHashMap<>();
        Map vantiqConf = new LinkedHashMap<>();
        ArrayList list = new ArrayList();
        sendConfig(conf, vantiqConf,list);
        assertTrue("Should fail on empty configuration", configIsFailed());
    }
    
    
    @Test
    public void testMissingGeneral() {
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        ArrayList<Object> cameras = minimalCameraList();

        conf.remove("general");
        conf.clear();
        sendConfig(conf, vantiqConf,cameras);
        assertTrue("Should not fail when missing 'general' configuration", !configIsFailed());
    }
    
    
    @Test 
    public void testMissingOptions() {
        Map conf = minimalConfig();
        Map vantiqConf = new LinkedHashMap<>();
        ArrayList<Object> cameras = minimalCameraList();
        sendConfig(conf, vantiqConf,cameras);
        assertTrue("Should fail when missing 'options' configuration", configIsFailed());
    }
    

    @Test
    public void testMinimalConfig() {
        nCore.start(5); // Need a client to avoid NPEs on sends
        
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        ArrayList<Object> cameras = minimalCameraList();

        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }
    @Test
    public void testAsynchronousProcessingNoCameras() {
        nCore.start(5);
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        ArrayList<Object> cameras = minimalCameraList();
        cameras.clear();

        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should fail when No cameras is set correctly", !configIsFailed());
    }
    @Test
    public void testAsynchronousProcessingNoCamerasInEnable() {
        nCore.start(5);
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        ArrayList<Object> cameras = minimalCameraList();
        ((Map)cameras.get(0)).put("Enable","false");

        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should fail when No cameras is set correctly", !configIsFailed());
    }
    @Test
    public void testAsynchronousProcessingIllegalValidValues() {
        nCore.start(5);
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        vantiqConf.put("asynchronousProcessing", "jibberish");
        ArrayList<Object> cameras = minimalCameraList();


        vantiqConf.put("asynchronousProcessing", true);
        // Setting maxRunningThreads and maxQueuedTasks incorrectly
        vantiqConf.put("maxActiveTasks", "jibberish");
        vantiqConf.put("maxQueuedTasks", "moreJibberish");
        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should fail when maxActiveTasks and maxQueuedTasks are set correctly", configIsFailed());
    }
    @Test
    public void testAsynchronousProcessingValidValues() {
        nCore.start(5);
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        vantiqConf.put("asynchronousProcessing", "jibberish");
        ArrayList<Object> cameras = minimalCameraList();


        vantiqConf.put("asynchronousProcessing", true);
        vantiqConf.put("maxActiveTasks", 10);
        vantiqConf.put("maxQueuedTasks", 20);
        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail when maxActiveTasks and maxQueuedTasks are set correctly", configIsFailed());
    }
    
    @Test
    public void testAsynchronousProcessing() {
        nCore.start(5);

        // Setting asynchronousProcessing incorrectly
        
        Map conf = minimalConfig();
        Map vantiqConf = createMinimalVantiq();
        vantiqConf.put("asynchronousProcessing", "jibberish");
        ArrayList<Object> cameras = minimalCameraList();

        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail with invalid asynchronousProcessing value", configIsFailed());

        // Setting asynchronousProcessing to false (same as not including it)
        vantiqConf.put("asynchronousProcessing", false);
        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail with asynchronousProcessing set to false", configIsFailed());

        // Setting asynchronousProcessing to true
        vantiqConf.put("asynchronousProcessing", true);
        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail with asynchronousProcessing set to true", configIsFailed());

        // Setting maxRunningThreads and maxQueuedTasks incorrectly
        /*
        vantiqConf.put("maxActiveTasks", "jibberish");
        vantiqConf.put("maxQueuedTasks", "moreJibberish");
        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail when maxActiveTasks and maxQueuedTasks are set incorrectly", configIsFailed());

        // Setting maxRunningThreads and maxQueuedTasks correctly
        vantiqConf.put("asynchronousProcessing", true);
        vantiqConf.put("maxActiveTasks", 10);
        vantiqConf.put("maxQueuedTasks", 20);
        sendConfig(conf, vantiqConf,cameras);
        assertFalse("Should not fail when maxActiveTasks and maxQueuedTasks are set correctly", configIsFailed());
        */
    }
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String, String> generalConfig, Map<String, ?> vantiqConfig, ArrayList cameras) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("general", generalConfig);
        config.put("cameras", cameras);
        config.put("options", vantiqConfig);
        obj.put("config", config);
        m.object = obj;
        
        handler.handleMessage(m);
    }
    public ArrayList<Object> minimalCameraList() {
        
        ArrayList<Object> list = new ArrayList<Object>();
        Map<String, Object> camera = new LinkedHashMap<>();
        camera.put("CameraId","TestId"); 
        camera.put("Enable","true"); 
        camera.put("DVRIP","127.0.0.1"); 
        camera.put("DVRPort","8000"); 
        camera.put("DVRUserName","admin"); 
        camera.put("DVRPassword","password"); 

        list.add(camera);

        return list;

    }
    
    public Map<String, String> minimalConfig() {
        createMinimalGeneral();
       // Map<String, Object> ret = new LinkedHashMap<>();
       // ret.put("general", general);
        
        return general;
    }
    
    public void createMinimalGeneral() {
        general = new LinkedHashMap<>();
        general.put("sdkLogPath", testSdkLogPath);
        general.put("DVRImageFolderPath", testDVRImageFolderPath);
        general.put("VantiqDocumentPath", testVantiqDocumentPath);
        general.put("VantiqResourcePath", testVantiqResourcePath);


    }
    
    public Map<String, Object> createMinimalVantiq() {
        Map<String, Object> vantiq = new LinkedHashMap<>();
        vantiq.put("maxActiveTasks", 3);
        vantiq.put("maxQueuedTasks", 20);
        vantiq.put("IsInTest","true");
        return vantiq;
    }
    
    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
