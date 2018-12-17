
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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

public class TestObjRecCore extends ObjRecTestBase {
    
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
    
    
    @Test
    public void testProcessImage() throws IOException {
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
    public void testSourceNameInResults() {
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
        
        // Make sure that source name is included in map from results
        Map<String, Object> testMapFromResults = core.createMapFromResults(imageResults, neuralNetResults);
        assert testMapFromResults.get("sourceName").equals("src");
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
            neuralNet.setupImageProcessing(conf, modelDirectory, authToken, targetVantiqServer);
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
