package io.vantiq.extsrc.fhirAssembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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
import java.util.ArrayList;
import java.util.Collections;
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
    public ObjectMapper mapper = new ObjectMapper();
    
    public static String heroId = null;
    public static Map heroMap = null;
    public static String heroUpdateEtag = null;
    static int invocationCount = 0;
    
    
    @BeforeClass
    public static void setupEnv() {
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
                log.trace("Found catalog data: {}", ((List) resp.getBody()).get(i));
                
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
            List assemblies = ((JsonArray) resp.getBody()).asList();
            assertEquals("Wrong number of assemblies in catalog", 1, assemblies.size());
            for (Object assembly : assemblies) {
                log.debug("Found assembly: {}", ((JsonObject) assembly).get("name"));
                log.trace("Found assembly: {}", assembly);
            }
            Map targetAssembly = ((JsonObject) assemblies.get(0)).asMap();
            assertEquals("Found wrong assembly: " +
                                 ((JsonElement) targetAssembly.get("name")).getAsString(),
                         ((JsonElement) targetAssembly.get("name")).getAsString(),
                         FHIR_ASSY_NAME);
            resp = v.execute("Subscriber.installAssembly", Map.of("assemblyName", FHIR_ASSY_NAME,
                                                                  "catalogName", CATALOG_NAME,
                                                                  "configuration",
                                                                    Map.of("fhirServerBaseUrl", FHIR_SERVER)));
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
    
    @Test
    public void test000CapabilityFetch() {
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        Map capStmt = null;
        for (int i = 0; i < 2; i++) {
            VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.getCapabilityStatement",
                                            Collections.emptyList());
            assertTrue("Could not fetch capabilities: " + resp.getErrors(), resp.isSuccess());
            Map<String, ?> newCapStmt = ((JsonObject) resp.getBody()).asMap();
            assertEquals("Not a capability statement",
                         "CapabilityStatement"
,                         ((JsonElement) newCapStmt.get("resourceType")).getAsString());
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
        Map<String, ?> fhirResp = mapper.readValue(new Gson().toJson(((JsonObject) resp.getBody())),
                                                            Map.class);
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
        Map ourHeroMap = mapper.readValue(ourHeroFile, Map.class);
        log.debug("Our hero: {}", ourHeroMap);
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.create",
                  Map.of("type", "Patient", "resource", ourHeroMap));
        assertTrue("Could not create our hero: " + resp.getErrors(), resp.isSuccess());
        Map<String, ?> fhirResp = extractFhirResponse(resp);
        // 201 statu means a new thingy was created
        assertEquals("Unexpected return status" + fhirResp.get("statusCode"), 201,
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
        log.debug("Create FHIR response : {}", fhirResp);
        // 200 is returned when things "worked" as intended, but it wasn't created.
        // We expect this here because we sent a modifiers saying to only create if there's no match,
        assertEquals("Unexpected return status" + fhirResp.get("statusCode"), 200,
                     fhirResp.get("statusCode"));
        // Now, make sure we have only a single here
        traverseSearch(v, 20, "Patient",  Map.of("name", "Man"), 1);
        
    }
    
    @Test
    public void test300Update() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        
        // Now, make an update.  Change the last name to MaGillicuddy
        //noinspection unchecked
        ((Map) ((List) heroMap.get("name")).get(0)).put("family", "McGillicuddy");
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
        ((Map) ((List) heroMap.get("name")).get(0)).put("family", "Boy");
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
    public void test400Delete() throws Exception {
        assertNotNull("No inserted hero id found", heroId);
        assertNotNull("No inserted here map found", heroMap);
        
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.delete",
                                        Map.of("type", "Patient",
                                               "id", heroId));
        assertTrue("Could not delete our hero: " + resp.getErrors(), resp.isSuccess());
        // Now, verify that he's gone.  Really a FHIR check, but we'll do it anyway
        
        Map<String, ?> resAsMap = fetchResourceUnchecked(v, "Patient", heroId);
        
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
    }
    
    @Test
    public void test500SearchUnrestrictedPatient() throws Exception {
        
        int countSize = 20;
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        traverseSearch(v, countSize, "Patient", Collections.emptyMap(), 28);
    }
    
    @Test
    public void test501SearchUnrestrictedEncounter() throws Exception {
        
        int countSize = 20;
        Vantiq v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        
        traverseSearch(v, countSize, "Encounter", Collections.emptyMap(), 842);
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
        
        do {
            log.trace("Response: {}", resp);
            Map<String, ?> fhirResp = extractFhirResponse(resp);
            log.trace("FHIRResponse: {}", fhirResp);
            //noinspection unchecked
            Map<String, ?> bundleEntry = (Map<String, ?>) fhirResp.get("body");
            log.trace("Bundle is {}", bundleEntry);
            
            assertEquals("Wrong resource type", "Bundle", bundleEntry.get("resourceType"));
            assertEquals("Wrong lower type", "searchset", bundleEntry.get("type"));
            //noinspection unchecked
            List<Map<String, ?>> entrySet = (List<Map<String, ?>>) bundleEntry.get("entry");
            assertTrue("Wrong entry count in bundle: expected <= " + bundleSize +
                               ", but found entry count: " + entrySet.size(),
                       bundleSize >= entrySet.size());
            List<String> expKeySet = new ArrayList<>();
            if (modifiers != null && modifiers.containsKey("_elements")) {
                expKeySet.addAll(List.of(((String) modifiers.get("_elements")).split(",")));
            }
            for (Map<String, ?> entry : entrySet) {
                //noinspection unchecked
                Map<String, ?> resource =  ((Map<String, ?>) entry.get("resource"));
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
            for (Map<String, ?> aLink: links) {
                if (aLink.get("relation").equals("next")) {
                    nextUrl = (String) aLink.get("url");
                    break;
                }
            }
            log.debug("Next url is {}",  nextUrl);
            if (nextUrl != null) {
                resp = v.execute("com.vantiq.fhir.fhirService.returnLink", Map.of("link", nextUrl));
            }
        } while (nextUrl != null);
        if (!sortCheck.isEmpty()) {
            log.trace("SortCheck: {}", sortCheck);
            String lastSeen = null;
            for (Map<String, ?> oneName: sortCheck) {
                if (lastSeen != null) {
                    String nextOne = (String) oneName.get("family");
                    assertTrue("Family names in wrong order: "  +lastSeen + " should be <= " + nextOne,
                               lastSeen.compareTo(nextOne) <= 0);
                } else {
                    lastSeen = (String) oneName.get("family");
                }
            }
        }
        assertEquals("Wrong total resource count", expectedCount, totalResourceCount);
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
