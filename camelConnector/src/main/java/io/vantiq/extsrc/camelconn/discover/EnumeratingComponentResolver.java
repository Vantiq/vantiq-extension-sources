/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.ComponentResolver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is used to determine the set of components used to a collection of routes.  It replaces the Camel
 * context's component resolver.  The context, when adding routes, will call resolveComponent() with the scheme/name
 * of the component and the context, with the result expected to be a resolved (class loaded, etc.) component.
 *
 * To accomplish this, this resolver notes the component in use.  It then loads a placeholder component which has
 * enough capability to act as if it has started.  This allows the context to believe that the routes successfully
 * loaded.  The result of this is that this component resolver has the set of components that need to be made
 * available (added to the class path) to allow a "real" component resolver to operate.
 */
@Slf4j
class EnumeratingComponentResolver implements ComponentResolver {
    private final Set<String> componentsToLoad;
    private final Set<String> systemComponentsUsed;
    private final ComponentResolver originalResolver;
    public static final Set<String> CORE_COMPONENTS_SCHEMES = new HashSet<>(
            Arrays.asList("bean", "binding", "browse", "class", "controlbus", "dataformat", "dataset", "direct",
                    "direct-vm", "file", "language", "log", "mock", "properties", "ref", "rest", "rest-api",
                    "saga", "scheduler", "seda", "stub", "test", "timer", "validator", "vantiq", "vm", "xslt"));
    
    // These don't load and hare handled specially internally
    public static final Set<String> NONLOADABLE_CORE_COMPONENT_SCHEMES =  new HashSet<>(
            Arrays.asList("binding", "properties", "test"));
    
    public EnumeratingComponentResolver(ComponentResolver origResolver) {
        componentsToLoad = new HashSet<>();
        systemComponentsUsed = new HashSet<>();
        originalResolver = origResolver;
    }
    
    @Override
    public Component resolveComponent(String name, CamelContext context) throws Exception {
        log.debug("Resolver asked to resolve component: {}", name);
        if (CORE_COMPONENTS_SCHEMES.contains(name)) {
            log.debug("... but this is a system provided entity, so we'll skip our resolution");
            systemComponentsUsed.add(name);
            if (originalResolver != null) {
                return originalResolver.resolveComponent(name, context);
            } else {
                log.error("Original resolved not available to find system component: {}", name);
            }
        } else {
            componentsToLoad.add(name);
        }
        return new PlaceholderComponent();
    }
    
    public Set<String> getComponentsToLoad() {
        return componentsToLoad;
    }
    
    public Set<String> getSystemComponentsUsed() {
        return systemComponentsUsed;
    }
}
