/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import java.io.IOException;
import java.io.InputStream;
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
    
    private static Map<String, Object> artifactMap;
    private static Map<String, String> artifacts;
    
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
            log.debug("Discovering Camel (version {}) components using context: {}", ectx.getVersion(), ectx.getName());
    
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
    
    @SuppressWarnings({"unchecked"})
    String findComponentForScheme(String scheme) throws DiscoveryException {
        if (artifactMap == null || artifacts == null) {
            loadArtifactMap();
            artifacts = (Map<String, String>) artifactMap.get("artifacts");
        }
        return artifacts.get(scheme);
    }
    
    /**
     * Load the artifact map.
     *
     * This is loaded once per class load rather than once/instance.  It's produced by the build and will not change
     * over the time that the system is running.
     *
     * @throws DiscoveryException If there are issues loading the artifact
     */
    static synchronized void loadArtifactMap() throws DiscoveryException {
        try (InputStream in =
                     CamelDiscovery.class.getResourceAsStream("/artifactMap.json")) {
            if (in == null) {
                throw new DiscoveryException("Unable to load artifactMap.json file.");
            }
            String jsonString = new String(in.readAllBytes());
            log.debug("Map is {}", jsonString);
            artifactMap = new ObjectMapper().readValue(jsonString, new TypeReference<>() {});
        } catch (IOException ioe) {
            throw new DiscoveryException("Error loading artifact map from classpath", ioe);
        }
    }
}
