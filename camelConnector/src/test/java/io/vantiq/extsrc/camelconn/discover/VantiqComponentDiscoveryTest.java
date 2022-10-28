/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.dsl.xml.io.XmlRoutesBuilderLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perforn unit tests for component discovery
 */
public class VantiqComponentDiscoveryTest extends CamelTestSupport {
    
    private final String routeStartUri = "direct:start";
    private final String routeEndUri = "mock:direct:result";
    private static String vantiqEndpointUri;
    
    @BeforeClass
    public static void setup() throws Exception {
        URI vuri = new URI("http://localhost:8080");
        String endpointUri = "vantiq://" + vuri.getHost();
        if (vuri.getPort() > 0) {
            endpointUri = endpointUri + ":" + vuri.getPort();
        }
        vantiqEndpointUri = endpointUri +
                "?sourceName=" + "fauxSource" +
                "&accessToken=" + "fauxAccessToken";
        if (("http".equals(vuri.getScheme()) || "ws".equals(vuri.getScheme()))) {
            vantiqEndpointUri = vantiqEndpointUri + "&noSsl=true";
        }
    }
    
    /**
     * Verify that our transcription from code/doc of what's built into Camel core is correct.
     */
    @Test
    public void testTranscription() {
        assert EnumeratingComponentResolver.CORE_COMPONENTS_SCHEMES.size() == 26;
        ComponentResolver cr = context.adapt(ExtendedCamelContext.class).getComponentResolver();
        assertTrue("Non-default component resolver: " + cr.getClass().getName(),
                cr instanceof DefaultComponentResolver);
        List<String> failureList = new ArrayList<>();
        EnumeratingComponentResolver.CORE_COMPONENTS_SCHEMES.forEach( comp -> {
            if (EnumeratingComponentResolver.NONLOADABLE_CORE_COMPONENT_SCHEMES.contains(comp)) {
                log.debug("Skipping component scheme '{}' since it is handled internally", comp);
            } else {
                log.debug("Resolving component with scheme: {}", comp);
                Object result;
                try {
                    result = cr.resolveComponent(comp, context);
                    if (result == null) {
                        failureList.add(comp + " -- no resolution");
                    }
                } catch (Exception e) {
                    failureList.add("comp" + " -- via exception: " + e.getClass().getName() + ":" + e.getMessage());
                }
            }
        });
        failureList.forEach(failure -> log.error("Failed to load component: {}", failure));
        assertEquals("Failed to load components", 0, failureList.size());
    }
    
    /**
     * Verify that we can discover the set of components in a simple set of routes.
     * @throws Exception as found
     */
    @Test
    public void testBasicDiscovery() throws Exception {
        RouteBuilder rb = new TwoSimpleRoutes();
        performDiscoveryTest(rb);
    }
    
    @Test
    public void testMultiEndpointDiscovery() throws Exception {
        RouteBuilder rb = new MultiendedRoutes();
        performDiscoveryTest(rb);
    }
    
    @Test
    public void testComplexUrlRoutesDiscovery() throws Exception {
        RouteBuilder rb = new ComplexUrlRoutes();
        performDiscoveryTest(rb);
    }
    
    @Test
    public void testXmlRouteDiscovery() throws Exception {
        RouteBuilder rb = new SimpleXmlRoutes();
        performDiscoveryTest(rb);
    }
    
    @Test
    public void testComplexXmlRouteDiscovery() throws Exception {
        RouteBuilder rb = new ComplexXmlRoutes();
        performDiscoveryTest(rb);
    }
    
    void performDiscoveryTest(RouteBuilder rb) throws Exception {
        assert rb instanceof TestExpectations;
        setUseRouteBuilder(false);
        CamelDiscovery discoverer = new CamelDiscovery();
        Map<String, Set<String>> discResults = discoverer.performComponentDiscovery(rb);
        
        List<String> expectedCTL = ((TestExpectations) rb).getExpectedComponentsToLoad();
        List<String> expectedSysComp = ((TestExpectations) rb).getExpectedSystemComponents();
        discResults.get(CamelDiscovery.COMPONENTS_TO_LOAD).forEach(comp -> {
            log.debug("    ---> {}", comp);
            assertTrue("Unexpected component to load: " + comp, expectedCTL.contains(comp));
        });
    
        discResults.get(CamelDiscovery.SYSTEM_COMPONENTS).forEach(comp -> {
            log.debug("    ---> {}", comp);
            assertTrue("Unexpected system component: " + comp, expectedSysComp.contains(comp));
    
        });
        assertEquals("Enumerated Components:",
                     expectedCTL.size(),  discResults.get(CamelDiscovery.COMPONENTS_TO_LOAD).size());
        assertEquals("System Components:", expectedSysComp.size(),
                     discResults.get(CamelDiscovery.SYSTEM_COMPONENTS).size());
    }
    
    interface TestExpectations {
        List<String> getExpectedComponentsToLoad();
        List<String> getExpectedSystemComponents();
    }
    private class TwoSimpleRoutes extends RouteBuilder implements TestExpectations {

