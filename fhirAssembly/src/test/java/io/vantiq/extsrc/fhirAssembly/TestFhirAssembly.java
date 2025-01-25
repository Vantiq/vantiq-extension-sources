package io.vantiq.extsrc.fhirAssembly;

import static org.junit.Assert.assertEquals;
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
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.util.Collections;
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
        Map<String, ?> res = fetchResource(v, "Patient", id);
        String pName =
                ((Map) ((List) res.get("name")).get(0))
                  .get("family").toString();
        assertEquals("Wrong Patient for id: " + id, pName, name);
    }
    
    Map<String, ?> fetchResource(Vantiq v, String type, String id) throws Exception {
        Map<String, ?> res = fetchResourceUnchecked(v, type, id);
        
        assertEquals("Wrong resource type returned", type,
                     res.get("resourceType"));
        return res;
    }
    
    Map<String, ?> fetchResourceUnchecked(Vantiq v, String type, String id) throws Exception {
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.read",
                                        Map.of("type", type,
                                               "id", id));
        assertTrue("Could not read resource: " + resp.getErrors(), resp.isSuccess());
        log.debug("Read {} {} response: {}", type, id, resp.getBody());
        //noinspection unchecked
        return mapper.readValue(new Gson().toJson(resp.getBody()), Map.class);
    }
    
    /**
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
     * @param v
     * @param type
     * @param query
     * @return
     */
    Map<String, ?> findTarget(Vantiq v, String type, Map<String, ?> query) throws Exception {
        VantiqResponse resp = v.execute("com.vantiq.fhir.fhirService.searchType",
                                        Map.of("type", type,
                                               "theQuery", query));
        assertTrue("Could not perform searchType: " + resp.getErrors(), resp.isSuccess());
        log.trace("Search Type {} {} response: {}", type, query, resp.getBody());
        //noinspection unchecked
        Map<String, ?> bundle = mapper.readValue(new Gson().toJson(((JsonArray) resp.getBody()).get(0)), Map.class);
        assertEquals("Wrong result type: ", "Bundle", bundle.get("resourceType"));
        assertEquals("Wrong entry count", 1, bundle.get("total"));
        //noinspection unchecked
        Map<String, ?> one = (Map<String, ?>) ((List<Map<String, ?>>) bundle.get("entry")).get(0).get("resource");
        assertEquals("Wrong resource type from search type", type, one.get("resourceType"));
        return one;
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
        log.debug("Created Patient : {}", resp.getBody());
        assertEquals("Wrong resource type returned", "Patient",
                     ((JsonObject) resp.getBody()).asMap().get("resourceType").getAsString());
        String insertedHeroId =
                ((JsonObject) resp.getBody()).get("id").getAsString();
        log.debug("Our hero's id: {}", insertedHeroId);
        Gson gson = new Gson();
        String insertedHero = gson.toJson(resp.getBody());
        log.trace("Our hero as string: {}", insertedHero);
        Map insertedHeroMap = mapper.readValue(insertedHero, Map.class);
        heroId = insertedHeroId;
        heroMap = insertedHeroMap;
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
        Gson gson = new Gson();
        String updatedHero = gson.toJson(resp.getBody());
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
}
