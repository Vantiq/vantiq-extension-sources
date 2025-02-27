package io.vantiq.extsrc.fhirAssembly;

import static io.vantiq.extsrc.fhirAssembly.TestFhirAssembly.SUB_USER;
import static io.vantiq.extsrc.fhirAssembly.TestFhirAssembly.TEST_SERVER;
import static io.vantiq.extsrc.fhirAssembly.TestFhirAssembly.performSetup;
import static io.vantiq.extsrc.fhirAssembly.TestFhirAssembly.performTeardown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;

import org.apache.http.Header;
import org.apache.http.HttpHost;

import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalServerTestBase;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TestFhirUseAccessToken extends LocalServerTestBase {
    
    public static String fauxServer = null;
    public final static String FHIR_SERVER_PATH = "/fhir/";
    ObjectMapper mapper = new ObjectMapper();
    
    Header[] foundHeaders;
    
    @BeforeClass
    public static void setupEnv() throws Exception {
        performSetup(null);
    }
    
    private HttpRequestHandler returnEmpty =
            (request, response, context) -> {
                log.debug("Got headers: {}", (Object) request.getAllHeaders());
                foundHeaders = request.getAllHeaders();
                response.setEntity(new StringEntity("{}"));
            };
    
    public static Map<String,?> getAssemblyConfigForTest() {
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged");
        log.debug("Returning assembly config of: {}", config);
        return config;
    }
    
    @AfterClass
    public static void endTest() {
        performTeardown();
    }
    
    @Before
    public void setup() throws Exception {
        foundHeaders = null;
        this.serverBootstrap.registerHandler(FHIR_SERVER_PATH + "*", returnEmpty);
        this.serverBootstrap.setConnectionReuseStrategy((response, context) -> false);
        HttpHost host = super.start();
        fauxServer = host.toURI() + FHIR_SERVER_PATH;
        
        log.debug("Server created at {}, fauxServer at {}", host.toURI(), fauxServer);
    }
    
    @After
    public void shutdown() {
        server.shutdown(0, TimeUnit.SECONDS);
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        TestFhirAssembly.uninstallAssembly(v);
    }
    
    @Test
    public void testOperationsWithAuth() {
        doOperations("Bearer", "worthlessToken", getAssemblyConfigForTest());
    }
    
    @Test
    public void testOperationsSansAuth() {
        doOperations(null, null, getAssemblyConfigForTest());
    }
    
    @Test
    public void testOperationsWithRealmAuth() {
        doOperations("SomeRandomRealm", "sillyRealmToken", getAssemblyConfigForTest());
    }
    
    @Test
    public void testOperationsWithConfigBasedToken() {
        String tokenValue = "ImALittleTeaPot";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged",
                                        "basicAccessToken", tokenValue);
        doOperations("Bearer", tokenValue, config);
    }
    
    @Test
    public void testOperationsWithConfigBasedTokenAndRealm() {
        String tokenValue = "ImALittleTeaPot";
        String tokenRealm = "HonaleeLandOfPuff";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged",
                                        "basicRealm", tokenRealm,
                                        "basicAccessToken", tokenValue);
        doOperations(tokenRealm, tokenValue, config);
    }
    
    @Test
    public void testOperationsWithOverrideConfigBasedToken() {
        String tokenValue = "ImALittleTeaPot";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged",
                                        "basicAccessToken", tokenValue);
        doOperations("Bearer", "IAmNotATeaPot", config);
    }
    
    @Test
    public void testOperationsWithOverrideConfigBasedTokenAndRealm() {
        String tokenValue = "ImALittleTeaPot";
        String tokenRealm = "HonaleeLandOfPuff";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged",
                                        "basicRealm", tokenRealm,
                                        "basicAccessToken", tokenValue);
        doOperations("LandOfMakeBelieve", "IAmNotATeaPot", config);
    }
    
    @Test
    public void testOperationsWithAppManagedConfigBasedToken() {
        String tokenValue = "ImALittleTeaPot";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged",
                                        "basicAccessToken", tokenValue);
        doOperations("Bearer", tokenValue, config, true);
    }
    
    @Test
    public void testOperationsWithBasicTokenAndRealm() {
        String tokenValue = "ImALittleTeaPot";
        String tokenRealm = "HonaleeLandOfPuff";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "Basic",
                                        "basicRealm", tokenRealm,
                                        "basicAccessToken", tokenValue);
        doOperations(tokenRealm, tokenValue, config, true);
    }
    
    @Test
    public void testOperationsBasicConfigBasedToken() {
        String tokenValue = "ImALittleTeaPot";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "Basic",
                                        "basicAccessToken", tokenValue);
        doOperations("Bearer", tokenValue, config, true);
    }
    
    @Test
    public void testOperationsWithAppManagedConfigBasedTokenAndRealm() {
        String tokenValue = "ImALittleTeaPot";
        String tokenRealm = "HonaleeLandOfPuff";
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", fauxServer,
                                        "authenticationMechanism", "ApplicationManaged",
                                        "basicRealm", tokenRealm,
                                        "basicAccessToken", tokenValue);
        doOperations(tokenRealm, tokenValue, config, true);
    }
    
    
    void doOperations(String tType, String tValue, Map<String, ?> assyConfig) {
        doOperations(tType, tValue, assyConfig, false);
    }
    
    void doOperations(String tType, String tValue, Map<String, ?> assyConfig, boolean suppressUseToken) {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        VantiqResponse resp;
        TestFhirAssembly.installAssembly(v, assyConfig);
        if (!suppressUseToken && (tType != null || tValue != null)) {
            Map<String, String> params = new HashMap<>();
            params.put("accessToken", tValue);
            if (tType != null) {
                params.put("tokenType", tType);
            }
            resp = v.execute("com.vantiq.fhir.fhirService.useAccessToken",
                                            params);
            
            assertTrue("Could not provide access token: " + resp.getErrors(), resp.isSuccess());
        }
        resp = v.execute("com.vantiq.fhir.fhirService.getSMARTConfiguration",
                                        Collections.emptyList());
        assertTrue("Could not fetch SMART Config: " + resp.getErrors(), resp.isSuccess());
        assertNotNull("Ho headers found", foundHeaders);
        checkAuthHeader(tType, tValue);
        
        foundHeaders = null;
        Map<String, String> params = new HashMap<>();
        
        params.put("id", "doesn'tMatter");
        params.put("type", "Patient");
        resp = v.execute("com.vantiq.fhir.fhirService.read", params);
        assertTrue("Could not read resource: " + resp.getErrors(), resp.isSuccess());
        assertNotNull("Ho headers found", foundHeaders);
        checkAuthHeader(tType, tValue);
        
        foundHeaders = null;
        File ourHeroFile = new File("build/resources/test/ourHero.json");
        Map<String, ?> ourHeroMap = null;
        try {
            //noinspection unchecked
            ourHeroMap = mapper.readValue(ourHeroFile, Map.class);
        } catch (Exception e) {
            fail("Couldn't read standard file" + e);
        }
        
        resp = v.execute("com.vantiq.fhir.fhirService.create",
                                        Map.of("type", "Patient", "resource", ourHeroMap));
        assertTrue("Could not create our hero: " + resp.getErrors(), resp.isSuccess());
        assertNotNull("Ho headers found", foundHeaders);
        checkAuthHeader(tType, tValue);
        
        Map<String, ?> modifiers = Map.of("headers",
                                          Map.of("If-None-Exist", "name=" + "Man"));
        
        // Now, create our here again, but conditional on it's not being already there.
        
        resp = v.execute("com.vantiq.fhir.fhirService.create",
                                        Map.of("type", "Patient",
                                               "id", "batman",
                                               "resource", ourHeroMap,
                                               "modifiers", modifiers));
        assertTrue("Could not create our hero: " + resp.getErrors(), resp.isSuccess());
        assertNotNull("Ho headers found", foundHeaders);
        checkAuthHeader(tType, tValue);
        Header modHeader = findHeader("If-None-Exist");
        assertNotNull("No If-None-Exist header found", modHeader);
        assertEquals("Wrong value for If-None_Exist header", "name=Man", modHeader.getValue());
    }
    
    Header findHeader(String headerName) {
        for (Header foundHeader : foundHeaders) {
            if (headerName.equalsIgnoreCase(foundHeader.getName())) {
                return foundHeader;
            }
        }
        return null;
    }
    
    void checkAuthHeader(String tType, String tValue) {
        Header authHeader = findHeader("Authorization");
        if (tValue != null) {
            assertNotNull("No Authorization header found", authHeader);
            String[] hParts = authHeader.getValue().split("\\s");
            assertEquals("Authorization header wrong size", hParts.length, 2);
            assertEquals("Auth Header wrong value", tValue, hParts[1]);
            assertEquals("Auth header wrong type", tType, hParts[0]);
        } else {
            assertNull("Auth header found where none expected", authHeader);
        }
    }
}
