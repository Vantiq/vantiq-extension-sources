
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.ObjectDetector;
import edu.ml.tensorflow.util.ImageUtil;
import edu.ml.tensorflow.classifier.YOLOClassifier;
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
 * dimensions. If different dimensions are required, then changing {@code edu.ml.tensorflow.Config.FRAME_SIZE} to the correct
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
public class YoloProcessor extends NeuralNetUtils implements NeuralNetInterface {
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    String pbFile = null;
    String labelsFile = null;
    String metaFile = null;
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
    
    // Variables for pre crop
    int x = -1;
    int y = -1; 
    int w = -1;
    int h = -1;
    boolean preCropping = false;
    
    ObjectDetector objectDetector = null;
    
    private static final String CROP_BEFORE = "cropBeforeAnalysis";
    
    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String sourceName, String modelDirectory, String authToken, String server) throws Exception {
        setup(neuralNetConfig, sourceName, modelDirectory, authToken, server);
        try {
            objectDetector = new ObjectDetector(threshold, pbFile, labelsFile, metaFile, anchorArray, imageUtil, outputDir, labelImage, saveRate, vantiq, sourceName);
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
       if (neuralNet.get("pbFile") instanceof String && 
               (neuralNet.get("labelFile") instanceof String || neuralNet.get("metaFile") instanceof String)) {
           if (!modelDirectory.equals("") && !modelDirectory.endsWith("/") && !modelDirectory.endsWith("\\")) {
               modelDirectory += "/";
           }
           if (neuralNet.get("labelFile") instanceof String) {
               labelsFile = modelDirectory + (String) neuralNet.get("labelFile");
               log.warn("A label file has been detected and will be used. Label files are deprecated, we encourage you "
                       + "to use a meta file instead.");
           }  
           if (neuralNet.get("metaFile") instanceof String) {
               metaFile = modelDirectory + (String) neuralNet.get("metaFile");
           }
           pbFile = modelDirectory + (String) neuralNet.get("pbFile");
           
       } else {
           throw new Exception(this.getClass().getCanonicalName() + ".missingConfig: Invalid Configuration: " 
                   + "A YOLO NeuralNet configuration requires a pbFile and a metaFile and/or labelFile. " 
                   + "pbFile: " + (neuralNet.get("pbFile") instanceof String ? (String) neuralNet.get("pbFile")  : " ") 
                   + ", metaFile: " + (neuralNet.get("metaFile") instanceof String ? (String) neuralNet.get("metaFile")  : " ") 
                   + ", labelFile: " + (neuralNet.get("labelFile") instanceof String ? (String) neuralNet.get("labelFile")  : " "));
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
           // Checking that the number of anchor pairs matches NUMBER_OF_BOUNDING_BOXES * 2
           if (tempAnchorList.size() != YOLOClassifier.NUMBER_OF_BOUNDING_BOX * 2) {
               log.error("Invalid AnchorList Size: there must be exactly " + YOLOClassifier.NUMBER_OF_BOUNDING_BOX + 
                       " anchor pairs, totalling in " + YOLOClassifier.NUMBER_OF_BOUNDING_BOX * 2 + " elements. " 
                       + "Default anchor values will be used.");
           } else {
               // Checking to make sure anchor pairs are valid, and creating double[] from List if they are
               anchorArray = new double[YOLOClassifier.NUMBER_OF_BOUNDING_BOX * 2];
               for (int i = 0; i < tempAnchorList.size(); i++) {
                   if (!(tempAnchorList.get(i) instanceof Number)) {
                       log.error("Invalid Type: each anchor element must be a number. Default anchor values will be used.");
                       anchorArray = null;
                       break;
                   } else if (tempAnchorList.get(i) instanceof Integer) {
                       anchorArray[i] = (double) ((Integer) tempAnchorList.get(i));
                   } else {
                       anchorArray[i] = (double) tempAnchorList.get(i);
                   }
               }
           }
       } else if (neuralNet.get("anchors") != null){
           if (!(neuralNet.get("metaFile") instanceof String)) {
               log.warn("Anchor values were improperly set in the config. Additionally, no meta file was provided. "
                       + "Anchors must be a list of " + YOLOClassifier.NUMBER_OF_BOUNDING_BOX * 2 + " numbers. "
                       + "Default anchor values will be used.");
           } else {
               log.warn("Anchor values were not set, or improperly set, in the config. Anchors must be a list of " 
                       + YOLOClassifier.NUMBER_OF_BOUNDING_BOX * 2 + " numbers. " + "Anchors from the meta file will be used.");
           }
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
           
           // Check if any resolution configurations have been set
           if (neuralNet.get("savedResolution") instanceof Map) {
               Map savedResolution = (Map) neuralNet.get("savedResolution");
               if (savedResolution.get("longEdge") instanceof Integer) {
                   int longEdge = (Integer) savedResolution.get("longEdge");
                   if (longEdge < 0) {
                       log.error("The config value for longEdge must be a non-negative integer. Saved image resolution will not be changed.");
                   } else {
                       imageUtil.longEdge = longEdge;
                   }
               } else {
                   log.debug("The longEdge option was not set, or was improperly set. This option must be a non-negative integer. "
                           + "Saved image resolution will not be changed.");
               }
           } else {
               log.debug("No savedResolution was specified, or savedResolution was invalid. The savedResolution option must be a map.");
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
       
       // Checking if pre cropping was specified in config
       if (neuralNet.get(CROP_BEFORE) instanceof Map) {
           Map preCrop = (Map) neuralNet.get(CROP_BEFORE);
           if (preCrop.get("x") instanceof Integer && (Integer) preCrop.get("x") >= 0) {
               x = (Integer) preCrop.get("x");
           }
           if (preCrop.get("y") instanceof Integer && (Integer) preCrop.get("y") >= 0) {
               y = (Integer) preCrop.get("y");
           }
           if (preCrop.get("width") instanceof Integer && (Integer) preCrop.get("width") >= 0) {
               w = (Integer) preCrop.get("width");
           }
           if (preCrop.get("height") instanceof Integer && (Integer) preCrop.get("height") >= 0) {
               h = (Integer) preCrop.get("height");
           }
           if (x >= 0 && y >= 0 && w >= 1 && h >= 1) {
               preCropping = true;
           } else {
               log.error("The values specified by the cropBeforeAnalysis config option were invalid. Each value must be a non-negative "
                       + "integer.");
           }
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
        
        // Pre crop the image if vals were specified
        if (preCropping) {
            image = cropImage(image, x, y, w, h);
        }

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
                
        // Checking if pre cropping was specified in query parameters
        boolean queryCrop = false;
        int x, y, w, h;
        x = y = w = h = -1;
        if (request.get(CROP_BEFORE) instanceof Map) {
            Map preCrop = (Map) request.get(CROP_BEFORE);
            if (preCrop.get("x") instanceof Integer && (Integer) preCrop.get("x") >= 0) {
                x = (Integer) preCrop.get("x");
            }
            if (preCrop.get("y") instanceof Integer && (Integer) preCrop.get("y") >= 0) {
                y = (Integer) preCrop.get("y");
            }
            if (preCrop.get("width") instanceof Integer && (Integer) preCrop.get("width") >= 0) {
                w = (Integer) preCrop.get("width");
            }
            if (preCrop.get("height") instanceof Integer && (Integer) preCrop.get("height") >= 0) {
                h = (Integer) preCrop.get("height");
            }
            if (x >= 0 && y >= 0 && w >= 1 && h >= 1) {
                queryCrop = true;
            } else {
                log.error("The values specified by the cropBeforeAnalysis query parameter were invalid. Each value must be a "
                        + "non-negative integer.");
            }
        }
        
        if (queryCrop) {
            image = cropImage(image, x, y, w, h);
        } else if (preCropping) {
            image = cropImage(image, this.x, this.y, this.w, this.h);
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
