package io.vantiq.extsrc.objectRecognition.exception;

import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;

/**
 * An exception that signifies an image could not be found for an {@link ImageRetrieverInterface}
 */
public class ImageAcquisitionException extends Exception {

    public ImageAcquisitionException(String message) {
        super(message);
    }
    
    public ImageAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }

   
    private static final long serialVersionUID = 1L;
}
