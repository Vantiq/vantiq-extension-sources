
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
@SuppressWarnings({"PMD.TooManyFields"})
public class YoloProcessor extends NeuralNetUtils implements NeuralNetInterface2 {

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
    boolean uploadAsImage = false;
    
    // Variables for pre crop
    int x = -1;
    int y = -1; 
    int w = -1;
    int h = -1;
    boolean preCropping = false;
    
    ObjectDetector objectDetector = null;
    
    // Constants for Source Configuration options
    private static final String CROP_BEFORE = "cropBeforeAnalysis";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String META_FILE = "metaFile";
    private static final String LABEL_FILE = "labelFile";
    private static final String PB_FILE = "pbFile";
    private static final String THRESHOLD = "threshold";
    private static final String ANCHORS = "anchors";
    private static final String SAVE_IMAGE = "saveImage";
    private static final String BOTH = "both";
    private static final String LOCAL = "local";
    private static final String VANTIQ = "vantiq";
    private static final String OUTPUT_DIR = "outputDir";
    private static final String LABEL_IMAGE = "labelImage";
    private static final String SAVE_RATE = "saveRate";
    private static final String SAVED_RESOLUTION = "savedResolution";
    private static final String LONG_EDGE = "longEdge";
    private static final String UPLOAD_AS_IMAGE = "uploadAsImage";


    // Constants for Query Parameter options
    private static final String NN_OUTPUT_DIR = "NNoutputDir";
    private static final String NN_FILENAME = "NNfileName";
    private static final String NN_SAVE_IMAGE = "NNsaveImage";

    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");

