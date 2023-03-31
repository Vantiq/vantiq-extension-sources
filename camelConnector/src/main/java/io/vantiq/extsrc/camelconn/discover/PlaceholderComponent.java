/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.Endpoint;
import org.apache.camel.component.bean.BeanComponent;

import java.util.Map;

/**
 * This component is a placeholder used to allow the connector to gather the set of components it will need.
 * This component has sufficient content to allow the route building (but not starting) to complete, allowing
 * the connector to determine the set of components it will need (via the {@link EnumeratingComponentResolver}).
 *
 * Note that rather than simply extending the Default Component, we extend the BeanComponent instead.  For non-bean
 * components, this is harmless as BeanComponent, in turn, extends DefaultComponent.  And the method overrides
 * provided herein keep anything from starting up anyway (we are only doing this to satisfy Camel that things are
 * basically runnable.
 *
 * For things where Camel expects a BeanComponent, it checks that the component instance IsA BeanComponent,
 * complaining vociferously if that's not the case and shutting everything down (See issue
 * (<a href="https://github.com/Vantiq/vantiq-extension-sources/issues/372">#372</a>.)
 * With this change, non-bean component discovery continues to operate as expected, and bean component discovery
 * now succeeds.
 **/
class PlaceholderComponent extends BeanComponent {
    
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
    
    /**
     * This is a placeholder only, used to facilitate our discovery of necessary components.
     */
    @Override
    protected void doShutdown() {
        // noop
    }
}
