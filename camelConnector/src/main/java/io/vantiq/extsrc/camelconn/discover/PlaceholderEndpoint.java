/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;

import java.util.Map;

/**
 * This endpoint is a placeholder used to allow the connector to gather the set of components it will need.
 * This endpoint has sufficient content to allow the route building (but not starting) to complete, allowing
 * the connector to determine the set of components it will need (via the {@link EnumeratingComponentResolver}).
 **/

@Slf4j
public class PlaceholderEndpoint extends DefaultEndpoint {
    PlaceholderEndpoint(String uri, PlaceholderComponent component) {
        super(uri, component);
        setLazyStartProducer(true);
    }
    
    @Override
    public void configureProperties(Map<String, Object> options) {
        // This endpoint does nothing & doesn't need any properties.  So just ignore this.
        log.debug("PlaceholderEndpoint for {} called", getEndpointUri());
        return;
    }
    
    @Override
    public Producer createProducer() throws Exception {
        return new PlaceholderProducer(this);
    }
    
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new PlaceholderConsumer(this, processor);
    }
}