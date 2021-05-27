package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;

public class TestNoProcessorQueries extends NeuralNetTestBase {
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;
    static ObjectRecognitionCore core;
    static List<String> vantiqUploadFiles = new ArrayList<>();

    static final int CORE_START_TIMEOUT = 10;
    static final String OUTPUT_DIR = System.getProperty("buildDir") + "/resources/out";
    static final String SOURCE_NAME = "UnlikelyToExistTestObjectRecognitionSource";
    static final String IP_CAMERA_ADDRESS = "http://207.192.232.2:8000/mjpg/video.mjpg";
    static final String QUERY_FILENAME = "testFile";
    static final Map<String,String> IMAGE = new LinkedHashMap<String,String>() {{
        put("filename", "objectRecognition/" + SOURCE_NAME + "/" + QUERY_FILENAME + ".jpg");
        put("name", QUERY_FILENAME);
    }};
    
    @BeforeClass
    public static void setup() {
        if (testVantiqServer != null && testAuthToken != null) {
            vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
            vantiq.setAccessToken(testAuthToken);

            setupSource(createSourceDef());
        }
    }
    
    @AfterClass
    public static void tearDown() {
        if (core != null) {
            core.stop();
            core = null;
        }
        if (vantiq != null && vantiq.isAuthenticated()) {
            deleteSource();

            for (int i = 0; i < vantiqUploadFiles.size(); i++) {
                deleteFileFromVantiq(vantiqUploadFiles.get(i));
            }
        }
        deleteDirectory(OUTPUT_DIR);

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
        params.put("NNfileName", IMAGE.get("name"));
                
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE.get("name") + ".jpg");
        
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
        assert outputDirFiles[0].getName().equals(IMAGE.get("name") + ".jpg");
        
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
        checkUploadToVantiq(IMAGE.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE.get("filename"));
        
        // Running query with operation set to "processNextFrame"
        params.put("operation", "processNextFrame");
        querySource(params);
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE.get("filename"));
        
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
        
        // Make sure that output directory has not yet been created
        File outputDir = new File(OUTPUT_DIR);
        assert !outputDir.exists();
        
        // Run query without setting "operation":"processNextFrame"
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("NNsaveImage", "both");
        params.put("NNoutputDir", OUTPUT_DIR);
        params.put("NNfileName", IMAGE.get("name"));
                
        querySource(params);
        
        // Check we saved a file in the output directory
        outputDir = new File(OUTPUT_DIR);
        assert outputDir.exists();
        
        // Check there is only one file, and it's name is equivalent to QUERY_FILENAME
        File[] outputDirFiles = outputDir.listFiles();
        assert outputDirFiles.length == 1;
        assert outputDirFiles[0].getName().equals(IMAGE.get("name") + ".jpg");
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE.get("filename"));
        
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
        assert outputDirFiles[0].getName().equals(IMAGE.get("name") + ".jpg");
        
        // Check that file was saved to Vantiq
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE.get("filename"), vantiq, VANTIQ_DOCUMENTS);
        
        // Deleting file for next test
        deleteFileFromVantiq(IMAGE.get("filename"));
        
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
        checkUploadToVantiq(core.lastQueryFilename, vantiq, VANTIQ_DOCUMENTS);
        
        deleteFileFromVantiq(core.lastQueryFilename);
        vantiqUploadFiles.add(core.lastQueryFilename);
        
        deleteDirectory(OUTPUT_DIR);
    }

    @Test
    public void testUploadAsImage() throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        // Run query without setting "operation":"processNextFrame"
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("NNsaveImage", "vantiq");
        params.put("uploadAsImage", true);
        params.put("NNfileName", QUERY_FILENAME);

        querySource(params);

        // Check that file was saved to Vantiq as an Image
        Thread.sleep(1000);
        checkUploadToVantiq(IMAGE.get("filename"), vantiq, VANTIQ_IMAGES);

        // Check that it wasn't saved to Vantiq as a Document
        checkNotUploadToVantiq(IMAGE.get("filename"), vantiq, VANTIQ_DOCUMENTS);

        // Deleting file
        deleteFileFromVantiq(IMAGE.get("filename"));
    }
    
    // ================================================= Helper functions =================================================
    
    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new ObjectRecognitionCore(SOURCE_NAME, testAuthToken, testVantiqServer, MODEL_DIRECTORY);;
            core.start(CORE_START_TIMEOUT);
        }
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
        neuralNet.put("type", "none");
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
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
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
        vantiq.deleteOne(VANTIQ_DOCUMENTS, filename);
        vantiq.deleteOne(VANTIQ_IMAGES, filename);
    }
}