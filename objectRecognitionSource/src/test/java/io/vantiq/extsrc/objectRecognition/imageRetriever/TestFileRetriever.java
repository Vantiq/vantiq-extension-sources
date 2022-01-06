
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class TestFileRetriever extends ObjRecTestBase {
    FileRetriever fr;
    ObjectRecognitionCore source;
    
    @BeforeClass
    public static void checkFilesExist() {
        assumeTrue("No video file for test. Should be at " +  new File(VIDEO_LOCATION).getAbsolutePath() + "."
                , new File(VIDEO_LOCATION).exists());
    }
    
    @Before
    public void setup() {
        fr = new FileRetriever();
        source = new ObjectRecognitionCore("src", UNUSED, UNUSED, UNUSED);
    }
    
    @After
    public void tearDown() {
        source.close();
        fr = null;
        source = null;
    }
    
    @Test
    public void testImageReadBasic() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", JPEG_IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            ImageRetrieverResults results;
            results = fr.getImage();
            byte[] data = results.getImage();
            assert data != null;
            assert data.length >= 64000 && data.length < 66000;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image " + e.toString());
        }
    }
    
    @Test
    public void testImagePngConversion() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", PNG_IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            ImageRetrieverResults results;
            results = fr.getImage();
            byte[] data = results.getImage();
            assert data != null;
            assert data.length >= 120000 && data.length <= 130000;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image " + e.toString());
        }
    }
    
    @Test
    public void testImageReadBasicInvalidLocation() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", "invalidLocation");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            fr.getImage();
            fail("Expected exception when calling with invalid default");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by unreadable default image. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FileRetriever.class.getCanonicalName() + ".defaultImageDoesNotExist"));
        }
    }
    
    @Test
    public void testImageReadBasicInitiallyInvalidLocation() {
        final String location2 = "src/test/resources/image2";
        try {
            
            try {
                Map<String, String> config = new LinkedHashMap<>();
                config.put("fileLocation", location2);
                fr.setupDataRetrieval(config, source);
            } catch (Exception e) {
                fail("Exception occurred when setting up: " + e.toString());
            }
            try {
                fr.getImage();
                fail("Expected exception when calling with invalid default");
            } catch (ImageAcquisitionException e) {
                assertTrue("Failure should be caused by unreadable default image. Error actually was: " + e.getMessage()
                    , e.getMessage().startsWith(FileRetriever.class.getCanonicalName() + ".defaultImageDoesNotExist"));
            }
            
            copyFile(JPEG_IMAGE_LOCATION, location2);
            
            try {
                ImageRetrieverResults results;
                results = fr.getImage();
                byte[] data = results.getImage();
                assert data != null;
                assert data.length >= 64000 && data.length < 66000;
            } catch (ImageAcquisitionException e) {
                fail("Exception occurred when obtaining image after creating it " + e.toString());
            }
        } finally {
            deleteFile(location2);
        }
    }
    
    @Test
    public void testImageReadQuery() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", "invalidLocation");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            Map<String, String> message = new LinkedHashMap<>();
            message.put("DSfileLocation", JPEG_IMAGE_LOCATION);
            ImageRetrieverResults results;
            results = fr.getImage(message);
            byte[] data = results.getImage();
            assert data != null;
            assert data.length >= 64000 && data.length < 66000;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image " + e.toString());
        }
    }
    
    @Test
    public void testImageReadQueryWithInvalidLocation() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", JPEG_IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            Map<String, String> message = new LinkedHashMap<>();
            message.put("DSfileLocation", "invalidLocation");
            fr.getImage(message);
            fail("Expected exception when calling with invalid default");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by unreadable image. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FileRetriever.class.getCanonicalName() + ".queryImageDoesNotExist"));
        }
    }
    
    @Test
    public void testImageReadEmptyQuery() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", JPEG_IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            Map<String, String> message = new LinkedHashMap<>();
            ImageRetrieverResults results;
            results = fr.getImage(message);
            byte[] data = results.getImage();
            assert data != null;
            assert data.length >= 64000 && data.length < 66000;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image: " + e.toString());
        }
    }
    
    @Test
    public void testVideoBasicRead() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", VIDEO_LOCATION);
            config.put("fileExtension", "mov");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up " + e.toString());
        }
        try {
            ImageRetrieverResults results;
            Map od;
            results = fr.getImage();
            int frameCount = (Integer) results.getOtherData().get("videoFrameCount");
            byte[] data = results.getImage();
            od = results.getOtherData();
            assert od.get("releaseException") == null;
            assert data != null;
            assert data.length >= 640000 && data.length <= 660000;

            // Now, ensure that we can read the next image
            results = fr.getImage();
            od = results.getOtherData();
            assert od.get("releaseException") == null;
            byte[] data2 = results.getImage();
            assert data2 != null;
            assert data2.length >= 640000 && data2.length <= 660000;
            assertNotEquals(data, data2);

            // Now, repeat until done, making sure that each image is different from the previous one

            int loopLimit = Math.min(50, frameCount);
            int loopCount = 0;
            while (loopCount < loopLimit) {
                loopCount += 1;
                byte[] oldData = data2;
                results = fr.getImage();
                od = results.getOtherData();
                assert od.get("releaseException") == null;
                data2 = results.getImage();
                int nextFrameNumber = (Integer) od.get("frame");
                assert data2 != null;
                assert nextFrameNumber < frameCount;
                // otherData["frame"] is, in fact, the next frame number.
                // So we'll expect it to be the two we started with (0 & 1 + another) == +2
                assertEquals(loopCount + 2, nextFrameNumber);
                assertNotEquals(oldData, data2);
            }
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image: " + e);
        }
    }
    
    @Test
    public void testVideoInvalidLocation() {
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("fileLocation", "invalidLocation");
            config.put("fileExtension", "mov");
            fr.setupDataRetrieval(config, source);
            fail("Expected setup exception on invalid video");
        } catch (Exception e) {
            assertTrue("Failure should be caused by nonexistent video. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FileRetriever.class.getCanonicalName() + ".mainVideoDoesNotExist"));
        }
    }
    
    @Test
    public void testVideoQuery() {
        Map<String, Object> request = new LinkedHashMap<>();
        try {
            Map<String, String> config = new LinkedHashMap<>();
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when obtaining image: " + e.toString());
        }
        try {
            request.put("DSfileExtension", "mov");
            request.put("DSfileLocation", VIDEO_LOCATION);
            request.put("DStargetFrame", (double) 3);
            ImageRetrieverResults results;
            results = fr.getImage(request);
            byte[] data = results.getImage();
            assert data != null;
            assert data.length >= 620000 && data.length <= 660000;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when requesting frame 4 of video: " + e.toString());
        }
        try {
            request.put("DStargetFrame", (double) 1000000000000000.0);
            fr.getImage(request);
            fail("Did not reject frame past end of video");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by invalid target frame. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FileRetriever.class.getCanonicalName() + ".invalidTargetFrame"));
        }
        try {
            request.put("DStargetFrame", (double) -1);
            fr.getImage(request);
            fail("Did not reject negative frame");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by invalid target frame. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FileRetriever.class.getCanonicalName() + ".invalidTargetFrame"));
        }
    }
    
// ================================================= Helper functions =================================================
    
    public void copyFile(String initialLocation, String copyLocation) {
        File f1 = new File(initialLocation);
        File f2 = new File(copyLocation);
        
        try {
            byte[] fileData = Files.readAllBytes(f1.toPath());
            Files.write(f2.toPath(), fileData);
        } catch (Exception e) {
            throw new RuntimeException("Error copying data", e);
        }
    }
}
