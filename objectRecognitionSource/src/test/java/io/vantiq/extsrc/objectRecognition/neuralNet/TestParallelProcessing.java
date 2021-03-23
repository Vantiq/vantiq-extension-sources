package io.vantiq.extsrc.objectRecognition.neuralNet;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class TestParallelProcessing extends NeuralNetTestBase {

    static ObjectRecognitionCore core;
    static Vantiq vantiq;

    static final String FAKE_MODEL_DIR = "";
    static final int CORE_START_TIMEOUT = 10;
    static final String IP_CAMERA_ADDRESS = "http://207.192.232.2:8000/mjpg/video.mjpg";

    @BeforeClass
    public static void classSetup() {
        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }

    @AfterClass
    public static void tearDown() {
        // Double check that everything was deleted from VANTIQ
        deleteSource(vantiq);
        deleteType(vantiq);
        deleteRule(vantiq);
    }

    @Test
    public void testDiscardPolicyDefaultThreadConfig() throws InterruptedException {
        // The default values for maxRunningThreads and maxQueuedTasks are 10 and 20 respectively
        // The pollTime is set to 100 (10 fps). Since the TestProcessor sleepTime is set to 5 seconds, we know that 10 fps is impossible
        // The first 10 tasks should take 5 seconds to complete, as well as the next 10. We know we should have ~20 entries after waiting 10 seconds.
        // The timing is not exact, so we make sure we are under 30 total, (well below the 100 we would see if the TestProcessor sleepTime was 0).
        discardPolicyTestHelper(false, 30);
    }

    @Test
    public void testDiscardPolicyCustomThreadConfig() throws InterruptedException {
        // The custom values for maxRunningThreads and maxQueuedTasks are 5 and 10 respectively
        // The pollTime is set to 100 (10 fps). Since the TestProcessor sleepTime is set to 5 seconds, we know that 10 fps is impossible
        // The first 5 tasks should take 5 seconds to complete, as well as the next 5. We know we should have ~10 entries after waiting 10 seconds.
        // The timing is not exact, so we make sure we are under 20 total, (well below the 100 we would see if the TestProcessor sleepTime was 0).
        discardPolicyTestHelper(true, 20);
    }

    public void discardPolicyTestHelper(boolean useCustomTaskConfig, int numberOfEntries) throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        // Check that Source, Type, Topic, Procedure and Rule do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists(vantiq));
        assumeFalse(checkTypeExists(vantiq));
        assumeFalse(checkRuleExists(vantiq));

        // Setup a VANTIQ Obj Rec Source, and start running the core
        setupSource(createSourceDef(useCustomTaskConfig));

        // Create Type to store results
        setupType();

        // Create Rule to store results in Type
        setupRule();

        // Wait for 10 seconds while the source polls for frames from the data source and queues up tasks.
        Thread.sleep(10000);

        // Make sure that appropriate number of entries are stored in type (this means discard policy works, and core is still alive)
        VantiqResponse response = vantiq.select(testTypeName, null, null, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        assert responseBody.size() < numberOfEntries;

        // We also make sure that the core did not close, proving the discard policy works and no fatal exceptions were thrown
        assert core.pollTimer != null;
        assert core.pool != null;

        // Delete the Source/Type/Rule from VANTIQ
        core.close();
        deleteSource(vantiq);
        deleteType(vantiq);
        deleteRule(vantiq);
    }

    // ================================================= Helper functions =================================================

    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new ObjectRecognitionCore(testSourceName, testAuthToken, testVantiqServer, FAKE_MODEL_DIR);
            core.start(CORE_START_TIMEOUT);
        }
    }

    public static Map<String,Object> createSourceDef(boolean useCustomTaskConfig) {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> objRecConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        Map<String,Object> dataSource = new LinkedHashMap<String,Object>();
        Map<String,Object> neuralNet = new LinkedHashMap<String,Object>();

        // Setting up general config options
        general.put("pollTime", 100);

        // Setting up general config options
        if (useCustomTaskConfig) {
            general.put("maxRunningThreads", 5);
            general.put("maxQueuedTasks", 10);
        }

        // Setting up dataSource config options
        dataSource.put("camera", IP_CAMERA_ADDRESS);
        dataSource.put("type", "network");

        // Setting up neuralNet config options
        neuralNet.put("type", "test");
        neuralNet.put("sleepTime", 5000);

        // Placing general config options in "objRecConfig"
        objRecConfig.put("general", general);
        objRecConfig.put("dataSource", dataSource);
        objRecConfig.put("neuralNet", neuralNet);

        // Putting objRecConfig in the source configuration
        sourceConfig.put("objRecConfig", objRecConfig);

        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", OR_SRC_TYPE);
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");

        return sourceDef;
    }

    public static void setupType() {
        Map<String,Object> typeDef = new LinkedHashMap<String,Object>();
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        Map<String,Object> propertyDef = new LinkedHashMap<String,Object>();
        propertyDef.put("type", "Integer");
        propertyDef.put("required", true);
        properties.put("id", propertyDef);
        typeDef.put("properties", properties);
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }

    public static void setupRule() {
        String rule = "RULE " + testRuleName + "\n"
                + "WHEN EVENT OCCURS ON \"/sources/" + testSourceName + "\" AS sourceEvent\n"
                + "INSERT " + testTypeName + "(id: 1)";

        vantiq.insert("system.rules", rule);
    }
}
