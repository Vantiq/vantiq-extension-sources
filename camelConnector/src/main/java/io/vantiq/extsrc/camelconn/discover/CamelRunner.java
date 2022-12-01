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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.MainRegistry;
import org.apache.camel.main.MainSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.ResourceHelper;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
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
public class CamelRunner extends MainSupport implements Closeable {
    private final String appName;
    private final List<URI> repositories;
    
    private final File ivyCache;
    private final File loadedLibraries;
    
    private RouteBuilder routeBuilder;
    
    private final String routeSpec;
    private final String routeSpecType;
    private ClassLoader routeBasedCL;
    private ClassLoader originalClassLoader;
    
    protected final MainRegistry registry;
    
    private Thread camelThread;
    
    /**
     * Create a CamelRunner instance
     *
     * @param appName String Name for this instance
     * @param routeSpecification String specification for the route(s) to run.  Syntax must match
     *                                  routeSpecificationType
     * @param routeSpecificationType String the type of specification provided
     * @param repos List<URI> The list of repos to search for libraries needed by the route(s)
     * @param cacheDirectory String Directory path to use to cache downloaded libraries
     * @param loadedLibDir String Directory path into which to put the libraries needed at run time.
     */
    CamelRunner(String appName, String routeSpecification, String routeSpecificationType, List<URI> repos,
                String cacheDirectory, String loadedLibDir) {
        super();
        this.registry = new MainRegistry();
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
        this.routeBuilder = null;
        this.routeSpec = routeSpecification;
        this.routeSpecType = routeSpecificationType;
        mainConfigurationProperties.setRoutesCollectorEnabled(false);
    }
    
    /**
     * Create a CamelRunner instance
     *
     * @param appName String Name for this instance
     * @param routeBuilder RouteBuilder specification for the route(s) to run
     * @param repos List<URI> The list of repos to search for libraries needed by the route(s)
     * @param cacheDirectory String Directory path to use to cache downloaded libraries
     * @param loadedLibDir String Directory path into which to put the libraries needed at run time.
     */
    CamelRunner(String appName, RouteBuilder routeBuilder, List<URI> repos,
                String cacheDirectory, String loadedLibDir) {
        super();
        this.registry = new MainRegistry();
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
        this.routeBuilder = routeBuilder;
        this.routeSpecType = null;
        this.routeSpec = null;
        mainConfigurationProperties.setRoutesBuilders(List.of(routeBuilder));
        mainConfigurationProperties.setRoutesCollectorEnabled(false);
    }
    
    @Override
    protected void doInit() throws Exception {
        super.doInit();
        createCamelContext();
    }
    
