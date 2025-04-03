package io.vantiq.extsrc.naturallanguage;

import static java.util.Arrays.asList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.Serializable;
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
@SuppressWarnings("rawtypes")
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

    @BeforeClass
    public static void setupCat() {
        //noinspection unchecked
        NatLangTestBase.performSetup(Collections.EMPTY_MAP);
    }

    @Before
    public void setup() {
        v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
        LinkedHashMap<Object, Object> newType = new LinkedHashMap<>();
        newType.put("name", "Patients");
        LinkedHashMap<String, LinkedHashMap<String, Serializable>> map = new LinkedHashMap<>(
            3);
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<>(2);
        map1.put("type", "String");
        map1.put("required", true);
        map.put("name", map1);
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<>(2);
        map2.put("type", "String");
        map2.put("required", true);
        map.put("room", map2);
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<>(2);
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
            "} else if (nlUtterance.equals(\"" + UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE + "\")){\n" +
            "        // These are statements of the form \"show active collaborations since 1 September\"\n" +
            "    var dateString = \"" + CLUProcessingTest.randomStartOfLastMonthWithoutYear() + "\"\n" +
            "    var dateLength = dateString.length() + " + "since ".length() + "\n" +
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
            "        entToFix.resolutions[0].start = \"" + CLUProcessingTest.randomDatesNumericMonthYear() + "-" + CLUProcessingTest.randomDatesNumericMonth() + "-01\"\n" +
            "        entToFix.resolutions[1].start = \"" + CLUProcessingTest.randomDatesNumericMonthNextYear() + "-" + CLUProcessingTest.randomDatesNumericMonth() + "-01\"\n" +
            "    } else {\n" +
            "        exception(\"test.setup.no.date.collabSince\", \"Did not find a dateTimeV2\", [])\n" +
            "    }\n" +
            "\n} else if (nlUtterance.equals(\"" + UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE + "\")){\n" +
            "        // These are statements of the form \"show active collaborations before 1 August\"\n" +
            "    var dateString = \"" + CLUProcessingTest.randomStartOfNextMonthWithoutYear() + "\"\n" +
            "    var dateLength = dateString.length() + " + "before ".length() + "\n" +
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
            "        entToFix.resolutions[0].end = \"" + CLUProcessingTest.randomDatesNumericMonthYear() + "-" + CLUProcessingTest.randomDatesNumericMonth() + "-01\"\n" +
            "        entToFix.resolutions[1].end = \"" + CLUProcessingTest.randomDatesNumericMonthNextYear() + "-" + CLUProcessingTest.randomDatesNumericMonth() + "-01\"\n" +
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
            "        result = com.vantiq.nlp.NLCore.interpretConversationalQuery(nlUtterance, \"" + CLU_SOURCE_NAME + "\",\n" +
            "                        \"VantiqTutorial\", \"mig1\", " + !useAzureSource + ")\n" +
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
            "        result = com.vantiq.nlp.NLCore.interpretConversationalQuery(nlUtterance, " +
                "\"IAmAServiceThatDoesNotExist\",\n" +
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
            "        result = com.vantiq.nlp.NLSystemExec.executeSystemIntent(intentUnderTest)\n" +
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
            "    var interpretation = com.vantiq.nlp.NLCore.interpretConversationalQuery(utterance, \"" + CLU_SOURCE_NAME + "\",\n" +
            "                                    \"VantiqTutorial\", \"mig1\", " + !useAzureSource + ")\n" +
            "    log.debug(\"ProcessUtterance() interpretation: {}\", [interpretation.stringify(true)])\n" +
            "    if (interpretation.errorMsg != null) {\n" +
            "        // Then, we had some error.  Let's just dump that as the response and move on\n" +
            "        log.debug(\"ProcessUtterance(): Found Error: {}\", [interpretation.errorMsg])\n" +
            "        response = interpretation.errorMsg\n" +
            "    } else if (interpretation.response.intent.startsWith(\"system.\")) {\n" +
            "        log.debug(\"ProcessUtterance():  Attempting interpretation of intent: {}\", [interpretation.response.intent])\n" +
            "        var interpretedString = " +
                         "com.vantiq.nlp.NLSystemExec.executeSystemIntent(interpretation.response)\n" +
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
            "        result = NLTest.processUtterance(nlUtterance, \"" + CLU_SOURCE_NAME + "\", " + !useAzureSource + ")\n" +
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
        final LinkedHashMap<Object, Object> source = new LinkedHashMap<>();
        source.put("name", CLU_SOURCE_NAME);
        source.put("type", "REMOTE");
        if (useAzureSource) {
            // Here, we'll use a real azure source.
            LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<>(2);
            map4.put("uri",
                     "https://fcarter-clu-migration.cognitiveservices.azure.com/language/" +
                             ":analyze-conversations?api-version=2022-10-01-preview");
            LinkedHashMap<String, LinkedHashMap<String, String>> map5 = new LinkedHashMap<>(
                    1);
            LinkedHashMap<String, String> map6 = new LinkedHashMap<>(1);
            map6.put("Ocp-Apim-Subscription-Key", azureCluSubscriptionKey);
            map5.put("headers", map6);
            map4.put("requestDefaults", map5);
            source.put("config", map4);
        } else {
            // Otherwise, the normal case, fake it
            LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<>(6);
            map4.put("uri", "http://localhost:8080/api/v1/resources");
            map4.put("accessToken", v.getAccessToken());
            map4.put("contentType", "application/json");
            map4.put("keepAliveInterval", 0);
            map4.put("connectionTimeout", 0);
            LinkedHashMap<String, String> map5 = new LinkedHashMap<>(1);
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

        checkNLExec(UTTERANCE_BYE_BYE, INTENT_BYE_BYE, IS_ERROR_BYE_BYE, false);

        // Note: The following two differ in the case of Patients (vs. patients)
        checkNLExec(UTTERANCE_LIST_Patients, INTENT_LIST_Patients, IS_ERROR_LIST_Patients,  false);
        // So far, we have none of these types.

        checkNLExec(UTTERANCE_LIST_PATIENTS_LC, INTENT_LIST_PATIENTS_LC, IS_ERROR_LIST_PATIENTS_LC, false);
        // End of errors

        checkNLExec(UTTERANCE_LIST_Patients_CONDITION_PUNCT, INTENT_LIST_Patients_CONDITION_PUNCT,
                    IS_ERROR_LIST_Patients_CONDITION_PUNCT, false);// So far, we have none of these types.

        checkNLExec(UTTERANCE_LIST_Patients_CONDITION_AGE, INTENT_LIST_Patients_CONDITION_AGE,
                    IS_ERROR_LIST_Patients_CONDITION_AGE, false);

        // Note: The following two differ in the text.  The meaning of the condition is identical.
        // The queries, generally, are different.

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, false);

        checkNLExec(UTTERANCE_SHOW_CURRENT_COLLABORATIONS, INTENT_SHOW_CURRENT_COLLABORATIONS,
                    IS_ERROR_SHOW_CURRENT_COLLABORATIONS, false);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE, false);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE, INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE, false);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    false);
        
        checkNLExec(UTTERANCE_HI, INTENT_HI, IS_ERROR_HI, false);
    }

    @Test
    public void testQueryBasic() {
        try {
            checkNLExec(UTTERANCE_DESCRIBE_Patients, INTENT_DESCRIBE_Patients,
                        IS_ERROR_DESCRIBE_Patients, false);

            checkNLExec(UTTERANCE_COUNT_Patients, INTENT_COUNT_Patients, IS_ERROR_COUNT_Patients, false);

            // Let's create some actual data here.  Having done that, we'll issue NL queries to fetch it.

            loadPatients();

            // Now, we can issue the NL query to fetch these, and they should return the rows.
            // These are queries for Patients with a CAPITAL P.  It should fetch data.

            // Here, we've already saved the CLU output, so we wont pass it again
            checkNLExec(UTTERANCE_DESCRIBE_Patients, INTENT_DESCRIBE_Patients,
                        IS_ERROR_DESCRIBE_Patients, false);

            checkNLExec(UTTERANCE_COUNT_Patients, INTENT_COUNT_Patients, IS_ERROR_COUNT_Patients, false);


            // Here, we create a closure that will check the response for validity.  A plain-ole string compare
            // won't work since there are dates, id's, etc. involved.

            checkNLExec(UTTERANCE_LIST_Patients, INTENT_LIST_Patients, IS_ERROR_LIST_PATIENTS_LC, false);

            // Here, we're checking similar queries but with a condition.
            checkNLExec(UTTERANCE_LIST_Patients_CONDITION, INTENT_LIST_Patients_CONDITION,
                        IS_ERROR_LIST_Patients_CONDITION, false);

            checkNLExec(UTTERANCE_LIST_Patients_CONDITION_PUNCT, INTENT_LIST_Patients_CONDITION_PUNCT,
                        IS_ERROR_LIST_Patients_CONDITION_PUNCT, false);

            checkNLExec(UTTERANCE_LIST_Patients_CONDITION_AGE, INTENT_LIST_Patients_CONDITION_AGE,
                        IS_ERROR_LIST_Patients_CONDITION_AGE, false);

            // The following should generate user errors
            // This is a query for lower-case patients.  No such type.
            checkNLExec(UTTERANCE_LIST_PATIENTS_LC, INTENT_LIST_PATIENTS_LC, IS_ERROR_LIST_PATIENTS_LC, false);
            // End of errors
        } finally {
            // After the test, remove the type.  The other test depends upon it being absent.
            unloadPatients();
        }


        checkNLExec(UTTERANCE_COUNT_Patients, INTENT_COUNT_Patients, IS_ERROR_COUNT_Patients, false);
    }

    @Test
    public void testQueryCollaborations() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        log.debug(">>> testQueryCollaborations::Date is: {}",
                  StringGroovyMethods.plus(cal.get(Calendar.DATE), "-") + cal.getDisplayName(Calendar.MONTH,
                                                                                             Calendar.LONG,
                                                                                             Locale.US) + "-" + cal.get(
                      Calendar.YEAR));
        
        Map collab1;
        Map collab2;

        // Now, let's create collaborations in various states, and see that we get them back OK

        LinkedHashMap<Object, Object> aCollab = new LinkedHashMap<>();
        aCollab.put("name", COLLABORATION_ONE);
        aCollab.put("entities", Collections.emptyMap());
        aCollab.put("results", Collections.emptyMap());
        aCollab.put("status", "active");
        aCollab.put("id", UUID.randomUUID());

        VantiqResponse vr = v.insert("system.collaborations", aCollab);
        
        collab1 = ((JsonObject) vr.getBody()).asMap();

        aCollab = new LinkedHashMap<>();
        aCollab.put("name", COLLABORATION_TWO);
        aCollab.put("entities", Collections.emptyMap());
        aCollab.put("results", Collections.emptyMap());
        aCollab.put("status", "active");
        aCollab.put("id", UUID.randomUUID());
        vr = v.insert("system.collaborations", aCollab);
        collab2 = ((JsonObject) vr.getBody()).asMap();
        
        
        // OK, now we have two active collaborations just created.  Let's run our query again to ensure we get them

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, false);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE, false);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE, INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE, false);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, false);


        // Now, let's update collab 1 to be closed

        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        map.put("id", collab1.get("id").toString());
        map.put("status", "complete");
        v.update("system.collaborations", collab1.get("id").toString(), map);
        
        LinkedHashMap<String, String> map1 = new LinkedHashMap<>(2);
        map1.put("id", collab2.get("id").toString());
        map1.put("status", "complete");
        v.update("system.collaborations", collab2.get("id").toString(), map1);

        // At this point, we should be back to the default state -- no collaborations
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, false);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, false);

        // Here, let's delete the collaborations & verify that we still get no answer.
        LinkedHashMap<String, String> map2 = new LinkedHashMap<>(1);
        map2.put("name", COLLABORATION_ONE);
        v.delete("system.collaborations", map2);
        LinkedHashMap<String, String> map3 = new LinkedHashMap<>(1);
        map3.put("name", COLLABORATION_TWO);
        v.delete("system.collaborations", map3);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS, INTENT_SHOW_ACTIVE_COLLABORATIONS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS, false);

        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE, false);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE, INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE, false);
        checkNLExec(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS,
                    IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS, false);
    }

    @Test
    public void testNonsense() {
        checkNLExec(UTTERANCE_NONSENSE, INTENT_NONSENSE,IS_ERROR_NONSENSE, false);
    }

    @Test
    public void testSmalltalk() {
        checkNLExec(UTTERANCE_HI, INTENT_HI, IS_ERROR_HI, false);

        checkNLExec(UTTERANCE_GOOD_AFTERNOON, INTENT_GOOD_AFTERNOON, IS_ERROR_GOOD_AFTERNOON, false);

        checkNLExec(UTTERANCE_ARE_YOU_SAD, INTENT_ARE_YOU_SAD, IS_ERROR_ARE_YOU_SAD, false);

        checkNLExec(UTTERANCE_ARE_YOU_A_CHATBOT, INTENT_ARE_YOU_A_CHATBOT, IS_ERROR_ARE_YOU_A_CHATBOT, false);

        checkNLExec(UTTERANCE_HOW_DID_YOU_GET_STARTED, INTENT_HOW_DID_YOU_GET_STARTED, IS_ERROR_HOW_DID_YOU_GET_STARTED,
                    false);

        checkNLExec(UTTERANCE_WHEN_IS_YOUR_BIRTHDAY, INTENT_WHEN_IS_YOUR_BIRTHDAY, IS_ERROR_WHEN_IS_YOUR_BIRTHDAY,
                    false);

        checkNLExec(UTTERANCE_THANK_YOU, INTENT_THANK_YOU, IS_ERROR_THANK_YOU, false);
    }

    @Test
    public void testUserIntents() {
        try {
            loadPatients();
            checkNLExec(UTTERANCE_WHO_IS_SICK, INTENT_WHO_IS_SICK, IS_ERROR_WHO_IS_SICK, true);
        } finally {
            // After the test, remove the type.  The other test depends upon it being absent.
            unloadPatients();
        }

    }
    
    @Test
    public void testCheckServiceComposition() {
        String proc = "package test.it\n" +
                "\n" +
                "import service com.vantiq.nlp.NLCore\n" +
                "\n" +
                "PROCEDURE NLTest.callService(event Object)\n" +
                
                "\n" +
                "event = NLCore.respondToConversationalQuery(cluDeployment : \"someDeployment\", " +
                "cluModel : \"someModel\", collab : null, natLangQuery : event.text," +
                "naturalLanguageSource : \"" + CLU_SOURCE_NAME + "\")\n" +
                "return event" +
                "\n";
        registerProcedure(v, proc);
        
        VantiqResponse vr = v.execute("test.it.NLTest.callService", Map.of("event",
                                                                    Map.of("text", "sillyness")));
        log.debug("callService response: {}", vr);
        Assert.assertTrue("service call Failed: " + vr, vr.isSuccess());
        Map rawResult = ((JsonObject) vr.getBody()).asMap();
        assert rawResult.size() == 2;
        String em = null;
        if (rawResult.get("errorMsg") != null && !(rawResult.get("errorMsg") instanceof JsonNull)) {
            em = ((JsonPrimitive) rawResult.get("errorMsg")).getAsString();
        }

        assert rawResult.get("response") != null;
        Map resp;
        try {
            resp = mapper.readValue(new Gson().toJson(((JsonObject) rawResult.get("response"))), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        assert resp != null;
        assert resp.get("rawIntent") != null;
        assert ((Map) resp.get("rawIntent")).get("topIntent") != null;
        assert ((Map) resp.get("rawIntent")).get("topIntent").equals(INTENT_NONSENSE);
        
    }

    public void checkNLExec(String utterance, String desiredIntent,
                            Object expectedIsError, boolean useCustomCallProc) {

        Map approvedIntent = null;
        Map rawResult;
        VantiqResponse vr = v.execute("NLTest.fullInterpreter", Map.of("nlUtterance", utterance));

        rawResult = ((JsonObject) vr.getBody()).asMap();
        String jsonResult = vr.getBody().toString();
        log.debug("full - result for utterance: {} :: \n{}\n:::::::::", utterance, jsonResult);
        assert rawResult.size() == 2;
        String em = null;
        if (rawResult.get("errorMsg") != null && !(rawResult.get("errorMsg") instanceof JsonNull)) {
            em = ((JsonPrimitive) rawResult.get("errorMsg")).getAsString();
        }
        if (Strings.isNullOrEmpty(em)) {
            assert rawResult.get("response") != null;
            Map resp;
            try {
                resp = mapper.readValue(new Gson().toJson(((JsonObject) rawResult.get("response"))), Map.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            assert resp != null;
            assert resp.get("rawIntent") != null;

            assert resp.get("query") != null;
            assert resp.get("query") instanceof String;
            assert DATE_BASED_UTTERANCES.contains(utterance) || ((String) resp.get("query")).equalsIgnoreCase(
                    utterance);

            assert ((Map) resp.get("rawIntent")).get("topIntent") != null;
            assert ((Map) resp.get("rawIntent")).get("topIntent").equals(desiredIntent);

            //noinspection GroovyAssignabilityCheck
            JsonElement err = (JsonElement) rawResult.get(1);
            assert err == null;
            approvedIntent = resp;
        }


        if (approvedIntent != null && !approvedIntent.isEmpty()) {
            // Now, let's see if these intents can be executed

            String procToRun = "NLTest.testExecIntent";
            if (useCustomCallProc) {
                procToRun = "NLTest.testExecCustomIntent";
            }


            log.debug("Running {} using intent: {}", procToRun, approvedIntent);
            LinkedHashMap<String, Map> map = new LinkedHashMap<>(1);
            map.put("intentUnderTest", approvedIntent);
            vr = v.execute(procToRun, map);
            log.debug("Result for {} is: {}", procToRun, vr);
            assert vr.isSuccess();
            Map rslt = ((JsonObject) vr.getBody()).asMap();
            assert rslt.size() == 10;
            // The following few lines mirror those above.  The intent should be preserved as it comes through
            // as it is used by the interpreter in some cases.
            assert rslt.get("intent") != null;
            assert rslt.get("response") != null;
            String cv = ((JsonPrimitive) rslt.get("cluVersion")).getAsString();
            int cluVersion = -1;
            if (cv != null) {
                cluVersion = Integer.parseInt(cv);
            }
            String intent = ((JsonPrimitive) rslt.get("intent")).getAsString();
            
            assert intent != null;
            assert intent.equals(desiredIntent);
            Object rawIntent = rslt.get("rawIntent");
            assert rawIntent != null;

            assert rslt.get("query") != null;
            assert DATE_BASED_UTTERANCES.contains(utterance) ||
                    ((JsonPrimitive) rslt.get("query")).getAsString().equalsIgnoreCase( utterance);

            if (cluVersion != 1) {
                Assert.fail("Unsupported CLU Version encountered: " + cluVersion);
            }

            Boolean err = ((JsonPrimitive) rslt.get("isError")).getAsBoolean();
            assert err.equals(expectedIsError) : "Unexpected error status: " + rslt;
            assert rslt.get("response") != null;
        }
    }

    public void loadPatients() {
        // Insert a record into the type.
        
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
        map.put("name", "Patient Zero");
        map.put("room", "Z000");
        map.put("age", 30);
        v.insert("Patients", map);
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<>(3);
        map1.put("name", "Patient One");
        map1.put("room", "Z001");
        map1.put("age", 40);
        v.insert("Patients", map1);
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<>(3);
        map2.put("name", "Patient Two");
        map2.put("room", "Z002");
        map2.put("age", 50);
        v.insert("Patients", map2);
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<>(3);
        map3.put("name", "APatient Zero");
        map3.put("room", "AZ000");
        map3.put("age", 60);
        v.insert("Patients", map3);
        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<>(3);
        map4.put("name", "APatient One");
        map4.put("room", "AZ001");
        map4.put("age", 70);
        v.insert("Patients", map4);
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<>(3);
        map5.put("name", "APatient Two");
        map5.put("room", "AZ002");
        map5.put("age", 80);
        v.insert("Patients", map5);
    }

    public void unloadPatients() {
        try {
            v.delete("Patients", new LinkedHashMap<>());
        } catch (Exception ignore) {
        }
    }

    /**
     * Test basic support for natural language using the Azure Conversational Language Understanding service.
     * <p>
     * Created by fhcarter on 4 April 2023.
     */
    private static final String CLU_SOURCE_NAME = "VANTIQ_TEST_CLU_SOURCE";
    private static final Boolean useAzureSource = StringGroovyMethods.toBoolean(
        System.getProperty("azureCluVantiqUse", "false"));
    private static final String azureCluSubscriptionKey = System.getProperty("azureCluSubscriptionKey", null);
    private static final String UTTERANCE_BYE_BYE = "bye bye";
    private static final String INTENT_BYE_BYE = "system.endDiscussion";
    private static final Boolean IS_ERROR_BYE_BYE = false;
    private static final String CANON_BYE_BYE = "nlpClu/byeBye.json";
    private static final String CLU_BYE_BYE = NatLangTestBase.fetchFromFile(CANON_BYE_BYE);
    private static final String UTTERANCE_COUNT_Patients = "how many Patients are there?";
    private static final String INTENT_COUNT_Patients = "system.count";
    
    private static final Boolean IS_ERROR_COUNT_Patients = false;
    private static final String CANON_COUNT_Patients = "nlpClu/countPatients.json";
    private static final String CLU_COUNT_Patients = NatLangTestBase.fetchFromFile(CANON_COUNT_Patients);
    private static final String UTTERANCE_DESCRIBE_Patients = "describe Patients";
    private static final String INTENT_DESCRIBE_Patients = "system.describeType";

    private static final Boolean IS_ERROR_DESCRIBE_Patients = false;
    private static final String CANON_DESCRIBE_Patients = "nlpClu/describePatients.json";
    private static final String CLU_DESCRIBE_Patients = NatLangTestBase.fetchFromFile(CANON_DESCRIBE_Patients);
    private static final String UTTERANCE_LIST_Patients = "list Patients";
    private static final String INTENT_LIST_Patients = "system.list";

    private static final Boolean IS_ERROR_LIST_Patients = false;
    private static final String CANON_LIST_Patients = "nlpClu/listPatients.json";
    private static final String CLU_LIST_Patients = NatLangTestBase.fetchFromFile(CANON_LIST_Patients);
    private static final String UTTERANCE_LIST_Patients_CONDITION = "list Patients whose name is greater than or equal to Fred";
    private static final String INTENT_LIST_Patients_CONDITION = "system.list";
    
    private static final Boolean IS_ERROR_LIST_Patients_CONDITION = false;
    private static final String CANON_LIST_Patients_CONDITION = "nlpClu/listPatientsCondition.json";
    private static final String CLU_LIST_PATIENTS_CONDITION = NatLangTestBase.fetchFromFile(
        CANON_LIST_Patients_CONDITION);
    private static final String UTTERANCE_LIST_Patients_CONDITION_PUNCT = "list Patients where name >= Fred";
    private static final String INTENT_LIST_Patients_CONDITION_PUNCT = "system.list";

    private static final Boolean IS_ERROR_LIST_Patients_CONDITION_PUNCT = false;
    private static final String CANON_LIST_Patients_CONDITION_PUNCT = "nlpClu/listPatientsConditionPunct.json";
    private static final String CLU_LIST_Patients_CONDITION_PUNCT = NatLangTestBase.fetchFromFile(
        CANON_LIST_Patients_CONDITION_PUNCT);
    private static final String UTTERANCE_LIST_Patients_CONDITION_AGE = "list Patients whose age <= 40";
    private static final String INTENT_LIST_Patients_CONDITION_AGE = "system.list";

    private static final Boolean IS_ERROR_LIST_Patients_CONDITION_AGE = false;
    private static final String CANON_LIST_Patients_CONDITION_AGE = "nlpClu/listPatientsConditionPunctAge.json";
    private static final String CLU_LIST_Patients_CONDITION_AGE = NatLangTestBase.fetchFromFile(
        CANON_LIST_Patients_CONDITION_AGE);
    private static final String UTTERANCE_LIST_PATIENTS_LC = "list patients";
    private static final String INTENT_LIST_PATIENTS_LC = "system.list";

    private static final Boolean IS_ERROR_LIST_PATIENTS_LC = false;
    private static final String CANON_LIST_PATIENTS_LC = "nlpClu/listPatientsConditionLC.json";
    private static final String CLU_LIST_PATIENTS_LC = NatLangTestBase.fetchFromFile(CANON_LIST_PATIENTS_LC);
    private static final String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS = "show active collaborations younger than 2 days";
    private static final String INTENT_SHOW_ACTIVE_COLLABORATIONS = "system.showActive";

    private static final Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS = false;
    private static final String CANON_SHOW_ACTIVE_COLLABORATIONS = "nlpClu/showActiveCollaborations.json";
    private static final String CLU_SHOW_ACTIVE_COLLABORATIONS = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS);
    private static final String UTTERANCE_SHOW_CURRENT_COLLABORATIONS = "show current collaborations older than yesterday";
    private static final String INTENT_SHOW_CURRENT_COLLABORATIONS = "system.showActive";

    private static final Boolean IS_ERROR_SHOW_CURRENT_COLLABORATIONS = false;
    private static final String CANON_SHOW_CURRENT_COLLABORATIONS = "nlpClu/showCurrentCollaborations.json";
    private static final String CLU_SHOW_CURRENT_COLLABORATIONS = NatLangTestBase.fetchFromFile(
        CANON_SHOW_CURRENT_COLLABORATIONS);
    private static final String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE = "show active collaborations since " + randomStartOfLastMonthWithoutYear();
    private static final String INTENT_SHOW_ACTIVE_COLLABORATIONS_SINCE = "system.showActive";
    
    private static final Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_SINCE = false;
    private static final String CANON_SHOW_ACTIVE_COLLABORATIONS_SINCE = "nlpClu/showActiveCollaborationsSince.json";
    private static final String CLU_SHOW_ACTIVE_COLLABORATIONS_SINCE = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS_SINCE);
    private static final String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE = "show active collaborations before " +
            randomStartOfNextMonthWithoutYear();
    private static final String INTENT_SHOW_ACTIVE_COLLABORATIONS_BEFORE = "system.showActive";

    private static final Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_BEFORE = false;
    private static final String CANON_SHOW_ACTIVE_COLLABORATIONS_BEFORE = "nlpClu/showActiveCollaborationsBefore.json";
    private static final String CLU_SHOW_ACTIVE_COLLABORATIONS_BEFORE = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS_BEFORE);
    private static final String UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = "show active collaborations younger than 3 days old";
    private static final String INTENT_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = "system.showActive";

    private static final Boolean IS_ERROR_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = false;
    private static final String CANON_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = "nlpClu/showActiveCollaborationsYounger3Days.json";
    private static final String CLU_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS = NatLangTestBase.fetchFromFile(
        CANON_SHOW_ACTIVE_COLLABORATIONS_YOUNGER_3_DAYS);
    private static final String COLLABORATION_ONE = "NLTestCollab_One";
    private static final String COLLABORATION_TWO = "NLTestCollab_Two";

    private static final String UTTERANCE_NONSENSE = "What is the airspeed velocity of an unladen sparrow";
    private static final String INTENT_NONSENSE = "None";

    private static final Boolean IS_ERROR_NONSENSE = false;
    private static final String CANON_NONSENSE = "nlpClu/nonsense.json";
    private static final String CLU_NONSENSE = NatLangTestBase.fetchFromFile(CANON_NONSENSE);
    private static final String UTTERANCE_HI = "hi";
    private static final String INTENT_HI = "system.smalltalk.greetings";

    private static final Boolean IS_ERROR_HI = false;
    private static final String CANON_HI = "nlpClu/hi.json";
    private static final String CLU_HI = NatLangTestBase.fetchFromFile(CANON_HI);
    private static final String UTTERANCE_GOOD_AFTERNOON = "good afternoon";
    private static final String INTENT_GOOD_AFTERNOON = "system.smalltalk.greetings";

    private static final Boolean IS_ERROR_GOOD_AFTERNOON = false;
    private static final String CANON_GOOD_AFTERNOON = "nlpClu/goodAfternoon.json";
    private static final String CLU_GOOD_AFTERNOON = NatLangTestBase.fetchFromFile(CANON_GOOD_AFTERNOON);
    private static final String UTTERANCE_THANK_YOU = "thank you";
    private static final String INTENT_THANK_YOU = "system.smalltalk.thankYou";

    private static final Boolean IS_ERROR_THANK_YOU = false;
    private static final String CANON_THANK_YOU = "nlpClu/thankYou.json";
    private static final String CLU_THANK_YOU = NatLangTestBase.fetchFromFile(CANON_THANK_YOU);
    private static final String UTTERANCE_HOW_DID_YOU_GET_STARTED = "how did you get started";
    private static final String INTENT_HOW_DID_YOU_GET_STARTED = "system.smalltalk.bio";

    private static final Boolean IS_ERROR_HOW_DID_YOU_GET_STARTED = false;
    private static final String CANON_HOW_DID_YOU_GET_STARTED = "nlpClu/howDidYouGetStarted.json";
    private static final String CLU_HOW_DID_YOU_GET_STARTED = NatLangTestBase.fetchFromFile(
        CANON_HOW_DID_YOU_GET_STARTED);
    private static final String UTTERANCE_ARE_YOU_SAD = "are you sad?";
    private static final String INTENT_ARE_YOU_SAD = "system.smalltalk.mindframe";

    private static final Boolean IS_ERROR_ARE_YOU_SAD = false;
    private static final String CANON_ARE_YOU_SAD = "nlpClu/areYouSad.json";
    private static final String CLU_ARE_YOU_SAD = NatLangTestBase.fetchFromFile(CANON_ARE_YOU_SAD);
    private static final String UTTERANCE_ARE_YOU_A_CHATBOT = "are you a chatbot?";
    private static final String INTENT_ARE_YOU_A_CHATBOT = "system.smalltalk.chatbot";

    private static final Boolean IS_ERROR_ARE_YOU_A_CHATBOT = false;
    private static final String CANON_ARE_YOU_A_CHATBOT = "nlpClu/areYouAChatbot.json";
    private static final String CLU_ARE_A_CHATBOT = NatLangTestBase.fetchFromFile(CANON_ARE_YOU_A_CHATBOT);
    private static final String UTTERANCE_WHEN_IS_YOUR_BIRTHDAY = "when is your birthday?";
    private static final String INTENT_WHEN_IS_YOUR_BIRTHDAY = "system.smalltalk.birthday";

    private static final Boolean IS_ERROR_WHEN_IS_YOUR_BIRTHDAY = false;
    private static final String CANON_WHEN_IS_YOUR_BIRTHDAY = "nlpClu/whenIsYourBirthday.json";
    private static final String CLU_WHEN_IS_YOUR_BIRTHDAY = NatLangTestBase.fetchFromFile(CANON_WHEN_IS_YOUR_BIRTHDAY);
    private static final String UTTERANCE_WHO_IS_SICK = "who is sick?";
    private static final String INTENT_WHO_IS_SICK = "health.patientsByCondition";

    private static final Boolean IS_ERROR_WHO_IS_SICK = false;
    private static final String CANON_WHO_IS_SICK = "nlpClu/whoIsSick.json";
    private static final String CLU_WHO_IS_SICK = NatLangTestBase.fetchFromFile(CANON_WHO_IS_SICK);
    private static final List<String> DATE_BASED_UTTERANCES =
        asList(UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_BEFORE, UTTERANCE_SHOW_ACTIVE_COLLABORATIONS_SINCE);
    private Vantiq v = null;
}
