/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */
package io.vantiq.extsrc.HikVisionSource;

//import edu.ml.tensorflow.model.BoxPosition;
//import edu.ml.tensorflow.model.Recognition;
import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.ResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import okhttp3.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Util class for image processing.
 */
public class ImageUtil {
    private final static Logger LOGGER = LoggerFactory.getLogger(ImageUtil.class);
    public String outputDir = null; // Added to remember the output dir for each instance
    public Vantiq vantiq = null; // Added to allow image saving with VANTIQ
    public String sourceName = null;
    public Boolean saveImage;
    public int frameSize;
    public Boolean queryResize = false;
    public int longEdge = 0;
    public Boolean uploadAsImage = false;

    // Used to upload image to VANTIQ as VANTIQ Image
    final static String IMAGE_RESOURCE_PATH = "/resources/images";

    /**
     * A helper method called by uploadImage that uses VANTIQ SDK to upload the
     * image.
     * 
     * @param fileToUpload          File to be uploaded.
     * @param resoursePath          where to locate in Vantiq documant storage
     * @param target                The name of the file to be uploaded.
     * @param uploadResponseHandler connection to use for uploading the file
     */
    public void uploadToVantiq(File fileToUpload, String resoursePath, String target,
            ResponseHandler uploadResponseHandler) {

        // Create the response handler for either upload option
        BaseResponseHandler responseHandler = new BaseResponseHandler() {
            @Override
            public void onSuccess(Object body, Response response) {
                super.onSuccess(body, response);
                LOGGER.trace("Content Location = " + this.getBodyAsJsonObject().get("content"));

                if (outputDir == null) {
                    // deleteImage(fileToUpload);
                }

                uploadResponseHandler.onSuccess(body, response);
            }

            @Override
            public void onError(List<VantiqError> errors, Response response) {
                super.onError(errors, response);
                uploadResponseHandler.onError(errors, response);
                LOGGER.error("Errors uploading image with VANTIQ SDK: " + errors);
            }
        };

        if (!fileToUpload.isFile()) {
            LOGGER.error("Requested file {} is not exists", fileToUpload.toPath().getFileName());
            return;
        }

        vantiq.upload(fileToUpload, "image/jpeg", target, resoursePath, responseHandler);
    }

    /**
     * A method used to delete locally saved images.
     * 
     * @param imgFile The file to be deleted.
     */
    public void deleteImage(File imgFile) {
        if (imgFile.delete()) {
            LOGGER.trace("File was successfully deleted.");
        } else {
            LOGGER.error("Failed to delete file");
        }
    }

    /**
     * A function used to resize the original captured image, if requested.
     * 
     * @param image The original image to be resized.
     * @return The resized image.
     */
    public BufferedImage resizeImage(BufferedImage image) {
        BufferedImage resizedImage;
        int width = image.getWidth();
        int height = image.getHeight();
        if (longEdge > Math.max(width, height)) {
            LOGGER.trace("The longEdge value is too large, resizing cannot enlarge the original image. "
                    + "Original image resolution will not be changed");
            return image;
        } else {
            if (width > height) {
                double ratio = (double) longEdge / width;
                int newHeight = (int) (height * ratio);
                resizedImage = resizeHelper(longEdge, newHeight, image);
            } else if (height > width) {
                double ratio = (double) longEdge / height;
                int newWidth = (int) (width * ratio);
                resizedImage = resizeHelper(newWidth, longEdge, image);
            } else {
                resizedImage = resizeHelper(longEdge, longEdge, image);
            }
        }

        return resizedImage;
    }

    /**
     * A helper function that resizes the image, called by resizeImage()
     * 
     * @param width  The new width of the resized image.
     * @param height The new height of the resized image.
     * @param image  The original image to be resized.
     * @return The resized image.
     */
    public BufferedImage resizeHelper(int width, int height, BufferedImage image) {
        BufferedImage resizedImage = new BufferedImage(width, height, image.getType());
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }
}