/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.exceptions;

/**
 * A custom exception used to signify a JMS Element was not setup correctly (most likely was returned as null)
 */
public class FailedJMSSetupException extends Exception {
    
    public FailedJMSSetupException() {
        super();
    }

    public FailedJMSSetupException(String message) {
        super(message);
    }
    
    public FailedJMSSetupException(String message, Throwable cause) {
        super(message, cause);
    }
}
