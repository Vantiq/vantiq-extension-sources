package io.vantiq.extsrc.naturallanguage;

import static java.util.Arrays.asList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CLUProcessingTest extends NatLangTestBase {
    private static int randomDatesNumericMonth() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        return cal.get(Calendar.MONTH) + 1;// These are zero based months -- need human convention here
    }

    private static int randomDatesNumericMonthYear() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        return cal.get(Calendar.YEAR);
    }

    private static int randomDatesNumericMonthNextYear() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.add(Calendar.YEAR, 1);
        return cal.get(Calendar.YEAR);
    }

    private static String randomStartOfLastMonthWithoutYear() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        // Let's go down a month so that the test will work on the 1st.
        // The problem we're avoiding is a test started @ 11:59 that crosses
        // the boundary.

        cal.add(Calendar.MONTH, -1);

        String firstMonthFix = "";
        int monthNumeric = cal.get(Calendar.MONTH);
        if (monthNumeric == Calendar.DECEMBER) {
            // Then "first of December" will mean next december.  So we'll add the year to set things right.
            int year = cal.get(Calendar.YEAR);
            firstMonthFix = " " + year;
        }


        return "1 " + cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US) + firstMonthFix;
    }

    private static String randomStartOfNextMonthWithoutYear() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.add(Calendar.MONTH, 1);// Let's go down a month so this test will work on the 1st.

        return "1 " + cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US);
    }

    private static void responseDescribePatientsSomeChecker(final Map resp) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(3);
        map.put("name", "String");
        map.put("room", "String");
        map.put("age", "Integer");
