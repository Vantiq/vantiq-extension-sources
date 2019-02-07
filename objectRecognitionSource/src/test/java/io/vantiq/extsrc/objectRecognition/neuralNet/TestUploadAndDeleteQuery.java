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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.NoSendORCore;
import okhttp3.Response;
import okio.BufferedSource;

public class TestUploadAndDeleteQuery extends NeuralNetTestBase {
    static NoSendORCore core;
    
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;
    static List<String> vantiqUploadFiles = new ArrayList<>();
    
    static final String OUTPUT_DIR = System.getProperty("buildDir") + "/resources/out";
    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";
    
    static final Map<String,String> IMAGE_1 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/2019-02-05--02-35-10.jpg");
        put("date", "2019-02-05--02-35-10");
    }};
    static final Map<String,String> IMAGE_2 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/2019-02-05--02-35-13.jpg");
        put("date", "2019-02-05--02-35-13");
    }};
    static final Map<String,String> IMAGE_3 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/2019-02-05--02-35-16.jpg");
        put("date", "2019-02-05--02-35-16");
    }};
    static final Map<String,String> IMAGE_4 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/2019-02-05--02-35-19.jpg");
        put("date", "2019-02-05--02-35-19");
    }};
    static final Map<String,String> IMAGE_5 = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/2019-02-05--02-35-22.jpg");
        put("date", "2019-02-05--02-35-22");
    }};
    
    static final String START_DATE = IMAGE_2.get("date");
    static final String END_DATE = IMAGE_4.get("date");
    
    static final int RESIZED_IMAGE_WIDTH = 100;
    static final int RESIZED_IMAGE_HEIGHT = 75;
    
    
    @BeforeClass
    public static void setup() {
        testAuthToken="-YyPeih6BkZoQoVa5tUT3cMZ4DXaWs7M6hg26WEdU88=";
        testVantiqServer="https://dev.vantiq.com";
        core = new NoSendORCore(SOURCE_NAME, testAuthToken, testVantiqServer, MODEL_DIRECTORY);
        core.start(10);
        
        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
        
        // Add files to be deleted from VANTIQ later
        vantiqUploadFiles.add(IMAGE_1.get("filename"));
        vantiqUploadFiles.add(IMAGE_2.get("filename"));
        vantiqUploadFiles.add(IMAGE_3.get("filename"));
        vantiqUploadFiles.add(IMAGE_4.get("filename"));
        vantiqUploadFiles.add(IMAGE_5.get("filename"));
    }
    
    @Before
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
    }
    
    @AfterClass
    public static void tearDown() {
        core.stop();
    }
    
    @After
    public void deleteFromVantiq() throws InterruptedException {
        for (int i = 0; i < vantiqUploadFiles.size(); i++) {
            Thread.sleep(1000);
            vantiq.deleteOne("system.documents", vantiqUploadFiles.get(i), new BaseResponseHandler() {

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
        
        File d = new File(OUTPUT_DIR);
        if (d.exists()) {
            deleteDirectory(OUTPUT_DIR);
        }
    }
    
    @Test
    public void testInvalidImageUploadParameters() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        // Forgetting to include imageDir
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageName", "all");
        core.uploadLocalImages(request, null);
                
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));  
        
        // Using wrong type for imageDir
        request.put("imageDir", 5);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename")); 
        
        // Forgetting to include either imageName or imageDate
        request.put("imageDir", OUTPUT_DIR);
        request.remove("imageName");
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename")); 
        
        // Using wrong type for imageName
        request.put("imageName", 5);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename")); 
        
        // Using wrong type for imageDate
        request.remove("imageName");
        request.put("imageDate", 5);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename")); 
    }
    
    @Test
    public void testInvalidImageDeleteParameters() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        File d = new File(OUTPUT_DIR);
        
        // Forgetting to include imageDir
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageName", "all");
        core.deleteLocalImages(request, null); 
        
        File[] dList = d.listFiles();
        assert dList.length == 5;
        
        // Using wrong type for imageDir
        request.put("imageDir", 5);
        core.deleteLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 5;
        
        
        // Forgetting to include either imageName or imageDate
        request.put("imageDir", OUTPUT_DIR);
        request.remove("imageName");
        core.deleteLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 5;
        
        
        // Using wrong type for imageName
        request.put("imageName", 5);
        core.deleteLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 5;
        
        
        // Using wrong type for imageDate
        request.remove("imageName");
        request.put("imageDate", 5);
        core.deleteLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 5;
    }
    
    @Test
    public void testImageNameUploadAll() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageName", "all");
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"));
        checkUploadToVantiq(IMAGE_2.get("filename"));
        checkUploadToVantiq(IMAGE_3.get("filename"));
        checkUploadToVantiq(IMAGE_4.get("filename"));
        checkUploadToVantiq(IMAGE_5.get("filename"));         
    }
    
    @Test
    public void testImageNameUploadOne() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageName", START_DATE);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename")); 
        
    }
    
    @Test
    public void testImageDateUploadAll() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"));
        checkUploadToVantiq(IMAGE_2.get("filename"));
        checkUploadToVantiq(IMAGE_3.get("filename"));
        checkUploadToVantiq(IMAGE_4.get("filename"));
        checkUploadToVantiq(IMAGE_5.get("filename"));   
    }
    
    @Test
    public void testImageDateUploadOne() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(START_DATE);
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
    }
    
    @Test
    public void testImageDateUploadBefore() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"));
        checkUploadToVantiq(IMAGE_2.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
        checkNotUploadToVantiq(IMAGE_4.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
    }
    
    @Test
    public void testImageDateUploadAfter() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(END_DATE);
        imageDate.add("-");
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_4.get("filename"));
        checkUploadToVantiq(IMAGE_5.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_2.get("filename"));
        checkNotUploadToVantiq(IMAGE_3.get("filename"));
    }
    
    @Test
    public void testImageDateUploadRange() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(3000);
        checkUploadToVantiq(IMAGE_2.get("filename"));
        checkUploadToVantiq(IMAGE_3.get("filename"));
        checkUploadToVantiq(IMAGE_4.get("filename"));
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"));
        checkNotUploadToVantiq(IMAGE_5.get("filename"));
    }
    
    @Test
    public void testImageUploadChangeResolution() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> savedResolution = new LinkedHashMap<>();
        
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);
        
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageName", START_DATE);
        request.put("savedResolution", savedResolution);
        
        core.uploadLocalImages(request, null);
        
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
    public void testImageNameDeleteAll() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageName", "all");
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 0;
    }   
    
    @Test
    public void testImageNameDeleteOne() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageName", START_DATE);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 4;
        
        for (File imageFile: dList) {
            if (imageFile.getName().equals(IMAGE_2.get("filename"))) {
                fail("Image should have been deleted locally.");
            }
        }
    }
    
    @Test
    public void testImageDateDeleteAll() {
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 0;
    }
    
    @Test
    public void testImageDateDeleteOne() {
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(START_DATE);
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 4;
        
        for (File imageFile: dList) {
            if (imageFile.getName().equals(IMAGE_2.get("filename"))) {
                fail("Image should have been deleted locally.");
            }
        }
    }
    
    @Test
    public void testImageDateDeleteBefore() {
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 3;
        
        for (File imageFile: dList) {
            if (imageFile.getName().equals(IMAGE_1.get("filename")) || imageFile.getName().equals(IMAGE_2.get("filename"))) {
                fail("Image should have been deleted locally.");
            }
        }
    }
    
    @Test
    public void testImageDateDeleteAfter() {
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add("-");
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", imageDate);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 1;
        assert dList[0].getName().equals(IMAGE_1.get("date") + ".jpg");
    }
    
    @Test
    public void testImageDateDeleteRange() {
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> dateRange = new ArrayList<String>();
        dateRange.add(START_DATE);
        dateRange.add(END_DATE);
        request.put("imageDir", OUTPUT_DIR);
        request.put("imageDate", dateRange);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 2;
        
        for (File imageFile: dList) {
            if (!imageFile.getName().equals(IMAGE_1.get("date") + ".jpg") && !imageFile.getName().equals(IMAGE_5.get("date") + ".jpg")) {
                fail("Images should have been deleted locally.");
            }
        }
    }
    
    // ================================================= Helper functions =================================================
    
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
}
