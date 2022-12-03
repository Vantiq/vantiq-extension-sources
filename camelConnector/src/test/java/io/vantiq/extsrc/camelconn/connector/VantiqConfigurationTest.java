package io.vantiq.extsrc.camelconn.connector;


import static io.vantiq.extjsdk.ExtensionServiceMessage.OP_CONFIGURE_EXTENSION;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.APP_NAME;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_APP;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.CAMEL_CONFIG;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_CACHE;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.COMPONENT_LIB;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.GENERAL;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.ROUTES_FORMAT;
import static io.vantiq.extsrc.camelconn.connector.CamelHandleConfiguration.ROUTES_LIST;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.QUERY_AARDVARK;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.QUERY_MONKEY;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.RESPONSE_AARDVARK;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.RESPONSE_MONKEY;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.defineVerifyOperation;
import static io.vantiq.extsrc.camelconn.discover.VantiqComponentResolverTest.XML_ROUTE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import org.apache.camel.CamelContext;
import org.apache.commons.lang3.function.TriFunction;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
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
    
        TriFunction<CamelContext, String, String, Boolean> verifyOperation = defineVerifyOperation();
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
}
