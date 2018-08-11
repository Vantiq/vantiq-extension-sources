package io.vantiq.extsrc.objectRecognition.exception;

import io.vantiq.extsrc.objectRecognition.DataRetrieverInterface;

/**
 * An exception that signifies an image could not be found for a {@link DataRetrieverInterface}
 */
public class ImageAcquisitionException extends Exception {

    public ImageAcquisitionException(String message) {
        super(message);
    }
    
    public ImageAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 
     */
    private static final long serialVersionUID = -1191996065278592294L;
}
