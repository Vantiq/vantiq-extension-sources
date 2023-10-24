/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import io.vantiq.extsrc.camel.HeaderDuplicationBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.TemplatedRouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.MainRegistry;
import org.apache.camel.main.MainSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.ResourceHelper;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Slf4j
public class CamelRunner extends MainSupport implements Closeable {
    public static final String COMPONENT_NAME = "componentName";
    public static final String COMPONENT_PROPERTIES = "componentProperties";
    private final String appName;
    private final List<URI> repositories;
    private List<String> additionalLibraries;
    
    private final List<Map<String, Object>> initComponents;
    
    private final Properties camelProperties;
    String headerBeanName;
    Map<String, String> headerDuplications;
    private final File ivyCache;
    private final File loadedLibraries;
    
    private RouteBuilder routeBuilder;
    
    private final String routeSpec;
    private final String routeSpecType;
    private ClassLoader routeBasedCL;
    
    private Map<String, Component> componentOverrides = null; // Used in testing only
    private ClassLoader originalClassLoader;
    
    protected final MainRegistry registry;
    
    private Thread camelThread;
    
    private Boolean startupCompleted = false;
    private Exception startupFailed = null;
    private final static String startupSync = "startupSync";
    
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
     * @param initComponents List<Map<String, Object>> List of component names that need initialization using
     *         the properties included herein
     * @param camelProperties Properties set of general property values that Camel can use for property
     *         resolution
     * @param headerBeanName String Name of bean to use generate.  Should match what's in route & be unique to this
     *         camel instance
     * @param headerDuplications Map<String, String> directed set of header names to duplicate
     */
    public CamelRunner(String appName, String routeSpecification, String routeSpecificationType, List<URI> repos,
                       String cacheDirectory, String loadedLibDir, List<Map<String, Object>> initComponents,
                       Properties camelProperties, String headerBeanName, Map<String, String> headerDuplications) {
        super();
        this.registry = new MainRegistry();
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
        this.routeBuilder = null;
        this.routeSpec = routeSpecification;
        // Camel doesn't like simpler yml, so be helpful here.
        this.routeSpecType = routeSpecificationType.equals("yml") ? "yaml" : routeSpecificationType;
        this.additionalLibraries = null;
        this.initComponents = initComponents;
        this.camelProperties = camelProperties;
        this.headerDuplications = headerDuplications != null ? headerDuplications : new HashMap<>();
        this.headerBeanName = headerBeanName;
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
     * @param initComponents List<Map<String, Object>> List of component names that need
     *                          initialization using the properties included herein
     * @param camelProperties Properties set of general property values that Camel can use for property resolution
     * @param headerBeanName String Name of bean to use generate.  Should match what's in route & be unique to this
     *                          camel instance
     * @param headerDuplications Map<String, String> directed set of header names to duplicate
     */
    CamelRunner(String appName, RouteBuilder routeBuilder, List<URI> repos,
                String cacheDirectory, String loadedLibDir, List<Map<String, Object>> initComponents,
                Properties camelProperties, String headerBeanName, Map<String, String> headerDuplications) {
        super();
        this.registry = new MainRegistry();
        this.appName = appName;
        this.repositories = repos;
        this.ivyCache = new File(cacheDirectory);
        this.loadedLibraries = new File(loadedLibDir);
        this.routeBuilder = routeBuilder;
        this.routeSpecType = null;
        this.routeSpec = null;
        this.additionalLibraries = null;
        this.initComponents = initComponents;
        this.camelProperties = camelProperties;
        this.headerDuplications = headerDuplications != null ? headerDuplications : new HashMap<>();
        this.headerBeanName = headerBeanName;
    
        mainConfigurationProperties.setRoutesBuilders(List.of(routeBuilder));
        mainConfigurationProperties.setRoutesCollectorEnabled(false);
    }
    
    protected void setComponentOverrides(Map<String, Component> compOverrides) {
        this.componentOverrides = compOverrides;
    }
    public void setAdditionalLibraries(List<String> libs) {
        this.additionalLibraries = libs;
    }
    
    @Override
    protected void doInit() throws Exception {
        super.doInit();
        CamelContext ctx = createCamelContext();
        if (componentOverrides != null && !componentOverrides.isEmpty()) {
            componentOverrides.forEach(ctx::addComponent);
        }
        if (headerBeanName != null) {
            HeaderDuplicationBean hdBean = new HeaderDuplicationBean();
            hdBean.setHeaderDuplicationMap(headerDuplications);
            ctx.getRegistry().bind(headerBeanName, hdBean);
        }
    }
    
    public Properties getCamelProperties() {
        return camelProperties;
    }
    
    /**
     * Build the application classloader we will use.
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
    private ClassLoader discoverAndBuildClassloader() throws Exception {
        if (getCamelContext() == null) {
            throw new IllegalStateException("Camel context does not exist.");
        }
        CamelDiscovery discoverer = new CamelDiscovery();
        Map<String, Set<String>> discResults = discoverer.performComponentDiscovery(routeBuilder, camelProperties);
    
        // Now, based on our discovery, construct a classLoader
        return buildClassloader(discoverer, discResults);
    }
    
    private ClassLoader buildClassloader(CamelDiscovery discoverer,
                                         Map<String, Set<String>> discResults) throws Exception {
        // Now, generate the component list to load...
        // We use a set to avoid duplicates, but we want things resolved in the order they are found in the
        // repositories, so we'll use a LinkedHashSet<>.  That acts as a set but retains the order in which elements
        // were inserted.
        List<URI> repsToUse = repositories;
        if (repsToUse == null) {
            repsToUse = Collections.emptyList();
        }
        // If providing a list of repos, we require the user to include maven-central (if they want to use it).
        // This allows test cases, etc., not to over-resolve.
        Set<File> aReposWorth = resolveFromRepo(repsToUse,
                                                discResults.get(CamelDiscovery.COMPONENTS_TO_LOAD),
                                                discResults.get(CamelDiscovery.DATAFORMATS_TO_LOAD),
                                                this.additionalLibraries,
                                                discoverer);
        Set<File> jarList = new LinkedHashSet<>(aReposWorth);
    
        // Now, construct a classloader that includes all these loaded files
    
        ArrayList<URL> urlList = new ArrayList<>();
        for (File jar : jarList) {
            URL url = jar.toURI().toURL();
            urlList.add(url);
        }
        if (log.isTraceEnabled()) {
            // This list is long, so if we're ignoring it, no reason to prepare to Stringify it.
            log.trace("URLList: {}", urlList);
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
     * @param dataformatsToResolve Set<String> the data formats to load
     * @param additionalLibraries List<String> Extra libraries to load (overcome missing from discovery
     * @param discoverer CamelDiscovery to use.
     * @return Set<File> The set of (jar) files downloaded.
     * @throws Exception when things go awry
     */

    private Set<File> resolveFromRepo(List<URI> repositories, Set<String> componentsToResolve,
                                      Set<String> dataformatsToResolve,
                                      List<String> additionalLibraries, CamelDiscovery discoverer)
            throws Exception {
        if (getCamelContext() == null) {
            throw new IllegalStateException("Camel context does not exist.");
        }
        Set<File> jarSet = new LinkedHashSet<>();
        CamelResolver cr = new CamelResolver(appName, repositories,
                                             ivyCache, loadedLibraries);
        if (discoverer != null) {
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
                Collection<File> resolved = cr.resolve("org.apache.camel", lib, getCamelContext().getVersion(),
                                                       "Component Resolution");
                jarSet.addAll(resolved);
            }
    
            for (String df : dataformatsToResolve) {
                String lib = discoverer.findDataFormatForName(df);
                if (lib == null) {
                    // If we don't know the name, we have no chance of resolving it.  So complain.
                    throw new ResolutionException("Unable to determine jar file for dataformat: " + df);
                }
                // Our resolver is a chain resolver, including all the IBibloResolvers that are in our list of URLs.
                // Thus, we will see an error here only if the artifact is resolved by none of the repos in our list.
                // We could learn to ignore it & let camel throw the error, but our throwing the ResolutionException
                // seems cleaner.  Notify about the problems closer to the source.
                Collection<File> resolved = cr.resolve("org.apache.camel", lib, getCamelContext().getVersion(),
                                                       "Dataformat Resolution");
                jarSet.addAll(resolved);
            }
        }
        // If our use has specified additional libraries to include, do those now.
        if (additionalLibraries != null && !additionalLibraries.isEmpty()) {
            for (String comp : additionalLibraries) {
                String[] compParts = comp.split(":");
                if (compParts.length == 2 && compParts[0].startsWith("org.apache.camel")) {
                    // Then, this is a camel component.  Use our current version
                    String[] newParts = new String[3];
                    newParts[0] = compParts[0];
                    newParts[1] = compParts[1];
                    newParts[2] = getCamelContext().getVersion();
                    compParts = newParts;
                } else if (compParts.length != 3) {
                    throw new IllegalArgumentException("The configuration's 'additionalLibraries' property must be a list " +
                                                       "of library specifications of the form " +
                                                       "<organization>:<name>:<revision>.  Found " + comp);
                }
                
                // Our resolver is a chain resolver, including all the IBibloResolvers that are in our list of URLs.
                // Thus, we will see an error here only if the artifact is resolved by none of the repos in our list.
                // We could learn to ignore it & let camel throw the error, but our throwing the ResolutionException
                // seems cleaner.  Notify about the problems closer to the source.
                Collection<File> resolved = cr.resolve(compParts[0], compParts[1], compParts[2],
                                                       "Additional Library Resolution");
                jarSet.addAll(resolved);
            }
        }
        return jarSet;
    }
    
