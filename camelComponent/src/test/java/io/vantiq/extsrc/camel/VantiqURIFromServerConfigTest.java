/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import static io.vantiq.extjsdk.Utils.SERVER_CONFIG_FILENAME;

import org.apache.camel.Endpoint;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import java.util.Collection;

public class VantiqURIFromServerConfigTest extends VantiqUriTestBase {
    
    private final String routeStartUri = "direct:start";
    private final String routeEndUri = "mock:direct:result";
    
    public final String vantiqSrcConfigEnd = "vantiq://" + SERVER_CONFIG_FILENAME;
    public final String vantiqSrcConfigStart = "vantiq://" + SERVER_CONFIG_FILENAME + "/"; // Check both ways
    
    @Test
    public void testEndpointsFromSourceConfig() throws Exception {
    
        Collection<Endpoint> eps = context.getEndpoints();
        int vantiqEndpoints = 0;
        for (Endpoint ep: eps) {
            if (ep.getEndpointBaseUri().startsWith("vantiq")) {
                checkEndpoint(ep);
                vantiqEndpoints += 1;
            }
        }
        assert vantiqEndpoints == 2;
        assert eps.size() == 5;
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
    
                FauxVantiqComponent us = new FauxVantiqComponent();
                // Override the component type to be used...
                context.addComponent("vantiq", us);
                for (String name: context.getComponentNames()) {
                    log.info("Component name: {}", name);
                }
                onException(InvalidPayloadException.class)
                        .log(LoggingLevel.ERROR, "Got InvalidPayloadException")
                                .to(exceptionEndpoint);
                
                from(routeStartUri)
                        .to(vantiqSrcConfigEnd);
                
                from(vantiqSrcConfigStart)
                        .to(routeEndUri);
            }
        };
    }
}
