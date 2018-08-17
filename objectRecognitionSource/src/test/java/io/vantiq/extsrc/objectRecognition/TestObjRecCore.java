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
import io.vantiq.extsrc.objectRecognition.neuralNet.BasicTestNeuralNet;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

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
        
        core.start();
    }
    
    @After
    public void tearDown() {
        core.stop();
    }
    
    @Test
    public void testRetrieveImage() {
        assumeTrue("Can't test retrieveImage without file to retrieve", new File(IMAGE_LOCATION).exists());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(null));
        byte[] data;
        data = core.retrieveImage();
        assert data != null && data.length > 1; 
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.RETURN_NULL));
        data = core.retrieveImage();
        assert data == null; 
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.THROW_EXCEPTION_ON_REQ));
        data = core.retrieveImage();
        assert data == null;
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.THROW_FATAL_ON_REQ));
        data = core.retrieveImage();
        assert data == null;
        assertTrue("Core should be closed after fatal error", core.isClosed());
        
        core.closed = false;
        core.imageRetriever = retriever;
        core.start();
        assertFalse("Resetting closed status failed", core.isClosed());
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(BasicTestRetriever.THROW_RUNTIME_ON_REQ));
        data = core.retrieveImage();
        assert data == null;
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    @Test
    public void testRetrieveImageQuery() {
        assumeTrue("Can't test retrieveImage without file to retrieve", new File(IMAGE_LOCATION).exists());
        
        Map<String,Object> request;
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String,String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        byte[] data;
        
        assertTrue("Test helper setupRetriever failed unexpectedly"
                , setupRetriever(null));
        request = new LinkedHashMap<>();
        request.put(BasicTestRetriever.RETURN_NULL, null);
        msg.object = request;
        data = core.retrieveImage(msg);
        assert data == null;
        assertFalse("Core should not be closed", core.isClosed());
        
        request = new LinkedHashMap<>();
        request.put(BasicTestRetriever.THROW_EXCEPTION_ON_REQ, null);
        msg.object = request;
        data = core.retrieveImage(msg);
        assert data == null;
        assertFalse("Core should not be closed", core.isClosed());
        
        request = new LinkedHashMap<>();
        msg.object = request;
        data = core.retrieveImage(msg);
        assert data != null && data.length > 1;
        assertFalse("Core should not be closed", core.isClosed());
        
        request = new LinkedHashMap<>();
        request.put(BasicTestRetriever.THROW_FATAL_ON_REQ, null);
        msg.object = request;
        data = core.retrieveImage(msg);
        assert data == null;
        assertTrue("Core should be closed after fatal error", core.isClosed());
        
        core.closed = false;
        core.imageRetriever = retriever;
        core.start();
        assertFalse("Resetting closed status failed", core.isClosed());
        
        msg.object = null;
        data = core.retrieveImage(msg);
        assert data == null;
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    
    @Test
    public void testProcessImage() throws IOException {
        final byte[] imageData;
        Map sentMsg;
        byte[] lastBytes;
        try {
            imageData = Files.readAllBytes(new File(IMAGE_LOCATION).toPath());
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
        assertFalse("Core should not be closed", core.isClosed());
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_FATAL_ON_REQ));
        core.sendDataFromImage(imageData);
        assertTrue("Core should be closed after fatal error", core.isClosed());
        
        core.closed = false;
        core.neuralNet = neuralNet;
        core.start();
        assertFalse("Resetting closed status failed", core.isClosed());
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_RUNTIME_ON_REQ));
        core.sendDataFromImage(imageData);
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    @Test
    public void testProcessImageQuery() {
        final byte[] imageData;
        Map sentMsg;
        try {
            imageData = Files.readAllBytes(new File(IMAGE_LOCATION).toPath());
        } catch (IOException e) {
            assumeFalse("Could not read image for the test", true);
            return; // Never reaches, just silences imageData not initialized errors
        }
        
        ExtensionServiceMessage msg = new ExtensionServiceMessage("");
        Map<String,String> header = new LinkedHashMap<>();
        header.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, "queryAddress");
        msg.messageHeaders = header;
        
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
        core.start();
        assertFalse("Resetting closed status failed", core.isClosed());
        
        assertTrue("Test helper setupNeuralNet failed unexpectedly"
                , setupNeuralNet(BasicTestNeuralNet.THROW_RUNTIME_ON_REQ));
        core.sendDataFromImage(imageData, msg);
        assertTrue("Core should be closed after runtime error", core.isClosed());
    }
    
    @Test
    public void testExitIfConnectionFails() {
        core.start();
        assertTrue("Should have succeeded", core.exitIfConnectionFails(core.client));
        assertFalse("Success means it shouldn't be closed", core.isClosed());
        
        
        core.close();
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        FalseClient fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(false);
        assertFalse("Should fail due to auth failing", core.exitIfConnectionFails(core.client));
        assertTrue("Failure means it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(false);
        assertFalse("Should fail due to websocket failing", core.exitIfConnectionFails(core.client));
        assertTrue("Failure means it should be closed", core.isClosed());
        
        core.close();
        core = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        fc = new FalseClient(sourceName);
        core.client = core.fClient = fc;
        fc.initiateFullConnection(targetVantiqServer, authToken);
        fc.completeWebSocketConnection(true);
        fc.completeAuthentication(true);
        assertFalse("Should fail due to timeout on source connection", core.exitIfConnectionFails(core.client));
        assertTrue("Failure means it should be closed", core.isClosed());
    }
// ================================================= Helper functions =================================================
    
    public boolean setupRetriever(String config) {
        Map<String,Object> conf = new LinkedHashMap<>();
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
        Map<String,Object> conf = new LinkedHashMap<>();
        if (config != null) {
            conf.put(config, null);
        }
        
        try {
            neuralNet.setupImageProcessing(conf, modelDirectory);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
