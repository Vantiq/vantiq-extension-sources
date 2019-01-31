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
    public int longEdge = 0;

    /**
     * Label image with classes and predictions given by the ThensorFLow
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
                    ImageIO.write(image,"jpg", new File(outputDir + File.separator + target));
                } else {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (longEdge > Math.max(width, height)) {
                        LOGGER.error("The longEdge value is too large, resizing cannot enlarge the original image. "
                                + "Original image resolution will not be changed");
                        ImageIO.write(image,"jpg", new File(outputDir + File.separator + target));
                        longEdge = 0;
                    } else {
                        if (width > height) {
                            double ratio = (double) longEdge/width;
                            int newHeight = (int) (height * ratio);
                            BufferedImage resizedImage = resizeImage(longEdge, newHeight, image);
                            ImageIO.write(resizedImage,"jpg", new File(outputDir + File.separator + target));
                        } else if (height > width) {
                            double ratio = (double) longEdge/height;
                            int newWidth = (int) (width * ratio);
                            BufferedImage resizedImage = resizeImage(newWidth, longEdge, image);
                            ImageIO.write(resizedImage,"jpg", new File(outputDir + File.separator + target));
                        } else {
                            BufferedImage resizedImage = resizeImage(longEdge, longEdge, image);
                            ImageIO.write(resizedImage,"jpg", new File(outputDir + File.separator + target));
                        }
                    }
                }
                fileToUpload = new File(outputDir + File.separator + target);
            } catch (IOException e) {
                LOGGER.error("Unable to save image {}", target, e);
            }
        }
        if (vantiq != null) {
            if (fileToUpload != null) {
                vantiq.upload(fileToUpload, 
                        "image/jpeg", 
                        "objectRecognition/" + sourceName + '/' + target,
                        new BaseResponseHandler() {
                            @Override public void onSuccess(Object body, Response response) {
                                super.onSuccess(body, response);
                                LOGGER.trace("Content Location = " + this.getBodyAsJsonObject().get("content"));
                            }
                            
                            @Override public void onError(List<VantiqError> errors, Response response) {
                                super.onError(errors, response);
                                LOGGER.error("Errors uploading image with VANTIQ SDK: " + errors);
                            }
                });
            } else {
                try {
                    File imgFile = File.createTempFile("tmp", ".jpg");
                    imgFile.deleteOnExit();
                    if (longEdge == 0) {
                        ImageIO.write(image, "jpg", imgFile);
                    } else {
                        int width = image.getWidth();
                        int height = image.getHeight();
                        if (longEdge > Math.max(width, height)) {
                            LOGGER.error("The longEdge value is too large, resizing cannot enlarge the original image. "
                                    + "Original image resolution will not be changed");
                            ImageIO.write(image,"jpg", imgFile);
                            longEdge = 0;
                        } else {
                            if (width > height) {
                                double ratio = (double) longEdge/width;
                                int newHeight = (int) (height * ratio);
                                BufferedImage resizedImage = resizeImage(longEdge, newHeight, image);
                                ImageIO.write(resizedImage,"jpg", imgFile);
                            } else if (height > width) {
                                double ratio = (double) longEdge/height;
                                int newWidth = (int) (width * ratio);
                                BufferedImage resizedImage = resizeImage(newWidth, longEdge, image);
                                ImageIO.write(resizedImage,"jpg", imgFile);
                            } else {
                                BufferedImage resizedImage = resizeImage(longEdge, longEdge, image);
                                ImageIO.write(resizedImage,"jpg", imgFile);
                            }
                        }
                    }
                    vantiq.upload(imgFile, 
                            "image/jpeg", 
                            "objectRecognition/" + sourceName + '/'  + target,
                            new BaseResponseHandler() {
                                @Override public void onSuccess(Object body, Response response) {
                                    super.onSuccess(body, response);
                                    LOGGER.trace("Content Location = " + this.getBodyAsJsonObject().get("content"));
                                    if(imgFile.delete()) { 
                                        LOGGER.trace("Temp file deleted successfully"); 
                                    } else { 
                                        LOGGER.warn("Failed to delete temp file"); 
                                    } 
                                }
                                
                                @Override public void onError(List<VantiqError> errors, Response response) {
                                    super.onError(errors, response);
                                    LOGGER.error("Errors uploading image with VANTIQ SDK: " + errors);
                                }
                    });
                } catch (IOException e) {
                    LOGGER.error("Unable to save image {}", target, e);
                }
            }
        }
    }
    
    /**
     * A function used to resize the original captured image, if requested.
     * @param width     The resized width of the image to be saved.
     * @param height    The resized height of the image to be saved.
     * @param image     The original image to be resized.
     * @return          The resized image.
     */
    public BufferedImage resizeImage(int width, int height, BufferedImage image) {
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