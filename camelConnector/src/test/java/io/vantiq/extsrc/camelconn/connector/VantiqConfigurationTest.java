package io.vantiq.extsrc.camelconn.connector;


import static io.vantiq.extjsdk.ExtensionServiceMessage.OP_CONFIGURE_EXTENSION;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.APP_NAME;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_APP;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_CONFIG;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_CACHE;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_LIB;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_PROPERTIES;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.GENERAL;
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
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VantiqConfigurationTest {
    
    private final String BUILD_DIR = System.getProperty("BUILD_DIR", "build");
    private final String CAMEL_CONN_BASE_DIR = "camelConnBase";
    private final String CAMEL_BASE_PATH = BUILD_DIR + File.separator + CAMEL_CONN_BASE_DIR + File.separator;
    private final String CACHE_DIR = CAMEL_BASE_PATH + "cacheDir";
    private final String LOADED_LIBRARIES = CAMEL_BASE_PATH + "loadedLib";
    
    @Test
    public void testSimpleConfiguration() {
        Map<String, Object> simpleConfig = new HashMap<>();
        Map<String, Object> camelConfig = new HashMap<>();
        simpleConfig.put(CAMEL_CONFIG, camelConfig);
        Map<String, String> camelAppConfig = new HashMap<>();
        camelConfig.put(CAMEL_APP, camelAppConfig);
        Map<String, String> generalConfig = new HashMap<>();
        camelConfig.put(GENERAL, generalConfig);
        
        generalConfig.put(COMPONENT_CACHE, CACHE_DIR);
        generalConfig.put(COMPONENT_LIB, LOADED_LIBRARIES);
        
        camelAppConfig.put(ROUTES_LIST, XML_ROUTE);
        camelAppConfig.put(ROUTES_FORMAT, "xml");
        camelAppConfig.put(APP_NAME, "testSimpleConfiguration");
    
        String fauxVantiqUrl = "http://someVantiqServer";
        ExtensionServiceMessage esm = new ExtensionServiceMessage(fauxVantiqUrl);
        esm.op = OP_CONFIGURE_EXTENSION;
        esm.object = simpleConfig;
        
        CamelCore core = new CamelCore("testSimpleConfiguration", "someAccessToken", fauxVantiqUrl);
        CamelHandleConfiguration handler = new CamelHandleConfiguration(core);
        
        handler.handleMessage(esm);
        assertTrue("handler completed", handler.isComplete());
        assertNotNull("Camel Runner", handler.getCurrentCamelRunner());
        assertTrue("Runner started", handler.getCurrentCamelRunner().isStarted());
        assertNotNull("Runner's Camel Context", handler.getCurrentCamelRunner().getCamelContext());
        assertNotNull("Runner's thread", handler.getCurrentCamelRunner().getCamelThread());
        
        // Assuming things worked, now we have a camel app running.  We'll run our test against it & verify
    
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = defineVerifyOperation();
        CamelContext runnerContext = handler.getCurrentCamelRunner().getCamelContext();
        assertTrue("Context Running", runnerContext.isStarted());
        assert verifyOperation.apply(runnerContext, QUERY_MONKEY, RESPONSE_MONKEY);
        assert verifyOperation.apply(runnerContext, QUERY_AARDVARK, RESPONSE_AARDVARK);
    
        handler.getCurrentCamelRunner().close();
        try {
            handler.getCurrentCamelRunner().getCamelThread().join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException ie) {
            fail("Trapped Interrupted Exception");
        }
    }
    
    @Test
    public void testComponentInitConfiguration() {
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
        
        camelAppConfig.put(ROUTES_LIST, SALESFORCETASKS_YAML);
        camelAppConfig.put(ROUTES_FORMAT, "yaml");
        camelAppConfig.put(APP_NAME, "testComponentInitConfiguration");
        camelAppConfig.put(COMPONENT_PROPERTIES, getComponentsToInit());
        
        String fauxVantiqUrl = "http://someVantiqServer";
        ExtensionServiceMessage esm = new ExtensionServiceMessage(fauxVantiqUrl);
        esm.op = OP_CONFIGURE_EXTENSION;
        esm.object = simpleConfig;
        
        CamelCore core = new CamelCore("testComponentInitConfiguration", "someAccessToken", fauxVantiqUrl);
        CamelHandleConfiguration handler = new CamelHandleConfiguration(core);
        
        handler.handleMessage(esm);
        assertTrue("handler completed", handler.isComplete());
        assertNotNull("Camel Runner", handler.getCurrentCamelRunner());
        assertTrue("Runner started", handler.getCurrentCamelRunner().isStarted());
        assertNotNull("Runner's Camel Context", handler.getCurrentCamelRunner().getCamelContext());
        assertNotNull("Runner's thread", handler.getCurrentCamelRunner().getCamelThread());
        
        // Assuming things worked, now we have a camel app running.  We'll run our test against it & verify
        
        TriFunction<CamelContext, String, Object, Boolean> verifyOperation = defineVerifyOperation();
        CamelContext runnerContext = handler.getCurrentCamelRunner().getCamelContext();
        assertTrue("Context Running", runnerContext.isStarted());
        assert verifyOperation.apply(runnerContext, "not used", Map.of( "done", true));
        assert verifyOperation.apply(runnerContext, "not used", Map.of( "done", true));
        
        handler.getCurrentCamelRunner().close();
        try {
            handler.getCurrentCamelRunner().getCamelThread().join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException ie) {
            fail("Trapped Interrupted Exception");
        }
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
    
}
