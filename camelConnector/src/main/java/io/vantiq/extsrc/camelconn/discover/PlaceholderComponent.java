/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

/**
 * This component is a placeholder used to allow the connector to gather the set of components it will need.
 * This component has sufficient content to allow the route building (but not starting) to complete, allowing
 * the connector to determine the set of components it will need (via the {@link EnumeratingComponentResolver}).
 **/
class PlaceholderComponent extends DefaultComponent {
    
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        PlaceholderEndpoint pe = new PlaceholderEndpoint(uri, this);
        getCamelContext().addEndpoint(uri, pe);
        return pe;
    }
    
    @Override
    protected void validateParameters(String uri, Map<String, Object> parameters, String optionPrefix) {
        return;
    }
    
    /**
     * This is a placeholder only, used to facilitate our discovery of necessary components.
     */
    @Override
    protected void doStart() {
        // noop
    }
}
