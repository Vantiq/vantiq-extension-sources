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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.MainRegistry;
import org.apache.camel.main.MainSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.function.TriFunction;
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

@Slf4j
public class CamelRunner extends MainSupport {
    private final String appName;
    private final List<URI> repositories;
    
    private final File ivyCache;
    private final File loadedLibraries;
    
    private final RouteBuilder routeBuilder;
    private ClassLoader routeBasedCL;
    private ClassLoader originalClassLoader;
    
    private CamelContext initialContext;
    
    protected final MainRegistry registry;
    
    CamelRunner(String appName, RouteBuilder routeBuilder, List<URI> repos,
                String cacheDirectory,
                String loadedLibDir) throws Exception {
        super();
        this.registry = new MainRegistry();
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
        this.routeBuilder = routeBuilder;
        mainConfigurationProperties.setRoutesBuilders(List.of(routeBuilder));
        mainConfigurationProperties.setRoutesCollectorEnabled(false);
        createCamelContext();
    }
    CamelRunner(String appName, CamelContext initialContext, RouteBuilder routeBuilder, List<URI> repos,
                String cacheDirectory,
                String loadedLibDir) throws Exception {
        super();
        this.initialContext = initialContext; // Used in tests
        this.registry = new MainRegistry();
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
        this.routeBuilder = routeBuilder;
        mainConfigurationProperties.setRoutesBuilders(List.of(routeBuilder));
        mainConfigurationProperties.setRoutesCollectorEnabled(false);
        createCamelContext();
    }
    
    private ClassLoader constructClassLoader(RouteBuilder rb) throws Exception {
        if (getCamelContext() == null) {
            throw new IllegalStateException("Camel context does not exist.");
        }
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
    
        ClassLoader parent = getCamelContext().getApplicationContextClassLoader();
        if (parent == null) {
            // Note that this is often the case.  But in our case, we want to include the various camel components
            // and runtime that we include in our connector class path.  So, we'll have our current classloader be
            // the parent so those are included.  An alternative would be to include the classpath of the connector
            // in the generated classloader's list of URIs, but this seems a cleaner solution.
            // TODO: Change to trace
            log.debug("Camel context {} has no applicationContextClassLoader, using class's loader as parent",
                      getCamelContext().getName());
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
        if (getCamelContext() == null) {
            throw new IllegalStateException("Camel context does not exist.");
        }
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
            Collection<File> resolved = cr.resolve("org.apache.camel", lib, getCamelContext().getVersion());
            jarSet.addAll(resolved);
        }
        return jarSet;
    }
    
    void runRouteWithLoader(RouteBuilder rb) throws Exception {

        try {
            log.debug("Calling Run()");
            new Thread(() -> {
                try {
                    run();
                } catch (Exception e) {
                    throw new RuntimeException("run() in runRouteWithLoader failed", e);
                }
            }).start();
            while (!isStarted()) {
                log.trace("Awaiting camel startup");
                Thread.sleep(500);
            }
            log.debug("Camel is started");
        } finally {
            completed();
            if (getCamelContext().isStopped()) {
                getCamelContext().stop();
                getCamelContext().close();
            }
        }
    }
    
    // Test version of above
    void runRouteWithLoader(RouteBuilder rb,
                            TriFunction<CamelContext, List<String>, List<String>, Boolean> testCode,
                            List<Pair<String, String>> args) throws Exception {

        try {

            log.debug("Calling Run()");
            new Thread(() -> {
                try {
                    run();
                } catch (Exception e) {
                    throw new RuntimeException("run() in runRouteWithLoader failed", e);
                }
            }).start();
            while (!isStarted()) {
                log.debug("Awaiting camel startup");
                Thread.sleep(500);
    
            }
            log.debug("Camel is started, context {} started: {}", getCamelContext().getName(),
                      getCamelContext().isStarted());
//            Thread.sleep(10_000);
//            log.debug("Waited 10 seconds, now running tests. Context {} stopped: {}", getCamelContext().getName(),
//                      getCamelContext().isStopped());

            if (testCode != null && args != null) {
                List<String> query = List.of(args.get(0).getKey(), args.get(1).getKey());
                List<String> answer =List.of(args.get(0).getValue(), args.get(1).getValue());
                if (!testCode.apply(getCamelContext(), query, answer)) {
                    throw new RuntimeException("Test code operation returned false");
                }
            }
        } finally {
            completed();
            if (getCamelContext().isStopped()) {
                getCamelContext().stop();
                getCamelContext().close();
            }
        }
    }
    
    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (getCamelContext() != null) {
            return getCamelContext().createProducerTemplate();
        } else {
            return null;
        }
    }
    
    @Override
    protected void beforeStart() {
        if (routeBasedCL == null) {
            if (routeBuilder != null) {
                try {
                    routeBasedCL = constructClassLoader(routeBuilder);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to create route-based class loader", e);
                }
                originalClassLoader = camelContext.getApplicationContextClassLoader();
                camelContext.setApplicationContextClassLoader(routeBasedCL);
                try {
                    camelContext.addRoutes(routeBuilder);
                } catch (Exception e) {
                    throw new RuntimeException("Adding routes to camel context failed", e);
                }
            }
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getCamelContext().start();
    }
    
    @Override
    protected CamelContext createCamelContext() {
        if (camelContext == null) {
            // FIXME: Remove initial context if not necessary.
            if (initialContext == null) {
                camelContext = new DefaultCamelContext();
            } else {
                log.debug("Using passed-in context: {}", initialContext.getName());
                camelContext = initialContext;
            }
            log.debug(">>>Using context: {}, stopped: {}", camelContext.getName(), camelContext.isStopped());
        }

        return camelContext;
    }
}
