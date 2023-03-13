/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import static org.junit.Assume.assumeTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.converter.JacksonTypeConvertersLoader;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class VantiqLiveComponentTest extends CamelTestSupport {
    private static final String SRC_IMPL_TYPE = "CAMEL_COMPONENT";
    private static final String IMPL_DEF = "camelComponentImpl.json";
    private static final Integer IMPL_MAX_SIZE = 1000;
    private static final String VANTIQ_SOURCE_IMPL = "system.sourceimpls";
    
    private static final String publishStuffName = System.getProperty("TestCamelPublisher", "publishStuff");
    private static final String queryStuffName = System.getProperty("TestCamelQuerier", "queryStuff");
    
    private static final String testSourceName = System.getProperty("TestCamelSourceName", "camelSource");
    
    private static final String testQuerySourceName = System.getProperty("TestCamelSourceName",
                                                                         "camelSource") + "Query";
    private static final String testRuleName = testSourceName + "Rule";
    private static final String testTypeName = testSourceName + "Type";
    private static final String testMsgPreamble = "published message ";
    
    private static final String vantiqInstallation = System.getProperty("TestVantiqServer");
    public static String vantiqAccessToken = System.getProperty("TestAuthToken");
    
    private static Vantiq vantiq;
    
    private final String routeStartUri = "direct:start";
    private final String routeEndUri = "mock:direct:result";
    private static String vantiqEndpointUri;
    
    private static String vantiqQueryEndpointUri;
    
    @Test
    public void testVantiqProducerLive() throws Exception {
        assumeTrue(vantiqInstallation != null && vantiqAccessToken != null);
        ArrayList<String> msgs =new ArrayList<String>();
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add("{\"hi\": \"mom " + i + "\"}");
        }
        JacksonTypeConvertersLoader jtcl = new JacksonTypeConvertersLoader();
        jtcl.load(context.getTypeConverterRegistry());
        log.trace("Type converter count: {}", context.getTypeConverterRegistry().size());

        for (Object item: msgs) {
            log.debug("Sending msg: " + item);
            sendBody(routeStartUri, item);
        }
    
        // Now, try a similar test sending maps
        ArrayList<Map<String, String>> mapList = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Map<String, String> mp = Map.of("hi", "dad " + i);
            mapList.add(mp);
        }
        
        for (Map item: mapList) {
            sendBody(routeStartUri, item);
        }
    
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger counter = new AtomicInteger(0);
        msgs.forEach( msg -> {
            try {
                ObjectNode node = mapper.readValue(msg, ObjectNode.class);
                node.put("hi", "aki " + counter.getAndIncrement());
                sendBody(routeStartUri, node);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        
        sendBody(routeStartUri, "{\"bye\": \"mom\"}");
        Thread.sleep(5000);
        
        VantiqResponse vr = vantiq.select(testTypeName, null, null, null);
        Map<String, Set<String>> countBySubject = new HashMap<>();
        countBySubject.put("mom", new HashSet<>());
        countBySubject.put("dad", new HashSet<>());
        countBySubject.put("aki", new HashSet<>());
        if (!vr.isSuccess()) {
            for (VantiqError err: vr.getErrors()) {
               log.error("Error: {}", err);
            }
        }
        assert vr.isSuccess();
    
        assert vr.getBody() instanceof List;
        @SuppressWarnings("unchecked")
        List<JsonObject> retVal = (List<JsonObject>) vr.getBody();
        retVal.forEach( row -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> ent = (Map<String, Object>) new Gson().fromJson(row, Map.class);
            log.debug("Got value: {} ({})", ent.get("value"), ent.get("value").getClass().getName());
            if (((Map) ent.get("value")).containsKey("hi")) {
                String val = (String) ((Map) ent.get("value")).get("hi");
                if (val.contains("mom")) {
                    countBySubject.get("mom").add(val);
                } else if (val.contains("dad")) {
                    countBySubject.get("dad").add(val);
                } else if (val.contains("aki")) {
                    countBySubject.get("aki").add(val);
                }
            }
        });
        assertEquals("Mom count", itemCount, countBySubject.get("mom").size());
        assertEquals("Dad count", itemCount, countBySubject.get("dad").size());
        assertEquals("Aki count", itemCount, countBySubject.get("aki").size());
        assert retVal.size() == 3 * itemCount + 1;
    }
    
    @Test
    public void testVantiqConsumerLive() throws Exception {
        log.info("tVCR: {}/{}", vantiqInstallation, vantiqAccessToken);
        assumeTrue(vantiqInstallation != null && vantiqAccessToken != null);
        for (String name: context.getComponentNames()) {
            log.info("Component name: {}", name);
        }
    
        int expectedMsgCount = 10;
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(expectedMsgCount);
    
        Map<String, Object> params = Map.of("messageCount", expectedMsgCount);
        VantiqResponse vr = vantiq.execute(publishStuffName, params);
        assert vr.isSuccess();
        
        mocked.await(5L, TimeUnit.SECONDS);
        assert mocked.getReceivedCounter() == expectedMsgCount;
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<String>();
        exchanges.forEach(exchange -> {
                assert exchange.getIn().getBody() instanceof Map;
                Map msg = exchange.getIn().getBody(Map.class);
                assert msg.containsKey("test");
                assert msg.get("test") instanceof String;
                assert ((String) msg.get("test")).startsWith(testMsgPreamble);
                uniqueMsgs.add(((String) msg.get("test")));
        });
        assert uniqueMsgs.size() == expectedMsgCount;
    }
    
    @Test
    public void testVantiqQueryLive() throws Exception {
        log.info("tVCR: {}/{}", vantiqInstallation, vantiqAccessToken);
        assumeTrue(vantiqInstallation != null && vantiqAccessToken != null);
        for (String name: context.getComponentNames()) {
            log.info("Component name: {}", name);
        }
        
        int expectedMsgCount = 10;
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(expectedMsgCount);
        
        Map<String, Object> params = Map.of("messageCount", expectedMsgCount);
        VantiqResponse vr = vantiq.execute(queryStuffName, params);
        assert vr.isSuccess();
        
        mocked.await(5L, TimeUnit.SECONDS);
        assert mocked.getReceivedCounter() == expectedMsgCount;
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<String>();
        exchanges.forEach(exchange -> {
            assert exchange.getIn().getBody() instanceof Map;
            Map msg = exchange.getIn().getBody(Map.class);
            assert msg.containsKey("test");
            assert msg.get("test") instanceof String;
            assert ((String) msg.get("test")).startsWith(testMsgPreamble);
            uniqueMsgs.add(((String) msg.get("test")));
        });
        assert uniqueMsgs.size() == expectedMsgCount;
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // Used here as well as in the tests so that we don't try & set up routes
                // when we're not even going to run the tests.
                assumeTrue(vantiqInstallation != null && vantiqAccessToken != null);

                from(routeStartUri)
                  .to(vantiqEndpointUri);
                
                from(vantiqEndpointUri)
                        .to(routeEndUri);
                
                from(vantiqQueryEndpointUri)
                        .to(routeEndUri)
                        .setExchangePattern(ExchangePattern.InOut)
                        .setBody(constant("{ \"Response\": \"Message\" }"))
                        .to(vantiqQueryEndpointUri);
            }
        };
    }
    
    @BeforeClass
    public static void setupVantiqEnvironment() throws Exception {
        if (vantiqInstallation != null && vantiqAccessToken != null) {
            URI vuri = new URI(vantiqInstallation);
            String endpointUri = "vantiq://" + vuri.getHost();
            if (vuri.getPort() > 0) {
                endpointUri = endpointUri + ":" + vuri.getPort();
            }
            vantiqEndpointUri = endpointUri +
                    "?sourceName=" + testSourceName +
                    "&accessToken=" + vantiqAccessToken;
            if (("http".equals(vuri.getScheme()) || "ws".equals(vuri.getScheme()))) {
                vantiqEndpointUri = vantiqEndpointUri + "&noSsl=true";
            }
            
            vantiqQueryEndpointUri = endpointUri +
                    "?sourceName=" + testQuerySourceName +
                    "&accessToken=" + vantiqAccessToken;
            if (("http".equals(vuri.getScheme()) || "ws".equals(vuri.getScheme()))) {
                vantiqQueryEndpointUri = vantiqQueryEndpointUri + "&noSsl=true";
            }
            vantiq = new Vantiq(vantiqInstallation);
            vantiq.setAccessToken(vantiqAccessToken);
            assertTrue("Vantiq Auth'd", vantiq.isAuthenticated());
            createSourceImpl();
            createSource();
            createQuerySource();
            createType();
            setupPublishProcedure();
            setupQueryProcedure();
            setupReceiverRule();
        }
    }
    
    @AfterClass
    public static void teardownVantiqEnvironment() {
        if (vantiqInstallation != null && vantiqAccessToken != null) {
            deletePublishProcedure();
            deleteQueryProcedure();
            deleteReceiverRule();
            deleteType();
            deleteSource();
            deleteQuerySource();
            deleteSourceImpl();
            vantiq = null;
        }
    }
    
    public static void createType() {
        Map<String, Object> typeDef = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("value", Map.of("type", "Object", "required", false));
        typeDef.put("properties", properties);
        typeDef.put("name", testTypeName);
        VantiqResponse vr = vantiq.insert("system.types", typeDef);
        if (!vr.isSuccess()) {
            for (VantiqError err: vr.getErrors()) {
                System.out.println("Error: " + err.getMessage());
            }
        }
    }
    
    public static void deleteType() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testTypeName);
        vantiq.delete("system.types", where);
    }
    
    public static void setupPublishProcedure() {
        String procedure =
                "PROCEDURE " + publishStuffName +  "(messageCount Integer)\n"
                        + "for (i in range(0, messageCount)) {\n"
                        + "    PUBLISH {test: \"" + testMsgPreamble + "\" + i} to SOURCE " + testSourceName + "\n"
                        + "    log.info(\"Published message to source: {}\", [\"" + testSourceName + "\"])"
                        + "}";
       
        VantiqResponse vr = vantiq.insert("system.procedures", procedure);
        assert vr.isSuccess();
    }
    
    public static void setupQueryProcedure() {
        String procedure =
                "PROCEDURE " + queryStuffName +  "(messageCount Integer)\n"
                        + "for (i in range(0, messageCount)) {\n"
                        + "    var resp = "
                        +           "SELECT ONE * from SOURCE " + testQuerySourceName + " WITH test: \"" + testMsgPreamble
                        + "               \" + i, iteration: i\n"
                        + "    log.info(\"Query message to source: {}\", [\"" + testQuerySourceName + "\"])\n"
                        + "    if (!resp.containsKey(\"Response\")) {\n"
                        + "        exception(\"missing.required.key\", \"missing required key\")\n"
                        + "    }\n"
                        + "}\n";
        
        VantiqResponse vr = vantiq.insert("system.procedures", procedure);
        assert vr.isSuccess();
    }
    
    public static void deletePublishProcedure() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", publishStuffName);
        VantiqResponse response = vantiq.delete("system.procedures", where);
        assert response.isSuccess();
    }
    
    public static void deleteQueryProcedure() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", queryStuffName);
        VantiqResponse response = vantiq.delete("system.procedures", where);
        assert response.isSuccess();
    }
    public static void setupReceiverRule() {
        String rule =
                "RULE " + testRuleName + "\n"
                        + "when EVENT OCCURS on \"/sources/" + testSourceName + "\" as message\n"
                        + "\n"
                        + "insert " + testTypeName + "(value: message.value)";
        
        VantiqResponse vr = vantiq.insert("system.rules", rule);
        assert vr.isSuccess();
    }
    
    public static void deleteReceiverRule() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.delete("system.rules", where);
        if (!response.isSuccess()) {
            for (VantiqError err: response.getErrors()) {
                System.out.println("Error: " + err.getMessage());
            }
        }
        assert response.isSuccess();
    }
    
    private static boolean createdImpl = false;

    @SuppressWarnings("unchecked")
    protected static void createSourceImpl() throws Exception {
        VantiqResponse resp = vantiq.selectOne(VANTIQ_SOURCE_IMPL, SRC_IMPL_TYPE);
        if (!resp.isSuccess()) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            byte[] implDef = new byte[IMPL_MAX_SIZE];
            try (InputStream is = loader.getResourceAsStream(IMPL_DEF))
            {
                assert is != null;
                int implSize = is.read(implDef);
                assert implSize > 0;
                assert implSize < IMPL_MAX_SIZE;
            }
            
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> implMap = new LinkedHashMap<>();
            implMap = mapper.readValue(implDef, implMap.getClass());
            resp = vantiq.insert(VANTIQ_SOURCE_IMPL, implMap);
            if (!resp.isSuccess()) {
                System.out.println("Got errors: " +  resp);
            }
            assert resp.isSuccess();
            
            Map<String, String> where = new LinkedHashMap<>();
            where.put("name", SRC_IMPL_TYPE);
            VantiqResponse implResp = vantiq.select(VANTIQ_SOURCE_IMPL, null, where, null);
            assertFalse("Errors from fetching source impl", implResp.hasErrors());
            @SuppressWarnings({"rawtypes"})
            ArrayList responseBody = (ArrayList) implResp.getBody();
            assertEquals("Missing sourceImpl -- expected a count of 1", 1, responseBody.size());
            createdImpl = true;
        }
    }
    
    protected static void deleteSourceImpl() {
        if (createdImpl) {
            vantiq.deleteOne(VANTIQ_SOURCE_IMPL, SRC_IMPL_TYPE);
        }
        
        VantiqResponse resp = vantiq.selectOne(VANTIQ_SOURCE_IMPL, SRC_IMPL_TYPE);
        if (resp.hasErrors()) {
            List<VantiqError> errors = resp.getErrors();
            assert errors != null;
            assert errors.size() > 0;
            if (errors.size() != 1 || !"io.vantiq.resource.not.found".equals(errors.get(0).getCode())) {
                fail("Error deleting source impl" + resp.getErrors());
            }
        } else if (createdImpl) {
            fail(SRC_IMPL_TYPE + " source impl found after deletion.");
        }
    }
    
    public static void createSource() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", SRC_IMPL_TYPE);
        VantiqResponse implResp = vantiq.select(VANTIQ_SOURCE_IMPL, null, where, null);
        assertFalse("Errors from fetching source impl", implResp.hasErrors());
        ArrayList responseBody = (ArrayList) implResp.getBody();
        assertEquals("Missing sourceimpl -- expected a count of 1", 1, responseBody.size());
    
        Map<String, Object> sourceDef = new LinkedHashMap<>();
        // Setting up the source definition
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", SRC_IMPL_TYPE);
        sourceDef.put("active", "true");
        sourceDef.put("config", new LinkedHashMap<String, Object>());
    
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        assert insertResponse.isSuccess();
    }
    
    public static void createQuerySource() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", SRC_IMPL_TYPE);
        VantiqResponse implResp = vantiq.select(VANTIQ_SOURCE_IMPL, null, where, null);
        assertFalse("Errors from fetching source impl", implResp.hasErrors());
        ArrayList responseBody = (ArrayList) implResp.getBody();
        assertEquals("Missing sourceimpl -- expected a count of 1", 1, responseBody.size());
        
        Map<String, Object> sourceDef = new LinkedHashMap<>();
        // Setting up the source definition
        sourceDef.put("name", testQuerySourceName);
        sourceDef.put("type", SRC_IMPL_TYPE);
        sourceDef.put("active", "true");
        sourceDef.put("config", new LinkedHashMap<String, Object>());
        
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        assert insertResponse.isSuccess();
    }
    
    public static void deleteSource() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.delete("system.sources", where);
        assert response.isSuccess();
    }
    
    public static void deleteQuerySource() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testQuerySourceName);
        VantiqResponse response = vantiq.delete("system.sources", where);
        assert response.isSuccess();
    }
}