//        DefaultGroovyMethods.each(map, new Closure<Void>(null, null) {
//            public void doCall(final Object propPair) {
//                assert resp.contains.call(
//                    ((Map.Entry<String, String>) propPair).getKey() + " : " + ((Map.Entry<String, String>) propPair).getValue());
//            }
//
//        });
//        assert resp.contains.call("Properties for Patients...");
    }

    private static void patientResponseChecker(final Map resp) {
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("Zero", "One", "Two")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object num) {
//                                          assert resp.contains.call("name: Patient " + num);
//                                          assert resp.contains.call("name: APatient " + num);
//                                      }
//
//                                  });
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("000", "001", "002")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object num) {
//                                          assert resp.contains.call("room: Z" + num);
//                                          assert resp.contains.call("room: AZ" + num);
//                                      }
//
//                                  });
//        assert resp.contains.call("• There are 6 items of type Patients.");
    }

    private static void patientResponseCheckerCondition(final Map resp) {
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("Zero", "One", "Two")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object num) {
//                                          assert resp.contains.call("name: Patient " + num);
//                                          assert !DefaultGroovyMethods.asBoolean(
//                                              resp.contains.call("name: APatient " + num));
//                                      }
//
//                                  });
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("000", "001", "002")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object num) {
//                                          assert resp.contains.call("room: Z" + num);
//                                          assert !DefaultGroovyMethods.asBoolean(resp.contains.call("room: AZ" + num));
//                                      }
//
//                                  });
//        assert resp.contains.call("• There are 3 items of type Patients.");
    }

    private static void patientResponseCheckerConditionPunct(final Map resp) {
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("Zero", "One", "Two")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object num) {
//                                          assert resp.contains.call("name: Patient " + num);
//                                          assert !DefaultGroovyMethods.asBoolean(
//                                              resp.contains.call("name: APatient " + num));
//                                      }
//
//                                  });
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("000", "001", "002")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object num) {
//                                          assert resp.contains.call("room: Z" + num);
//                                          assert !DefaultGroovyMethods.asBoolean(resp.contains.call("room: AZ" + num));
//                                      }
//
//                                  });
//        assert resp.contains.call("• There are 3 items of type Patients.");
    }

    private static void patientResponseCheckerConditionAge(final Map resp) {
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("Zero", "One")), new Closure<Void>(null, null) {
//            public void doCall(Object num) {
//                assert resp.contains.call("name: Patient " + num);
//                assert !DefaultGroovyMethods.asBoolean(resp.contains.call("name: APatient " + num));
//            }
//
//        });
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("000", "001")), new Closure<Void>(null, null) {
//            public void doCall(Object num) {
//                assert resp.contains.call("room: Z" + num);
//                assert !DefaultGroovyMethods.asBoolean(resp.contains.call("room: AZ" + num));
//            }
//
//        });
//        assert resp.contains.call("• There are 2 items of type Patients.");
    }

    private static void activeCollaborationReponseChecker(final Map resp) {
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList(COLLABORATION_ONE, COLLABORATION_TWO)),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(Object nm) {
//                                          assert resp.contains.call("Name: " + nm);
//                                          assert resp.contains.call("Status: active");
//                                      }
//
//                                  });
    }

    private static void responseWhoIsSickChecker(final Map resp) {
//        resp.contains.call("I got a custom intent: health.patientsByCondition!");
//        DefaultGroovyMethods.each(new ArrayList<String>(Arrays.asList("Zero", "One", "Two")),
//                                  new Closure<Void>(null, null) {
//                                      public void doCall(final Object nm) {
//                                          assert resp.contains.call("Sick folks include Patient " + nm);
//                                      }
//
//                                  });
//        assert resp.contains.call("Total of 3 Patients");
    }

    @BeforeClass
    public static void setupCat() {
        NatLangTestBase.performSetup(Collections.EMPTY_MAP);
    }

    @Before
    public void setup() {
        v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        LinkedHashMap<Object, Object> newType = new LinkedHashMap<Object, Object>();
        newType.put("name", "Patients");
        LinkedHashMap<String, LinkedHashMap<String, Serializable>> map = new LinkedHashMap<String, LinkedHashMap<String, Serializable>>(
            3);
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(2);
        map1.put("type", "String");
        map1.put("required", true);
        map.put("name", map1);
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(2);
        map2.put("type", "String");
        map2.put("required", true);
        map.put("room", map2);
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(2);
        map3.put("type", "Integer");
        map3.put("required", true);
        map.put("age", map3);
        newType.put("properties", map);
        v.update("system.types", "Patients", newType);

        String fauxLuisProc = "\n" +
            "procedure NLTest.fauxLuisService(nlUtterance String)\n" +
            "\n// Here, we put in a simplistic NLI to appease the tests\n" +
            "\n//var theIntent = null\n" +
            "//var theEnts = null\n" +
            "var cluResp = null\n" +
            "if (nlUtterance.equals(\"" + UTTERANCE_BYE_BYE + "\")) {\n" +
            "    cluResp = " + CLU_BYE_BYE + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_COUNT_Patients + "\")) {\n" +
            "    cluResp = " + CLU_COUNT_Patients + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_DESCRIBE_Patients + "\")) {\n" +
            "    cluResp = " + CLU_DESCRIBE_Patients + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_LIST_Patients_CONDITION + "\")) {\n" +
            "    // \"list Patients whose name is greater than or equal to Patient\"\n" +
            "    // Within the list below, the \"is greater than or equal to\" will resolve to many comparators\n" +
            "    // the interpretation code should determine that all but one are occluded, resolving\n" +
            "    // to only the system.comparator_gte.  Which will be used for the query.\n" +
            "    cluResp = " + CLU_LIST_PATIENTS_CONDITION + "\n" +
            "         \n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_LIST_Patients_CONDITION_PUNCT + "\")) {\n" +
            "    // \"list Patients where name >= Patient\"\n" +
            "    // Within the list below, the \"is greater than or equal to\" will resolve to many comparators\n" +
            "    // the interpretation code should determine that all but one are occluded, resolving\n" +
            "    // to only the system.comparator_gte.  Which will be used for the query.\n" +
            "    cluResp = " + CLU_LIST_Patients_CONDITION_PUNCT + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_LIST_Patients_CONDITION_AGE + "\")) {\n" +
            "    // Here. we are primarily testing that the occlusion between system.propertyValue & builtin.number\n" +
            "    // is resolved correctly (in favor of system.propertyValue).\n" +
            "    cluResp = " + CLU_LIST_Patients_CONDITION_AGE + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_LIST_PATIENTS_LC + "\")) {\n" +
            "    cluResp = " + CLU_LIST_PATIENTS_LC + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_LIST_Patients + "\")) {\n" +
            "    cluResp = " + CLU_LIST_Patients + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_SHOW_ACTIVE_COLLABORATIONS + "\")){\n" +
            "    cluResp = " + CLU_SHOW_ACTIVE_COLLABORATIONS + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_SHOW_CURRENT_COLLABORATIONS + "\")){\n" +
            "    cluResp = " + CLU_SHOW_CURRENT_COLLABORATIONS + "\n" +
            "} else if (nlUtterance.equals(\"" + String.valueOf(
            UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE) + "\")){\n" +
            "        // These are statements of the form \"show active collaborations since 1 September\"\n" +
            "    var dateString = \"" + CLUProcessingTest.randomStartOfLastMonthWithoutYear() + "\"\n" +
            "    var dateLength = dateString.length() + " + String.valueOf("since ".length()) + "\n" +
            "    var dateEndIndex = 27 + dateLength - 1\n" +
            "\n    cluResp = " + CLU_SHOW_ACTIVE_COLLABORATIONS_SINCE + "\n" +
            "\n    var ents = cluResp.result.prediction.entities\n" +
            "    var entToFix\n" +
            "    for (e in ents until entToFix != null) {\n" +
            "        if (e.category.equalsIgnoreCase(\"dateTimeV2\")) {\n" +
            "            entToFix = e\n" +
            "        }\n" +
            "    }\n" +
            "    if (entToFix) {\n" +
            "        entToFix.text = \"since \" + dateString\n" +
            "        entToFix.length = dateLength\n" +
            "        entToFix.resolutions[0].start = \"" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonthYear()) + "-" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonth()) + "-01\"\n" +
            "        entToFix.resolutions[1].start = \"" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonthNextYear()) + "-" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonth()) + "-01\"\n" +
            "    } else {\n" +
            "        exception(\"test.setup.no.date.collabSince\", \"Did not find a dateTimeV2\", [])\n" +
            "    }\n" +
            "\n} else if (nlUtterance.equals(\"" + String.valueOf(
            UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE) + "\")){\n" +
            "        // These are statements of the form \"show active collaborations before 1 August\"\n" +
            "    var dateString = \"" + CLUProcessingTest.randomStartOfNextMonthWithoutYear() + "\"\n" +
            "    var dateLength = dateString.length() + " + String.valueOf("before ".length()) + "\n" +
            "    var dateEndIndex = 27 + dateLength - 1\n" +
            "    cluResp = " + CLU_SHOW_ACTIVE_COLLABORATIONS_BEFORE + "\n" +
            "    var ents = cluResp.result.prediction.entities\n" +
            "    var entToFix\n" +
            "    for (e in ents until entToFix != null) {\n" +
            "        if (e.category.equalsIgnoreCase(\"dateTimeV2\")) {\n" +
            "            entToFix = e\n" +
            "        }\n" +
            "    }\n" +
            "    if (entToFix) {\n" +
            "        entToFix.text = \"before \" + dateString\n" +
            "        entToFix.length = dateLength\n" +
            "        entToFix.resolutions[0].end = \"" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonthYear()) + "-" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonth()) + "-01\"\n" +
            "        entToFix.resolutions[1].end = \"" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonthNextYear()) + "-" + String.valueOf(
            CLUProcessingTest.randomDatesNumericMonth()) + "-01\"\n" +
            "    } else {\n" +
            "        exception(\"test.setup.no.date.collabBefore\", \"Did not find a dateTimeV2\", [])\n" +
            "    }\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS + "\")){\n" +
            "    // These are statements of the form \"show active collaborations before 1 August\"\n" +
            "    cluResp =  " + CLU_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_HI + "\")) {\n" +
            "    cluResp = " + CLU_HI + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_GOOD_AFTERNOON + "\")) {\n" +
            "    cluResp = " + CLU_GOOD_AFTERNOON + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_THANK_YOU + "\")) {\n" +
            "    cluResp = " + CLU_THANK_YOU + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_HOW_DID_YOU_GET_STARTED + "\")) {\n" +
            "    cluResp = " + CLU_HOW_DID_YOU_GET_STARTED + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_ARE_YOU_SAD + "\")) {\n" +
            "    cluResp = " + CLU_ARE_YOU_SAD + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_ARE_YOU_A_CHATBOT + "\")) {\n" +
            "    cluResp = " + CLU_ARE_A_CHATBOT + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_WHEN_IS_YOUR_BIRTHDAY + "\")) {\n" +
            "    cluResp = " + CLU_WHEN_IS_YOUR_BIRTHDAY + "\n" +
            "} else if (nlUtterance.equals(\"" + UTTERANCE_WHO_IS_SICK + "\")) {\n" +
            "    cluResp = " + CLU_WHO_IS_SICK + "\n" +
            "} else {\n" +
            "    cluResp = " + CLU_NONSENSE + "\n" +
            "}\n" +
            "\nreturn cluResp\n" +
            "        \n";
        log.debug("Faux Source----- \n{}\n-------", fauxLuisProc);
        NatLangTestBase.registerProcedure(v, fauxLuisProc);

        String testInterpretProc = "\n" +
            "procedure NLTest.fullInterpreter(nlUtterance String)\n" +
            "    var result\n" +
            "    try {\n" +
            "\n        // The last parameter adjusts askLuis's behavior (via intepretNLQuery) to handle a VAIL-procedure-based source\n" +
            "        result = NaturalLanguageCore.interpretConversationalQuery(nlUtterance, \"" + CLU_SOURCE_NAME + "\",\n" +
            "                        \"VantiqTutorial\", \"mig1\", " + String.valueOf(!useAzureSource) + ")\n" +
            "    } \n" +
            "    catch (error) {\n" +
            "        // Something went wrong.  Return the error\n" +
            "        result = error\n" +
            "    }\n" +
            "    return result\n";
        NatLangTestBase.registerProcedure(v, testInterpretProc);

        String badTestInterpretProc = "\n" +
            "procedure NLTest.badFullInterpreter(nlUtterance String)\n" +
            "    var result\n" +
            "    try {\n" +
            "        // 'true' as the last parameter adjusts askLuis's behavior (via intepretConversationalQuery) \n" +
            "        // to handle a VAIL-procedure-based source\n" +
            "        result = NaturalLanguageCore.interpretConversationalQuery(nlUtterance, \"IAmAServiceThatDoesNotExist\",\n" +
            "                        \"doesn't\", \"matter\", true)\n" +
            "    } \n" +
            "    catch (error) {\n" +
            "        // Something went wrong.  Return the error\n" +
            "        result = error\n" +
            "    }\n" +
            "    return result\n";
        NatLangTestBase.registerProcedure(v, badTestInterpretProc);


        String execIntentProc = "\n" +
            "procedure NLTest.testExecIntent(intentUnderTest Object)\n" +
            "    var result\n" +
            "    try {\n" +
            "        result = NaturalLanguageCore.executeSystemIntent(intentUnderTest)\n" +
            "    } \n" +
            "    catch (error) {\n" +
            "        // Something went wrong.  Return the error\n" +
            "        result = error\n" +
            "    }\n" +
            "    return result\n";
        NatLangTestBase.registerProcedure(v, execIntentProc);

        String userCustomProcessor = "\n" +
            "PROCEDURE NLTest.execCustomIntent(interpretation Object)\n" +
            "\nvar response = \"I got a custom intent: \" + interpretation.intent + \"!\\n" +
            "\\n" +
            "\"\n" +
            "var rowCount = 0\n" +
            "SELECT * FROM Patients as row {\n" +
            "    var thisRow = \"    Sick folks include \" + row.name + \"\\n" +
            "\"\n" +
            "    response += thisRow\n" +
            "    rowCount += 1\n" +
            "}\n" +
            "response += \"Total: \" + rowCount + \" Patients\"\n" +
            "interpretation.response = response\n" +
            "interpretation.isError = false\n" +
            "return interpretation\n";
        NatLangTestBase.registerProcedure(v, userCustomProcessor);

        String execCustomIntentProc = "\n" +
            "procedure NLTest.testExecCustomIntent(intentUnderTest Object)\n" +
            "    var result\n" +
            "    try {\n" +
            "            // Run the procedure defined just above\n" +
            "        result = NLTest.execCustomIntent(intentUnderTest)\n" +
            "    } \n" +
            "    catch (error) {\n" +
            "        // Something went wrong.  Return the error\n" +
            "        result = error\n" +
            "    }\n" +
            "    return result\n";
        NatLangTestBase.registerProcedure(v, execCustomIntentProc);

        String customCallProc = "\n" +
            "PROCEDURE NLTest.processUtterance(utterance String, languageService String, isProcedureBasedTest Boolean)\n" +
            "\n// Let's figure out if we can translate these into actions...\n" +
            "var response = \"something is very much amiss\"\n" +
            "\nif (isProcedureBasedTest == null) {\n" +
            "    isProcedureBasedTest = false\n" +
            "}\n" +
            "try {\n" +
            "    var interpretation = NaturalLanguageCore.interpretConversationalQuery(utterance, \"" + CLU_SOURCE_NAME + "\",\n" +
            "                                    \"VantiqTutorial\", \"mig1\", " + String.valueOf(
            !useAzureSource) + ")\n" +
            "    log.debug(\"ProcessUtterance() interpretation: {}\", [interpretation.stringify(true)])\n" +
            "    if (interpretation.errorMsg != null) {\n" +
            "        // Then, we had some error.  Let's just dump that as the response and move on\n" +
            "        log.debug(\"ProcessUtterance(): Found Error: {}\", [interpretation.errorMsg])\n" +
            "        response = interpretation.errorMsg\n" +
            "    } else if (interpretation.response.intent.startsWith(\"system.\")) {\n" +
            "        log.debug(\"ProcessUtterance():  Attempting interpretation of intent: {}\", [interpretation.response.intent])\n" +
            "        var interpretedString = NaturalLanguageCore.executeSystemIntent(interpretation.response)\n" +
            "        response = interpretedString.response\n" +
            "    } else { // if (!interp.response.intent.startsWith(\"system.\")) {\n" +
            "        exception(\"io.vantiq.testing.was.execCustomIntent\", \"Trying to call nonexistent NLTest.executeCustomIntent\", [])\n" +
            "    }\n" +
            "}\n" +
            "catch (error) {\n" +
            "    log.error(\"ProcessUtterance(): Error Encountered: \" + stringify(error))\n" +
            "    response = error.message\n" +
            "}\n" +
            "\nreturn response\n";
        NatLangTestBase.registerProcedure(v, customCallProc);


        String userCallProc = "\n" +
            "procedure NLTest.testUserCall(nlUtterance String)\n" +
            "    var result = \"something is very much amiss\"\n" +
            "    try {\n" +
            "        // 'true' as the last parameter adjusts askAzureClu's behavior (via intepretConversationalQuery) to handle a \n" +
            "        //  VAIL-procedure-based source\n" +
            "        result = NLTest.processUtterance(nlUtterance, \"" + CLU_SOURCE_NAME + "\", " + String.valueOf(
            !useAzureSource) + ")\n" +
            "    }\n" +
            "    catch (error) {\n" +
            "        // Something went wrong.  Return the error\n" +
            "        result = error\n" +
            "    }\n" +
            "    return result\n";
        NatLangTestBase.registerProcedure(v, userCallProc);

        //
        // Create a remote source that calls our test procedure (created above).  This is used to test basic operation
        // of the natural language systems.
        //
        final LinkedHashMap<Object, Object> source = new LinkedHashMap<Object, Object>();
        source.put("name", CLU_SOURCE_NAME);
        source.put("type", "REMOTE");
        if (useAzureSource) {
            // Here, we'll use a real azure source.
            LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(2);
            map4.put("uri",
                     "https://fcarter-clu-migration.cognitiveservices.azure.com/language/" + ":analyze-conversations?api-version=2022-10-01-preview");
            LinkedHashMap<String, LinkedHashMap<String, String>> map5 = new LinkedHashMap<String,
                    LinkedHashMap<String, String>>(
                1);
            LinkedHashMap<String, String> map6 = new LinkedHashMap<String, String>(1);
            map6.put("Ocp-Apim-Subscription-Key", azureCluSubscriptionKey);
            map5.put("headers", map6);
            map4.put("requestDefaults", map5);
            source.put("config", map4);
        } else {
            // Otherwise, the normal case, fake it
            LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(6);
            map4.put("uri", "http://localhost:8080/api/v1/resources");
            map4.put("accessToken", v.getAccessToken());
            map4.put("contentType", "application/json");
            map4.put("keepAliveInterval", 00);
            map4.put("connectionTimeout", 00);
            LinkedHashMap<String, String> map5 = new LinkedHashMap<String, String>(1);
            map5.put("path", "/api/v1/resources/procedures/NLTest.fauxLuisService");
            map4.put("requestDefaults", map5);
            source.put("config", map4);
        }

        log.debug("Updating source: {}", source);
        v.update("system.sources", source.get("name").toString(), source);
    }

    @Test
    public void testInvalidCLUService() {
        String utterance = "describe types";
        String expectedMessage = "of the sources resource could not be found.";

        VantiqResponse vr = v.execute("NLTest.badFullInterpreter", Map.of("nlUtterance", utterance));
        log.debug("Result: {}", vr);
        Assert.assertTrue("No Service should fail", vr.isSuccess());
        Map rawResult = ((JsonObject) vr.getBody()).asMap();
        
        assert rawResult.get("errorMsg").toString().contains(expectedMessage);

    }

    @Test
    public void testIntentDetermination() {

        checkNLExec(UTTERANCE_BYE_BYE, INTENT_BYE_BYE, ENTITIES_BYE_BYE, RESPONSE_BYE_BYE, IS_ERROR_BYE_BYE,
                    CANON_BYE_BYE);

        // Note: The following two differ in the case of Patients (vs. patients)
        checkNLExec(UTTERANCE_LIST_Patients, INTENT_LIST_Patients, ENTITIES_LIST_Patients, RESPONSE_LIST_Patients,
                    IS_ERROR_LIST_Patients, CANON_LIST_Patients);// So far, we have none of these types.

        checkNLExec(UTTERANCE_LIST_PATIENTS_LC, INTENT_LIST_PATIENTS_LC, ENTITIES_LIST_PATIENTS_LC,
                    RESPONSE_LIST_PATIENTS_LC, IS_ERROR_LIST_PATIENTS_LC, CANON_LIST_PATIENTS_LC);
        // End of errors

        checkNLExec(UTTERANCE_LIST_Patients_CONDITION_PUNCT, INTENT_LIST_Patients_CONDITION_PUNCT,
                    ENTITIES_LIST_Patients_CONDITION_PUNCT, RESPONSE_LIST_Patients_CONDITION_PUNCT,
                    IS_ERROR_LIST_Patients_CONDITION_PUNCT,
                    CANON_LIST_Patients_CONDITION_PUNCT);// So far, we have none of these types.

        checkNLExec(UTTERANCE_LIST_Patients_CONDITION_AGE, INTENT_LIST_Patients_CONDITION_AGE,
                    ENTITIES_LIST_Patients_CONDITION_AGE, RESPONSE_LIST_Patients_CONDITION_AGE,
                    IS_ERROR_LIST_Patients_CONDITION_AGE, CANON_LIST_Patients_CONDITION_AGE);

        // Note: The following two differ in the text.  The meaning of the condition is identical.
        // The queries, generally, are different.

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, CANON_SHOW_ACTIVE_COLLABORATIONS);

        checkNLExec(UTTERANCE_SHOW_CURRENT_COLLABORATIONS, INTENT_SHOW_CURRENT_COLLABORATIONS,
                    ENTITIES_SHOW_CURRENT_COLLABORATIONS, RESPONSE_SHOW_CURRENT_COLLABORATIONS,
                    IS_ERROR_SHOW_CURRENT_COLLABORATIONS, CANON_SHOW_CURRENT_COLLABORATIONS);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_BEFORE, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE, CANON_SHOW_ACTIVE_COLLABORATIONS_BEFORE);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE, INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_SINCE, RESPONSE_SHOW_CURRENT_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE, CANON_SHOW_ACTIVE_COLLABORATIONS_SINCE);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, RESPONSE_SHOW_CURRENT_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    CANON_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS);
        
        checkNLExec(UTTERANCE_HI, INTENT_HI, ENTITIES_HI, RESPONSE_HI, IS_ERROR_HI, CANON_HI);
    }

    @Test
    public void testQueryBasic() {
        try {
            checkNLExec(UTTERANCE_DESCRIBE_Patients, INTENT_DESCRIBE_Patients, ENTITIES_DESCRIBE_Patients,
                        (Object) new MethodClosure(this, "responseDescribePatientsSomeChecker"),
                        IS_ERROR_DESCRIBE_Patients, CANON_DESCRIBE_Patients);

            checkNLExec(UTTERANCE_COUNT_Patients, INTENT_COUNT_Patients, ENTITIES_COUNT_Patients,
                        RESPONSE_COUNT_Patients_NONE, IS_ERROR_COUNT_Patients, CANON_COUNT_Patients);

            // Let's create some actual data here.  Having done that, we'll issue NL queries to fetch it.

            loadPatients();

            // Now, we can issue the NL query to fetch these, and they should return the rows.
            // These are queries for Patients with a CAPITAL P.  It should fetch data.

            // Here, we've already saved the CLU output, so we wont pass it again
            checkNLExec(UTTERANCE_DESCRIBE_Patients, INTENT_DESCRIBE_Patients, ENTITIES_DESCRIBE_Patients,
                        (Object) new MethodClosure(this, "responseDescribePatientsSomeChecker"),
                        IS_ERROR_DESCRIBE_Patients, null);

            checkNLExec(UTTERANCE_COUNT_Patients, INTENT_COUNT_Patients, ENTITIES_COUNT_Patients,
                        RESPONSE_COUNT_Patients_SOME, IS_ERROR_COUNT_Patients, null);


            // Here, we create a closure that will check the response for validity.  A plain-ole string compare
            // won't work since there are dates, id's, etc. involved.

            checkNLExec(UTTERANCE_LIST_Patients, INTENT_LIST_Patients, ENTITIES_LIST_Patients,
                        (Object) new MethodClosure(this, "patientResponseChecker"), IS_ERROR_LIST_PATIENTS_LC, null);

            // Here, we're checking similar queries but with a condition.
            checkNLExec(UTTERANCE_LIST_Patients_CONDITION, INTENT_LIST_Patients_CONDITION,
                        ENTITIES_LIST_Patients_CONDITION,
                        (Object) new MethodClosure(this, "patientResponseCheckerCondition"),
                        IS_ERROR_LIST_Patients_CONDITION, CANON_LIST_Patients_CONDITION);

            checkNLExec(UTTERANCE_LIST_Patients_CONDITION_PUNCT, INTENT_LIST_Patients_CONDITION_PUNCT,
                        ENTITIES_LIST_Patients_CONDITION_PUNCT,
                        (Object) new MethodClosure(this, "patientResponseCheckerConditionPunct"),
                        IS_ERROR_LIST_Patients_CONDITION_PUNCT, null);

            checkNLExec(UTTERANCE_LIST_Patients_CONDITION_AGE, INTENT_LIST_Patients_CONDITION_AGE,
                        ENTITIES_LIST_Patients_CONDITION_AGE,
                        (Object) new MethodClosure(this, "patientResponseCheckerConditionAge"),
                        IS_ERROR_LIST_Patients_CONDITION_AGE, null);

            // The following should generate user errors
            // This is a query for lower-case patients.  No such type.
            checkNLExec(UTTERANCE_LIST_PATIENTS_LC, INTENT_LIST_PATIENTS_LC, ENTITIES_LIST_PATIENTS_LC,
                        RESPONSE_LIST_PATIENTS_LC, IS_ERROR_LIST_PATIENTS_LC, null);
            // End of errors
        } finally {
            // After the test, remove the type.  The other test depends upon it being absent.
            unloadPatients();
        }


        checkNLExec(UTTERANCE_COUNT_Patients, INTENT_COUNT_Patients, ENTITIES_COUNT_Patients,
                    RESPONSE_COUNT_Patients_NONE, IS_ERROR_COUNT_Patients, null);
    }

    @Test
    public void testQueryCollaborations() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        log.debug(">>> testQueryCollaborations::Date is: {}",
                  StringGroovyMethods.plus(cal.get(Calendar.DATE), "-") + cal.getDisplayName(Calendar.MONTH,
                                                                                             Calendar.LONG,
                                                                                             Locale.US) + "-" + cal.get(
                      Calendar.YEAR));

        Map collab1 = null;
        Map collab2 = null;

        // Now, let's create collaborations in various states, and see that we get them back OK

        LinkedHashMap<Object, Object> aCollab = new LinkedHashMap<Object, Object>();
        aCollab.put("name", COLLABORATION_ONE);
        aCollab.put("entities", Collections.emptyMap());
        aCollab.put("results", Collections.emptyMap());
        aCollab.put("status", "active");
        aCollab.put("id", UUID.randomUUID());

        VantiqResponse vr = v.insert("system.collaborations", aCollab);
        Map rawResult = ((JsonObject) vr.getBody()).asMap();
