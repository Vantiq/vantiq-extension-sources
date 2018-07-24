package io.vantiq.extsrc.opcua.uaOperations;

import org.eclipse.milo.examples.server.ExampleNamespace;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;


import static org.junit.Assert.fail;

@Slf4j
public class Monitoring extends OpcUaTestBase {

    public AtomicInteger updateCount;
    public AtomicInteger eventErrorCount;
    List<Class> expectedClass;
    List<String> expectedNodeIdentifier;
    Map<String, Integer> updateByNode = new HashMap<>();
    int repetitionCount = 10;
    int msDelayBetweenUpdates = 1500;

    @Test
    public void testTrivialMonitor() {

        resetUpdateCounters();

        HashMap config = new HashMap();
        Map<String, Object> opcConfig = new HashMap<>();
        Map<String, Map<String, String>> misMap = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/tmp/opcua-storage");
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        // Here, we'll create a simple map set that creates a monitored
        opcConfig.put(OpcUaESClient.CONFIG_OPC_MONITORED_ITEMS, misMap);
        Map timeMap = new HashMap<>();

        // Leaving since we use it elsewhere.
        // For most node identification, we cannot trust the namespace index to remain stable across reboots.
        // In practice, it usually does, but it is not guaranteed to do so.  Thus, we should always
        // really use the URN to identify the nodes.
        //
        // It's also the case that purportedly ns=0 is reserved for OPC, is it should always be valid.
        // Nonetheless, we'll test that it works both ways...

//        timeMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_INDEX,
//                Identifiers.Server_ServerStatus_CurrentTime.getNamespaceIndex().toString());
        timeMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_URN,
                Namespaces.OPC_UA);
        timeMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER,
                Identifiers.Server_ServerStatus_CurrentTime.getIdentifier().toString());
        timeMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER_TYPE, "i");  // This ident is a number...
        misMap.put("Server Time", timeMap);

        OpcUaESClient client = Utils.makeConnection(config, false);

        // Now, we'll update our monitored items list & see if we get any actual values...

        try {
            expectedClass = Collections.singletonList(DateTime.class);
            expectedNodeIdentifier = Collections.singletonList(Identifiers.Server_ServerStatus_CurrentTime.getIdentifier().toString());
            client.updateMonitoredItems(config, this::checkSimpleUpdate);

            // Now, let's wait a bit to see if we get any updates...
            log.info("Stalling a bit...");
            Thread.sleep(10 * 1000);
            log.debug("Update Count: " + updateCount);
            Assert.assertEquals("Errors recorded by event processor", 0, eventErrorCount.get());
            Assert.assertTrue("Not enough updates", updateCount.get() > 8);

        }
        catch (Exception e) {
            if (e.getCause() != null) {
                log.error(Utils.errFromExc(e.getCause()));
                e.getCause().printStackTrace();
            }
            fail("Unexpected Exception updating monitored items: " + Utils.errFromExc(e));
        }
    }

    private void checkSimpleUpdate(String nodeInfo, Object newValue) {
        try {
            updateCount.incrementAndGet();
            log.debug(">>>> Update number {}: Node: {}, newValue: {} ", updateCount.get(), nodeInfo, newValue.toString());
            if (!updateByNode.containsKey(nodeInfo)) {
                updateByNode.put(nodeInfo, 0);
            }
            updateByNode.put(nodeInfo, updateByNode.get(nodeInfo) + 1);

            boolean found = false;

            for (String expected: expectedNodeIdentifier) {
                if (nodeInfo.contains(expected)) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue("Incorrect node information: " + nodeInfo + " should contain items from: " + expectedNodeIdentifier,
                    found);
            Assert.assertTrue("Wrong class for updated object: " + newValue.getClass().getName() +
                    ", should be: " + expectedClass.getClass().getName(), expectedClass.contains(newValue.getClass()));
        }
        catch (AssertionError e) {
            eventErrorCount.incrementAndGet();
            log.error("Trapped assertion error during event processing: ", e);
        }
        catch (Throwable e) {
            eventErrorCount.incrementAndGet();
            log.error("Trapped unexpected error during event processing: ", e);
        }
    }

    private void resetUpdateCounters() {
        updateCount = new AtomicInteger(0);
        eventErrorCount = new AtomicInteger(0);
        updateByNode = new HashMap<>();
    }

    @Test
    public void testBasicMonitor() {
        resetUpdateCounters();

        HashMap config = new HashMap();
        Map<String, Object> opcConfig = new HashMap<>();
        Map<String, Map<String, String>> misMap = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/tmp/opcua-storage");
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        // Here, we'll create a simple map set that creates a monitored
        opcConfig.put(OpcUaESClient.CONFIG_OPC_MONITORED_ITEMS, misMap);
        Map miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_URN,
                ExampleNamespace.NAMESPACE_URI);
        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER);

        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER_TYPE, "s");  // This ident is a string

        misMap.put("Hello World Int 32", miMap);

        OpcUaESClient client = Utils.makeConnection(config, false);

        // Now, we'll update our monitored items list & see if we get any actual values...

        try {
            expectedClass = Collections.singletonList(Integer.class);
            expectedNodeIdentifier = Collections.singletonList(Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER);
            client.updateMonitoredItems(config, this::checkSimpleUpdate);

            // Now, let's perform some actual updates & make sure we're notified.

            // In this case, we're going to perform 20 updates, stalling for 1.5 seconds between each update.
            // Since the monitoring probes for updates 1/second, this should give our server sufficient
            // time to tell us about what's going on.
            //
            // At the end, we'll verify that we saw roughly the correct amount of updates.
            // Note that the "expected behavior" is that the server will monitor for data
            // changes only on the periods about which we want to be informed.  So if some
            // node's value changes 3 times in a second, if we're probing only once per second,
            // we probably won't see all the updates.
            // (Best we can do is "roughly" since the OPC UA server isn't really real-time.)

            int repetitionCount = 20;
            WriteToOPCUA.performWrites(client,
                    ExampleNamespace.NAMESPACE_URI,
                    Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER,
                    Utils.EXAMPLE_NS_SCALAR_INT32_TYPE, repetitionCount, msDelayBetweenUpdates);

            log.debug("Update Count: " + updateCount);
            Assert.assertEquals("Errors recorded by event processor", 0, eventErrorCount.get());
            Assert.assertTrue("Not enough updates", updateCount.get() > repetitionCount * .90);
            // We may be notified of the initial setting our first check.
            Assert.assertTrue("Too many repetitions",updateCount.get() <= repetitionCount + 1);

        }
        catch (Exception e) {
            if (e.getCause() != null) {
                log.error(Utils.errFromExc(e.getCause()));
                e.getCause().printStackTrace();
            }
            fail("Unexpected Exception updating monitored items: " + Utils.errFromExc(e));
        }
    }

    @Ignore     // this is a simpler version of testUpdateMonitor.  That test is sufficient at present.
    @Test
    public void testMultiMonitor() {
        resetUpdateCounters();

        HashMap config = new HashMap();
        Map<String, Object> opcConfig = new HashMap<>();
        Map<String, Map<String, String>> misMap = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/tmp/opcua-storage");
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        // Here, we'll create a simple map set that creates a monitored
        opcConfig.put(OpcUaESClient.CONFIG_OPC_MONITORED_ITEMS, misMap);

        Map miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_URN,
                ExampleNamespace.NAMESPACE_URI);
        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER);
        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER_TYPE, "s");

        misMap.put("Hello World Int 32", miMap);

        miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_URN,
                ExampleNamespace.NAMESPACE_URI);
        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_STRING_IDENTIFIER);

        // This time, we'll leave the string designation out since it's the default...

        misMap.put("Hello World String", miMap);

        // Here, we'll test that the nodeId form of the node specification works as expected
        // Note: NodeId.toParseableString() returns the "ns=0;i=2258" form of the node id.
        //       NodeId.toString() returns the "NodeId{ns=0, id=2258}" form which isn't accepted as input to OPC UA.
        // Thus, we'll use the more accepted version here for passing into the OPC system.

        miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NODEID,
                Identifiers.Server_ServerStatus_CurrentTime.toParseableString());
        misMap.put("Server Time", miMap);

        Assert.assertEquals("Test is requesting wrong number of items(expecting 3): " + misMap.size(), 3, misMap.size());



        OpcUaESClient client = Utils.makeConnection(config, false);

        // Now, we'll update our monitored items list & see if we get any actual values...

        int expectedRepetitionCount = ((2 * repetitionCount) // "manual" changes above
                + 2)                  // Plus startup states sometimes
                + (repetitionCount * 2 * msDelayBetweenUpdates) / 1000;
        // twice manual changes * delay in seconds

        performUpdates(client, config, expectedRepetitionCount);

    }

    @Test
    public void testUpdateMonitor() {
        resetUpdateCounters();

        HashMap config = new HashMap();
        Map<String, Object> opcConfig = new HashMap<>();
        Map<String, Map<String, String>> misMap = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/tmp/opcua-storage");
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        // Here, we'll create a simple map set that creates a monitored item list
        opcConfig.put(OpcUaESClient.CONFIG_OPC_MONITORED_ITEMS, misMap);

        Map miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_URN,
                ExampleNamespace.NAMESPACE_URI);
        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER);

        // This time, we'll leave the string designation out since it's the default...

        misMap.put("Hello World Int 32", miMap);

        miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NAMESPACE_URN,
                ExampleNamespace.NAMESPACE_URI);
        miMap.put(OpcUaESClient.CONFIG_MI_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_STRING_IDENTIFIER);

        // This time, we'll leave the string designation out since it's the default...

        misMap.put("Hello World String", miMap);

        // Here, we'll test that the nodeId form of the node specification works as expected
        // Note: NodeId.toParseableString() returns the "s=0;i=2258" form of the node id.
        //       NodeId.toString() returns the "NodeId{ns=0, id=2258}" form which isn't accepted as input to OPC UA.
        // Thus, we'll use the more accepted version here for passing into the OPC system.

        miMap = new HashMap<>();
        miMap.put(OpcUaESClient.CONFIG_MI_NODEID,
                Identifiers.Server_ServerStatus_CurrentTime.toParseableString());
        misMap.put("Server Time", miMap);

        Assert.assertEquals("Test is requesting wrong number of items(expecting 3): " + misMap.size(), 3, misMap.size());

        OpcUaESClient client = Utils.makeConnection(config, false);

        // Now, we'll update our monitored items list & see if we get any actual values...

        // Here, the expected event update count will be the
        //   twice the repetitionCount + twice that turned into seconds for the server time updates.
        // Plus a few for initial changes which sometimes happen.

        int expectedEventCount = ((2 * repetitionCount) // "manual" changes above
                + 2)                  // Plus startup states sometimes
                + (repetitionCount * 2 * msDelayBetweenUpdates) / 1000
                // twice manual changes * delay in seconds
                + 1; // for startup (sometimes)

        performUpdates(client, config, expectedEventCount);

        // Now, we shall update our monitored item list to remove one of the things we're updating
        // from the monitored list.  We should end up with fewer items monitored.

        misMap.remove("Hello World Int 32");
        Assert.assertEquals("Test is requesting wrong number of items(expecting 2): " + misMap.size(), 2, misMap.size());

        // Here, the expected event update count will be the
        //   (once) repetitionCount + twice that turned into seconds for the server time updates.
        // Plus a few for initial changes which sometimes happen.

        expectedEventCount = ((1 * repetitionCount) // "manual" changes above
                + 2)                  // Plus startup states sometimes
                + (repetitionCount * 2 * msDelayBetweenUpdates) / 1000
                // twice manual changes * delay in seconds -- since we're still going to update both fields,
                // we just don't care about the updates so we don't count them
                + 1; // for startup (sometimes)
        resetUpdateCounters();
        performUpdates(client, config, expectedEventCount);
    }

    private void performUpdates(OpcUaESClient client, Map config, int expectedEventCount) {
        try {
            expectedClass = Arrays.asList(Integer.class, String.class, DateTime.class);
            expectedNodeIdentifier = Arrays.asList(Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER,
                    Utils.EXAMPLE_NS_SCALAR_STRING_IDENTIFIER,
                    Identifiers.Server_ServerStatus_CurrentTime.getIdentifier().toString());
            client.updateMonitoredItems(config, this::checkSimpleUpdate);

            // Now, let's perform some actual updates & make sure we're notified.

            // In this case, we're going to perform 20 updates, stalling for 1.5 seconds between each update.
            // Since the monitoring probes for updates 1/second, this should give our server sufficient
            // time to tell us about what's going on.
            //
            // At the end, we'll verify that we saw roughly the correct amount of updates.
            // Note that the "expected behavior" is that the server will monitor for data
            // changes only on the periods about which we want to be informed.  So if some
            // node's value changes 3 times in a second, if we're probing only once per second,
            // we probably won't see all the updates.
            // (Best we can do is "roughly" since the OPC UA server isn't really real-time.)

            int repetitionCount = 10;
            int msDelayBetweenUpdates = 1500;
            WriteToOPCUA.performWrites(client,
                    ExampleNamespace.NAMESPACE_URI,
                    Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER,
                    Utils.EXAMPLE_NS_SCALAR_INT32_TYPE, repetitionCount, msDelayBetweenUpdates);

            WriteToOPCUA.performWrites(client,
                    ExampleNamespace.NAMESPACE_URI,
                    Utils.EXAMPLE_NS_SCALAR_STRING_IDENTIFIER,
                    Utils.EXAMPLE_NS_SCALAR_STRING_TYPE, repetitionCount, msDelayBetweenUpdates);

            // Time updates will happen regardless :-)

            log.debug("Update Count: " + updateCount);
            log.debug("Repetition Count: " + repetitionCount);

            log.debug(">>>> Expected update event count: " + expectedEventCount);
            for (Map.Entry<String, Integer> ent : updateByNode.entrySet()) {
                log.debug("    Node: {} -> {} updates", ent.getKey(), ent.getValue());
            }
            Assert.assertEquals("Errors recorded by event processor", 0, eventErrorCount.get());
            Assert.assertTrue("Not enough updates", updateCount.get() > (expectedEventCount * .90));
            // We may be notified of the initial setting our first check.
            Assert.assertTrue("Too many updates.  Expected: " + updateCount.get() + " <= " + expectedEventCount,
                    updateCount.get() <= expectedEventCount);

        }
        catch (Exception e) {
            if (e.getCause() != null) {
                log.error(Utils.errFromExc(e.getCause()));
                e.getCause().printStackTrace();
            }
            fail("Unexpected Exception updating monitored items: " + Utils.errFromExc(e));
        }
    }
}
