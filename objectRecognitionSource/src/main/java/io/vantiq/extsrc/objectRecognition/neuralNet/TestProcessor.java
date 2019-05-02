package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

public class TestProcessor implements NeuralNetInterface2 {
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    int sleepTime = 1000;

    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String sourceName, String modelDirectory,
            String authToken, String server) throws Exception {
        if (neuralNetConfig.get("sleepTime") instanceof Integer && (Integer) neuralNetConfig.get("sleepTime") > 0) {
            sleepTime = (Integer) neuralNetConfig.get("sleepTime");
        }
    }

    @Override
    public NeuralNetResults processImage(byte[] image) throws ImageProcessingException {
        processImage(null, image);
        return new NeuralNetResults();
    }

    @Override
    public NeuralNetResults processImage(byte[] image, Map<String, ?> request) throws ImageProcessingException {
        processImage(null, image, request);
        return new NeuralNetResults();
    }

    @Override
    public NeuralNetResults processImage(Map<String, ?> processingParams, byte[] image) throws ImageProcessingException {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            log.error("An error occured while trying to sleep.", e);
        }
        return new NeuralNetResults();
    }

    @Override
    public NeuralNetResults processImage(Map<String, ?> processingParams, byte[] image, Map<String, ?> request) throws ImageProcessingException {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            log.error("An error occured while trying to sleep.", e);
        }
        return new NeuralNetResults();
    }
    
    @Override
    public void close() {}
}
