package io.vantiq.extsrc.camelconn.connector;

import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.NO_RAW_REQUEST;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_END;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_END_ALT;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_START;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_START_ALT;

import static io.vantiq.extjsdk.ExtensionServiceMessage.OP_CONFIGURE_EXTENSION;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.APP_NAME;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_APP;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_CONFIG;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_CACHE;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_LIB;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_PROPERTIES;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.DISCOVERED_RAW;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.GENERAL;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.PROPERTY_VALUES;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.RAW_REQUIRED;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.ROUTES_FORMAT;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.ROUTES_LIST;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.MISSING_VALUE;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.QUERY_AARDVARK;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.QUERY_MONKEY;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.RESPONSE_AARDVARK;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.RESPONSE_MONKEY;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.defineVerifyOperation;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.XML_ROUTE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extsrc.camelconn.discover.CamelRunner;
import org.apache.camel.CamelContext;
import org.apache.commons.lang3.function.TriFunction;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class VantiqConfigurationTest {
    
    
    @Rule
    public TestName name = new TestName();
    
    private final String BUILD_DIR = System.getProperty("BUILD_DIR", "build");
    private final String CAMEL_CONN_BASE_DIR = "camelConnBase";
    private final String CAMEL_BASE_PATH = BUILD_DIR + File.separator + CAMEL_CONN_BASE_DIR + File.separator;
    private final String CACHE_DIR = CAMEL_BASE_PATH + "cacheDir";
    private final String LOADED_LIBRARIES = CAMEL_BASE_PATH + "loadedLib";
    
    // Interface to use in declaration.  We'll pass lambda's in to do the actual verification work
    interface Verifier {
        void doVerify(CamelContext runnerContext, Properties props);
    }
    
    void performConfigTest(String appName, String route, String routeFormat,
                           List<Map<String, Object>> compInitProps, Properties propertyValues, Verifier vfy) {
        performConfigTest(appName, route, routeFormat, compInitProps, propertyValues, vfy, null);
    }
    void performConfigTest(String appName, String route, String routeFormat,
                           List<Map<String, Object>> compInitProps, Properties propertyValues, Verifier vfy,
                           List<String> rawReq) {
        assumeTrue(!sfLoginUrl.equals(MISSING_VALUE) && !sfClientId.equals(MISSING_VALUE) &&
                           !sfClientSecret.equals(MISSING_VALUE) && !sfRefreshToken.equals(MISSING_VALUE));
        
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

        String fauxVantiqUrl = "http://someVantiqServer";
        ExtensionServiceMessage esm = new ExtensionServiceMessage(fauxVantiqUrl);
        esm.op = OP_CONFIGURE_EXTENSION;
        esm.object = simpleConfig;

        CamelCore core = new CamelCore("testComponentInitConfiguration",
                                       "someAccessToken", fauxVantiqUrl);
        CamelHandleConfiguration handler = new CamelHandleConfiguration(core);

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
            vfy.doVerify(runnerContext, handler.getCurrentCamelRunner().getCamelProperties());
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
    
        performConfigTest(name.getMethodName(), ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext, Properties lclProps) -> {
                                assert lclProps != null;
                                assert propValues.size() == lclProps.size();
                                propValues.forEach( (name, val) -> {
                                    assert lclProps.containsKey(name);
                                    assert lclProps.getProperty(name).equals(val);
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
    
        performConfigTest(name.getMethodName(), ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext, Properties lclProps) -> {
                              assert lclProps != null;
                              assert propValues.size() == lclProps.size();
                              propValues.forEach( (name, val) -> {
                                  assert lclProps.containsKey(name);
                                  if (unchanged.contains(name)) {
                                      assert lclProps.getProperty(name).equals(val);
                                  } else {
                                      assert lclProps.getProperty(name).equals(CamelHandleConfiguration.RAW_START +
                                                              val + CamelHandleConfiguration.RAW_END);
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
    
        performConfigTest(name.getMethodName(), ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext, Properties lclProps) -> {
                              assert lclProps != null;
                              assert propValues.size() == lclProps.size();
                              propValues.forEach( (name, val) -> {
                                  assert lclProps.containsKey(name);
                                  if (raws.contains(name)) {
                                      assert lclProps.getProperty(name).equals(CamelHandleConfiguration.RAW_START +
                                              val + CamelHandleConfiguration.RAW_END);
                                  } else {
                                      assert lclProps.getProperty(name).equals(val);
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
        
        performConfigTest(name.getMethodName(), ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext, Properties lclProps) -> {
                              assert lclProps != null;
                              assert propValues.size() == lclProps.size();
                              propValues.forEach( (name, val) -> {
                                  assert lclProps.containsKey(name);
                                  if (name.equals("prop1")) {
                                      assert lclProps.getProperty(name).equals(CamelHandleConfiguration.RAW_START_ALT +
                                                                                       val + CamelHandleConfiguration.RAW_END_ALT);
                                  } else if (name.equals("prop3")) {
                                      assert lclProps.getProperty(name).equals(CamelHandleConfiguration.RAW_START +
                                                                                       val + CamelHandleConfiguration.RAW_END);
                                  } else {
                                      assert lclProps.getProperty(name).equals(val);
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
    
        performConfigTest(name.getMethodName(), ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext, Properties lclProps) -> {
                              assert lclProps != null;
                              assert propValues.size() == lclProps.size();
                              propValues.forEach( (name, val) -> {
                                  assert lclProps.containsKey(name);
                                  if (raws.contains(name)) {
                                      String newVal = ((String) propValues.get(name))
                                              .substring(NO_RAW_REQUEST.length());
                                      assert lclProps.getProperty(name).equals(newVal);
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
    
        performConfigTest(name.getMethodName(), ROUTE_NOT_USED, "yaml",
                          null, props,
                          (CamelContext runnerContext, Properties lclProps) -> {
                              assert lclProps != null;
                              assert propValues.size() == lclProps.size();
                              propValues.forEach( (name, val) -> {
                                  assert lclProps.containsKey(name);
                                  if (name.equals("prop1")) {
                                      assert lclProps.getProperty(name).equals(RAW_START + val + RAW_END);
                                  } else {
                                      assert lclProps.getProperty(name).equals(val);
                                  }
                              });
                          }, raws);
    
    }
    @Test
    public void testSimpleConfiguration() {
        performConfigTest(name.getMethodName(), XML_ROUTE, "xml",
                          null, null,
                          (CamelContext runnerContext, Properties props) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                              assert verifyOperation.apply(runnerContext, QUERY_MONKEY, RESPONSE_MONKEY);
                              assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, RESPONSE_AARDVARK);
                          });
    }
    
    @Test
    public void testComponentInitConfiguration() {
        performConfigTest(name.getMethodName(), SALESFORCETASKS_YAML, "yaml",
                          getComponentsToInit(), null,
                          (CamelContext runnerContext, Properties props) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                            assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                            assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                        });
    }
    
    @Test
    public void testComponentInitConfigurationWithPropertyValues() {
        Properties props = new Properties(pValues.size());
        // Though officially frowned upon, Properties.putAll here from a Map<String, String> is safe as it cannot put
        // non-String keys or values into the Properties base map.
        props.putAll(pValues);
        performConfigTest(name.getMethodName(), PARAMETERIZED_SALESFORCE_ROUTE, "yaml",
                          getComponentsToInit(), props,
                          (CamelContext runnerContext, Properties pros) -> {
                              TriFunction<CamelContext, String, Object, Boolean> verifyOperation =
                                      defineVerifyOperation();
                              assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                              assert verifyOperation.apply(runnerContext, "not used", Map.of("done", true));
                          });
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
}
