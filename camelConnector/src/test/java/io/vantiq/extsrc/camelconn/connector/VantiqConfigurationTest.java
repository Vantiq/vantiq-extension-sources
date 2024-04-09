/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.connector;

import static io.vantiq.extjsdk.ExtensionServiceMessage.OP_CONFIGURE_EXTENSION;
import static io.vantiq.extsrc.camel.VantiqEndpoint.ACCESS_TOKEN_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.HEADER_DUPLICATION_BEAN_NAME;
import static io.vantiq.extsrc.camel.VantiqEndpoint.SOURCE_NAME_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADERS_PROPERTY;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADER_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_MESSAGE_PROPERTY;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.APP_NAME;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_APP;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_CONFIG;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_CACHE;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_LIB;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_PROPERTIES;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.DISCOVERED_RAW;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.GENERAL;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.HEADER_BEAN_NAME;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.HEADER_DUPLICATION;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.NO_RAW_REQUEST;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.PROPERTY_VALUES;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_END;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_END_ALT;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_REQUIRED;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_START;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_START_ALT;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.ROUTES_FORMAT;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.ROUTES_LIST;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.MISSING_VALUE;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.QUERY_WIKIPEDIA;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.QUERY_VANTIQ;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.RESPONSE_WIKIPEDIA;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.RESPONSE_VANTIQ;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.XML_ROUTE;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.defineVerifyOperation;
import static org.junit.Assume.assumeTrue;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extsrc.camel.FauxVantiqComponent;
import io.vantiq.extsrc.camel.FauxVantiqEndpoint;
import io.vantiq.extsrc.camelconn.discover.CamelRunner;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.ivy.util.FileUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Slf4j
public class VantiqConfigurationTest extends CamelTestSupport {
    @Rule
    public TestName name = new TestName();
    public String testName = this.getClass().getSimpleName();
    
    private static final String BUILD_DIR = System.getProperty("BUILD_DIR", "build");
    private static final String CAMEL_CONN_BASE_DIR = "camelConnBase";
    private static final String CAMEL_BASE_PATH = BUILD_DIR + File.separator + CAMEL_CONN_BASE_DIR + File.separator;
    private static final String CACHE_DIR = CAMEL_BASE_PATH + "cacheDir";
    private static final String LOADED_LIBRARIES = CAMEL_BASE_PATH + "loadedLib";
    public static final File cache = new File(CACHE_DIR);
    
    // Interface to use in declaration.  We'll pass lambda's in to do the actual verification work
    interface Verifier {
        void doVerify(CamelContext runnerContext);
    }
    
    @BeforeClass
    public static void setup() {
        // Clean out cache to avoid spurious warnings about unknown resolvers.  These come about because our app name
        // (after which we name our resolvers) vary test by test or test class by test class.
        FileUtil.forceDelete(cache);    // Clear the cache
    }
    void performConfigTest(String appName, String route, String routeFormat,
                           List<Map<String, Object>> compInitProps, Properties propertyValues, Verifier vfy) {
        performConfigTest(appName, route, routeFormat, compInitProps, propertyValues, vfy, null,
                          null, null, null);
    }
    
    void performConfigTest(String appName, String route, String routeFormat,
                           List<Map<String, Object>> compInitProps, Properties propertyValues, Verifier vfy,
                           List<String> rawReq) {
        performConfigTest(appName, route, routeFormat, compInitProps, propertyValues, vfy, rawReq,
                          null, null, null);
    }
    
