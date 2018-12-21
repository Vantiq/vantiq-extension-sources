
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
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;
import io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever;
import okhttp3.Response;

public class TestYoloProcessor extends NeuralNetTestBase {

    static final String LABEL_FILE = "coco.names";
    static final String PB_FILE = "yolo.pb";
    static final String OUTPUT_DIR = "src/test/resources/out";
    static final int SAVE_RATE = 2; // Saves every other so that we can know it counts correctly
    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";

    static YoloProcessor ypJson;
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;
    
    static List<String> vantiqSavedFiles = new ArrayList<>();

    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}\\.jpg";

    // A single processor is used for the entire class because it is very expensive to do initial setup
    @BeforeClass
    public static void classSetup() {
        assumeTrue("No model file for test. Should be at " + new File(MODEL_DIRECTORY + "/" + PB_FILE).getAbsolutePath() + ""
                , new File(MODEL_DIRECTORY + "/" + PB_FILE).exists());

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
        for(int i = 0; i < vantiqSavedFiles.size(); i++) {
            Thread.sleep(1000);
            vantiq.deleteOne("system.documents", vantiqSavedFiles.get(i), new BaseResponseHandler() {

                @Override public void onSuccess(Object body, Response response) {
                    super.onSuccess(body, response);
                }
                @Override public void onError(List<VantiqError> errors, Response response) {
                    super.onError(errors, response);
                }

            });
        }
    }

    @AfterClass
    public static void classTearDown() {
        ypJson.close();
        ypJson = null;

        File d = new File(OUTPUT_DIR);
        if (d.exists()) {
            deleteDirectory(OUTPUT_DIR);
        }
    }

    @Test
    public void testResults() throws ImageProcessingException {
        NeuralNetResults results = ypJson.processImage(getTestImage());
        assert results != null;
        assert results.getResults() != null;
        try {
            resultsEquals(results.getResults(), getExpectedResults()); // Will throw assert error with a message when not equivalent
        } catch (IOException e) {
            fail("Could not interpret json string" + e.getMessage());
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
        
        if(labelOption) {
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
                byte [] originalImg = baos.toByteArray();
                byte [] savedImg = Files.readAllBytes(d.listFiles()[0].toPath());
                
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
    String imageResultsAsString = "[{\"confidence\":0.8445639, \"location\":{\"top\":229.5673, \"left\":99.603455, \"bottom\":398.36725, "
            + "\"right\":372.37628}, \"label\":\"keyboard\"}, {\"confidence\":0.7516027, \"location\":{\"top\":79.53046, \"left\":62.83799, "
            + "\"bottom\":298.18152, \"right\":516.48846}, \"label\":\"tvmonitor\"}]";

    List<Map> getExpectedResults() throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper m = new ObjectMapper();
        return m.readValue(imageResultsAsString, List.class);
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
}
