package io.vantiq.extsrc.naturallanguage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;

@Slf4j
public class NLUtilsTest extends NatLangTestBase {
    
    Vantiq v = new Vantiq(TEST_SERVER, 1);
    
    @BeforeClass
    public static void setupCat() {
        performSetup(Collections.EMPTY_MAP);
    }
    @Before
    public void setup() {
        v.authenticate(SUB_USER, SUB_USER);
        String callPrepTextProc = "\n" +
                "package nlp.test\n" +
                "import service com.vantiq.nlp.NLUtils\n" +
                "procedure NLTest.callPrepText(channel String, incoming Boolean, msg String)\n" +
                "\n    var retVal\n" +
                "    try {\n" +
                "        retVal = NLUtils.prepareText(channel, incoming, msg)\n" +
                "    }\n" +
                "    catch (err) {\n" +
                "        retVal = format(\"prepareText() call threw exception on <<msg>>: {0} for channel {1}, incoming: {2}: errCode: {3}, err: {4}\", msg, channel, incoming, err.code, err.message)\n" +
                "    }\n" +
                "    return retVal\n";
        registerProcedure(v, callPrepTextProc);
    }
    
    @After
    public void cleanup() {
        deleteProcedure(v, "nlp.test.NLTest.callPrepText");
    }
    
    public static void testOp(String testType, String expected, VantiqResponse vr) {
        assertTrue("Failed on " + testType + "::" + vr.getErrors(), vr.isSuccess());
        log.debug("Actual return: {}", vr);
        if (vr.getBody() instanceof JsonPrimitive) {
            assertEquals("Unexpected message from " + testType, expected,
                         ((JsonPrimitive) vr.getBody()).getAsString());
        } else if (vr.getBody() instanceof JsonNull) {
            assertNull("Unexpected message from " + testType, expected);
        }
    }
    
    @Test
    public void directlineTest() {
        String testMsg = "I am a test message";
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(3);
        map.put("channel", DIRECTLINE);
        map.put("incoming", true);
        map.put("msg", testMsg);
        VantiqResponse vr = v.execute("nlp.test.NLTest.callPrepText", map);
        testOp(DIRECTLINE, testMsg, vr);

        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(3);
        map1.put("channel", DIRECTLINE);
        map1.put("incoming", false);
        map1.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map1);
        testOp(DIRECTLINE, testMsg, vr);
        
