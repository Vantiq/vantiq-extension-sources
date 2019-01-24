
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import okhttp3.Response;

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
    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";
    static final String CONFIG_JSON_LOCATION = "src/test/resources/";

    static YoloProcessor ypJson;
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;

    static List<String> vantiqSavedFiles = new ArrayList<>();

    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}\\.jpg";

    // A single processor is used for the entire class because it is very expensive to do initial setup
    @BeforeClass
    public static void classSetup() {
        ypJson = new YoloProcessor();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("pbFile", PB_FILE);
        config.put("labelFile", LABEL_FILE);
        try {
            ypJson.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }

        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }

    @AfterClass
    public static void deleteFromVantiq() throws InterruptedException {
        for (int i = 0; i < vantiqSavedFiles.size(); i++) {
            Thread.sleep(1000);
            vantiq.deleteOne("system.documents", vantiqSavedFiles.get(i), new BaseResponseHandler() {

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
            fail("Should not fail with meta file but no label file.");
        }

        // Meta file included and anchors included
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with meta file and anchors.");
        }

        // Label file included, no meta file
        config.remove("metaFile");
        config.remove("anchors");
        config.put("labelFile", LABEL_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with label file but no meta file.");
        }

        // Label file included and anchors included
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with label file and anchors.");
        }

        // Label and meta file included, anchors included
        config.put("metaFile", META_FILE);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with label and meta file, and anchors.");
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
            fail("Should not fail setup.");
        }
        
        // useMetaIfAvailable flag should be true, since Config Size value is unchanged (default is 416)
        assert ypImageSaver.objectDetector.metaConfigOptions.useMetaIfAvailable == true;
        
        // Frame Size should be 416 since this is what is included in the meta file
        assert ypImageSaver.objectDetector.metaConfigOptions.frameSize == 608;
        
        config.remove("metaFile");
        config.put("labelFile", LABEL_FILE);
        try {
            ypImageSaver2.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail setup.");
        }
        
        // useMetaIfAvailable flag should still be true regardless of meta file presence
        // Since there is no meta file, default value will be used
        assert ypImageSaver2.objectDetector.metaConfigOptions.useMetaIfAvailable == true;
        
        // Frame Size should be 416 since this is what is the default
        assert ypImageSaver2.objectDetector.metaConfigOptions.frameSize == 416;
    }

    @Test
    public void testResults() throws ImageProcessingException {
        verifyProcessing(ypJson, true);
    }

    @Test
    public void testRealJSONConfig() throws ImageProcessingException, JsonParseException, JsonMappingException, IOException, InterruptedException {
        YoloProcessor ypImageSaver = new YoloProcessor();
        ExtensionServiceMessage msg = createRealConfig(neuralNetJSON1);
        Map config = (Map) msg.getObject();
        Map neuralNetConfig = (Map) config.get("neuralNet");

        // Config with meta file, label file, pb file, and anchors
        try {
            ypImageSaver.setupImageProcessing(neuralNetConfig, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
            verifyProcessing(ypImageSaver, true);
        } catch (Exception e) {
            fail("Should not fail with valid config.");
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
            verifyProcessing(ypImageSaver, true);
        } catch (Exception e) {
            fail("Should not fail with valid config.");
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
            verifyProcessing(ypImageSaver, true);
        } catch (Exception e) {
            fail("Should not fail with valid config.");
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
            verifyProcessing(ypImageSaver, true);
        } catch (Exception e) {
            fail("Should not fail with valid config.");
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
            verifyProcessing(ypImageSaver, false);
        } catch (Exception e) {
            fail("Should not fail with valid config.");
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
            fail("Should not fail with valid anchors.");
        }

        // Checking mix of integers and floating points
        anchorList = Arrays.asList(1, 1.0, 0.01, 1, 1.0, 0.01, 1, 1.0, 0.01, 1);
        config.remove("anchors");
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors");
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
            fail("Should not fail with invalid anchors");
        }

        // anchorList is too long
        anchorList = Arrays.asList(1, 3, 6, 4, 4, 5, 7, 4, 9, 0, 9);
        config.remove("anchors");
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors");
        }

        // anchorList contains non-numbers
        anchorList = Arrays.asList(1, 3, 6, "a", 4, 5, 7, 4, 9, 0);
        config.remove("anchors");
        config.put("anchors", anchorList);

        try {
            ypImageSaver.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        } catch (Exception e) {
            fail("Should not fail with invalid anchors");
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
        config.put("labelFile", LABEL_FILE);
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
    public void testImageSavingLocal() throws ImageProcessingException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        Map config = new LinkedHashMap<>();
        YoloProcessor ypImageSaver = new YoloProcessor();

        config.put("pbFile", PB_FILE);
        config.put("labelFile", LABEL_FILE);
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.isSuccess()) {
                fail();
            }

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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.isSuccess()) {
                fail();
            }

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
        config.put("labelFile", LABEL_FILE);
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.hasErrors()) {
                List<VantiqError> errors = vantiqResponse.getErrors();
                for (int i = 0; i < errors.size(); i++) {
                    if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                        fail();
                    }
                }
            }
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.hasErrors()) {
                List<VantiqError> errors = vantiqResponse.getErrors();
                for (int i = 0; i < errors.size(); i++) {
                    if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                        fail();
                    }
                }
            }
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
        config.put("labelFile", LABEL_FILE);
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.hasErrors()) {
                List<VantiqError> errors = vantiqResponse.getErrors();
                for (int i = 0; i < errors.size(); i++) {
                    if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                        fail();
                    }
                }
            }
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
        config.put("labelFile", LABEL_FILE);
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.hasErrors()) {
                List<VantiqError> errors = vantiqResponse.getErrors();
                for (int i = 0; i < errors.size(); i++) {
                    if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                        fail();
                    }
                }
            }
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.hasErrors()) {
                List<VantiqError> errors = vantiqResponse.getErrors();
                for (int i = 0; i < errors.size(); i++) {
                    if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                        fail();
                    }
                }
            }
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
            vantiqResponse = vantiq.selectOne("system.documents", results.getLastFilename());
            if (vantiqResponse.isSuccess()) {
                fail();
            }

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

    // ================================================= Helper functions =================================================
    String imageResultsAsString = "[{\"confidence\":0.8445639, \"location\":{\"top\":255.70024, \"left\":121.859344, \"bottom\":372.2343, "
            + "\"right\":350.1204}, \"label\":\"keyboard\"}, {\"confidence\":0.7974271, \"location\":{\"top\":91.255974, \"left\":164.41359, "
            + "\"bottom\":275.69666, \"right\":350.50714}, \"label\":\"tvmonitor\"}]";
    
    String imageResultsAsString608 = "[{\"confidence\":0.8672237, \"location\":{\"top\":93.55155, \"left\":157.38762, \"bottom\":280.36542, "
            + "\"right\":345.06442}, \"label\":\"tvmonitor\"}, {\"confidence\":0.7927524, \"location\":{\"top\":263.62683, \"left\":123.48807, "
            + "\"bottom\":371.69046, \"right\":331.86023}, \"label\":\"keyboard\"}]";

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

    List<Map> getExpectedResults(boolean useDefault) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper m = new ObjectMapper();
        if (useDefault) {
            return m.readValue(imageResultsAsString, List.class);
        } else {
            return m.readValue(imageResultsAsString608, List.class);
        }
    }

    void verifyProcessing(YoloProcessor ypImageSaver, boolean useDefault) throws ImageProcessingException {
        NeuralNetResults results = ypImageSaver.processImage(getTestImage());
        assert results != null;
        assert results.getResults() != null;
        try {
            resultsEquals(results.getResults(), getExpectedResults(useDefault)); // Will throw assert error with a message when not equivalent
        } catch (IOException e) {
            fail("Could not interpret json string" + e.getMessage());
        }
    }

    void resultsEquals(List<Map<String, ?>> list, List<Map> expectedRes) {
        assert list.size() == expectedRes.size();
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
}
