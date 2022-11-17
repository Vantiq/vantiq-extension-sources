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
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Slf4j
public class CamelRunner {
    
    private final CamelContext context;
    private final String appName;
    private final List<URI> repositories;
    
    private final File ivyCache;
    private final File loadedLibraries;
    
    CamelRunner(CamelContext context, String appName, List<URI> repos, String cacheDirectory, String loadedLibDir) {
        this.context = context;
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
    }
    
    ClassLoader constructClassLoader(RouteBuilder rb) throws Exception {
        CamelDiscovery discoverer = new CamelDiscovery();
        Map<String, Set<String>> discResults = discoverer.performComponentDiscovery(rb);
    
        // Now, generate the component list to load...
        // We use a set to avoid duplicates, but we want things resolved in the order they are found in the
        // repositories, so we'll use a LinkedHashSet<>.  That acts as a set but retains the order in which elements
        // were inserted.
        Set<File> jarList = new LinkedHashSet<>();
        if (repositories != null && repositories.size() > 0) {
            Set<File> aReposWorth = resolveFromRepo(repositories,
                                                    discResults.get(CamelDiscovery.COMPONENTS_TO_LOAD),
                                                    discoverer);
           jarList.addAll(aReposWorth);
        } else {
            // If providing a list of repos, we require the user to include maven-central (if they want to use it).
            // This allows test cases, etc., not to over-resolve.
            Set<File> aReposWorth = resolveFromRepo(Collections.<URI>emptyList(),
                                                    discResults.get(CamelDiscovery.COMPONENTS_TO_LOAD),
                                                    discoverer);
            jarList.addAll(aReposWorth);
        }
    
        // Now, construct a classloader that includes all these loaded files
    
        ArrayList<URL> urlList = new ArrayList<>();
        for (File jar : jarList) {
            URL url = jar.toURI().toURL();
            urlList.add(url);
        }
    
        // Now, we'll set up a classLoader based on that list of jar files and use that to run our routes.
    
        ClassLoader parent = context.getApplicationContextClassLoader();
        if (parent == null) {
            // Note that this is often the case.  But in our case, we want to include the various camel components
            // and runtime that we include in our connector class path.  So, we'll have our current classloader be
            // the parent so those are included.  An alternative would be to include the classpath of the connector
            // in the generated classloader's list of URIs, but this seems a cleaner solution.
            // TODO: Change to trace
            log.debug("Camel context {} has no applicationContextClassLoader, using class's loader as parent",
                      context.getName());
            parent = this.getClass().getClassLoader();
        }
        return new URLClassLoader(urlList.toArray(new URL[0]), parent);
    }
    
    /**
     * Given a repository, resolve (download) the components available.
     *
     * @param repositories List<URI> Repository from which to (attempt) to load.  Not all components must be available
     * here.
     * @param componentsToResolve Set<String> the components to load
     * @param discoverer CamelDiscovery to use.
     * @return Set<File> The set of (jar) files downloaded.
     * @throws Exception when things go awry
     */

    private Set<File> resolveFromRepo(List<URI> repositories, Set<String> componentsToResolve,
                                      CamelDiscovery discoverer)
            throws Exception {
        Set<File> jarSet = new LinkedHashSet<>();
        CamelResolver cr = new CamelResolver(appName, repositories,
                                             ivyCache, loadedLibraries);
        for (String comp : componentsToResolve) {
            String lib = discoverer.findComponentForScheme(comp);
            if (lib == null) {
                // If we don't know the name, we have no chance of resolving it.  So complain.
                throw new ResolutionException("Unable to determine jar file for component: " + comp);
            }
            // Our resolver is a chain resolver, including all the IBibloResolvers that are in our list of URLs.
            // Thus, we will see an error here only if the artifact is resolved by none of the repos in our list.
            // We could learn to ignore it & let camel throw the error, but our throwing the ResolutionException
            // seems cleaner.  Notify about the problems closer to the source.
            Collection<File> resolved = cr.resolve("org.apache.camel", lib, context.getVersion());
            jarSet.addAll(resolved);
        }
        return jarSet;
    }
    
    void runRouteWithLoader(RouteBuilder rb, ClassLoader routeClassLoader) throws Exception {
        ClassLoader original = context.getApplicationContextClassLoader();
        try {
            context.setApplicationContextClassLoader(routeClassLoader);
            context.addRoutes(rb);
            context.start();
        } finally {
            context.stop();
            context.setApplicationContextClassLoader(original);
        }
    }
    
    // Test version of above
    void runRouteWithLoader(RouteBuilder rb, ClassLoader routeClassLoader,
                            BiFunction<String, String, Boolean> testCode,
                            List<Pair<String, String>> args) throws Exception {
        ClassLoader original = context.getApplicationContextClassLoader();
        try {
            context.setApplicationContextClassLoader(routeClassLoader);
            context.addRoutes(rb);
            context.start();
            if (testCode != null && args != null) {
                args.forEach(entry -> {
                    if (!testCode.apply(entry.getKey(), entry.getValue())) {
                        throw new RuntimeException("Test code operation returned false");
                    }
                });
            }
        } finally {
            context.stop();
            context.setApplicationContextClassLoader(original);
        }
    }
}