    /**
     * Run the routes specified in this CamelRunner
     *
     * @param waitForThread boolean indicating whether to await the completion of the thread started by this method
     */
    public void runRoutes(boolean waitForThread) {
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
            log.trace("Awaiting camel startup");
            awaitStartup();
            
            if (startupFailed == null) {
                log.debug("Camel is started");
                if (waitForThread) {
                    camelThread.join();
                }
            }
            // else
            // We have failed to successfully start.  Finally case below will wait for the thread to die
            // then throw the exception problem
        } catch (Exception e) {
            log.error("Exception from runtime", e);
        } finally {
            if (waitForThread || startupFailed != null) {
                try {
                    close();
                } catch (Exception e) {
                    // Log but ignore -- this is going away anyway
                    log.error("Error closing CamelRunner when the routes complete:", e);
                }
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
     * these routes/applications will begin to operate. We will also do any specified component initialization.
     *
     * @throws IllegalStateException when no routes have been specified.
     * @throws RuntimeException when routes cannot be added to the Camel environment.
     */
    @Override
    protected void beforeStart() throws Exception {
    
        try {
            // Set any property values provided in the configuration.  These will be used in lieu of others in the classpath
            // This method is tolerant being passed a null value, and will do the right thing. This needs to be done
            // before the class loader is constructed since some properties may be used as things like the URL which may
            // be used at class-load time.
            camelContext.getPropertiesComponent().setInitialProperties(this.camelProperties);
            if (routeBuilder == null) {
                if (StringUtils.isEmpty(routeSpec) || StringUtils.isEmpty(routeSpecType)) {
                    log.error("No routes have been specified to run.  Either {} or {} and {} are " +
                                      "required.", "routeBuilder", "routeSpec", "routeSpecType");
                    throw new IllegalStateException("No routes have been specified to run.");
                }
                routeBuilder = loadRouteFromText(this.routeSpec, this.routeSpecType);
            }
            if (routeBasedCL == null) {
                // FIXME -- need to detect when there are no routes.
                if (routeBuilder != null) {
                    try {
                        routeBasedCL = discoverAndBuildClassloader();
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

            // Some routes may have components (e.g., Salesforce) that need specific configuration.
            // If that's the case, do that now before we start our route(s).
            if (initComponents != null && initComponents.size() > 0) {
                for (Map<String, Object> compToInit : initComponents) {
                    Object propsObj = compToInit.get(COMPONENT_PROPERTIES);
                    Map<String, ?> props;
                    if (propsObj instanceof Map) {
                        //noinspection unchecked
                        props = (Map<String, ?>) propsObj;
                    } else {
                        throw new IllegalArgumentException("Component initialization type is incorrect.");
                    }
                    if (!props.isEmpty()) {
                        // Don't bother to go further if there are no properties to set
                
                        Object comp = camelContext.getComponent(compToInit.get(COMPONENT_NAME).toString());
                        if (comp != null) {
                            Class<?> toBeInit = comp.getClass();
                            for (Map.Entry<String, ?> compProp : props.entrySet()) {
                                // For each property to be initialized, we'll look for the setter method
                                // (i.e., set${propertyName}(value) where the parameter's class matches the class of
                                // the property value passed in).  If that exists, we'll call it.  Otherwise, log a
                                // warning and ignore it. Things are likely to fail, but there's not much we can do about
                                // improper specifications.
                                String methName = "set" + compProp.getKey().substring(0, 1).toUpperCase() +
                                        compProp.getKey().substring(1);
                                Class<?>[] paramSpec = {compProp.getValue().getClass()};
                                try {
                                    Method meth = toBeInit.getMethod(methName, paramSpec);
                                    log.debug("Initializing component class {}.{}{}) (from property {})",
                                              comp.getClass().getSimpleName(),
                                              meth.getName(), compProp.getValue().getClass().getName(),
                                              compProp.getKey());
                                    meth.invoke(comp, compProp.getValue());
                                } catch (NoSuchMethodException nsme) {
                                    log.warn("No setter found for component class: {}.{}({}) :: " +
                                                     "required by property name: {}",
                                             toBeInit.getName(), methName,
                                             compProp.getValue().getClass().getName(),
                                             compProp.getKey());
                                } catch (Exception e) {
                                    // This is generally a fatal condition, so we'll toss the error up the chain
                                    log.error("Cannot invoke {}.{}({}) due to Exception.", toBeInit.getName(),
                                              methName, compProp.getValue().getClass().getName(), e);
                                    throw e;
                                }
                            }
                        } else {
                            log.warn("No instance of component {} to initialize.",
                                     compToInit.get(COMPONENT_NAME).toString());
                        }
                    } else {
                        log.warn("Component {} was specified for initialization, but no properties were provided. " +
                                         "No initialization was performed.", compToInit.get(COMPONENT_NAME));
                    }
                }
            }
            super.beforeStart();
        } catch (Exception e) {
            log.error("Failed in beforeStart(): ", e);
            throw e;
        }
    }
    
    @Override
    protected void afterStart() throws Exception {
        super.afterStart();
        notifyStarted();
    }
    
    protected void notifyStarted() {
        log.debug("NotifyStarted()");
        synchronized (startupSync) {
            // Avoid busy-wait during startup.
            startupCompleted = true;
            startupSync.notify();
        }
        log.debug("NotifyStarted() completed");
    }
    
    protected void awaitStartup() throws Exception {
        log.debug("AwaitStarted()");
        synchronized (startupSync) {
            if (!startupCompleted) {
                // Avoid busy-wait during startup.
                startupSync.wait();
            }
        }
        log.debug("AwaitStarted() completed");
    }
    
    /**
     * Load a route from a textual description of the route.
     *
     * Here, we'll use the camel runtime to construct a RouteBuilder from the text specification and specification
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
        log.debug("Loading route from {} specificationType", specificationType);
        if (log.isTraceEnabled()) {
            // Dump route spec out here only under trace.  Camel may have substituted key values, and we don't want
            // these in the log files
            log.trace("Loading route (specificationType {}):\n{}", specificationType, specification);
        }
        // First, construct a simple classloader.  This is based on the dependencies defined in the Kamelet. Later,
        // these will be augmented via discovery since these declarations need not be complete. These are used to
        // satisfy camel's need to instantiate as part of setting up the routes.
        Map<String, Set<String>> discResults = new HashMap<>();
        discResults.put(CamelDiscovery.COMPONENTS_TO_LOAD, Set.of());
        discResults.put(CamelDiscovery.DATAFORMATS_TO_LOAD, Set.of());
        ClassLoader cl = buildClassloader(null, discResults);
        
        ModelCamelContext mcc = (ModelCamelContext) camelContext;
        ExtendedCamelContext extendedCamelContext = mcc.adapt(ExtendedCamelContext.class);
        ClassLoader oldAppClassLoader = extendedCamelContext.getApplicationContextClassLoader();
        try {
            extendedCamelContext.setApplicationContextClassLoader(cl);
            RoutesLoader loader = extendedCamelContext.getRoutesLoader();
            Resource resource = ResourceHelper.fromString("in-memory." + specificationType, specification);
            loader.loadRoutes(resource);
            log.debug("loadRoutesFromText(): routes: {}, routeTemplates: {}", mcc.getRoutes(),
                      mcc.getRouteTemplateDefinitions());
            // Based on what was loaded, we can now construct our RouteBuilder.
            // For cases where we have a route, we are done.
            RoutesBuilderLoader rbl = loader.getRoutesLoader(specificationType);
            RoutesBuilder rsb = rbl.loadRoutesBuilder(resource);
            RouteBuilder rb;
            if (rsb instanceof RouteBuilder) {
                rb = (RouteBuilder) rsb;
            } else {
                String className = rsb == null ? "null" : rsb.getClass().getName();
                throw new RuntimeException("loadRoutesFromText(): Expected RouteBuilder by got " + className);
            }
            if (mcc.getRouteTemplateDefinitions().size() > 0) {
                // However, if we have a RouteTemplate rather than a route, we need to construct a route from the template.
                // We don't (currently) support parameters on templates, we'll simply fetch any templates present and
                // build the routes from them (property placeholder substitution will work as expected).
                RoutesDefinition rsd = new RoutesDefinition();
                List<String> beansInvolved = new ArrayList<>();
                mcc.getRouteTemplateDefinitions().forEach((RouteTemplateDefinition rtd) -> {
                    log.debug("loadRoutesFromText(): Found route Def id: {} :: {}", rtd.getId(), rtd.getRoute());
                    String templateId = rtd.getId();
                    TemplatedRouteBuilder builder = TemplatedRouteBuilder.builder(mcc, templateId).routeId(templateId);
                    String routeId;
                    try {
                        routeId = builder.add();
                    } catch (Exception e) {
                        throw new RuntimeException("Error adding route template to context", e);
                    }
                    log.debug("loadRoutesFromText(): Added route (id {}) from template (id {}): {}", routeId,
                              templateId,
                              rtd.getRoute());
                    RouteDefinition rd = mcc.getRouteDefinition(routeId);
                    rsd.route(rd);
                });
                rb.setRouteCollection(rsd);
            } // Else, all the work was done by loading the route.  Only templates have the extra steps above
            log.debug("loadRoutesFromText(): Returning RouteBuilder: {}", rb);
            return rb;
        } finally {
            // TODO -- tbis may need to go as we may need it for dependency determination.
            extendedCamelContext.setApplicationContextClassLoader(oldAppClassLoader);
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getCamelContext().start();
    }
    
    @Override
    protected void doFail(Exception e) {
        // Save our failure reason
        startupFailed = e;
        // In the event of failure, cancel outstanding operations & let the system discover that by marking that
        // things have started.  Otherwise, we hang awaiting the startup monitor.
        log.error("Camel startup has failed due to: ", e);
        notifyStarted();
        super.doFail(e);
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
