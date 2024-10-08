/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import okio.BufferedSource;

@SuppressWarnings({"PMD.ExcessiveClassLength"})
@Slf4j
public class TestYoloQueries extends NeuralNetTestBase {
    
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;
    static ObjectRecognitionCore core;
    static List<String> vantiqUploadFiles = new ArrayList<>();
    
    static final int CORE_START_TIMEOUT = 10;
    static final String COCO_MODEL_VERSION = "1.2";
    static final String LABEL_FILE = "coco-" + COCO_MODEL_VERSION + ".names";
    static final String PB_FILE = "coco-" + COCO_MODEL_VERSION + ".pb";
    static final String META_FILE = "coco-" + COCO_MODEL_VERSION + ".meta";
    static final String OUTPUT_DIR = System.getProperty("buildDir") + "/resources/out";

    // Used for pre-cropping tests
    static final int IP_CAMERA_WIDTH = 704;
    static final int IP_CAMERA_HEIGHT = 480;
    static final String IP_CAMERA_ADDRESS = "http://60.45.181.202:8080/mjpg/quad/video.mjpg";

    static final String IMAGE_1_DATE = "2019-02-05--02-35-10";
    static final Map<String,String> IMAGE_1 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_1_DATE + ".jpg");
        put("date", IMAGE_1_DATE);
    }};
    static final String IMAGE_2_DATE = "2019-02-05--02-35-13";
    static final Map<String,String> IMAGE_2 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_2_DATE + ".jpg");
        put("date", IMAGE_2_DATE);
    }};
    static final String IMAGE_3_DATE = "2019-02-05--02-35-16";
    static final Map<String,String> IMAGE_3 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_3_DATE + ".jpg");
        put("date", IMAGE_3_DATE);
    }};
    static final String IMAGE_4_DATE = "2019-02-05--02-35-19";
    static final Map<String,String> IMAGE_4 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_4_DATE + ".jpg");
        put("date", IMAGE_4_DATE);
    }};
    static final String IMAGE_5_DATE = "2019-02-05--02-35-22";
    static final Map<String,String> IMAGE_5 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_5_DATE + ".jpg");
        put("date", IMAGE_5_DATE);
    }};
    static final String IMAGE_6_DATE = "2019-02-05--02-35-22(1)";
    static final Map<String,String> IMAGE_6 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_6_DATE + ".jpg");
        put("date", IMAGE_6_DATE);
    }};
    static final String IMAGE_7_DATE = "2019-02-05--02-35-22(2)";
    static final Map<String,String> IMAGE_7 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + IMAGE_7_DATE + ".jpg");
        put("date", IMAGE_7_DATE);
    }};
    
    // Non-date filename
    static final String QUERY_FILENAME = "testFile";
    static final Map<String,String> IMAGE_8 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + QUERY_FILENAME + ".jpg");
        put("name", QUERY_FILENAME);
    }};
    
    static final String START_DATE = IMAGE_2.get("date");
    static final String END_DATE = IMAGE_4.get("date");
    
    // For image resizing tests
    static final int RESIZED_IMAGE_WIDTH = 100;
    static final int RESIZED_IMAGE_HEIGHT = 75;
    
    // For pre cropping tests
    static final int PRECROP_TOP_LEFT_X_COORDINATE = 50;
    static final int PRECROP_TOP_LEFT_Y_COORDINATE = 50;
    static final int CROPPED_WIDTH = 200;
    static final int CROPPED_HEIGHT = 150;
    
    static String ipCameraToUse = null;
    static boolean cameraOperational = false;
    
    @BeforeClass
    public static void setup() throws Exception {
        if (testAuthToken != null && testVantiqServer != null) {

            vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
            vantiq.setAccessToken(testAuthToken);

            // Add files to be deleted from VANTIQ later
            vantiqUploadFiles.add(IMAGE_1.get("filename"));
            vantiqUploadFiles.add(IMAGE_2.get("filename"));
            vantiqUploadFiles.add(IMAGE_3.get("filename"));
            vantiqUploadFiles.add(IMAGE_4.get("filename"));
            vantiqUploadFiles.add(IMAGE_5.get("filename"));
            vantiqUploadFiles.add(IMAGE_6.get("filename"));
            vantiqUploadFiles.add(IMAGE_7.get("filename"));
            vantiqUploadFiles.add(IMAGE_8.get("filename"));

            try {
                createSourceImpl(vantiq);
            } catch (Exception e) {
                fail("Trapped exception creating source impl: " + e);
            }
            ipCameraToUse = findValidCamera();
            cameraOperational = isIpAccessible(ipCameraToUse);
            createServerConfig();
            setupSource(createSourceDef());
        }
    }

    @SuppressWarnings("PMD.JUnit4TestShouldUseAfterAnnotation")
    @AfterClass
    public static void tearDown() {
        if (core != null) {
            core.stop();
            core = null;
        }
        if (vantiq != null && vantiq.isAuthenticated()) {
            deleteSource(vantiq);

            try {
                deleteSourceImpl(vantiq);
            } catch (Exception e) {
                fail("Trapped exception deleting source impl: " + e);
            }

            for (String vantiqUploadFile : vantiqUploadFiles) {
                deleteFileFromVantiq(vantiqUploadFile);
            }
        }
        deleteDirectory(OUTPUT_DIR);
    }
    
    @After
    public void deleteOldFiles() {
        if (vantiq != null && vantiq.isAuthenticated()) {
            // Deleting all the files from VANTIQ, and deleting local directory for next test
            for (String vantiqUploadFile : vantiqUploadFiles) {
                deleteFileFromVantiq(vantiqUploadFile);
            }
        }
        deleteDirectory(OUTPUT_DIR);
    }
    
    @Test
    public void testInvalidImageUploadParameters() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        invalidParametersHelper(params, "upload");
    }
    
    @Test
    public void testInvalidImageDeleteParameters() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "delete");
        
        invalidParametersHelper(params, "delete");
    }
    
    @Test
    public void testProcessNextFrameLocal() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(cameraOperational);
    
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Run query without setting "operation":"processNextFrame"
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("NNsaveImage", "local");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", IMAGE_8.get("name"));
        
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE_8.get("name") + ".jpg");
        
        // Deleting directory for next test
        deleteDirectory(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Running query with operation set to "processNextFrame"
        params.put("operation", "processNextFrame");
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE_8.get("name") + ".jpg");
        
        // Deleting directory for next test
        deleteDirectory(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Running query with no specific filename set
        params.remove("NNfileName");
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to the last saved file
        outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        
        int index = core.lastQueryFilename.lastIndexOf('/') + 1;
        assert outputDirFiles[0].getName().equals(core.lastQueryFilename.substring(index));
        
        deleteDirectory(OUTPUT_DIR);
    }
    
    @Test
    public void testProcessNextFrameVantiq() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(cameraOperational);
    
        // Run query without setting "operation":"processNextFrame"
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("NNsaveImage", "vantiq");
        params.put("NNfileName", QUERY_FILENAME);
        
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE_8.get("filename"));
        
        // Running query with operation set to "processNextFrame"
        params.put("operation", "processNextFrame");
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE_8.get("filename"));
        
        // Running query with no specific filename set
        params.remove("NNfileName");
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(core.lastQueryFilename, vantiq, VANTIQ_DOCUMENTS);
        
        deleteFileFromVantiq(core.lastQueryFilename);
        vantiqUploadFiles.add(core.lastQueryFilename);
    }
    
    @Test
    public void testProcessNextFrameBoth() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(cameraOperational);

        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Run query without setting "operation":"processNextFrame"
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("NNsaveImage", "both");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", IMAGE_8.get("name"));
        
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE_8.get("name") + ".jpg");
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE_8.get("filename"));
        
        // Deleting directory for next test
        deleteDirectory(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Running query with operation set to "processNextFrame"
        params.put("operation", "processNextFrame");
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE_8.get("name") + ".jpg");
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE_8.get("filename"));
        
        // Deleting directory for next test
        deleteDirectory(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Running query with no specific filename set
        params.remove("NNfileName");
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to the last saved file
        outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        
        int index = core.lastQueryFilename.lastIndexOf('/') + 1;
        assert outputDirFiles[0].getName().equals(core.lastQueryFilename.substring(index));
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(core.lastQueryFilename, vantiq, VANTIQ_DOCUMENTS);
        
        deleteFileFromVantiq(core.lastQueryFilename);
        vantiqUploadFiles.add(core.lastQueryFilename);
        
        deleteDirectory(OUTPUT_DIR);
    }
    
    @Test
    public void testImageNameUploadOne() throws IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        params.put("imageName", IMAGE_2.get("date"));
        
        querySource(params);
        
        // Check the file was uploaded
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    @Test
    public void testImageDateUploadAll() throws IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    @Test
    public void testImageDateUploadOne() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(START_DATE);
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    @Test
    public void testImageDateUploadBefore() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    @Test
    public void testImageDateUploadAfter() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(END_DATE);
        imageDate.add("-");
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    @Test
    public void testImageDateUploadRange() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(3000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    @Test
    public void testImageUploadChangeResolution() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        
        Map<String, Object> savedResolution = new LinkedHashMap<>();
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);
        
        params.put("imageName", IMAGE_2.get("date"));
        params.put("savedResolution", savedResolution);
        
        querySource(params);
        
        // Checking that image was saved to VANTIQ
        Thread.sleep(1000);
        vantiqResponse = vantiq.selectOne("system.documents", IMAGE_2.get("filename"));
        if (vantiqResponse.hasErrors()) {
            List<VantiqError> errors = vantiqResponse.getErrors();
            for (VantiqError error : errors) {
                if (error.getCode().equals(NOT_FOUND_CODE)) {
                    fail();
                }
            }
        }
        
        // Get the path to the saved image in VANTIQ
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
    }
    
    @Test
    public void testImageNameDeleteOne() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "delete");
        params.put("imageName", IMAGE_3.get("date"));
        
        querySource(params);
        
        // Check that directory still exists, and that only the one file inside was deleted
        File outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 7;
        
        for (File file : outputDirFiles) {
            if (file.getName().equals(IMAGE_3.get("date") + ".jpg")) {
                fail("This file should have been deleted.");
            }
        }
        
        deleteDirectory(OUTPUT_DIR);
    }
    
    @Test
    public void testImageDateDeleteAll() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        assert dList != null;
        assert dList.length == 1;
        assert dList[0].getName().equals(IMAGE_8.get("name") + ".jpg");
    }
    
    @Test
    public void testImageDateDeleteBefore() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        assert dList != null;
        assert dList.length == 6;
        
        for (File imageFile: dList) {
            if (imageFile.getName().equals(IMAGE_1.get("filename")) || imageFile.getName().equals(IMAGE_2.get("filename"))) {
                fail("Image should have been deleted locally.");
            }
        }
    }
    
    @Test
    public void ranCameraBasedTests() {
        // This test will fail when all the others are skipped.  This is there to simply alert us that a public
        // camera on which we depend is currently not working.  IF this continues for a while, it's time to find
        // a new public camera.
        assertTrue("Public camera is not working: " + ipCameraToUse, cameraOperational);
    }
    @Test
    public void testImageDateDeleteAfter() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add("-");
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        assert dList != null;
        assert dList.length == 2;
        for (File file : dList) {
            assert file.getName().equals(IMAGE_1.get("date") + ".jpg") || file.getName().equals(IMAGE_8.get("name") + ".jpg");
        }
    }
    
    @Test
    public void testImageDateDeleteRange() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        assert dList != null;
        assert dList.length == 5;
        
        for (File imageFile: dList) {
            if (imageFile.getName().equals(IMAGE_2.get("date") + ".jpg") && imageFile.getName().equals(IMAGE_3.get("date") + ".jpg")
                    && imageFile.getName().equals(IMAGE_4.get("date") + ".jpg")) {
                fail("Images should have been deleted locally.");
            }
        }
    }
    
    @Test
    public void testInvalidPreCroppingQuery() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(cameraOperational);
    
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        Map<String, Object> params = new LinkedHashMap<>();
        
        // Invalid preCrop, it isn't a map
        params.put("cropBeforeAnalysis", "jibberish");
        
        // Run query without setting "operation":"processNextFrame"
        params.put("NNsaveImage", "local");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", "testInvalidPreCrop");
        
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals("testInvalidPreCrop.jpg");
        
        File resizedImageFile = new File(OUTPUT_DIR + "/" + outputDirFiles[0].getName());
        BufferedImage resizedImage = ImageIO.read(resizedImageFile);
        // If we're here, then we can read the image which is what we expect
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testPreCroppingQueryEncode() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> preCrop = new LinkedHashMap<>();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        
        params.put("cropBeforeAnalysis", preCrop);
        
        // Run query without setting "operation":"processNextFrame"
        params.put("NNsaveImage", "local");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", "testPreCrop");
        params.put("includeEncodedImage", true);
        params.put("sendFullResponse", true);
        
        VantiqResponse resp = querySourceWithResponse(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals("testPreCrop.jpg");
        
        File resizedImageFile = new File(OUTPUT_DIR + "/" + outputDirFiles[0].getName());
        byte[] resizedImageBytes = Files.readAllBytes(Paths.get(resizedImageFile.getAbsolutePath()));
        
        ByteArrayInputStream img = new ByteArrayInputStream(resizedImageBytes);
        BufferedImage resizedImage = ImageIO.read(img);
        
        assert resizedImage.getWidth() == CROPPED_WIDTH;
        assert resizedImage.getHeight() == CROPPED_HEIGHT;
        
        // Now, check that this image has been encoded correctly.
        String encodedImage = NeuralNetUtils.convertToBase64(resizedImageBytes);
        
        if (resp.hasErrors()) {
            if (log.isErrorEnabled()) {
                for (VantiqError err : resp.getErrors()) {
                    log.error("Query had errors: {}::{}", err.getCode(), err.getMessage());
                }
            }
        }
        assert resp.isSuccess();
        assert resp.getBody() != null;
        assert resp.getBody() instanceof JsonObject;
        JsonObject responseObj = (JsonObject) resp.getBody();
        assert responseObj.has("encodedImage");
        String returnedImage = responseObj.getAsJsonPrimitive("encodedImage").getAsString();
        if (log.isDebugEnabled()) {
            log.debug("Encoded image size: {} -- returned image size: {}", encodedImage.length(),
                    returnedImage.length());
            if (log.isTraceEnabled()) {
                log.trace("Encoded image: {} -- returned image: {}", encodedImage, returnedImage);
            }
        }
        byte[] retBytes = Base64.getDecoder().decode(returnedImage.getBytes(StandardCharsets.UTF_8));
        try (ByteArrayInputStream retImgStream = new ByteArrayInputStream(retBytes)) {
            BufferedImage retImg = ImageIO.read(retImgStream);
            
            assert retImg.getWidth() == CROPPED_WIDTH;
            assert retImg.getHeight() == CROPPED_HEIGHT;
            assert encodedImage.length() == returnedImage.length();
            assert returnedImage.equals(encodedImage);
        }
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testLocalLabelQueryEncode() throws IOException {
        doLabelTest(true, true, true);
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testLocalLabelNoSaveQueryEncode() throws IOException {
        doLabelTest(true, true, false);
    }
    
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void doLabelTest(boolean localLabelRequest, boolean includeEncoded, boolean saveImage) throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(cameraOperational);
    
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        Map<String, Object> params = new LinkedHashMap<>();
        
        // Run query without setting "operation":"processNextFrame"
        if (saveImage) {
            params.put("NNsaveImage", "local");
            params.put("NNoutputDir", OUTPUT_DIR);
            params.put("NNfileName", "testLabel");
        }
        params.put("includeEncodedImage", includeEncoded);
        params.put("sendFullResponse", true);
        if (localLabelRequest) {
            params.put("labelImage", "true");
        }
        
        VantiqResponse resp = querySourceWithResponse(params);
        if (resp.hasErrors()) {
            if (log.isErrorEnabled()) {
                for (VantiqError err : resp.getErrors()) {
                    log.error("Query had errors: {}::{}", err.getCode(), err.getMessage());
                }
            }
        }
        assert resp.isSuccess();
        assert resp.getBody() != null;
        assert resp.getBody() instanceof JsonObject;
        JsonObject responseObj = (JsonObject) resp.getBody();
        assert responseObj.has("encodedImage") == includeEncoded;
        
        BufferedImage image = null;
        String encodedImage = null;
        if (saveImage) {
            // Check we saved a file in the output directory
            outputDir = new File(OUTPUT_DIR);
            assert outputDir.exists();
            
            // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
            File[] outputDirFiles = outputDir.listFiles();
            assert outputDirFiles != null;
            assert outputDirFiles.length == 1;
            assert outputDirFiles[0].getName().equals("testLabel.jpg");
            
            File imageFile = new File(OUTPUT_DIR + "/" + outputDirFiles[0].getName());
            byte[] imageBytes = Files.readAllBytes(Paths.get(imageFile.getAbsolutePath()));
            
            ByteArrayInputStream img = new ByteArrayInputStream(imageBytes);
            image = ImageIO.read(img);
            // Now, check that this image has been encoded correctly.
            encodedImage = NeuralNetUtils.convertToBase64(imageBytes);
        }
        
        if (includeEncoded) {
            String returnedImage = responseObj.getAsJsonPrimitive("encodedImage").getAsString();
            
            if (encodedImage != null && log.isDebugEnabled()) {
                log.debug("Encoded image size: {} -- returned image size: {}",
                        encodedImage.length(), returnedImage.length());
            }
            byte[] retBytes = Base64.getDecoder().decode(returnedImage.getBytes(StandardCharsets.UTF_8));
            try (ByteArrayInputStream retImgStream = new ByteArrayInputStream(retBytes)) {
                BufferedImage retImg = ImageIO.read(retImgStream);
                
                // In the no-save case, we'll just validate that we got an encoded image back & that we can
                // turn it into an image.
                
                if (image != null) {
                    assert retImg.getWidth() == image.getWidth();
                    assert retImg.getHeight() == image.getHeight();
                    assert (encodedImage.length() == returnedImage.length());
                    assert returnedImage.equals(encodedImage);
                }
            }
        }
    }
    
   @Test
    public void testPreCroppingQuery() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(cameraOperational);
    
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
                
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> preCrop = new LinkedHashMap<>();
        
        preCrop.put("x", PRECROP_TOP_LEFT_X_COORDINATE);
        preCrop.put("y", PRECROP_TOP_LEFT_Y_COORDINATE);
        preCrop.put("width", CROPPED_WIDTH);
        preCrop.put("height", CROPPED_HEIGHT);
        
        params.put("cropBeforeAnalysis", preCrop);
        
        // Run query without setting "operation":"processNextFrame"
        params.put("NNsaveImage", "local");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", "testPreCrop");
                
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles != null;
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals("testPreCrop.jpg");
        
        File resizedImageFile = new File(OUTPUT_DIR + "/" + outputDirFiles[0].getName());
        BufferedImage resizedImage = ImageIO.read(resizedImageFile);
        
        assert resizedImage.getWidth() == CROPPED_WIDTH;
        assert resizedImage.getHeight() == CROPPED_HEIGHT;
    }

    @Test
    public void testUploadAsImage() throws IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        addLocalTestImages();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", "upload");
        params.put("uploadAsImage", true);

        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        params.put("imageDate", imageDate);

        querySource(params);

        // Checking that all images were uploaded to VANTIQ as images
        Thread.sleep(3000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_IMAGES);
        checkUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_IMAGES);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_IMAGES);

        // Checking that the other images were not uploaded to VANTIQ as images
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_IMAGES);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_IMAGES);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_IMAGES);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_IMAGES);

        // Checking that no images were uploaded to VANTIQ as documents
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq, VANTIQ_DOCUMENTS);
    }
    
    // ================================================= Helper functions =================================================
    
    public static void setupSource(Map<String, Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new ObjectRecognitionCore(SOURCE_NAME, testAuthToken, testVantiqServer, MODEL_DIRECTORY);;
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static void querySource(Map<String, Object> params) {
        vantiq.query(SOURCE_NAME, params);
    }

    public static VantiqResponse querySourceWithResponse(Map<String, Object> params) {
        return vantiq.query(SOURCE_NAME, params);
    }
    
    public static void deleteFileFromVantiq(String filename) {
        vantiq.deleteOne(VANTIQ_DOCUMENTS, filename);
        vantiq.deleteOne(VANTIQ_IMAGES, filename);
    }
    
    public void checkQueryError(Map<String, Object> params, String operation) {
        vantiqResponse = vantiq.query(SOURCE_NAME, params);
        assert vantiqResponse.hasErrors();
        String errorMessage = vantiqResponse.getErrors().get(0).getMessage();
        if (!errorMessage.equals("No imageName or imageDate was specified, "
                + "or they were incorrectly specified. Cannot select image(s) to " + operation + ".")) {
            fail("Incorrect error message: " + errorMessage);
        }
    }
    
    public void checkQueryErrorImageDateListSize(Map<String, Object> params) {
        vantiqResponse = vantiq.query(SOURCE_NAME, params);
        assert vantiqResponse.hasErrors();
        String errorMessage = vantiqResponse.getErrors().get(0).getMessage();
        if (!errorMessage.equals("The imageDate value did not contain exactly"
                + " two elements. Must be a list containing only [<yourStartDate>, <yourEndDate>].")) {
            fail("Incorrect error message: " + errorMessage);
        }
    }
    
    public void checkQueryErrorInvalidDate(Map<String, Object> params) {
        vantiqResponse = vantiq.query(SOURCE_NAME, params);
        assert vantiqResponse.hasErrors();
        String errorMessage = vantiqResponse.getErrors().get(0).getMessage();
        if (!errorMessage.equals("One of the dates in the imageDate list could "
                + "not be parsed. Please be sure that both dates are in the following format: yyyy-MM-dd--HH-mm-ss")) {
            fail("Incorrect error message: " + errorMessage);
        }
    }
    
    public void invalidParametersHelper(Map<String, Object> params, String operation) {
        // Not including imageName or imageDate
        checkQueryError(params, operation);
        
        // Using wrong type for imageName
        params.put("imageName", 5);
        checkQueryError(params, operation);
        

        // Using wrong type for imageDate
        params.remove("imageName");
        params.put("imageDate", 5);
        checkQueryError(params, operation);
        
        // Using an imageDate list that is null
        List<String> invalidImageDates = null;
        params.put("imageDate", invalidImageDates);
        checkQueryError(params, operation);
        
        // Using an imageDate list that has no values
        invalidImageDates = new ArrayList<String>();
        params.put("imageDate", invalidImageDates);
        checkQueryErrorImageDateListSize(params);
        
        // Using an imageDate list that contains non-dates
        invalidImageDates.add("Not a date");
        invalidImageDates.add("Also not a date");
        params.put("imageDate", invalidImageDates);
        checkQueryErrorInvalidDate(params);
        
        // Using an imageDate list with only one date
        invalidImageDates.clear();
        invalidImageDates.add(IMAGE_1_DATE);
        params.put("imageDate", invalidImageDates);
        checkQueryErrorImageDateListSize(params);

        // Using an imageDate list with more than two dates
        invalidImageDates.add(IMAGE_2_DATE);
        invalidImageDates.add(IMAGE_3_DATE);
        params.put("imageDate", invalidImageDates);
        checkQueryErrorImageDateListSize(params);
    }
    
    public static Map<String, Object> createSourceDef() {
        Map<String, Object> sourceDef = new LinkedHashMap<>();
        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        Map<String, Object> objRecConfig = new LinkedHashMap<>();
        Map<String, Object> dataSource = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        Map<String, Object> neuralNet = new LinkedHashMap<>();
        
        // Setting up dataSource config options
        dataSource.put("fileLocation", VIDEO_LOCATION);
        dataSource.put("fileExtension", "mov");
        dataSource.put("type", "file");
        
        // Setting up general config options
        general.put("allowQueries", true);
        
        // Setting up neuralNet config options
        neuralNet.put("pbFile", PB_FILE);
        neuralNet.put("metaFile", META_FILE);
        neuralNet.put("type", "yolo");
        neuralNet.put("saveImage", "local");
        neuralNet.put("outputDir", OUTPUT_DIR);
        
        // Placing dataSource, general, and neuralNet config options in "objRecConfig"
        objRecConfig.put("dataSource", dataSource);
        objRecConfig.put("general", general);
        objRecConfig.put("neuralNet", neuralNet);
        
        // Putting objRecConfig in the source configuration
        sourceConfig.put("objRecConfig", objRecConfig);
        
        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", SOURCE_NAME);
        sourceDef.put("type", OR_SRC_TYPE);
        sourceDef.put("active", "true");
        
        return sourceDef;
    }

    @SuppressWarnings({"PMD.DetachedTestCase"})
    public void addLocalTestImages() throws IOException {
        assert new File(OUTPUT_DIR).mkdirs();
        
        byte[] testImageBytes = getTestImage();
        assert testImageBytes != null;
        InputStream in = new ByteArrayInputStream(testImageBytes);
        BufferedImage testImageBuffer = ImageIO.read(in);
        
        File testFile = new File(OUTPUT_DIR + File.separator + IMAGE_1.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_2.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_3.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_4.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_5.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_6.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_7.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_8.get("name") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
    }
}
