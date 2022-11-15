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

public class VantiqEmptyURITest extends VantiqUriTestBase {
    
    private final String routeStartUri = "direct:start";
    private final String routeEndUri = "mock:direct:result";
    
    FauxVantiqEndpoint fve = null;
    FauxVantiqEndpoint fve2 = null;
    
    public final String vantiqSrcConfigEnd = "vantiq://" + SERVER_CONFIG_FILENAME;
    public final String vantiqSrcConfigStart = "vantiq://" + SERVER_CONFIG_FILENAME + "/"; // Check both ways

    @Test
    public void testEndpointsWithNullUri() throws Exception {
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        
        checkEndpoint(fve);
        checkEndpoint(fve2);
    
        Collection<Endpoint> eps = context.getEndpoints();
        int vantiqEndpoints = 0;
        for (Endpoint ep: eps) {
            if (ep.getEndpointBaseUri().startsWith("vantiq")) {
                checkEndpoint(ep);
                vantiqEndpoints += 1;
            }
        }
        
        // In the constructed case, these our bean-simulation endpoints get registered manually.  Since they have the
        // same endpointUri, they are, in the registry, the same endpoint (same key)..  So verify that that's
        // true.
        assertEquals("Number of Vantiq endpoints", 1, vantiqEndpoints);
        assertEquals("Number of Endpoints found", 4, eps.size());
    
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
                try {
                    fve = new FauxVantiqEndpoint(null, us);
                    context.addEndpoint(fve.getEndpointUri(), fve);
                    // Note that since the URI's are the same, these are really the same endpoint in the registry
                    fve2 = new FauxVantiqEndpoint(null, us);
                    context.addEndpoint(fve2.getEndpointUri(), fve2);
                } catch (Exception e) {
                    assertNull("Exception while creating endpoints", e);
                }
    
                from(routeStartUri)
                        .to(fve);

                from(fve2)
                        .to(routeEndUri);
            }
        };
    }
}
