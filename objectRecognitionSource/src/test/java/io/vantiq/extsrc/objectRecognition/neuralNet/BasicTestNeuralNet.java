
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

public class BasicTestNeuralNet implements NeuralNetInterface {
    
    public Map<String, ?>            config;
    public String                   modelDirectory;
    public final static String      THROW_EXCEPTION         = "throwException";
    public final static String      THROW_EXCEPTION_ON_REQ  = "throwReqException";
    public final static String      THROW_FATAL_ON_REQ      = "throwFatalException";
    public final static String      THROW_RUNTIME_ON_REQ    = "throwRuntimeException";
    public final static String      RETURN_NULL             = "retNull";

    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String modelDirectory, String authToken, String server) throws Exception {
        config = neuralNetConfig;
        this.modelDirectory = modelDirectory;
        if (config.containsKey(THROW_EXCEPTION)) {
            throw new Exception("Exception requested");
        }
    }

    @Override
    public NeuralNetResults processImage(byte[] image) throws ImageProcessingException {
        if (config.containsKey(THROW_EXCEPTION_ON_REQ)) {
            throw new ImageProcessingException("Exception on request");
        } else if (config.containsKey(THROW_FATAL_ON_REQ)) {
            throw new FatalImageException("Fatal exception requested");
        } else if (config.containsKey(THROW_RUNTIME_ON_REQ)) {
            throw new RuntimeException("Exception on request");
        } else if (config.containsKey(RETURN_NULL)) {
            return null;
        } else {
            Map<String, String> m = new LinkedHashMap<>();
            List<Map<String, ?>> l = new ArrayList<>(); l.add(m);
            
            return new NeuralNetResults(l);
        }
    }
    
    @Override
    public NeuralNetResults processImage(byte[] image, Map<String, ?> request) throws ImageProcessingException {
        if (config.containsKey(THROW_EXCEPTION_ON_REQ)) {
            throw new ImageProcessingException("Exception on request");
        } else if (config.containsKey(THROW_FATAL_ON_REQ)) {
            throw new FatalImageException("Fatal exception requested");
        } else if (config.containsKey(THROW_RUNTIME_ON_REQ)) {
            throw new RuntimeException("Exception on request");
        } else if (config.containsKey(RETURN_NULL)) {
            return null;
        } else {
            Map<String, String> m = new LinkedHashMap<>();
            List<Map<String, ?>> l = new ArrayList<>(); l.add(m);
            
            return new NeuralNetResults(l);
        }
    }

    @Override
    public void close() {}
}