    void performConfigTest(String appName, String route, String routeFormat,
                           List<Map<String, Object>> compInitProps, Properties propertyValues, Verifier vfy,
                           List<String> rawReq, String headerBeanName, Map<String, String> headerDuplications,
                           Map<String, Component> overrideComponents) {
        Map<String, Object> simpleConfig = new HashMap<>();
        Map<String, Object> camelConfig = new HashMap<>();
        simpleConfig.put(CAMEL_CONFIG, camelConfig);
        Map<String, Object> camelAppConfig = new HashMap<>();
        camelConfig.put(CAMEL_APP, camelAppConfig);
        Map<String, String> generalConfig = new HashMap<>();
        camelConfig.put(GENERAL, generalConfig);

        generalConfig.put(COMPONENT_CACHE, CACHE_DIR);
        generalConfig.put(COMPONENT_LIB, LOADED_LIBRARIES);

        camelAppConfig.put(ROUTES_LIST, route);
        camelAppConfig.put(ROUTES_FORMAT, routeFormat);
        camelAppConfig.put(APP_NAME, appName);
        if (compInitProps != null) {
            camelAppConfig.put(COMPONENT_PROPERTIES, compInitProps);
        }
        if (propertyValues != null) {
            camelAppConfig.put(PROPERTY_VALUES, propertyValues);
        }
        if (rawReq != null) {
            Map<String, Object> rawReqMap = new HashMap<>();
            
            rawReqMap.put(DISCOVERED_RAW, rawReq);
            camelAppConfig.put(RAW_REQUIRED, rawReqMap);
        }
        
        if (headerBeanName != null) {
            camelAppConfig.put(HEADER_BEAN_NAME, headerBeanName);
            camelAppConfig.put(HEADER_DUPLICATION, headerDuplications);
        }

        String fauxVantiqUrl = "http://someVantiqServer";
        ExtensionServiceMessage esm = new ExtensionServiceMessage(fauxVantiqUrl);
        esm.op = OP_CONFIGURE_EXTENSION;
        esm.object = simpleConfig;

        CamelCore core = new CamelCore("testComponentInitConfiguration",
                                       "someAccessToken", fauxVantiqUrl);
        CamelHandleConfiguration handler;
        
        if (overrideComponents == null) {
            handler = new CamelHandleConfiguration(core);
        } else {
            handler = new HandleConfigurationWithOverrides(core, overrideComponents);
        }

        handler.handleMessage(esm);
        assertTrue("handler completed", handler.isComplete());
        assertNotNull("Camel Runner", handler.getCurrentCamelRunner());
        assertTrue("Runner started", handler.getCurrentCamelRunner().isStarted());
        assertNotNull("Runner's Camel Context", handler.getCurrentCamelRunner().getCamelContext());
        assertNotNull("Runner's thread", handler.getCurrentCamelRunner().getCamelThread());
        
        // Assuming things worked, now we have a camel app running.  We'll run our test against it & verify
        
        CamelContext runnerContext = handler.getCurrentCamelRunner().getCamelContext();
        assertTrue("Context Running", runnerContext.isStarted());

        try {
            vfy.doVerify(runnerContext);
        } finally {
            handler.getCurrentCamelRunner().close();
        }
    
        handler.getCurrentCamelRunner().close();
        try {
            handler.getCurrentCamelRunner().getCamelThread().join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException ie) {
            fail("Trapped Interrupted Exception");
        }
    }
    
    public static final String ROUTE_NOT_USED = ""
            + "- route:\n"
            + "    id: \"YAML route just there for existence\"\n"
            + "    from:\n"
            + "      uri: \"direct:start\"\n"
            + "      steps:\n"
            + "        - to:\n"
            + "            uri: \"log:simplelog\"\n";
    
    @Test
    public void testRawNoRaw() {
        Map<String, Object> propValues = Map.of("prop1", "prop1Value",
                                                "prop2", "prop2Value",
                                                "prop3", "prop3Value");
        Properties props = new Properties(propValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(propValues);
    
        performConfigTest(testName, ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext) -> {
                              PropertiesComponent pc = runnerContext.getPropertiesComponent();
                              propValues.forEach( (name, val) -> {
                                  Optional<String> os = pc.resolveProperty("{{" + name + "}}");
                                  assert os.isPresent();
                                  String s = os.get();
                                  assert s.equals(val);
                              });
                          });
    }
    
    @Test
    public void testRawAlready() {
        Map<String, Object> propValues = Map.of("prop1", "prop1Value",
                                                "prop2", "RAW{prop2Value}",
                                                "prop3", "RAW(prop3Value)",
                                                "prop4", "RAW{prop4}value",
                                                "prop5", "RAW{prop5value)");
        Properties props = new Properties(propValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(propValues);
        List<String> raws = List.of("prop1", "prop2", "prop3", "prop4");
        List<String> unchanged = List.of("prop2", "prop3", "prop5");
    
        performConfigTest(testName, ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext) -> {
                              PropertiesComponent pc = runnerContext.getPropertiesComponent();
                              propValues.forEach( (name, val) -> {
                                  Optional<String> os = pc.resolveProperty("{{" + name + "}}");
                                  assert os.isPresent();
                                  String s = os.get();
                                  if (unchanged.contains(name)) {
                                      assert s.equals(val);
                                  } else {
                                      assert s.equals(RAW_START + val + RAW_END);
                                  }
                              });
                          }, raws);
    }
    
