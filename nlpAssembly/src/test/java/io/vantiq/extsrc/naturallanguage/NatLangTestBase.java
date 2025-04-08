package io.vantiq.extsrc.naturallanguage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
public class NatLangTestBase {
    
    public static final boolean CREATE_TEST_NAMESPACE =
            Boolean.parseBoolean(System.getProperty("createNS", "true"));
    public static final String CATALOG_NS_NAME = System.getProperty("catalogNS", "NLCatalog");
    public static final String CATALOG_NAME = "testNLCatalog";
    public static final String SUBSCRIBER_NS_NAME = System.getProperty("subscriberNS", "NLUser");
    public static final String TEST_SERVER = System.getProperty("vantiqTestServer", "http://localhost:8080");
    public static Map<String, String> CATALOG_NAMESPACE = Map.of("namespace", CATALOG_NS_NAME);
    public static Map<String, String> SUBSCRIBER_NAMESPACE = Map.of("namespace", SUBSCRIBER_NS_NAME);
    public static final String NATLANG_ASSY_NAME = "com.vantiq.nlp.NaturalLanguageProcessing";
    public static String SUB_USER;
    public static String PUB_USER;
    
    public static String startTime = null;
    public ObjectMapper mapper = new ObjectMapper();
    
    public static void performSetup(Map<String, ?> assemblyConfig) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        startTime = sdf.format(new Date());
        SUB_USER = "admin__" + SUBSCRIBER_NS_NAME;
        PUB_USER = "admin__" + CATALOG_NS_NAME;
        File assyZip = new File("build/distributions/natLangProcessing-assembly.zip");
        assertTrue("Cannot find assembly file from build: " + assyZip.getAbsolutePath(), assyZip.exists());
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        if (CREATE_TEST_NAMESPACE) {
            v.authenticate("system", "fxtrt$1492");
            VantiqResponse resp = v.insert("system.namespaces", SUBSCRIBER_NAMESPACE);
            assertTrue("Error encountered creating NS: " + resp.getErrors(), resp.isSuccess());
            
            resp = v.insert("system.namespaces", CATALOG_NAMESPACE);
            assertTrue("Error encountered creating NS: " + resp.getErrors(), resp.isSuccess());
            resp = v.authenticate(PUB_USER, PUB_USER);
            assertTrue("Could not authenticate into ns: " + CATALOG_NS_NAME + " :: " + resp.getErrors(),
                       resp.isSuccess());
            resp = v.select("system.catalogs", Collections.emptyList(), null, null);
            assertTrue("Could not fetch catalogs: " + resp.getErrors(), resp.isSuccess());
            assertEquals("Found unexpected catalogs", 0, ((List<?>) resp.getBody()).size());
            
            resp = v.execute("Broker.createCatalog",
                             Map.of("name", CATALOG_NAME, "allowEdge", false));
            assertTrue("Could not create test catalog: " + resp.getErrors(), resp.isSuccess());
            resp = v.select("system.catalogs", Collections.emptyList(), null, null);
            assertTrue(resp.isSuccess());
            //noinspection unchecked
            for (int i = 0; i < ((List<JsonObject>) resp.getBody()).size(); i++ ) {
                //noinspection unchecked
                log.debug("Found catalog: {}", ((List<JsonObject>) resp.getBody()).get(i).getAsJsonObject()
                                                                                  .get("name"));
                //noinspection unchecked
                log.trace("Found catalog data: {}", ((List<JsonObject>) resp.getBody()).get(i));
                
            }
            resp = v.upload(assyZip, "application/zip", NATLANG_ASSY_NAME);
            assertTrue("Could not create document entry: " + resp.getErrors() +
                               " for file " + assyZip.getAbsolutePath(),
                       resp.isSuccess());
            resp = v.execute("Broker.createAssembly",
                             Map.of("catalogName", CATALOG_NAME,
                                    "assemblyName", NATLANG_ASSY_NAME,
                                    "zipFileName", NATLANG_ASSY_NAME));
            assertTrue("Could not create catalog entry: " + resp.getErrors() +
                               " for file " + assyZip.getAbsolutePath(),
                       resp.isSuccess());
            resp = v.insert("system.tokens",
                            Map.of("name", "catalogToken"));
            assertTrue("Could not create catalog token: " + resp.getErrors(), resp.isSuccess());
            resp = v.selectOne("system.tokens", "catalogToken");
            assertTrue("Could not fetch catalog token: " + resp.getErrors(), resp.isSuccess());
            assertTrue("Token not in form we expect: " + resp.getBody().getClass().getName(),
                       resp.getBody() instanceof JsonObject);
            Map<String, ?> catalogToken = ((JsonObject) resp.getBody()).asMap();
            log.trace("Got access token {}: {}", catalogToken, catalogToken.get("accessToken").toString());
            String tokenString = ((JsonPrimitive) catalogToken.get("accessToken")).getAsString();
            // OK, now we've published our assembly to the catalog.  Now, we'll switch to the subscriber, connect to
            // the catalog, and install the assembly.
            
            resp = v.authenticate(SUB_USER, SUB_USER);
            assertTrue("Could not authenticate into ns: " + SUBSCRIBER_NS_NAME + " :: " + resp.getErrors(),
                       resp.isSuccess());
            resp = v.execute("Broker.connect",
                             Map.of("token", tokenString, "uri", TEST_SERVER,"useVQS", false));
            assertTrue("Unable to connect to catalog:" + resp.getErrors(), resp.isSuccess());
            resp = v.execute("Broker.getAllAssemblies", Map.of("catalogName", CATALOG_NAME));
            assertTrue("Could not list assemblies: " + resp.getErrors(), resp.isSuccess());
            List<JsonElement> assemblies = ((JsonArray) resp.getBody()).asList();
            assertEquals("Wrong number of assemblies in catalog", 1, assemblies.size());
            for (Object assembly : assemblies) {
                log.debug("Found assembly: {}", ((JsonObject) assembly).get("name"));
                log.trace("Found assembly: {}", assembly);
            }
            Map<String, ?> targetAssembly = assemblies.get(0).getAsJsonObject().asMap();
            assertEquals("Found wrong assembly: " +
                                 ((JsonElement) targetAssembly.get("name")).getAsString(),
                         ((JsonElement) targetAssembly.get("name")).getAsString(),
                         NATLANG_ASSY_NAME);
            if (assemblyConfig != null) {
                installAssembly(v, assemblyConfig);
            }
        } else {
            fail("Have not yet setup running in other environments");
        }
    }
    
    public static void installAssembly(Vantiq v, Map<String, ?> assemblyConfig) {
        log.debug("Using assembly configuration: {}", assemblyConfig);
        VantiqResponse resp = v.execute("Subscriber.installAssembly", Map.of("assemblyName", NATLANG_ASSY_NAME,
                                                                             "catalogName", CATALOG_NAME,
                                                                             "configuration", assemblyConfig));
        assertTrue("Could not install our assembly: " + resp.getErrors(), resp.isSuccess());
        log.debug("Result of assembly installation: {}", resp);
    }
    
    public static void uninstallAssembly(Vantiq v) {
        VantiqResponse resp = v.execute("Subscriber.uninstallAssembly",
                                        Map.of("assemblyName", NATLANG_ASSY_NAME));
        assertTrue("Could not uninstall our assembly: " + resp.getErrors(), resp.isSuccess());
    }
    
    @AfterClass
    public static void teardownEnv() {
        performTeardown();
    }
    
    public static void performTeardown() {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        if (CREATE_TEST_NAMESPACE) {
            v.authenticate("system", "fxtrt$1492");
            VantiqResponse resp = v.delete("system.namespaces", CATALOG_NAMESPACE);
            assertTrue("Error encountered deleting NS: " + resp.getErrors(), resp.isSuccess());
            resp = v.delete("system.namespaces", SUBSCRIBER_NAMESPACE);
            assertTrue("Error encountered deleting NS: " + resp.getErrors(), resp.isSuccess());
        } else {
            fail("Have not yet setup running in other environments");
        }
    }
    
    public static VantiqResponse registerProcedure(Vantiq v, String procedure) {
        VantiqResponse vr = v.insert("system.procedures", procedure);
        log.debug("RegisterProcedure: {}", vr.getErrors());
        assert vr.isSuccess();
        return vr;
    }
    
    public static VantiqResponse deleteProcedure(Vantiq v, String procName) {
        return v.delete("system.procedures", procName);
    }
    
    public static String fetchFromFile(String fileName) {
        try {
            return Files.readString(Path.of("build/resources/test/" + fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Map fetchFromJsonFile(String fileName) {
        String contents = fetchFromFile(fileName);
        return new Gson().fromJson(contents, Map.class);
    }
    
}
