/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Support for discovery of system components from a (set of) Camel routes.  We will use this information to load the
 * libraries required to run the route(s) provided.
 */

@Slf4j
public class CamelDiscovery {
    
    public static final String SYSTEM_COMPONENTS = "systemComponents";
    public static final String COMPONENTS_TO_LOAD = "componentsToLoad";
    
    /**
     * Determine the set of components used in the routes in a route builder.
     *
     ** @param rb RouteBuilder containing the set of routes from which to discover components
     *
     * @return Map<String, Set<String>> containing two Set entries:
     *          systemComponents -- (informational) and
     *          componentsToLoad -- the set of component schemes in use in the collected routes.
     *
     * @throws DiscoveryException when exceptions encountered during processing.
     */
    Map<String, Set<String>> performComponentDiscovery(RouteBuilder rb) throws DiscoveryException {
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ExtendedCamelContext ectx = ctx.adapt(ExtendedCamelContext.class);
            EnumeratingComponentResolver ecr = new EnumeratingComponentResolver(ectx.getComponentResolver());
            ectx.setComponentResolver(ecr);
            log.debug("Discovering components using context: {}", ectx.getName());
    
            assert ectx.getComponentResolver() instanceof EnumeratingComponentResolver;
            try {
                ectx.addRoutes(rb);
                ectx.start();
            } catch (Exception e) {
                log.error("Trapped exception during component discovery:  ", e);
                throw new DiscoveryException("Exception during component discovery", e);
            }
    
            if (log.isDebugEnabled()) {
                log.debug("Component discovery complete:");
                ecr.getComponentsToLoad().forEach(comp -> log.debug("    ---> {}", comp));
    
                log.debug("System components (ignored): ");
                ecr.getSystemComponentsUsed().forEach(comp -> log.debug("    ---> {}", comp));
            }
            Map<String, Set<String>> retVal = new HashMap<>();
            retVal.put(SYSTEM_COMPONENTS, ecr.getSystemComponentsUsed());
            retVal.put(COMPONENTS_TO_LOAD, ecr.getComponentsToLoad());
            return retVal;
        } catch (Exception e) {
            log.error("Trapped exception preparing or completing component discovery", e);
            throw new DiscoveryException("Exception preparing or completing component discovery processing", e);
        }
    }
}
