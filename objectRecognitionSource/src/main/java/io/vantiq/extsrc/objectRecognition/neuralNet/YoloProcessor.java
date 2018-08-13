package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.ObjectDetector;

public class YoloProcessor extends NeuralNetInterface {
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    String pbFile = null;
    String labelsFile = null;
    double threshold = 0.5;
    
    ObjectDetector objectDetector = null;
    
    
    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String modelDirectory) throws Exception {
        setup(neuralNetConfig, modelDirectory);
        try {
            objectDetector = new ObjectDetector(pbFile, labelsFile);
        } catch (Exception e) {
            throw new Exception("Failed to create new ObjectDetector", e);
        }
    }
    
    private void setup(Map<String, ?> neuralNet, String modelDirectory) throws Exception {
        // Obtain the files for the net
       if (neuralNet.get("cfgFile") instanceof String && neuralNet.get("weightsFile") instanceof String) {
           pbFile = modelDirectory + (String) neuralNet.get("pbFile");
           labelsFile = modelDirectory + (String) neuralNet.get("labelFile");
       } else {
           log.error("No valid combination of cfgFile and weightsFile");
           log.error("Exiting...");
           throw new Exception("Could not find 'pbFile' and/or 'labelFile' in the neuralNet configuration");
       }
       if (neuralNet.get("threshold") instanceof Double || neuralNet.get("threshold") instanceof Float) {
           double thresh = (Double) neuralNet.get("threshold");
           if (thresh >= 0 && thresh <= 1) {
               threshold = thresh;
           }
       }
   }

    @Override
    public List<Map> processImage(byte[] image) {
        List<Map> results;
        long after;
        long before = System.currentTimeMillis();
        results = objectDetector.detect(image);
        after = System.currentTimeMillis();
        log.debug("Image processing time: " + (after - before) / 1000 + "." + (after - before) % 1000 + " seconds");
        return results;
    }

    @Override
    public void close() {
        objectDetector.close();
    }

}
