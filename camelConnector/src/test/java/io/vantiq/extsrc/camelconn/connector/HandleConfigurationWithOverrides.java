/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.connector;

import io.vantiq.extsrc.camelconn.discover.CamelRunner;
import io.vantiq.extsrc.camelconn.discover.CamelRunnerWithComponentSubs;
import org.apache.camel.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Test version of CamelHandleConfiguration.
 * </p>
 * This provides a type-safe, test only, mechanism to allow component type overrides for testing.  We do this to
 * handle, amongst other things, Vantiq-server-less implementation of the Vantiq Camel Component for use in unit tests.
 */
public class HandleConfigurationWithOverrides extends CamelHandleConfiguration {
    
    Map<String, Component> overrides = null;
    
    public HandleConfigurationWithOverrides(CamelCore source) {
        super(source);
    }
    
    public HandleConfigurationWithOverrides(CamelCore source, Map<String, Component> overrides) {
        super(source);
        this.overrides = overrides;
    }
    
    @Override
    protected CamelRunner createCamelRunner(String appName, String routeSpecification, String routeSpecificationType,
                                            List<URI> repos, String cacheDirectory, String loadedLibDir,
                                            List<Map<String, Object>> initComponents, Properties camelProperties,
                                            String headerBeanName, Map<String, String> headerDuplications) {
        CamelRunnerWithComponentSubs cr =
                new CamelRunnerWithComponentSubs(appName, routeSpecification, routeSpecificationType,
                                                 repos, cacheDirectory, loadedLibDir, initComponents, camelProperties,
                                                 headerBeanName, headerDuplications, overrides);
        return cr;
    }
}
