package io.vantiq.extsrc.naturallanguage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@SuppressWarnings("rawtypes")
public class NLAppTest extends NatLangTestBase {
    
    private Vantiq v = null;
    public final String WEATHER_RESPONSE = "It is sunny and 75 degrees";
    
    @BeforeClass
    public static void setupCat() {
        NatLangTestBase.performSetup(Collections.emptyMap());
    }

    @Before
    public void setup() {
        v = new Vantiq(TEST_SERVER, 1);
        v.authenticate(SUB_USER, SUB_USER);
    }

    @Test
    public void natLangInterpretCLUAndProcessIntentAP() throws Exception {
        setupConversationalResources();
        String type = "apps/types/com/vantiq/test/nlchat/PartitionedState.json";
        String svc = "apps/services/com/vantiq/test/nlchat.json";
        String ct = "apps/collaborationtypes/com/vantiq/test/nlchat/testchat.json";
        performNatLangInterpretAndProcessIntentAP(type, svc, ct);
    }

    public void performNatLangInterpretAndProcessIntentAP(String typeFile, String svcFile, String ctFile)
            throws InterruptedException {

        // Create an App that starts a Chatroom and processes messages from the chatroom using a LUIS source
        // logging the results of the processed intents
        Map type = NatLangTestBase.fetchFromJsonFile(typeFile);
        Map svc = NatLangTestBase.fetchFromJsonFile(svcFile);
        Map ct = NatLangTestBase.fetchFromJsonFile(ctFile);
        VantiqResponse vr;
        vr = v.update("system.types", (String) type.get("name"), type);
        Assert.assertTrue("Failed to add type: " + vr.getErrors(), vr.isSuccess());
        
        vr = v.update("system.services", svc.get("name").toString(), svc);
        Assert.assertTrue("Failed to add service: " + vr.getErrors(), vr.isSuccess());
        
        vr = v.update("system.collaborationtypes", ct.get("name").toString(), ct);
        Assert.assertTrue("Failed to add collaboration type: " + vr.getErrors(), vr.isSuccess());
        vr = v.selectOne("system.collaborationtypes", (String) ct.get("name"));
        Assert.assertTrue("Failed to get collaboration type: " + vr.getErrors(), vr.isSuccess());
        Map raw = ((JsonObject) vr.getBody()).asMap();
        log.debug("CT As Map: {}", raw);
        List errs = null;
        try {
            errs = mapper.readValue(new Gson().toJson(((JsonArray) raw.get("vailErrors"))), List.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Assert.assertTrue("found errors" + errs, errs == null || errs.isEmpty());
        
        // Trigger the App
        vr = v.publish("services", "com.vantiq.test.nlchat/startchat", Map.of("text", "hi"));
        Assert.assertTrue("Failed to publish to our service: " + vr.getErrors(), vr.isSuccess());
        
        Thread.sleep(2000);
        
        vr = v.publish("services", "com.vantiq.test.nlchat/startchat",
                       Map.of("text", "what is the temperature?"));
        Assert.assertTrue("Failed to publish(2) to our service: " + vr.getErrors(), vr.isSuccess());
        
        boolean foundResults = false;
        boolean gotWeatherIntent = false;
        boolean gotWeatherResponse = false;
        boolean gotGreetingsIntent = false;
        for (int i = 0; i < 5 && !foundResults; i++) {
            Thread.sleep(3000); // Let app do it's work.  This avoids spurious timing errors.
            vr = v.select("system.logs", Collections.emptyList(), Collections.emptyMap(), null);
            List logs;
            try {
                logs = mapper.readValue(new Gson().toJson(vr.getBody()), List.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            for (int j = 0; j < logs.size() && !foundResults; j++) {
                log.debug("Fetch ({}): Log entry # {}: ({}) {}", i, j, logs.get(j).getClass().getName(), logs.get(j));
                Map entry = null;
                try {
                    entry = mapper.readValue(new Gson().toJson(logs.get(j)), Map.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                if (!gotGreetingsIntent) {
                    gotGreetingsIntent = ((String) entry.get("message")).contains("system.smalltalk.greetings");
                }
                if (!gotWeatherIntent) {
                    gotWeatherIntent =  ((String) entry.get("message")).contains("WeatherService.getWeather");
                }
                if (!gotWeatherResponse) {
                    gotWeatherResponse =  ((String) entry.get("message")).contains(WEATHER_RESPONSE);
                }
                foundResults = gotGreetingsIntent && gotWeatherIntent && gotWeatherResponse;
            }
            
            // ensure no errors
            vr = v.select("ArsRuleSnapshot", Collections.emptyList(), Collections.emptyMap(), null);
            List snaps = (List) vr.getBody();
            Assert.assertTrue("Found snapshots" + snaps, snaps == null || snaps.isEmpty());
        }
        Assert.assertTrue("Did not find expected results == greeting intent: " +
                                  gotGreetingsIntent + ", weather intent: " + gotWeatherIntent +
                                  ", weather response: " + gotWeatherResponse,
                            foundResults);
    }

    public void setupConversationalResources() {
        setupChatSource();

        // Create the mock procedure that will fake a LUIS source and respond with "intents"
        String fauxCLUProc = "\n" +
            "procedure NLTest.fauxCLUService(query Object)\n" +
            "// Here, we put in a simplistic NLI to appease the tests\n" +
            "var nlUtterance = query.body.analysisInput.conversationItem.text\n" +
            "var theIntent = null\n" +
            "var theEnts = []\n" +
            "if (nlUtterance.equals(\"hi\")) {\n" +
            "    theIntent = \"system.smalltalk.greetings\"\n" +
            "} else if (nlUtterance.equals(\"good afternoon\")) {\n" +
            "    theIntent = \"system.smalltalk.greetings\"\n" +
            "} else if (nlUtterance.equals(\"thank you\")) {\n" +
            "    theIntent = \"system.smalltalk.thankYou\"\n" +
            "} else if (nlUtterance.equals(\"what is the temperature?\")) {\n" +
            "    theIntent = \"WeatherService.getWeather\"\n" +
            "} else {\n" +
            "    theIntent = \"Unknown\"\n" +
            "}\n" +
            "\nvar retVal = {}\n" +
            "retVal.Kind = \"ConversationResult\"\n" +
            "retVal.result = {}\n" +
            "retVal.result.query = nlUtterance\n" +
            "retVal.result.prediction = {}\n" +
            "retVal.result.prediction.projectKind = \"Conversation\"\n" +
            "retVal.result.prediction.topIntent = theIntent\n" +
            "retVal.result.prediction.intents = [ { confidenceScore: .999, category: theIntent } ]\n" +
            "retVal.result.prediction.entities = theEnts\n" +
            "\nif (theIntent == \"Unknown\") {\n" +
            "    retVal.foo.bar.baz = \"I will cause a snapshot\"\n" +
            "}\n" +
            "log.info(\"FauxCluService() returning {}\", [retVal])\n" +
            "\nreturn retVal\n";
        NatLangTestBase.registerProcedure(v, fauxCLUProc);

        String customIntentInterpret = "\n" +
            "PROCEDURE CustomIntentProc(event Object)\n" +
            "var intent = event.intentSpecification\n" +
            "return {response: \"" + WEATHER_RESPONSE + "\"}\n";
        NatLangTestBase.registerProcedure(v, customIntentInterpret);

        // Create the CLU source and set it to MOCK mode using the procedure above when the Source is queried
        LinkedHashMap<Object, Object> source = new LinkedHashMap<Object, Object>();
        source.put("name", "CluSource");
        source.put("type", "REMOTE");
        source.put("mockMode", true);
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
        map.put("query", "NLTest.fauxCLUService");
        source.put("mockProcedures", map);
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(6);
        map1.put("uri", TEST_SERVER + "/api/v1/resources");
        map1.put("accessToken", v.getAccessToken());
        map1.put("contentType", "application/json");
        map1.put("keepAliveInterval", 0);
        map1.put("connectionTimeout", 0);
        LinkedHashMap<String, String> map2 = new LinkedHashMap<String, String>(1);
        map2.put("path", "/api/v1/resources/procedures/NLTest.fauxCLUService");
        map1.put("requestDefaults", map2);
        source.put("config", map1);

        v.update("system.sources", source.get("name").toString(), source);
    }

    public void setupChatSource() {
        String proc = "\n" +
            "PROCEDURE sendMessage(msg)\n" +
            "createSourceEvent(\"MyChatBot\", msg)\n";
        NatLangTestBase.registerProcedure(v, proc);
        
        // Create the app source
        LinkedHashMap<Object, Object> appSource = new LinkedHashMap<Object, Object>();
        appSource.put("name", "MyChatBot");
        appSource.put("type", "CHATBOT");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("clientId", "blah-blah-blah-blah-blah");
        config.put("clientSecret", "n0TSoS3cr3tN0w!");
        appSource.put("config", config);
        v.update("system.sources", appSource.get("name").toString(), appSource);
    }
}
