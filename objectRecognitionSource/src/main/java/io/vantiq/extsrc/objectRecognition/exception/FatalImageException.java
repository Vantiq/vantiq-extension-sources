package io.vantiq.extsrc.objectRecognition.exception;

import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

/**
 * Indicates that an unrecoverable error occurred for either a {@link ImageRetrieverInterface} or a
 * {@link NeuralNetInterface}
 */
public class FatalImageException extends RuntimeException {

    public FatalImageException(String message) {
        super(message);
    }
    
    public FatalImageException(String message, Throwable cause) {
        super(message, cause);
    }
    
    private static final long serialVersionUID = 1925046953244621083L;
}
