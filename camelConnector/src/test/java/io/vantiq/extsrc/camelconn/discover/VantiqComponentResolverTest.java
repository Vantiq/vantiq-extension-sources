/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.xml.io.XmlRoutesBuilderLoader;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.ivy.util.FileUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perforn unit tests for component resolution
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
        CamelResolver cr = new CamelResolver("testResolutionSimpleCamelRepo", null,
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
    public void testResolutionSimpleCamelCached() throws Exception {
        // Here, we leave the cache alone
        CamelResolver cr = new CamelResolver("testResolutionSimpleCamelRepoCached", null,
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
        CamelResolver cr = new CamelResolver("resFailureResolver", null, null, dest);
        log.debug(cr.identity());
        assertTrue("Identity check:", cr.identity().contains("resFailureResolver"));
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
            CamelResolver nope = new CamelResolver("wontexist", null, null, null);
            fail("Cannot create CamelResover with a null destination");
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
        URI s3Repo = new URI("https://vantiqmaven.s3.amazonaws.com/");
        CamelResolver cr = new CamelResolver("S3RepoResolver", s3Repo, cache, dest);
        Collection<File> resolved = cr.resolve("vantiq.models", "coco", "1.1", "meta");
        assertEquals("Resolved file count: " + resolved.size(), 1, resolved.size());
        File[] files = resolved.toArray(new File[0]);
        assertEquals("File name match", "coco-1.1.meta", files[0].getName());
    }
}
