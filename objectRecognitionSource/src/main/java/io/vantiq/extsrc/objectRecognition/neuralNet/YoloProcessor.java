
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.ObjectDetector;
import edu.ml.tensorflow.util.ImageUtil;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

import io.vantiq.client.Vantiq;

/**
 * This is a TensorFlow implementation of YOLO (You Only Look Once). The identified objects have a {@code label} stating
 * the type of the object identified, a {@code confidence} specifying on a scale of 0-1 how confident the neural net is
 * that the identification is accurate, and a {@code location} containing the coordinates for the {@code top},
 * {@code left}, {@code bottom}, and {@code right} edges of the bounding box for the object. It can also save images
 * with the bounding boxes drawn.
 * <br>
 * The standard implementation expects a net trained on 416x416 images, and automatically resizes images to those
 * dimensions. If different dimensions are required, then changing {@code edu.ml.tensorflow.Config.SIZE} to the correct
 * dimension will change the dimensions of the image sent to the neural net. The dimensions will still be a square.
 * <br>
 * Unique settings are: 
 * <ul>
 *      <li>{@code pbFile}: Required. Config only. The .pb file for the model.
 *      <li>{@code labelsFile}: Required. Config only. The labels for the model.
 *      <li>{@code outputDir}: Optional. Config and Query. The directory in which the images (object boxes included)
 *                      will be placed. Images will be saved as
 *                      "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
 *                      where each value will zero-filled if necessary, e.g. "2018-08-14--06-30-22.jpg". For
 *                      non-Queries, no images will be saved if not set.
 *      <li>{@code saveImage}: Optional. Config and Query. Must be set in order to save images. Acceptable values are
 *                      "local", "vantiq", or "both".
 *      <li>{@code labelImage}: Optional. Config and Query. A boolean flag used to decide whether to save images with or
 *                      or without labels. Only applies if savedImages is true.
 *      <li>{@code fileName}: Optional. Query only. The name of the file that will be saved. Defaults to
 *                      "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
 *                      if not set.
 *      <li>{@code threshold}: Optional. Config and Query. The threshold of confidence used by the Yolo Neural Network 
 *                      when deciding whether to save a recognition.
 *      <li>{@code saveRate}: Optional. Config only. The rate at which images will be saved, once every n frames
 *                      captured. Default is every frame captured when unset or a non-positive number. Does nothing if
 *                      outputDir is not set at config.
 * </ul>
 * 
 * No additional data is given.
 */
public class YoloProcessor implements NeuralNetInterface {
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    String pbFile = null;
    String labelsFile = null;
    String outputDir = null;
    String saveImage = null;
    double[] anchorArray = null;
    Boolean labelImage = false;
    Vantiq vantiq;
    String server;
    String authToken;
    String sourceName;
    ImageUtil imageUtil;
    float threshold = 0.5f;
    int saveRate = 1;
    
