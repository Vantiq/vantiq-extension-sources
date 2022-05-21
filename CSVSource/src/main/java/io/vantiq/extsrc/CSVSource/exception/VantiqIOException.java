/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource.exception;

/**
 * A custom exception used to extract the useful information from a SQLException
 */
public class VantiqIOException extends Exception {

    public VantiqIOException() {
        super();
    }

    public VantiqIOException(String message) {
        super(message);
    }

    public VantiqIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
