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
    private String outputDir = null; // Added to remember the output dir for each instance
    private Vantiq vantiq = null; // Added to allow image saving with VANTIQ

    /**
     * Edited so that it can be instanced with its own output directory.
     * <br>Edited so that outDir will be recreated if deleted while running, vs making sure it exists initially
     * and erroring out if it disappears in the interim 
     * @param vant      The VANTIQ SDK connection, either authenticated or null
     * @param outDir    The directory to which images will be saved
     */
    public ImageUtil(Vantiq vant, String outDir) {
        outputDir = outDir;
        vantiq = vant;
    }

    /**
     * Label image with classes and predictions given by the ThensorFLow
     * @param image         buffered image to label
     * @param recognitions  list of recognized objects
     * @param fileName      The name of the file to be written
     * @param labelImage    Boolean flag designating if images should be saved with or without bounding boxes
     */
    public void labelImage(final byte[] image, final List<Recognition> recognitions, final String fileName, boolean labelImage) {
        BufferedImage bufferedImage = createImageFromBytes(image);
        // If labelImage is set to true, image is saved with labels
        if (labelImage) {
            float scaleX = (float) bufferedImage.getWidth() / (float) Config.SIZE;
            float scaleY = (float) bufferedImage.getHeight() / (float) Config.SIZE;
            Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
    
            for (Recognition recognition: recognitions) {
                BoxPosition box = recognition.getScaledLocation(scaleX, scaleY);
                //draw text
                graphics.drawString(recognition.getTitle() + " " + recognition.getConfidence(), box.getLeft(), box.getTop() - 7);
                // draw bounding box
                graphics.drawRect(box.getLeftInt(),box.getTopInt(), box.getWidthInt(), box.getHeightInt());
            }
    
            graphics.dispose();
        }
        saveImage(vantiq, bufferedImage, fileName);
    }

    /**
     * Saves an image to a location, expected to be in the directory specified in the constructor.
     * <br>Edited so that outDir will be recreated if deleted while running, vs making sure it exists initially
     * and erroring out if it disappears in the interim 
     * @param vantiq    The VANTIQ SDK connection, either authenticated or null
     * @param image     The image to save
     * @param target    The name of the file to be written
     */
    public void saveImage(Vantiq vantiq, final BufferedImage image, final String target) {
        File fileToUpload = null;
        if (outputDir != null) {
            try {
                IOUtil.createDirIfNotExists(new File(outputDir));
                ImageIO.write(image,"jpg", new File(outputDir + File.separator + target));
                fileToUpload = new File(outputDir + File.separator + target);
            } catch (IOException e) {
                LOGGER.error("Unable to save image {}", target, e);
            }
        }
        if (vantiq != null) {
            if (fileToUpload != null) {
                vantiq.upload(fileToUpload, 
                        "image/jpeg", 
                        target,
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
                    ImageIO.write(image, "jpg", imgFile);
                    vantiq.upload(imgFile, 
                            "image/jpeg", 
                            target,
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

    private BufferedImage createImageFromBytes(final byte[] imageData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        try {
            return ImageIO.read(bais);
        } catch (IOException ex) {
            throw new ServiceException("Unable to create image from bytes!", ex);
        }
    }
}