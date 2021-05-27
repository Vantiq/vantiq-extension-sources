package io.vantiq.extjsdk;

import io.vantiq.client.VantiqResponse;
import org.junit.AfterClass;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public void testNotificationEnMasse() throws Exception {

        int MAX_TRIES = 50;
        int EXPECTED_ROW_COUNT = 5000;
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        setupType();
        setupSourceImpl();
        setupSource(createSource());
        setupRule();
        
        assert checkSourceExists();
        assert checkRuleExists();
        
        ExtensionWebSocketClient client = new ExtensionWebSocketClient(SOURCE_NAME);
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
        
        int rowCount = 0;
        for (int i = 0; (rowCount < EXPECTED_ROW_COUNT) && (i < MAX_TRIES); i++) {
            VantiqResponse vr = vantiq.select(testTypeName, null, null, null);
            rowCount = ((List) vr.getBody()).size();
            Thread.sleep(50);
        }
        System.out.println("RowCount: " + rowCount);
        assert rowCount == EXPECTED_ROW_COUNT;
    }
}
