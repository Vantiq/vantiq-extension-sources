
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.imageRetriever.TestCoordConverter;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extsrc.objectRecognition.imageRetriever.BasicTestRetriever;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverResults;
import io.vantiq.extsrc.objectRecognition.neuralNet.BasicTestNeuralNet;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetResults;

@Slf4j
@SuppressWarnings({"WeakerAccess"})
public class TestObjRecCore extends ObjRecTestBase {

    static final Double ACCEPTABLE_DELTA = 0.0001d;
    
    NoSendORCore core;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    String modelDirectory;
    
    ImageRetrieverInterface retriever;
    NeuralNetInterface      neuralNet;

    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        modelDirectory = "models";
        
        retriever = new BasicTestRetriever();
        neuralNet = new BasicTestNeuralNet();
        
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        
        core.imageRetriever = retriever;
        core.neuralNet = neuralNet;
        
        core.start(10);
    }
    
    @After
    public void tearDown() {
        core.stop();
    }
    
    @Test
    public void testRetrieveImage() {
        assumeTrue("Can't test retrieveImage without file to retrieve", new File(JPEG_IMAGE_LOCATION).exists());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(null));
        ImageRetrieverResults retrieverResults; 
        byte[] data;
        retrieverResults = core.retrieveImage();
        data = retrieverResults.getImage();
        assert data != null && data.length > 1; 
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.RETURN_NULL));
        retrieverResults = core.retrieveImage();
        assert retrieverResults == null; 
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.THROW_EXCEPTION_ON_REQ));
        retrieverResults = core.retrieveImage();
        assert retrieverResults == null;
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.THROW_FATAL_ON_REQ));
        retrieverResults = core.retrieveImage();
        assert retrieverResults == null;
        assertTrue("Core should be closed after fatal error", core.isClosed());
        
        core.closed = false;
        core.imageRetriever = retriever;
        core.start(5);
        assertFalse("Resetting closed status failed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.THROW_RUNTIME_ON_REQ));
        retrieverResults = core.retrieveImage();
        assert retrieverResults == null;
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    @Test
    public void testRetrieveImageQuery() {
        assumeTrue("Can't test retrieveImage without file to retrieve", new File(JPEG_IMAGE_LOCATION).exists());
        
        Map<String, Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        byte[] data;
        ImageRetrieverResults retrieverResults; 
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(null));
        request = new LinkedHashMap<>();
        request.put(BasicTestRetriever.RETURN_NULL, null);
        msg.object = request;
        retrieverResults = core.retrieveImage(msg);
        assert retrieverResults == null;
        assertFalse("Core should not be closed", core.isClosed());
        
        request = new LinkedHashMap<>();
        request.put(BasicTestRetriever.THROW_EXCEPTION_ON_REQ, null);
        msg.object = request;
        retrieverResults = core.retrieveImage(msg);
        assert retrieverResults == null;
        assertFalse("Core should not be closed", core.isClosed());
        
        request = new LinkedHashMap<>();
        msg.object = request;
        retrieverResults = core.retrieveImage(msg);
        assert retrieverResults != null;
        data = retrieverResults.getImage();
        assert data != null && data.length > 1;
        assertFalse("Core should not be closed", core.isClosed());
        
        request = new LinkedHashMap<>();
        request.put(BasicTestRetriever.THROW_FATAL_ON_REQ, null);
        msg.object = request;
        retrieverResults = core.retrieveImage(msg);
        assert retrieverResults == null;
        assertTrue("Core should be closed after fatal error", core.isClosed());
        
        core.closed = false;
        core.imageRetriever = retriever;
        core.start(5);
        assertFalse("Resetting closed status failed", core.isClosed());
        
        msg.object = null;
        retrieverResults = core.retrieveImage(msg);
        assert retrieverResults == null;
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }

    // Verify core operation, not really doing much.
    public void performProcessImageTest(boolean doCoordConversion, boolean convertToJson) {
        final LocalImageRetrieverResults imageData;
        Map sentMsg;
        byte[] lastBytes;
        try {
            imageData = new LocalImageRetrieverResults(Files.readAllBytes(new File(JPEG_IMAGE_LOCATION).toPath()));
        } catch (IOException e) {
            assumeFalse("Could not read image for the test", true);
            return; // Never reaches, just silences imageData not initialized errors
        }

        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_EXCEPTION_ON_REQ));
        if (doCoordConversion) {
            core.createLocationMapper(plus50SrcPts, plus50DstPts, convertToJson);
        }
        core.sendDataFromImage(imageData);
        lastBytes = core.fClient.getLastMessageAsBytes();
        assert lastBytes == null;
        assertFalse("Core should not be closed", core.isClosed());

        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(null));
        core.sendDataFromImage(imageData);
        sentMsg = core.fClient.getLastMessageAsMap();
        assert sentMsg.get("op").equals(ExtensionServiceMessage.OP_NOTIFICATION);
        assert sentMsg.get("object") instanceof Map;
        Map<String, ?> sentObj = (Map) sentMsg.get("object");
        assert sentObj.get("results") instanceof List;
        if (doCoordConversion) {
            assert sentObj.get("mappedResults") != null;
            assert sentObj.get("mappedResults") instanceof List;
            assert ((List) sentObj.get("mappedResults")).size() == ((List) sentObj.get("results")).size();
        }
        assert sentObj.get("dataSource") instanceof Map;
        assert sentObj.get("neuralNet") instanceof Map;
        assertFalse("Core should not be closed", core.isClosed());

        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_FATAL_ON_REQ));
        core.sendDataFromImage(imageData);
        assertTrue("Core should be closed after fatal error", core.isClosed());

        core.closed = false;
        core.neuralNet = neuralNet;
        core.start(5);
        assertFalse("Resetting closed status failed", core.isClosed());

        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_RUNTIME_ON_REQ));
        core.sendDataFromImage(imageData);
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    @Test
    public void testProcessImage() throws IOException {
        performProcessImageTest(false, false);
    }

    @Test
    public void testProcessImageCoordConverter() throws IOException {
        performProcessImageTest(true, false);
    }

    @Test
    public void testProcessImageCoordConverterGeoJson() throws IOException {
        performProcessImageTest(true, true);
    }
    
    @Test
    public void testProcessImageQuery() {
        final LocalImageRetrieverResults imageData;
        Map sentMsg;
        try {
            imageData = new LocalImageRetrieverResults(Files.readAllBytes(new File(JPEG_IMAGE_LOCATION).toPath()));
        } catch (IOException e) {
            assumeFalse("Could not read image for the test", true);
            return; // Never reaches, just silences imageData not initialized errors
        }
        
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String, String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        msg.object = new LinkedHashMap<>();
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_EXCEPTION_ON_REQ));
        core.sendDataFromImage(imageData, msg);
        sentMsg = core.fClient.getLastMessageAsMap();
        assert !(sentMsg.get("body") instanceof List);
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(null));
        core.sendDataFromImage(imageData, msg);
        sentMsg = core.fClient.getLastMessageAsMap();
        assert sentMsg.get("body") instanceof List;
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_FATAL_ON_REQ));
        core.sendDataFromImage(imageData, msg);
        assertTrue("Core should be closed after fatal error", core.isClosed());
        
        core.closed = false;
        core.neuralNet = neuralNet;
        core.start(5);
        assertFalse("Resetting closed status failed", core.isClosed());
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_RUNTIME_ON_REQ));
        core.sendDataFromImage(imageData, msg);
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    @Test
    public void testSourceNameAndFilenameInResults() {
        LocalImageRetrieverResults imageResults;
        LocalNeuralNetResults neuralNetResults = new LocalNeuralNetResults();
        Map<String,String> imageOtherData = new LinkedHashMap<>();
        Map<String,String> neuralOtherData = new LinkedHashMap<>();
        
        // Initialize image results
        try {
            imageResults = new LocalImageRetrieverResults(Files.readAllBytes(new File(JPEG_IMAGE_LOCATION).toPath()));
        } catch (IOException e) {
            assumeFalse("Could not read image for the test", true);
            return; // Never reaches, just silences imageData not initialized errors
        }
        
        // Add fake data into "otherData" maps
        imageOtherData.put("jibberish", "moreJibberish");
        neuralOtherData.put("jibberish", "moreJibberish");
        
        // Set otherData for both results
        imageResults.setOtherData(imageOtherData);
        neuralNetResults.setOtherData(neuralOtherData);
        
        // Set last filename for neuralNetResults to ensure it is being sent back to VANTIQ Source
        neuralNetResults.setLastFilename("testFilename");
        
        // Make sure that source name and filename are included in map from results
        Map<String, Object> testMapFromResults = core.createMapFromResults(imageResults, neuralNetResults);
        assert testMapFromResults.get("sourceName").equals("src");
        assert testMapFromResults.get("filename").equals("testFilename");
        
        // If lastFilename is null, no "filename" field should be sent back to VANTIQ Source
        neuralNetResults.setLastFilename(null);
        testMapFromResults = core.createMapFromResults(imageResults, neuralNetResults);
        assert testMapFromResults.get("filename") == null;
    }
    
    @Test
    public void testExitIfConnectionFails() {
        core.start(3);
        assertTrue("Should have succeeded", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Success means it shouldn't be closed", core.isClosed());

        core.close();
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to authentication failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to WebSocket failing", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(core.client, 3));
        assertFalse("Failure does not mean it should be closed", core.isClosed());
    }

    @Test
    public void testLocationMapperPlain() {
        assertNull("Should not have a location mapper", core.locationMapper);

        core.createLocationMapper(plus50SrcPts, plus50DstPts);
        assertNotNull("Should now have a location mapper", core.locationMapper);

        // now, create some test data...

        List<Map<String, ?>> testinp = createTestData();


        List<Map<String, ?>> res = core.locationMapper.mapResults(testinp);
        // Check output...
        assert res.size() == testinp.size();
        for (int i = 0; i < res.size(); i++ ) {
            assertEquals("bad label value", testinp.get(i).get("label"), res.get(i).get("label"));
            assertEquals("bad confidence value", testinp.get(i).get("confidence"), res.get(i).get("confidence"));
            @SuppressWarnings({"unchecked"})
            Map<String, Double> inloc = (Map<String, Double>) testinp.get(i).get("location");
            @SuppressWarnings({"unchecked"})
            Map<String, Double> resloc = (Map<String, Double>) res.get(i).get("location");

            assertEquals("mapped top", 
                    inloc.get("top") + 50d, resloc.get("top"),ACCEPTABLE_DELTA);
            assertEquals("mapped left", 
                    inloc.get("left") + 50d, resloc.get("left"), ACCEPTABLE_DELTA);
            assertEquals("mapped bottom", 
                    inloc.get("bottom") + 50d, resloc.get("bottom"), ACCEPTABLE_DELTA);
            assertEquals("mapped right", 
                    inloc.get("right") + 50d, resloc.get("right"), ACCEPTABLE_DELTA);
            assertEquals("mapped centerX",
                    inloc.get("centerX") + 50d, resloc.get("centerX"), ACCEPTABLE_DELTA);
            assertEquals("mapped centerY",
                    inloc.get("centerY") + 50d, resloc.get("centerY"), ACCEPTABLE_DELTA);
            assertEquals("Wrong number of locations in result", inloc.size(), resloc.size());

        }

        core.createLocationMapper(doubleSrcPts, doubleDstPts);
        assertNotNull("Should now have a location mapper", core.locationMapper);

        // now, create some test data...
        testinp = createTestData();

        res = core.locationMapper.mapResults(testinp);
        // Check output...
        assert res.size() == testinp.size();
        for (int i = 0; i < res.size(); i++ ) {
            assertEquals("bad label value", testinp.get(i).get("label"), res.get(i).get("label"));
            assertEquals("bad confidence value", testinp.get(i).get("confidence"), res.get(i).get("confidence"));
            @SuppressWarnings({"unchecked"})
            Map<String, Double> inloc = (Map<String, Double>) testinp.get(i).get("location");
            @SuppressWarnings({"unchecked"})
            Map<String, Double> resloc = (Map<String, Double>) res.get(i).get("location");

            assertEquals("mapped top", 
                    inloc.get("top") * 2d, resloc.get("top"), ACCEPTABLE_DELTA);
            assertEquals("mapped left", 
                    inloc.get("left") * 2d, resloc.get("left"), ACCEPTABLE_DELTA);
            assertEquals("mapped bottom", 
                    inloc.get("bottom") * 2d, resloc.get("bottom"), ACCEPTABLE_DELTA);
            assertEquals("mapped bottom", 
                    inloc.get("bottom") * 2d, resloc.get("bottom"), ACCEPTABLE_DELTA);
            assertEquals("mapped centerX",
                    inloc.get("centerX") * 2d, resloc.get("centerX"), ACCEPTABLE_DELTA);
            assertEquals("mapped centerY",
                    inloc.get("centerY") * 2d, resloc.get("centerY"), ACCEPTABLE_DELTA);
            assertEquals("Wrong number of locations in result", inloc.size(), resloc.size());
        }
    }

    @Test
    public void testLocationMapperGeoJSON() {
        assertNull("Should not have a location mapper", core.locationMapper);

        core.createLocationMapper(plus50SrcPts, plus50DstPts, true);
        assertNotNull("Should now have a location mapper", core.locationMapper);

        // now, create some test data...
        List<Map<String, ?>> testinp = createTestData();

        List<Map<String, ?>> res = core.locationMapper.mapResults(testinp);
        // Check output...
        assert res.size() == testinp.size();
        for (int i = 0; i < res.size(); i++ ) {
            assertEquals("bad label value", testinp.get(i).get("label"), res.get(i).get("label"));
            assertEquals("bad confidence value", testinp.get(i).get("confidence"), res.get(i).get("confidence"));
            @SuppressWarnings({"unchecked"})
            Map<String, Double> inloc = (Map<String, Double>) testinp.get(i).get("location");
            @SuppressWarnings({"unchecked"})
            Map<String, Map<String, ?>> resloc = (Map<String, Map<String, ?>>) res.get(i).get("location");

            // When converting to GeoJSON, results come back as 3 points (center, topLeft & bottomRight) as opposed
            // to 6 single numbers (top, left, bottom, right, centerX, centerY)
            
            assertEquals("Wrong number of locations in result", 3, resloc.size());
            Map<String, ?> tlEnt = resloc.get("topLeft");
            assertNotNull("Missing topLeft", tlEnt);
            Map<String, ?> brEnt = resloc.get("bottomRight");
            assertNotNull("Missing bottomRight", brEnt);
            Map<String, ?> centEnt = resloc.get("center");
            assertNotNull("Missing center", centEnt);

            assertEquals("mapped topLeft type", "Point", tlEnt.get("type"));
            assertEquals("mapped bottomRight type", "Point", brEnt.get("type"));

            Double[] tlCoords = (Double[]) tlEnt.get("coordinates");
            assertNotNull("No topLeft coordinates", tlCoords);
            assertEquals("Wrong number of topLeft coordinates", 2, tlCoords.length);
            Double[] brCoords = (Double[]) brEnt.get("coordinates");
            assertNotNull("No bottomRight coordinates", brCoords);
            assertEquals("Wrong number of bottomRight coordinates", 2, brCoords.length);
            Double[] center = (Double[]) centEnt.get("coordinates");
            assertNotNull("No center coordinates", centEnt);
            assertEquals("Wrong number of center coordinates", 2, center.length);

            assertEquals("mapped topLeft longitude", 
                    inloc.get("top") + 50d, tlCoords[0], ACCEPTABLE_DELTA);
            assertEquals("mapped topLeft latitude", 
                    inloc.get("left") + 50d, tlCoords[1], ACCEPTABLE_DELTA);
            assertEquals("mapped bottomRight longitude", 
                    inloc.get("bottom") + 50d, brCoords[0], ACCEPTABLE_DELTA);
            assertEquals("mapped bottomRight latitude", 
                    inloc.get("right") + 50d, brCoords[1], ACCEPTABLE_DELTA);
            assertEquals("mapped center longitude", 
                    inloc.get("centerY") + 50d, center[0], ACCEPTABLE_DELTA);
            assertEquals("mapped center latitude", 
                    inloc.get("centerX") + 50d, center[1], ACCEPTABLE_DELTA);

        }

        core.createLocationMapper(doubleSrcPts, doubleDstPts, true);
        assertNotNull("Should now have a location mapper", core.locationMapper);

        // now, create some test data...
        testinp = createTestData();

        res = core.locationMapper.mapResults(testinp);
        // Check output...
        assert res.size() == testinp.size();
        for (int i = 0; i < res.size(); i++ ) {
            assertEquals("bad label value", testinp.get(i).get("label"), res.get(i).get("label"));
            assertEquals("bad confidence value", testinp.get(i).get("confidence"), res.get(i).get("confidence"));
            @SuppressWarnings({"unchecked"})
            Map<String, Double> inloc = (Map<String, Double>) testinp.get(i).get("location");
            @SuppressWarnings({"unchecked"})
            Map<String, Map<String, ?>> resloc = (Map<String, Map<String, ?>>) res.get(i).get("location");

            // When converting to GeoJSON, results come back as 2 points (topLeft & bottomRight) as opposed
            // to 4 single numbers
            log.debug("Returned locations: {}", resloc);
            assertEquals("Wrong number of locations in result", 3, resloc.size());
            Map<String, ?> tlEnt = resloc.get("topLeft");
            assertNotNull("Missing topLeft", tlEnt);
            Map<String, ?> brEnt = resloc.get("bottomRight");
            assertNotNull("Missing bottomRight", brEnt);
            Map<String, ?> centEnt = resloc.get("center");
            assertNotNull("Missing center", centEnt);

            assertEquals("mapped topLeft type", "Point", tlEnt.get("type"));
            assertEquals("mapped bottomRight type", "Point", brEnt.get("type"));

            Double[] tlCoords = (Double[]) tlEnt.get("coordinates");
            assertNotNull("No topLeft coordinates", tlCoords);
            assertEquals("Wrong number of topLeft coordinates", 2, tlCoords.length);
            Double[] brCoords = (Double[]) brEnt.get("coordinates");
            assertNotNull("No bottomRight coordinates", brCoords);
            assertEquals("Wrong number of bottomRight coordinates", 2, brCoords.length);
            Double[] center = (Double[]) centEnt.get("coordinates");
            assertNotNull("No center coordinates", centEnt);
            assertEquals("Wrong number of center coordinates", 2, center.length);

            assertEquals("mapped topLeft longitude", inloc.get("top") * 2d, tlCoords[0], ACCEPTABLE_DELTA);
            assertEquals("mapped topLeft latitude", inloc.get("left") * 2d, tlCoords[1], ACCEPTABLE_DELTA);
            assertEquals("mapped bottomRight longitude", inloc.get("bottom") * 2d, brCoords[0], ACCEPTABLE_DELTA);
            assertEquals("mapped bottomRight latitude", inloc.get("right") * 2d, brCoords[1], ACCEPTABLE_DELTA);

            assertEquals("mapped center longitude",
                    inloc.get("centerY") * 2d, center[0], ACCEPTABLE_DELTA);
            assertEquals("mapped center latitude",
                    inloc.get("centerX") * 2d, center[1], ACCEPTABLE_DELTA);
        }
    }

    // Coordinate conversion data & support methods

    BigDecimal[][] plus50SrcPts = new BigDecimal[][]{
            {new BigDecimal(3), new BigDecimal(3)},
            {new BigDecimal(1.0d), new BigDecimal(1)},
            {new BigDecimal(2), new BigDecimal(1)},
            {new BigDecimal(4), new BigDecimal(3)}
    };
    BigDecimal[][] plus50DstPts = new BigDecimal[][]{
            {new BigDecimal(53d), new BigDecimal(53d)},
            {new BigDecimal(51.0d), new BigDecimal(51d)},
            {new BigDecimal(52d), new BigDecimal(51d)},
            {new BigDecimal(54d), new BigDecimal(53d)}};

    BigDecimal[][] doubleSrcPts = new BigDecimal[][] {
            {new BigDecimal(1.0f), new BigDecimal(1f)},
            {new BigDecimal(2f), new BigDecimal(1f)},
            {new BigDecimal(3f), new BigDecimal(3f)},
            {new BigDecimal(4f),new BigDecimal(3f)}};
    BigDecimal[][] doubleDstPts = new BigDecimal[][] {
            {new BigDecimal(2.0f), new BigDecimal(2f)},
            {new BigDecimal(4f), new BigDecimal(2f)},
            {new BigDecimal(6f), new BigDecimal(6f)},
            {new BigDecimal(8f), new BigDecimal(6f)}};

    static List<Map<String, ?>> generateSomePoints(int pointCount) {
        List<Map<String, ?>> res = new ArrayList<>();
        BigDecimal[][] pts = TestCoordConverter.generateRandomPoints(pointCount);
        for (int i = 0; i < pointCount/2; i++) {
            Map<String, BigDecimal> aloc = new HashMap<>();
            aloc.put("top", pts[i][1]);
            aloc.put("left", pts[i][0]);
            aloc.put("bottom", pts[i+1][1]);
            aloc.put("right", pts[i+1][0]);
            aloc.put("centerX", aloc.get("right").subtract(aloc.get("left")).divide(new BigDecimal(2.0), BigDecimal.ROUND_HALF_UP));
            aloc.put("centerY", aloc.get("bottom").subtract(aloc.get("top")).divide(new BigDecimal(2.0), BigDecimal.ROUND_HALF_UP));

            res.add(aloc);
        }
        return res;
    }
    static List<Map<String, ?>> createTestData() {
        List<Map<String, ?>> testinp = new ArrayList<>();
        Map<String, Object> aBox = new HashMap<>();
        aBox.put("confidence", .95);
        aBox.put("label", "a thingamajig");
        Map<String, Number> loc = new HashMap<>();
        loc.put("top", 1d);
        loc.put("left", 1d);
        loc.put("bottom", 3.0d);
        loc.put("right", 4.0d);
        loc.put("centerY", ((double) loc.get("bottom") - (double) loc.get("top"))/2.0d);
        loc.put("centerX", ((double) loc.get("right") - (double) loc.get("left"))/2.0d);
        aBox.put("location", loc);
        testinp.add(aBox);
        aBox = new HashMap<>();
        aBox.put("confidence", .35);
        aBox.put("label", "another thingamajig");
        loc = new HashMap<>();
        List<Map<String, ?>> targets = generateSomePoints(100);
        int i = 0;
        for (Map<String, ?> aloc : targets) {
            loc = new HashMap<>();
            loc.put("top", ((Number) aloc.get("top")).doubleValue());
            loc.put("left", ((Number) aloc.get("left")).doubleValue());;
            loc.put("bottom", ((Number) aloc.get("bottom")).doubleValue());
            loc.put("right", ((Number) aloc.get("right")).doubleValue());
            loc.put("centerY", ((double) loc.get("bottom") - (double) loc.get("top"))/2.0d);
            loc.put("centerX", ((double) loc.get("right") - (double) loc.get("left"))/2.0d);

            aBox.put("location", loc);
            aBox.put("label", "another thingamajig# " + i++);
            testinp.add(aBox);
        }

        return testinp;
    }

    // ================================================= Helper functions =================================================
    
    public boolean setupRetriever(String config) {
        Map<String, Object> conf = new LinkedHashMap<>();
        if (config != null) {
            conf.put(config, null);
        }
        
        try {
            retriever.setupDataRetrieval(conf, core);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean setupNeuralNet(String config) {
        Map<String, Object> conf = new LinkedHashMap<>();
        if (config != null) {
            conf.put(config, null);
        }
        
        try {
            neuralNet.setupImageProcessing(conf, sourceName, modelDirectory, authToken, targetVantiqServer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public class LocalImageRetrieverResults extends ImageRetrieverResults {
        public LocalImageRetrieverResults(byte[] image) {
            super(image);
        }
    }
    
    public class LocalNeuralNetResults extends NeuralNetResults {
        public LocalNeuralNetResults() {
            super();
        }
    }
}
