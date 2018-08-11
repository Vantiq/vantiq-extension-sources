package io.vantiq.extsrc.objectRecognition.exception;

import io.vantiq.extsrc.objectRecognition.NeuralNetInterface;

/**
 * An exception that signifies an image could not be processed by a {@link NeuralNetInterface}
 */
public class ImageProcessingException extends Exception {
    
    public ImageProcessingException(String message) {
        super(message);
    }
    
    public ImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 
     */
    private static final long serialVersionUID = -8171851765382657864L;

}
