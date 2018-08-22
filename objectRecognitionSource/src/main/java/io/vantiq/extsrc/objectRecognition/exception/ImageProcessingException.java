
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.exception;

import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

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

    private static final long serialVersionUID = 1L;

}
