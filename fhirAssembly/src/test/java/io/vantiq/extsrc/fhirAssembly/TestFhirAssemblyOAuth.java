package io.vantiq.extsrc.fhirAssembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Collections;
import java.util.Map;

/**
 * Test operations against a FHIR server using fhirConnection assembly
 */
@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestFhirAssemblyOAuth extends TestFhirAssembly {
    
    public static String FHIR_SOURCE = "com.vantiq.fhir.fhirServer";
    public static String FHIR_OAUTH_SOURCE = "com.vantiq.fhir.oauthSource";
    public static String TEST_CLIENT_ID = "accessTokenTestClient";
    public static String TEST_CLIENT_SECRET = "9oEkhu5FKdxFiJRd2pddlT9xYwrj2aeX";
    public static String KEYCLOAK_SOURCE_URL =
            "http://localhost:18080/auth/realms/local/protocol/openid-connect/token/";
    
    // Playing this little indirection since the methods are static (Junit Rules) but we want to override.  Copying a
    // bunch of code just to test different situations is not attractive.  JUnit's @BeforeClass will ensure that only
    // one instance of a named method is run, so that suffices for our needs.
    
    @BeforeClass
    public static void setupEnv() {
        performSetup(getAssemblyConfigForTest());
    }
    
    public static Map<String,?> getAssemblyConfigForTest() {
        Map<String, ?> config =  Map.of(
                "oauthClientId", TEST_CLIENT_ID,
                "oauthClientSecret", TEST_CLIENT_SECRET,
                "oauthGrantType", "client_credentials",
                "oauthServerUrl", KEYCLOAK_SOURCE_URL,
                "oauthSourceName", "com.vantiq.fhir.oauthSource",
                "oauthActive", true,
                "fhirServerBaseUrl", FHIR_SERVER,
                "defaultSearchHttpMethod", "GET",
                "authenticationMechanism", "OAuth");
        log.debug("Returning assembly config of: {}", config);
        return config;
    }
    
    @Test
    public void test000ValidateSourceOAuth() throws Exception {
        
        // Verify that the source's in the assembly are correctly set up...
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        // Force configuration to run...
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.getCapabilityStatement",
                                        Collections.emptyList());
        assertTrue("Could not fetch capabilities: " + resp.getErrors(), resp.isSuccess());
        
        registerGetConfiguredSource(v);
        
        resp = v.execute("getConfiguredSource",
                                       Map.of("sourceName", FHIR_SOURCE));
        assertTrue("Could not fetch fetch source: " + FHIR_SOURCE + "::" + resp.getErrors(), resp.isSuccess());
        assertTrue("Source not in form we expect: " + resp.getBody().getClass().getName(),
                   resp.getBody() instanceof JsonObject);
        //noinspection unchecked
        Map<String, ?> fhirSource = mapper.readValue(new Gson().toJson(((JsonObject) resp.getBody())),
                                                                                       Map.class);
        log.debug("{} Definition: {}", FHIR_SOURCE, fhirSource);
        //noinspection unchecked
        Map<String, ?> config = (Map<String, ?>) fhirSource.get("config");
        assertTrue(FHIR_SOURCE + ": wrong username", ((String) config.get("username")).isEmpty());
        assertTrue(FHIR_SOURCE + ": wrong password", ((String) config.get("password")).isEmpty());
        
        resp = v.execute("getConfiguredSource",
                         Map.of("sourceName", FHIR_OAUTH_SOURCE));
        assertTrue("Could not fetch fetch source: " + FHIR_OAUTH_SOURCE + "::" + resp.getErrors(),
                   resp.isSuccess());
        //noinspection unchecked
        Map<String, ?> oauthSource =  mapper.readValue(new Gson().toJson(((JsonObject) resp.getBody())),
                                                       Map.class);
        log.debug("{} Definition: {}", FHIR_OAUTH_SOURCE, oauthSource);
        
        //noinspection unchecked
        Map<String, ?> oauthConfig = (Map<String, ?>) oauthSource.get("config");
        assertEquals(FHIR_OAUTH_SOURCE + ": bad client id", TEST_CLIENT_ID, oauthConfig.get("oauthClientId"));
        assertEquals(FHIR_OAUTH_SOURCE + ": bad client secret", TEST_CLIENT_SECRET,
                     oauthConfig.get("oauthClientSecret"));
        assertEquals(FHIR_OAUTH_SOURCE + ": bad grant type", "client_credentials",
                     oauthConfig.get("oauthGrantType"));
        
        if (oauthSource.get("active") instanceof String) {
            assertTrue(FHIR_OAUTH_SOURCE + ": should be active",
                       Boolean.parseBoolean((String) oauthSource.get("active")));
        } else {
            assertTrue(FHIR_OAUTH_SOURCE + ": should be active",
                       (Boolean) oauthSource.get("active"));
        }
    }
}