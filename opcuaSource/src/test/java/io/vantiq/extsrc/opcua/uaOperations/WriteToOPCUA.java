package io.vantiq.extsrc.opcua.uaOperations;

import org.eclipse.milo.examples.server.ExampleNamespace;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.fail;

@Slf4j
public class WriteToOPCUA extends OpcUaTestBase {

    @Test
    public void testWriteSimpleIntegers() {

        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.Basic256Sha256.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_MESSAGE_SECURITY_MODE, MessageSecurityMode.SignAndEncrypt.toString());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        OpcUaESClient client = Utils.makeConnection(config, false);
        Assert.assertNotNull("No client returned", client);

        performWrites(client,
                ExampleNamespace.NAMESPACE_URI,
                Utils.EXAMPLE_NS_SCALAR_INT32_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_INT32_TYPE,
                42,
                0);
    }

    @Test
    public void testWriteSimpleStrings() {

        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        OpcUaESClient client = Utils.makeConnection(config, false);
        Assert.assertNotNull("No client returned", client);

        performWrites(client,
                ExampleNamespace.NAMESPACE_URI,
                Utils.EXAMPLE_NS_SCALAR_STRING_IDENTIFIER,
                Utils.EXAMPLE_NS_SCALAR_STRING_TYPE,
                42,
                0);
    }

    // Factored out for multiple use herein + use outside this class.
    public static void performWrites(OpcUaESClient client, String namespace, String identifier, String dType, int repetitions, int delay) {
        try {
            String prefix = "";
            if (dType == "String") {
                prefix = "I am number ";
            }
            int startingValue = 42;
            int limitValue = startingValue + repetitions;
            for (int i = startingValue ; i < limitValue ; i++){
                Object valueToWrite = null;
                if (dType == "String") {
                    valueToWrite = prefix + i;
                } else if (dType == "Int32") {
                    valueToWrite = Integer.valueOf(i);
                } else {
                    fail("TEST ISSUE (not product): Unexpected data type for performWrites: " + dType);
                }
                client.writeValue(namespace,
                        identifier,
                        dType,
                        valueToWrite);
                Object value = client.readValue(namespace, identifier);
                if (delay != 0) {
                    Thread.sleep(delay);
                }
                Assert.assertTrue("read value & written value class mismatch", valueToWrite.getClass().isInstance(value));
                Assert.assertEquals("Value mismatch", value, valueToWrite);
            }

        }
        catch (Exception e) {
            fail("Unexpected Exception: " + Utils.errFromExc(e));
        }
    }

    @Test
    public void testBogusWrites() {

        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        OpcUaESClient client = Utils.makeConnection(config, false);

        try {
            client.writeValue(ExampleNamespace.NAMESPACE_URI + "IAmNotThere", "HelloWorld/ScalarTypes/Int32", "Int32", new Integer(42));
        }
        catch (OpcExtRuntimeException e) {
            if (!e.getMessage().startsWith(OpcUaESClient.ERROR_PREFIX + ".badNamespaceURN")) {
                fail("Unexpected OpcExtRuntimeException: " + Utils.errFromExc(e));
            }
        }
        catch (Exception e) {
            fail("Unexpected Exception: " + Utils.errFromExc(e));
        }

        try {
            client.writeValue(ExampleNamespace.NAMESPACE_URI, "HelloWorld/ScalarTypes/Int32" + "/IAmNotThere", "Int32", 42);
        }
        catch (OpcExtRuntimeException e) {
            if (!e.getMessage().startsWith(OpcUaESClient.ERROR_PREFIX + ".writeError")) {
                fail("Unexpected OpcExtRuntimeException: " + Utils.errFromExc(e));
            }
        }
        catch (Exception e) {
            fail("Unexpected Exception: " + Utils.errFromExc(e));
        }

        try {
            // The following will fail because we're trying to write a float/double to an integer type
            client.writeValue(ExampleNamespace.NAMESPACE_URI, "HelloWorld/ScalarTypes/Int32", "Int32", 3.14159);
        }
        catch (OpcExtRuntimeException e) {
            if (!e.getMessage().startsWith(OpcUaESClient.ERROR_PREFIX + ".writeError")) {
                fail("Unexpected OpcExtRuntimeException: " + Utils.errFromExc(e));
            }
        }
        catch (Exception e) {
            fail("Unexpected Exception: " + Utils.errFromExc(e));
        }

        try {
            client.writeValue(ExampleNamespace.NAMESPACE_URI, "HelloWorld/ScalarTypes/Int32", "Int32", "I am most assuredly NOT an integer of any length!");
        }
        catch (OpcExtRuntimeException e) {
            if (!e.getMessage().startsWith(OpcUaESClient.ERROR_PREFIX + ".writeError")) {
                fail("Unexpected OpcExtRuntimeException: " + Utils.errFromExc(e));
            }
        }
        catch (Exception e) {
            fail("Unexpected Exception: " + Utils.errFromExc(e));
        }
    }

}
