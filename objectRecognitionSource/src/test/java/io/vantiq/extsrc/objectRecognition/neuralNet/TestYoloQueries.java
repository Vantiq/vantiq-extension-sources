package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

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
    static final String SOURCE_NAME = "UnlikelyToExistTestObjectRecognitionSource";
    static final String IP_CAMERA_ADDRESS = "http://207.192.232.2:8000/mjpg/video.mjpg";
    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";
    
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
    static final int IP_CAMERA_WIDTH = 800;
    static final int IP_CAMERA_HEIGHT = 450;

    
    @BeforeClass
    public static void setup() {
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
        
        setupSource(createSourceDef());
    }
    
    @AfterClass
    public static void tearDown() {
        if (core != null) {
            core.stop();
            core = null;
        }
        deleteSource();
        deleteDirectory(OUTPUT_DIR);
        
        for (int i = 0; i < vantiqUploadFiles.size(); i++) {
            deleteFileFromVantiq(vantiqUploadFiles.get(i));
        }
    }
    
    @After
    public void deleteOldFiles() {
        // Deleting all the files from VANTIQ, and deleting local directory for next test
        for (int i = 0; i < vantiqUploadFiles.size(); i++) {
            deleteFileFromVantiq(vantiqUploadFiles.get(i));
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
        
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Run query without setting "operation":"processNextFrame"
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("NNsaveImage", "local");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", IMAGE_8.get("name"));
                
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
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
        assert outputDirFiles.length == 1;
        
        int index = core.lastQueryFilename.lastIndexOf('/') + 1;
        assert outputDirFiles[0].getName().equals(core.lastQueryFilename.substring(index));
        
        deleteDirectory(OUTPUT_DIR);
    }
    
    @Test
    public void testProcessNextFrameVantiq() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        // Run query without setting "operation":"processNextFrame"
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("NNsaveImage", "vantiq");
        params.put("NNfileName", QUERY_FILENAME);
                
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"));
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE_8.get("filename"));
        
        // Running query with operation set to "processNextFrame"
        params.put("operation", "processNextFrame");
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"));
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE_8.get("filename"));
        
        // Running query with no specific filename set
        params.remove("NNfileName");
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(core.lastQueryFilename);
        
        deleteFileFromVantiq(core.lastQueryFilename);
        vantiqUploadFiles.add(core.lastQueryFilename);
    }
    
    @Test
    public void testProcessNextFrameBoth() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Run query without setting "operation":"processNextFrame"
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("NNsaveImage", "both");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", IMAGE_8.get("name"));
                
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE_8.get("name") + ".jpg");
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"));
        
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
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE_8.get("name") + ".jpg");
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_8.get("filename"));
        
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
        assert outputDirFiles.length == 1;
        
        int index = core.lastQueryFilename.lastIndexOf('/') + 1;
        assert outputDirFiles[0].getName().equals(core.lastQueryFilename.substring(index));
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(core.lastQueryFilename);
        
        deleteFileFromVantiq(core.lastQueryFilename);
        vantiqUploadFiles.add(core.lastQueryFilename);
        
        deleteDirectory(OUTPUT_DIR);
    }
    
    @Test
    public void testImageNameUploadOne() throws IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "upload");
        params.put("imageName", IMAGE_2.get("date"));
        
        querySource(params);
        
        // Check the file was uploaded
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
        checkNotUploadToVantiq(IMAGE_6.get("filename"));
        checkNotUploadToVantiq(IMAGE_7.get("filename"));
    }
    
    @Test
    public void testImageDateUploadAll() throws IOException, InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        params.put("imageDate", imageDate);
                
        querySource(params);
        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"));
        checkUploadToVantiq(IMAGE_2.get("filename"));
        checkUploadToVantiq(IMAGE_3.get("filename"));
        checkUploadToVantiq(IMAGE_4.get("filename"));
        checkUploadToVantiq(IMAGE_5.get("filename"));
        checkUploadToVantiq(IMAGE_6.get("filename"));
        checkUploadToVantiq(IMAGE_7.get("filename"));
    }
    
    @Test
    public void testImageDateUploadOne() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(START_DATE);
        params.put("imageDate", imageDate);
                
        querySource(params);
                        
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
        checkNotUploadToVantiq(IMAGE_6.get("filename"));
        checkNotUploadToVantiq(IMAGE_7.get("filename"));
    }
    
    @Test
    public void testImageDateUploadBefore() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        params.put("imageDate", imageDate);
                
        querySource(params);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"));
        checkUploadToVantiq(IMAGE_2.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
        checkNotUploadToVantiq(IMAGE_6.get("filename"));
        checkNotUploadToVantiq(IMAGE_7.get("filename"));
    }
    
    @Test
    public void testImageDateUploadAfter() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(END_DATE);
        imageDate.add("-");
        params.put("imageDate", imageDate);
                
        querySource(params);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_4.get("filename"));
        checkUploadToVantiq(IMAGE_5.get("filename"));
        checkUploadToVantiq(IMAGE_6.get("filename"));
        checkUploadToVantiq(IMAGE_7.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
    }
    
    @Test
    public void testImageDateUploadRange() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "upload");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        params.put("imageDate", imageDate);
                
        querySource(params);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(3000);
        checkUploadToVantiq(IMAGE_2.get("filename"));
        checkUploadToVantiq(IMAGE_3.get("filename"));
        checkUploadToVantiq(IMAGE_4.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
        checkNotUploadToVantiq(IMAGE_6.get("filename"));
        checkNotUploadToVantiq(IMAGE_7.get("filename"));
    }
    
    @Test
    public void testImageUploadChangeResolution() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
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
            for (int i = 0; i < errors.size(); i++) {
                if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
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
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "delete");
        params.put("imageName", IMAGE_3.get("date"));
        
        querySource(params);
        
        // Check that directory still exists, and that only the one file inside was deleted
        File outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        File[] outputDirFiles = outputDir.listFiles();
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
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 1;
        assert dList[0].getName().equals(IMAGE_8.get("name") + ".jpg");
    }
    
    @Test
    public void testImageDateDeleteBefore() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 6;
        
        for (File imageFile: dList) {
            if (imageFile.getName().equals(IMAGE_1.get("filename")) || imageFile.getName().equals(IMAGE_2.get("filename"))) {
                fail("Image should have been deleted locally.");
            }
        }
    }
    
    @Test
    public void testImageDateDeleteAfter() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        addLocalTestImages();
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add("-");
        
        params.put("imageDate", imageDate);
        
        querySource(params);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
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
        
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "delete");
        
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        
        params.put("imageDate", imageDate);
        
        querySource(params);

        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
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
        
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
                
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        
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
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals("testInvalidPreCrop.jpg");
        
        File resizedImageFile = new File(OUTPUT_DIR + "/" + outputDirFiles[0].getName());
        BufferedImage resizedImage = ImageIO.read(resizedImageFile);
        
        assert resizedImage.getWidth() == IP_CAMERA_WIDTH;
        assert resizedImage.getHeight() == IP_CAMERA_HEIGHT;
    }
    
    @Test
    public void testPreCroppingQuery() throws IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
                
        Map<String,Object> params = new LinkedHashMap<String,Object>();
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
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals("testPreCrop.jpg");
        
        File resizedImageFile = new File(OUTPUT_DIR + "/" + outputDirFiles[0].getName());
        BufferedImage resizedImage = ImageIO.read(resizedImageFile);
        
        assert resizedImage.getWidth() == CROPPED_WIDTH;
        assert resizedImage.getHeight() == CROPPED_HEIGHT;
    }
    
    // ================================================= Helper functions =================================================
    
    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new ObjectRecognitionCore(SOURCE_NAME, testAuthToken, testVantiqServer, MODEL_DIRECTORY);;
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static void querySource(Map<String,Object> params) {
        vantiq.query(SOURCE_NAME, params);
    }
    
    public static void deleteSource() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", SOURCE_NAME);
        vantiq.delete("system.sources", where);
    }
    
    public static void deleteFileFromVantiq(String filename) {
        vantiq.deleteOne("system.documents", filename);
    }
    
    public void checkUploadToVantiq(String name) {
        vantiqResponse = vantiq.selectOne("system.documents", name);
        if (vantiqResponse.hasErrors()) {
            List<VantiqError> errors = vantiqResponse.getErrors();
            for (int i = 0; i < errors.size(); i++) {
                if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                    fail("Image should have been uploaded to VANTIQ");
                }
            }
        }
    }
    
    public void checkNotUploadToVantiq(String name) {
        vantiqResponse = vantiq.selectOne("system.documents", name);
        if (vantiqResponse.isSuccess()) {
            fail("Image should not have been uploaded to VANTIQ");
        }
    }
    
    public void checkQueryError(Map<String,Object> params, String operation) {
        vantiqResponse = vantiq.query(SOURCE_NAME, params);
        assert vantiqResponse.hasErrors();
        assert vantiqResponse.getErrors().get(0).getMessage().equals("No imageName or imageDate was specified, "
                + "or they were incorrectly specified. Cannot select image(s) to " + operation + ".");
    }
    
    public void checkQueryErrorImageDateListSize(Map<String,Object> params) {
        vantiqResponse = vantiq.query(SOURCE_NAME, params);
        assert vantiqResponse.hasErrors();
        assert vantiqResponse.getErrors().get(0).getMessage().equals("The imageDate value did not contain exactly"
                + " two elements. Must be a list containing only [<yourStartDate>, <yourEndDate>].");
    }
    
    public void checkQueryErrorInvalidDate(Map<String,Object> params) {
        vantiqResponse = vantiq.query(SOURCE_NAME, params);
        assert vantiqResponse.hasErrors();
        assert vantiqResponse.getErrors().get(0).getMessage().equals("One of the dates in the imageDate list could "
                + "not be parsed. Please be sure that both dates are in the following format: yyyy-MM-dd--HH-mm-ss");
    }
    
    public void invalidParametersHelper(Map<String,Object> params, String operation) {
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
    
    public static Map<String,Object> createSourceDef() {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> objRecConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> dataSource = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        Map<String,Object> neuralNet = new LinkedHashMap<String,Object>();
        
        // Setting up dataSource config options
        dataSource.put("camera", IP_CAMERA_ADDRESS);
        dataSource.put("type", "network");
        
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
        sourceDef.put("type", "ObjectRecognition");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
    }
    
    public void addLocalTestImages() throws IOException {
        new File(OUTPUT_DIR).mkdirs();
        
        byte[] testImageBytes = getTestImage();
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
