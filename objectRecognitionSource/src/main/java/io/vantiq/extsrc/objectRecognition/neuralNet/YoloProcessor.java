package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.ObjectDetector;

/**
 * Unique settings are: 
 * <ul>
 *  <li>{@code pbFile}: Required. The .pb file for the model.
 *  <li>{@code labelFile}: Required. The labels for the model.
 *  <li>{@code outputDir}: Optional. The directory in which the images (object boxes included) will be placed. Images 
 *                  will be saved as "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
 *                  where each value will zero-filled if necessary, e.g. "2018-08-14--06-30-22" No images
 *                  will be saved if not set.
 *  <li>{@code saveRate}: Optional. The rate at which images will be saved, once in every n frames captured starting 
 *                  with the first. Default is 1 when unset or a non-positive number. Does nothing if outputDir is not set.
 * </ul> 
 */
public class YoloProcessor implements NeuralNetInterface {
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    String pbFile = null;
    String labelsFile = null;
    String outputDir = null;
    int saveRate = 1;
    
    ObjectDetector objectDetector = null;
    
    
    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String modelDirectory) throws Exception {
        setup(neuralNetConfig, modelDirectory);
        try {
            // TODO bring back option to save images with/without the annotations
            objectDetector = new ObjectDetector(pbFile, labelsFile, outputDir, saveRate);
        } catch (Exception e) {
            throw new Exception("Failed to create new ObjectDetector", e);
        }
    }
    
    /**
     * Save the necessary data from the given map.
     * @param neuralNet         The configuration from 'neuralNet' in the config document
     * @param modelDirectory    The directory in which the .pb and label files are placed
     * @throws Exception        Thrown when an invalid configuration is requested
     */
    private void setup(Map<String, ?> neuralNet, String modelDirectory) throws Exception {
        // Obtain the files for the net
       if (neuralNet.get("pbFile") instanceof String && neuralNet.get("labelFile") instanceof String) {
           pbFile = modelDirectory + (String) neuralNet.get("pbFile");
           labelsFile = modelDirectory + (String) neuralNet.get("labelFile");
       } else {
           throw new Exception("Could not find 'pbFile' and/or 'labelFile' in the neuralNet configuration");
       }
       if (neuralNet.get("outputDir") instanceof String) {
           outputDir = (String) neuralNet.get("outputDir");
           if (neuralNet.get("saveRate") instanceof Integer) {
               saveRate = (Integer) neuralNet.get("saveRate");
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
