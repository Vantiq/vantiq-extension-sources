/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;

import java.io.InputStream;
import java.io.OutputStream;

public class PlaceholderDataFormat implements DataFormat {
    
    /**
     * This is a placeholder only, used to facilitate our discovery of necessary DataFormats.
     */
    @Override
    public void start() {
        // noop
    }
    
    @Override
    public void stop() {
        // noop
    }
    
    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
    
    }
    
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return null;
    }
}