    @Test
    public void testRawRequired() {
        Map<String, Object> propValues = Map.of("prop1", "prop1Value",
                                                "prop2", "prop2Value",
                                                "prop3", "prop3Value");
        Properties props = new Properties(propValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(propValues);
        List<String> raws = List.of("prop1", "prop3");
    
        performConfigTest(testName, ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext) -> {
                              PropertiesComponent pc = runnerContext.getPropertiesComponent();
                              propValues.forEach( (name, val) -> {
                                  Optional<String> os = pc.resolveProperty("{{" + name + "}}");
                                  assert os.isPresent();
                                  String s = os.get();
                                  if (raws.contains(name)) {
                                      assert s.equals(
                                          CamelHandleConfiguration.RAW_START + val + CamelHandleConfiguration.RAW_END);
                                  } else {
                                      assert s.equals(val);
                                  }
                              });
                         }, raws);
    }
    
    @Test
    public void testRawAltRequired() {
        Map<String, Object> propValues = Map.of("prop1", "prop1)Value",
                                                "prop2", "prop2Value",
                                                "prop3", "prop3Value");
        Properties props = new Properties(propValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(propValues);
        List<String> raws = List.of("prop1", "prop3");
        
        performConfigTest(testName, ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext) -> {
                              PropertiesComponent pc = runnerContext.getPropertiesComponent();
                              propValues.forEach( (name, val) -> {
                                  Optional<String> os = pc.resolveProperty("{{" + name + "}}");
                                  assert os.isPresent();
                                  String s = os.get();
                                  if (name.equals("prop1")) {
                                      assert s.equals(RAW_START_ALT + val + RAW_END_ALT);
                                  } else if (name.equals("prop3")) {
                                      assert s.equals(RAW_START + val + RAW_END);
                                  } else {
                                      assert s.equals(val);
                                  }
                              });
                          }, raws);
    }
    @Test
    public void testRawSuppressed() {
        Map<String, Object> propValues = Map.of("prop1", "prop1Value",
                                                "prop2", NO_RAW_REQUEST + "prop2Value",
                                                "prop3", "prop3Value");
        Properties props = new Properties(propValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(propValues);
        List<String> raws = List.of("prop2");
    
        performConfigTest(testName, ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext) -> {
                              PropertiesComponent pc = runnerContext.getPropertiesComponent();
                              propValues.forEach( (name, val) -> {
                                  Optional<String> os = pc.resolveProperty("{{" + name + "}}");
                                  assert os.isPresent();
                                  String s = os.get();
                                  if (raws.contains(name)) {
                                      String newVal = ((String) propValues.get(name))
                                              .substring(NO_RAW_REQUEST.length());
                                      assert newVal.equals(s);
                                  } else {
                                      assert s.equals(val);
                                  }
                              });
                          }, raws);
    }
    
