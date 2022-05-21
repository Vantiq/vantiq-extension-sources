/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource.exception;

/**
 * A custom exception used to extract the useful information from a Exception
 */
public class VantiqFTPClientException extends Exception {
    
    public VantiqFTPClientException() {
        super();
    }

    public VantiqFTPClientException(String message) {
        super(message);
    }
    
    public VantiqFTPClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
