/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.ml.tensorflow.util.ImageUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;
import okhttp3.Response;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestNoProcessor extends NeuralNetTestBase {

    static final String OUTPUT_DIR = System.getProperty("buildDir") + "/resources/out";
    static final int SAVE_RATE = 2; // Saves every other so that we can know it counts correctly

    static NoProcessor noProcessor;
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;

    static List<String> vantiqSavedFiles = new ArrayList<>();
    static List<String> vantiqSavedImageFiles = new ArrayList<>();

    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}\\.jpg";
    static final String sameTimestampPattern = "\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}\\(\\d+\\)\\.jpg";

    // A single processor is used for the entire class because it is very expensive to do initial setup
    @BeforeClass
    public static void classSetup() {
        noProcessor = new NoProcessor();

        Map<String, Object> config = new LinkedHashMap<>();
        noProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);

        if (testAuthToken != null && testVantiqServer != null) {
            vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
            vantiq.setAccessToken(testAuthToken);

            try {
                createSourceImpl(vantiq);
            } catch (Exception e) {
                fail("Trapped exception creating source impl: " + e);
            }
        }
    }

    @AfterClass
    public static void deleteFromVantiq() throws InterruptedException {
        if (vantiq != null && vantiq.isAuthenticated()) {

            try {
                deleteSourceImpl(vantiq);
            } catch (Exception e) {
                fail("Trapped exception deleting source impl: " + e);
            }
            // Deleting files saved as documents
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

    @AfterClass
    public static void classTearDown() {
        if (noProcessor != null) {
            noProcessor.close();
            noProcessor = null;
        }

        File d = new File(OUTPUT_DIR);
        if (d.exists()) {
            deleteDirectory(OUTPUT_DIR);
        }
    }

    @Test
    public void testInvalidConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();
        NoProcessor npProcessor2 = new NoProcessor();

        // Nothing included
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        assertTrue("NoProcessor should still setup with empty config", npProcessor.isSetup);
        
        // Testing with nonsense config options
        config.put("jibberish", "garbage");
        config.put("more", "jibberish");
        npProcessor2.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        assertTrue("NoProcessor should still setup with empty config", npProcessor2.isSetup);
    }

    @Test
    public void testValidConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();
        NoProcessor npProcessor2 = new NoProcessor();
        NoProcessor npProcessor3 = new NoProcessor();

        config.put("saveImage", "local");
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        assertTrue("NoProcessor should still setup with empty config", npProcessor.isSetup);
        
        config.put("saveImage", "both");
        npProcessor2.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        assertTrue("NoProcessor should still setup with empty config", npProcessor2.isSetup);
        
        config.put("saveImage", "vantiq");
        npProcessor3.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        assertTrue("NoProcessor should still setup with empty config", npProcessor3.isSetup);
    }

    @Test
    public void testImageSavingLocal() throws ImageProcessingException, InterruptedException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        String lastFilename;
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();

        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "local");
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        File d = new File(OUTPUT_DIR);
        
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            // Should not return any results, just saving image
            Thread.sleep(1000);
            NeuralNetResults results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Should save first image with timestamp
            assert d.exists();
            assert d.isDirectory();
            File[] lf = d.listFiles();
            assert lf != null;
            assert lf.length == 1;
            assert lf[0].getName().matches(timestampPattern);

            // Check it didn't save to VANTIQ
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkNotUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);

            results = null;
            results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Every other so second should not save
            assert d.exists();
            assert d.isDirectory();
            lf = d.listFiles();
            assert lf != null;
            assert lf.length == 1;
            
            results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Every other so third and first should be saved
            assert d.exists();
            assert d.isDirectory();
            lf = d.listFiles();
            assert lf != null;
            assert lf.length == 2;
            assert lf[0].getName().matches(timestampPattern) || lf[0].getName().matches(sameTimestampPattern);
            assert lf[1].getName().matches(timestampPattern) || lf[1].getName().matches(sameTimestampPattern);

            // Check it didn't save to VANTIQ
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkNotUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            npProcessor.close();
        }
    }

    // Identical to testImageSavingLocal(), but saveImage is set to "both". Behavior should be identical.
    @Test
    public void testImageSavingBoth() throws ImageProcessingException, InterruptedException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        String lastFilename;
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();

        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "both");
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);

        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Should save first image with timestamp
            assert d.exists();
            assert d.isDirectory();
            File[] lf = d.listFiles();
            assert lf != null;
            assert lf.length == 1;
            assert lf[0].getName().matches(timestampPattern);

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(lastFilename);

            results = null;
            results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Every other so second should not save
            assert d.exists();
            assert d.isDirectory();
            lf = d.listFiles();
            assert lf != null;
            assert lf.length == 1;

            results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Every other so third and first should be saved
            assert d.exists();
            assert d.isDirectory();
            lf = d.listFiles();
            assert lf != null;
            assert lf.length == 2;
            assert lf[0].getName().matches(timestampPattern);
            assert lf[1].getName().matches(timestampPattern);

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(lastFilename);

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            npProcessor.close();
        }
    }
    
    // Similar to testImageSavingVantiq(), but includes returning the image in a Base64-encoded form.
    // No images should be saved locally.
    @Test
    public void testImageSavingVantiqPlusEncoded() throws ImageProcessingException, InterruptedException {
        
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        String lastFilename;
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();
        
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        config.put("includeEncodedImage", true);
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);
        assert npProcessor.includeEncodedImage;
        
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            
            byte[] testImage = getTestImage();
            NeuralNetResults results = npProcessor.processImage(testImage);
            assert results.getResults() == null;
            assert results.getEncodedImage() != null;
    
            String incImage = results.getEncodedImage();
            assert incImage != null;
            byte[] retBytes = Base64.getDecoder().decode(incImage.getBytes(StandardCharsets.UTF_8));
    
            // For insurance, we'll also ensure that we can decode the bits & that our images are at
            // least the same size.
            BufferedImage encodedImage = ImageUtil.createImageFromBytes(retBytes);
            BufferedImage origImage = ImageUtil.createImageFromBytes(testImage);
            assert origImage.getWidth() == encodedImage.getWidth();
            assert origImage.getHeight() == encodedImage.getHeight();
            // Round trip from bytes to base64 to bytes for images can have slightly varying lengths, probably
            // due to image compression, etc.  Semantically, the images are the same, so we'll look at them as a JPEG,
            // then  get the bytes back to compare.
            testImage = ImageUtil.getBytesForImage(origImage);
            assert testImage.length == retBytes.length;
            assert Arrays.equals(testImage, retBytes);
            
            // Should not exist since images are not being saved locally.
            assert !d.exists();
            
            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(lastFilename);
        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            
            npProcessor.close();
        }
    }

    // Similar to testImageSavingLocal() and testImageSavingBoth(), but saveImage is set to "vantiq".
    // No images should be saved locally.
    @Test
    public void testImageSavingVantiq() throws ImageProcessingException, InterruptedException {

        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        String lastFilename;
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();

        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);

        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Should not exist since images are not being saved locally.
            assert !d.exists();

            // Checking that image was saved to VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(lastFilename);

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            npProcessor.close();
        }
    }

    @Test
    public void testQuery() throws ImageProcessingException, InterruptedException {
        doQueryTest(false);
    }
    
    @Test
    public void testQueryWithEncoded() throws ImageProcessingException, InterruptedException {
        doQueryTest(true);
    }
    
    void doQueryTest(boolean includeEncoded) throws ImageProcessingException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        String lastFilename;
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();

        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);

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

            Map<String, String> request = new LinkedHashMap<>();
            NeuralNetResults results = npProcessor.processImage(getTestImage(), request);
            assert results.getResults().isEmpty();

            // Should not have saved image
            assert !d.exists();

            // Test when saveImage is not set correctly
            request.put("NNsaveImage", "jibberish");
            request.put("NNoutputDir", queryOutputDir);
            if (includeEncoded) {
                request.put("includeEncodedImage", "true");
            }
            
            byte[] testImage = getTestImage();
            results = npProcessor.processImage(testImage, request);
            assert results.getResults().isEmpty();
            assert (results.getEncodedImage() == null) != includeEncoded;

            if (includeEncoded) {
                String incImage = results.getEncodedImage();
                assert incImage != null;
                byte[] retBytes = Base64.getDecoder().decode(incImage.getBytes(StandardCharsets.UTF_8));
    
                // For insurance, we'll also ensure that we can decode the bits & that our images are at
                // least the same size.
                BufferedImage encodedImage = ImageUtil.createImageFromBytes(retBytes);
                BufferedImage origImage = ImageUtil.createImageFromBytes(testImage);
                assert origImage.getWidth() == encodedImage.getWidth();
                assert origImage.getHeight() == encodedImage.getHeight();
                assert testImage != null;
                assert testImage.length == retBytes.length;
            }
            // Should not have saved the image
            assert !dNew.exists();

            // Test when saveImage is set to vantiq, even when NNoutputDir is specified
            request.remove("NNsaveImage");
            request.put("NNsaveImage", "vantiq");
            request.put("NNfileName", queryOutputFileVantiq);
            results = npProcessor.processImage(getTestImage(), request);
            assert results.getResults().isEmpty();

            // Should not have saved the image locally
            assert !dNew.exists();

            // Checking that image was saved in VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(lastFilename);

            request.put("NNsaveImage", "both");
            request.put("NNfileName", queryOutputFile);
            request.put("NNoutputDir", queryOutputDir);
            results = npProcessor.processImage(getTestImage(), request);
            assert results.getResults().isEmpty();

            // Should have saved the image at queryOutputFile + ".jpg"
            assert dNew.exists();
            assert dNew.isDirectory();
            File[] lf = dNew.listFiles();
            assert lf != null;
            assert lf.length == 1;
            assert lf[0].getName().equals(queryOutputFile + ".jpg");

            // Checking that image was saved in VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);
            vantiqSavedFiles.add(lastFilename);

            // Save with "local" instead of "both", and remove fileName
            request.remove("NNfileName");
            request.remove("NNsaveImage");
            request.put("NNsaveImage", "local");
            results = npProcessor.processImage(getTestImage(), request);
            assert results.getResults().isEmpty();

            // Should be saved with a timestamp
            assert dNew.exists();
            assert dNew.isDirectory();
            File[] listOfFiles = dNew.listFiles();
            assert listOfFiles != null;
            assert listOfFiles.length == 2;

            // listFiles() Returns data in no specific order, so we check to make sure we are grabbing correct file
            if (listOfFiles[0].getName().equals("file.jpg")) {
                assert listOfFiles[1].getName().matches(timestampPattern);
            } else {
                assert listOfFiles[0].getName().matches(timestampPattern);
            }

            // Checking that image was not saved in VANTIQ
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkNotUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);

            queryOutputFile += ".jpeg";
            request.put("NNfileName", queryOutputFile);
            results = npProcessor.processImage(getTestImage(), request);
            assert results.getResults().isEmpty();

            listOfFiles = dNew.listFiles();
            assert listOfFiles != null;
            assert listOfFiles.length == 3;

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            if (dNew.exists()) {
                deleteDirectory(queryOutputDir);
            }

            npProcessor.close();
        }
    }

    @Test
    public void testUploadAsImage() throws ImageProcessingException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        String lastFilename;
        Map<String, Object> config = new LinkedHashMap<>();
        NoProcessor npProcessor = new NoProcessor();

        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        config.put("saveImage", "vantiq");
        config.put("uploadAsImage", true);
        npProcessor.setupImageProcessing(config, SOURCE_NAME, MODEL_DIRECTORY, testAuthToken, testVantiqServer);

        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            NeuralNetResults results = npProcessor.processImage(getTestImage());
            assert results.getResults() == null;

            // Should not exist since images are not being saved locally.
            assert !d.exists();

            // Checking that image was saved to VANTIQ as an image
            Thread.sleep(1000);
            lastFilename = "objectRecognition/" + SOURCE_NAME + '/' + npProcessor.lastFilename;
            checkUploadToVantiq(lastFilename, vantiq, VANTIQ_IMAGES);
            vantiqSavedImageFiles.add(lastFilename);

            // Checking that image was not saved to VANTIQ as a document
            checkNotUploadToVantiq(lastFilename, vantiq, VANTIQ_DOCUMENTS);

        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }

            npProcessor.close();
        }
    }
}
