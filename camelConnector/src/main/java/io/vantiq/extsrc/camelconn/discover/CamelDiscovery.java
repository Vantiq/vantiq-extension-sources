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
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.reifier.language.ExpressionReifier;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.support.PluginHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Support for discovery of system components from a (set of) Camel routes.  We will use this information to load the
 * libraries required to run the route(s) provided.
 */

@Slf4j
public class CamelDiscovery {
    
    public static final String SYSTEM_COMPONENTS = "systemComponents";
    public static final String COMPONENTS_TO_LOAD = "componentsToLoad";
    public static final String SYSTEM_DATAFORMATS = "systemDataFormats";
    public static final String DATAFORMATS_TO_LOAD = "dataFormatsToLoad";
    public static final String VANTIQ_COMPONENT_SCHEME = "vantiq";
    private static final String COMPONENTS_KEY_NAME = "components";
    private static final String DATAFORMATS_KEY_NAME = "dataformats";
    private static final String CAMEL_VERSION_KEY_NAME = "camelVersion";
    
    private static Map<String, Object> artifactMap;
    private static Map<String, String> components;
    private static Map<String, String> dataformats;
    
    /**
     * Determine the set of components used in the routes in a route builder.
     *
     * @param rb RouteBuilder containing the set of routes from which to discover components
     * @param propertyValues Properties set of properties, keyed by name, to provide for the route
     *
     * @return Map<String, Set<String>> containing two Set entries:
     *          systemComponents -- (informational) and
     *          componentsToLoad -- the set of component schemes in use in the collected routes.
     *
     * @throws DiscoveryException when exceptions encountered during processing.
     */
    Map<String, Set<String>> performComponentDiscovery(RouteBuilder rb, Properties propertyValues)
            throws DiscoveryException {
        // When the context used here gets closed, the type converter is removed/nulled out.  Later, when
        // running in "real life," we try & use that expression predicate that we find and it fails (NPE due to
        // typeconverter being null).  For this reason, to make things work (for the cases where this matters),
        // we simply don't close the CamelContext in use.  I'm still looking for a better way.
        
        //noinspection resource
        DefaultCamelContext ctx = new DefaultCamelContext();
    
        try  {
            ExtendedCamelContext ectx = ctx.getCamelContextExtension(); //ExtendedCamelContext.class);
            
            EnumeratingComponentResolver ecr =
                    new EnumeratingComponentResolver(PluginHelper.getComponentResolver(ectx));
            ectx.addContextPlugin(ComponentResolver.class, ecr);
            EnumeratingDataFormatResolver edfr =
                    new EnumeratingDataFormatResolver(PluginHelper.getDataFormatResolver(ectx));
            ectx.addContextPlugin(DataFormatResolver.class, edfr);
            EnumeratingConfigurerResolver epcr = new EnumeratingConfigurerResolver();
            ectx.addContextPlugin(ConfigurerResolver.class, epcr);
            
            // If our current context has property placeholders defined, we'll need to provide them to the context we
            // create for discovery, since things like the URLs could be defined in properties, and that's used for
            // discovery. We define a new context for use here since we're overriding all the resolvers with those
            // specially designed for discovery.
            ctx.getPropertiesComponent().setInitialProperties(propertyValues);

            log.debug("Discovering Camel (version {}) components using context: {}", ctx.getVersion(), ctx.getName());
    
            try {
                ctx.addRoutes(rb);
                ctx.start();
            } catch (Exception e) {
                log.error("Trapped exception during component discovery:  ", e);
                throw new DiscoveryException("Exception during component discovery", e);
            }
    
            if (log.isDebugEnabled()) {
                log.debug("Component discovery complete:");
                ecr.getComponentsToLoad().forEach(comp -> log.debug("    ---> {}", comp));
    
                log.debug("System components (ignored): ");
                ecr.getSystemComponentsUsed().forEach(comp -> log.debug("    ---> {}", comp));
                
                log.debug("DataFormat discovery complete:");
                edfr.getDataFormatsToLoad().forEach(df -> log.debug("    ---> {}", df));
                log.debug("System dataformats (ignored): ");
                edfr.getSystemDataFormatsUsed().forEach(df -> log.debug("    ---> {}", df));
    
            }
            Map<String, Set<String>> retVal = new HashMap<>();
    
            retVal.put(SYSTEM_COMPONENTS, ecr.getSystemComponentsUsed());
            retVal.put(COMPONENTS_TO_LOAD, ecr.getComponentsToLoad());
            retVal.put(SYSTEM_DATAFORMATS, edfr.getSystemDataFormatsUsed());
            retVal.put(DATAFORMATS_TO_LOAD, edfr.getDataFormatsToLoad());
            return retVal;
        } catch (Exception e) {
            log.error("Trapped exception preparing or completing component discovery", e);
            throw new DiscoveryException("Exception preparing or completing component discovery processing", e);
        } finally {
            // The following is aimed at reducing the resource overhead that discovery may introduce.  The ctx.close()
            // call claims to remove all stuff used, but it doesn't, leaving bits of expressions around that refer to
            // the camel context under which they were built.  Consequently, we cannot close the context, so we'll do
            // what we can to get rid of the detritus.
    
            try {
                ctx.stopAllRoutes();
            } catch (Exception e) {
                log.error("Unable to stop all discovery routes:", e);
            }
            ctx.getRoutes().forEach(ctx::removeRoute);
            
            List<RouteDefinition> rds = new ArrayList<>(ctx.getRouteDefinitions());
            rds.forEach( (rd) -> {
                try {
                    ctx.removeRouteDefinition(rd);
                    log.debug("Removed discovery-time route definition: {}", rd.getShortName());
                } catch (Exception e) {
                    log.error("Failed to remove discovery-time route definition: {}", rd.getShortName());
                    throw new RuntimeException(e);
                }
            });
            ExpressionReifier.clearReifiers();
            ProcessorReifier.clearReifiers();
        }
    }
    