//        String jsonResult = JsonOutput.prettyPrint(((JsonObject) vr.getBody()).getAsString());
//        log.debug("full - result for utterance: {} :: \n{}\n:::::::::", utterance, jsonResult);
//        assert rawResult.size() == 2;

//            Map rslt = rawResult;
//            assert rslt.response != null;
//            Map resp = DefaultGroovyMethods.invokeMethod(mapper, "readValue", new Object[]{rslt.response, Map.class});
        
        collab1 = rawResult;

        aCollab = new LinkedHashMap<Object, Object>();
        aCollab.put("name", COLLABORATION_TWO);
        aCollab.put("entities", Collections.emptyMap());
        aCollab.put("results", Collections.emptyMap());
        aCollab.put("status", "active");
        aCollab.put("id", UUID.randomUUID());
        vr = v.insert("system.collaborations", aCollab);
        collab2 = ((JsonObject) vr.getBody()).asMap();
        
        
        // OK, now we have two active collaborations just created.  Let's run our query again to ensure we get them

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS,
                    (Object) new MethodClosure(this, "activeCollaborationReponseChecker"),
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, null);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    (Object) new MethodClosure(this, "activeCollaborationReponseChecker"),
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE, null);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE, INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    (Object) new MethodClosure(this, "activeCollaborationReponseChecker"),
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE, null);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    (Object) new MethodClosure(this, "activeCollaborationReponseChecker"),
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, null);


        // Now, let's update collab 1 to be closed

        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("id", collab1.get("id").toString());
        map.put("status", "complete");
        v.update("system.collaborations", DefaultGroovyMethods.asType(collab1.get("id"), String.class), map);
