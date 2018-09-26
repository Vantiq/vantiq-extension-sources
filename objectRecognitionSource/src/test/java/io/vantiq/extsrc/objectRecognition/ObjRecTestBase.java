
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

public class ObjRecTestBase {
    public static final String UNUSED = "unused";
    
    public static final String JPEG_IMAGE_LOCATION = "src/test/resources/sampleImage.jpg";
    public static final String PNG_IMAGE_LOCATION = "src/test/resources/sampleImage.png";
    public static final String VIDEO_LOCATION = "src/test/resources/sampleVideo.mov";
    public static String testAuthToken = null;
    public static String testVantiqServer = null;

    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        System.out.println("SETUP: Got VS: " + testVantiqServer + ", Token: " + testVantiqServer);
    }
    
    public static void deleteFile(String fileName) {
        File f = new File(fileName);
        
        try {
            Files.deleteIfExists(f.toPath());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting file " + f.getAbsolutePath(), e);
        }
    }
    
    public static void deleteDirectory(String directoryName) {
        File d = new File(directoryName);
        
        try {
            File[] subFiles = d.listFiles();
            if (subFiles != null) {
                for (File f : subFiles) {
                    Files.delete(f.toPath());
                }
            }
            Files.deleteIfExists(d.toPath());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting directory " + d.getAbsolutePath(), e);
        }
    }
    
    public static boolean checkUrl(String url) {
        try {
            URL urlProtocolTest = new URL((String) url);
            InputStream urlReadTest = urlProtocolTest.openStream();
            urlReadTest.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