        testMsg = "I am a multi-line\n" +
                            "test message.";
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
        map2.put("channel", DIRECTLINE);
        map2.put("incoming", true);
        map2.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map2);
        testOp(DIRECTLINE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(3);
        map3.put("channel", DIRECTLINE);
        map3.put("incoming", false);
        map3.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map3);
        testOp(DIRECTLINE, testMsg, vr);
        
        testMsg = "• I have bullets • •";
        final String expectedOutputString = "* I have bullets * *";
        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(3);
        map4.put("channel", DIRECTLINE);
        map4.put("incoming", true);
        map4.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map4);
        testOp(DIRECTLINE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<String, Serializable>(3);
        map5.put("channel", DIRECTLINE);
        map5.put("incoming", false);
        map5.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map5);
        testOp(DIRECTLINE, expectedOutputString, vr);
    }
    
    @Test
    public void webchatTest() {
        String testMsg = "I am a test message";
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(3);
        map.put("channel", WEBCHAT);
        map.put("incoming", true);
        map.put("msg", testMsg);
        VantiqResponse vr = v.execute("nlp.test.NLTest.callPrepText", map);
        testOp(WEBCHAT, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(3);
        map1.put("channel", WEBCHAT);
        map1.put("incoming", false);
        map1.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map1);
        testOp(WEBCHAT, testMsg, vr);
        
        testMsg = "I am a multi-line\n" +
                            "test message.";
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
        map2.put("channel", WEBCHAT);
        map2.put("incoming", true);
        map2.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map2);
        testOp(WEBCHAT, testMsg, vr);

        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(3);
        map3.put("channel", WEBCHAT);
        map3.put("incoming", false);
        map3.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map3);
        testOp(WEBCHAT, "I am a multi-line  \\ntest message.", vr);
        
        testMsg = "• I have bullets • •";
        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(3);
        map4.put("channel", WEBCHAT);
        map4.put("incoming", true);
        map4.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map4);
        testOp(WEBCHAT, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<String, Serializable>(3);
        map5.put("channel", WEBCHAT);
        map5.put("incoming", false);
        map5.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map5);
        String expectedOutputString = "* I have bullets * *";
        
        testOp(WEBCHAT, expectedOutputString, vr);
        
        testMsg = "I am a \"test message\"\\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map6 = new LinkedHashMap<String, Serializable>(3);
        map6.put("channel", WEBCHAT);
        map6.put("incoming", true);
        map6.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map6);
        testOp(WEBCHAT, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map7 = new LinkedHashMap<String, Serializable>(3);
        map7.put("channel", WEBCHAT);
        map7.put("incoming", false);
        map7.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map7);
        expectedOutputString = "I am a 'test message'  \\nthat has a <number> of <lines> & <characters>.";
        testOp(WEBCHAT, expectedOutputString, vr);
    }
    
    @Test
    public void noWorkTest() {
        String testMsg = null;
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(3);
        map.put("channel", SOMETHING_ELSE);
        map.put("incoming", true);
        map.put("msg", (Serializable) testMsg);
        VantiqResponse vr = v.execute("nlp.test.NLTest.callPrepText", map);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(3);
        map1.put("channel", SOMETHING_ELSE);
        map1.put("incoming", false);
        map1.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map1);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        testMsg = "I am a test message";
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
        map2.put("channel", SOMETHING_ELSE);
        map2.put("incoming", true);
        map2.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map2);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(3);
        map3.put("channel", SOMETHING_ELSE);
        map3.put("incoming", false);
        map3.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map3);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        testMsg = "I am a <test message>";
        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(3);
        map4.put("channel", SOMETHING_ELSE);
        map4.put("incoming", true);
        map4.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map4);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<String, Serializable>(3);
        map5.put("channel", SOMETHING_ELSE);
        map5.put("incoming", false);
        map5.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map5);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        testMsg = "I am a \"test message\"\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map6 = new LinkedHashMap<String, Serializable>(3);
        map6.put("channel", SOMETHING_ELSE);
        map6.put("incoming", true);
        map6.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map6);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map7 = new LinkedHashMap<String, Serializable>(3);
        map7.put("channel", SOMETHING_ELSE);
        map7.put("incoming", false);
        map7.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map7);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        testMsg = "I am a &guot;test message&quot;\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map8 = new LinkedHashMap<String, Serializable>(3);
        map8.put("channel", SOMETHING_ELSE);
        map8.put("incoming", true);
        map8.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map8);
        testOp(SOMETHING_ELSE, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map9 = new LinkedHashMap<String, Serializable>(3);
        map9.put("channel", SOMETHING_ELSE);
        map9.put("incoming", false);
        map9.put("msg", (Serializable) testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map9);
        testOp(SOMETHING_ELSE, testMsg, vr);
    }
    
    @Test
    public void msTeamsTest() {
        String testMsg = "I am a test message";
        String sillyBotAddress = "<at>HeyYouBot</at>";
        
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(3);
        map.put("channel", MSTEAMS);
        map.put("incoming", true);
        map.put("msg", sillyBotAddress + testMsg);
        VantiqResponse vr = v.execute("nlp.test.NLTest.callPrepText", map);
        testOp(MSTEAMS, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(3);
        map1.put("channel", MSTEAMS);
        map1.put("incoming", false);
        map1.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map1);
        testOp(MSTEAMS, testMsg, vr);
        
        testMsg = "I am a <test message>";
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
        map2.put("channel", MSTEAMS);
        map2.put("incoming", true);
        map2.put("msg", sillyBotAddress + testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map2);
        testOp(MSTEAMS, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(3);
        map3.put("channel", MSTEAMS);
        map3.put("incoming", false);
        map3.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map3);
        testOp(MSTEAMS, testMsg, vr);
        
        // These next two verify that if the silly MSTeams header doesn't come along, that we do no harm.

        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(3);
        map4.put("channel", MSTEAMS);
        map4.put("incoming", true);
        map4.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map4);
        testOp(MSTEAMS, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<String, Serializable>(3);
        map5.put("channel", MSTEAMS);
        map5.put("incoming", false);
        map5.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map5);
        testOp(MSTEAMS, testMsg, vr);
        
        testMsg = "I am a <test message>";
        LinkedHashMap<String, Serializable> map6 = new LinkedHashMap<String, Serializable>(3);
        map6.put("channel", MSTEAMS);
        map6.put("incoming", true);
        map6.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map6);
        testOp(MSTEAMS, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map7 = new LinkedHashMap<String, Serializable>(3);
        map7.put("channel", MSTEAMS);
        map7.put("incoming", false);
        map7.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map7);
        testOp(MSTEAMS, testMsg, vr);
        
        testMsg = "I am a \"test message\"\\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map8 = new LinkedHashMap<String, Serializable>(3);
        map8.put("channel", MSTEAMS);
        map8.put("incoming", true);
        map8.put("msg", sillyBotAddress + testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map8);
        testOp(MSTEAMS, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map9 = new LinkedHashMap<String, Serializable>(3);
        map9.put("channel", MSTEAMS);
        map9.put("incoming", false);
        map9.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map9);
        String expectedString = "I am a 'test message'<br/>that has a <number> of <lines> & <characters>.";
        
        testOp(MSTEAMS, expectedString, vr);
        
        testMsg = "I am a &quot;test message&quot;\\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map10 = new LinkedHashMap<String, Serializable>(3);
        map10.put("channel", MSTEAMS);
        map10.put("incoming", true);
        map10.put("msg", sillyBotAddress + testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map10);
        testOp(MSTEAMS, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map11 = new LinkedHashMap<String, Serializable>(3);
        map11.put("channel", MSTEAMS);
        map11.put("incoming", false);
        map11.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map11);
        expectedString = "I am a &quot;test message&quot;<br/>that has a <number> of <lines> & <characters>.";
        testOp(MSTEAMS, expectedString, vr);
    }
    
    @Test
    public void slackTest() {
        String testMsg = "I am a test message";
        
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(3);
        map.put("channel", SLACK);
        map.put("incoming", true);
        map.put("msg", testMsg);
        VantiqResponse vr = v.execute("nlp.test.NLTest.callPrepText", map);
        testOp(SLACK, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<String, Serializable>(3);
        map1.put("channel", SLACK);
        map1.put("incoming", false);
        map1.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map1);
        testOp(SLACK, testMsg, vr);
        
        testMsg = "I am a <test message>";
        LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
        map2.put("channel", SLACK);
        map2.put("incoming", true);
        map2.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map2);
        testOp(SLACK, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map3 = new LinkedHashMap<String, Serializable>(3);
        map3.put("channel", SLACK);
        map3.put("incoming", false);
        map3.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map3);
        String exp = "I am a &lt;test message&gt;";
        testOp(SLACK, exp, vr);
        
        testMsg = "I am a \"test message\"\\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map4 = new LinkedHashMap<String, Serializable>(3);
        map4.put("channel", SLACK);
        map4.put("incoming", true);
        map4.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map4);
        testOp(SLACK, testMsg, vr);
        
        LinkedHashMap<String, Serializable> map5 = new LinkedHashMap<String, Serializable>(3);
        map5.put("channel", SLACK);
        map5.put("incoming", false);
        map5.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map5);
        String expectedString = "I am a &quot;test message&quot;   \\nthat has a &lt;number&gt; of &lt;lines&gt; " +
                "&amp; &lt;characters&gt;.";
        
        testOp(SLACK, expectedString, vr);
        
        testMsg = "I am a &quot;test message&quot;\\nthat has a <number> of <lines> & <characters>.";
        LinkedHashMap<String, Serializable> map6 = new LinkedHashMap<String, Serializable>(3);
        map6.put("channel", SLACK);
        map6.put("incoming", true);
        map6.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map6);
        expectedString = "I am a \"test message\"\\nthat has a <number> of <lines> & <characters>.";
        
        testOp(SLACK, expectedString, vr);
        
        LinkedHashMap<String, Serializable> map7 = new LinkedHashMap<String, Serializable>(3);
        map7.put("channel", SLACK);
        map7.put("incoming", false);
        map7.put("msg", testMsg);
        vr = v.execute("nlp.test.NLTest.callPrepText", map7);
        expectedString = "I am a &amp;quot;test message&amp;quot;   \\nthat has a &lt;number&gt; of &lt;" +
                "lines&gt;" +
                " &amp; &lt;characters&gt;.";
        testOp(SLACK, expectedString, vr);
    }
    
    private static final String DIRECTLINE = "directline";
    private static final String SLACK = "slack";
    private static final String MSTEAMS = "msteams";
    private static final String WEBCHAT = "webchat";
    private static final String SOMETHING_ELSE = "somethingElse";
}
