/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource.exception;

/**
 * A custom exception used to extract the useful information from a SQLException
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
