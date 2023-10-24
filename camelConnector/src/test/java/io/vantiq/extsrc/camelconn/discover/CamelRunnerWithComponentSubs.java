/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Test version of CamelRunner.
 * </p>
 * This provides a type-safe, test only, mechanism to allow component type overrides for testing.  We do this to
 * handle, amongst other things, Vantiq-server-less implementation of the Vantiq Camel Component for use in unit tests.
 */
public class CamelRunnerWithComponentSubs extends CamelRunner {
    /**
     * Create a CamelRunner instance
     *
     * @param appName String Name for this instance
     * @param routeSpecification String specification for the route(s) to run.  Syntax must match
     *         routeSpecificationType
     * @param routeSpecificationType String the type of specification provided
     * @param repos List<URI> The list of repos to search for libraries needed by the route(s)
     * @param cacheDirectory String Directory path to use to cache downloaded libraries
     * @param loadedLibDir String Directory path into which to put the libraries needed at run time.
     * @param initComponents List<Map<String, Object>> List of component names that need initialization using the
     *         properties included herein
     * @param camelProperties Properties set of general property values that Camel can use for property resolution
     * @param headerBeanName String Name of bean to use generate.  Should match what's in route & be unique to this
     *         camel instance
     * @param headerDuplications Map<String, String> directed set of header names to duplicate
     */
    public CamelRunnerWithComponentSubs(String appName, String routeSpecification, String routeSpecificationType,
                                        List<URI> repos, String cacheDirectory, String loadedLibDir,
                                        List<Map<String, Object>> initComponents,
                                        Properties camelProperties,
                                        String headerBeanName, Map<String, String> headerDuplications) {
        
        super(appName, routeSpecification, routeSpecificationType, repos, cacheDirectory, loadedLibDir, initComponents,
              camelProperties, headerBeanName, headerDuplications);
    }
    
    public CamelRunnerWithComponentSubs(String appName, String routeSpecification, String routeSpecificationType,
                                        List<URI> repos, String cacheDirectory, String loadedLibDir,
                                        List<Map<String, Object>> initComponents,
                                        Properties camelProperties,
                                        String headerBeanName, Map<String, String> headerDuplications,
                                        Map<String, Component> componentOverrides) {
        super(appName, routeSpecification, routeSpecificationType, repos, cacheDirectory, loadedLibDir, initComponents,
              camelProperties, headerBeanName, headerDuplications);
        this.setComponentOverrides(componentOverrides);
    }
    
    public void overrideComponents(Map<String, Component> compOverrides) {
        this.setComponentOverrides(compOverrides);
    }
    
}
