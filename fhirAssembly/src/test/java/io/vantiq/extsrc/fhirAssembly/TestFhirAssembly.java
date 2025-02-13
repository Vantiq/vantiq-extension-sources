package io.vantiq.extsrc.fhirAssembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;

import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test operations against a FHIR server using fhirConnection assembly
 */
@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestFhirAssembly {
    public static final String ASSEMBLY_FILE_NAME = "fhirConnection-assembly.zip";
    public static final boolean CREATE_CATALOG =
            Boolean.parseBoolean(System.getProperty("createCatalog", "true"));
    public static final boolean CREATE_TEST_NAMESPACE =
            Boolean.parseBoolean(System.getProperty("createNS", "true"));
    public static final String CATALOG_NS_NAME = System.getProperty("catalogNS", "fhirCatalog");
    public static final String CATALOG_NAME = "testFhirCatalog";
    public static final String SUBSCRIBER_NS_NAME = System.getProperty("subscriberNS", "fhirUser");
    public static final String TEST_SERVER = System.getProperty("vantiqTestServer", "http://localhost:8080");
    public static final String FHIR_SERVER = System.getProperty("fhirServer", "http://localhost:8090/fhir/");
    public static Map<String, String> CATALOG_NAMESPACE = Map.of("namespace", CATALOG_NS_NAME);
    public static Map<String, String> SUBSCRIBER_NAMESPACE = Map.of("namespace", SUBSCRIBER_NS_NAME);
    public static final String FHIR_ASSY_NAME = "com.vantiq.fhir.fhirConnection";
    public static String SUB_USER;
    public static String PUB_USER;
    
    public static Integer BASE_ENCOUNTER_COUNT = 842;
    // Used as a base for history configurations
    public static String startTime = null;
    public ObjectMapper mapper = new ObjectMapper();
    
    public static String heroId = null;
    public static Map<String, ?> heroMap = null;
    public static String heroUpdateEtag = null;
    public static String batGirlId = null;
    public static Map<String, String> placeholderToLocation = new HashMap<>();
    static int invocationCount = 0;
    
    
    // Playing this little indirection since the methods are static (Junit Rules) but we want to override.  Copying a
    // bunch of code just to test different situations is not attractive.  JUnit's @BeforeClass will ensure that only
    // one instance of a named method is run, so that suffices for our needs.
    
    @BeforeClass
    public static void setupEnv() {
        performSetup(getAssemblyConfigForTest());
    }
    
    public static Map<String,?> getAssemblyConfigForTest() {
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", FHIR_SERVER);
        log.debug("Returning assembly config of: {}", config);
        return config;
    }
    
    public static void performSetup(Map<String, ?> assemblyConfig) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        startTime = sdf.format(new Date());
        SUB_USER = "admin__" + SUBSCRIBER_NS_NAME;
        PUB_USER = "admin__" + CATALOG_NS_NAME;
        File assyZip = new File("build/distributions/fhirConnection-assembly.zip");
        assertTrue("Cannot find assembly file from build: " + assyZip.getAbsolutePath(), assyZip.exists());
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        if (CREATE_TEST_NAMESPACE) {
            v.authenticate("system", "fxtrt$1492");
            VantiqResponse resp = v.insert("system.namespaces", CATALOG_NAMESPACE);
            assertTrue("Error encountered creating NS: " + resp.getErrors(), resp.isSuccess());
            resp = v.insert("system.namespaces", SUBSCRIBER_NAMESPACE);
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
            resp = v.upload(assyZip, "application/zip", FHIR_ASSY_NAME);
            assertTrue("Could not create document entry: " + resp.getErrors() +
                               " for file " + assyZip.getAbsolutePath(),
                       resp.isSuccess());
            resp = v.execute("Broker.createAssembly",
                             Map.of("catalogName", CATALOG_NAME,
                                    "assemblyName", FHIR_ASSY_NAME,
                                    "zipFileName", FHIR_ASSY_NAME));
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
                             Map.of("token", tokenString, "uri", "http://localhost:8080","useVQS", false));
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
                         FHIR_ASSY_NAME);
            log.debug("Using assembly configuration: {}", assemblyConfig);
            resp = v.execute("Subscriber.installAssembly", Map.of("assemblyName", FHIR_ASSY_NAME,
                                                                  "catalogName", CATALOG_NAME,
                                                                  "configuration", assemblyConfig));
            assertTrue("Could not install our assembly: " + resp.getErrors(), resp.isSuccess());
        } else {
            fail("Have not yet setup running in other environments");
        }
    }
    
    @AfterClass
    public static void teardownEnv() {
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
        assertTrue("Not enough invocations of searchType() -- count: " + invocationCount,
                   invocationCount >= 2);
    }
    
    // used in some subclasses.
    public static void registerGetConfiguredSource(Vantiq v) {
        String procedure =
                "PROCEDURE getConfiguredSource(sourceName)\n" +
                        "return SELECT ONE FROM sources WITH configure = true where name == sourceName";
        
        VantiqResponse vr = v.insert("system.procedures", procedure);
        assert vr.isSuccess();
    }
    
    @Test
    public void test000CapabilityFetch() {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        Map<String, ?> capStmt = null;
        for (int i = 0; i < 2; i++) {
            VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.getCapabilityStatement",
                                            Collections.emptyList());
            assertTrue("Could not fetch capabilities: " + resp.getErrors(), resp.isSuccess());
            Map<String, ?> newCapStmt = ((JsonObject) resp.getBody()).asMap();
            assertEquals("Not a capability statement",
                         "CapabilityStatement",
                         ((JsonElement) newCapStmt.get("resourceType")).getAsString());
            if (capStmt != null) {
                assertEquals("Capability stmts not equal", capStmt, newCapStmt);
            }
            capStmt = newCapStmt;
        }
        log.debug("Found Capability statement with {} entries", capStmt.size());
        log.trace("Found Capabilities: {}", capStmt);
    }
    
    @Test
    public void test100Read() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        // We have to do these searches because 1) the "read" interaction requires an id, and 2) id assignment in the
        // server is not consistent/repeatable.  So we'll hunt them down by birthday...
        
        Map<String, ?>  berg = findTarget(v, "Patient", Map.of("birthdate", "1957-05-06" ));
        Map<String, ?> lang = findTarget(v, "Patient", Map.of( "birthdate","2011-03-11"));
        fetchAndCheckPatient(v, berg.get("id").toString(), "Bergstrom287");
        fetchAndCheckPatient(v, lang.get("id").toString(), "Lang846");
    }
    
    @Test
    public void test120ReadWithGeneralParams() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        // We have to do these searches because 1) the "read" interaction requires an id, and 2) id assignment in the
        // server is not consistent/repeatable.  So we'll hunt them down by birthday...
        
        Map<String, ?>  berg = findTarget(v, "Patient", Map.of("birthdate", "1957-05-06" ));
        Map<String, ?> lang = findTarget(v, "Patient", Map.of( "birthdate","2011-03-11"));
        fetchAndCheckPatient(v, berg.get("id").toString(), "Bergstrom287",
                                Map.of("_elements", "name,birthDate"));
        fetchAndCheckPatient(v, lang.get("id").toString(), "Lang846");
    }
    
    @Test
    public void test150ReadError() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        Map<String, ?> resAsMap = fetchResourceUnchecked(v, "Patient", "ImALittleTeaPot");
        
        assertEquals("Wrong resource type returned", "OperationOutcome",
                     resAsMap.get("resourceType"));
        //noinspection unchecked
        List<Map<String, ?>> issues = (List<Map<String, ?>>) resAsMap.get("issue");
        Map<String, ?> one = issues.get(0);
        assertEquals("Wrong severity", "error", one.get("severity"));
        assertEquals("Wrong status for invalid id", "processing", one.get("code"));
        assertEquals("Wrong diagnostics invalid id",
                     "HAPI-2001: Resource Patient/ImALittleTeaPot is not known",
                     one.get("diagnostics").toString());
    }
    
    void fetchAndCheckPatient(Vantiq v, String id, String name) throws Exception {
        fetchAndCheckPatient(v, id, name, null);
    }
    
    void fetchAndCheckPatient(Vantiq v, String id, String name, Map<String, ?> gp) throws Exception {
        Map<String, ?> res = fetchResource(v, "Patient", id, gp);
        //noinspection unchecked
        String pName =
                ((List<Map<String, ?>>) res.get("name")).get(0).get("family").toString();
        assertEquals("Wrong Patient for id: " + id, pName, name);
        if (gp != null) {
            if (gp.containsKey("_elements")) {
                List<String> expKeySet = new ArrayList<>(List.of(((String) gp.get("_elements")).split(",")));
                checkPropertySet(expKeySet, res);
            }
        }
    }
    
    Map<String, ?> fetchResource(Vantiq v, String type, String id, Map<String, ?> gp) throws Exception {
        Map<String, ?> res = fetchResourceUnchecked(v, type, id, gp);
        
        assertEquals("Wrong resource type returned", type,
                     res.get("resourceType"));
        return res;
    }
    
    Map<String, ?> fetchResourceUnchecked(Vantiq v, String type, String id) throws Exception {
        return fetchResourceUnchecked(v, type, id, null);
    }
    
    Map<String, ?> fetchResourceUnchecked(Vantiq v, String type, String id, Map<String, ?> gp) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("id", id);
        if (gp != null) {
            Map<String, ?> mods = Map.of("generalParams", gp);
            params.put("modifiers", mods);
        }
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.read", params);
        assertTrue("Could not read resource: " + resp.getErrors(), resp.isSuccess());
        log.debug("Read {} {} response: {}", type, id, resp.getBody());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        //noinspection unchecked
        return (Map<String, ?>) fhirResp.get("body");
    }
    
    /*
     * [
     *    {
     *       "resourceType": "Bundle",
     *       "id": "6159ad21-9637-43ba-aef4-974643ebef34",
     *       "meta": {
     *          "lastUpdated": "2025-01-25T00:21:05.565+00:00"
     *       },
     *       "type": "searchset",
     *       "total": 1,
     *       "link": [
     *          {
     *             "relation": "self",
     *             "url": "http://localhost:8090/fhir/Patient?birthdate=1957-05-06"
     *          }
     *       ],
     *       "entry": [
     *          {
     *             "fullUrl": "http://localhost:8090/fhir/Patient/8480",
     *             "resource": {
     *                "resourceType": "Patient",
     *                "id": "8480",
     *                "meta": {
     *                   "versionId": "1",
     *                   "lastUpdated": "2025-01-25T00:17:10.404+00:00",
     *                   "source": "#j47JF9DQ2Fw5DAHK"
     *                },
     *                "text": {
     *                   "status": "generated",
     *                   "div": "<div xmlns=\"http://www.w3.org/1999/xhtml\">Generated by <a href=\"https://github.com/synthetichealth/synthea\">Synthea</a>.Version identifier: v2.4.0-404-ge7ce2295\n .   Person seed: -6567364183639478705  Population seed: 0</div>"
     *                },
     *                "extension": [
     *                   {
     *                      "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race",
     *                      "extension": [
     *                         {
     *                            "url": "ombCategory",
     *                            "valueCoding": {
     *                               "system": "urn:oid:2.16.840.1.113883.6.238",
     *                               "code": "2106-3",
     *                               "display": "White"
     *                            }
     *                         },
     *                         {
     *                            "url": "text",
     *                            "valueString": "White"
     *                         }
     *                      ]
     *                   },
     *                   {
     *                      "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity",
     *                      "extension": [
     *                         {
     *                            "url": "ombCategory",
     *                            "valueCoding": {
     *                               "system": "urn:oid:2.16.840.1.113883.6.238",
     *                               "code": "2186-5",
     *                               "display": "Not Hispanic or Latino"
     *                            }
     *                         },
     *                         {
     *                            "url": "text",
     *                            "valueString": "Not Hispanic or Latino"
     *                         }
     *                      ]
     *                   },
     *                   {
     *                      "url": "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
     *                      "valueString": "Coretta598 Legros616"
     *                   },
     *                   {
     *                      "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex",
     *                      "valueCode": "M"
     *                   },
     *                   {
     *                      "url": "http://hl7.org/fhir/StructureDefinition/patient-birthPlace",
     *                      "valueAddress": {
     *                         "city": "Revere",
     *                         "state": "Massachusetts",
     *                         "country": "US"
     *                      }
     *                   },
     *                   {
     *                      "url": "http://synthetichealth.github.io/synthea/disability-adjusted-life-years",
     *                      "valueDecimal": 0.5117897586364106
     *                   },
     *                   {
     *                      "url": "http://synthetichealth.github.io/synthea/quality-adjusted-life-years",
     *                      "valueDecimal": 23.48821024136359
     *                   }
     *                ],
     *                "identifier": [
     *                   {
     *                      "system": "https://github.com/synthetichealth/synthea",
     *                      "value": "7e3221f0-593e-4d91-8b1d-0d478d97fb2e"
     *                   },
     *                   {
     *                      "type": {
     *                         "coding": [
     *                            {
     *                               "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *                               "code": "MR",
     *                               "display": "Medical Record Number"
     *                            }
     *                         ],
     *                         "text": "Medical Record Number"
     *                      },
     *                      "system": "http://hospital.smarthealthit.org",
     *                      "value": "7e3221f0-593e-4d91-8b1d-0d478d97fb2e"
     *                   },
     *                   {
     *                      "type": {
     *                         "coding": [
     *                            {
     *                               "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *                               "code": "SS",
     *                               "display": "Social Security Number"
     *                            }
     *                         ],
     *                         "text": "Social Security Number"
     *                      },
     *                      "system": "http://hl7.org/fhir/sid/us-ssn",
     *                      "value": "999-85-8099"
     *                   },
     *                   {
     *                      "type": {
     *                         "coding": [
     *                            {
     *                               "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *                               "code": "DL",
     *                               "display": "Driver's License"
     *                            }
     *                         ],
     *                         "text": "Driver's License"
     *                      },
     *                      "system": "urn:oid:2.16.840.1.113883.4.3.25",
     *                      "value": "S99963923"
     *                   },
     *                   {
     *                      "type": {
     *                         "coding": [
     *                            {
     *                               "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *                               "code": "PPN",
     *                               "display": "Passport Number"
     *                            }
     *                         ],
     *                         "text": "Passport Number"
     *                      },
     *                      "system": "http://standardhealthrecord.org/fhir/StructureDefinition/passportNumber",
     *                      "value": "X88685755X"
     *                   }
     *                ],
     *                "name": [
     *                   {
     *                      "use": "official",
     *                      "family": "Bergstrom287",
     *                      "given": [
     *                         "Walter473"
     *                      ],
     *                      "prefix": [
     *                         "Mr."
     *                      ]
     *                   }
     *                ],
     *                "telecom": [
     *                   {
     *                      "system": "phone",
     *                      "value": "555-905-7110",
     *                      "use": "home"
     *                   }
     *                ],
     *                "gender": "male",
     *                "birthDate": "1957-05-06",
     *                "deceasedDateTime": "1982-02-15T19:35:26-05:00",
     *                "address": [
     *                   {
     *                      "extension": [
     *                         {
     *                            "url": "http://hl7.org/fhir/StructureDefinition/geolocation",
     *                            "extension": [
     *                               {
     *                                  "url": "latitude",
     *                                  "valueDecimal": 42.04890500042543
     *                               },
     *                               {
     *                                  "url": "longitude",
     *                                  "valueDecimal": -71.3078807918413
     *                               }
     *                            ]
     *                         }
     *                      ],
     *                      "line": [
     *                         "483 Johnson Mews Unit 27"
     *                      ],
     *                      "city": "North Attleborough",
     *                      "state": "Massachusetts",
     *                      "country": "US"
     *                   }
     *                ],
     *                "maritalStatus": {
     *                   "coding": [
     *                      {
     *                         "system": "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus",
     *                         "code": "S",
     *                         "display": "Never Married"
     *                      }
     *                   ],
     *                   "text": "Never Married"
     *                },
     *                "multipleBirthBoolean": false,
     *                "communication": [
     *                   {
     *                      "language": {
     *                         "coding": [
     *                            {
     *                               "system": "urn:ietf:bcp:47",
     *                               "code": "en-US",
     *                               "display": "English"
     *                            }
     *                         ],
     *                         "text": "English"
     *                      }
     *                   }
     *                ]
     *             },
     *             "search": {
     *                "mode": "match"
     *             }
     *          }
     *       ]
     *    }
     * ]
     */
    Map<String, ?> findTarget(Vantiq v, String type, Map<String, ?> query) throws Exception {
        VantiqResponse resp;
        // Alternate calling via GET (the default) vs. POST.  Both methods are allowed, some servers apparently have
        // a preference one vs. the other.  We don't care, but want to allow our customers to use the one that suits
        // them best.
        if ((invocationCount++ % 2) == 0) {
             resp = v.execute("com.vantiq.fhir.fhirService.searchType",
                              Map.of("type", type,
                                     "query", query));
        } else {
            resp = v.execute("com.vantiq.fhir.fhirService.searchType",
                             Map.of("type", type,
                                    "query", query,
                                    "method", "POST"));
        }
        assertTrue("Could not perform searchType: " + resp.getErrors(), resp.isSuccess());
        log.trace("Search Type {} {} response: {}", type, query, resp.getBody());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        //noinspection unchecked
        Map<String, ?> bundle = (Map<String, ?>) fhirResp.get("body");
        assertEquals("Wrong result type: ", "Bundle", bundle.get("resourceType"));
        assertEquals("Wrong entry count", 1, bundle.get("total"));
        //noinspection unchecked
        Map<String, ?> one = (Map<String, ?>) ((List<Map<String, ?>>) bundle.get("entry")).get(0).get("resource");
        assertEquals("Wrong resource type from search type", type, one.get("resourceType"));
        return one;
    }
    
    Map<String, ?> extractFhirResponse(VantiqResponse resp) throws Exception {
        log.debug("extract FHIR Response from response: {}", resp.getBody());
        //noinspection unchecked
        Map<String, ?> fhirResp = mapper.readValue(new Gson().toJson(((JsonObject) resp.getBody())),
                                                            Map.class);
        assertNotNull("Null FHIR Response during extraction", fhirResp);
        assertTrue("Invalid status code type: " + fhirResp.get("statusCode").getClass().getName(),
                   fhirResp.get("statusCode") instanceof Integer);
        assertTrue("Invalid fhirResp.body type: " + fhirResp.get("body").getClass().getName(),
                   fhirResp.get("body") instanceof Map);
        assertTrue("Invalid result headers type: " + fhirResp.get("headers").getClass().getName(),
                   fhirResp.get("headers") instanceof Map);
        return fhirResp;
    }
    
    @Test
    public void test200Create() throws Exception {
        File ourHeroFile = new File("build/resources/test/ourHero.json");
        //noinspection unchecked
        Map<String, ?> ourHeroMap = mapper.readValue(ourHeroFile, Map.class);
        log.debug("Our hero: {}", ourHeroMap);
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.create",
                  Map.of("type", "Patient", "resource", ourHeroMap));
        assertTrue("Could not create our hero: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        // 201 statu means a new thingy was created
        assertEquals("Unexpected return status: " + fhirResp.get("statusCode"), 201,
                     fhirResp.get("statusCode"));
        log.debug("Created Patient : {}", fhirResp.get("body"));
        //noinspection unchecked
        assertEquals("Wrong resource type returned", "Patient",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType").toString());
        //noinspection unchecked
        String insertedHeroId =
                ((Map<String, ?>) fhirResp.get("body")).get("id").toString();
        log.debug("Our hero's id: {}", insertedHeroId);
        Gson gson = new Gson();
        String insertedHero = gson.toJson(fhirResp.get("body"));
        log.trace("Our hero as string: {}", insertedHero);
        //noinspection unchecked
        Map<String, ?> insertedHeroMap = mapper.readValue(insertedHero, Map.class);
        heroId = insertedHeroId;
        heroMap = insertedHeroMap;
    }
    
    @Test
    public void test210CreateConditional() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        Map<String, ?> modifiers = Map.of("headers",
                                          Map.of("If-None-Exist", "name=" + "Man"));
        
        // Now, create our here again, but conditional on it's not being already there.
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.create",
                                        Map.of("type", "Patient",
                                               "id", heroId,
                                               "resource", heroMap,
                                               "modifiers", modifiers));
        assertTrue("Could not create our hero: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("Create Conditional FHIR response : {}", fhirResp);
        // 200 is returned when things "worked" as intended, but it wasn't created.
        // We expect this here because we sent a modifiers saying to only create if there's no match,
        assertEquals("Unexpected return status" + fhirResp.get("statusCode"), 200,
                     fhirResp.get("statusCode"));
        // Now, make sure we have only a single entry in the server
        traverseSearch(v, 20, "Patient",  Map.of("name", "Man"), 1);
    }
    
    @Test
    public void test300Update() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        
        // Now, make an update.  Change the last name to MaGillicuddy
        //noinspection unchecked
        ((List<Map<String, String>>) heroMap.get("name")).get(0).put("family", "McGillicuddy");
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.update",
                                        Map.of("type", "Patient",
                                               "id", heroId,
                                               "resource", heroMap));
        assertTrue("Could not update our hero: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("FHIR Response: {}", fhirResp);
        //noinspection unchecked
        String eTag = ((Map<String, ?>) fhirResp.get("headers")).get("ETag").toString();
        heroUpdateEtag = eTag;
        Gson gson = new Gson();
        String updatedHero = gson.toJson(fhirResp.get("body"));
        log.trace("Our updated hero as string: {}", updatedHero);
        //noinspection unchecked
        Map<String, ?> updatedHeroMap = mapper.readValue(updatedHero, Map.class);
        
        log.debug("Updated Hero : {}", updatedHero);
        assertEquals("Wrong resource type returned", "Patient",
                     updatedHeroMap.get("resourceType"));
        //noinspection unchecked
        assertEquals("Wrong family name post update", "McGillicuddy",
                    ((List<Map<String, ?>>) heroMap.get("name")).get(0).get("family"));
    }
    
    @Test
    @Ignore("This does not seem to be supported (it's ignored) by the HAPI FHIR server.")
    public void test310UpdateEtag() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        assertNotNull("No saved Etag", heroUpdateEtag);
        // Now, make an update.  Change the last name to Boy
        //noinspection unchecked
         ((List<Map<String, String>>) heroMap.get("name")).get(0).put("family", "Boy");
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        Map<String, ?> modifiers = Map.of("headers",
                                          Map.of("If-None-Match", "*"));
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.update",
                                        Map.of("type", "Patient",
                                               "id", heroId,
                                               "resource", heroMap,
                                               "modifiers", modifiers));

        assertTrue("Could not update our hero: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("FHIR Response: {}", fhirResp);
        assertEquals("Wrong status code: " + fhirResp.get("statusCode"), 200,
                     fhirResp.get("statusCode"));
        //noinspection unchecked
        String eTag = ((Map<String, ?>) fhirResp.get("headers")).get("ETag").toString();
        assertNotEquals("Etags match when they should not", heroUpdateEtag, eTag);
        Gson gson = new Gson();
        String updatedHero = gson.toJson(fhirResp.get("body"));
        log.trace("Our updated hero as string: {}", updatedHero);
        //noinspection unchecked
        Map<String, ?> updatedHeroMap = mapper.readValue(updatedHero, Map.class);
        
        log.debug("Updated Hero : {}", updatedHero);
        assertEquals("Wrong resource type returned", "Patient",
                     updatedHeroMap.get("resourceType"));
        //noinspection unchecked
        assertEquals("Wrong family name post update", "McGillicuddy",
                     ((List<Map<String, ?>>) heroMap.get("name")).get(0).get("family"));
    }
    
    @Test
    public void test320Patch() throws Exception {
        Map<String, ?> makeHimATwin = Map.of("op", "replace",
                                  "path", "/multipleBirthBoolean",
                                  "value", true);
        Map<String, ?> removeLanguage = Map.of("op", "remove",
                                                "path", "/communication");
        List<Map<String, ?>> patchCommands = List.of(
                makeHimATwin,
                removeLanguage
                );
                
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        assertNotNull("No saved Etag", heroUpdateEtag);
        
        assertEquals("Our hero should not be a multiple: " + heroMap, false,
                     heroMap.get("multipleBirthBoolean"));
        assertNotNull("Our Hero should have communication field: " + heroMap, heroMap.get("communication"));
        // Now, make an updates via patch
        //noinspection unchecked
        ((List<Map<String, String>>) heroMap.get("name")).get(0).put("family", "Boy");
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        Map<String, ?> modifiers = Map.of("headers",
                                          Map.of("If-None-Match", "*"));
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.patch",
                                        Map.of("type", "Patient",
                                               "id", heroId,
                                               "patchCommands", patchCommands,
                                               "modifiers", modifiers));
        assertTrue("Could not patch our hero: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("patch FHIR Response: {}", fhirResp);
        assertEquals("Wrong status code: " + fhirResp.get("statusCode"), 200,
                     fhirResp.get("statusCode"));
        Map<String, ?> twinHero = fetchResource(v, "Patient", heroId, null);
        assertEquals("Our hero should be a multiple: " + twinHero, true,
                     twinHero.get("multipleBirthBoolean"));
        assertNull("Our Hero should have no communication field: " + twinHero, twinHero.get("communication"));
    }
    
    @Test
    public void test350Transaction() throws Exception {
        File bgTransactionFile = new File("build/resources/test/bgXact.json");
        //noinspection unchecked
        Map<String, ?> bgXactMap = mapper.readValue(bgTransactionFile, Map.class);
        log.debug("New hero: {}", bgXactMap);
        //noinspection unchecked
        List<Map<String, ?>> entries = (List<Map<String, ?>>) bgXactMap.get("entry");
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.bundleInteraction",
                                        Map.of("bundle", bgXactMap));
        assertTrue("Could not create bat girl: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        // 201 statu means a new thingy was created
        assertEquals("Unexpected return status: " + fhirResp.get("statusCode"), 200,
                     fhirResp.get("statusCode"));
        log.debug("Created bat girl and associated resouce instances: {}", fhirResp.get("body"));
        //noinspection unchecked
        assertEquals("Wrong resource type returned", "Bundle",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType").toString());
        //noinspection unchecked
        assertEquals("Wrong bundle type returned", "transaction-response",
                     ((Map<String, ?>) fhirResp.get("body")).get("type").toString());
        
        //noinspection unchecked
        Map<String, ?> resultBundleMap = (Map<String, ?>) fhirResp.get("body");
        //noinspection unchecked
        assertEquals("Wrong number of results: " + resultBundleMap.get("entry"), 30,
                     ((List<Map<String, ?>>) resultBundleMap.get("entry")).size());
        
        //noinspection unchecked
        List<Map<String, ?>> outcomes = ((List<Map<String, ?>>) resultBundleMap.get("entry"));
        Map<String, ?> ptOutcome = outcomes.get(0);
        //noinspection unchecked
        String bgLocation = ((Map<String, ?>) ptOutcome.get("response")).get("location").toString();
        String[] locArray = bgLocation.split("/");
        assertEquals("First response is not a Patient: " + ptOutcome, "Patient", locArray[0]);
        String bgId = locArray[1];

        log.debug("Bat Girl's id: {}", bgId);
        log.trace("Bat Girl as string: {}", ptOutcome);
        batGirlId = bgId;
        // Now, map all the placeholder URL to the locations returned.  We'll use this in the batch test to refer
        // back to what was created.
        for (int i = 0; i < outcomes.size(); i++ ) {
            //noinspection unchecked
            log.debug("Transaction adding conversion for {} --> {}",
                      entries.get(i).get("fullUrl"),
                      instanceIdSansHistory((String) ((Map<String, ?>) outcomes.get(i).get("response")).get("location")));
            
            //noinspection unchecked
            placeholderToLocation.put((String) entries.get(i).get("fullUrl"),
                                      instanceIdSansHistory((String) ((Map<String, ?>) outcomes.get(i).get("response"))
                                                                                                .get("location")));
        }
        log.debug("Placeholder to Location map: {}", placeholderToLocation);
    }
    
    private String instanceIdSansHistory(String fullUrl) {
        String[] locArray = fullUrl.split("/");
        assert locArray.length >= 2;
        String retVal = locArray[0] + "/" + locArray[1];
        assertNotNull("Bad conversion for " + fullUrl, retVal);
        return retVal;
    }
    
    @Test
    public void test360Batch() throws Exception {
        assertNotNull("No batgirl id found", batGirlId);
        String patientFullUrl = "Patient" + "/" + batGirlId;
        File bgTransactionFile = new File("build/resources/test/bgBatch.json");
        //noinspection unchecked
        Map<String, ?> bgBatchMap = mapper.readValue(bgTransactionFile, Map.class);
        //noinspection unchecked
        List<Map<String, ?>> entries = (List<Map<String, ?>>) bgBatchMap.get("entry");
        
        //noinspection unchecked
        List<Map<String, ?>> items = ((List<Map<String, ?>>) bgBatchMap.get("entry"));
        int urlsReplaced = 0;
        for (Map<String, ?> item: items) {
            log.debug("Checking {} for url to replace", item);
            // These are not else cases since there may be multiple references in an instance.  An encounter has a
            // patient reference, their doctor(s), their facility, their organization, etc.
            //noinspection unchecked
            if (((Map<String, ?>) item.get("resource")).containsKey("subject") &&
                    ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                            .get("subject")).containsKey("reference")) {
                //noinspection unchecked
                String refKey = ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("subject")).get("reference");
                assertNotNull("No real location for placeholder: " + refKey,
                              placeholderToLocation.get(refKey));
                //noinspection unchecked
                ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("subject")).put("reference", placeholderToLocation.get(refKey));
                urlsReplaced += 1;
            }
            //noinspection unchecked
            if (((Map<String, ?>) item.get("resource")).containsKey("encounter") &&
                ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("encounter")).containsKey("reference")) {
                //noinspection unchecked
                String refKey = ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("encounter")).get("reference");
                assertNotNull("No real location for placeholder: " + refKey,
                              placeholderToLocation.get(refKey));
                //noinspection unchecked
                ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("encounter")).put("reference", placeholderToLocation.get(refKey));
                urlsReplaced += 1;
            }
            //noinspection unchecked
            if (((Map<String, ?>) item.get("resource")).containsKey("requester") &&
                    ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                            .get("requester")).containsKey("reference")) {
                //noinspection unchecked
                String refKey = ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("requester")).get("reference");
                assertNotNull("No real location for placeholder: " + refKey,
                              placeholderToLocation.get(refKey));
                //noinspection unchecked
                ((Map<String, String>) ((Map<String, ?>) item.get("resource"))
                        .get("requester")).put("reference", placeholderToLocation.get(refKey));
                urlsReplaced += 1;
            }
        }
        log.debug("Replaced {} urls.", urlsReplaced);
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.bundleInteraction",
                                        Map.of("bundle", bgBatchMap));
        assertTrue("Could not run batch: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        // 201 statu means a new thingy was created
        assertEquals("Unexpected return status: " + fhirResp.get("statusCode"), 200,
                     fhirResp.get("statusCode"));
        log.debug("Created batch data : {}", fhirResp.get("body"));
        //noinspection unchecked
        assertEquals("Wrong resource type returned", "Bundle",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType").toString());
        //noinspection unchecked
        assertEquals("Wrong bundle type returned", "batch-response",
                     ((Map<String, ?>) fhirResp.get("body")).get("type").toString());
        
        //noinspection unchecked
        Map<String, ?> resultBundleMap = (Map<String, ?>) fhirResp.get("body");
        //noinspection unchecked
        assertEquals("Wrong number of results: " + resultBundleMap.get("entry"), items.size(),
                     ((List<Map<String, ?>>) resultBundleMap.get("entry")).size());
        // Now, map all the placeholder URL to the locations returned.  We'll use this in the batch test to refer
        // back to what was created.
        //noinspection unchecked
        List<Map<String, ?>> outcomes = ((List<Map<String, ?>>) resultBundleMap.get("entry"));
        for (int i = 0; i < outcomes.size(); i++ ) {
            //noinspection unchecked
            log.debug("Batch: Adding conversion for {} --> {}",
                      entries.get(i).get("fullUrl"),
                      instanceIdSansHistory((String) ((Map<String, ?>) outcomes.get(i).get("response")).get("location")));
            //noinspection unchecked
            placeholderToLocation.put((String) entries.get(i).get("fullUrl"),
                                      instanceIdSansHistory((String) ((Map<String, ?>) outcomes.get(i).get("response"))
                                            .get("location")));
        }
        log.debug("After batch operations, placeholder to Location map: {}", placeholderToLocation);
    }
    
    @Test
    public void test380HistoryIndividuals() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No batgirl id found", batGirlId);
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        Map<String, Integer> toQuery = Map.of(heroId, 3, batGirlId, 1);
        for (Map.Entry<String, Integer> ent : toQuery.entrySet()) {
            log.debug("Querying Patient {}", ent);
            VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.history",
                                            Map.of("type", "Patient",
                                                   "id", ent.getKey()));
            assertTrue("Could not get history for Patient id: " + ent + ": " + resp.getErrors(),
                       resp.isSuccess());
            Map<String, ?> fhirResp = extractFhirResponse(resp);
            log.debug("FHIR Response for history for Patient {}: {}", ent.getKey(), fhirResp);
            assertTrue("Wrong status code returned for Patient " + ent,
                       (int) fhirResp.get("statusCode") < 300);
            //noinspection unchecked
            assertEquals("Wrong resource type", "Bundle",
                         ((Map<String, ?>) fhirResp.get("body")).get("resourceType"));
            //noinspection unchecked
            assertEquals("Wrong type", "history",
                         ((Map<String, ?>) fhirResp.get("body")).get("type"));
            //noinspection unchecked
            assertEquals("Wrong history count", ent.getValue(),
                         ((Map<String, ?>) fhirResp.get("body")).get("total"));
        }
    }
    
    @Test
    public void test400Delete() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        Map<String, Object> toDelete = Map.of(heroId, 200, batGirlId, 409);
        for (Map.Entry<String, Object> ent: toDelete.entrySet()) {
            log.debug("Deleting Patient {}", ent);
            VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.delete",
                                            Map.of("type", "Patient",
                                                   "id", ent.getKey()));
            assertTrue("Could not delete id: " + ent + ": " + resp.getErrors(), resp.isSuccess());
            Map<String, ?> fhirResp = extractFhirResponse(resp);
            assertEquals("Wrong status code returned for " + ent, ent.getValue(), fhirResp.get("statusCode"));
            
            // Now, verify the expected state.  Really a FHIR check, but we'll do it anyway to ensure we return the
            // right status & data
            
            Map<String, ?> resAsMap = fetchResourceUnchecked(v, "Patient", ent.getKey());
            log.debug("Result of fetch of {}: {}", ent, resAsMap);
            if (ent.getValue().equals(200)) {
                assertEquals("Wrong resource type returned", "OperationOutcome",
                             resAsMap.get("resourceType"));
//{
//            "resourceType": "OperationOutcome",
//            "issue": [
//              {
//                "severity": "information",
//                "code": "informational",
//                "details": {
//                "coding": [
//                   {
//                    "system": "https://hapifhir.io/fhir/CodeSystem/hapi-fhir-storage-response-code",
//                    "code": "SUCCESSFUL_DELETE_NOT_FOUND",
//                    "display": "Delete succeeded: No existing resource was found so no action was taken."
//                   }
//                 ]
//              },
//             "diagnostics": "Not deleted, resource Patient/bm12345678 does not exist."
//            }
//          ]
//        }
                //noinspection unchecked
                List<Map<String, ?>> issues = (List<Map<String, ?>>) resAsMap.get("issue");
                Map<String, ?> one = issues.get(0);
                assertEquals("Wrong severity", "error", one.get("severity"));
                assertEquals("Wrong status post delete", "processing", one.get("code"));
                assertTrue("Wrong diagnostics post delete",
                           one.get("diagnostics").toString().startsWith("Resource was deleted"));
            } else if (ent.getValue().equals(409)) {
                // Then our delete didn't happen for referential integrity reasons.
                // We should get a Patient back
                assertEquals("Wrong resource type returned", "Patient",
                             resAsMap.get("resourceType"));
            }
        }
    }
    
    @Test
    public void test450TransactionalDelete() throws Exception {
        // Note:  Has to be done in a transaction.  Batches cannot have references between the entities in the batch,
        // and all of these things are interelated.
        assertFalse("No placeholder to location data found: " + placeholderToLocation,
                    placeholderToLocation.isEmpty());
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        Map<String, Object> xactDelete = new HashMap<>();

        xactDelete.put("resourceType", "Bundle");
        xactDelete.put("type", "transaction");
        xactDelete.put("id", "test450BatchDelete-request");
        List<Map<String, Object>> entList = new ArrayList<>(placeholderToLocation.size());
        xactDelete.put("entry", entList);
        for (Map.Entry<String, String> p2l: placeholderToLocation.entrySet()) {
            Map<String, Object> ent = new HashMap<>();
            ent.put("request", Map.of("method", "DELETE",
                                      "url", p2l.getValue()));
            entList.add(ent);
        }

        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.bundleInteraction",
                                        Map.of("bundle", xactDelete));
        assertTrue("Could not process batch delete: " + entList + ": " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("Batch delete response: {}", fhirResp);
        //noinspection unchecked
        Map<String, ?> resultBundleMap = (Map<String, ?>) fhirResp.get("body");
        // Now, map all the placeholder URL to the locations returned.  We'll use this in the batch test to refer
        // back to what was created.
        //noinspection unchecked
        List<Map<String, ?>> outcomes = ((List<Map<String, ?>>) resultBundleMap.get("entry"));
        for (Map<String, ?> outcome: outcomes) {
            //noinspection unchecked
            String opResult = (String) ((Map<String, ?>) outcome.get("response"))
                    .get("status");
            String[] resArray = opResult.split("\\s+");
            int status = Integer.parseInt(resArray[0]);
            assertTrue("Wrong status code returned for " + outcome,status < 300);
        }
    }
    
    @Test
    public void test500SearchUnrestrictedPatient() throws Exception {
        
        int countSize = 20;
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        // We added bat girl, so one mor ethan originally inserted
        traverseSearch(v, countSize, "Patient", Collections.emptyMap(), 28);
    }
    
    @Test
    public void test501SearchUnrestrictedEncounter() throws Exception {
        
        int countSize = 20;
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        traverseSearch(v, countSize, "Encounter", Collections.emptyMap(), BASE_ENCOUNTER_COUNT);
    }
    
    @Test
    public void test502SearchPatientQual() throws Exception {
        Map<Map<String, ?>, Integer> searches = Map.of(
                Map.of("name", "Lesch175"), 1,
                Map.of("name", "Mr."), 14,
                Map.of("gender:not","female"), 20);
        
        int countSize = 20;
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        searches.forEach ( (key, val) -> {
            try {
                traverseSearch(v, countSize, "Patient", key, val);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    @Test
    public void test550SearchPatientModifiers() throws Exception {
        Map<Map<String, ?>, Integer> searches = Map.of(
                Map.of("name", "Lesch175"), 1,
                Map.of("name", "Mr."), 14,
                Map.of("gender:not","female"), 20);
        
        Map<String, ?> genParams = Map.of (
                "_count", 3, // Page size of 3
                "_sort", "name",
                "_elements", "name, birthDate"
        );
        Map<String, ?> modifiers = Map.of("generalParams", genParams);
        
        int countSize = (int) genParams.get("_count");
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        searches.forEach ( (key, val) -> {
            try {
                traverseSearch(v, countSize, "Patient", key, val, modifiers);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    @Test
    public void test600HistoryType() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.history",
                                        Map.of("type", "Encounter"));
        assertTrue("Could not get history for Encounter: " + resp.getErrors(),
                   resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("FHIR Response for history for Encounter: {}", fhirResp);
        assertTrue("Wrong status code returned for Encounter",
                   (int) fhirResp.get("statusCode") < 300);
        //noinspection unchecked
        assertEquals("Wrong resource type", "Bundle",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType"));
        //noinspection unchecked
        assertEquals("Wrong type", "history",
                     ((Map<String, ?>) fhirResp.get("body")).get("type"));
        // History here is tricky -- even deletes end up with history.  So we'll check that the total returned
        // exceeds the base number of encounters.  Kinda the best we can do.  Otherwise, we're checking that the FHIR
        // server is working -- and that's not our job.
        //noinspection unchecked
        int expectedTotal = (Integer) ((Map<String, ?>) fhirResp.get("body")).get("total");
        assertTrue("Low Encounter/history count: " + expectedTotal,
                   expectedTotal > BASE_ENCOUNTER_COUNT);
    }
    
    @Test
    public void test610HistoryTypeModifiers() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.history",
                                        Map.of("type", "Encounter",
                                               "modifiers",
                                                        Map.of("generalParams",
                                                               Map.of("_since", startTime,
                                                                      "_sort", "_lastUpdated"))));
        assertTrue("Could not get history for Encounter (_at): " + resp.getErrors(),
                   resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        log.debug("FHIR Response for history for Encounter(_at): {}", fhirResp);
        assertTrue("Wrong status code returned for Encounter",
                   (int) fhirResp.get("statusCode") < 300);
        //noinspection unchecked
        assertEquals("Wrong resource type", "Bundle",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType"));
        //noinspection unchecked
        assertEquals("Wrong type", "history",
                     ((Map<String, ?>) fhirResp.get("body")).get("type"));
        // History here is tricky -- even deletes end up with history.  So we'll check that the total returned
        // exceeds the base number of encounters.  Kinda the best we can do.  Otherwise, we're checking that the FHIR
        // server is working -- and that's not our job.
        //noinspection unchecked
        if (((Map<String, ?>) fhirResp.get("body")).containsKey("total")) {
            //noinspection unchecked
            int expectedTotal = (Integer) ((Map<String, ?>) fhirResp.get("body")).get("total");
            assertTrue("Low Encounter/history count: " + expectedTotal,
                       expectedTotal > BASE_ENCOUNTER_COUNT);
        } else {
            // Returning the total doesn't seem to happen (with this server) when a modifier is added.
            // Consequently, the best we can do is to ensure that we have some results...
            //noinspection unchecked
            assertTrue("No results for Encounter(_since) request",
                       ((Map<String, ?>) fhirResp.get("body")).containsKey("entry"));
        }
    }
    
    
    @Test
    public void test620HistorySystem() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.history",
                                        Collections.emptyMap());
        assertTrue("Could not get history for everything: " + resp.getErrors(),
                   resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        assertTrue("Wrong status code returned for everything",
                   (int) fhirResp.get("statusCode") < 300);
        //noinspection unchecked
        assertEquals("Wrong resource type", "Bundle",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType"));
        //noinspection unchecked
        assertEquals("Wrong type", "history",
                     ((Map<String, ?>) fhirResp.get("body")).get("type"));
        // History here is tricky -- even deletes end up with history.  So we'll check that the total returned
        // exceeds the base number of encounters.  Kinda the best we can do.  Otherwise, we're checking that the FHIR
        // server is working -- and that's not our job.
        //noinspection unchecked
        int expectedTotal = (Integer) ((Map<String, ?>) fhirResp.get("body")).get("total");
        log.debug("History total for everything: {}", expectedTotal);
        assertTrue("Low everything /history count: " + expectedTotal,
                   expectedTotal > 1000);
    }
    
    @Test
    public void test630HistorySystemModifiers() throws Exception {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.history",
                                            Map.of("modifiers",
                                                Map.of("generalParams",
                                                       Map.of("_since", startTime))));
        assertTrue("Could not get history for everything: " + resp.getErrors(),
                   resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        assertTrue("Wrong status code returned for everything",
                   (int) fhirResp.get("statusCode") < 300);
        //noinspection unchecked
        assertEquals("Wrong resource type", "Bundle",
                     ((Map<String, ?>) fhirResp.get("body")).get("resourceType"));
        //noinspection unchecked
        assertEquals("Wrong type", "history",
                     ((Map<String, ?>) fhirResp.get("body")).get("type"));
        // History here is tricky -- even deletes end up with history.  So we'll check that the total returned
        // exceeds the base number of encounters.  Kinda the best we can do.  Otherwise, we're checking that the FHIR
        // server is working -- and that's not our job.
        //noinspection unchecked
        if (((Map<String, ?>) fhirResp.get("body")).containsKey("total")) {
            //noinspection unchecked
            int expectedTotal = (Integer) ((Map<String, ?>) fhirResp.get("body")).get("total");
            assertTrue("Low everything/history count: " + expectedTotal,
                       expectedTotal > BASE_ENCOUNTER_COUNT);
        } else {
            // Returning the total doesn't seem to happen (with this server) when a modifier is added.
            // Consequently, the best we can do is to ensure that we have some results...
            //noinspection unchecked
            assertTrue("No results for everytning(_since) request",
                       ((Map<String, ?>) fhirResp.get("body")).containsKey("entry"));
        }
    }
    
    void traverseSearch(Vantiq v, int bundleSize, String type, Map<String, ?> query, int expectedCount)
            throws Exception {
        traverseSearch(v, bundleSize, type, query, expectedCount, null);
    }
    
    public final static List<String> FHIR_INTERNAL_KEYS = List.of("resourceType", "id", "meta");
    
    void traverseSearch(Vantiq v, int bundleSize, String type, Map<String, ?> query, int expectedCount,
                    Map<String, ?> modifiers) throws Exception {
        VantiqResponse resp;
        String nextUrl;
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("type", type);
        params.put("query", query);
        if ((invocationCount++ % 2) ==0) {
            params.put("method", "POST");
        }
        if (modifiers != null) {
            params.put("modifiers", modifiers);
        }
        resp = v.execute("com.vantiq.fhir.fhirService.searchType", params);
        
        int totalResourceCount = 0;
        List<Map<String, ?>> sortCheck = new ArrayList<>();
        int initialSearchAttempt = 1;
        boolean gotEntries = false;
        Map<String, ?> bundleEntry;  // Keep this around after the loop for some cursory testing...
        do {
            log.debug("Response: {}", resp);
            Map<String, ?> fhirResp = extractFhirResponse(resp);
            log.debug("FHIRResponse: {}", fhirResp);
            //noinspection unchecked
            bundleEntry = (Map<String, ?>) fhirResp.get("body");
            log.debug("Bundle is {}", bundleEntry);
            
            assertEquals("Wrong resource type", "Bundle", bundleEntry.get("resourceType"));
            assertEquals("Wrong lower type", "searchset", bundleEntry.get("type"));
            //noinspection unchecked
            List<Map<String, ?>> entrySet = (List<Map<String, ?>>) bundleEntry.get("entry");
            if (entrySet != null) {
                // There seems to be a bug whereby the HAPI FHIR servers sometimes returns incomplete search results.
                // When we encounter this, it's a HAPI server bug, not ours.  We'll try repeating the search & see if
                // things improve
                
                gotEntries = true;
                
                assertNotNull("No entry set returned: " + bundleEntry, entrySet);
                assertTrue("Wrong entry count in bundle: expected <= " + bundleSize +
                                   ", but found entry count: " + entrySet.size(),
                           bundleSize >= entrySet.size());
                List<String> expKeySet = new ArrayList<>();
                if (modifiers != null && modifiers.containsKey("_elements")) {
                    expKeySet.addAll(List.of(((String) modifiers.get("_elements")).split(",")));
                }
                for (Map<String, ?> entry : entrySet) {
                    //noinspection unchecked
                    Map<String, ?> resource = ((Map<String, ?>) entry.get("resource"));
                    assertEquals("Wrong resource type in list", type,
                                 resource.get("resourceType"));
                    checkPropertySet(expKeySet, resource);
                    
                    if (modifiers != null && modifiers.containsKey("_sort")) {
                        //noinspection unchecked
                        sortCheck.add((Map<String, ?>) ((List<?>) resource.get("name")).get(0));
                    }
                    totalResourceCount += 1;
                }
                
                //noinspection unchecked
                List<Map<String, ?>> links = (List<Map<String, ?>>) bundleEntry.get("link");
                assertNotNull("Bundle should have links", links);
                nextUrl = null;
                for (Map<String, ?> aLink : links) {
                    if (aLink.get("relation").equals("next")) {
                        nextUrl = (String) aLink.get("url");
                        break;
                    }
                }
                log.debug("Next url is {}", nextUrl);
                if (nextUrl != null) {
                    resp = v.execute("com.vantiq.fhir.fhirService.returnLink", Map.of("link", nextUrl));
                }
            } else {
                log.debug("No entry set returned, trying again.  Count: {}", initialSearchAttempt);
                initialSearchAttempt += 1;
                nextUrl = "http://somewhere"; // Shouldn't be used, but keeps the loop running.
                resp = v.execute("com.vantiq.fhir.fhirService.searchType", params);
            }
        } while (nextUrl != null && initialSearchAttempt < 5);

        if (!gotEntries) {
            // Then things didn't work as expected.
            // In these cases, our search purported worked (we'll check here), but we didn't get results back.  This
            // seems to be a bug in the HAPI server.  See HAPI server issue:
            // https://github.com/hapifhir/hapi-fhir-jpaserver-starter/issues/383, specifically this comment
            // https://github.com/hapifhir/hapi-fhir-jpaserver-starter/issues/383#issuecomment-1146376048
            
            // When that happens, we'll make a more cursory test.  But we don't want to fail _our_ tests since this
            // isn't a Vantiq issue.
            
            int searchResultCount = (int) bundleEntry.get("total");
            // We test this separately just so we know that it's the more cursory test.
            assertEquals("Wrong (cursory) total resource count from search.",
                         expectedCount, searchResultCount);
        } else {
            if (!sortCheck.isEmpty()) {
                log.trace("SortCheck: {}", sortCheck);
                String lastSeen = null;
                for (Map<String, ?> oneName : sortCheck) {
                    if (lastSeen != null) {
                        String nextOne = (String) oneName.get("family");
                        assertTrue("Family names in wrong order: " + lastSeen + " should be <= " + nextOne,
                                   lastSeen.compareTo(nextOne) <= 0);
                    } else {
                        lastSeen = (String) oneName.get("family");
                    }
                }
            }
            assertEquals("Wrong total resource count", expectedCount, totalResourceCount);
        }
    }
    
    void checkPropertySet(List<String> expKeySet, Map<String, ?> resource) {
        if (!expKeySet.isEmpty()) {
            expKeySet.forEach( (k) -> {
                assertTrue("Property " + k.trim() + " not in resource: " + resource,
                           resource.containsKey(k.trim()));
            });
            // Check that we got only what we expect.  The FHIR_INTERNAL_KEYS are always there...
            // But we could have fewer if one of the keys requested isn't in that particular item.
            assertTrue("Too many properties returned: expected " + expKeySet.size() +
                               ", found " + resource.keySet().size() + " in resource: " + resource,
                       resource.keySet().size() <= expKeySet.size() + FHIR_INTERNAL_KEYS.size());
        }
        
    }
}
