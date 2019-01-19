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
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.ml.tensorflow.util.ImageUtil;
import io.vantiq.client.Vantiq;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

public class NoProcessor implements NeuralNetInterface {
    
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

    @Override
    public void setupImageProcessing(Map<String, ?> neuralNetConfig, String sourceName, String modelDirectory, String authToken, String server) {
        this.server = server;
        this.authToken = authToken;
        this.sourceName = sourceName;
        
        // Setup the variables for saving images
        imageUtil = new ImageUtil();
        if (neuralNetConfig.get("saveImage") instanceof String) {
            saveImage = (String) neuralNetConfig.get("saveImage");
            
            // Check which method of saving the user requests
            if (!saveImage.equalsIgnoreCase("vantiq") && !saveImage.equalsIgnoreCase("both") && !saveImage.equalsIgnoreCase("local")) {
                log.error("The config value for saveImage was invalid. Images will not be saved.");
            }
            if (!saveImage.equalsIgnoreCase("vantiq")) {
                if (neuralNetConfig.get("outputDir") instanceof String) {
                    outputDir = (String) neuralNetConfig.get("outputDir");
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
            if (neuralNetConfig.get("saveRate") instanceof Integer) {
                saveRate = (Integer) neuralNetConfig.get("saveRate");
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
            String fileName = format.format(now) + ".jpg";
            lastFilename = fileName;
            imageUtil.saveImage(buffImage, fileName);
            frameCount = 0;
        } else {
            lastFilename = null;
        }
        return null;
    }

    // Does no processing, just saves images
    @Override
    public NeuralNetResults processImage(byte[] image, Map<String, ?> request) throws ImageProcessingException {
        String saveImage = null;
        String outputDir = null;
        String fileName = null;
        Vantiq vantiq = null;
        ImageUtil queryImageUtil = new ImageUtil();
        
        if (request.get("NNsaveImage") instanceof String) {
            saveImage = (String) request.get("NNsaveImage");
            if (!saveImage.equalsIgnoreCase("vantiq") && !saveImage.equalsIgnoreCase("both") && !saveImage.equalsIgnoreCase("local")) {
                log.error("The config value for saveImage was invalid. Images will not be saved.");
                queryImageUtil.saveImage = false;
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
                queryImageUtil.outputDir = outputDir;
                queryImageUtil.vantiq = vantiq;
                queryImageUtil.saveImage = true;
                queryImageUtil.sourceName = sourceName;
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
            imageUtil.saveImage(buffImage, fileName);
        } else {
            lastFilename = null;
        }
        
        
        return null;
    }

    @Override
    public void close() {
        // Nothing to close here
    }

}
