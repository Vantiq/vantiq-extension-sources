package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestYoloProcessor extends NeuralNetTestBase {
    
    static final String LABEL_FILE         = "coco.names";
    static final String PB_FILE            = "yolo.pb";
    static final String OUTPUT_DIR         = "src/test/resources/out";
    static final int    SAVE_RATE          = 2; // Saves every other so that we can know it counts correctly
    
    static YoloProcessor ypImageSaver;
    static YoloProcessor ypJson;
    
    // A single processor is used for the entire class because it is very expensive to do initial setup
    @BeforeClass
    public static void classSetup() {
        assumeTrue("No model file for test. Should be at " + new File(MODEL_DIRECTORY + "/" + PB_FILE).getAbsolutePath() + ""
                , new File(MODEL_DIRECTORY + "/" + PB_FILE).exists());
        
        ypImageSaver = new YoloProcessor();
        ypJson = new YoloProcessor();
        
        Map<String,Object> config = new LinkedHashMap<>();
        config.put("pbFile", PB_FILE);
        config.put("labelFile", LABEL_FILE);
        try {
            ypJson.setupImageProcessing(config, MODEL_DIRECTORY);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
        
        config.put("outputDir", OUTPUT_DIR);
        config.put("saveRate", SAVE_RATE);
        try {
            ypImageSaver.setupImageProcessing(config, MODEL_DIRECTORY);
        } catch (Exception e) {
            fail("Could not setup the JSON YoloProcessor");
        }
    }
    
    @AfterClass
    public static void classTearDown() {
        ypImageSaver.close();
        ypImageSaver = null;
        
        ypJson.close();
        ypJson = null;
        
        File d = new File(OUTPUT_DIR);
        if (d.exists()) {
            deleteDirectory(OUTPUT_DIR);
        }
    }
    
    @Test
    public void testPureJson() {
        List<Map> results = ypJson.processImage(getTestImage());
        assert results != null;
    }
    
    @Test
    public void testImageSaving() {
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            
            List<Map> results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            
            // Should save first image
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            
            results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            
            // Every other so second should not save
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            
            results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            
            // Every other so third and first should be saved
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 2;
            
        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
        }
    }
}
