
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

public class TestYoloProcessor extends NeuralNetTestBase {
    
    static final String LABEL_FILE         = "coco.names";
    static final String PB_FILE            = "yolo.pb";
    static final String OUTPUT_DIR         = "src/test/resources/out";
    static final int    SAVE_RATE          = 2; // Saves every other so that we can know it counts correctly
    
    static YoloProcessor ypImageSaver;
    static YoloProcessor ypJson;
    
    static final String timestampPattern   = "\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}\\.jpg";
    
    // A single processor is used for the entire class because it is very expensive to do initial setup
    @BeforeClass
    public static void classSetup() {
        assumeTrue("No model file for test. Should be at " + new File(MODEL_DIRECTORY + "/" + PB_FILE).getAbsolutePath() + ""
                , new File(MODEL_DIRECTORY + "/" + PB_FILE).exists());
        
        ypImageSaver = new YoloProcessor();
        ypJson = new YoloProcessor();
        
        Map<String, Object> config = new LinkedHashMap<>();
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
    public void testResults() throws ImageProcessingException {
        List<Map> results = ypJson.processImage(getTestImage());
        assert results != null;
        try {
            resultsEquals(results, getExpectedResults()); // Will throw assert error with a message when not equivalent
        }
        catch (IOException e) {
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
            // Expected exception
        }
        try {
            ypJson.processImage(invalidImage);
            fail("Should throw Exception when not a jpeg");
        } catch (ImageProcessingException e) {
            // Expected results
        }
    }
    
    @Test
    public void testImageSaving() throws ImageProcessingException {
        File d = new File(OUTPUT_DIR);
        try {
            // Ensure no results from previous tests
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            
            List<Map> results = ypImageSaver.processImage(getTestImage());
            assert results != null;
            
            // Should save first image with timestamp
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().matches(timestampPattern);
            
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
            assert d.listFiles()[0].getName().matches(timestampPattern);
            assert d.listFiles()[1].getName().matches(timestampPattern);
            
        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
        }
    }
    
    @Test
    public void testQuery() throws ImageProcessingException {
        String queryOutputDir = OUTPUT_DIR + "query";
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
            List<Map> results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            
            // Should not have saved image
            assert !d.exists();
            
            request.put("NNfileName", queryOutputFile);
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            
            // Should have saved the image at queryOutputFile + ".jpg"
            assert d.exists();
            assert d.isDirectory();
            assert d.listFiles().length == 1;
            assert d.listFiles()[0].getName().equals(queryOutputFile + ".jpg");
            
            request.put("NNoutputDir", queryOutputDir);
            request.remove("NNfileName");
            assert !dNew.exists();
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            
            // Should be saved with a timestamp
            assert dNew.exists();
            assert dNew.isDirectory();
            assert dNew.listFiles().length == 1;
            assert dNew.listFiles()[0].getName().matches(timestampPattern);
            
            queryOutputFile += ".jpeg";
            request.put("NNfileName", queryOutputFile);
            results = ypImageSaver.processImage(getTestImage(), request);
            assert results != null;
            
            assert dNew.listFiles().length == 2;
            for (File f : dNew.listFiles()) {
                String name = f.getName();
                assert name.matches(timestampPattern) || name.equals(queryOutputFile);
            }
            
        } finally {
            // delete the directory even if the test fails
            if (d.exists()) {
                deleteDirectory(OUTPUT_DIR);
            }
            if (dNew.exists()) {
                deleteDirectory(queryOutputDir);
            }
        }
    }
    
// ================================================= Helper functions =================================================
    String imageResultsAsString = "[{\"confidence\":0.8445639, \"location\":{\"top\":254.66667, \"left\":82.87007, \"bottom\":441.9221, "
            + "\"right\":309.81705}, \"label\":\"keyboard\"}, {\"confidence\":0.7516027, \"location\":{\"top\":88.22579, \"left\":52.281204, "
            + "\"bottom\":330.7827, \"right\":429.71838}, \"label\":\"tvmonitor\"}]";
    
    List<Map> getExpectedResults() throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper m = new ObjectMapper();
        return m.readValue(imageResultsAsString, List.class);
    }
    
    void resultsEquals(List<Map> actualRes, List<Map> expectedRes) {
        assert actualRes.size() == expectedRes.size();
        for (int i = 0; i < actualRes.size(); i ++) {
            mapEquals(actualRes.get(i), expectedRes.get(i));
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