        @Override
        public void configure() {
            from(routeStartUri)
                    .to(vantiqEndpointUri);
    
            from(vantiqEndpointUri)
                    .to(routeEndUri);
        }
        public List<String> getExpectedComponentsToLoad() {
            return List.of("vantiq");
        }
    
        public List<String> getExpectedSystemComponents() {
            return List.of("mock", "direct");
        }
    }
    
    private class MultiendedRoutes extends RouteBuilder implements TestExpectations {
        
        @Override
        public void configure() {
            from(routeStartUri)
                    .to("log:debug")
                    .to("mock:foobar")
                    .to("direct-vm:something");
            
            
            from(vantiqEndpointUri)
                    .to(routeEndUri);
        }
        public List<String> getExpectedComponentsToLoad() {
            return List.of("vantiq");
        }
        
        public List<String> getExpectedSystemComponents() {
            return List.of("direct-vm", "direct", "mock", "log");
        }
    }
    
    private static class ComplexUrlRoutes extends RouteBuilder implements TestExpectations {
        
        @Override
        public void configure() {
            // From some atomix resource, check the message content.  If it's an error, log it & send to jmx queue
            // Otherwise, send to the Vantiq app
            from("atomix:someAtomixResource?atomix=someAtomixInstance&broadcastType=ALL&channelName=foo" +
                            "&transport=io.atomix.catalyst.transport.netty.NettyTransport&bridgeErrorHandler=true" +
                            "&synchronous=true")
                .choice()
                    .when(xpath("/record/error"))
                        .to("log:error")
                        .to("jmx:queue:errorNotification?acceptMessagesWhileStopping=false" +
                            "&acknowledgementModeName=CLIENT_ACKNOWLEDGE&cacheLevelName=NONE" +
                            "&deliveryMode=PERSISTENT")
                    .otherwise()
                        .to(vantiqEndpointUri);
        }
        @Override
        public List<String> getExpectedComponentsToLoad() {
            return List.of("atomix", "jmx", "vantiq");
        }
        
        @Override
        public List<String> getExpectedSystemComponents() {
            return List.of("log");
        }
    }
    
    private static class XmlRouteBuilder {
        
        RouteBuilder builder;
        XmlRouteBuilder(String content) throws Exception {
    
            Resource resource = ResourceHelper.fromString("in-memory.xml", content);
            try (XmlRoutesBuilderLoader xmlr = new XmlRoutesBuilderLoader()) {
                builder = (RouteBuilder) xmlr.loadRoutesBuilder(resource);
            }
        }
        
        public RouteBuilder getRouteBuilder() {
            return builder;
        }
    }
    private static class SimpleXmlRoutes extends RouteBuilder implements TestExpectations {
    
        @Override
        public List<String> getExpectedComponentsToLoad() {
            return Collections.emptyList();
        }
    
        @Override
        public List<String> getExpectedSystemComponents() {
            return List.of("direct");
        }
    
        @Override
        public void configure() throws Exception {
            String content = ""
                    + "<routes xmlns=\"http://camel.apache.org/schema/spring\" xmlns:foo=\"http://io.vantiq/foo\">"
                    + "   <route id=\"xpath-route\">"
                    + "      <from uri=\"direct:test\"/>"
                    + "      <setBody>"
                    + "         <xpath resultType=\"java.lang.String\">"
                    + "            /foo:orders/order[1]/country/text()"
                    + "         </xpath>"
                    + "      </setBody>"
                    + "   </route>"
                    + "</routes>";
            RouteBuilder rb = new XmlRouteBuilder(content).getRouteBuilder();
            rb.configure();
            this.setRouteCollection(rb.getRouteCollection());
        }
    }
    
    private static class ComplexXmlRoutes extends RouteBuilder implements TestExpectations {
    
        @Override
        public List<String> getExpectedComponentsToLoad() {
            return List.of("salesforce");
        }
        
        @Override
        public List<String> getExpectedSystemComponents() {
            return List.of("direct-vm");
        }
        
        @Override
        public void configure() throws Exception {
            String content = ""
                    + "    <routes>"
                    + "        <route id=\"salesforce1\">"
                    + "            <from uri=\"salesforce:CamelTestTopic?notifyForFields=ALL"
                    + "&amp;notifyForOperations=ALL&amp;sObjectName=Merchandise__c&amp;updateTopic=true"
                    + "&amp;sObjectQuery=SELECT Id, Name FROM Merchandise__c\"/>"
                    + "            <to uri=\"salesforce:createSObject?sObjectName=TestEvent__e\"/>"
                    + "            <to uri=\"direct-vm:salesforce-notifier\"/>"
                    + "        </route>"
                    + "    </routes>";
            RouteBuilder rb = new XmlRouteBuilder(content).getRouteBuilder();
            rb.configure();
            this.setRouteCollection(rb.getRouteCollection());
        }
    }
}
