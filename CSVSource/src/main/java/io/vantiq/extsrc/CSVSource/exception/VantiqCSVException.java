/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource.exception;

/**
 * A custom exception used to extract the useful information from a Exception
 */
public class VantiqCSVException extends Exception {
    
    public VantiqCSVException() {
        super();
    }

    public VantiqCSVException(String message) {
        super(message);
    }
    
    public VantiqCSVException(String message, Throwable cause) {
        super(message, cause);
    }
}
