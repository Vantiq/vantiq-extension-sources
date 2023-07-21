/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.AvroLibrary;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.ivy.util.FileUtil;
import org.junit.Assume;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Message;
import org.xbill.DNS.Section;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Perform unit tests for component resolution
 */

// Method order used to check caching for SimpleCamelResolution
// Not using Slf4J due to use in static context
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VantiqComponentResolverTest extends CamelTestSupport {
    public final static String MISSING_VALUE = "<missing>";
    
    @Rule
    public TestName testName = new TestName();
    
    private static final Logger log =  LoggerFactory.getLogger(VantiqComponentResolverTest.class);
    public static final String DEST_PATH = "build/loadedlib";
    public static final String IVY_CACHE_PATH = "build/ivyCache";
    
    File dest;
    File cache = new File(IVY_CACHE_PATH);
    
    public static final String sfLoginUrl = System.getProperty("camel-salesforce-loginUrl");
    public static final String sfClientId = System.getProperty("camel-salesforce-clientId");
    public static final String sfClientSecret = System.getProperty("camel-salesforce-clientSecret");
    public static final String sfRefreshToken = System.getProperty("camel-salesforce-refreshToken");
    
    @Before
    public void setup() {
        dest = new File(DEST_PATH);
        if (dest.exists()) {
            FileUtil.forceDelete(dest);
        }
    }
    
    @Test
    public void testResolutionSimpleCamel() throws Exception {
        
        FileUtil.forceDelete(cache);    // Clear the cache
        CamelResolver cr = new CamelResolver(this.getTestMethodName(), (URI) null,
                                             cache, dest);
        Collection<File> resolved = cr.resolve("org.apache.camel", "camel" + "-salesforce",
                                               context.getVersion(), testName.getMethodName());
        assert resolved.size() > 0;
        File[] files = resolved.toArray(new File[0]);
        boolean foundRequested = false;
        for (File f: files) {
            assertEquals("Path in destination", dest.getAbsolutePath(), f.getParent());
            if (f.getName().equals("camel-salesforce-" + context.getVersion() + ".jar")) {
                foundRequested = true;
            }
        }
        assertTrue("Found original request", foundRequested);
        
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
            assertNotNull("SalesforceComponent class null", sfClass);
            log.debug("Found SalesforceComponent class: {}::{}", sfClass.getPackageName(), sfClass.getName());

            for (java.lang.reflect.Method m: sfClass.getMethods()) {
                log.debug("SalesforceComponent class has method: {}", m.getName());
            }

            sfClass = Class.forName("org.apache.camel.component.salesforce.SalesforceEndpoint", false, ucl);
            assertNotNull("SalesforceEndpoing class null", sfClass);
            log.debug("Found SalesforceEndpoint class: {}::{}", sfClass.getPackageName(), sfClass.getName());

            for (java.lang.reflect.Method m: sfClass.getMethods()) {
                log.debug("SalesforceEndpoint class has method: {}", m.getName());
            }
            for (Package p: ucl.getDefinedPackages()) {
                log.debug("Classloader has package: {}", p.getName());
            }
        } catch (Exception e) {
            // Putting assert here so we get information about the unexpected exception
            assertNull("Unexpected Exception", e);
        }
    }
    
    @Test
    public void testResolutionSimpleCamelCached() throws Exception {
        // Here, we leave the cache alone
        CamelResolver cr = new CamelResolver(this.getTestMethodName(), (URI) null,
                                             cache, dest);
        Collection<File> resolved = cr.resolve("org.apache.camel", "camel" + "-salesforce",
                                               context.getVersion(), testName.getMethodName());
        assert resolved.size() > 0;
        File[] files = resolved.toArray(new File[0]);
        boolean foundRequested = false;
        for (File f: files) {
            assertEquals("Path in destination", dest.getAbsolutePath(), f.getParent());
            if (f.getName().equals("camel-salesforce-" + context.getVersion() + ".jar")) {
                foundRequested = true;
            }
        }
        assertTrue("Found original request", foundRequested);
    }
    
    @Test
    public void testResolutionFailure() throws Exception {
        CamelResolver cr = new CamelResolver(this.getTestMethodName(), (URI) null, null, dest);
        log.debug(cr.identity());
        assertTrue("Identity check:", cr.identity().contains(this.getTestMethodName()));
        assertTrue("Identity check:", cr.identity().contains(dest.getAbsolutePath()));
    
        try {
            Collection<File> resolved = cr.resolve("org.apache.camel",
                                                         "camel" + "-horse-designed-by-committee",
                                                         context.getVersion(),
                                                         testName.getMethodName());
        } catch (ResolutionException re) {
            assert re.getMessage().contains("Error(s) encountered during resolution: ");
            assert re.getMessage().contains("org.apache.camel#camel-horse-designed-by-committee;");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            CamelResolver nope = new CamelResolver("wontexist",(URI) null, null, null);
            fail("Cannot create CamelResolver with a null destination");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The destination parameter cannot be null");
        } catch (Exception e) {
            assertNull("Unexpected exception: " + e.getMessage(), e);
        }
    
        try {
            cr.resolve(null, "somename", "someVersion", testName.getMethodName());
            fail("Null organization should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
    
        try {
            cr.resolve("somegroup", null,
                             "someVersion", testName.getMethodName());
            fail("Null name should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            cr.resolve("somegroup", "somename", null, testName.getMethodName());
            fail("Null revision should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            cr.resolve("somegroup", "somename", "someVersion", testName.getMethodName());
            fail("Non-sensical resolution should fail");
        } catch (ResolutionException re) {
            assert re.getMessage().contains("Error(s) encountered during resolution: ");
            assert re.getMessage().contains("somegroup#somename;someVersion");
         } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
    }
    @Test
    public void testResolutionAlternateRepos() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        URI s3Repo = new URI("https://vantiqmaven.s3.amazonaws.com/");
        CamelResolver cr = new CamelResolver(this.getTestMethodName(), s3Repo, cache, dest);
        Collection<File> resolved = cr.resolve("vantiq.models", "coco", "1.1", "meta",
                                                       testName.getMethodName());
        assertEquals("Resolved file count: " + resolved.size(), 1, resolved.size());
        File[] files = resolved.toArray(new File[0]);
        assertEquals("File name match", "coco-1.1.meta", files[0].getName());
    }
    
    @Test
    public void testStartRouteLoadedComponents() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilderWithProps rb = new SimpleExternalRoute();
        assertNotNull("No routebuilder", rb);
        setUseRouteBuilder(false);
        try (CamelRunner runner = new CamelRunner(this.getTestMethodName(), rb, null,
                                                  IVY_CACHE_PATH, DEST_PATH,
                                                  rb.getComponentsToInit(), null)) {
            runner.runRoutes(false);
        }
    }
    
    @Test
    public void testStartRouteLoadedComponentsAndMarshaling() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilderWithProps rb = new MarshaledExternalRoute();
        assertNotNull("No routebuilder", rb);
        setUseRouteBuilder(false);
        try (CamelRunner runner = new CamelRunner(this.getTestMethodName(), rb, null,
                                                  IVY_CACHE_PATH, DEST_PATH,
                                                  rb.getComponentsToInit(), null)) {
            runner.runRoutes(false);
        }
    }
    
    
    @Test
    public void testStartRouteLoadedComponentsMultiRepo() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilderWithProps rb = new SimpleExternalRoute();
        assertNotNull("No routebuilder", rb);
        setUseRouteBuilder(false);
        List<URI> repoList = new ArrayList<>();
        repoList.add(new URI("https://vantiqmaven.s3.amazonaws.com/"));
        repoList.add(new URI("https://repo.maven.apache.org/maven2/"));
        try (CamelRunner runner = new CamelRunner(this.getTestMethodName(),rb, repoList,
                                                  IVY_CACHE_PATH, DEST_PATH, rb.getComponentsToInit(), null)) {
            runner.runRoutes(false);
        }
    }
    
    public static final String QUERY_MONKEY = "monkey.wp.dg.cx";
    public static final String RESPONSE_MONKEY = "\"A Macaque, an old world species of "
            + "monkey native to Southeast Asia|thumb]A monkey is a primate of the "
            + "Haplorrhini suborder and simian infraorder, either an Old World monkey "
            + "or a New World monkey, but excluding apes. There are about 260 known "
            + "living specie\" \"s of monkey. Many are arboreal, although there are "
            + "species that live primarily on the ground, such as baboons... "
            + "http://en.wikipedia.org/wiki/Monkey\"";
    
    public static final String QUERY_AARDVARK = "aardvark.wp.dg.cx";
    public static final String RESPONSE_AARDVARK = "\"The aardvark (Orycteropus afer) is a medium-sized, burrowing, " +
            "nocturnal mammal native to Africa. It is the only living species of the order Tubulidentata, although " +
            "other prehistoric species and genera of Tubulidentata are known. " +
            "http://en.wikipedia.org/wi\" \"ki/Aardvark\"";
    
    @Test
    public void testStartRunLoadedComponents() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDigCall();
        assertNotNull("No routebuilder", rb);
        
        performLoadAndRunTest(rb, rb.getComponentsToInit());
    }
    
    @Test
    public void testStartRunLoadedComponentsMarshaled() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDigCallMarshaled();
        assertNotNull("No routebuilder", rb);
        
        performLoadAndRunTest(rb, rb.getComponentsToInit());
    }
    
    @Test
    public void testStartRunLoadedComponentsMarshaledAvroFailure() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDigCallMarshaledAvroFailure();
        assertNotNull("No routebuilder", rb);
        
        performLoadAndRunTest(rb, false, rb.getComponentsToInit(), null);
    }
    
    @Test
    public void testStartRunLoadedComponentsMarshaledGzip() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDigCallMarshaledGzip();
        assertNotNull("No routebuilder", rb);
        
        performLoadAndRunTest(rb, rb.getComponentsToInit());
    }
    
    @Test
    public void testStartRunLoadedComponentsSalesforceRefreshToken() throws Exception {
        Assume.assumeTrue(sfLoginUrl != null &&
                                  sfClientId != null &&
                                  sfClientSecret != null &&
                                  sfRefreshToken != null);
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new SalesForceTasks();
        assertNotNull("No routebuilder", rb);
        
        performLoadAndRunTest(rb, rb.getComponentsToInit());
    }
 
    // Test that caching works as expected when thing downloaded, then only cached, and then already in place.
    // In all cases, our internal classloader should load the things needed by the routes in question.
    @Test
    public void testStartRunLoadedComponentsUsingPreviouslyRetrievedComponents() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilderWithProps rb = new MakeDigCall();
        assertNotNull("No routebuilder", rb);
        
        log.info("Loading cache and library");
        // First run: cache & lib/dest deleted.  Download all
        performLoadAndRunTest(rb, rb.getComponentsToInit());
        
        // Second run, leave the cache but delete the library.  Should copy from cache
        if (dest.exists()) {
            FileUtil.forceDelete(dest);
        }
        log.info("Cache loaded, deleted lib/dest, running same route");
        performLoadAndRunTest(rb, rb.getComponentsToInit());
        
        log.info("Leaving cache & lib/dest intact, running again");
        // Finally, leave everything.  Resolution should do nothing but code will run
        performLoadAndRunTest(rb, rb.getComponentsToInit());
    }
    
    public static final String XML_ROUTE = ""
            + "<routes xmlns=\"http://camel.apache.org/schema/spring\" xmlns:foo=\"http://io.vantiq/foo\">"
            + "   <route id=\"Dig from xml-route\">"
            + "      <from uri=\"direct:start\"/>"
            + "      <to uri=\"dns:dig\"/>"
            + "      <to uri=\"mock:result\"/>"
            + "   </route>"
            + "</routes>";
    
    // Note: Unlike the example on the site, the following will fail to start (claiming > 1 consumer for
    // direct:start) if the top-level "- route" line is missing.d
    public static final String YAML_ROUTE =  "\n"
            + "- route:\n"
            + "    id: \"Dig from yaml-route\"\n"
            + "    from:\n"
            + "      uri: \"direct:start\"\n"
            + "      steps:\n"
            + "        - to:\n"
            + "            uri: \"dns:dig\"\n"
            + "        - to:\n"
            + "            uri: \"mock:result\"\n";
    
    public static final String YAML_ROUTE_PARAMETERIZED =  "\n"
            + "- route:\n"
            + "    id: \"Dig from yaml-route\"\n"
            + "    from:\n"
            + "      uri: \"{{directStart}}\"\n"
            + "      steps:\n"
            + "        - to:\n"
            + "            uri: \"{{dnsDig}}\"\n"
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
    public void testStartRunLoadedComponentsFromXmlText() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
    
        setUseRouteBuilder(false);
    
        performLoadAndRunTest(XML_ROUTE, "xml", null);
    }
    
    @Test
    public void testStartRunLoadedComponentsFromYamlText() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        setUseRouteBuilder(false);
    
        performLoadAndRunTest(YAML_ROUTE, "yaml", null);
    }
    
    @Test
    public void testStartRunLoadedComponentsFromYamlTextParameterized() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        setUseRouteBuilder(false);
        
        Properties propertyValues = new Properties(3);
        propertyValues.setProperty("directStart", "direct:start");
        propertyValues.setProperty("dnsDig", "dns:dig");
        propertyValues.setProperty("mockResult", "mock:result");
        
        performLoadAndRunTest(YAML_ROUTE_PARAMETERIZED, "yaml", null, false,
                              propertyValues);
    }
    
    @Test
    public void testDiscoverBeanRoute() throws Exception {
        FileUtil.forceDelete(cache);
        setUseRouteBuilder(false);
        
        performLoadAndRunTest(ROUTE_WITH_BEAN, "yaml", null, true, null);
    }
    
    /**
     * Create a callable that the test method will call.
     *
     * In this case, the callable "sends"
     * message to the route which, in turn, makes a call to return some data.  We verify that the expected
     * results are presented.
     *
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
                
                resultEndpoint.expectedMessageCount(2);
                resultEndpoint.expectedMessagesMatches(new Predicate() {
                    public boolean matches(Exchange exchange) {
                        Object msg = exchange.getIn().getBody();
                        if (msg instanceof Message) {
                            String str =
                                    ((Message) exchange.getIn().getBody()).getSection(Section.ANSWER).get(0)
                                                                          .rdataToString();
                            log.debug("Matches: Route {} got {}", routeId, str);
                            assertNotNull(answerStr);
                            return answerStr.contains(str);
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
                        }
                        return false;
                    }
                });
    
                Map<String, Object> headers = new HashMap<>();
                if (routeId.contains("Dig")) {
                    headers.put("dns.name", query);
                    headers.put("dns.type", "TXT");
                }
                if (context.isStopped()) {
                    log.error("Context {} is stopped", context.getName());
                }
                template.sendBodyAndHeaders("direct:start", null, headers);
                
                assertTrue("Expected some exchanges", resultEndpoint.getReceivedCounter() > 0);
                if (resultEndpoint.getReceivedCounter() > 1) {
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
    
    public void performLoadAndRunTest(String content, String contentType,
                                      List<Map<String, Object>> compToInit) throws Exception {
        performLoadAndRunTest(content, contentType, compToInit, false, null);
    }
    
    public void performLoadAndRunTest(String content, String contentType,
                                      List<Map<String, Object>> compToInit, boolean defeatVerify,
                                      Properties propertyValues) throws Exception {
        // To do this test, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the dig call to lookup a monkey.  We verify that the expected
        // results is presented.
        //
        // In this case, our MakeDigCall route uses the (dynamically loaded) dns component to make a dig call.
    
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = defineVerifyOperation();
        
        CamelContext runnerContext = null;
        Thread runnerThread = null;
        CamelRunner openedRunner = null;
        
        try (CamelRunner runner =
                     new CamelRunner(this.getTestMethodName(), content, contentType, null,
                                     IVY_CACHE_PATH, DEST_PATH, compToInit, propertyValues)) {
            openedRunner = runner;
            runner.runRoutes(false);
            runnerContext = runner.getCamelContext();
            runnerThread = runner.getCamelThread();
            assert runner.isStarted();
    
            // At this point, the route is running -- let's verify that it works.
    
            if (!defeatVerify) {
                assert verifyOperation.apply(runnerContext, QUERY_MONKEY, RESPONSE_MONKEY);
                assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, RESPONSE_AARDVARK);
            }
        }
        
        while (!openedRunner.isStopped()) {
            Thread.sleep(500);
        }
        
        assert runnerThread != null && !runnerThread.isAlive();
        assert runnerContext != null && runnerContext.isStopped();
    }
    
    public void performLoadAndRunTest(RouteBuilder rb, List<Map<String, Object>> compToInit) throws Exception {
        performLoadAndRunTest(rb, true, compToInit, null);
    }
    public void performLoadAndRunTest(RouteBuilder rb, boolean shouldStart,
                                      List<Map<String, Object>> componentToInit,
                                      Properties propertyValues) throws Exception {
        // To do this test, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the dig call to lookup a monkey & aardvark.  We verify that the
        // expected results are presented.
        //
        // In this case, our MakeDigCall route uses the (dynamically loaded) dns component to make a dig call.
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = defineVerifyOperation();
    
        CamelContext runnerContext = null;
        Thread runnerThread = null;
        CamelRunner openedRunner = null;
        
        try (CamelRunner runner =
                     new CamelRunner(this.getTestMethodName(), rb, null, IVY_CACHE_PATH, DEST_PATH,
                                     componentToInit, propertyValues)) {
            openedRunner = runner;
            runner.runRoutes(false);
            runnerContext = runner.getCamelContext();
            runnerThread = runner.getCamelThread();
            assert runner.isStarted() == shouldStart;
            if (shouldStart) {
                // At this point, the route is running -- let's verify that it works.
                String routeId = runnerContext.getRoutes().get(0).getId();
                if (routeId.contains("Dig")) {
                    if (routeId.contains("Marshaled")) {
                        Map<String, ?> expectedMap =  Map.of("signed", MISSING_VALUE,
                                                        "rcode", MISSING_VALUE,
                                                        "verified", MISSING_VALUE,
                                                        "opt", MISSING_VALUE,
                                                        "tsig", MISSING_VALUE,
                                                        "question", MISSING_VALUE,
                                                        "resolver", MISSING_VALUE,
                                                        "header", MISSING_VALUE);
                        assert verifyOperation.apply(runnerContext, QUERY_MONKEY, expectedMap);
                        assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, expectedMap);
                    } else {
                        assert verifyOperation.apply(runnerContext, QUERY_MONKEY, RESPONSE_MONKEY);
                        assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, RESPONSE_AARDVARK);
                    }
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
    
        assertNotNull("runnerThread is null", runnerThread);
        assert runnerContext != null && runnerContext.isStopped();
        int deathWaitCount = 30;
        // Wait for thread to die off.  Otherwise, spurious errors occasionally.
        while (runnerThread.isAlive() && deathWaitCount > 0) {
            Thread.sleep(500);
            deathWaitCount -= 1;
        }
        // Ensure that the running thread has completed.  Issue of test resource starvation, not product
        assertTrue("ShouldStart: " + shouldStart + ", runnerThread: " + runnerThread +
                           ", ...isAlive: " + runnerThread.isAlive(),
                   !shouldStart || !runnerThread.isAlive());
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
    
    private static class MakeDigCall extends RouteBuilderWithProps {
        public void configure() {
            from("direct:start")
                    .routeId("Simple Dig Call")
                    .to("dns:dig")
                    .to("mock:result");
        }
    }
    
    // The *Marshaled* route builders here verify that we properly discover & load data formats
    // Generally, the routes are not expected to be sensible or useful.
    
    private static class MakeDigCallMarshaled extends RouteBuilderWithProps {
        public void configure() {
            from("direct:start")
                    .routeId("Dig Call Marshaled")
                    .to("dns:dig")
                    .log("INFO")
                    .marshal().json(JsonLibrary.Jackson)
                    .log("INFO")
                    .unmarshal().json(JsonLibrary.Jackson)
                    .log("INFO")
                    .to("mock:result");
        }
    }
    
    private static class MakeDigCallMarshaledGzip extends RouteBuilderWithProps {

        public void configure() {
            from("direct:start")
                    .routeId("Dig Call MarshaledGzip")
                    .to("dns:dig")
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
    
    private static class MakeDigCallMarshaledAvroFailure extends RouteBuilderWithProps {
        
        public void configure() {
            from("direct:start")
                    .routeId("Dig Call Marshaled Avro Failure")
                    .to("dns:dig")
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
                    .to("salesforce:query?rawPayload=true" +
                                "&SObjectQuery=SELECT Id, Subject, OwnerId from Task")
                    .unmarshal().json()
                    .to("log:info")
                    .to("mock:result");
        }
    }
}
