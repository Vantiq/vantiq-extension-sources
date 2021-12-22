
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

import static org.junit.Assume.assumeTrue;

public class ObjRecTestBase {
    public static final String UNUSED = "unused";
    
    public static final String JPEG_IMAGE_LOCATION = System.getProperty("buildDir") + "/testResources/sampleImage.jpg";
    public static final String PNG_IMAGE_LOCATION = System.getProperty("buildDir") + "/testResources/sampleImage.png";
    public static final String VIDEO_LOCATION = System.getProperty("buildDir") + "/testResources/sampleVideo-1.0.mov";
    // Used to test suppressNullValues. Finding online cameras with nothing on them is hard, so we'll
    // simply include a video of a white wall.  This should be good enough to find nothing.
    public static final String NOTHING_VIDEO_LOCATION =
            System.getProperty("buildDir") + "/testResources/nothingVideo-1.0.mov";
    public static String testAuthToken = null;
    public static String testVantiqServer = null;
    public static String testSourceName = null;
    public static String testTypeName = null;
    public static String testRuleName = null;

    // Few of these are truly "public services" on purpose, so they periodically drop
    // offline & things need to be refreshed here.  We centralize these definitions to simplify things when we need to
    // find & change them.

    // Camera with some recognizable objects in it.
    // Use camera "close to home" -- CalTrans camera close to the office...
//    public static final String IP_CAMERA_URL = "https://wzmedia.dot.ca.gov/D4/S680_at_N_Main_St.stream/playlist.m3u8";
    // Walnut Creek/North Main camera (above) currently malfunctioning.  Swapping to Hwy 242 Junction for now.
    // Keeping old around to swap back sometime.
    public static final String IP_CAMERA_URL = "https://wzmedia.dot.ca.gov/D4/N680_JSO_JCT_242.stream/playlist.m3u8";

    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
        testTypeName = System.getProperty("EntConTestTypeName", "testTypeName");
        testRuleName = System.getProperty("EntConTestRuleName", "testRuleName");
        assumeTrue("Tests require system property 'buildDir' to be set -- should be objectRecognitionSource/build",
                System.getProperty("buildDir") != null);
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
}
