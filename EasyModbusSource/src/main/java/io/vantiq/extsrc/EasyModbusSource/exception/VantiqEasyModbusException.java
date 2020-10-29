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
public class VantiqEasyModbusException extends Exception {

    public VantiqEasyModbusException() {
        super();
    }

    public VantiqEasyModbusException(String message) {
        super(message);
    }

    public VantiqEasyModbusException(String message, Throwable cause) {
        super(message, cause);
    }
}