    @Test
    public void testCannotWrap() {
        Map<String, Object> propValues = Map.of("prop1", "prop1Value",
                                                "prop2", "prop2Value",
                                                "prop3", "prop3}Value)Value");
        Properties props = new Properties(propValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(propValues);
        List<String> raws = List.of("prop1", "prop3");
    
        performConfigTest(testName, ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext) -> {
                              PropertiesComponent pc = runnerContext.getPropertiesComponent();
                              propValues.forEach( (name, val) -> {
                                  Optional<String> os = pc.resolveProperty("{{" + name + "}}");
                                  assert os.isPresent();
                                  String s = os.get();
                                  if (name.equals("prop1")) {
                                      assert s.equals(RAW_START + val + RAW_END);
                                  } else {
                                      assert s.equals(val);
                                  }
                              });
                          }, raws);
    
    }
    @Test
    public void testSimpleConfiguration() {
        performConfigTest(testName, XML_ROUTE, "xml",
                          null, null,
                          (CamelContext runnerContext) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                              assert verifyOperation.apply(runnerContext, QUERY_VANTIQ, RESPONSE_VANTIQ);
                              assert verifyOperation.apply(runnerContext, QUERY_WIKIPEDIA, RESPONSE_WIKIPEDIA);
                          });
    }
    
    @Test
    public void testRouteWithChoice() {
        Properties fhirProps = new Properties();
        fhirProps.putAll(FHIR_PROPERTIES);
        performConfigTest(testName, FHIR_SINK_ROUTE_YAML, "yaml",
                          null,
                          fhirProps,
                          (CamelContext runnerContext) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                              assert verifyOperation.apply(runnerContext, "smith", "smith");
                              assert verifyOperation.apply(runnerContext, "smithsonian", "\"total\"; 0");
                              assert verifyOperation.apply(runnerContext, "jackson", "jackson");
                          });
    }
    
    @Test
    public void testComponentInitConfiguration() {
        assumeTrue(!sfLoginUrl.equals(MISSING_VALUE) && !sfClientId.equals(MISSING_VALUE) &&
                           !sfClientSecret.equals(MISSING_VALUE) && !sfRefreshToken.equals(MISSING_VALUE));
        performConfigTest(testName, SALESFORCETASKS_YAML, "yaml",
                          getComponentsToInit(), null,
                          (CamelContext runnerContext) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                            assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                            assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                        });
    }
    
    @Test
    public void testComponentInitConfigurationWithPropertyValues() {
        assumeTrue(!sfLoginUrl.equals(MISSING_VALUE) && !sfClientId.equals(MISSING_VALUE) &&
                           !sfClientSecret.equals(MISSING_VALUE) && !sfRefreshToken.equals(MISSING_VALUE));
    
        Properties props = new Properties(pValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(pValues);
        performConfigTest(testName, PARAMETERIZED_SALESFORCE_ROUTE, "yaml",
                          getComponentsToInit(), props,
                          (CamelContext runnerContext) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                              assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                              assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                          });
    }
    
    @Test
    public void testHdrDupConfiguration() {
        String dupBeanName = testName + System.currentTimeMillis();
        Map<String, String> hdrDups = Map.of("hdr1", "otherHdr1",
                                             "hdr2", "otherHdr2",
                                             "hdr3", "otherHdr3");
        // Here, we want these tests to run sans a running Vantiq server, so we'll use the fake Vantiq component of
        // our component (and, thus, endpoint).  This will allow us to see the results without real network
        // interactions.
        Map<String, Component> oRides = Map.of("vantiq", new FauxVantiqComponent());
        String vantiqEpUri = constructVantiqUri("notReal", dupBeanName);
        String vantiqRoute = constructVantiqRoute(vantiqEpUri);
        performConfigTest(testName, vantiqRoute, "yaml",
                          null, null,
                          (CamelContext runnerContext) -> {
                              TriFunction<CamelContext, Map<String, Object>, Map<String, Object>, Boolean> verifyOperation =
                                      defineVerifyHeaders(1, "direct:vantiqStart",
                                                          vantiqEpUri);
                              for (int i = 0; i < 10; i++) {
                                  String h1Val = "h1Value-" + i;
                                  String h2Val = "h2Value-" + i;
                                  String h3Val = "h3Value-" + i;
                                  assert verifyOperation.apply(runnerContext,
                                                               Map.of("headers", Map.of("hdr1", h1Val,
                                                                                        "hdr2", h2Val,
                                                                                        "hdr3", h3Val),
                                                                      "message", Map.of("bodyPart", "arm")),
                                                               Map.of("hdr1", h1Val,
                                                                      "hdr2", h2Val,
                                                                      "hdr3", h3Val,
                                                                      "otherHdr1", h1Val,
                                                                      "otherHdr2", h2Val,
                                                                      "otherHdr3", h3Val));
                              }
                          }, null, dupBeanName, hdrDups, oRides);
    }
    
    /**
     * Create a callable that the test method will call.
     *
     * In this case, the callable "sends"
     * message to the route which, in turn, makes a call to return some data.  We verify that the expected
     * results are presented.
     *
     * In this case, our route uses the dynamically loaded component to make a call.
     * @return TriFunction<CamelContext, Map<String, Object>, Map<String, Object>, Boolean>
     */
    public TriFunction<CamelContext, Map<String, Object>, Map<String, Object>, Boolean> defineVerifyHeaders(int msgCount, String startEp,
                                                                                    String endEp) {
        TriFunction<CamelContext, Map<String, Object>, Map<String, Object>, Boolean> verifyOperation =
                (context, query, answerMap) -> {
            boolean worked = true;
            String routeId = context.getRoutes().get(0).getId();
            ProducerTemplate template = null;
            try {
                assertFalse("At test start, context " + context.getName() + " is stopped", context.isStopped());
    
                template = context.createProducerTemplate();
    
                Endpoint res = context.getEndpoint(endEp);
                assert res instanceof FauxVantiqEndpoint;
                FauxVantiqEndpoint resultEndpoint = (FauxVantiqEndpoint) res;
                FalseClient fc = resultEndpoint.myClient;
    
                //noinspection unchecked
                Map<String, Object> hdrsToSend = (Map<String, Object>) query.get(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                assert hdrsToSend != null;
                log.debug("Sending message to {}", startEp);
                template.sendBodyAndHeaders(startEp, query.get(STRUCTURED_MESSAGE_MESSAGE_PROPERTY), hdrsToSend);
                Map lastMsg = fc.getLastMessageAsMap();
                assert lastMsg.containsKey("op");
                assert "notification".equals(lastMsg.get("op"));
                assert lastMsg.containsKey("sourceName");
                assert "someSource".equals(lastMsg.get("sourceName"));
                assert lastMsg.containsKey("object");
                assert lastMsg.get("object") instanceof Map;
                //noinspection unchecked
                Map<String, Object> msg = (Map<String, Object>) lastMsg.get("object");
                assert msg.containsKey(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                //noinspection unchecked
                Map<String, Object> hdrs =
                        (Map<String, Object>) msg.get(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                assert hdrs != null;
                answerMap.forEach( (key, value) -> {
                    assertTrue("Missing header " + key, hdrs.containsKey(key));
                    log.debug("For key {}, comparing value {} ({}) with expected {} ({}).",
                              key, hdrs.get(key),
                              hdrs.get(key) != null ? hdrs.get(key).getClass().getName() : hdrs.get(key),
                              value,
                              value != null ? value.getClass().getName() : value);
                    if (hdrs.get(key) != null) {
                        assert hdrs.get(key).equals(value);
                    } else {
                        assert value == null;
                        // Also, verify that the header is present -- get(key) will return null if key isn't present.
                        assert hdrs.containsKey(key);
                    }
                });
            } catch (Exception e) {
                log.error("Route " + routeId + ": Trapped exception", e);
                worked = false;
            } finally {
                if (template != null) {
                    try {
                        template.stop();
                        template.close();
                        template.cleanUp();
                    } catch (IOException e) {
                       log.error("Trapped Exception closing template", e);
                    }
                }}
            return worked;
        };
        return verifyOperation;
    }

    public static final String sfLoginUrl = System.getProperty("camel-salesforce-loginUrl", MISSING_VALUE);
    public static final String sfClientId = System.getProperty("camel-salesforce-clientId", MISSING_VALUE);
    public static final String sfClientSecret = System.getProperty("camel-salesforce-clientSecret", MISSING_VALUE);
    public static final String sfRefreshToken = System.getProperty("camel-salesforce-refreshToken", MISSING_VALUE);
    
    private final Map<String, String> propList = Map.of( "loginUrl", sfLoginUrl,
                                                         "clientId", sfClientId,
                                                         "clientSecret", sfClientSecret,
                                                         "refreshToken", sfRefreshToken
    );
    
    public static final Map<String, String> FHIR_PROPERTIES = Map.of (
            "apiName", "SEARCH",
            "prettyPrint", "true",
            "log", "true",
            "methodName", "searchByUrl",
            "encoding", "JSON",
            "lazyStartProducer", "false",
            "serverUrl", "https://server.fire.ly",
            "fhirVersion", "R4"//,
    );
    
    // The following is a _modified for test purposes_ version of the route used in the fhir-sink kamelet. Testing
    // that, discovered that the _choice_ statement gets built up at discovery time (which we would expect), but the
    // expression it generates internally is cached WITH a reference to the camel context used for discovery. Then,
    // when that context was closed (it no longer is), subsequent evaluations using that context found no
    // typeConverter in the context (trashed on close), and threw an NPE.  Here, we verify that this basic route
    // work as expected.  It takes a bit of time to set things up, but a reasonable test.
    public static final String FHIR_SINK_ROUTE_YAML = "\n"
            + "-   route-template:\n"
            + "       id: Route templates from fhir_sink:v3_21_0\n"
            + "       from:\n"
            + "           uri: direct:start\n"
//            + "           uri: vantiq://server.config?consumerOutputJsonStream=true&structuredMessageHeader=true\n"
            + "           steps:\n"
            + "           -   choice:\n"
            + "                    precondition: true\n"
            + "                    when:\n"
            + "                    -   simple: ${properties:encoding} =~ 'JSON'\n"
            + "                        steps:\n"
            + "                        -   unmarshal:\n"
            + "                                fhirJson:\n"
            + "                                    fhir-version: '{{fhirVersion}}'\n"
            + "                                    pretty-print: '{{prettyPrint}}'\n"
            + "                    -   simple: ${properties:encoding} =~ 'XML'\n"
            + "                        steps:\n"
            + "                        -   unmarshal:\n"
            + "                                fhirXml:\n"
            + "                                    fhir-version: '{{fhirVersion}}'\n"
            + "                                    pretty-print: '{{prettyPrint}}'\n"
            + "           -   to:\n"
            + "                    uri: fhir://{{apiName}}/{{methodName}}\n"
            + "                    parameters:\n"
            + "                        serverUrl: '{{serverUrl}}'\n"
// fhir-sink kamelet says this is there, but it just gets errors about unknown parameter.  Leaving it out to get
// things to work.  fhir-sink route is similarly overridden.
//            + "                        inBody: \"resource\" \n"
            + "                        encoding: '{{encoding}}'\n"
            + "                        fhirVersion: '{{fhirVersion}}'\n"
            + "                        log: '{{log}}'\n"
            + "                        prettyPrint: '{{prettyPrint}}'\n"
            + "                        lazyStartProducer: '{{lazyStartProducer}}'\n"
            + "                        proxyHost: '{{?proxyHost}}'\n"
            + "                        proxyPassword: '{{?proxyPassword}}'\n"
            + "                        proxyPort: '{{?proxyPort}}'\n"
            + "                        proxyUser: '{{?proxyUser}}'\n"
            + "                        accessToken: '{{?accessToken}}'\n"
            + "                        username: '{{?username}}'\n"
            + "                        password: '{{?password}}'\n"
            + "           - marshal:\n"
            + "               fhirJson:\n"
            + "                   fhir-version: '{{fhirVersion}}'\n"
            + "                   pretty-print: '{{prettyPrint}}'\n"
            + "           - to:\n"
            + "                  uri: mock:result"; // Send off to mock result so we can verify the output

    public static final String SALESFORCETASKS_YAML =  "\n"
        + "- route:\n"
        + "    id: \"Salesforce from yaml-route\"\n"
        + "    from:\n"
        + "      uri: \"direct:start\"\n"
        + "      steps:\n"
//        + "        - set-exchange-pattern: \"inOut\"\n" // Leaving as a reminder re: how to do in/out YAML routes
        + "        - to:\n"
        + "            uri: \"salesforce:query?rawPayload=true&SObjectQuery=SELECT Id, Subject, OwnerId from Task\"\n"
        + "        - unmarshal:\n"
        + "            json: {}\n"
        + "        - to:\n"
        + "            uri: \"mock:result\"\n";
    // in/out have to route their answer back to their source. In our
    // case, we're not really doing a "query", so this is fine.
    
    public List<Map<String, Object>> getComponentsToInit() {
        return List.of(
                Map.of(CamelRunner.COMPONENT_NAME, "salesforce",
                       COMPONENT_PROPERTIES, propList)
        );
    }
    
    public static final String PARAMETERIZED_SALESFORCE_ROUTE  = "\n"
            + "- route:\n"
            + "    id: \"Salesforce from yaml-route\"\n"
            + "    from:\n"
            + "      uri: \"{{directStart}}\"\n"
            + "      steps:\n"
//        + "        - set-exchange-pattern: \"inOut\"\n" // Leaving as a reminder re: how to do in/out YAML routes
            + "        - to:\n"
            + "            uri: \"salesforce:query?rawPayload=true&SObjectQuery={{query}}\"\n"
            + "        - unmarshal:\n"
            + "            json: {}\n"
            + "        - to:\n"
            + "            uri: \"{{mockResult}}\"\n";
    
    public static final Map<String, String> pValues = Map.of("directStart", "direct:start",
                                                             "query", "SELECT Id, Subject, OwnerId from Task",
                                                             "mockResult", "mock:result");
    
    
    public String constructVantiqUri(String vantiqSvr, String beanName) {
        return "vantiq://" + vantiqSvr + "/" +
                "?" + SOURCE_NAME_PARAM + "=someSource" +
                "&" + ACCESS_TOKEN_PARAM + "=someAccessToken" +
                "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true" +
                "&" + HEADER_DUPLICATION_BEAN_NAME + "=" + beanName;
    }
    public String constructVantiqRoute(String vantiqEndpointStructuredHeaderMapUri) {
        return "\n"
                + "- route:\n"
                + "    id: \"Vantiq Route with Header Duplication\"\n"
                + "    from:\n"
                + "      uri: \"direct:vantiqStart\"\n"
                + "      steps:\n"
                + "        - to:\n"
                + "            uri: log:VantiqConfigurationTest?" +
                                                "level=info&showHeaders=true&showAllProperties=true\n"
                + "        - to:\n"
                + "            uri: " + vantiqEndpointStructuredHeaderMapUri + "\n";
    }
}
