/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.connector;

/**
 * A custom exception used to extract the useful information from a SQLException
 */
public class VantiqCamelException extends Exception {
    
    public VantiqCamelException() {
        super();
    }
    
    public VantiqCamelException(String message) {
        super(message);
    }
    
    public VantiqCamelException(String message, Throwable cause) {
        super(message, cause);
    }
}