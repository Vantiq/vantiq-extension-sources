/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.exceptions;

/**
 * A custom exception used to signify that a requested queue was not configured
 */
public class UnsupportedJMSMessageTypeException extends Exception {
    
    public UnsupportedJMSMessageTypeException() {
        super();
    }

    public UnsupportedJMSMessageTypeException(String message) {
        super(message);
    }
    
    public UnsupportedJMSMessageTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
