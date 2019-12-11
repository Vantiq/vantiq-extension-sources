
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extsrc.objectRecognition.imageRetriever.BasicTestRetriever;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.BasicTestNeuralNet;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

public class TestObjRecConfig {

    ObjectRecognitionConfigHandler handler;
    
    NoSendORCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    String modelDirectory;
    
    Map<String, Object> general;
    Map<String, Object> dataSource;
    Map<String, Object> neuralNet;
    Map<String, Object> postProcessor;

    @BeforeClass
    public static void getProps() {
        assumeTrue("Tests require system property 'buildDir' to be set -- should be objectRecognitionSource/build",
                System.getProperty("buildDir") != null);
    }

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        modelDirectory = "models";
        nCore = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        handler = new ObjectRecognitionConfigHandler(nCore);
        postProcessor = null;
    }
    
    @After
    public void tearDown() {
        nCore.stop();
    }
    
    @Test
    public void testGetImageRetrieverNotAClass() {
        ImageRetrieverInterface ir;
        String className;
        // Should return null when not a valid class
        className = "Not a class";
        ir = handler.getImageRetriever(className);
        assertTrue(ir == null);
        assertTrue("Should fail when not given a valid class name", configIsFailed());
    }
    @Test
    public void testGetImageRetrieverWrongImplementation() {
        ImageRetrieverInterface ir;
        String className;
        // Should return null when not an ImageRetrieverInterface
        className = this.getClass().getCanonicalName();
        ir = handler.getImageRetriever(className);
        assertTrue(ir == null);
        assertTrue("Should fail when not given a class that's not an image retriever", configIsFailed());
    }
    @Test
    public void testGetImageRetrieverValidClass() {
        ImageRetrieverInterface ir;
        String className;
        // Should return an instance of the class when an ImageRetrieverInterface implementation
        className = BasicTestRetriever.class.getCanonicalName();
        ir = handler.getImageRetriever(className);
        assertFalse("Should succeed when given an image retriever", configIsFailed());
        assertTrue(ir instanceof ImageRetrieverInterface);
        assertTrue(ir instanceof BasicTestRetriever);
    }
    
    @Test
    public void testGetNeuralNetNotAClass() {
        NeuralNetInterface nn;
        String className;
        // Should return null when not a valid class
        className = "Not a class";
        nn = handler.getNeuralNet(className);
        assertTrue(nn == null);
        assertTrue("Should fail when not given a valid class name", configIsFailed());
    }
    @Test
    public void testGetNeuralNetWrongImplementation() {
        NeuralNetInterface nn;
        String className;
        // Should return null when not a NeuralNetInterface
        className = this.getClass().getCanonicalName();
        nn = handler.getNeuralNet(className);
        assertTrue(nn == null);
        assertTrue("Should fail when not given a class that's not an image retriever", configIsFailed());
    }
    @Test
    public void testGetNeuralNetValidClass() {
        NeuralNetInterface nn;
        String className;
        // Should return an instance of the class when a NeuralNetInterface implementation
        className = BasicTestNeuralNet.class.getCanonicalName();
        nn = handler.getNeuralNet(className);
        assertFalse("Should succeed when given an image retriever", configIsFailed());
        assertTrue(nn instanceof NeuralNetInterface);
        assertTrue(nn instanceof BasicTestNeuralNet);
    }
    
    @Test
    public void testEmptyConfig() {
        Map conf = new LinkedHashMap<>();
        sendConfig(conf);
        assertTrue("Should fail on empty configuration", configIsFailed());
    }
    
    @Test
    public void testMissingGeneral() {
        Map conf = minimalConfig();
        conf.remove("general");
        sendConfig(conf);
        assertTrue("Should fail when missing 'general' configuration", configIsFailed());
    }
    
    @Test
    public void testMissingDataSource() {
        Map conf = minimalConfig();
        conf.remove("dataSource");
        sendConfig(conf);
        assertTrue("Should fail when missing 'dataSource' configuration", configIsFailed());
    }
    
    @Test
    public void testMissingNeuralNet() {
        Map conf = minimalConfig();
        conf.remove("neuralNet");
        sendConfig(conf);
        assertTrue("Should fail when missing 'neuralNet' configuration", configIsFailed());
    }
    
    @Test
    public void testInvalidThreshold() {
        Map conf = minimalConfig();
        neuralNet.put("threshold", 1000);
        sendConfig(conf);
        assertFalse("Should not fail when threshold is large int.", configIsFailed());
        
        neuralNet.remove("threshold");
        neuralNet.put("threshold", -1);
        sendConfig(conf);
        assertFalse("Should not fail when threshold is negative.", configIsFailed());
    }
    
    @Test
    public void testValidThreshold() {
        Map conf = minimalConfig();
        neuralNet.put("threshold", 0);
        sendConfig(conf);
        assertFalse("Should not fail when threshold is 0.", configIsFailed());
        
        neuralNet.remove("threshold");
        neuralNet.put("threshold", 40);
        sendConfig(conf);
        assertFalse("Should not fail when threshold is greater than 1.", configIsFailed());
        
        neuralNet.remove("threshold");
        neuralNet.put("threshold", 0.6);
        sendConfig(conf);
        assertFalse("Should not fail when threshold is a decimal.", configIsFailed());
    }
    
    @Test
    public void testInvalidThreadConfigs() {
        Map conf = minimalConfig();
        general.put("maxRunningThreads", "jibberish");
        general.put("maxQueuedTasks", "moreJibberish");
        sendConfig(conf);
        assertFalse("Should not fail when thread config options are strings.", configIsFailed());
        
        general.put("maxRunningThreads", -1);
        general.put("maxQueuedTasks", -10);
        sendConfig(conf);
        assertFalse("Should not fail when thread config options are negative.", configIsFailed());
    }
    
    @Test
    public void testValidThreadConfigs() {
        Map conf = minimalConfig();
        general.put("maxRunningThreads", 5);
        general.put("maxQueuedTasks", 10);
        sendConfig(conf);
        assertFalse("Should not fail when maxRunningThreads < maxQueuedTasks.", configIsFailed());
        
        general.put("maxRunningThreads", 10);
        general.put("maxQueuedTasks", 5);
        sendConfig(conf);
        assertFalse("Should not fail when maxRunningThreads > maxQueuedTasks.", configIsFailed());
    }

    @Test
    public void testInvalidImageUploadConfig() {
        Map conf = minimalConfig();
        neuralNet.put("uploadAsImage", "jibberish");
        sendConfig(conf);
        assertFalse("Should not fail when image upload config is a string", configIsFailed());

        neuralNet.put("uploadAsImage", 10);
        sendConfig(conf);
        assertFalse("Should not fail when image upload config is an int", configIsFailed());
    }

    @Test
    public void testValidImageUploadConfig() {
        Map conf = minimalConfig();
        neuralNet.put("uploadAsImage", true);
        sendConfig(conf);
        assertFalse("Should not fail when image upload config is true", configIsFailed());

        neuralNet.put("uploadAsImage", false);
        sendConfig(conf);
        assertFalse("Should not fail when image upload config is false", configIsFailed());
    }
    
    @Test
    public void testMinimalConfig() {
        nCore.start(5); // Need a client to avoid NPEs on sends
        
        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
        
        // Making sure pollTime works
        general.put("pollTime", 300000);
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
        assertTrue("Timer should exist after pollTime set to positive number", nCore.pollTimer != null); 
        
        // Making sure pollRate works (backwards compatibility)
        general.remove("pollTime");
        general.put("pollRate", 300000);
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
        assertTrue("Timer should exist after pollRate set to positive number", nCore.pollTimer != null); 
        
        general.remove("pollRate");
        general.put("allowQueries", true);
        sendConfig(conf);
        assertFalse("Should not fail with minimal configuration", configIsFailed());
    }

    @Test
    public void testEmptyPostProcessor() {
        postProcessor = new HashMap<String, Object>();
        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with empty postProcessor", configIsFailed());
    }

    @Test
    public void testEmptyPPMapper() {
        postProcessor = new HashMap<String, Object>();
        postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, new HashMap<String, Object>());
        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with empty postProcessor", configIsFailed());
    }

    @Test
    public void testBadCoords() {
        postProcessor = new HashMap<String, Object>();
        Map<String, Object> mapper = new HashMap<String,Object>();
        List<Map> imgCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);
        postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, mapper);

        Map conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with missing mapping coordinate sets", configIsFailed());

        List<Map> mappedCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);

        conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with invalid coordinate lists", configIsFailed());

        // Now, let's create some bad coordinate lists...

        for (int i = 0; i <= ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Integer> aCoord = new HashMap<>();
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X + "oops", i);
            mappedCoords.add(aCoord);
        }
        conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with invalid coordinate lists (2)", configIsFailed());

        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, mappedCoords);
        conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with invalid coordinate lists (3)", configIsFailed());

        mappedCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        imgCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);

        for (int i = 0; i < ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Integer> aCoord = new HashMap<>();
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X + "oops", i);
            mappedCoords.add(aCoord);
            imgCoords.add(aCoord);
        }
        conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with invalid coordinate lists (3)", configIsFailed());

        mappedCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        imgCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);

        for (int i = 0; i < ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Integer> aCoord = new HashMap<>();
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X, i);
            mappedCoords.add(aCoord);
            imgCoords.add(aCoord);
        }
        conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with invalid coordinate lists (4)", configIsFailed());

        mappedCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        imgCoords = new ArrayList<>();
        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);

        for (int i = 0; i < ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Object> aCoord = new HashMap<>();
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X, i);
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_Y, i + "oops");
            mappedCoords.add(aCoord);
            imgCoords.add(aCoord);
        }
        conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with invalid coordinate lists (5)", configIsFailed());
    }

    @Test
    public void testInvalidResultSpec() {
        postProcessor = new HashMap<String, Object>();
        Map<String, Object> mapper = new HashMap<String,Object>();
        List<Map> imgCoords = new ArrayList<>();
        List<Map> mappedCoords = new ArrayList<>();

        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, mapper);

        // Now, let's construct some reasonable coordinate lists to validate setup.

        for (int i = 0; i < ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Object> aCoord = new HashMap<>();
            Number xVal = Double.valueOf(i + ".14159");
            Number yVal = i;

            // Verify that either x,y or lat,lon works as expected.
            if ((i % 2) == 0) {
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X, xVal);
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_Y, yVal);
            } else {
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_LONGITUDE, xVal);
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_LATITUDE, String.valueOf(yVal));
            }
            mappedCoords.add(aCoord);
            imgCoords.add(aCoord);
        }

        mapper.put(ObjectRecognitionConfigHandler.RESULTS_AS_GEOJSON, 37);
        Map conf = minimalConfig();
        sendConfig(conf);
        assertTrue("Should fail with non-boolean-ish " + ObjectRecognitionConfigHandler.RESULTS_AS_GEOJSON,
                configIsFailed());
    }

    @Test
    public void testValidPostProcessor() {
        postProcessor = new HashMap<String, Object>();
        Map<String, Object> mapper = new HashMap<String,Object>();
        List<Map> imgCoords = new ArrayList<>();
        List<Map> mappedCoords = new ArrayList<>();

        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, mapper);

        // Now, let's construct some reasonable coordinate lists to validate setup.

        for (int i = 0; i < ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Object> aCoord = new HashMap<>();
            Number xVal = Double.valueOf(i + ".14159");
            Number yVal = i;

            // Verify that either x,y or lat,lon works as expected.
            if ((i % 2) == 0) {
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X, xVal);
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_Y, yVal);
            } else {
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_LONGITUDE, xVal);
                aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_LATITUDE, String.valueOf(yVal));
            }
            mappedCoords.add(aCoord);
            imgCoords.add(aCoord);
        }

        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with valid setup", configIsFailed());

        mapper = new HashMap<String,Object>();
        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        mapper.put(ObjectRecognitionConfigHandler.RESULTS_AS_GEOJSON, true);
        postProcessor = new HashMap<String, Object>();
        postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, mapper);
        conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with valid setup & resultsAsGeoJSON", configIsFailed());

        mapper = new HashMap<String,Object>();
        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        mapper.put(ObjectRecognitionConfigHandler.RESULTS_AS_GEOJSON, "false");
        postProcessor = new HashMap<String, Object>();
        postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, mapper);
        conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with valid setup & resultsAsGeoJSON (false/string)", configIsFailed());
    }
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String, ?> ORConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("objRecConfig", ORConfig);
        obj.put("config", config);
        m.object = obj;
        
        handler.handleMessage(m);
    }
    
    public Map<String, Object> minimalConfig() {
        createMinimalSubConfigs();
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("dataSource", dataSource);
        ret.put("general", general);
        ret.put("neuralNet", neuralNet);
        if (postProcessor != null) {
            ret.put(ObjectRecognitionConfigHandler.POST_PROCESSOR, postProcessor);
        }
        
        return ret;
    }
    
    public void createMinimalSubConfigs() {
        createMinimalGeneral();
        createMinimalDataSource();
        createMinimalNeuralNet();
    }
    
    public void createMinimalGeneral() {
        general = new LinkedHashMap<>();
        general.put("pollRate", 0);
    }
    
    public void createMinimalDataSource() {
        dataSource = new LinkedHashMap<>();
        dataSource.put("type", BasicTestRetriever.class.getCanonicalName());
    }
    
    public void createMinimalNeuralNet() {
        neuralNet = new LinkedHashMap<>();
        neuralNet.put("type", BasicTestNeuralNet.class.getCanonicalName());
    }
    
    public boolean configIsFailed() {
        return handler.isComplete() && nCore.isClosed();
    }
}