    /**
     * Find the name of the camel component from which it runs these components based on the scheme.
     *
     * Using the generated artifactsMap (see build.gradle's generateComponentList task), lookup the schema
     * name and return the component name found.  It (will be) assumed that these are all in org.apache.camel, and
     * that they all share the same camel version as that which we are running.  This is the Apache Camel way.
     *
     * @param scheme String name of component scheme used to define endpoints, etc.
     * @return String identifying the name of the loadable artifact that contains the code to run the component.  Can
     *         be null if no artifact is found.
     * @throws DiscoveryException If errors occur attempting to find the artifact
     */

    String findComponentForScheme(String scheme) throws DiscoveryException {
        if (artifactMap == null || components == null) {
            loadArtifactMap();
        }
        return components.get(scheme);
    }
    
    String findDataFormatForName(String dfName) throws DiscoveryException {
        if (artifactMap == null || dataformats == null) {
            loadArtifactMap();
        }
        return dataformats.get(dfName);
    }
    
    String getComponentListVersion() throws DiscoveryException {
        if (artifactMap == null || components == null) {
            loadArtifactMap();
        }
        
        if (artifactMap.get(CAMEL_VERSION_KEY_NAME) instanceof String) {
            return (String) artifactMap.get(CAMEL_VERSION_KEY_NAME);
        } else {
            throw new DiscoveryException("No " + CAMEL_VERSION_KEY_NAME + " property present");
        }
    }
    
    boolean isVersionCompatible(String comparisonVersion) throws DiscoveryException {
        StringTokenizer artifactVersionParts = new StringTokenizer(getComponentListVersion(), ".");
        StringTokenizer comparisonVersionParts = new StringTokenizer(comparisonVersion, ".");
        return artifactVersionParts.countTokens() >= 2 && comparisonVersionParts.countTokens() >= 2 &&
                artifactVersionParts.nextToken().equals(comparisonVersionParts.nextToken()) &&
                artifactVersionParts.nextToken().equals(comparisonVersionParts.nextToken());
    }
    
    /**
     * Load the artifact map.
     *
     * This is loaded once per class load rather than once/instance.  It's produced by the build and will not change
     * over the time that the system is running.
     *
     * @throws DiscoveryException If there are issues loading the artifact
     */
    @SuppressWarnings({"unchecked"})
    static synchronized void loadArtifactMap() throws DiscoveryException {
        try (InputStream in =
                     CamelDiscovery.class.getResourceAsStream("/artifactMap.json")) {
            if (in == null) {
                throw new DiscoveryException("Unable to load artifactMap.json file.");
            }
            String jsonString = new String(in.readAllBytes());
            log.trace("Map is {}", jsonString);
            artifactMap = new ObjectMapper().readValue(jsonString, new TypeReference<>() {});
            components = (Map<String, String>) artifactMap.get(COMPONENTS_KEY_NAME);
            dataformats = (Map<String, String>) artifactMap.get(DATAFORMATS_KEY_NAME);
        } catch (IOException ioe) {
            throw new DiscoveryException("Error loading artifact map from classpath", ioe);
        }
    }
}
