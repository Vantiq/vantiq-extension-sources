/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.llrpConnector.exception;

/**
 * A custom exception used to extract the useful information from a LLRP Exception
 * - InvalidLLRPMessageException
 * - MissingParameterException
 */
public class VantiqLLRPException extends Exception {

    private static final long serialVersionUID = 1L;

    public VantiqLLRPException() {
        super();
    }

    public VantiqLLRPException(String message) {
        super(message);
    }

    public VantiqLLRPException(String message, Throwable cause) {
        super(message, cause);
    }

}