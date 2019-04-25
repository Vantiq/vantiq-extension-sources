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
public class FailedInterfaceSetupException extends Exception {
    
    public FailedInterfaceSetupException() {
        super();
    }

    public FailedInterfaceSetupException(String message) {
        super(message);
    }
    
    public FailedInterfaceSetupException(String message, Throwable cause) {
        super(message, cause);
    }
}
