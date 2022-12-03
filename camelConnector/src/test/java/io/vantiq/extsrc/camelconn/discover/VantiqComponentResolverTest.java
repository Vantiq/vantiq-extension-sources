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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.ivy.util.FileUtil;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
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

/**
 * Perform unit tests for component resolution
 */

// Method order used to check caching for SimpleCamelResolution
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
// @Slf4j
public class VantiqComponentResolverTest extends CamelTestSupport {
    private static final Logger log =  LoggerFactory.getLogger(VantiqComponentResolverTest.class);
    public static final String DEST_PATH = "build/loadedlib";
    public static final String IVY_CACHE_PATH = "build/ivyCache";
    
    File dest;
    File cache = new File(IVY_CACHE_PATH);
    
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
                                               context.getVersion());
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
            Class sfClass = Class.forName("org.apache.camel.component.salesforce.SalesforceComponent", false, ucl);
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
            assert e == null;
        }
    }
    
    @Test
    public void testResolutionSimpleCamelCached() throws Exception {
        // Here, we leave the cache alone
        CamelResolver cr = new CamelResolver(this.getTestMethodName(), (URI) null,
                                             cache, dest);
        Collection<File> resolved = cr.resolve("org.apache.camel", "camel" + "-salesforce", context.getVersion());
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
                                                         context.getVersion());
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
            cr.resolve(null, "somename", "someVersion");
            fail("Null organization should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
    
        try {
            cr.resolve("somegroup", null,
                             "someVersion");
            fail("Null name should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            cr.resolve("somegroup", "somename", null);
            fail("Null revision should not work");
        } catch (IllegalArgumentException iae) {
            assert iae.getMessage().contains("The parameters organization, name, and revision must be non-null");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass().getName() + "::" + e.getMessage());
        }
        
        try {
            cr.resolve("somegroup", "somename", "someVersion");
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
        Collection<File> resolved = cr.resolve("vantiq.models", "coco", "1.1", "meta");
        assertEquals("Resolved file count: " + resolved.size(), 1, resolved.size());
        File[] files = resolved.toArray(new File[0]);
        assertEquals("File name match", "coco-1.1.meta", files[0].getName());
    }
    
    @Test
    public void testStartRouteLoadedComponents() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilder rb = new SimpleExternalRoute();
        assertNotNull("No routebuilder", rb);
        setUseRouteBuilder(false);
        try (CamelRunner runner = new CamelRunner(this.getTestMethodName(), rb, null,
                                                  IVY_CACHE_PATH, DEST_PATH)) {
            runner.runRoutes(false);
        }
    }
    
    // FIXME: Need better test(s) for list of repos.  Specifically, things that load from multiple repos.
    
    @Test
    public void testStartRouteLoadedComponentsMultiRepo() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilder rb = new SimpleExternalRoute();
        assertNotNull("No routebuilder", rb);
        setUseRouteBuilder(false);
        List<URI> repoList = new ArrayList<>();
        repoList.add(new URI("https://vantiqmaven.s3.amazonaws.com/"));
        repoList.add(new URI("https://repo.maven.apache.org/maven2/"));
        try (CamelRunner runner = new CamelRunner(this.getTestMethodName(),rb, repoList, IVY_CACHE_PATH, DEST_PATH)) {
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
        RouteBuilder rb = new MakeDigCall();
        assertNotNull("No routebuilder", rb);
        
        performLoadAndRunTest(rb);
    }
    
    // Test that caching works as expected when thing downloaded, then only cached, and then already in place.
    // In all cases, our internal classloader should load the things needed by the routes in question.
    @Test
    public void testStartRunLoadedComponentsUsingPreviouslyRetrievedComponents() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        
        setUseRouteBuilder(false);
        RouteBuilder rb = new MakeDigCall();
        assertNotNull("No routebuilder", rb);
        
        // First run: cache & lib/dest deleted.  Download all
        performLoadAndRunTest(rb);
        
        // Second run, leave the cache but delete the library.  Should copy from cache
        if (dest.exists()) {
            FileUtil.forceDelete(dest);
        }
        performLoadAndRunTest(rb);
        // Finally, leave everything.  Resolution should do nothing but code will run
        performLoadAndRunTest(rb);
    }
    
    public static final String XML_ROUTE = ""
            + "<routes xmlns=\"http://camel.apache.org/schema/spring\" xmlns:foo=\"http://io.vantiq/foo\">"
            + "   <route id=\"xml-route\">"
            + "      <from uri=\"direct:start\"/>"
            + "      <to uri=\"dns:dig\"/>"
            + "      <to uri=\"mock:result\"/>"
            + "   </route>"
            + "</routes>";
    
    // Note: Unlike the example on the site, the following will fail to start (claiming > 1 consumer for
    // direct:start) if th top-level "- route" line is missing.d
    public static final String YAML_ROUTE =  "\n"
            + "- route:\n"
            + "    id: \"yaml-route\"\n"
            + "    from:\n"
            + "      uri: \"direct:start\"\n"
            + "      steps:\n"
            + "        - to:\n"
            + "            uri: \"dns:dig\"\n"
            + "        - to:\n"
            + "            uri: \"mock:result\"\n";
    
    @Test
    public void testStartRunLoadedComponentsFromXmlText() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
    
        setUseRouteBuilder(false);
    
        performLoadAndRunTest(XML_ROUTE, "xml");
    }
    
    @Test
    public void testStartRunLoadedComponentsFromYamlText() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        setUseRouteBuilder(false);
    
        performLoadAndRunTest(YAML_ROUTE, "yaml");
    }
    
    /**
     * Create a callable that the test method will call.
     *
     * In this case, the callable "sends"
     * message to the route which, in turn, makes the dig call to lookup a monkey.  We verify that the expected
     * results is presented.
     *
     * In this case, our MakeDigCall route uses the (dynamically loaded) dns component to make a dig call.
     * @return TriFunction<CamelContext, String, String, Boolean>
     */
    public static TriFunction<CamelContext, String, String, Boolean> defineVerifyOperation() {
        TriFunction<CamelContext, String, String, Boolean> verifyOperation = (context, query, answer) -> {
            boolean worked = true;
            try {
                if (context.isStopped()) {
                    log.error("At test start, context {} is stopped", context.getName());
                }
                Endpoint res = context.getEndpoint("mock://result");
                assert res instanceof MockEndpoint;
                MockEndpoint resultEndpoint = (MockEndpoint) res;
            
                ProducerTemplate template = context.createProducerTemplate();
                log.debug("Test code using context: {}", context.getName());
            
            
                resultEndpoint.expectedMessageCount(2);
                resultEndpoint.expectedMessagesMatches(new Predicate() {
                    public boolean matches(Exchange exchange) {
                        String str =
                                ((Message) exchange.getIn().getBody()).getSectionArray(Section.ANSWER)[0].rdataToString();
                        log.debug("Matches: Dig lookup got {}", str);
                        return answer.contains(str);
                    }
                });
            
                Map<String, Object> headers = new HashMap<>();
                headers.put("dns.name", query);
                headers.put("dns.type", "TXT");
                if (context.isStopped()) {
                    log.error("Context {} is stopped", context.getName());
                }
                template.sendBodyAndHeaders("direct:start", null, headers);
            
                List<Exchange> exchanges = resultEndpoint.getReceivedExchanges();
                boolean found = false;
                for (Exchange exch: exchanges) {
                    String str =
                            ((Message) exch.getIn().getBody()).getSectionArray(Section.ANSWER)[0].rdataToString();
                    log.debug("Exchange check: Dig lookup got {}", str);
                    if (answer.contains(str)) {
                        found = true;
                    }
                }
                assertTrue("Found expected message in messages", found);
                if (resultEndpoint.getReceivedCounter() > 1) {
                    resultEndpoint.assertIsSatisfied();
                }
            } catch (Exception e) {
                log.error("Trapped exception", e);
                worked = false;
            }
            return worked;
        };
        return verifyOperation;
    }
    
    public void performLoadAndRunTest(String content, String contentType) throws Exception {
        // To do this test, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the dig call to lookup a monkey.  We verify that the expected
        // results is presented.
        //
        // In this case, our MakeDigCall route uses the (dynamically loaded) dns component to make a dig call.
    
        TriFunction<CamelContext, String, String, Boolean> verifyOperation = defineVerifyOperation();
        
        CamelContext runnerContext = null;
        Thread runnerThread = null;
        CamelRunner openedRunner = null;
        
        try (CamelRunner runner =
                     new CamelRunner(this.getTestMethodName(), content, contentType, null, IVY_CACHE_PATH, DEST_PATH)) {
            openedRunner = runner;
            runner.runRoutes(false);
            runnerContext = runner.getCamelContext();
            runnerThread = runner.getCamelThread();
            assert runner.isStarted();
    
            // At this point, the route is running -- let's verify that it works.
    
            assert verifyOperation.apply(runnerContext, QUERY_MONKEY, RESPONSE_MONKEY);
            assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, RESPONSE_AARDVARK);
        }
        
        while (!openedRunner.isStopped()) {
            Thread.sleep(500);
        }
        
        assert runnerThread != null && !runnerThread.isAlive();
        assert runnerContext != null && runnerContext.isStopped();
    }
    
    public void performLoadAndRunTest(RouteBuilder rb) throws Exception {
        // To do this test, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the dig call to lookup a monkey.  We verify that the expected
        // results is presented.
        //
        // In this case, our MakeDigCall route uses the (dynamically loaded) dns component to make a dig call.
        TriFunction<CamelContext, String, String, Boolean> verifyOperation = defineVerifyOperation();
    
        CamelContext runnerContext = null;
        Thread runnerThread = null;
        CamelRunner openedRunner = null;
    
        try (CamelRunner runner =
                     new CamelRunner(this.getTestMethodName(), rb, null, IVY_CACHE_PATH, DEST_PATH)) {
            openedRunner = runner;
            runner.runRoutes(false);
            runnerContext = runner.getCamelContext();
            runnerThread = runner.getCamelThread();
            assert runner.isStarted();
    
            // At this point, the route is running -- let's verify that it works.
    
            assert verifyOperation.apply(runnerContext, QUERY_MONKEY, RESPONSE_MONKEY);
            assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, RESPONSE_AARDVARK);
        }
    
        while (!openedRunner.isStopped()) {
            Thread.sleep(500);
        }
    
        assert runnerThread != null && !runnerThread.isAlive();
        assert runnerContext != null && runnerContext.isStopped();
    }
    
    private static class SimpleExternalRoute extends RouteBuilder  {
        @Override
        public void configure() {
            // From some jetty resource, get the message content & log it.
            
            // The following does nothing, but verifies that things all start up.  All we are really verifying here
            // is that all the classes are loaded so that camel & its associated components are operating.
            
            from("jetty:http://0.0.0.0/myapp/myservice/?sessionSupport=true")
                    .to("log:" + log.getName()  + "level=debug");
        }
    }
    
    private static class MakeDigCall extends RouteBuilder {
    
        public void configure() {
            from("direct:start").to("dns:dig").to("mock:result");
        }
    }
}
