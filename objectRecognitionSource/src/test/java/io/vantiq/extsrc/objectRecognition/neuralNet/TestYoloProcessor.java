
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import okhttp3.Response;
import okio.BufferedSource;

@SuppressWarnings("PMD.ExcessiveClassLength")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestYoloProcessor extends NeuralNetTestBase {

    static final String COCO_MODEL_VERSION = "1.2";
    static final String LABEL_FILE = "coco-" + COCO_MODEL_VERSION + ".names";
    static final String PB_FILE = "coco-" + COCO_MODEL_VERSION + ".pb";
    static final String META_FILE = "coco-" + COCO_MODEL_VERSION + ".meta";
    static final String PB_FILE_608 = "coco-1.1.pb";
    static final String META_FILE_608 = "coco-1.1.meta";
    static final String OUTPUT_DIR = System.getProperty("buildDir") + "/resources/out";
    static final int SAVE_RATE = 2; // Saves every other so that we can know it counts correctly

    // For image resizing tests
    static final int TEST_IMAGE_WIDTH = 500;
    static final int TEST_IMAGE_HEIGHT = 375;
    static final int RESIZED_IMAGE_WIDTH = 100;
    static final int RESIZED_IMAGE_HEIGHT = 75;
    
    // For pre cropping tests
    static final int PRECROP_TOP_LEFT_X_COORDINATE = 50;
    static final int PRECROP_TOP_LEFT_Y_COORDINATE = 50;
    static final int CROPPED_WIDTH = 200;
    static final int CROPPED_HEIGHT = 150;
    
    // For pre cropping results test
    static final int PRECROP_KEYBOARD_TOP_LEFT_X_COORDINATE = 120;
    static final int PRECROP_KEYBOARD_TOP_LEFT_Y_COORDINATE = 250;
    static final int KEYBOARD_CROPPED_WIDTH = 230;
    static final int KEYBOARD_CROPPED_HEIGHT = 125;

    static final int CORE_START_TIMEOUT = 10;

    static ObjectRecognitionCore core;
    static YoloProcessor ypJson;
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;

    static List<String> vantiqSavedFiles = new ArrayList<>();
    static List<String> vantiqSavedImageFiles = new ArrayList<>();

    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}\\.jpg";

    // A single processor is used for the entire class because it is very expensive to do initial setup
    @BeforeClass
    public static void classSetup() {
        ypJson = new YoloProcessor();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        try {
            ypJson.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }

        if (testVantiqServer != null && testAuthToken != null) {
            vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
            vantiq.setAccessToken(testAuthToken);
        }
    }

    @AfterClass
    public static void deleteFromVantiq() throws InterruptedException {
        // Deleting files saved as documents
        if (vantiq != null && vantiq.isAuthenticated()) {
            for (String vantiqSavedFile : vantiqSavedFiles) {
                Thread.sleep(1000);
                vantiq.deleteOne(VANTIQ_DOCUMENTS, vantiqSavedFile, new BaseResponseHandler() {

                    @Override
                    public void onSuccess(Object body, Response response) {
                        super.onSuccess(body, response);
                    }

                    @Override
                    public void onError(List<VantiqError> errors, Response response) {
                        super.onError(errors, response);
                    }
                });
            }
            // Deleting files saved as images
            for (String vantiqSavedImageFile : vantiqSavedImageFiles) {
                Thread.sleep(1000);
                vantiq.deleteOne(VANTIQ_IMAGES, vantiqSavedImageFile, new BaseResponseHandler() {

                    @Override
                    public void onSuccess(Object body, Response response) {
                        super.onSuccess(body, response);
                    }

                    @Override
                    public void onError(List<VantiqError> errors, Response response) {
                        super.onError(errors, response);
                    }
                });
            }
        }
    }

    @SuppressWarnings("PMD.JUnit4TestShouldUseAfterAnnotation")
    @AfterClass
    public static void classTearDown() {
        if (ypJson != null) {
            ypJson.close();
            ypJson = null;
        }

        File d = new File(OUTPUT_DIR);
        if (d.exists()) {
            deleteDirectory(OUTPUT_DIR);
        }

        if (vantiq != null && vantiq.isAuthenticated()) {
            // Double check that everything was deleted from VANTIQ
            deleteSource(vantiq);
            deleteType(vantiq);
            deleteRule(vantiq);
        }
    }

    @Test
    public void testInvalidConfig() {
        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        // Nothing included
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            fail("Should fail without anything in config.");
        } catch (Exception e) {
            assertTrue("Failure should be caused by empty config. Error actually was: " + e.getMessage(),
                    e.getMessage().startsWith(YoloProcessor.class.getCanonicalName() + ".missingConfig:"));
        }

        // No meta file or label file
        config.put("pbFile", PB_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            fail("Should fail without either a label file or a meta file.");
        } catch (Exception e) {
            assertTrue("Failure should be caused by lack of either label or meta file. Error actually was: "
                    + e.getMessage(), e.getMessage().startsWith(YoloProcessor.class.getCanonicalName() + ".missingConfig:"));
        }

        // No pb file included but label file included
        config.remove("pbFile");
        config.put("labelFile", LABEL_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            fail("Should fail when label file is included but pbFile is not.");
        } catch (Exception e) {
            assertTrue("Failure should be caused by lack of pbFile even when label file is included. Error actually was: "
                    + e.getMessage(), e.getMessage().startsWith(YoloProcessor.class.getCanonicalName() + ".missingConfig:"));
        }

        // No pb file included but label file included
        config.remove("labelFile");
        config.put("metaFile", META_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            fail("Should fail when meta file is included but pbFile is not.");
        } catch (Exception e) {
            assertTrue("Failure should be caused by lack of pbFile even when meta file is included. Error actually was: "
                    + e.getMessage(), e.getMessage().startsWith(YoloProcessor.class.getCanonicalName() + ".missingConfig:"));
        }
    }

    @Test
    public void testValidConfig() {
        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        List<Number> anchorList = Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);

        // Meta file included, no label file
        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with meta file but no label file: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // Meta file included and anchors included
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with meta file and anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // Label file included, no meta file
        config.remove("metaFile");
        config.remove("anchors");
        config.put("labelFile", LABEL_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with label file but no meta file: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // Label file included and anchors included
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with label file and anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // Label and meta file included, anchors included
        config.put("metaFile", META_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with label and meta file, and anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }
    }
    
    @Test
    public void testMetaConfig() {
        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        YoloProcessor ypImageSaver2 = new YoloProcessor();
        
        config.put("pbFile", PB_FILE_608);
        config.put("metaFile", META_FILE_608);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail setup: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        // useMetaIfAvailable flag should be true, since Config Size value is unchanged (default is 416)
        assert ypImageSaver.objectDetector.metaConfigOptions.useMetaIfAvailable == true;
        
        // Frame Size should be 608 since this is what is included in the meta file
        assert ypImageSaver.objectDetector.metaConfigOptions.frameSize == 608;
        
        config.remove("metaFile");
        config.put("labelFile", LABEL_FILE);
        try {
            ypImageSaver2.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail setup: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        // useMetaIfAvailable flag should still be true regardless of meta file presence
        // Since there is no meta file, default value will be used
        assert ypImageSaver2.objectDetector.metaConfigOptions.useMetaIfAvailable == true;
        
        // Frame Size should be 416 since this is what is the default
        assert ypImageSaver2.objectDetector.metaConfigOptions.frameSize == 416;
    }

    @Test
    public void testResults() throws ImageProcessingException {
        String expectedResults = imageResultsAsStringWithoutServer;
        if (testAuthToken != null && testVantiqServer != null) {
            expectedResults = imageResultsAsString;
        }
        verifyProcessing(ypJson, expectedResults);
    }

    @Test
    public void testRealJSONConfig() throws ImageProcessingException, JsonParseException, JsonMappingException, IOException {

        String expectedResults = imageResultsAsStringWithoutServer;
        if (testAuthToken != null && testVantiqServer != null) {
            expectedResults = imageResultsAsString;
        }
        YoloProcessor ypImageSaver = new YoloProcessor();
        ExtensionServiceMessage msg = createRealConfig(neuralNetJSON1);
        Map config = (Map) msg.getObject();
        Map neuralNetConfig = (Map) config.get("neuralNet");

        // Config with meta file, label file, pb file, and anchors
        try {
            ypImageSaver.setupImageProcessing(neuralNetConfig, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, expectedResults);
        } catch (Exception e) {
            fail("Should not fail with valid config: " + e.getClass().getName() + "::" + e.getMessage());
        } finally {
            if (ypImageSaver != null) {
                ypImageSaver.close();
            }
        }

        // Config with meta file and pb file
        msg = createRealConfig(neuralNetJSON2);
        config = (Map) msg.getObject();
        neuralNetConfig = (Map) config.get("neuralNet");

        try {
            ypImageSaver.setupImageProcessing(neuralNetConfig, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, expectedResults);
        } catch (Exception e) {
            fail("Should not fail with valid config: " + e.getClass().getName() + "::" + e.getMessage());
        } finally {
            if (ypImageSaver != null) {
                ypImageSaver.close();
            }
        }

        // Config with label file, pb file, and anchors
        msg = createRealConfig(neuralNetJSON3);
        config = (Map) msg.getObject();
        neuralNetConfig = (Map) config.get("neuralNet");

        try {
            ypImageSaver.setupImageProcessing(neuralNetConfig, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, expectedResults);
        } catch (Exception e) {
            fail("Should not fail with valid config: " + e.getClass().getName() + "::" + e.getMessage());
        } finally {
            if (ypImageSaver != null) {
                ypImageSaver.close();
            }
        }

        // Config with label file and pb file
        msg = createRealConfig(neuralNetJSON4);
        config = (Map) msg.getObject();
        neuralNetConfig = (Map) config.get("neuralNet");

        try {
            ypImageSaver.setupImageProcessing(neuralNetConfig, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, expectedResults);
        } catch (Exception e) {
            fail("Should not fail with valid config: " + e.getClass().getName() + "::" + e.getMessage());
        } finally {
            if (ypImageSaver != null) {
                ypImageSaver.close();
            }
        }
    }
    
    @Test
    public void testDifferentFrameSizeProcessing () throws JsonParseException, JsonMappingException, IOException {
        YoloProcessor ypImageSaver = new YoloProcessor();
        ExtensionServiceMessage msg = createRealConfig(neuralNetJSON5);
        Map config = (Map) msg.getObject();
        Map neuralNetConfig = (Map) config.get("neuralNet");
      
        // Config with meta file and pb file for 608 frame size
        config = (Map) msg.getObject();
        neuralNetConfig = (Map) config.get("neuralNet");

        try {
            ypImageSaver.setupImageProcessing(neuralNetConfig, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, imageResultsAsString608);
        } catch (Exception e) {
            fail("Should not fail with valid config: " + e.getClass().getName() + "::" + e.getMessage());
        } finally {
            if (ypImageSaver != null) {
                ypImageSaver.close();
            }
        }
    }

    @Test
    public void testValidAnchors() throws ImageProcessingException {
        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        List<Number> anchorList = Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        config.put("pbFile", PB_FILE);
        config.put("labelFile", LABEL_FILE);
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with valid anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // Checking mix of integers and floating points
        anchorList = Arrays.asList(1, 1.0, 0.01, 1, 1.0, 0.01, 1, 1.0, 0.01, 1);
        config.remove("anchors");
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }
    }

    @Test
    public void testInvalidAnchors() throws ImageProcessingException {
        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        // anchorList is too short
        List anchorList = Arrays.asList(0.5);
        config.put("pbFile", PB_FILE);
        config.put("labelFile", LABEL_FILE);
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // anchorList is too long
        anchorList = Arrays.asList(1, 3, 6, 4, 4, 5, 7, 4, 9, 0, 9);
        config.remove("anchors");
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }

        // anchorList contains non-numbers
        anchorList = Arrays.asList(1, 3, 6, "a", 4, 5, 7, 4, 9, 0);
        config.remove("anchors");
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors: " + e.getClass().getName() + "::" + e.getMessage());
        }
    }

    @Test
    public void testInvalidData() {
        byte[] invalidImage = {123, 012, 45, -3, -102};
        try {
            ypJson.processImage(invalidImage);
            fail("Should throw Exception when not a jpeg");
        } catch (ImageProcessingException e) {
            assertTrue("Failure should be caused by invalid image type. Error actually was: " + e.getMessage()
                    , e.getMessage().startsWith(YoloProcessor.class.getCanonicalName() + ".invalidImage"));
        }
        try {
            ypJson.processImage(invalidImage);
            fail("Should throw Exception when not a jpeg");
        } catch (ImageProcessingException e) {
            assertTrue("Failure should be caused by invalid image type. Error actually was: " + e.getMessage()
                    , e.getMessage().startsWith(YoloProcessor.class.getCanonicalName() + ".invalidImage"));
        }
    }

    @Test
    public void testImageSavingWithoutLabels() throws ImageProcessingException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        labelTestHelper(false);
    }

    @Test
    public void testImageSavingWithLabels() throws ImageProcessingException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        labelTestHelper(true);
    }

    // Helper function to test the "labelImage" option
    public void labelTestHelper(Boolean labelOption) throws ImageProcessingException {
        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");

        if (labelOption) {
            config.put("labelImage", "true");
        } else {
            config.put("labelImage", "false");
        }

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should save first image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);

            try {
                // Converting original test image to buffered image, since this is how we save images
                BufferedImage tempOriginal = ImageIO.read(new ByteArrayInputStream(getTestImage()));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(tempOriginal, "jpg", baos);
                baos.flush();

                // Getting both images as byte arrays and comparing them for equality
                byte[] originalImg = baos.toByteArray();
                byte[] savedImg = Files.readAllBytes(d.listFiles()[0].toPath());

                // Based on labelOption, check if they are equal or not equal
                if (labelOption) {
                    assertFalse("Images should not be identical", Arrays.equals(originalImg, savedImg));
                } else {
                    assertTrue("Images should be identical", Arrays.equals(originalImg, savedImg));
                }

                baos.close();
            } catch (IOException e) {
                fail("Should not catch exception when checking saved image vs. original.");
            }
        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testInvalidResizingSavedImage() throws ImageProcessingException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map savedResolution = new LinkedHashMap<>();
        
        // The longEdge is larger than the original image, should not do any resizing
        savedResolution.put("longEdge", 1000);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        config.put("savedResolution", savedResolution);
        checkInvalidResizing(config);
        
        // The longEdge is a negative number which is not allowed, should not do any resizing
        savedResolution.put("longEdge", -100);
        config.put("savedResolution", savedResolution); 
        checkInvalidResizing(config);
        
        // The longEdge is not an integer which is not allowed, should not do any resizing
        savedResolution.put("longEdge", 0.5);
        config.put("savedResolution", savedResolution);
        checkInvalidResizing(config);
        
        // The longEdge is not a number which is not allowed, should not do any resizing
        savedResolution.put("longEdge", "jibberish");
        config.put("savedResolution", savedResolution);
        checkInvalidResizing(config);
        
        // The savedResolution is not a map which is not allowed, should not do any resizing
        config.put("savedResolution", "jibberish");
        checkInvalidResizing(config);
    }
    
    // Helper function to test invalid resizing
    public void checkInvalidResizing(Map config) throws ImageProcessingException, IOException {
        YoloProcessor ypImageSaver = new YoloProcessor();
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            // Original dimensions
            assert resizedImage.getWidth() == TEST_IMAGE_WIDTH;
            assert resizedImage.getHeight() == TEST_IMAGE_HEIGHT;

        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testResizingSavedImageLocal() throws ImageProcessingException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map savedResolution = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        config.put("savedResolution", savedResolution);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            assert resizedImage.getWidth() == RESIZED_IMAGE_WIDTH;
            assert resizedImage.getHeight() == RESIZED_IMAGE_HEIGHT;

        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testResizingSavedImageVantiq() throws ImageProcessingException, IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map savedResolution = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        config.put("savedResolution", savedResolution);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        try {

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;
            
            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());
            
            // Get the path to the saved image in VANTIQ
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            JsonObject responseObj = (JsonObject) vantiqResponse.getBody();
            JsonElement pathJSON = responseObj.get("content");
            String imagePath = pathJSON.getAsString();
            
            // Download image so we can confirm dimensions
            vantiqResponse = vantiq.download(imagePath);
            BufferedSource source = (BufferedSource) vantiqResponse.getBody();
            byte[] imageBytes = source.readByteArray();
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            BufferedImage resizedImage = ImageIO.read(imageStream);
            
            
            assert resizedImage.getWidth() == RESIZED_IMAGE_WIDTH;
            assert resizedImage.getHeight() == RESIZED_IMAGE_HEIGHT;

        } finally {
            ypImageSaver.close();
        }
    }
    
    @Test
    public void testResizingSavedImageBoth() throws ImageProcessingException, IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map savedResolution = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "both");
        config.put("savedResolution", savedResolution);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            assert resizedImage.getWidth() == RESIZED_IMAGE_WIDTH;
            assert resizedImage.getHeight() == RESIZED_IMAGE_HEIGHT;
            
            // Checking that image was saved to VANTIQ
            Thread.sleep(2000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());
            
            // Get the path to the saved image in VANTIQ
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            JsonObject responseObj = (JsonObject) vantiqResponse.getBody();
            JsonElement pathJSON = responseObj.get("content");
            String imagePath = pathJSON.getAsString();
            
            // Download image so we can confirm dimensions
            vantiqResponse = vantiq.download(imagePath);
            BufferedSource source = (BufferedSource) vantiqResponse.getBody();
            byte[] imageBytes = source.readByteArray();
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            resizedImage = ImageIO.read(imageStream);
            
            
            assert resizedImage.getWidth() == RESIZED_IMAGE_WIDTH;
            assert resizedImage.getHeight() == RESIZED_IMAGE_HEIGHT;

        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }

    @Test
    public void testImageSavingLocal() throws ImageProcessingException, InterruptedException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should save first image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);

            // Check it didn't save to VANTIQ
            checkNotUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);

            results = null;
            results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Every other so second should not save
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;

            // Check lastFilename is null, meaning it couldn't have saved in VANTIQ
            assert results.getLastFilename() == null;

            results = null;
            results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Every other so third and first should be saved
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 2;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            assert d.listFiles()[1].getName().matches(timestampPattern);

            // Check it didn't save to VANTIQ
            checkNotUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }

    // Identical to testImageSavingLocal(), but saveImage is set to "both". Behavior should be identical.
    @Test
    public void testImageSavingBoth() throws ImageProcessingException, InterruptedException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "both");
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }

        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should save first image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());

            results = null;
            results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Every other so second should not save
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;

            results = null;
            results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Every other so third and first should be saved
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 2;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            assert d.listFiles()[1].getName().matches(timestampPattern);

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());
        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }

    // Similar to testImageSavingLocal() and testImageSavingBoth(), but saveImage is set to "vantiq".
    // No images should be saved locally.
    @Test
    public void testImageSavingVantiq() throws ImageProcessingException, InterruptedException {

        // Only run test with intended vantiq availability

        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }

        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should not exist since images are not being saved locally.
            assert !d.exists();

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }

    @Test
    public void testQuery() throws ImageProcessingException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }

        String queryOutputDir = OUTPUT_DIR + "query";
        String queryOutputFileVantiq = "vantiqTest";
        String queryOutputFile = "file";
        File d = new File(OUTPUT_DIR);
        File dNew = new File(queryOutputDir);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            if (dNew.exists()) {
                deleteDirectory(queryOutputDir);
            }

            Map request = new LinkedHashMap<>();
            NeuralNetResults results = ypJson.processImage(getTestImage(), request);
            assert results != null;
            assert results.getResults() != null;

            // Should not have saved image
            assert !d.exists();

            // Test when saveImage is not set correctly
            request.put("NNsaveImage", "jibberish");
            request.put("NNoutputDir", queryOutputDir);
            results = null;
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            assert results.getResults() != null;

            // Should not have saved the image
            assert !dNew.exists();

            // Test when saveImage is set to vantiq, even when NNoutputDir is specified
            request.remove("NNsaveImage");
            request.put("NNsaveImage", "vantiq");
            request.put("NNfileName", queryOutputFileVantiq);
            results = null;
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            assert results.getResults() != null;

            // Should not have saved the image locally
            assert !dNew.exists();

            // Checking that image was saved in VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());

            request.put("NNsaveImage", "both");
            request.put("NNfileName", queryOutputFile);
            request.put("NNoutputDir", queryOutputDir);
            results = null;
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            assert results.getResults() != null;

            // Should have saved the image at queryOutputFile + ".jpg"
            assert dNew.exists();
            assert dNew.isDirectory();
            assert dNew.listFiles().length == 1;
            assert dNew.listFiles()[0].getName().equals(queryOutputFile + ".jpg");

            // Checking that image was saved in VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());

            // Save with "local" instead of "both", and remove fileName
            request.remove("NNfileName");
            request.remove("NNsaveImage");
            request.put("NNsaveImage", "local");
            results = null;
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            assert results.getResults() != null;

            // Should be saved with a timestamp
            assert dNew.exists();
            assert dNew.isDirectory();
            File[] listOfFiles = dNew.listFiles();
            assert listOfFiles.length == 2;

            // listFiles() Returns data in no specific order, so we check to make sure we are grabbing correct file
            if (listOfFiles[0].getName().equals("file.jpg")) {
                assert listOfFiles[1].getName().matches(timestampPattern);
            } else {
                assert listOfFiles[0].getName().matches(timestampPattern);
            }

            // Checking that image was not saved in VANTIQ
            Thread.sleep(1000);
            checkNotUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);

            queryOutputFile += ".jpeg";
            request.put("NNfileName", queryOutputFile);
            results = null;
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            assert results.getResults() != null;

            assert dNew.listFiles().length == 3;

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            if (dNew.exists()) {
                deleteDirectory(queryOutputDir);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testInvalidPreCropping() throws ImageProcessingException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        
        // Uninitialized preCrop
        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // Only the "x" value
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // Only the "y" value
        preCrop.remove("x");
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // Only the "width" value
        preCrop.remove("y");
        preCrop.put("width", CROPPED_WIDTH);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // Only the "height" value
        preCrop.remove("width");
        preCrop.put("height", CROPPED_HEIGHT);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // Only "x" and "y"
        preCrop.remove("height");
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // Only "x", "y", and "width"
        preCrop.put("width", CROPPED_WIDTH);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // The width and height are larger than the original image, should not do any resizing
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", 1000);
        preCrop.put("height", 1000);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // The width and height are 0, should not do any resizing
        preCrop.put("width", 0);
        preCrop.put("height", 0);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // The coordinates are negative, should not do any resizing
        preCrop.put("x", -1);
        preCrop.put("y", -1);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        config.put("cropBeforeAnalysis", preCrop); 
        checkInvalidPreCropping(config);
        
        // Just x coordinate is negative, should not do any resizing
        preCrop.put("x", -1);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        config.put("cropBeforeAnalysis", preCrop); 
        checkInvalidPreCropping(config);
        
        // Just y coordinate is negative, should not do any resizing
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", -1);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        config.put("cropBeforeAnalysis", preCrop); 
        checkInvalidPreCropping(config);
        
        // Two values are not integers, should not do any resizing
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", 0.5);
        preCrop.put("height", 0.5);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // A value is not a number, should not do any resizing
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", "jibberish");
        preCrop.put("height", CROPPED_HEIGHT);
        config.put("cropBeforeAnalysis", preCrop);
        checkInvalidPreCropping(config);
        
        // The cropBeforeAnalysis is not a map which is not allowed, should not do any resizing
        config.put("cropBeforeAnalysis", "jibberish");
        checkInvalidPreCropping(config);
    }
    
    // Helper method used to check invalid pre cropping configurations
    public void checkInvalidPreCropping(Map config) throws ImageProcessingException, IOException {
        YoloProcessor ypImageSaver = new YoloProcessor();
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            // Original dimensions
            assert resizedImage.getWidth() == TEST_IMAGE_WIDTH;
            assert resizedImage.getHeight() == TEST_IMAGE_HEIGHT;

        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testPreCroppingLocal() throws ImageProcessingException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        
        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        config.put("cropBeforeAnalysis", preCrop);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            assert resizedImage.getWidth() == CROPPED_WIDTH;
            assert resizedImage.getHeight() == CROPPED_HEIGHT;
        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testPreCroppingVantiq() throws ImageProcessingException, InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        config.put("cropBeforeAnalysis", preCrop);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        try {

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;
            
            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());
            
            // Get the path to the saved image in VANTIQ
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            JsonObject responseObj = (JsonObject) vantiqResponse.getBody();
            JsonElement pathJSON = responseObj.get("content");
            String imagePath = pathJSON.getAsString();
            
            // Download image so we can confirm dimensions
            vantiqResponse = vantiq.download(imagePath);
            BufferedSource source = (BufferedSource) vantiqResponse.getBody();
            byte[] imageBytes = source.readByteArray();
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            BufferedImage resizedImage = ImageIO.read(imageStream);

            assert resizedImage.getWidth() == CROPPED_WIDTH;
            assert resizedImage.getHeight() == CROPPED_HEIGHT;
        } finally {
            ypImageSaver.close();
        }
    }
    
    @Test
    public void testPreCroppingBoth() throws ImageProcessingException, InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "both");
        config.put("cropBeforeAnalysis", preCrop);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            assert resizedImage.getWidth() == CROPPED_WIDTH;
            assert resizedImage.getHeight() == CROPPED_HEIGHT;
            
            // Checking that image was saved to VANTIQ
            Thread.sleep(2000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(results.getLastFilename());
            
            // Get the path to the saved image in VANTIQ
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            JsonObject responseObj = (JsonObject) vantiqResponse.getBody();
            JsonElement pathJSON = responseObj.get("content");
            String imagePath = pathJSON.getAsString();
            
            // Download image so we can confirm dimensions
            vantiqResponse = vantiq.download(imagePath);
            BufferedSource source = (BufferedSource) vantiqResponse.getBody();
            byte[] imageBytes = source.readByteArray();
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            resizedImage = ImageIO.read(imageStream);
            
            assert resizedImage.getWidth() == CROPPED_WIDTH;
            assert resizedImage.getHeight() == CROPPED_HEIGHT;
        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testPreCroppingAndResizing() throws ImageProcessingException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        Map savedResolution = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);
        
        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        config.put("cropBeforeAnalysis", preCrop);
        config.put("savedResolution", savedResolution);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            // Since savedResolution was set, the image dimensions should correspond to the longEdge value
            assert resizedImage.getWidth() == RESIZED_IMAGE_WIDTH;
            assert resizedImage.getHeight() == RESIZED_IMAGE_HEIGHT;
        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testInvalidPreCroppingAndResizing() throws ImageProcessingException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        Map savedResolution = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        
        // This value is larger than the pre-cropped image size
        savedResolution.put("longEdge", 400);
        
        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        config.put("cropBeforeAnalysis", preCrop);
        config.put("savedResolution", savedResolution);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Should saved image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
            File resizedImageFile = new File(OUTPUT_DIR + "/" + d.listFiles()[0].getName());
            BufferedImage resizedImage = ImageIO.read(resizedImageFile);
            
            // Since the savedResolution was too large, image dimensions should correspond to the preCrop values
            assert resizedImage.getWidth() == CROPPED_WIDTH;
            assert resizedImage.getHeight() == CROPPED_HEIGHT;
        } finally {
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            ypImageSaver.close();
        }
    }
    
    @Test
    public void testPreCroppingResults() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();
        
        preCrop.put("x", PRECROP_KEYBOARD_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_KEYBOARD_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", KEYBOARD_CROPPED_WIDTH);
        preCrop.put("height", KEYBOARD_CROPPED_HEIGHT);

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("cropBeforeAnalysis", preCrop);

        // Config with meta file, label file, pb file, and anchors
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, croppedImageResultsAsString);
        } catch (Exception e) {
            fail("Should not fail with valid config: " + e.getClass().getName() + "::" + e.getMessage());
        } finally {
            if (ypImageSaver != null) {
                ypImageSaver.close();
            }
        }
    }

    @Test
    public void testNotSuppressNullValues() throws InterruptedException {
        // Setting suppress null values to false
        // Here, we check to see that we still received data even though there were no recognitions
        testSuppressNullValuesHelper(false);
    }

    @Test
    public void testSuppressNullValues() throws InterruptedException {
        // Setting suppress null values to true
        // Here, we check to see that we did not receive any data since there were no recognitions
        testSuppressNullValuesHelper(true);
    }

    public void testSuppressNullValuesHelper(boolean suppressNullValues) throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        // Check that Source, Type, Topic, Procedure and Rule do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists(vantiq));
        assumeFalse(checkTypeExists(vantiq));
        assumeFalse(checkRuleExists(vantiq));

        // Setup a VANTIQ Obj Rec Source, and start running the core

        setupSource(createSourceDef(suppressNullValues));

        // Create Type to store results
        setupType();

        // Create Rule to store results in Type
        setupRule();

        // Wait for 15 seconds while the source polls for frames from the data source and stores data in type
        Thread.sleep(15000);
        try {
            // Make sure that appropriate number of entries are stored in type (this means discard policy works, and core is still alive)
            VantiqResponse response = vantiq.select(testTypeName, null, null, null);
            ArrayList responseBody = (ArrayList) response.getBody();

            // If suppressNullValues is set to true, we shouldn't have any results stored in our type
            if (suppressNullValues) {
                assertEquals("Get responseBody content when none expected: " + responseBody,
                        0, responseBody.size());
            } else {
                System.out.println("Found response: " + responseBody);
                // Otherwise, we should have some results and they should be empty arrays
                assert responseBody.size() > 0;

                for (int i = 0; i < responseBody.size(); i++) {
                    JsonObject resultObject = (JsonObject) responseBody.get(i);
                    assert resultObject.get("results") instanceof JsonArray;
                    assertEquals("Get resultObject.results content when none expected: " + responseBody,
                            0, ((JsonArray) resultObject.get("results")).size());
                }
            }
        } finally {
            // Delete the Source/Type/Rule from VANTIQ
            core.close();
            deleteSource(vantiq);
            deleteType(vantiq);
            deleteRule(vantiq);
        }
    }

    @Test
    public void testUploadAsVantiqImage() throws ImageProcessingException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        Map preCrop = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("metaFile", META_FILE);
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        config.put("uploadAsImage", true);
        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the YoloProcessor");
        }
        try {

            NeuralNetResults results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            assert results.getResults() != null;

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            checkUploadToVantiq(results.getLastFilename(), vantiq, VANTIQ_IMAGES);
            vantiqSavedImageFiles.add(results.getLastFilename());

        } finally {
            ypImageSaver.close();
        }
    }

    // ================================================= Helper functions =================================================

    String imageResultsAsString = "[" +
            "{\"confidence\": 0.8445639, " +
                 "\"location\": {\"top\": 255.70024, \"left\": 121.859344, \"bottom\": 372.2343, \"right\": 350.1204, " +
                        "\"centerX\": 235.98985, \"centerY\": 313.9673 }, \"label\": \"keyboard\"}," +
            "{\"confidence\": 0.7974271," +
                "\"location\":{\"top\": 91.255974, \"left\": 164.41359, \"bottom\": 275.69666, \"right\": 350.50714," +
                        "\"centerX\": 257.46036, \"centerY\": 183.47632 }, \"label\": \"tvmonitor\"}," +
            "{\"confidence\": 0.6308692, " +
                "\"location\":{\"top\": 13.766539, \"left\": -0.614191, \"bottom\": 367.22836, \"right\": 122.28085," +
                        "\"centerX\": 60.83333, \"centerY\": 190.49745 }, \"label\": \"refrigerator\"}, " +
            "{\"confidence\":0.53600997," +
                "\"location\":{\"top\": 309.05722, \"left\": 422.40067, \"bottom\": 361.50223, \"right\": 485.40735," +
                        "\"centerX\": 453.90402, \"centerY\": 335.2797 }, \"label\": \"mouse\"}" +
    "]";

    // In this class, some tests are dependent upon whether there's a VANTIQ server defined.  When those tests run,
    // they alter the conditions of other tests.  To make running the tests a bit more friendly, we'll try
    // and be aware of these conditions and set our expectations accordingly.

   
    String imageResultsAsStringWithoutServer = "[{\"confidence\":0.8445639, "
               + "\"location\": {\"top\": 169.16177, \"left\": 6.4747243, \"bottom\": 285.69586, \"right\": 234.73576," +
                    "\"centerX\": 120.60525, \"centerY\": 227.42882 }, \"label\":\"keyboard\"},"
            + "{\"confidence\": 0.7974271, "
                + "\"location\": {\"top\": 33.56367, \"left\": 241.33667, \"bottom\": 218.00436, \"right\": 427.43024," +
                    "\"centerX\": 334.38342, \"centerY\": 125.78402 }, \"label\": \"tvmonitor\"},"
            + "{\"confidence\": 0.6955277, "
                + "\"location\": {\"top\": 149.68317, \"left\": 507.42972, \"bottom\": 256.89297, \"right\": 739.0765," +
                     "\"centerX\": 623.2531, \"centerY\": 203.28807 }, \"label\": \"keyboard\"},"
            + "{\"confidence\": 0.53600997, "
               + "\"location\": {\"top\": 222.51874, \"left\": 76.24681, \"bottom\": 274.96375, \"right\": 139.2535," +
                    "\"centerX\": 107.75015, \"centerY\": 248.74124 }, \"label\": \"mouse\"}" +
    "]";

    
    String imageResultsAsString608 = "[{\"confidence\":0.8672237, "
            + "\"location\": {\"top\": 93.55155, \"left\": 157.38762, \"bottom\": 280.36542, \"right\": 345.06442, " +
                 "\"centerX\": 251.22603, \"centerY\": 186.95847 }, \"label\": \"tvmonitor\"},"
        + "{\"confidence\" :0.7927524, \"location\": {\"top\": 263.62683, \"left\": 123.48807, \"bottom\": 371.69046," +
            "\"right\": 331.86023, \"centerX\": 227.67413, \"centerY\": 317.65866 }, \"label\": \"keyboard\"}, "
        + "{\"confidence\": 0.57419246, \"location\": {\"top\": 146.11186, \"left\": 319.9637, \"bottom\": 317.0444," +
            "\"right\": 423.20792, \"centerX\": 371.58582, \"centerY\": 231.57812 }, \"label\": \"cup\"}" +
    "]";
    
    String croppedImageResultsAsString = "[{\"confidence\": 0.91599655, \"location\": " +
            "{\"top\": 8.16388, \"left\": 1.0525968, \"bottom\": 118.984344, " +
                "\"right\": 217.2165, \"centerX\": 109.134544, \"centerY\": 63.574112 }, \"label\": \"keyboard\"}," +
            "{\"confidence\": 0.57027775, \"location\": {\"top\": 2.1221037, \"left\": 205.86801, \"bottom\": 64.227806, " +
                "\"right\": 230.19707, \"centerX\": 218.03256, \"centerY\": 33.174957 }, \"label\": \"cup\"}" +
       "]";
    
    String neuralNetJSON1 =
            "{"
                    + "     \"neuralNet\":"
                    + "         {"
                    + "             \"anchors\":[0.57273, 0.677385, 1.87446, 2.06253, 3.33843, 5.47434, 7.88282, 3.52778, 9.77052, 9.16828], "
                    + "             \"pbFile\": \"" + PB_FILE + "\", "
                    + "             \"labelFile\": \"" + LABEL_FILE + "\", "
                    + "             \"metaFile\": \"" + META_FILE + "\", "
                    + "             \"type\": \"yolo\""
                    + "         }"
                    + "}";

    String neuralNetJSON2 =
            "{"
                    + "     \"neuralNet\":"
                    + "         {"
                    + "             \"pbFile\": \"" + PB_FILE + "\", "
                    + "             \"metaFile\": \"" + META_FILE + "\", "
                    + "             \"type\": \"yolo\""
                    + "         }"
                    + "}";

    String neuralNetJSON3 =
            "{"
                    + "     \"neuralNet\":"
                    + "         {"
                    + "             \"anchors\":[0.57273, 0.677385, 1.87446, 2.06253, 3.33843, 5.47434, 7.88282, 3.52778, 9.77052, 9.16828], "
                    + "             \"pbFile\": \"" + PB_FILE + "\", "
                    + "             \"labelFile\": \"" + LABEL_FILE + "\", "
                    + "             \"type\": \"yolo\""
                    + "         }"
                    + "}";

    String neuralNetJSON4 =
            "{"
                    + "     \"neuralNet\":"
                    + "         {"
                    + "             \"pbFile\": \"" + PB_FILE + "\", "
                    + "             \"labelFile\": \"" + LABEL_FILE + "\", "
                    + "             \"type\": \"yolo\""
                    + "         }"
                    + "}";
    
    String neuralNetJSON5 =
            "{"
                    + "     \"neuralNet\":"
                    + "         {"
                    + "             \"pbFile\": \"" + PB_FILE_608 + "\", "
                    + "             \"metaFile\": \"" + META_FILE_608 + "\", "
                    + "             \"type\": \"yolo\""
                    + "         }"
                    + "}";

    List<Map> getExpectedResults(String resultsAsString) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper m = new ObjectMapper();
        return m.readValue(resultsAsString, List.class);
    }

    void verifyProcessing(YoloProcessor ypImageSaver, String resultsAsString) throws ImageProcessingException {
        NeuralNetResults results = ypImageSaver.processImage(getTestImage());
        assert results != null;
        assert results.getResults() != null;
        try {
            resultsEquals(results.getResults(), getExpectedResults(resultsAsString)); // Will throw assert error with a message when not equivalent
        } catch (IOException e) {
            fail("Could not interpret json string" + e.getMessage());
        }
    }

    void resultsEquals(List<Map<String, ?>> list, List<Map> expectedRes) {
        assertTrue("list Size: " + list.size() + ", expected: "+ expectedRes.size(),
                list.size() == expectedRes.size());
        for (int i = 0; i < list.size(); i++) {
            mapEquals(list.get(i), expectedRes.get(i));
        }
    }

    void mapEquals(Map<String, ?> actualMap, Map<String, ?> expectedMap) {
        for (Map.Entry<String, ?> entry : actualMap.entrySet()) {
            Object expected = expectedMap.get(entry.getKey());
            Object actual = entry.getValue();
            assertTrue("Result did not match. Expected " + expected + " in " + entry.getKey()
                    + " but received " + actual, valEquals(actual, expected));
        }
    }

    boolean valEquals(Object actual, Object expected) {
        if (actual instanceof Map && expected instanceof Map) {
            mapEquals((Map) actual, (Map) expected);
            return true; // mapEquals will throw assertionError if they don't match
        } else if (actual instanceof Number && expected instanceof Number) {
            double diff = ((Number) actual).doubleValue() - ((Number) expected).doubleValue();
            return Math.abs(diff) < .0001;
        }

        return actual.equals(expected);
    }

    ExtensionServiceMessage createRealConfig(String neuralNetJSON) throws JsonParseException, JsonMappingException, IOException {
        Map msg = new LinkedHashMap();
        Map object = new LinkedHashMap();

        ObjectMapper m = new ObjectMapper();
        object = m.readValue(neuralNetJSON, Map.class);
        msg.put("object", object);

        ExtensionServiceMessage message = new ExtensionServiceMessage("").fromMap(msg);
        return message;
    }

    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new ObjectRecognitionCore(testSourceName, testAuthToken, testVantiqServer, MODEL_DIRECTORY);
            core.start(CORE_START_TIMEOUT);
        }
    }

    public static Map<String,Object> createSourceDef(boolean suppressNullValues) {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> objRecConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        Map<String,Object> dataSource = new LinkedHashMap<String,Object>();
        Map<String,Object> neuralNet = new LinkedHashMap<String,Object>();

        // Setting up general config options
        general.put("pollTime", 1000);
        general.put("suppressEmptyNeuralNetResults", suppressNullValues);

        // Setting up dataSource config options
        dataSource.put("fileLocation", NOTHING_VIDEO_LOCATION);
        dataSource.put("fileExtension", "mov");
        dataSource.put("type", "file");
//        dataSource.put("camera", NO_RECOGNIZED_OBJECTS_CAMERA_ADDRESS);
//        dataSource.put("type", "network");

        // Setting up neuralNet config options
        neuralNet.put("type", "yolo");
        neuralNet.put("metaFile", META_FILE);
        neuralNet.put("pbFile", PB_FILE);
        neuralNet.put("threshold", 60); // Overcome strange interpretation of camera images

        // Placing general config options in "objRecConfig"
        objRecConfig.put("general", general);
        objRecConfig.put("dataSource", dataSource);
        objRecConfig.put("neuralNet", neuralNet);

        // Putting objRecConfig in the source configuration
        sourceConfig.put("objRecConfig", objRecConfig);

        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", OR_SRC_TYPE);
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");

        return sourceDef;
    }

    public static void setupType() {
        Map<String,Object> typeDef = new LinkedHashMap<String,Object>();
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        Map<String,Object> propertyDef = new LinkedHashMap<String,Object>();
        propertyDef.put("type", "Object");
        propertyDef.put("multi", true);
        propertyDef.put("required", true);
        properties.put("results", propertyDef);
        typeDef.put("properties", properties);
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }

    public static void setupRule() {
        String rule = "RULE " + testRuleName + "\n"
                + "WHEN EVENT OCCURS ON \"/sources/" + testSourceName + "\" AS sourceEvent\n"
                + "var message = sourceEvent.value\n"
                + "INSERT " + testTypeName + "(results: message.results)";

        vantiq.insert("system.rules", rule);
    }
}
