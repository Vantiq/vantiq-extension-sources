/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */
package io.vantiq.extsrc.EasyModbusSource.exception;

/**
 * A custom exception used to extract the useful information from a SQLException
 */
public class VantiqEasymodbusException extends Exception {

    public VantiqEasymodbusException() {
        super();
    }

    public VantiqEasymodbusException(String message) {
        super(message);
    }

    public VantiqEasymodbusException(String message, Throwable cause) {
        super(message, cause);
    }
}