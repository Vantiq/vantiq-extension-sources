/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is used to determine the set of dataformats used to a collection of routes.  It replaces the Camel
 * context's dataformat resolver.  The context, when adding routes, will call createDataFormat() with the scheme/name
 * of the dataformat and the context, with the result expected to be a resolved (class loaded, etc.) dataformat.
 *
 * To accomplish this, this resolver notes the dataformat in use.  It then loads a placeholder dataformat which has
 * enough capability to act as if it has started.  This allows the context to believe that the routes successfully
 * loaded.  The result of this is that this dataformat resolver has the set of dataformats that need to be made
 * available (added to the class path) to allow a "real" dataformat resolver to operate.
 */
@Slf4j
public class EnumeratingDataFormatResolver implements DataFormatResolver {
    
    private final Set<String> dataFormatsToLoad;
    private final Set<String> systemDataFormatsUsed;
    
    private final DataFormatResolver originalResolver;
    
    public static final Set<String> CORE_DATAFORMAT_NAMES = new HashSet<>(List.of());
    
    
    public EnumeratingDataFormatResolver(DataFormatResolver origResolver) {
        dataFormatsToLoad = new HashSet<>();
        systemDataFormatsUsed = new HashSet<>();
        originalResolver = origResolver;
    }
    
    @Override
    public DataFormat createDataFormat(String name, CamelContext context) {
        log.debug("Resolver asked to resolve component: {}", name);
        if (CORE_DATAFORMAT_NAMES.contains(name)) {
            log.debug("... but this is a system provided entity, so we'll skip our resolution");
            systemDataFormatsUsed.add(name);
            if (originalResolver != null) {
                return originalResolver.createDataFormat(name, context);
            } else {
                log.error("Original resolved not available to find system component: {}", name);
            }
        } else {
            dataFormatsToLoad.add(name);
        }
        return new PlaceholderDataFormat();
    }
    
    public Set<String> getDataFormatsToLoad() {
        return dataFormatsToLoad;
    }
    
    public Set<String> getSystemDataFormatsUsed() {
        return systemDataFormatsUsed;
    }
}
