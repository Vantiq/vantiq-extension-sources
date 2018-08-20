package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.ObjectDetector;

/**
 * Unique settings are: 
 * <ul>
 *      <li>{@code pbFile}: Required. Config only. The .pb file for the model.
 *      <li>{@code labelFile}: Required. Config only. The labels for the model.
 *      <li>{@code outputDir}: Optional. Config and Query. The directory in which the images (object boxes included)
 *                      will be placed. Images will be saved as
 *                      "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
 *                      where each value will zero-filled if necessary, e.g. "2018-08-14--06-30-22.jpg". For
 *                      non-Queries, no images will be saved if not set. For Queries, either this must be set in the
 *                      Query, or this must be set in the config and fileName must be set in the Query for images to be
 *                      saved.
 *      <li>{@code fileName}: Optional. Query only. The name of the file that will be saved. Defaults to
 *                      "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
 *                      if not set.
 *      <li>{@code saveRate}: Optional. Config only. The rate at which images will be saved, once every n frames
 *                      captured. Default is every frame captured when unset or a non-positive number. Does nothing if
 *                      outputDir is not set at config.
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
           if (!modelDirectory.equals("") && !modelDirectory.endsWith("/") && !modelDirectory.endsWith("\\")) {
               modelDirectory += "/";
           }
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
    public List<Map> processImage(byte[] image, Map<String,?> request) {
        List<Map> results;
        String outputDir = null;
        String fileName = null;
        
        if (request.get("outputDir") instanceof String) {
            outputDir = (String) request.get("outputDir");
        }
        if (request.get("fileName") instanceof String) {
            fileName = (String) request.get("fileName");
        }
        
        long after;
        long before = System.currentTimeMillis();
        results = objectDetector.detect(image, outputDir, fileName);
        after = System.currentTimeMillis();
        log.debug("Image processing time: " + (after - before) / 1000 + "." + (after - before) % 1000 + " seconds");
        return results;
    }

    @Override
    public void close() {
        objectDetector.close();
    }
}
