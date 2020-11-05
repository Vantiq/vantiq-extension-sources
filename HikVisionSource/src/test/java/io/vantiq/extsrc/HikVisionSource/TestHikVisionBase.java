/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.BeforeClass;

public class TestHikVisionBase {
    static String testVantiqServer;
    static String testAuthToken;
    static String testSourceName;
    static String testDVRImageFolderPath;
    static String testVantiqDocumentPath;
    static String testVantiqResourcePath;
    static String testSdkLogPath;
    static String testIPAddress;
    static int testIPPort;
    static int testSize;

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
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");

        testSdkLogPath = System.getProperty("TestSdkLogPath", "c:/tmp");
        testDVRImageFolderPath = System.getProperty("TestDVRImageFolderPath", "c:/tmp");
        testVantiqDocumentPath = System.getProperty("TestVantiqDocumentPath", "public/image");
        testVantiqResourcePath = System.getProperty("TestVantiqResourcePath", "/resources/documents");
    }

    Map<String, String> general;

    public Map<String, String> minimalConfig() {
        createMinimalGeneral();
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
        vantiq.put("IsInTest", "true");
        return vantiq;
    }

    public ArrayList<Object> minimalCameraList() {

        ArrayList<Object> list = new ArrayList<Object>();
        Map<String, Object> camera = new LinkedHashMap<>();
        camera.put("CameraId", "TestId");
        camera.put("Enable", "true");
        camera.put("DVRIP", "127.0.0.1");
        camera.put("DVRPort", "8000");
        camera.put("DVRUserName", "admin");
        camera.put("DVRPassword", "password");

        list.add(camera);

        return list;
    }

    public Map<String, Object> createConfigurationSection() {
        Map generalConfig = minimalConfig();
        Map vantiqConfig = createMinimalVantiq();
        ArrayList<Object> cameras = minimalCameraList();

        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("general", generalConfig);
        config.put("cameras", cameras);
        config.put("options", vantiqConfig);
        obj.put("config", config);

        return obj;
    }
}