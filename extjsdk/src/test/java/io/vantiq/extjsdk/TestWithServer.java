package io.vantiq.extjsdk;

import io.vantiq.client.VantiqResponse;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class TestWithServer extends RoundTripTestBase {
    
    @AfterClass
    public static void cleanup() {
        // If we started up, shut down nicely.  Otherwise, pass w/o issue
        if (vantiq != null && vantiq.isAuthenticated()) {
            deleteType();
            deleteRule();
            deleteSource();
            deleteSourceImpl();
        }
    }

    @Test
    public void testRepeatedConnects() throws Exception {

        assumeTrue(testRepeatedConnectsEnabled);    // TODO remove after 1.32 is out

        int repCount = 20;

        setupSourceImpl();
        setupSource(createSource());

        assert checkSourceExists();

        ExtensionWebSocketClient client = new ExtensionWebSocketClient(SOURCE_NAME);

        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        File serverConfigFile = new File("server.config");
        serverConfigFile.createNewFile();
        serverConfigFile.deleteOnExit();
        Utils.obtainServerConfig();

        // Now, to verify that things work as expected, repeatedly connect w/o close
        // This is verifying that the reconnect secret is being passed & used correctly.

        for (int i = 0; i < repCount; i++ ) {
            client = new ExtensionWebSocketClient(SOURCE_NAME);
            client.initiateFullConnection(testVantiqServer, testAuthToken).get();
            assertTrue("Failed to open connection", client.isOpen());
            assertTrue("Failed to authenticate", client.isAuthed());
            assertTrue("Failed to connect to source (may fail if run against Vantiq version < 1.32)",
                    client.isConnected());
        }
    }

    @Test
    public void testNotificationEnMasse() throws Exception {

        int MAX_TRIES = 100;
        int EXPECTED_ROW_COUNT = 5000;
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        setupType();
        setupSourceImpl();
        setupSource(createSource());
        setupRule();
        
        assert checkSourceExists();
        assert checkRuleExists();
        
        ExtensionWebSocketClient client = new ExtensionWebSocketClient(SOURCE_NAME);

        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        File serverConfigFile = new File("server.config");
        serverConfigFile.createNewFile();
        serverConfigFile.deleteOnExit();
        Utils.obtainServerConfig();

        client.initiateFullConnection(testVantiqServer, testAuthToken).get();

        List<String> simpleList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String junk = Instant.now().toString();
            simpleList.add(junk);
        }
        for (int i = 0; i < EXPECTED_ROW_COUNT; i++) {
            Map ntfy = new HashMap();
            ntfy.put("data", simpleList);
            ntfy.put("msgId", i);
            client.sendNotification(ntfy);
        }
        Thread.sleep(500);
        
        int rowCount = 0;
        for (int i = 0; (rowCount < EXPECTED_ROW_COUNT) && (i < MAX_TRIES); i++) {
            VantiqResponse vr = vantiq.select(testTypeName, null, null, null);
            rowCount = ((List) vr.getBody()).size();
            Thread.sleep(50);
        }
        System.out.println("RowCount: " + rowCount);
        assertEquals( "Expected " + EXPECTED_ROW_COUNT + ", found " + rowCount,
                EXPECTED_ROW_COUNT, rowCount);
    }
}
