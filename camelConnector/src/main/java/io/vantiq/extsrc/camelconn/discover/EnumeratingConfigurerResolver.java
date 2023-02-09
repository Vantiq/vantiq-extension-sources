
/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.PropertyConfigurer;

/**
 * This class is used to as part of our discovery process.  This can load a no-op configurer since we don't
 * want any real configuration (inside camel) to happen during discovery. During the "real" startup, this is not used.
 */
public class EnumeratingConfigurerResolver implements ConfigurerResolver {
    @Override
    public PropertyConfigurer resolvePropertyConfigurer(String name, CamelContext context) {
        return new NoopConfigurer();
    }
}
