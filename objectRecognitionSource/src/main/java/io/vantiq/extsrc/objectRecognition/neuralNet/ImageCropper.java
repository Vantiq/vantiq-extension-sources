package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ImageCropper {
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    
    /**
     * A helper method used to crop images before they are run through the Neural Net, if specified in the
     * source configuration or as query parameters. Returns the original image if an exception was caught while
     * cropping.
     * @param image     The byte array representation of the captured image
     * @param x         The top left x coordinate
     * @param y         The top left y coordinate
     * @param w         The width of the "sub image"
     * @param h         The height of the "sub image"
     * @return          The cropped image resulting from the parameters above
     */
    public byte[] cropImage(byte[] image, int x, int y, int w, int h) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(image);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();){
            
            // Convert byte[] to BufferedImage and crop
            BufferedImage buffImage = ImageIO.read(bais);
            BufferedImage croppedImage = buffImage.getSubimage(x, y, w, h);
            
            // Convert BufferedImage to byte[] and save it
            ImageIO.write(croppedImage, "jpg", baos);
            baos.flush();
            image = baos.toByteArray();
        } catch (IOException e) {
            log.error("An error occured when trying to crop the captured image. Image will not be cropped.");
        } catch (RasterFormatException e) {
            log.error("An error occured when trying to crop the captured image. This most likely occured because "
                    + "the values given for cropping resulted in coordinates outside of the image. Image will not be cropped");
        } catch (Exception e) {
            log.error("An unexpected error occured while trying to crop the captured image. Image will not be cropped");
        }
        
        return image;
    }
}
