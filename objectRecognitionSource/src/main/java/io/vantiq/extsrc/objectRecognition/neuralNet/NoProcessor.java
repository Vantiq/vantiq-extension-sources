/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.util.ImageUtil;
import io.vantiq.client.Vantiq;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

public class NoProcessor implements NeuralNetInterface {

    // Constants for Source Configuration options
    private static final String SAVE_IMAGE = "saveImage";
    private static final String BOTH = "both";
    private static final String LOCAL = "local";
    private static final String VANTIQ = "vantiq";
    private static final String OUTPUT_DIR = "outputDir";
    private static final String SAVE_RATE = "saveRate";
    private static final String UPLOAD_AS_IMAGE = "uploadAsImage";

    // Constants for Query Parameter options
    private static final String NN_OUTPUT_DIR = "NNoutputDir";
    private static final String NN_FILENAME = "NNfileName";
    private static final String NN_SAVE_IMAGE = "NNsaveImage";
    
    // This will be used to create
    // "year-month-date-hour-minute-seconds"
    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");
    
    public String lastFilename;
    public Boolean isSetup = false;
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    String outputDir = null;
    String saveImage = null;
    Vantiq vantiq;
    String server;
    String authToken;
    String sourceName;
    ImageUtil imageUtil;
    int saveRate = 1;
    int frameCount = 0;
    int fileCount = 0; // Used for saving files with same name
    boolean uploadAsImage = false;

    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String sourceName, String modelDirectory, String authToken, String server) {
        this.server = server;
        this.authToken = authToken;
        this.sourceName = sourceName;
        
        // Setup the variables for saving images
        imageUtil = new ImageUtil();
        if (neuralNetConfig.get(SAVE_IMAGE) instanceof String) {
            saveImage = (String) neuralNetConfig.get(SAVE_IMAGE);
            
            // Check which method of saving the user requests
            if (!saveImage.equalsIgnoreCase(VANTIQ) && !saveImage.equalsIgnoreCase(BOTH) && !saveImage.equalsIgnoreCase(LOCAL)) {
                log.error("The config value for saveImage was invalid. Images will not be saved.");
            }
            if (!saveImage.equalsIgnoreCase(VANTIQ)) {
                if (neuralNetConfig.get(OUTPUT_DIR) instanceof String) {
                    outputDir = (String) neuralNetConfig.get(OUTPUT_DIR);
                }
            }
            if (saveImage.equalsIgnoreCase(VANTIQ) || saveImage.equalsIgnoreCase(BOTH)) {
                vantiq = new io.vantiq.client.Vantiq(server);
                vantiq.setAccessToken(authToken);

                // Check if images should be uploaded to VANTIQ as VANTIQ IMAGES
                if (neuralNetConfig.get(UPLOAD_AS_IMAGE) instanceof Boolean && (Boolean) neuralNetConfig.get(UPLOAD_AS_IMAGE)) {
                    uploadAsImage = (Boolean) neuralNetConfig.get(UPLOAD_AS_IMAGE);
                }
            }
            imageUtil.outputDir = outputDir;
            imageUtil.vantiq = vantiq;
            imageUtil.saveImage = true;
            imageUtil.sourceName = sourceName;
            imageUtil.uploadAsImage = uploadAsImage;
            if (neuralNetConfig.get(SAVE_RATE) instanceof Integer) {
                saveRate = (Integer) neuralNetConfig.get(SAVE_RATE);
                frameCount = saveRate;
            }
        } else {
            // Flag to mark that we should not save images
            imageUtil.saveImage = false;
            log.info("The Neural Net Config did not specify a method of saving images. No images will be saved when polling. "
                    + "If allowQueries is set, then the user can query the source and save images based on the query options.");
        }
        
        isSetup = true;
    }

    // Does no processing, just saves images
    @Override
    public NeuralNetResults processImage(byte[] image) throws ImageProcessingException {
        if (imageUtil.saveImage && ++frameCount >= saveRate) {
            Date now = new Date(); // Saves the time before
            BufferedImage buffImage = imageUtil.createImageFromBytes(image);
            String fileName = format.format(now);
            // If filename is same as previous name, add parentheses containing the count
            if (lastFilename != null && lastFilename.contains(fileName)) {
                fileName = fileName + "(" + ++fileCount + ").jpg";
            } else {
                fileName = fileName + ".jpg";
                fileCount = 0;
            }
            lastFilename = fileName;
            imageUtil.saveImage(buffImage, fileName);
            frameCount = 0;
        }
        NeuralNetResults emptyResults = new NeuralNetResults();
        return emptyResults;
    }

    // Does no processing, just saves images
    @Override
    public NeuralNetResults processImage(byte[] image, Map<String, ?> request) throws ImageProcessingException {
        String saveImage = null;
        String outputDir = null;
        String fileName = null;
        Vantiq vantiq = null;
        boolean uploadAsImage = false;
        ImageUtil queryImageUtil = new ImageUtil();
        
        if (request.get(NN_SAVE_IMAGE) instanceof String) {
            saveImage = (String) request.get(NN_SAVE_IMAGE);
            if (!saveImage.equalsIgnoreCase(VANTIQ) && !saveImage.equalsIgnoreCase(BOTH) && !saveImage.equalsIgnoreCase(LOCAL)) {
                log.error("The config value for saveImage was invalid. Images will not be saved.");
                queryImageUtil.saveImage = false;
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
                    if (!fileName.endsWith(".jpg")) {
                        fileName = fileName + ".jpg";
                    }
                }
                queryImageUtil.outputDir = outputDir;
                queryImageUtil.vantiq = vantiq;
                queryImageUtil.saveImage = true;
                queryImageUtil.sourceName = sourceName;
                queryImageUtil.uploadAsImage = uploadAsImage;
            }
        } else {
            queryImageUtil.saveImage = false;
        }
        
        if (queryImageUtil.saveImage) {
            Date now = new Date(); // Saves the time before
            BufferedImage buffImage = imageUtil.createImageFromBytes(image);
            if (fileName == null) {
                fileName = format.format(now) + ".jpg";
            }
            lastFilename = fileName;
            queryImageUtil.saveImage(buffImage, fileName);
        } else {
            lastFilename = null;
        }
        
        NeuralNetResults emptyResults = new NeuralNetResults();
        List emptyList = new ArrayList();
        emptyResults.setResults(emptyList);
        emptyResults.setLastFilename("objectRecognition/" + sourceName + "/" + lastFilename);
        return emptyResults;
    }

    @Override
    public void close() {
        // Nothing to close here
    }

}
