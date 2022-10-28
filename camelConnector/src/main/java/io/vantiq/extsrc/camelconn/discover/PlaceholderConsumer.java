/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

import java.util.concurrent.ExecutorService;

/**
 * This consumer stands in for a "real" consumer, allowing route setup to proceed and, thus, enumerate all the
 * components necessary.  See {@link EnumeratingComponentResolver} for further explanation.
 */
@Slf4j
public class PlaceholderConsumer extends DefaultConsumer {
    private final PlaceholderEndpoint endpoint;
    private ExecutorService executorService;
    
    public PlaceholderConsumer(PlaceholderEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
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
    
    private void processMessage(Object msg) {
        // noop
    }
}
