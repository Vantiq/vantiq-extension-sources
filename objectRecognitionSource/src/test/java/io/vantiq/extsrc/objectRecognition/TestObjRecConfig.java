package io.vantiq.extsrc.objectRecognition;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
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
    
    Map<String,Object> general;
    Map<String,Object> dataSource;
    Map<String,Object> neuralNet;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        modelDirectory = "models";
        nCore = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        handler = new ObjectRecognitionConfigHandler(nCore);
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
        assertTrue("Should fail when not given a valid classname", configIsFailed());
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
        assertTrue("Should fail when not given a valid classname", configIsFailed());
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
        assertTrue("Should fail on empty config", configIsFailed());
    }
    
    @Test
    public void testMissingGeneral() {
        Map conf = minimalConfig();
        conf.remove("general");
        sendConfig(conf);
        assertTrue("Should fail when missing 'general' config", configIsFailed());
    }
    
    @Test
    public void testMissingDataSource() {
        Map conf = minimalConfig();
        conf.remove("dataSource");
        sendConfig(conf);
        assertTrue("Should fail when missing 'dataSource' config", configIsFailed());
    }
    
    @Test
    public void testMissingNeuralNet() {
        Map conf = minimalConfig();
        conf.remove("neuralNet");
        sendConfig(conf);
        assertTrue("Should fail when missing 'neuralNet' config", configIsFailed());
    }
    
    @Test
    public void testMinimalConfig() {
        nCore.start(5); // Need a client to avoid NPEs on sends
        
        Map conf = minimalConfig();
        sendConfig(conf);
        assertFalse("Should not fail with minimal config", configIsFailed());
        
        general.put("pollRate", 300000);
        sendConfig(conf);
        assertFalse("Should not fail with minimal config", configIsFailed());
        assertTrue("Timer should exist after pollRate set to positive number", nCore.pollTimer != null); 
        
        general.put("pollRate", -100);
        sendConfig(conf);
        assertFalse("Should not fail with minimal config", configIsFailed());
    }
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String,?> ORConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String,Object> obj = new LinkedHashMap<>();
        Map<String,Object> config = new LinkedHashMap<>();
        config.put("objRecConfig", ORConfig);
        obj.put("config", config);
        m.object = obj;
        
        handler.handleMessage(m);
    }
    
    public Map<String,Object> minimalConfig() {
        createMinimalSubConfigs();
        Map<String,Object> ret = new LinkedHashMap<>();
        ret.put("dataSource", dataSource);
        ret.put("general", general);
        ret.put("neuralNet", neuralNet);
        
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
