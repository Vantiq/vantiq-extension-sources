/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.ivy.util.FileUtil;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
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
import java.util.function.BiFunction;

/**
 * Perform unit tests for component resolution
 */

// Method order used to check caching for SimpleCamelResolution
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Slf4j
public class VantiqComponentResolverTest extends CamelTestSupport {
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
        CamelRunner runner = new CamelRunner(context, this.getTestMethodName(), null, IVY_CACHE_PATH, DEST_PATH);
        ClassLoader routeLoader = runner.constructClassLoader(rb);
        runner.runRouteWithLoader(rb, routeLoader);
    }
    
    // FIXME: Need test(s) for list of repos
    
    @Test
    public void testStartRouteLoadedComponentsMultiRepo() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
        RouteBuilder rb = new SimpleExternalRoute();
        assertNotNull("No routebuilder", rb);
        setUseRouteBuilder(false);
        List<URI> repoList = new ArrayList<>();
        repoList.add(new URI("https://vantiqmaven.s3.amazonaws.com/"));
        repoList.add(new URI("https://repo.maven.apache.org/maven2/"));
        CamelRunner runner = new CamelRunner(context, this.getTestMethodName(), repoList, IVY_CACHE_PATH, DEST_PATH);
        ClassLoader routeLoader = runner.constructClassLoader(rb);
        runner.runRouteWithLoader(rb, routeLoader);
    }
    
    private static final String QUERY_MONKEY = "monkey.wp.dg.cx";
    private static final String RESPONSE_MONKEY = "\"A Macaque, an old world species of "
            + "monkey native to Southeast Asia|thumb]A monkey is a primate of the "
            + "Haplorrhini suborder and simian infraorder, either an Old World monkey "
            + "or a New World monkey, but excluding apes. There are about 260 known "
            + "living specie\" \"s of monkey. Many are arboreal, although there are "
            + "species that live primarily on the ground, such as baboons... "
            + "http://en.wikipedia.org/wiki/Monkey\"";
    
    private static final String QUERY_AARDVARK = "aardvark.wp.dg.dx";
    private static final String RESPONSE_AARDVARK = "\"The aardvark (Orycteropus afer) is a medium-sized, burrowing, " +
            "nocturnal mammal native to Africa. It is the only living species of the order Tubulidentata, although " +
            "other prehistoric species and genera of Tubulidentata are known. " +
            "http://en.wikipedia.org/wi\" \"ki/Aardvark\"";
    
    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;
    
    @Produce("direct:start")
    protected ProducerTemplate template;
    
    @Test
    public void testStartRunLoadedComponents() throws Exception {
        FileUtil.forceDelete(cache);    // Clear the cache
    
        setUseRouteBuilder(false);
        RouteBuilder rb = new MakeDigCall();
        assertNotNull("No routebuilder", rb);
        
        // At this point, the route is running -- let's verify that it works.
        // To do this, we'll create a callable that the test method will call. In this case, the callable "sends"
        // message to the route which, in turn, makes the dig call to lookup a monkey.  We verify that the expected
        // results is presented.
        //
        // In this case, our MakeDigCall route uses the (dynamically loaded) dns component to make a dig call.
       BiFunction<String, String, Boolean> verifyOperation = (query, answer) -> {
           boolean worked = true;
           try {
               // .reset() leaves mock endpoint in an odd state where it fires index errors on some array internally.
               // Since we invoke this multiple times & we expect a single result each time, we'll just adjust our
               // expectations based on what we've already done.
               resultEndpoint.expectedMessageCount(resultEndpoint.getReceivedCounter() + 1);
               resultEndpoint.expectedMessagesMatches(new Predicate() {
                   public boolean matches(Exchange exchange) {
                       String str =
                               ((Message) exchange.getIn().getBody()).getSectionArray(Section.ANSWER)[0].rdataToString();
                       log.debug("Dig lookup got {}", str);
                       return answer.equals(str);
                   }
               });
               Map<String, Object> headers = new HashMap<>();
               headers.put("dns.name", query);
               headers.put("dns.type", "TXT");
               template.sendBodyAndHeaders(null, headers);
               resultEndpoint.assertIsSatisfied();
            } catch (Exception e) {
                log.error("Trapped exception", e);
                worked = false;
            }
            return worked;
       };
       
       CamelRunner runner = new CamelRunner(context, this.getTestMethodName(), null, IVY_CACHE_PATH, DEST_PATH);
       ClassLoader routeLoader = runner.constructClassLoader(rb);
       runner.runRouteWithLoader(rb, routeLoader, verifyOperation,
                           List.of(new ImmutablePair<>(QUERY_MONKEY, RESPONSE_MONKEY),
                                   new ImmutablePair<>(QUERY_AARDVARK, RESPONSE_AARDVARK)));
    }
    
    private static class SimpleExternalRoute extends RouteBuilder  {
        @Override
        public void configure() {
            // From some AWS S3 resource, check the message content.  If it's an error, log it & send to jmx queue
            // Otherwise, send to the Vantiq app
            
            // The following does nothing, but verifies that things all start up.  All we are really verifying here
            // is that all the classes are loaded so that camel & its associated components are operating.
    
            from("aws2-s3://vanti-maven?prefix=vantiq.models:coco-1.1.meta&useDefaultCredentialsProvider=true")
                    .to("log:" + log.getName()  + "level=debug");
        }
    }
    
    private static class MakeDigCall extends RouteBuilder {
    
        public void configure() {
            from("direct:start").to("dns:dig").to("mock:result");
        }
    }
}