    /**
     * Build the application classload we will use.
     *
     * Construct a URLClassLoader that includes the requirements determined during the discovery phase.  We set the
     * parent classloader to the current application classloader (if any) or that of this class.  This allows this
     * classloader to find anything built into the caller.
     *
     * Constructing this classloader will trigger the requirement resolution, and may involve downloading the
     * packages required to run the provided routes (via the routeBuilder parameter).
     *
     * @return ClassLoader (specifically, a URLClassLoader)
     * @throws Exception for errors during the process.  IllegalStateException if called before there is a Camel
     *                   context available.  Others may be thrown by called methods.
     */
    private ClassLoader constructClassLoader() throws Exception {
        if (getCamelContext() == null) {
            throw new IllegalStateException("Camel context does not exist.");
        }
        CamelDiscovery discoverer = new CamelDiscovery();
        Map<String, Set<String>> discResults = discoverer.performComponentDiscovery(routeBuilder);
    
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
    
        ClassLoader parent = getCamelContext() != null ? getCamelContext().getApplicationContextClassLoader() : null;
        if (parent == null) {
            // Note that this is often the case.  But in our case, we want to include the various camel components
            // and runtime that we include in our connector class path.  So, we'll have our current classloader be
            // the parent so those are included.  An alternative would be to include the classpath of the connector
            // in the generated classloader's list of URIs, but this seems a cleaner solution.
            log.trace("Camel context {} has no applicationContextClassLoader, using class's loader as parent",
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
    
    /**
     * Run the routes specified in this CamelRunner
     *
     * @param waitForThread boolean indicating whether to await the completion of the thread started by this method
     * @throws Exception if camel thread throws an exception.
     */
    void runRoutes(boolean waitForThread) throws Exception {
        try {
            log.debug("Calling Run()");
            camelThread = new Thread(() -> {
                try {
                    run();
                } catch (Exception e) {
                    throw new RuntimeException("run() in runRoutes failed", e);
                }
            });
            camelThread.start();
            while (!isStarted()) {
                log.trace("Awaiting camel startup");
                Thread.sleep(500);
            }
            log.debug("Camel is started");
            if (waitForThread) {
                camelThread.join();
            }
        } finally {
            if (waitForThread) {
               close();
            }
        }
    }
    
    /**
     * Close this CamelRunner and the context associated with it.
     *
     * Once done, this entity cannot be reused.
     */
    public void close() {
        completed();
        if (!getCamelContext().isStopped()) {
            getCamelContext().stop();
            try {
                getCamelContext().close();
            } catch (Exception e) {
                // Ignore -- this is going away anyway
                log.error("Error closing camel context used in runner:", e);
            }
        }
    }
    
    public Thread getCamelThread() {
        return camelThread;
    }
    
    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (getCamelContext() != null) {
            return getCamelContext().createProducerTemplate();
        } else {
            return null;
        }
    }
    
    /**
     * Set up runtime environment.
     *
     * In addition to the super() stuff, we'll (optionally, build and) add our route(s) to the context.  At startup,
     * these routes/applications will begin to operate.
     *
     * @throws IllegalStateException when no routes have been specified.
     * @throws RuntimeException when routes cannot be added to the Camel environment.
     */
    @Override
    protected void beforeStart() throws Exception {
        if (routeBuilder == null) {
            if (StringUtils.isEmpty(routeSpec) || StringUtils.isEmpty(routeSpecType)) {
                log.error("No routes have been specified to run.  Either {} or {} and {} are " +
                        "required.", "routeBuilder", "routeSpec", "routeSpecType");
                throw new IllegalStateException("No routes have been specified to run.");
            }
            routeBuilder = loadRouteFromText(this.routeSpec, this.routeSpecType);
        }
        if (routeBasedCL == null) {
            if (routeBuilder != null) {
                try {
                    routeBasedCL = constructClassLoader();
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
        super.beforeStart();
    }
    
    /**
     * Load a route from a textual description of the route.
     *
     * Here, we'll use the camel runtime to construct a RouteBuilder from the text specification and specificaiton
     * type we are given.  We let the underlying Camel system find the route loaders so that this code extends to
     * support new specification types.  Changes to the set of libraries loaded in this image may be required to load
     * the new DSL interpreters of the new specification types.
     *
     * @param specification String specification of the route(s) to load
     * @param specificationType String syntactic type of the specification (xml, yaml, etc.)
     * @return RouteBuilder containing the route defined by the specification.
     * @throws Exception for errors in loading the routes.
     */
    protected RouteBuilder loadRouteFromText(String specification, String specificationType) throws Exception {
        log.debug("Loading route (specificationType {}): {}", specificationType, specification);
        ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
        RoutesLoader loader = extendedCamelContext.getRoutesLoader();
        Resource resource = ResourceHelper.fromString("in-memory." + specificationType, specification);
        loader.loadRoutes(resource);
        RoutesBuilderLoader rbl = loader.getRoutesLoader(specificationType);
        RoutesBuilder rb = rbl.loadRoutesBuilder(resource);
        log.trace("Route builder: {}", rb.toString());
        
        return (RouteBuilder) rb;
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getCamelContext().start();
    }
    
    @Override
    protected CamelContext createCamelContext() {
        if (camelContext == null) {
            camelContext = new DefaultCamelContext();
            log.debug(">>>Using context: {}, stopped: {}", camelContext.getName(), camelContext.isStopped());
        }
        return camelContext;
    }
}
