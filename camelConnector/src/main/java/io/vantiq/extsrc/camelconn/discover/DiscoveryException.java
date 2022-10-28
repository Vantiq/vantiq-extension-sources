/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

/**
 * Marker for exceptions found during the discovery process/
 */
public class DiscoveryException extends Exception {
    public DiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DiscoveryException(String message) {
        super(message);
    }
}