    @Override
    @SuppressWarnings({"PMD.UseObjectForClearerAPI"})
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String sourceName, String modelDirectory,
                                     String authToken, String server) throws Exception {
        setup(neuralNetConfig, sourceName, modelDirectory, authToken, server);
        try {
            objectDetector = new ObjectDetector(threshold, pbFile, labelsFile, metaFile, anchorArray,
                    imageUtil, outputDir, labelImage, saveRate, vantiq, sourceName);
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
       if (neuralNet.get(PB_FILE) instanceof String && 
               (neuralNet.get(LABEL_FILE) instanceof String || neuralNet.get(META_FILE) instanceof String)) {
           if (!modelDirectory.equals("") && !modelDirectory.endsWith("/") && !modelDirectory.endsWith("\\")) {
               modelDirectory += "/";
           }
           if (neuralNet.get(LABEL_FILE) instanceof String) {
               labelsFile = modelDirectory + (String) neuralNet.get(LABEL_FILE);
               log.warn("A label file has been detected and will be used. Label files are deprecated, we encourage you "
                       + "to use a meta file instead.");
           }  
           if (neuralNet.get(META_FILE) instanceof String) {
               metaFile = modelDirectory + (String) neuralNet.get(META_FILE);
           }
           pbFile = modelDirectory + (String) neuralNet.get(PB_FILE);
           
       } else {
           throw new Exception(this.getClass().getCanonicalName() + ".missingConfig: Invalid Configuration: " 
                   + "A YOLO NeuralNet configuration requires a pbFile and a metaFile and/or labelFile. " 
                   + "pbFile: " + (neuralNet.get(PB_FILE) instanceof String ? (String) neuralNet.get(PB_FILE)  : " ") 
                   + ", metaFile: " + (neuralNet.get(META_FILE) instanceof String ? (String) neuralNet.get(META_FILE)  : " ") 
                   + ", labelFile: " + (neuralNet.get(LABEL_FILE) instanceof String ? (String) neuralNet.get(LABEL_FILE)  : " "));
       }
       
       if (neuralNet.get(THRESHOLD) instanceof Number) {
           Number threshNum = (Number) neuralNet.get(THRESHOLD);
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
       if (neuralNet.get(ANCHORS) instanceof List) {
           List tempAnchorList = (List) neuralNet.get(ANCHORS);
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
       } else if (neuralNet.get(ANCHORS) != null){
           if (!(neuralNet.get(META_FILE) instanceof String)) {
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
       if (neuralNet.get(SAVE_IMAGE) instanceof String) {
           saveImage = (String) neuralNet.get(SAVE_IMAGE);
           
           // Check if user wants to save original image without labels
           if (neuralNet.get(LABEL_IMAGE) instanceof String) {
               String labelImageString = (String) neuralNet.get(LABEL_IMAGE);
               labelImage = labelImageString.equalsIgnoreCase("true");
           }
           
           // Check which method of saving the user requests
           if (!saveImage.equalsIgnoreCase(VANTIQ) && !saveImage.equalsIgnoreCase(BOTH) && !saveImage.equalsIgnoreCase(LOCAL)) {
               log.error("The config value for saveImage was invalid. Images will not be saved.");
           }
           if (!saveImage.equalsIgnoreCase(VANTIQ)) {
               if (neuralNet.get(OUTPUT_DIR) instanceof String) {
                   outputDir = (String) neuralNet.get(OUTPUT_DIR);
               }
           }
           if (saveImage.equalsIgnoreCase(VANTIQ) || saveImage.equalsIgnoreCase(BOTH)) {
               vantiq = new io.vantiq.client.Vantiq(server);
               vantiq.setAccessToken(authToken);

               // Check if images should be uploaded to VANTIQ as VANTIQ IMAGES
               if (neuralNet.get(UPLOAD_AS_IMAGE) instanceof Boolean && (Boolean) neuralNet.get(UPLOAD_AS_IMAGE)) {
                   uploadAsImage = (Boolean) neuralNet.get(UPLOAD_AS_IMAGE);
               }
           }
           
           // Check if any resolution configurations have been set
           if (neuralNet.get(SAVED_RESOLUTION) instanceof Map) {
               Map savedResolution = (Map) neuralNet.get(SAVED_RESOLUTION);
               if (savedResolution.get(LONG_EDGE) instanceof Integer) {
                   int longEdge = (Integer) savedResolution.get(LONG_EDGE);
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
           imageUtil.uploadAsImage = uploadAsImage;
           if (neuralNet.get(SAVE_RATE) instanceof Integer) {
               saveRate = (Integer) neuralNet.get(SAVE_RATE);
           }
       } else {
           // Flag to mark that we should not save images
           imageUtil.saveImage = false;
       }
       
       // Checking if pre cropping was specified in config
       if (neuralNet.get(CROP_BEFORE) instanceof Map) {
           Map preCrop = (Map) neuralNet.get(CROP_BEFORE);
           if (preCrop.get(X) instanceof Integer && (Integer) preCrop.get(X) >= 0) {
               x = (Integer) preCrop.get(X);
           }
           if (preCrop.get(Y) instanceof Integer && (Integer) preCrop.get(Y) >= 0) {
               y = (Integer) preCrop.get(Y);
           }
           if (preCrop.get(WIDTH) instanceof Integer && (Integer) preCrop.get(WIDTH) >= 0) {
               w = (Integer) preCrop.get(WIDTH);
           }
           if (preCrop.get(HEIGHT) instanceof Integer && (Integer) preCrop.get(HEIGHT) >= 0) {
               h = (Integer) preCrop.get(HEIGHT);
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
     * Deprecated method, should not be used anymore.
     */
    @Override
    public NeuralNetResults processImage(byte[] image) throws ImageProcessingException {
        log.debug("Deprecated method, should no longer be used.");
        return processImage(null, image);
    }
    
    /**
     * Deprecated method, should not be used anymore.
     */
    @Override
    public NeuralNetResults processImage(byte[] image,  Map<String, ?> request) throws ImageProcessingException {
        log.debug("Deprecated method, should no longer be used.");
        return processImage(null, image, request);
    }
    
    /**
     * Run the image through a YOLO net. May save the resulting image depending on the settings.
     */
    @Override
    public NeuralNetResults processImage(Map<String, ?> processingParams, byte[] image) throws ImageProcessingException {
        Date timestamp;
        if (processingParams != null && processingParams.get(IMAGE_TIMESTAMP) instanceof Date) {
            timestamp = (Date) processingParams.get(IMAGE_TIMESTAMP);
        } else {
            timestamp = new Date();
        }
        List<Map<String, ?>> foundObjects;
        NeuralNetResults results = new NeuralNetResults();
        long after;
        long before = System.currentTimeMillis();
        
        // Pre crop the image if vals were specified
        if (preCropping) {
            image = cropImage(image, x, y, w, h);
        }

        try {
            foundObjects = objectDetector.detect(image, timestamp);
        } catch (IllegalArgumentException e) {
            throw new ImageProcessingException(this.getClass().getCanonicalName() + ".invalidImage: " 
                    + "Data to be processed was invalid. Most likely it was not correctly encoded as a jpg.", e);
        }

        after = System.currentTimeMillis();
        log.debug("Image processing time for source " + sourceName + ": {}.{} seconds"
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
    public NeuralNetResults processImage(Map<String, ?> processingParams, byte[] image, Map<String, ?> request) throws ImageProcessingException {
        List<Map<String, ?>> foundObjects;
        NeuralNetResults results = new NeuralNetResults();
        String saveImage = null;
        String outputDir = null;
        String fileName = null;
        Vantiq vantiq = null;
        boolean uploadAsImage = false;

        Date timestamp;
        if (processingParams != null && processingParams.get(IMAGE_TIMESTAMP) instanceof Date) {
            timestamp = (Date) processingParams.get(IMAGE_TIMESTAMP);
        } else {
            timestamp = new Date();
        }
        fileName = format.format(timestamp);

        if (request.get(NN_SAVE_IMAGE) instanceof String) {
            saveImage = (String) request.get(NN_SAVE_IMAGE);
            if (!saveImage.equalsIgnoreCase(VANTIQ) && !saveImage.equalsIgnoreCase(BOTH) && !saveImage.equalsIgnoreCase(LOCAL)) {
                log.error("The config value for saveImage was invalid. Images will not be saved.");
            } else {
                if (saveImage.equalsIgnoreCase(VANTIQ) || saveImage.equalsIgnoreCase(BOTH)) {
                    vantiq = new io.vantiq.client.Vantiq(server);
                    vantiq.setAccessToken(authToken);

                    // Check if images should be uploaded to VANTIQ as VANTIQ IMAGES
                    if (request.get(UPLOAD_AS_IMAGE) instanceof Boolean && (Boolean) request.get(UPLOAD_AS_IMAGE)) {
                        uploadAsImage = (Boolean) request.get(UPLOAD_AS_IMAGE);
                    }
                }
                if (!saveImage.equalsIgnoreCase(VANTIQ)) {
                    if (request.get(NN_OUTPUT_DIR) instanceof String) {
                        outputDir = (String) request.get(NN_OUTPUT_DIR);
                    }
                }
                if (request.get(NN_FILENAME) instanceof String) {
                    fileName = (String) request.get(NN_FILENAME);
                }
            }
        }
                
        // Checking if pre cropping was specified in query parameters
        boolean queryCrop = false;
        int x, y, w, h;
        x = y = w = h = -1;
        if (request.get(CROP_BEFORE) instanceof Map) {
            Map preCrop = (Map) request.get(CROP_BEFORE);
            if (preCrop.get(X) instanceof Integer && (Integer) preCrop.get(X) >= 0) {
                x = (Integer) preCrop.get(X);
            }
            if (preCrop.get(Y) instanceof Integer && (Integer) preCrop.get(Y) >= 0) {
                y = (Integer) preCrop.get(Y);
            }
            if (preCrop.get(WIDTH) instanceof Integer && (Integer) preCrop.get(WIDTH) >= 0) {
                w = (Integer) preCrop.get(WIDTH);
            }
            if (preCrop.get(HEIGHT) instanceof Integer && (Integer) preCrop.get(HEIGHT) >= 0) {
                h = (Integer) preCrop.get(HEIGHT);
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
        
        if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
            fileName += ".jpg";
        }
        
        long after;
        long before = System.currentTimeMillis();
        try {
            foundObjects = objectDetector.detect(image, outputDir, fileName, vantiq, uploadAsImage);
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