    ObjectDetector objectDetector = null;
    
    
    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String sourceName, String modelDirectory, String authToken, String server) throws Exception {
        setup(neuralNetConfig, sourceName, modelDirectory, authToken, server);
        try {
            objectDetector = new ObjectDetector(threshold, pbFile, labelsFile, anchorArray, imageUtil, outputDir, labelImage, saveRate, vantiq, sourceName);
        } catch (Exception e) {
            throw new Exception(this.getClass().getCanonicalName() + ".yoloBackendSetupError: " 
                    + "Failed to create new ObjectDetector", e);
        }
    }
    
    /**
     * Save the necessary data from the given map.
     * @param neuralNet         The configuration from 'neuralNet' in the config document
     * @param sourceName        The name of the VANTIQ Source
     * @param modelDirectory    The directory in which the .pb and label files are placed
     * @param authToken         The authToken used to with the VANTIQ SDK
     * @throws Exception        Thrown when an invalid configuration is requested
     */
    private void setup(Map<String, ?> neuralNet, String sourceName, String modelDirectory, String authToken, String server) throws Exception {
        this.server = server;
        this.authToken = authToken;
        this.sourceName = sourceName;
        // Obtain the files for the net
       if (neuralNet.get("pbFile") instanceof String && neuralNet.get("labelFile") instanceof String) {
           if (!modelDirectory.equals("") && !modelDirectory.endsWith("/") && !modelDirectory.endsWith("\\")) {
               modelDirectory += "/";
           }
           pbFile = modelDirectory + (String) neuralNet.get("pbFile");
           labelsFile = modelDirectory + (String) neuralNet.get("labelFile");
       } else {
           throw new Exception(this.getClass().getCanonicalName() + ".missingConfig: " 
                   + "Could not find 'pbFile' and/or 'labelFile' in the neuralNet configuration");
       }
       
       if (neuralNet.get("threshold") instanceof Number) {
           Number threshNum = (Number) neuralNet.get("threshold");
           float tempThresh = threshNum.floatValue();
           if (0 <= tempThresh && tempThresh <= 1) {
               threshold = tempThresh;
           } else if (0 <= tempThresh && tempThresh <= 100) {
               threshold = tempThresh/100;
           } else {
               log.warn("The threshold specified in the config is not valid. The threshold was set to its default "
                       + "value of 0.5");
           }
       } else {
           log.debug("The threshold was not specified in the config. Using default threshold value of 0.5.");
       }
       
       // Get anchor values if they exist
       if (neuralNet.get("anchors") instanceof List) {
           List tempAnchorList = (List) neuralNet.get("anchors");
           // Checking that there are 5 anchor pairs (10 elements total)
           if (tempAnchorList.size() != 10) {
               log.error("Invalid AnchorList Size: there must be exactly 5 anchor pairs, totalling in 10 elements. "
                       + "Default anchor values will be used.");
           } else {
               // Checking to make sure anchor pairs are valid
               Boolean validElements = true;
               for (int i = 0; i < tempAnchorList.size(); i++) {
                   if (!(tempAnchorList.get(i) instanceof Number)) {
                       log.error("Invalid Type: each anchor element must be a double. Default anchor values will be used.");
                       validElements = false;
                       break;
                   }
               }
               
               // If valid, then creating double[] from List
               if (validElements) {
                   anchorArray = new double[10];
                   for (int i = 0; i< tempAnchorList.size(); i++) {
                       if (tempAnchorList.get(i) instanceof Integer) {
                           anchorArray[i] = (double) ((Integer) tempAnchorList.get(i));
                       } else {
                           anchorArray[i] = (double) tempAnchorList.get(i);
                       }
                   }
               }
           }
       } else {
           log.debug("Anchor values were not set in the config. Default anchor values will be used.");
       }
              
       // Setup the variables for saving images
       imageUtil = new ImageUtil();
       if (neuralNet.get("saveImage") instanceof String) {
           saveImage = (String) neuralNet.get("saveImage");
           
           // Check if user wants to save original image without labels
           if (neuralNet.get("labelImage") instanceof String) {
               String labelImageString = (String) neuralNet.get("labelImage");
               labelImage = labelImageString.equalsIgnoreCase("true");
           }
           
           // Check which method of saving the user requests
           if (!saveImage.equalsIgnoreCase("vantiq") && !saveImage.equalsIgnoreCase("both") && !saveImage.equalsIgnoreCase("local")) {
               log.error("The config value for saveImage was invalid. Images will not be saved.");
           }
           if (!saveImage.equalsIgnoreCase("vantiq")) {
               if (neuralNet.get("outputDir") instanceof String) {
                   outputDir = (String) neuralNet.get("outputDir");
               }
           }
           if (saveImage.equalsIgnoreCase("vantiq") || saveImage.equalsIgnoreCase("both")) {
               vantiq = new io.vantiq.client.Vantiq(server);
               vantiq.setAccessToken(authToken);
           }
           imageUtil.outputDir = outputDir;
           imageUtil.vantiq = vantiq;
           imageUtil.saveImage = true;
           imageUtil.sourceName = sourceName;
           if (neuralNet.get("saveRate") instanceof Integer) {
               saveRate = (Integer) neuralNet.get("saveRate");
           }
       } else {
           // Flag to mark that we should not save images
           imageUtil.saveImage = false;
       }
   }

    /**
     * Run the image through a YOLO net. May save the resulting image depending on the settings.
     */
    @Override
    public NeuralNetResults processImage(byte[] image) throws ImageProcessingException {
        List<Map<String, ?>> foundObjects;
        NeuralNetResults results = new NeuralNetResults();
        long after;
        long before = System.currentTimeMillis();

        try {
            foundObjects = objectDetector.detect(image);
        } catch (IllegalArgumentException e) {
            throw new ImageProcessingException(this.getClass().getCanonicalName() + ".invalidImage: " 
                    + "Data to be processed was invalid. Most likely it was not correctly encoded as a jpg.", e);
        }

        after = System.currentTimeMillis();
        log.debug("Image processing time: {}.{} seconds"
                , (after - before) / 1000, String.format("%03d", (after - before) % 1000));
        
        // Save filename, or mark it as null if images are not saved
        if (objectDetector.lastFilename == null) {
            results.setLastFilename(null);
        } else {
            results.setLastFilename("objectRecognition/" + sourceName + '/' + objectDetector.lastFilename);
        }
        results.setResults(foundObjects);
        return results;
    }
    
    /**
     * Run the image through a YOLO net. May save the resulting image depending on the request.
     */
    @Override
    public NeuralNetResults processImage(byte[] image, Map<String, ?> request) throws ImageProcessingException {
        List<Map<String, ?>> foundObjects;
        NeuralNetResults results = new NeuralNetResults();
        String saveImage = null;
        String outputDir = null;
        String fileName = null;
        Vantiq vantiq = null;
        
        if (request.get("NNsaveImage") instanceof String) {
            saveImage = (String) request.get("NNsaveImage");
            if (!saveImage.equalsIgnoreCase("vantiq") && !saveImage.equalsIgnoreCase("both") && !saveImage.equalsIgnoreCase("local")) {
                log.error("The config value for saveImage was invalid. Images will not be saved.");
            } else {
                if (saveImage.equalsIgnoreCase("vantiq") || saveImage.equalsIgnoreCase("both")) {
                    vantiq = new io.vantiq.client.Vantiq(server);
                    vantiq.setAccessToken(authToken);
                }
                if (!saveImage.equalsIgnoreCase("vantiq")) {
                    if (request.get("NNoutputDir") instanceof String) {
                        outputDir = (String) request.get("NNoutputDir");
                    }
                }
                if (request.get("NNfileName") instanceof String) {
                    fileName = (String) request.get("NNfileName");
                }
            }
        }
        
        long after;
        long before = System.currentTimeMillis();
        try {
            foundObjects = objectDetector.detect(image, outputDir, fileName, vantiq);
        } catch (IllegalArgumentException e) {
            throw new ImageProcessingException(this.getClass().getCanonicalName() + ".queryInvalidImage: " 
                    + "Data to be processed was invalid. Most likely it was not correctly encoded as a jpg.", e);
        }
        

        after = System.currentTimeMillis();
        log.debug("Image processing time: {}.{} seconds"
                , (after - before) / 1000, String.format("%03d", (after - before) % 1000));
        
        // Save filename, or mark it as null if images are not saved
        if (objectDetector.lastFilename == null) {
            results.setLastFilename(null);
        } else {
            results.setLastFilename("objectRecognition/" + sourceName + '/' + objectDetector.lastFilename);
        }
        results.setResults(foundObjects);
        return results;
    }

    @Override
    public void close() {
        objectDetector.close();
    }
}