//        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
//                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS, this.&closedOneCollaborationResponseChecker,
//                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, null);
//        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
//                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
//                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, closedOneCollaborationResponseChecker,
//                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, null);


        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("id", collab2.get("id").toString());
        map1.put("status", "complete");
        v.update("system.collaborations", collab2.get("id").toString(), map1);

        // At this point, we should be back to the default state -- no collaborations
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, null);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, null);

        // Here, let's delete the collaborations & verify that we still get no answer.
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(1);
        map2.put("name", COLLABORATION_ONE);
        v.delete("system.collaborations", map2);
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(1);
        map3.put("name", COLLABORATION_TWO);
        v.delete("system.collaborations", map3);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, null);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_BEFORE, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE, null);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE, INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_SINCE, RESPONSE_SHOW_CURRENT_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE, null);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, RESPONSE_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, null);
    }

    @Test
    public void testNonsense() {
        checkNLExec(UTTERANCE_NONSENSE, INTENT_NONSENSE, ENTITIES_NONSENSE, RESPONSE_NONSENSE, IS_ERROR_NONSENSE,
                    CANON_NONSENSE);
    }

    @Test
    public void testSmalltalk() {
        checkNLExec(UTTERANCE_HI, INTENT_HI, ENTITIES_HI, RESPONSE_HI, IS_ERROR_HI, CANON_HI);

        checkNLExec(UTTERANCE_GOOD_AFTERNOON, INTENT_GOOD_AFTERNOON, ENTITIES_GOOD_AFTERNOON, RESPONSE_GOOD_AFTERNOON,
                    IS_ERROR_GOOD_AFTERNOON, CANON_GOOD_AFTERNOON);

        checkNLExec(UTTERANCE_ARE_YOU_SAD, INTENT_ARE_YOU_SAD, ENTITIES_ARE_YOU_SAD, RESPONSE_ARE_YOU_SAD,
                    IS_ERROR_ARE_YOU_SAD, CANON_ARE_YOU_SAD);

        checkNLExec(UTTERANCE_ARE_YOU_A_CHATBOT, INTENT_ARE_YOU_A_CHATBOT, ENTITIES_ARE_YOU_A_CHATBOT,
                    RESPONSE_ARE_YOU_A_CHATBOT, IS_ERROR_ARE_YOU_A_CHATBOT, CANON_ARE_YOU_A_CHATBOT);

        checkNLExec(UTTERANCE_HOW_DID_YOU_GET_STARTED, INTENT_HOW_DID_YOU_GET_STARTED, ENTITIES_HOW_DID_YOU_GET_STARTED,
                    RESPONSE_HOW_DID_YOU_GET_STARTED, IS_ERROR_HOW_DID_YOU_GET_STARTED, CANON_HOW_DID_YOU_GET_STARTED);

        checkNLExec(UTTERANCE_WHEN_IS_YOUR_BIRTHDAY, INTENT_WHEN_IS_YOUR_BIRTHDAY, ENTITIES_WHEN_IS_YOUR_BIRTHDAY,
                    RESPONSE_WHEN_IS_YOUR_BIRTHDAY, IS_ERROR_WHEN_IS_YOUR_BIRTHDAY, CANON_WHEN_IS_YOUR_BIRTHDAY);

        checkNLExec(UTTERANCE_THANK_YOU, INTENT_THANK_YOU, ENTITIES_THANK_YOU, RESPONSE_THANK_YOU, IS_ERROR_THANK_YOU,
                    CANON_THANK_YOU);
    }

    @Test
    public void testUserIntents() {
        try {
            loadPatients();
            checkNLExec(UTTERANCE_WHO_IS_SICK, INTENT_WHO_IS_SICK, ENTITIES_WHO_IS_SICK,
                        (Object) new MethodClosure(this, "responseWhoIsSickChecker"), IS_ERROR_WHO_IS_SICK,
                        CANON_WHO_IS_SICK, true);
        } finally {
            // After the test, remove the type.  The other test depends upon it being absent.
            unloadPatients();
        }

    }

    public void checkNLExec(Object utterance, Object desiredIntent, Object entities, Object expectedResponse, Object expectedIsError, String canonFile, Object useCustomCallProc) {

//        Object approvedIntent = null;
//        Map rawResult;
//        VantiqResponse vr = v.execute("NLTest.fullInterpreter", Map.of("nlUtterance", utterance));
//
//        rawResult = ((JsonObject) vr.getBody()).asMap();
//        String jsonResult = JsonOutput.prettyPrint(((JsonObject) vr.getBody()).getAsString());
//        log.debug("full - result for utterance: {} :: \n{}\n:::::::::", utterance, jsonResult);
//        assert rawResult.size() == 2;
//        if (DefaultGroovyMethods.asBoolean(rawResult.errorMsg)) {
//            assert rawResult.errorMsg instanceof String;
//        } else {
//            Map rslt = rawResult;
//            assert rslt.response != null;
//            Map resp = DefaultGroovyMethods.invokeMethod(mapper, "readValue", new Object[]{rslt.response, Map.class});
//            assert resp != null;
//            assert resp.rawIntent != null;
//
//            assert resp.query != null;
//            assert resp.query instanceof String;
//            if (!DATE_BASED_UTTERANCES.contains(utterance)) {
//                assert DefaultGroovyMethods.invokeMethod(resp.query, "equalsIgnoreCase", new Object[]{utterance});
//            }
//
//            assert resp.rawIntent.topIntent != null;
//            assert resp.rawIntent.topIntent.equals(desiredIntent);
//            assert DefaultGroovyMethods.find(resp.rawIntent.intents, new Closure<Boolean>(this, this) {
//                public Boolean doCall(Object it) {
//                    return it.category.equals(desiredIntent);
//                }
//
//                public Boolean doCall() {
//                    return doCall(null);
//                }
//
//            }) != null;
//            assert resp.score.equals(
//                DefaultGroovyMethods.find(resp.rawIntent.intents, new Closure<Boolean>(this, this) {
//                    public Boolean doCall(Object it) {
//                        return it.category.equals(desiredIntent);
//                    }
//
//                    public Boolean doCall() {
//                        return doCall(null);
//                    }
//
//                }).confidenceScore);
//            if (!DefaultGroovyMethods.invokeMethod(resp.entities, "size", new Object[0])
//                                     .equals(entities.invokeMethod("size", new Object[0]))) {
//                log.debug(">>> Response entities: {}",
//                          DefaultGroovyMethods.collect(resp.entities, new Closure(this, this) {
//                              public Object doCall(Object it) {
//                                  return it.name + ":" + it.type;
//                              }
//
//                              public Object doCall() {
//                                  return doCall(null);
//                              }
//
//                          }));
//                DefaultGroovyMethods.invokeMethod(log, "debug", new Object[]{">>> Expected entities: {}", entities});
//            }
//
//            if (utterance.equals(UTTERANCE_WHEN_IS_YOUR_BIRTHDAY)) {
//                // CLU picks up the 'is' as a comparator.  So we'll accept that...
//                assert DefaultGroovyMethods.invokeMethod(resp.entities, "size", new Object[0])
//                                           .equals(entities.invokeMethod("size", new Object[0]) + 1);
//            }
//
//            for (Object e : entities) {
//                // First, find the corresponding entry in the resp set
//                Object intentsE = DefaultGroovyMethods.find(resp.entities, new Closure<Boolean>(this, this) {
//                    public Boolean doCall(Object it) {
//                        return (it.name.equals(e.name) || it.type.equals(e.name));
//                    }
//
//                    public Boolean doCall() {
//                        return doCall(null);
//                    }
//
//                });
//                // && it.value == e.value }
//                log.debug(">>> Looking for {} in {}", e.name,
//                          DefaultGroovyMethods.collect(resp.entities, new Closure(this, this) {
//                              public Object doCall(Object it) {
//                                  return it.name + "::" + it.type;
//                              }
//
//                              public Object doCall() {
//                                  return doCall(null);
//                              }
//
//                          }));
//                Assert.assertNotNull("For query " + DefaultGroovyMethods.invokeMethod(String.class, "valueOf",
//                                                                                      new Object[]{utterance}) + ", unable to find expected ".plus(
//                    DefaultGroovyMethods.invokeMethod(String.class, "valueOf",
//                                                      new Object[]{e.name}) + " in returned " + String.valueOf(
//                        resp.entities)), intentsE);
//                assert intentsE.name.equals(e.name) || intentsE.type.equals(e.name);
//                // v3 returns these as a list
//                final Object value = (intentsE == null ? null : intentsE.value);
//                final Object var = (value == null ? null : value.class);
//                log.debug("Comparing {} ({}) with {}", intentsE.value, (var == null ? null : var.name), e.value);
//                if (intentsE.value instanceof List) {
//                    assert intentsE.value.invokeMethod("contains", new Object[]{e.value});
//                } else if (intentsE.value instanceof String) {
//                    assert intentsE.value.equals(e.value);
//                } else if (intentsE.value instanceof Map) {
//                    assert intentsE.rawEnt.text.equals(e.value);
//                }
//
//            }
//
//            //noinspection GroovyAssignabilityCheck
//            JsonElement err = rslt.get(1);
//            assert err == null;
//            approvedIntent = ((Object) (resp));
//        }
//
//
//        if (DefaultGroovyMethods.asBoolean(approvedIntent)) {
//            // Now, let's see if these intents can be executed
//
//            String procToRun = "NLTest.testExecIntent";
//            if (useCustomCallProc.asBoolean()) {
//                procToRun = "NLTest.testExecCustomIntent";
//            }
//
//
//            log.debug("Running {} using intent: {}", procToRun, approvedIntent);
//            LinkedHashMap<String, Map> map = new LinkedHashMap<String, Map>(1);
//            map.put("intentUnderTest", approvedIntent);
//            vr = v.execute(procToRun, map);
//            log.debug("Result for {} is: {}", procToRun, vr);
//            assert vr.isSuccess();
//            rawResult = ((JsonObject) vr.getBody()).asMap();
//            assert rawResult.response != null;
//            Map rslt = DefaultGroovyMethods.invokeMethod(mapper, "readValue",
//                                                         new Object[]{getProperty("rslt").response, Map.class});
//            assert rslt.size() == 10;
//            // The following few lines mirror those above.  The intent should be preserved as it comes through
//            // as it is used by the interpreter in some cases.
//            assert rslt.intent != null;
//            assert rslt.response;
//            Object luisVersion = rslt.luisVersion;
//            Object cluVersion = rslt.cluVersion;
//            Object intent = rslt.intent;
//            assert intent != null;
//            assert intent.equals(desiredIntent);
//            Object rawIntent = rslt.rawIntent;
//            assert rawIntent != null;
//
//            assert rslt.query != null;
//            if (!DATE_BASED_UTTERANCES.contains(utterance)) {
//                assert DefaultGroovyMethods.invokeMethod(rslt.query, "equalsIgnoreCase", new Object[]{utterance});
//            }
//
//            if (rslt.cluVersion.equals(1)) {
//                assert rslt.rawIntent.topIntent != null;
//                assert rslt.rawIntent.topIntent.equals(desiredIntent);
//                assert rslt.intent.equals(rslt.rawIntent.topIntent);
//                assert DefaultGroovyMethods.getAt(rslt.rawIntent.intents,
//                                                  DefaultGroovyMethods.asType(rslt.rawIntent.topIntent, String.class));
//            } else {
//                Assert.fail("Unsupported LUIS Version encountered: " + luisVersion);
//            }
//
//            if (cluVersion.equals(1) && utterance.equals(UTTERANCE_WHEN_IS_YOUR_BIRTHDAY)) {
//                // CLU picks up the 'is' as a comparator.  So we'll accept that...
//                assert DefaultGroovyMethods.invokeMethod(rslt.entities, "size", new Object[0])
//                                           .equals(entities.invokeMethod("size", new Object[0]) + 1);
//            } else {
//                assert DefaultGroovyMethods.invokeMethod(rslt.entities, "size", new Object[0]).equals(entities.size);
//            }
//
//
//            for (Object e : entities) {
//                // First, find the corresponding entry in the intent set
//                Object intentsE = DefaultGroovyMethods.find(rslt.entities, new Closure<Boolean>(this, this) {
//                    public Boolean doCall(Object it) {
//                        return it.name.equals(e.name) || it.type.equals(e.name);
//                    }
//
//                    public Boolean doCall() {
//                        return doCall(null);
//                    }
//
//                });
//                assert intentsE != null;
//                assert intentsE.name.equals(e.name) || intentsE.type.equals(e.name);
//                if (luisVersion.equals(3)) {
//                    // LUIS v3 returns these as a list sometimes
//                    final Object value = (intentsE == null ? null : intentsE.value);
//                    final Object var = (value == null ? null : value.class);
//                    log.debug("Comparing {} ({}) with {}", intentsE.value, (var == null ? null : var.name), e.value);
//                    if (intentsE.value instanceof List) {
//                        assert intentsE.value.invokeMethod("contains", new Object[]{e.value});
//                    } else if (intentsE.value instanceof String) {
//                        assert intentsE.value.equals(e.value);
//                    } else if (intentsE.value instanceof Map) {
//                        assert intentsE.rawEnt.text.equals(e.value);
//                    }
//
//                } else {
//                    assert intentsE.value.equals(e.value);
//                }
//
//            }
//
//            Object err = rslt.isError;
//            assert err != null;
//            assert err.equals(expectedIsError) : "Unexpected error status: " + rslt;
//            assert rslt.response != null;
//            if (expectedResponse != null) {
//                if (expectedResponse instanceof Closure) {
//                    // The the caller has provided a closure to check the results.  Use that...
//                    ((Closure) expectedResponse).call(rslt.response);
//                } else {
//                    assert rslt.response.equals(expectedResponse);
//                }
//// Else, we do no check here.
//            }
//
//        }
//
//        // Finally, we'll execute at a higher level & verify results
//
//        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>(1);
//        map.put("nlUtterance", utterance);
//        vr = v.execute("NLTest.testUserCall", map);
//        Map rslt = ((JsonObject) vr.getBody()).asMap();
//        assert rslt.response != null;
//        Map resp = DefaultGroovyMethods.invokeMethod(mapper, "readValue", new Object[]{rslt.response, Map.class});
//        log.debug("Result for NLTest.testUserCall is: {}", rslt);
//        assert rslt instanceof String;
//        if (expectedResponse != null) {
//            if (expectedResponse instanceof Closure) {
//                // Then the caller has provided a closure to check the results.  Use that...
//                ((Closure) expectedResponse).call(rslt);
//            } else {
//                assert rslt == expectedResponse;
//            }
//// Else, we do no check here.
//        }
//
//
//        if (refreshCanonFiles && useAzureSource && canonFile != null && rawResult != null) {
//            // If asked & using a real source, save results for next time
//            String here = System.getProperty("user.dir");
//            assert here.endsWith("integration");
//            Path p = Paths.get(here, CANON_ROOT, canonFile);
//            log.debug("Raw Result: {}", rawResult);
//            String canon;
//            if (DefaultGroovyMethods.asBoolean(rawResult.response)) {
//                canon = JsonOutput.prettyPrint(
//                    JsonOutput.toJson(DefaultGroovyMethods.asType(rawResult.response.rawCluResp, Map.class)));
//            } else {
//                canon = "{\n" +
//                    "                \"kind \": \"ConversationResult \",\n" +
//                    "                \"result \": {\n" +
//                    "                          \"query\" : \"What is the airspeed velocity of an unladen sparrow\",\n" +
//                    "                          \"projectKind\": \"Conversation\",\n" +
//                    "                          \"prediction\" : {\n" +
//                    "                              \"topIntent\" : \"None\" ,\n" +
//                    "                              \"intents\" : {\n" +
//                    "                                  \"None\" : {\n" +
//                    "                                      \"score\" : 0.965313256\n" +
//                    "                                  }\n" +
//                    "                              } ,\n" +
//                    "                              \"entities\" : {\n" +
//                    "                              }\n" +
//                    "                          }\n" +
//                    "                      }\n" +
//                    "              }";
//            }
//
//            log.info(">>>>> Writing canon file: {} -->\n{}", p, canon);
//            Files.writeString(p, canon);
//        }

    }

    public void checkNLExec(Object utterance, Object desiredIntent, Object entities, Object expectedResponse, Object expectedIsError, String canonFile) {
        checkNLExec(utterance, desiredIntent, entities, expectedResponse, expectedIsError, canonFile, false);
    }

    public void loadPatients() {
        // Insert a record into the type.

        LinkedHashMap<Object, Object> instance1 = new LinkedHashMap<Object, Object>();
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(3);
        map.put("name", "Patient Zero");
        map.put("room", "Z000");
        map.put("age", 30);
        v.insert("Patients", map);
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(3);
        map1.put("name", "Patient One");
        map1.put("room", "Z001");
        map1.put("age", 40);
        v.insert("Patients", map1);
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
        map2.put("name", "Patient Two");
        map2.put("room", "Z002");
        map2.put("age", 50);
        v.insert("Patients", map2);
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(3);
        map3.put("name", "APatient Zero");
        map3.put("room", "AZ000");
        map3.put("age", 60);
        v.insert("Patients", map3);
        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(3);
        map4.put("name", "APatient One");
        map4.put("room", "AZ001");
        map4.put("age", 70);
        v.insert("Patients", map4);
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<String, Serializable>(3);
        map5.put("name", "APatient Two");
        map5.put("room", "AZ002");
        map5.put("age", 80);
        v.insert("Patients", map5);
    }

    public void unloadPatients() {
        try {
            v.delete("Patients", new LinkedHashMap<Object, Object>());
        } catch (Exception ignore) {
        }

    }

    public Vantiq getV() {
        return v;
    }

    public void setV(Vantiq v) {
        this.v = v;
    }

    /**
     * Test basic support for natural language using the Azure Conversational Language Understanding service.
     *
     * Created by fhcarter on 4 April 2023.
     */
    private static String CLU_SOURCE_NAME = "VANTIQ_TEST_CLU_SOURCE";
    private static Boolean useAzureSource = StringGroovyMethods.toBoolean(
        System.getProperty("azureCluVantiqUse", "false"));
    private static String azureCluSubscriptionKey = System.getProperty("azureCluSubscriptionKey", null);
    private static Boolean refreshCanonFiles = false && useAzureSource;
    private static String CANON_ROOT = "src/test/resources";
    private static String UTTERANCE_BYE_BYE = "bye bye";
    private static String INTENT_BYE_BYE = "system.endDiscussion";
    private static ArrayList<Object> ENTITIES_BYE_BYE = new ArrayList<Object>();
    private static String RESPONSE_BYE_BYE = "Goodbye for now. It has been a pleasure.";
    private static Boolean IS_ERROR_BYE_BYE = false;
    private static String CANON_BYE_BYE = "nlpClu/byeBye.json";
    private static String CLU_BYE_BYE = NatLangTestBase.fetchFromFile(CANON_BYE_BYE);
    private static String UTTERANCE_COUNT_Patients = "how many Patients are there?";
    private static String INTENT_COUNT_Patients = "system.count";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "Patients");
        map.put("name", "system.typeName");
        ENTITIES_COUNT_Patients = new ArrayList<LinkedHashMap<String, String>>(asList(map));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_COUNT_Patients;
    private static String RESPONSE_COUNT_Patients_NONE = "• There are 0 items of type Patients.";
    private static String RESPONSE_COUNT_Patients_SOME = "• There are 6 items of type Patients.";
    private static Boolean IS_ERROR_COUNT_Patients = false;
    private static String CANON_COUNT_Patients = "nlpClu/countPatients.json";
    private static String CLU_COUNT_Patients = NatLangTestBase.fetchFromFile(CANON_COUNT_Patients);
    private static String UTTERANCE_DESCRIBE_Patients = "describe Patients";
    private static String INTENT_DESCRIBE_Patients = "system.describeType";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "Patients");
        map.put("name", "system.plainWord");
        ENTITIES_DESCRIBE_Patients = new ArrayList<LinkedHashMap<String, String>>(asList(map));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_DESCRIBE_Patients;
    private static Boolean IS_ERROR_DESCRIBE_Patients = false;
    private static String CANON_DESCRIBE_Patients = "nlpClu/describePatients.json";
    private static String CLU_DESCRIBE_Patients = NatLangTestBase.fetchFromFile(CANON_DESCRIBE_Patients);
    private static String UTTERANCE_LIST_Patients = "list Patients";
    private static String INTENT_LIST_Patients = "system.list";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "Patients");
        map.put("name", "system.typeName");
        ENTITIES_LIST_Patients = new ArrayList<LinkedHashMap<String, String>>(asList(map));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_LIST_Patients;
    private static String RESPONSE_LIST_Patients = "• There are 0 items of type Patients.";
    private static Boolean IS_ERROR_LIST_Patients = false;
    private static String CANON_LIST_Patients = "nlpClu/listPatients.json";
    private static String CLU_LIST_Patients = NatLangTestBase.fetchFromFile(CANON_LIST_Patients);
    private static String UTTERANCE_LIST_Patients_CONDITION = "list Patients whose name is greater than or equal to Fred";
    private static String INTENT_LIST_Patients_CONDITION = "system.list";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "Patients");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "is greater than or equal to");
        map1.put("name", "system.comparator_gte");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "Fred");
        map2.put("name", "system.propertyValue");
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(2);
        map3.put("value", "name");
        map3.put("name", "system.propertyName");
        ENTITIES_LIST_Patients_CONDITION = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2, map3));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_LIST_Patients_CONDITION;
    private static Boolean IS_ERROR_LIST_Patients_CONDITION = false;
    private static String CANON_LIST_Patients_CONDITION = "nlpClu/listPatientsCondition.json";
    private static String CLU_LIST_PATIENTS_CONDITION = NatLangTestBase.fetchFromFile(
        CANON_LIST_Patients_CONDITION);
    private static String UTTERANCE_LIST_Patients_CONDITION_PUNCT = "list Patients where name >= Fred";
    private static String INTENT_LIST_Patients_CONDITION_PUNCT = "system.list";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "Patients");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", ">=");
        map1.put("name", "system.comparator_gte");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "Fred");
        map2.put("name", "system.propertyValue");
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(2);
        map3.put("value", "name");
        map3.put("name", "system.propertyName");
        ENTITIES_LIST_Patients_CONDITION_PUNCT = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2, map3));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_LIST_Patients_CONDITION_PUNCT;
    private static String RESPONSE_LIST_Patients_CONDITION_PUNCT = "• There are 0 items of type Patients.";
    private static Boolean IS_ERROR_LIST_Patients_CONDITION_PUNCT = false;
    private static String CANON_LIST_Patients_CONDITION_PUNCT = "nlpClu/listPatientsConditionPunct.json";
    private static String CLU_LIST_Patients_CONDITION_PUNCT = NatLangTestBase.fetchFromFile(
        CANON_LIST_Patients_CONDITION_PUNCT);
    private static String UTTERANCE_LIST_Patients_CONDITION_AGE = "list Patients whose age <= 40";
    private static String INTENT_LIST_Patients_CONDITION_AGE = "system.list";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "Patients");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "<=");
        map1.put("name", "system.comparator_lte");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "40");
        map2.put("name", "system.propertyValue");
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(2);
        map3.put("value", "age");
        map3.put("name", "system.propertyName");
        ENTITIES_LIST_Patients_CONDITION_AGE = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2, map3));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_LIST_Patients_CONDITION_AGE;
    private static String RESPONSE_LIST_Patients_CONDITION_AGE = "• There are 0 items of type Patients.";
    private static Boolean IS_ERROR_LIST_Patients_CONDITION_AGE = false;
    private static String CANON_LIST_Patients_CONDITION_AGE = "nlpClu/listPatientsConditionPunctAge.json";
    private static String CLU_LIST_Patients_CONDITION_AGE = NatLangTestBase.fetchFromFile(
        CANON_LIST_Patients_CONDITION_AGE);
    private static String UTTERANCE_LIST_PATIENTS_LC = "list patients";
    private static String INTENT_LIST_PATIENTS_LC = "system.list";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "patients");
        map.put("name", "system.typeName");
        ENTITIES_LIST_PATIENTS_LC = new ArrayList<LinkedHashMap<String, String>>(asList(map));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_LIST_PATIENTS_LC;
    private static String RESPONSE_LIST_PATIENTS_LC = "The name \"patients\" is not a recognized type or resource name.";
    private static Boolean IS_ERROR_LIST_PATIENTS_LC = false;
    private static String CANON_LIST_PATIENTS_LC = "nlpClu/listPatientsConditionLC.json";
    private static String CLU_LIST_PATIENTS_LC = NatLangTestBase.fetchFromFile(CANON_LIST_PATIENTS_LC);
    private static String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS = "show active collaborations younger than 2 days";
    private static String INTENT_SHOW_ACTIVE_COLLABORATIONS = "system.showActive";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "collaborations");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "active");
        map1.put("name", "system.condition_active");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "younger than");
        map2.put("name", "system.comparator_gt");
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(2);
        map3.put("value", "2 days");
        map3.put("name", "datetimeV2");
        ENTITIES_SHOW_ACTIVE_COLLABORATIONS = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2, map3));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_SHOW_ACTIVE_COLLABORATIONS;
    private static String RESPONSE_SHOW_ACTIVE_COLLABORATIONS = " There are no current collaborations.";
    private static Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS = false;
    private static String CANON_SHOW_ACTIVE_COLLABORATIONS = "nlpClu/showActiveCollaborations.json";
    private static String CLU_SHOW_ACTIVE_COLLABORATIONS = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS);
    private static String UTTERANCE_SHOW_CURRENT_COLLABORATIONS = "show current collaborations older than yesterday";
    private static String INTENT_SHOW_CURRENT_COLLABORATIONS = "system.showActive";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "collaborations");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "current");
        map1.put("name", "system.condition_active");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "older than");
        map2.put("name", "system.comparator_lt");
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(2);
        map3.put("value", "yesterday");
        map3.put("name", "datetimeV2");
        ENTITIES_SHOW_CURRENT_COLLABORATIONS = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2, map3));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_SHOW_CURRENT_COLLABORATIONS;
    private static String RESPONSE_SHOW_CURRENT_COLLABORATIONS = " There are no current collaborations.";
    private static Boolean IS_ERROR_SHOW_CURRENT_COLLABORATIONS = false;
    private static String CANON_SHOW_CURRENT_COLLABORATIONS = "nlpClu/showCurrentCollaborations.json";
    private static String CLU_SHOW_CURRENT_COLLABORATIONS = NatLangTestBase.fetchFromFile(
        CANON_SHOW_CURRENT_COLLABORATIONS);
    private static String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE = "show active collaborations since " + randomStartOfLastMonthWithoutYear();
    private static String INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE = "system.showActive";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "collaborations");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "active");
        map1.put("name", "system.condition_active");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "since " + randomStartOfLastMonthWithoutYear());
        map2.put("name", "datetimeV2");
        ENTITIES_SHOW_ACTIVE_COLLABORATIONS_SINCE = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_SHOW_ACTIVE_COLLABORATIONS_SINCE = null;
    private static Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE = false;
    private static String CANON_SHOW_ACTIVE_COLLABORATIONS_SINCE = "nlpClu/showActiveCollaborationsSince.json";
    private static String CLU_SHOW_ACTIVE_COLLABORATIONS_SINCE = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS_SINCE);
    private static String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE = "show active collaborations before " +
            randomStartOfNextMonthWithoutYear();
    private static String INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE = "system.showActive";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "collaborations");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "active");
        map1.put("name", "system.condition_active");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "before " + randomStartOfNextMonthWithoutYear());
        map2.put("name", "datetimeV2");
        ENTITIES_SHOW_ACTIVE_COLLABORATIONS_BEFORE = new ArrayList<LinkedHashMap<String, String>>(
            asList(map, map1, map2));
    }

    private static  ArrayList<LinkedHashMap<String, String>> ENTITIES_SHOW_ACTIVE_COLLABORATIONS_BEFORE = null;
    private static Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE = false;
    private static String CANON_SHOW_ACTIVE_COLLABORATIONS_BEFORE = "nlpClu/showActiveCollaborationsBefore.json";
    private static String CLU_SHOW_ACTIVE_COLLABORATIONS_BEFORE = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS_BEFORE);
    private static String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = "show active collaborations younger than 3 days old";
    private static String INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = "system.showActive";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "collaborations");
        map.put("name", "system.typeName");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "active");
        map1.put("name", "system.condition_active");
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(2);
        map2.put("value", "younger than");
        map2.put("name", "system.comparator_gt");
        LinkedHashMap<String, String> map3 = new LinkedHashMap<String, String>(2);
        map3.put("value", "3 days old");
        map3.put("name", "age");
        ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS =
                (List<LinkedHashMap<String, String>>) Arrays.asList(map, map1, map2, map3);
    }

    private static List<LinkedHashMap<String, String>> ENTITIES_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS
            = null;
    private static final Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = false;
    private static String CANON_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = "nlpClu/showActiveCollaborationsYounger3Days.json";
    private static String CLU_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS);
    private static String COLLABORATION_ONE = "NLTestCollab_One";
    private static String COLLABORATION_TWO = "NLTestCollab_Two";
    private  final void closedOneCollaborationResponseChecker(Map resp) {
         assert resp.get("status").equals("active");
        };
    private static String UTTERANCE_NONSENSE = "What is the airspeed velocity of an unladen sparrow";
    private static String INTENT_NONSENSE = "None";
    private static ArrayList<Object> ENTITIES_NONSENSE = new ArrayList<Object>();
    private static String RESPONSE_NONSENSE = "You requested, \"" + UTTERANCE_NONSENSE + "\". We could not " +
            "determine what " + "action to take.  Please rephrase your request.";
    private static Boolean IS_ERROR_NONSENSE = false;
    private static String CANON_NONSENSE = "nlpClu/nonsense.json";
    private static String CLU_NONSENSE = NatLangTestBase.fetchFromFile(CANON_NONSENSE);
    private static String UTTERANCE_HI = "hi";
    private static String INTENT_HI = "system.smalltalk.greetings";
    private static ArrayList<Object> ENTITIES_HI = new ArrayList<Object>();
    private static String RESPONSE_HI = "Greetings and salutations to you as well.";
    private static Boolean IS_ERROR_HI = false;
    private static String CANON_HI = "nlpClu/hi.json";
    private static String CLU_HI = NatLangTestBase.fetchFromFile(CANON_HI);
    private static String UTTERANCE_GOOD_AFTERNOON = "good afternoon";
    private static String INTENT_GOOD_AFTERNOON = "system.smalltalk.greetings";
    private static ArrayList<Object> ENTITIES_GOOD_AFTERNOON = new ArrayList<Object>();
    private static String RESPONSE_GOOD_AFTERNOON = "Greetings and salutations to you as well.";
    private static Boolean IS_ERROR_GOOD_AFTERNOON = false;
    private static String CANON_GOOD_AFTERNOON = "nlpClu/goodAfternoon.json";
    private static String CLU_GOOD_AFTERNOON = NatLangTestBase.fetchFromFile(CANON_GOOD_AFTERNOON);
    private static String UTTERANCE_THANK_YOU = "thank you";
    private static String INTENT_THANK_YOU = "system.smalltalk.thankYou";
    private static ArrayList<Object> ENTITIES_THANK_YOU = new ArrayList<Object>();
    private static String RESPONSE_THANK_YOU = "You are quite welcome.";
    private static Boolean IS_ERROR_THANK_YOU = false;
    private static String CANON_THANK_YOU = "nlpClu/thankYou.json";
    private static String CLU_THANK_YOU = NatLangTestBase.fetchFromFile(CANON_THANK_YOU);
    private static String UTTERANCE_HOW_DID_YOU_GET_STARTED = "how did you get started";
    private static String INTENT_HOW_DID_YOU_GET_STARTED = "system.smalltalk.bio";
    private static ArrayList<Object> ENTITIES_HOW_DID_YOU_GET_STARTED = new ArrayList<Object>();
    private static String RESPONSE_HOW_DID_YOU_GET_STARTED = "I started by just being chatty.  I was determined to use " + "my talents to their fullest, so I studied and applied myself. And now, here I am.";
    private static Boolean IS_ERROR_HOW_DID_YOU_GET_STARTED = false;
    private static String CANON_HOW_DID_YOU_GET_STARTED = "nlpClu/howDidYouGetStarted.json";
    private static String CLU_HOW_DID_YOU_GET_STARTED = NatLangTestBase.fetchFromFile(
        CANON_HOW_DID_YOU_GET_STARTED);
    private static String UTTERANCE_ARE_YOU_SAD = "are you sad?";
    private static String INTENT_ARE_YOU_SAD = "system.smalltalk.mindframe";
    private static ArrayList<Object> ENTITIES_ARE_YOU_SAD = new ArrayList<Object>();
    private static String RESPONSE_ARE_YOU_SAD = "My life in information service is very satisfying.";
    private static Boolean IS_ERROR_ARE_YOU_SAD = false;
    private static String CANON_ARE_YOU_SAD = "nlpClu/areYouSad.json";
    private static String CLU_ARE_YOU_SAD = NatLangTestBase.fetchFromFile(CANON_ARE_YOU_SAD);
    private static String UTTERANCE_ARE_YOU_A_CHATBOT = "are you a chatbot?";
    private static String INTENT_ARE_YOU_A_CHATBOT = "system.smalltalk.chatbot";
    private static ArrayList<Object> ENTITIES_ARE_YOU_A_CHATBOT = new ArrayList<Object>();
    private static String RESPONSE_ARE_YOU_A_CHATBOT = "Life as a chatbot keeps me occupied.  I enjoy providing " + "information.";
    private static Boolean IS_ERROR_ARE_YOU_A_CHATBOT = false;
    private static String CANON_ARE_YOU_A_CHATBOT = "nlpClu/areYouAChatbot.json";
    private static String CLU_ARE_A_CHATBOT = NatLangTestBase.fetchFromFile(CANON_ARE_YOU_A_CHATBOT);
    private static String UTTERANCE_WHEN_IS_YOUR_BIRTHDAY = "when is your birthday?";
    private static String INTENT_WHEN_IS_YOUR_BIRTHDAY = "system.smalltalk.birthday";
    private static ArrayList<Object> ENTITIES_WHEN_IS_YOUR_BIRTHDAY = new ArrayList<Object>();
    private static String RESPONSE_WHEN_IS_YOUR_BIRTHDAY = "I began operation a while ago.  I prefer not to " + "focus on those details.";
    private static Boolean IS_ERROR_WHEN_IS_YOUR_BIRTHDAY = false;
    private static String CANON_WHEN_IS_YOUR_BIRTHDAY = "nlpClu/whenIsYourBirthday.json";
    private static String CLU_WHEN_IS_YOUR_BIRTHDAY = NatLangTestBase.fetchFromFile(CANON_WHEN_IS_YOUR_BIRTHDAY);
    private static String UTTERANCE_WHO_IS_SICK = "who is sick?";
    private static String INTENT_WHO_IS_SICK = "health.patientsByCondition";

    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(2);
        map.put("value", "sick");
        map.put("name", "health.condition_general");
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("value", "is");
        map1.put("name", "system.comparator_eq");
        ENTITIES_WHO_IS_SICK = new ArrayList<LinkedHashMap<String, String>>(asList(map, map1));
    }

    private static ArrayList<LinkedHashMap<String, String>> ENTITIES_WHO_IS_SICK = null;
    private static Boolean IS_ERROR_WHO_IS_SICK = false;
    private static String CANON_WHO_IS_SICK = "nlpClu/whoIsSick.json";
    private static String CLU_WHO_IS_SICK = NatLangTestBase.fetchFromFile(CANON_WHO_IS_SICK);
    private static List<String> DATE_BASED_UTTERANCES = 
        asList(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE);
    private Vantiq v = null;
}
