/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

/**
 * Placeholder producer to accompany {@link PlaceholderComponent}, {@link PlaceholderConsumer}, and
 * {@link PlaceholderEndpoint} in support of gathering the names of the required components via
 * the {@link EnumeratingComponentResolver}.  There is sufficient mechanism here to convince the CamelContext to
 * continue to add and start routes, allowing the resolution to continue to completion.
 *
 */
@Slf4j
public class PlaceholderProducer extends DefaultProducer {
    private final PlaceholderEndpoint endpoint;
    
    public PlaceholderProducer(PlaceholderEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        // noop since we won't get this far.
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }
    
    @Override
    protected void doStop() throws Exception {
        if (null != endpoint) {
            endpoint.shutdown();
        }
        super.doStop();
    }
}
