/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertyConfigurer;

/**
 * Allow any properties to be configured.  This allows discovery to proceed.  Once complete & all classes are found,
 * the regular Camel property configurer will be used.
 */
public class NoopConfigurer implements PropertyConfigurer {
    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        // We are not picky -- we accept anything
        return true;
    }
}
