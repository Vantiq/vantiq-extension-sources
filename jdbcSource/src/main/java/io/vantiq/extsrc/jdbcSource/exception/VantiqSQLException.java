/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource.exception;

/**
 * An exception that signifies an image could not be found for an {@link ImageRetrieverInterface}
 */
public class VantiqSQLException extends Exception {
    
    public VantiqSQLException() {
        super();
    }

    public VantiqSQLException(String message) {
        super(message);
    }
    
    public VantiqSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
