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
    
    static final String START_DATE = IMAGE_2.get("date");
    static final String END_DATE = IMAGE_4.get("date");
    
    static final int RESIZED_IMAGE_WIDTH = 100;
    static final int RESIZED_IMAGE_HEIGHT = 75;
    
    
    @BeforeClass
    public static void setup() {
        core = new NoSendORCore(SOURCE_NAME, testAuthToken, testVantiqServer, MODEL_DIRECTORY);
        core.start(10);
        core.outputDir = OUTPUT_DIR;
        
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
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_6.get("date") + ".jpg");
        ImageIO.write(testImageBuffer, "jpg", testFile);
        
        testFile = new File(OUTPUT_DIR + File.separator + IMAGE_7.get("date") + ".jpg");
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
        
        // Not including imageName or imageDate
        Map<String, Object> request = new LinkedHashMap<>();
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using wrong type for imageName
        request.put("imageName", 5);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using wrong type for imageDate
        request.remove("imageName");
        request.put("imageDate", 5);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using an imageDate list that is null
        List<String> invalidImageDates = null;
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using an imageDate list that has no values
        invalidImageDates = new ArrayList<String>();
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using an imageDate list that contains non-dates
        invalidImageDates.add("Not a date");
        invalidImageDates.add("Also not a date");
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using an imageDate list with only one date
        invalidImageDates.clear();
        invalidImageDates.add(IMAGE_1_DATE);
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Using an imageDate list with more than two dates
        invalidImageDates.add(IMAGE_2_DATE);
        invalidImageDates.add(IMAGE_3_DATE);
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        // Checking that images were not uploaded to VANTIQ
        Thread.sleep(1000);
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
    }
    
    @Test
    public void testInvalidImageDeleteParameters() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        File d = new File(OUTPUT_DIR);
        
        // Not including imageName or imageDate
        Map<String, Object> request = new LinkedHashMap<>();
        core.deleteLocalImages(request, null);
        
        File[] dList = d.listFiles();
        assert dList.length == 7;
        
        
        // Using wrong type for imageName
        request.put("imageName", 5);
        core.deleteLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
        
        
        // Using wrong type for imageDate
        request.remove("imageName");
        request.put("imageDate", 5);
        core.deleteLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
        
        // Using an imageDate list that is null
        List<String> invalidImageDates = null;
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
        
        // Using an imageDate list that has no values
        invalidImageDates = new ArrayList<String>();
        core.uploadLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
        
        // Using an imageDate list that contains non-dates
        invalidImageDates.add("Not a date");
        invalidImageDates.add("Also not a date");
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
        
        // Using an imageDate list with only one date
        invalidImageDates.clear();
        invalidImageDates.add(IMAGE_1_DATE);
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
        
        // Using an imageDate list with more than two dates
        invalidImageDates.add(IMAGE_2_DATE);
        invalidImageDates.add(IMAGE_3_DATE);
        request.put("imageDate", invalidImageDates);
        core.uploadLocalImages(request, null);
        
        dList = d.listFiles();
        assert dList.length == 7;
    }
    
    @Test
    public void testImageNameUploadOne() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageName", START_DATE);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
    }
    
    @Test
    public void testImageDateUploadAll() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add("-");
        request.put("imageDate", imageDate);
        
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_7.get("filename"), vantiq);
    }
    
    @Test
    public void testImageDateUploadOne() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(START_DATE);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
    }
    
    @Test
    public void testImageDateUploadBefore() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add("-");
        imageDate.add(START_DATE);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
    }
    
    @Test
    public void testImageDateUploadAfter() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(END_DATE);
        imageDate.add("-");
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_7.get("filename"), vantiq);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_3.get("filename"), vantiq);
    }
    
    @Test
    public void testImageDateUploadRange() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> imageDate = new ArrayList<String>();
        imageDate.add(START_DATE);
        imageDate.add(END_DATE);
        request.put("imageDate", imageDate);
        
        core.uploadLocalImages(request, null);
                
        // Checking that all images were uploaded to VANTIQ
        Thread.sleep(3000);
        checkUploadToVantiq(IMAGE_2.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_3.get("filename"), vantiq);
        checkUploadToVantiq(IMAGE_4.get("filename"), vantiq);
        
        // Checking that none of the other images were uploaded to VANTIQ
        checkNotUploadToVantiq(IMAGE_1.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_5.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_6.get("filename"), vantiq);
        checkNotUploadToVantiq(IMAGE_7.get("filename"), vantiq);
    }
    
    @Test
    public void testImageUploadChangeResolution() throws InterruptedException, IOException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> savedResolution = new LinkedHashMap<>();
        
        savedResolution.put("longEdge", RESIZED_IMAGE_WIDTH);
        
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
    public void testImageNameDeleteOne() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("imageName", START_DATE);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 6;
        
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
        request.put("imageDate", imageDate);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 6;
        
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
        request.put("imageDate", imageDate);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 5;
        
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
        request.put("imageDate", dateRange);
        
        core.deleteLocalImages(request, null);
        
        File d = new File(OUTPUT_DIR);
        File[] dList = d.listFiles();
        
        assert dList.length == 4;
        
        for (File imageFile: dList) {
            if (!imageFile.getName().equals(IMAGE_1.get("date") + ".jpg") && !imageFile.getName().equals(IMAGE_5.get("date") + ".jpg")
                    && !imageFile.getName().equals(IMAGE_6.get("date") + ".jpg") && !imageFile.getName().equals(IMAGE_7.get("date") + ".jpg")) {
                fail("Images should have been deleted locally.");
            }
        }
    }
}
