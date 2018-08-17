package io.vantiq.extsrc.objectRecognition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extsrc.objectRecognition.imageRetriever.BasicTestRetriever;
import io.vantiq.extsrc.objectRecognition.neuralNet.BasicTestNeuralNet;

public class TestObjRecConfig {

    ObjectRecognitionConfigHandler handler;
    
    NoSendORCore nCore;
    
    String sourceName;
    String authToken;
    String targetVantiqServer;
    String modelDirectory;
    
    final String TYPE = "objectRecognition";
    
    Map<String,Object> minimalGeneral;
    Map<String,Object> minimalDataSource;
    Map<String,Object> minimalNeuralNet;
    
    @Before
    public void setup() {
        sourceName = "src";
        authToken = "token";
        targetVantiqServer = "dev.vantiq.com";
        modelDirectory = "models";
        nCore = new NoSendORCore(sourceName, authToken, targetVantiqServer, modelDirectory);
        handler = new ObjectRecognitionConfigHandler(nCore);
        createminimalSubConfigs();
    }
    
    @After
    public void tearDown() {
        nCore.stop();
    }
    
    
    
// ================================================= Helper functions =================================================
    
    public void sendConfig(Map<String,?> ORConfig) {
        ExtensionServiceMessage m = new ExtensionServiceMessage("");
        
        Map<String,Object> obj = new LinkedHashMap<>();
        Map<String,Object> config = new LinkedHashMap<>();
        config.put("extSrcConfig", ORConfig);
        obj.put("config", config);
        m.object = obj;
        
        handler.handleMessage(m);
    }
    
    public void createminimalSubConfigs() {
        createminimalGeneral();
        createminimalDataSource();
        createminimalNeuralNet();
    }
    
    public void createminimalGeneral() {
        minimalGeneral = new LinkedHashMap<>();
        minimalGeneral.put("pollRate", 0);
    }
    
    public void createminimalDataSource() {
        minimalDataSource = new LinkedHashMap<>();
        minimalDataSource.put("type", BasicTestRetriever.class.getCanonicalName());
    }
    
    public void createminimalNeuralNet() {
        minimalNeuralNet = new LinkedHashMap<>();
        minimalNeuralNet.put("type", BasicTestNeuralNet.class.getCanonicalName());
    }
}
