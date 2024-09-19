/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import groovy.json.JsonSlurper;
import io.vantiq.extsrc.camel.HeaderDuplicationBean;
import io.vantiq.extsrc.camel.VantiqEndpoint;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.model.dataformat.AvroLibrary;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.ivy.util.FileUtil;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Record;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Perform unit tests for component resolution
 */

// Method order used to check caching for SimpleCamelResolution
// Not using Slf4J due to use in static context
@TestMethodOrder(MethodOrderer.MethodName.class)
public class VantiqComponentResolverTest extends CamelTestSupport {
    public final static String MISSING_VALUE = "<missing>";
    
    private static final Logger log =  LoggerFactory.getLogger(VantiqComponentResolverTest.class);
    public static final String DEST_PATH = "build/loadedlib";
    public static final String IVY_CACHE_PATH = "build/ivyCache";
    
    File dest;
    File cache = new File(IVY_CACHE_PATH);
    
    public static final String sfLoginUrl = System.getProperty("camel-salesforce-loginUrl");
    public static final String sfClientId = System.getProperty("camel-salesforce-clientId");
    public static final String sfClientSecret = System.getProperty("camel-salesforce-clientSecret");
    public static final String sfRefreshToken = System.getProperty("camel-salesforce-refreshToken");
    
    // JIRA properties
    public static String jiraUrl = System.getProperty("io.vantiq.camel.test.jiraUrl",
                                        "https://team-0myhxzwb0ejl.atlassian.net/");
    public static String jiraUsername = System.getProperty("io.vantiq.camel.test.jiraUsername", "invalidUser");
    public static String jiraApiToken = System.getProperty("io.vantiq.camel.test.jiraApiToken", "bogusApiToken");
    public static String jiraJql = System.getProperty("io.vantiq.camel.test.jiraJql", "RAW(project=kamelets)");
    
    @BeforeEach
    public void setup() {
        dest = new File(DEST_PATH);
        if (dest.exists()) {
            FileUtil.forceDelete(dest);
        }
    }
    
    static String getTestMethodName(TestInfo ti) {
        String testName = null;
        if (ti.getTestMethod().isPresent()) {
            testName = ti.getTestMethod().get().getName();
        }
        assertNotNull(testName, "No test name available");
        return testName;
    }
    
    @Test
    public void testResolutionSimpleCamel(TestInfo ti) throws Exception {
        
        FileUtil.forceDelete(cache);    // Clear the cache
        CamelResolver cr = new CamelResolver(getTestMethodName(ti), (URI) null,
                                             cache, dest);
        Collection<File> resolved = cr.resolve("org.apache.camel", "camel" + "-salesforce",
                                               context.getVersion(), getTestMethodName(ti));
        assert !resolved.isEmpty();
        File[] files = resolved.toArray(new File[0]);
        boolean foundRequested = false;
        for (File f: files) {
            assertEquals(dest.getAbsolutePath(), f.getParent(), "Path in destination");
            if (f.getName().equals("camel-salesforce-" + context.getVersion() + ".jar")) {
                foundRequested = true;
            }
        }
        assertTrue(foundRequested, "Found original request");
        
        ArrayList<URL> urlList = new ArrayList<>();
        for (File f: files) {
            URL url = f.toURI().toURL();
            urlList.add(url);
        }
        for (URL one: urlList) {
            log.debug("urlList entry: {}", one.toString());
        }
    
        // Now, let's try & load a class from there...
        
        URLClassLoader ucl = new URLClassLoader(urlList.toArray(new URL[0]),
                                                this.getClass().getClassLoader());
       
        try {
            Class<?> sfClass = Class.forName("org.apache.camel.component.salesforce.SalesforceComponent", false, ucl);
            assertNotNull(sfClass ,"SalesforceComponent class null");
            log.debug("Found SalesforceComponent class: {}::{}", sfClass.getPackageName(), sfClass.getName());

            for (java.lang.reflect.Method m: sfClass.getMethods()) {
                log.debug("SalesforceComponent class has method: {}", m.getName());
            }

            sfClass = Class.forName("org.apache.camel.component.salesforce.SalesforceEndpoint", false, ucl);
            assertNotNull(sfClass, "SalesforceEndpoing class null");
            log.debug("Found SalesforceEndpoint class: {}::{}", sfClass.getPackageName(), sfClass.getName());

            for (java.lang.reflect.Method m: sfClass.getMethods()) {
                log.debug("SalesforceEndpoint class has method: {}", m.getName());
            }
            for (Package p: ucl.getDefinedPackages()) {
                log.debug("Classloader has package: {}", p.getName());
            }
        } catch (Exception e) {
            // Putting assert here so we get information about the unexpected exception
            assertNull(e, "Unexpected Exception");
        }
    }
    
    @Test
    public void testResolutionSimpleCamelCached(TestInfo ti) throws Exception {
        // Here, we leave the cache alone
        
        // Use same app name to avoid spurious Ivy errors about unknown resolvers.  Necessary since we didn't clear
        // the cache, and Ivy keeps resolver names in the cache records.
        String testName = getTestMethodName(ti);
        String nameOfPreviousTest = testName.substring(0, testName.lastIndexOf("Cached"));
        CamelResolver cr = new CamelResolver(nameOfPreviousTest, (URI) null,
                                             cache, dest);
        Collection<File> resolved = cr.resolve("org.apache.camel", "camel" + "-salesforce",
                                               context.getVersion(), testName);
        assert !resolved.isEmpty();
        File[] files = resolved.toArray(new File[0]);
        boolean foundRequested = false;
        for (File f: files) {
            assertEquals(dest.getAbsolutePath(), f.getParent(), "Path in destination");
            if (f.getName().equals("camel-salesforce-" + context.getVersion() + ".jar")) {
                foundRequested = true;
            }
        }
        assertTrue(foundRequested, "Found original request");
    }
    
