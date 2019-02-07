package edu.ml.tensorflow.util;

import edu.ml.tensorflow.Config;
import edu.ml.tensorflow.model.BoxPosition;
import edu.ml.tensorflow.model.Recognition;
import io.vantiq.client.BaseResponseHandler;
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
    public Boolean queryResize = false;
    public int longEdge = 0;

    /**
     * Label image with classes and predictions given by the TensorFLow YOLO Implementation
     * @param image         buffered image to label
     * @param recognitions  list of recognized objects
     */
    public BufferedImage labelImage(BufferedImage bufferedImage, final List<Recognition> recognitions) {
        float scaleX = (float) bufferedImage.getWidth() / (float) Config.FRAME_SIZE;
        float scaleY = (float) bufferedImage.getHeight() / (float) Config.FRAME_SIZE;
        Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();

        for (Recognition recognition: recognitions) {
            BoxPosition box = recognition.getScaledLocation(scaleX, scaleY);
            //draw text
            graphics.drawString(recognition.getTitle() + " " + recognition.getConfidence(), box.getLeft(), box.getTop() - 7);
            // draw bounding box
            graphics.drawRect(box.getLeftInt(),box.getTopInt(), box.getWidthInt(), box.getHeightInt());
        }

        graphics.dispose();
        return bufferedImage;
    }

    /**
     * Saves an image to a location, expected to be in the directory specified in the constructor.
     * <br>Edited so that outDir will be recreated if deleted while running, vs making sure it exists initially
     * and erroring out if it disappears in the interim 
     * @param image     The image to save
     * @param target    The name of the file to be written
     */
    public void saveImage(final BufferedImage image, final String target) {
        File fileToUpload = null;
        if (outputDir != null) {
            try {
                IOUtil.createDirIfNotExists(new File(outputDir));
                if (longEdge == 0) {
                    ImageIO.write(image, "jpg", new File(outputDir + File.separator + target));
                } else {
                    BufferedImage resizedImage = resizeImage(image);
                    ImageIO.write(resizedImage, "jpg", new File(outputDir + File.separator + target));
                }
                fileToUpload = new File(outputDir + File.separator + target);
            } catch (IOException e) {
                LOGGER.error("Unable to save image {}", target, e);
            }
        }
        if (vantiq != null) {
            if (fileToUpload != null) {
                uploadImage(fileToUpload, target);
            } else {
                try {
                    File imgFile = File.createTempFile("tmp", ".jpg");
                    imgFile.deleteOnExit();
                    if (longEdge == 0) {
                        ImageIO.write(image, "jpg", imgFile);
                    } else {
                        BufferedImage resizedImage = resizeImage(image);
                        ImageIO.write(resizedImage, "jpg", imgFile);
                    }
                    uploadImage(imgFile, target);
                } catch (IOException e) {
                    LOGGER.error("Unable to save image {}", target, e);
                }
            }
        }
    }
    
    /**
     * A method used to upload images to VANTIQ, using the VANTIQ SDK
     * @param imgFile   The file to be uploaded. If not specified to save locally, this file will be deleted.
     * @param target    The name of the file to be uploaded.
     */
    public void uploadImage(File imgFile, String target) {
        File fileToUpload = imgFile;
        if (queryResize) {
            try {
                BufferedImage resizedImage = resizeImage(ImageIO.read(imgFile));
                File tmpFile = File.createTempFile("tmp", ".jpg");
                tmpFile.deleteOnExit();
                ImageIO.write(resizedImage, "jpg", tmpFile);
                fileToUpload = tmpFile;
            } catch (IOException e) {
                LOGGER.error("An error occured while reading and/or resizing the locally saved image file. " + e.getMessage());
            }
        }
        uploadToVantiq(fileToUpload, target);
    }
    
    /**
     * A helper method called by uploadImage that uses VANTIQ SDK to upload the image.
     * @param fileToUpload  File to be uploaded.
     * @param target        The name of the file to be uploaded.
     */
    public void uploadToVantiq(File fileToUpload, String target) {
        vantiq.upload(fileToUpload, 
                "image/jpeg", 
                "objectRecognition/" + sourceName + '/'  + target,
                new BaseResponseHandler() {
                    @Override public void onSuccess(Object body, Response response) {
                        super.onSuccess(body, response);
                        LOGGER.trace("Content Location = " + this.getBodyAsJsonObject().get("content"));
                        
                        if (outputDir == null) {
                            deleteImage(fileToUpload);
                        }
                    }
                    
                    @Override public void onError(List<VantiqError> errors, Response response) {
                        super.onError(errors, response);
                        LOGGER.error("Errors uploading image with VANTIQ SDK: " + errors);
                    }
        });
    }
    
    /**
     * A method used to delete locally saved images.
     * @param imgFile  The file to be deleted.
     */
    public void deleteImage(File imgFile) {
        if(imgFile.delete()) {
            LOGGER.trace("File was successfully deleted.");
        } else {
            LOGGER.error("Failed to delete file");
        }
    }
    
    /**
     * A function used to resize the original captured image, if requested.
     * @param image     The original image to be resized.
     * @return          The resized image.
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
                double ratio = (double) longEdge/width;
                int newHeight = (int) (height * ratio);
                resizedImage = resizeHelper(longEdge, newHeight, image);
            } else if (height > width) {
                double ratio = (double) longEdge/height;
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
     * @param width     The new width of the resized image.
     * @param height    The new height of the resized image.
     * @param image     The original image to be resized.
     * @return          The resized image.
     */
    public BufferedImage resizeHelper(int width, int height, BufferedImage image) {
        BufferedImage resizedImage = new BufferedImage(width, height, image.getType());
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resizedImage;
    }

    public BufferedImage createImageFromBytes(final byte[] imageData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        try {
            return ImageIO.read(bais);
        } catch (IOException ex) {
            throw new ServiceException("Unable to create image from bytes!", ex);
        }
    }
}