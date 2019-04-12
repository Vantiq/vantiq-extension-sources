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
public class DestinationNotConfiguredException extends Exception {
    
    public DestinationNotConfiguredException() {
        super();
    }

    public DestinationNotConfiguredException(String message) {
        super(message);
    }
    
    public DestinationNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