    @Test
    public void testResolutionFailure(TestInfo ti) {
        String testName = getTestMethodName(ti);
        FileUtil.forceDelete(cache);    // Clear the cache
        CamelResolver cr = new CamelResolver(testName, (URI) null, null, dest);
        log.debug(cr.identity());
        assertTrue(cr.identity().contains(testName), "Identity check (testName):");
        assertTrue(cr.identity().contains(dest.getAbsolutePath()), "Identity check (path):");
    
        try {
            cr.resolve("org.apache.camel", "camel" + "-horse-designed-by-committee",
                       context.getVersion(), testName);
        } catch (ResolutionException re) {
            assert re.getMessage().contains("Error(s) encountered during resolution: ");
            assert re.getMessage().contains("org.apache.camel#camel-horse-designed-by-committee;");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            new CamelResolver("wontexist",(URI) null, null, null);
            fail("Cannot create CamelResolver with a null destination");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The destination parameter cannot be null");
        } catch (Exception e) {
            assertNull(e, "Unexpected exception: " + e.getMessage());
        }
    
        try {
            cr.resolve(null, "somename", "someVersion", testName);
            fail("Null organization should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
    
        try {
            cr.resolve("somegroup", null, "someVersion", testName);
            fail("Null name should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            cr.resolve("somegroup", "somename", null, testName);
            fail("Null revision should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
    }
    @Test
    public void testResolutionAlternateRepos(TestInfo ti) throws Exception {
        String testName = getTestMethodName(ti);
        FileUtil.forceDelete(cache);    // Clear the cache
        URI s3Repo = new URI("https://vantiqmaven.s3.amazonaws.com/");
        CamelResolver cr = new CamelResolver(testName, s3Repo, cache, dest);
        Collection<File> resolved = cr.resolve("vantiq.models", "coco", "1.1", "meta",
                                               testName);
        assertEquals(1, resolved.size(), "Resolved file count: " + resolved.size());
        File[] files = resolved.toArray(new File[0]);
        assertEquals("coco-1.1.meta", files[0].getName(), "File name match");
    }
    
    @Test
    public void testStartRouteLoadedComponents(TestInfo ti) {
        String testName = getTestMethodName(ti);
        
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilderWithProps rb = new SimpleExternalRoute();
        assertNotNull(rb, "No routebuilder");
        setUseRouteBuilder(false);
        try (CamelRunner runner = new CamelRunner(testName, rb, null,
                                                  IVY_CACHE_PATH, DEST_PATH,
                                                  rb.getComponentsToInit(), null, null, null)) {
            runner.runRoutes(false);
        }
    }
    
    @Test
    public void testStartRouteLoadedComponentsAndMarshaling(TestInfo ti) {
        String testName = getTestMethodName(ti);
        
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilderWithProps rb = new MarshaledExternalRoute();
        assertNotNull(rb, "No routebuilder");
        setUseRouteBuilder(false);
        try (CamelRunner runner = new CamelRunner(testName, rb, null,
                                                  IVY_CACHE_PATH, DEST_PATH,
                                                  rb.getComponentsToInit(), null, null, null)) {
            runner.runRoutes(false);
        }
    }
    
    @Test
    public void testStartRouteLoadedComponentsMultiRepo(TestInfo ti) throws Exception {
        String testName = getTestMethodName(ti);
        
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilderWithProps rb = new SimpleExternalRoute();
        assertNotNull(rb, "No routebuilder");
        setUseRouteBuilder(false);
        List<URI> repoList = new ArrayList<>();
        repoList.add(new URI("https://vantiqmaven.s3.amazonaws.com/"));
        repoList.add(new URI("https://repo.maven.apache.org/maven2/"));
        try (CamelRunner runner = new CamelRunner(testName,rb, repoList,
                                                  IVY_CACHE_PATH, DEST_PATH, rb.getComponentsToInit(),
                                                  null, null, null)) {
            runner.runRoutes(false);
        }
    }
    
    @Test
    public void testJiraSpecificRepo(TestInfo ti) throws Exception {
        String testName = getTestMethodName(ti);
        
        // Note -- invalid usenname/password/token result in just errors of not found.  Test still valid
        
        // Set access properties for Jira server
        Properties propertyValues = new Properties(3);
        propertyValues.setProperty("jiraUrl", jiraUrl);
        propertyValues.setProperty("jiraUsername", jiraUsername);
        propertyValues.setProperty("jiraPassword", jiraApiToken);
        propertyValues.setProperty("jiraJql", jiraJql);
        
        FileUtil.forceDelete(cache);    // Clear the cache
        FileUtil.forceDelete(new File(DEST_PATH));
        RouteBuilderWithProps rb = new JiraExternalRoute();
        assertNotNull(rb, "No routebuilder");
        setUseRouteBuilder(false);
        List<URI> repoList = new ArrayList<>();
        
        // Routes using the JIRA component require an additional repo from which to fetch their dependencies.
        // As per https://developer.atlassian.com/server/framework/atlassian-sdk/atlassian-maven-repositories-2818705/,
        // the following is the official atlassian proxy for their public repos. Without this, we end up with a
        // number of unresolved dependencies, and things tend not to go well.
        repoList.add(new URI("https://repo1.maven.org/maven2"));
        repoList.add(new URI("https://packages.atlassian.com/mvn/maven-external/"));
        
        // Something about the combination of the atlassian repo & (maybe) our ivy setup is working has these libraries not
        // found when retrieved via dependency mapping.  This is the only project/component in which this happens.
        // To remedy this for the moment, we'll just add them manually and things work just fine.
        //
        // It appears that some mechanism in the atlassian library is moderating (Ivy term) the requests and removing
        // the revision.  Consequently, we end up not finding .../httpmime-.jar (e.g.) -- the final dash there is
        // where the revision should be. I don't see anything we're doing here, so we'll just patch around it for now.
        // This is new behavior with the Camel 4.4.x release (current LTS release).
        // TODO: Remove this stuff when no longer necessary.  It's probably needed in the CamelAssemblies as well.
        List<String> discoveredDependencies = List.of (
                "org.apache.httpcomponents:httpmime:4.5.13",
                "org.apache.httpcomponents:httpclient-cache:4.5.13"
        );
        
        boolean weInterrupted = false;
        try (CamelRunner runner = new CamelRunner(testName, rb, repoList,
                                                  IVY_CACHE_PATH, DEST_PATH, rb.getComponentsToInit(),
                                                  propertyValues, null, null)) {
            runner.setAdditionalLibraries(discoveredDependencies);
            runner.runRoutes(false);
            log.debug("JIRA-based route started");
            
            Thread runnerThread = runner.getCamelThread();
            int startWait = 1;
            while (!runner.isStarted() && startWait < 25 && !runner.isFailed()) {
                log.debug("Still waiting to start: {}", startWait++);
                Thread.sleep(5000); // Resolution can take a few ticks.
            }
            if (runner.getStartupFailed() != null) {
                log.debug("Failure to start runner: ", runner.getStartupFailed());
            }
            assertNull(runner.getStartupFailed(), "Runner failed to start");
            Thread.sleep(5000); // Run a little bit.
            weInterrupted = true;
            runnerThread.interrupt();
        } catch (InterruptedException ie) {
            // This is OK -- it's how we exit
            if (!weInterrupted) {
                fail("Unexpected interrupted exception");
            }
        } catch (Exception e) {
            fail("Unexpected exception starting jira route: " + e.getClass().getName() + " :: " + e.getMessage());
        }
    }
    @Test
    public void testRouteTemplate(TestInfo ti) {
        String testName = getTestMethodName(ti);
        
        FileUtil.forceDelete(cache);    // Clear the cache
        BeanIncludingRouteTemplate rb = new BeanIncludingRouteTemplate();
        assertNotNull(rb, "No routebuilder");
        setUseRouteBuilder(false);
        // This is the set of discovered dependencies listed in the kamelet from which the route in question is taken.
        List<String> discoveredDependencies = List.of (
                "org.apache.camel:camel-core",
                "org.apache.camel:camel-aws2-s3",
                "org.apache.camel.kamelets:camel-kamelets-utils:3.21.0",
                "org.apache.camel:camel-kamelet"
        );
        try (CamelRunner runner = new CamelRunner(testName, rb, List.of(),
                                                  IVY_CACHE_PATH, DEST_PATH, rb.getComponentsToInit(),
                                                   null, null, null)) {
            runner.setAdditionalLibraries(discoveredDependencies);
            runner.createCamelContext();
            runner.loadRouteFromText(rb.content, "yaml");
            // We only need to test loading the route -- we needn't run this route (no context, etc.)
        } catch (Exception e) {
            fail("Trapped exception during test: " + e.getMessage() +
                         (e.getCause() != null ? e.getCause().getMessage() : ""));
        }
    }
    
    @Test
    public void testHdrDupSetup(TestInfo ti) {
        String testName = getTestMethodName(ti);
        
        FileUtil.forceDelete(cache);    // Clear the cache
        String headerBeanName = "MyHeaderBean" + System.currentTimeMillis();
        HdrRouteTemplate rb = new HdrRouteTemplate(headerBeanName);
        assertNotNull(rb, "No routebuilder");
        setUseRouteBuilder(false);
        // This is the set of discovered dependencies listed in the kamelet from which the route in question is taken.
        List<String> discoveredDependencies = List.of (
                "org.apache.camel:camel-core",
                "org.apache.camel:camel-aws2-s3",
                "org.apache.camel.kamelets:camel-kamelets-utils:3.21.0",
                "org.apache.camel:camel-kamelet"
        );
        
        Map<String, String> hdrDupMap = Map.of("testHdr1", "dupOfHdr1",
                                               "testHdr2", "dupOfHdr2",
                                               "testHdr3", "dupOfHdr3");
        try (CamelRunner runner = new CamelRunner(testName, rb, List.of(),
                                                  IVY_CACHE_PATH, DEST_PATH, rb.getComponentsToInit(),
                                                  null, headerBeanName, hdrDupMap)) {
            runner.setAdditionalLibraries(discoveredDependencies);
            CamelContext ctx = runner.createCamelContext();
            // Override the component type to be used...
            runner.doInit(); // This call creates & loads the bean.
            CamelContext newCtx = runner.getCamelContext();
            assert ctx == newCtx;
            runner.loadRouteFromText(rb.content, "yaml");
            // We only need to test loading the route -- we needn't run this route (no context, etc.)
            // However, to test that we're passing thing beans around as expected, init things & verify that the bean
            // was correctly created.  The component level tests verify that they operate as expected.
            HeaderDuplicationBean bean = ctx.getRegistry().lookupByNameAndType(headerBeanName,
                                                                               HeaderDuplicationBean.class);
            assert bean != null;
            Map<String, String> mapCopy = bean.getHeaderDuplicationMap();
            assert mapCopy.size() == hdrDupMap.size();
            mapCopy.forEach( (k, v) -> {
                assert hdrDupMap.containsKey(k);
                assert hdrDupMap.get(k).equals(v);
            });
            String vantiqEpUri = "vantiq://localhost:8080?structuredMessageHeader=true"
                    + "&" + VantiqEndpoint.HEADER_DUPLICATION_BEAN_NAME + "=" + headerBeanName;
            // I think that starting here fails due to trying to set up the bean name property.  The URL reported in
            // the error looks correct (and the bean with that name is present).  But Camel claims it cannot resolve
            // the endpoint as part of starting it up.  Still don't know why.  That's the $64K question.
            VantiqEndpoint ep = ctx.getEndpoint(vantiqEpUri, VantiqEndpoint.class);
            assert ep != null;
            List<Endpoint> ves = ctx.getEndpointRegistry().values().stream()
                                    .filter( v -> v instanceof VantiqEndpoint)
                                    .collect(Collectors.toList());
            assert ves.size() == 1;
            assert ves.get(0) instanceof VantiqEndpoint;
            VantiqEndpoint ve = (VantiqEndpoint) ves.get(0);
            assert Objects.equals(ve.getHeaderDuplicationBeanName(), headerBeanName);
            // Component tests verify that the map makes it thru, assuming the bean was created correctly.  Since we
            // aren't guaranteed to have enough context here to make the real connection, this is as far as we can
            // effectively test.
        } catch (Exception e) {
            fail("Trapped exception during test: " + e.getMessage() +
                         (e.getCause() != null ? e.getCause().getMessage() : ""));
        }
    }
    
    public static final String QUERY_VANTIQ = "vantiq.com";
    
    public static final String RESPONSE_VANTIQ = "vantiq.com.";
//
    public static final String QUERY_WIKIPEDIA = "wikipedia.org";
    public static final String RESPONSE_WIKIPEDIA = "wikipedia.org.";
    
    @Test
    public void testStartRunLoadedComponents(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDnsLookupCall();
        assertNotNull(rb, "No routebuilder");
        
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
    }
    
    @Test
    public void testStartRunLoadedComponentsMarshaled(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDnsLookupCallMarshaled();
        assertNotNull(rb, "No routebuilder");
        
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
    }
    
    @Test
    public void testStartRunLoadedComponentsMarshaledAvroFailure(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDnsLookupCallMarshaledAvroFailure();
        assertNotNull(rb, "No routebuilder");
        
        performLoadAndRunTest(ti, rb, false, rb.getComponentsToInit(), null, null, null);
    }
    
    @Test
    public void testStartRunLoadedComponentsMarshaledGzip(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDnsLookupCallMarshaledGzip();
        assertNotNull(rb, "No routebuilder");
        
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
    }
    
    @Test
    public void testStartRunLoadedComponentsSalesforceRefreshToken(TestInfo ti) throws Exception {
        assumeTrue(sfLoginUrl != null &&
                                  sfClientId != null &&
                                  sfClientSecret != null &&
                                  sfRefreshToken != null);
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new SalesForceTasks();
        assertNotNull(rb ,"No routebuilder");
        
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
    }
 
    // Test that caching works as expected when thing downloaded, then only cached, and then already in place.
    // In all cases, our internal classloader should load the things needed by the routes in question.
    @Test
    public void testStartRunLoadedComponentsUsingPreviouslyRetrievedComponents(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDnsLookupCall();
        assertNotNull(rb, "No routebuilder");
        
        log.info("Loading cache and library");
        // First run: cache & lib/dest deleted.  Download all
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
        
        // Second run, leave the cache but delete the library.  Should copy from cache
        if (dest.exists()) {
            FileUtil.forceDelete(dest);
        }
        log.info("Cache loaded, deleted lib/dest, running same route");
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
        
        log.info("Leaving cache & lib/dest intact, running again");
        // Finally, leave everything.  Resolution should do nothing but code will run
        performLoadAndRunTest(ti, rb, rb.getComponentsToInit());
    }
    
    public static final String XML_ROUTE = ""
            + "<routes xmlns=\"http://camel.apache.org/schema/spring\" xmlns:foo=\"http://io.vantiq/foo\">"
            + "   <route id=\"dns:lookup from xml-route\">"
            + "      <from uri=\"direct:start\"/>"
            + "      <to uri=\"dns:lookup\"/>"
            + "      <to uri=\"mock:result\"/>"
            + "   </route>"
            + "</routes>";
    
    // Note: Unlike the example on the site, the following will fail to start (claiming > 1 consumer for
    // direct:start) if the top-level "- route" line is missing.d
    public static final String YAML_ROUTE =  "\n"
            + "- route:\n"
            + "    id: \"dns:lookup from yaml-route\"\n"
            + "    from:\n"
            + "      uri: \"direct:start\"\n"
            + "      steps:\n"
            + "        - to:\n"
            + "            uri: \"dns:lookup\"\n"
            + "        - to:\n"
            + "            uri: \"mock:result\"\n";
    
    public static final String YAML_ROUTE_PARAMETERIZED =  "\n"
            + "- route:\n"
            + "    id: \"dns:lookup from yaml-route\"\n"
            + "    from:\n"
            + "      uri: \"{{directStart}}\"\n"
            + "      steps:\n"
            + "        - to:\n"
            + "            uri: \"{{dnsLookup}}\"\n"
            + "        - to:\n"
            + "            uri: \"{{mockResult}}\"\n";
    
    public static final String ROUTE_WITH_BEAN = "\n"
            + "- route: \n"
            + "    id: \"EventHub Sink\" \n"
            + "    from: \n"
            + "        uri: \"direct:start\" \n"
            + "        steps: \n"
            + "        - choice: \n"
            + "            when: \n"
            + "            - simple: \"${header[partition-id]}\" \n"
            + "              steps: \n"
            + "              - set-header: \n"
            + "                  name: CamelAzureEventHubsPartitionId \n"
            + "                  simple: \"${header[partition-id]}\" \n"
            + "            - simple: \"${header[ce-partition-id]}\" \n"
            + "              steps: \n"
            + "              - set-header: \n"
            + "                  name: CamelAzureEventHubsPartitionId \n"
            + "                  simple: \"${header[ce-partition-id]}\" \n"
            + "        - setBody: \n"
            + "            simple: \"${body.message}\" \n"
            + "        - marshal: \n"
            + "            json: \n"
            + "              library: jackson \n"
            + "        - to: \n"
            + "             uri: \"azure-eventhubs://vantiq-test/vantiqNotReallyThere\" \n"
            + "             parameters: \n"
            + "                 sharedAccessName: \"someRandomKey\" \n"
            + "                 sharedAccessKey: \"RAW(MY TOKEN)\" \n";
    
    @Test
    public void testStartRunLoadedComponentsFromXmlText(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
    
        setUseRouteBuilder(false);
    
        performLoadAndRunTest(ti, XML_ROUTE, "xml", null);
    }
    
    @Test
    public void testStartRunLoadedComponentsFromYamlText(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        setUseRouteBuilder(false);
    
        performLoadAndRunTest(ti, YAML_ROUTE, "yaml", null);
    }
    
    @Test
    public void testStartRunLoadedComponentsFromYamlTextParameterized(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        setUseRouteBuilder(false);
        
        Properties propertyValues = new Properties(3);
        propertyValues.setProperty("directStart", "direct:start");
        propertyValues.setProperty("dnsLookup", "dns:lookup");
        propertyValues.setProperty("mockResult", "mock:result");
        
        performLoadAndRunTest(ti, YAML_ROUTE_PARAMETERIZED, "yaml", null, false,
                              propertyValues);
    }
    
    @Test
    public void testDiscoverBeanRoute(TestInfo ti) throws Exception {
        FileUtil.forceDelete(cache);
        setUseRouteBuilder(false);
        
        performLoadAndRunTest(ti, ROUTE_WITH_BEAN, "yaml", null, true, null);
    }
    
    /**
     * Create a callable that the test method will call.
     * <p>
     * In this case, the callable "sends"
     * message to the route which, in turn, makes a call to return some data.  We verify that the expected
     * results are presented.
     * <p>
     * In this case, our route uses the dynamically loaded component to make a call.
     * @return TriFunction<CamelContext, String, Object, Boolean>
     */
    public static TriFunction<CamelContext, String, Object, Boolean> defineVerifyOperation() {
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = (context, query, answerObj) -> {
            boolean worked = true;
            String routeId = context.getRoutes().get(0).getId();
            final String answerStr;
            final Map<String, ?> answerMap;
            
            if (answerObj instanceof String) {
                answerStr = (String) answerObj;
                answerMap = null;
            } else if (answerObj instanceof Map) {
                //noinspection unchecked
                answerMap = (Map<String, ?>) answerObj;
                answerStr = null;
            } else {
                answerMap = null;
                answerStr = null;
            }
    
            try {
                if (context.isStopped()) {
                    log.error("At test start, context {} is stopped", context.getName());
                }
                Endpoint res = context.getEndpoint("mock://result");
                assert res instanceof MockEndpoint;
                MockEndpoint resultEndpoint = (MockEndpoint) res;
            
                ProducerTemplate template = context.createProducerTemplate();
                log.debug("Test code using context: {}, route id: {}", context.getName(), routeId);
                Map<String, Object> headers = new HashMap<>();
                Object message = null;
                int expResults = 2;
                if (routeId.contains("dns:lookup")) {
                    headers.put("dns.name", query);
                } else if (routeId.contains("fhir_sink")) {
                    // For FHIR search by Url, it expects a query parameter of URL that contains the search URL.
                    // However, we don't want to hard code that in the route (and the fhir-sink kamelet, on which the
                    // route for this test is based doesn't), so we'll use the header values as detailed in the FHIR
                    // component.  For those values, we include "CamelFhir.<parameter name>" in the header to get the
                    // value(s) in which we're interested.  Here, we want to look for a patient named "smith", so
                    // we'll use that.
                    message = "{ \"resourceType\": \"Patient\" }";
                    headers = Map.of("CamelFhir.inBody", "url",
                                     "CamelFhir.url", "Patient?name=" + query );
                    expResults = 3;
                }
    
                resultEndpoint.expectedMessageCount(expResults);
                resultEndpoint.expectedMessagesMatches(exchange -> {
                    Object msg = exchange.getIn().getBody();
                    if (msg instanceof Record[]) {
                        Object o = ((Record[]) exchange.getIn().getBody())[0];
                        if (o instanceof ARecord) {
                            ARecord t = (ARecord) o;
                            assertNotNull(answerStr);
                            assertNotNull(t.getName());
                            return answerStr.contains(t.getName().toString());
                        } else {
                            fail("Unexpected return type: " + (o == null ? "body/section is null" :
                                    o.getClass().getName()));
                        }
                    } else if (msg instanceof Map) {
                        //noinspection unchecked
                        Map<String, ?> msgMap = (Map<String, ?>) msg;
                        assertNotNull(answerMap);
                        boolean result = true;
                        for (Map.Entry<String, ?> ansEnt: answerMap.entrySet()) {
                            result &= msgMap.containsKey(ansEnt.getKey());
                            if (!ansEnt.getValue().equals(MISSING_VALUE)) {
                                result &= msgMap.get(ansEnt.getKey()).equals(ansEnt.getValue());
                            }
                        }
                        return result;
                    } else if (msg instanceof InputStreamCache) {
                        assertNotNull(answerStr);
                        InputStreamCache isc = (InputStreamCache) msg;
                        String msgString = new String(isc.readAllBytes(), StandardCharsets.UTF_8);
                        // THe FHIR Bundle structure is used to return the results.  In our route, we've converted it
                        // to JSON, but that, due to camel-isms, give is a stream containing the JSON String (not a
                        // bad idea since this one's ~ 26K).  So, we'll suck in the bytes from the stream and
                        // convert it to JSON.  Then, we can verify the basics of our result & let the machine search
                        // thru all that looking for a "smith" (which was who we asked for).
                        log.debug("Message string is: {}", msgString);
                        JsonSlurper jss = new JsonSlurper();
                        Object rawData = jss.parse(msgString.getBytes());
                        Map<String, ?> msgMap = null;
                        if (rawData instanceof Map){
                            //noinspection unchecked,rawtypes
                            msgMap = (Map) rawData;
                        } else if (rawData == null) {
                            log.error("RAWDATA is null");
                        } else {
                            log.error("Unexpected type returned from FHIR search: {}", rawData.getClass().getName());
                            return false;
                        }
                        boolean result = msgString.contains(answerStr);
                        assertNotNull(msgMap, "Missing msgMap value");
                        result = result && "Bundle".equals(msgMap.get("resourceType"));
                        result = result && "searchset".equals(msgMap.get("type"));
                        return result;
                    } else if (msg instanceof List) {
                        // Then we're running a lookup query to test marshalling, so check the results
                        //noinspection unchecked rawtypes
                        List<Map> msgs = (List<Map>) msg;
                        msgs.forEach( (anItem) -> {
                            log.debug("List<Map> item contents: {}", anItem);
                            assert anItem.containsKey("name");
                            assert anItem.containsKey("type");
                            assert anItem.containsKey("dclass");
                            assert anItem.containsKey("ttl");
                            assert anItem.containsKey("address");
                            assert anItem.containsKey("rrsetType");
                            assert anItem.containsKey("additionalName");
                            assert anItem.get("address") instanceof String;
                            assertEquals(answerStr, anItem.get("address"), "address content does not match");
                        });
                        return true;
                    }
                    return false;
                });
    
                if (context.isStopped()) {
                    log.error("Context {} is stopped", context.getName());
                }
                template.sendBodyAndHeaders("direct:start", message, headers);
                
                assertTrue(resultEndpoint.getReceivedCounter() > 0, "Expected some exchanges");
                log.debug("resultEndpoint received counter: {}", resultEndpoint.getReceivedCounter());
                if (resultEndpoint.getReceivedCounter() >= expResults) {
                    resultEndpoint.assertIsSatisfied();
                }
            } catch (Exception e) {
                log.error("Route " + routeId + ": Trapped exception", e);
                worked = false;
            }
            return worked;
        };
        return verifyOperation;
    }
    
    public void performLoadAndRunTest(TestInfo ti, String content, String contentType,
                                      List<Map<String, Object>> compToInit) throws Exception {
        performLoadAndRunTest(ti, content, contentType, compToInit, false, null);
    }
    
    public void performLoadAndRunTest(TestInfo ti, String content, String contentType,
                                      List<Map<String, Object>> compToInit, boolean defeatVerify,
                                      Properties propertyValues) throws Exception {
        String testName = getTestMethodName(ti);
        
        // To do this test, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the dns call to look up a ip address.  We verify that the expected
        // results is presented.
        //
        // In this case, our MakeDnkLookupCall route uses the (dynamically loaded) dns component to make a lookup call.
    
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = defineVerifyOperation();
        
        CamelContext runnerContext;
        Thread runnerThread;
        CamelRunner openedRunner;
        
        try (CamelRunner runner =
                     new CamelRunner(testName, content, contentType, null,
                                     IVY_CACHE_PATH, DEST_PATH, compToInit, propertyValues, null, null)) {
            openedRunner = runner;
            runner.runRoutes(false);
            runnerContext = runner.getCamelContext();
            runnerThread = runner.getCamelThread();
            assert runner.isStarted();
    
            // At this point, the route is running -- let's verify that it works.
    
            if (!defeatVerify) {
                assert verifyOperation.apply(runnerContext, QUERY_VANTIQ, RESPONSE_VANTIQ);
                assert verifyOperation.apply(runnerContext, QUERY_WIKIPEDIA, RESPONSE_WIKIPEDIA);
            }
        }
        
        while (!openedRunner.isStopped()) {
            Thread.sleep(500);
        }
        
        assert runnerThread != null && !runnerThread.isAlive();
        assert runnerContext != null && runnerContext.isStopped();
    }
    
    public void performLoadAndRunTest(TestInfo ti, RouteBuilder rb, List<Map<String, Object>> compToInit) throws Exception {
        performLoadAndRunTest(ti, rb, true, compToInit, null, null, null);
    }
    public void performLoadAndRunTest(TestInfo ti, RouteBuilder rb, boolean shouldStart,
                                      List<Map<String, Object>> componentToInit,
                                      Properties propertyValues, String headerBeanName,
                                      Map<String, String> headerDuplications) throws Exception {
        // To do this test, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the lookup call to look up an ip address.  We verify that
        // the
        // expected results are presented.
        //
        // In this case, our MakeDnsLookup Call route uses the (dynamically loaded) dns component to make a lookup call.
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = defineVerifyOperation();
        
        String testName = getTestMethodName(ti);
        
        CamelContext runnerContext;
        Thread runnerThread;
        CamelRunner openedRunner = null;
        
        try (CamelRunner runner =
                     new CamelRunner(testName, rb, null, IVY_CACHE_PATH, DEST_PATH,
                                     componentToInit, propertyValues, headerBeanName, headerDuplications)) {
            openedRunner = runner;
            runner.runRoutes(false);
            runnerContext = runner.getCamelContext();
            runnerThread = runner.getCamelThread();
            assert runner.isStarted() == shouldStart;
            if (shouldStart) {
                // At this point, the route is running -- let's verify that it works.
                String routeId = runnerContext.getRoutes().get(0).getId();
                if (routeId.contains("dns:lookup")) {
                    assert verifyOperation.apply(runnerContext, QUERY_VANTIQ, RESPONSE_VANTIQ);
                    assert verifyOperation.apply(runnerContext, QUERY_WIKIPEDIA, RESPONSE_WIKIPEDIA);
                } else if (routeId.contains("Salesforce")) {
                    assert verifyOperation.apply(runnerContext, "not used",
                                                 Map.of ("totalSize", MISSING_VALUE, "done", true));
                    assert verifyOperation.apply(runnerContext, "not used",
                                                 Map.of ("totalSize", MISSING_VALUE, "done", true));
                }
            }
        } finally {
            while (openedRunner != null && !openedRunner.isStopped()) {
                Thread.sleep(500);
            }
        }
    
        assertNotNull(runnerThread, "runnerThread is null");
        assert runnerContext != null && runnerContext.isStopped();
        int deathWaitCount = 30;
        // Wait for thread to die off.  Otherwise, spurious errors occasionally.
        while (runnerThread.isAlive() && deathWaitCount > 0) {
            Thread.sleep(500);
            deathWaitCount -= 1;
        }
        // Ensure that the running thread has completed.  Issue of test resource starvation, not product
        assertTrue(!shouldStart || !runnerThread.isAlive(),
                   "ShouldStart: " + shouldStart + ", runnerThread: " + runnerThread +
                           ", ...isAlive: " + runnerThread.isAlive());
    }
    
    private static abstract class RouteBuilderWithProps extends RouteBuilder {
        
        public List<Map<String, Object>> getComponentsToInit() {
            return null;
        }
    }
    
    private static class SimpleExternalRoute extends RouteBuilderWithProps  {
        @Override
        public void configure() {
            // From some jetty resource, get the message content & log it.
            
            // The following does nothing, but verifies that things all start up.  All we are really verifying here
            // is that all the classes are loaded so that camel & its associated components are operating.
            
            // Since 1) we don't really care, and 2) Jenkins runs may not allow us to pick a "normal" port,
            // we'll specify port 0 here.  Using port 0 tells Jetty to pick an unused port (well, actually, this tells
            // the lower-level socket constructor).  In either case, this allows this code to work in
            // environments more constrained than one's own machine.
            
            from("jetty:http://0.0.0.0:0/myapp/myservice/?sessionSupport=true")
                    .to("log:" + log.getName()  + "level=debug");
        }
    }
    
    private static class JiraExternalRoute extends RouteBuilderWithProps  {
        @Override
        public void configure() {
            // From some jira resource, look for new issues in some project.  At present, this is usually empty, but
            // we verify that that things load and start.
            
            // The following does nothing, but verifies that things all start up.  All we are really verifying here
            // is that all the classes are loaded so that camel & its associated components are operating.
    
            from("jira:newIssues?jiraUrl={{jiraUrl}}&jql={{jiraJql}}&username={{jiraUsername}}&" +
                         "password={{jiraPassword}}")
                    .to("log:" + log.getName() + "?level=debug");
         }
    }
    
    private static class MarshaledExternalRoute extends RouteBuilderWithProps  {
        
        @Override
        public void configure() {
            // From some jetty resource, get the message content & log it.
            
            // The following does nothing, but verifies that things all start up.  All we are really verifying here
            // is that all the classes are loaded so that camel & its associated components are operating.
            
            // Since 1) we don't really care, and 2) Jenkins runs may not allow us to pick a "normal" port,
            // we'll specify port 0 here.  Using port 0 tells Jetty to pick an unused port (well, actually, this tells
            // the lower-level socket constructor).  In either case, this allows this code to work in
            // environments more constrained than one's own machine.
            
            from("jetty:http://0.0.0.0:0/myapp/myservice/?sessionSupport=true")
                    .marshal().csv()
                    .to("log:" + log.getName()  + "level=debug");
        }
    }
    
    private static class MakeDnsLookupCall extends RouteBuilderWithProps {
        public void configure() {
            from("direct:start")
                    .routeId("Simple dns:lookup Call")
                    .to("dns:lookup")
                    .to("mock:result");
        }
    }
    
    // The *Marshaled* route builders here verify that we properly discover & load data formats
    // Generally, the routes are not expected to be sensible or useful.
    
    private static class MakeDnsLookupCallMarshaled extends RouteBuilderWithProps {
        public void configure() {
            from("direct:start")
                    .routeId("dns:lookup Call Marshaled")
                    .to("dns:lookup")
                    .log("INFO")
                    .marshal().json(JsonLibrary.Jackson)
                    .log("INFO")
                    .unmarshal().json(JsonLibrary.Jackson)
                    .log("INFO")
                    .to("mock:result");
        }
    }
    
    private static class MakeDnsLookupCallMarshaledGzip extends RouteBuilderWithProps {

        public void configure() {
            from("direct:start")
                    .routeId("dns:lookup Call MarshaledGzip")
                    .to("dns:lookup")
                    .log("INFO")
                    .marshal().json()
                    .marshal().gzipDeflater()
                    .log("INFO")
                    .unmarshal().gzipDeflater()
                    .unmarshal().json()
                    .log("INFO")
                    .to("mock:result");
        }
    }
    
    private static class MakeDnsLookupCallMarshaledAvroFailure extends RouteBuilderWithProps {
        
        public void configure() {
            from("direct:start")
                    .routeId("dns:lookup Call Marshaled Avro Failure")
                    .to("dns:lookup")
                    .log("INFO")
                    .marshal().json(JsonLibrary.Jackson, JsonNode.class)
                    .marshal().avro(AvroLibrary.ApacheAvro)
                    .log("INFO")
                    .unmarshal().avro(AvroLibrary.ApacheAvro, JsonNode.class)
                    .unmarshal().json(JsonLibrary.Jackson)
                    .log("INFO")
                    .to("mock:result");
        }
    }
    
    public static class SalesForceTasks extends RouteBuilderWithProps {
        private final Map<String, String> propList = Map.of( "loginUrl", sfLoginUrl,
                                                             "clientId", sfClientId,
                                                             "clientSecret", sfClientSecret,
                                                             "refreshToken", sfRefreshToken
        );
        
        @Override
        public List<Map<String, Object>> getComponentsToInit() {
            return List.of(
                    Map.of(CamelRunner.COMPONENT_NAME, "salesforce",
                           CamelRunner.COMPONENT_PROPERTIES, propList));
        }
        
        public void configure() {
            
            from("direct:start")
                    .routeId("Salesforce Query")
                    .to("salesforce:query?rawPayload=true&sObjectQuery=SELECT Id, Subject, OwnerId from Task")
                    .unmarshal().json()
                    .to("log:info")
                    .to("mock:result");
        }
    }
    
    private static class BeanIncludingRouteTemplate extends RouteBuilderWithProps {
        public String content = ""
                + "-   routeTemplate:\n"
                + "        id: Route templates from aws_s3_source:v3_21_0\n"
                + "        beans:\n"
                + "        -   name: renameHeaders\n"
                + "            type: '#class:org.apache.camel.kamelets.utils.headers.DuplicateNamingHeaders'\n"
                + "            property:\n"
                + "            -   key: prefix\n"
                + "                value: CamelAwsS3\n"
                + "            -   key: renamingPrefix\n"
                + "                value: aws.s3.\n"
                + "            -   key: mode\n"
                + "                value: filtering\n"
                + "            -   key: selectedHeaders\n"
                + "                value: CamelAwsS3Key,CamelAwsS3BucketName\n"
                + "        from:\n"
                + "            uri: aws2-s3:someSillyBucket \n"
                + "            parameters:\n"
                + "                autoCreateBucket: 'false'\n"
                + "                secretKey: 'dont tell'\n"
                + "                accessKey: 'let me in'\n"
                + "                region: 'us-west-2'\n"
                + "                ignoreBody: 'false'\n"
                + "                deleteAfterRead: 'false'\n"
                + "                prefix: 'null'\n"
                + "                useDefaultCredentialsProvider: 'true'\n"
                + "                uriEndpointOverride: ''\n"
                + "                overrideEndpoint: 'false'\n"
                + "                delay: '500'\n"
                + "            steps:\n"
                + "            -   process:\n"
                + "                    ref: '{{renameHeaders}}'\n"
                + "            -   to: vantiq://server.config?structuredMessageHeader=true";

        @Override
        public void configure() {
 
        }
    }
    private static class HdrRouteTemplate extends RouteBuilderWithProps {
        String headerDupBeanName;
        
        public String content = ""
                + "-   routeTemplate:\n"
                + "        id: Route templates from aws_s3_source:v3_21_0\n"
                + "        beans:\n"
                + "        -   name: renameHeaders\n"
                + "            type: '#class:org.apache.camel.kamelets.utils.headers.DuplicateNamingHeaders'\n"
                + "            property:\n"
                + "            -   key: prefix\n"
                + "                value: CamelAwsS3\n"
                + "            -   key: renamingPrefix\n"
                + "                value: aws.s3.\n"
                + "            -   key: mode\n"
                + "                value: filtering\n"
                + "            -   key: selectedHeaders\n"
                + "                value: CamelAwsS3Key,CamelAwsS3BucketName\n"
                + "        from:\n"
                + "            uri: aws2-s3:someSillyBucket \n"
                + "            parameters:\n"
                + "                autoCreateBucket: 'false'\n"
                + "                secretKey: 'dont tell'\n"
                + "                accessKey: 'let me in'\n"
                + "                region: 'us-west-2'\n"
                + "                ignoreBody: 'false'\n"
                + "                deleteAfterRead: 'false'\n"
                + "                prefix: 'null'\n"
                + "                useDefaultCredentialsProvider: 'true'\n"
                + "                uriEndpointOverride: ''\n"
                + "                overrideEndpoint: 'false'\n"
                + "                delay: '500'\n"
                + "            steps:\n"
                + "            -   process:\n"
                + "                    ref: '{{renameHeaders}}'\n"
                + "            -   to: vantiq://localhost:8080?structuredMessageHeader=true";
        
        
        HdrRouteTemplate(String headerDupBeanName) {
            this.headerDupBeanName = headerDupBeanName;
            if (headerDupBeanName != null) {
                content = content.concat(
                        "&" + VantiqEndpoint.HEADER_DUPLICATION_BEAN_NAME + "=" + headerDupBeanName
                );
            }
            this.content = this.content.concat("\n");
        }
        
        @Override
        public void configure() {
        
        }
    }
}
