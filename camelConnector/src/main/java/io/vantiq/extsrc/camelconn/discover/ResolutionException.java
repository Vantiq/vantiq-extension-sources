/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

/**
 * Marker for exceptions found during the component resolution process.
 */
public class ResolutionException extends Exception {
    public ResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ResolutionException(String message) {
        super(message);
    }
}
