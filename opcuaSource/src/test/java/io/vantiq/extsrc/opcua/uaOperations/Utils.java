/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.opcua.uaOperations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.fail;
import org.junit.Assume;

/**
 * A collection of Utility methods used by the OPC UA Extension Source unit tests
 */
@SuppressWarnings({"PMD.TooManyFields", "PMD.StaticVariableNameCheck"})
@Slf4j
public class Utils {

    // In the future, errors may occur if these are taken offline, etc.
    // This list is gathered from the somewhat maintained Wiki page:
    // https://github.com/node-opcua/node-opcua/wiki/publicly-available-OPC-UA-Servers-and-Clients
    //
    // We test against a number of servers because the different servers, configurations, and software used
    // have shown themselves to have various idiosyncrasies around which we have to work.
    //
    // At present, we'll check the "write value" stuff only on our internal server.

    // Leaving some things defined but not using them.  They are still listed in the "publicly available"
    // page show above, but are not responding.  Leaving so we can reinstate them easily should we so desire.
    public static final String OPC_PUBLIC_SERVER_1  = "opc.tcp://opcua.rocks:4840";
    public static final String OPC_PUBLIC_SERVER_2  = "opc.tcp://opcuaserver.com:48484";
    public static final String OPC_PUBLIC_SERVER_3  = "opc.tcp://opcua-demo.factry.io:51210";
    public static final String OPC_PUBLIC_SERVER_4  = "opc.tcp://commsvr.com:51234/UA/CAS_UA_Server";
    public static final String OPC_PUBLIC_SERVER_5  = "opc.tcp://milo.digitalpetri.com:62541/milo";
    public static final String OPC_PUBLIC_SERVER_6  = "opc.tcp://opcua.123mc.com:4840/";
    public static final String OPC_PUBLIC_SERVER_7  = "opc.tcp://mfactorengineering.com:4840";
    public static final String OPC_PUBLIC_SERVER_8 = "opc.tcp://opcua.machinetool.app:4840";
    public static final String OPC_PUBLIC_SERVER_9 = "opc.tcp://opcua.umati.app:4840";
    
    
    public static final List<String> OPC_PUBLIC_SERVERS = Arrays.asList(
//            OPC_PUBLIC_SERVER_1,
//            OPC_PUBLIC_SERVER_2,
            OPC_PUBLIC_SERVER_3,
//            OPC_PUBLIC_SERVER_4,  // not responding
            OPC_PUBLIC_SERVER_5,  // returns that the service is unsupported.
            OPC_PUBLIC_SERVER_6,
            OPC_PUBLIC_SERVER_7, // Flaky support -- sometimes times out after discovery, sometimes before
            OPC_PUBLIC_SERVER_8,
            OPC_PUBLIC_SERVER_9
    );

    public static final String OPC_INPROCESS_SERVER = "opc.tcp://localhost:12686/milo";
    public static final String OPC_PUBLIC_SERVER_NO_GOOD = "opc.tcp://opcuaserver.com:4840";

    public static final String EXAMPLE_NS_SCALAR_INT32_IDENTIFIER = "HelloWorld/ScalarTypes/Int32";
    public static final String EXAMPLE_NS_SCALAR_INT32_TYPE = "Int32";
    public static final String EXAMPLE_NS_SCALAR_STRING_IDENTIFIER = "HelloWorld/ScalarTypes/String";
    public static final String EXAMPLE_NS_SCALAR_STRING_TYPE = "String";

    /**
     * Turn an exception into readable text for test diagnostics
     * @param e The exception in question
     * @return Reasonable text to describe an exception.
     */
    public static String errFromExc(Throwable e) {
        return e.getClass().getName() + "::" + e.getMessage();
    }

    /**
     * Perform a basic connection to some server.  Parent (...) test may attempt
     * connection to a variety of places, so we separate this core function out.
     *
     * @param config config to use in building the connection.
     */
    static public OpcUaESClient makeConnection(Map config, boolean runAsync) {
        return makeConnection(config, runAsync, null);
    }

    /**
     * Perform basic connection to an OPC UA Server
     * @param config The configuration describing the connection to be made
     * @param runAsync Whether this should be an async connection or synchronous one
     * @param testInstance The test instance running.  Used to determine
     * @return The OpcUaESClient created
     * @throws ExecutionException Errors returned by underlying connection if connection attempt fails.
     */
    static public OpcUaESClient makeConnection(Map config, boolean runAsync, OpcUaTestBase testInstance) {
        try {
            return makeConnection(config, runAsync, testInstance, false);
        } catch (ExecutionException e) {
            Utils.unexpectedException(e);
            return null;
        }
    }

    /**
     * Perform basic connection to an OPC UA Server
     * @param config The configuration describing the connection to be made
     * @param runAsync Whether this should be an async connection or synchronous one
     * @param testInstance The test instance running.  Used to determine
     * @param startProcessOnly Whether this method should evaluate the success or just kick off the result
     * @return The OpcUaESClient created
     * @throws ExecutionException Errors returned by underlying connection if connection attempt fails.
     */
    public static OpcUaESClient makeConnection(Map config, boolean runAsync, OpcUaTestBase testInstance, boolean startProcessOnly) throws ExecutionException {
        return makeConnection(config, runAsync, testInstance, startProcessOnly, false);
    }

    /**
     * Perform basic connection to an OPC UA Server
     * @param config The configuration describing the connection to be made
     * @param runAsync Whether this should be an async connection or synchronous one
     * @param testInstance The test instance running.  Used to determine
     * @param startProcessOnly Whether this method should evaluate the success or just kick off the result
     * @param expectFailure Whether this connection is expected to succeed.
     * @return The OpcUaESClient created
     * @throws ExecutionException Errors returned by underlying connection if connection attempt fails.
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    public static OpcUaESClient makeConnection(Map config,
                                               boolean runAsync,
                                               OpcUaTestBase testInstance,
                                               boolean startProcessOnly,
                                               boolean expectFailure) throws ExecutionException {
        Map<String, String> opcConfig = (Map<String, String>) config.get(OpcConstants.CONFIG_OPC_UA_INFORMATION);
        String discoveryPoint = opcConfig.get(OpcConstants.CONFIG_DISCOVERY_ENDPOINT);

        OpcUaESClient client = null;
        try {
            client = new OpcUaESClient(config);

            if (runAsync) {
                CompletableFuture<Void> cf = client.connectAsync();
                if (startProcessOnly) {
                    return client;
                }
                Thread.sleep(1500);  // We'll stall here to let some complete on their own...
                // 1.5 seconds usually has the first one connecting within time
                // and the others having to perform the get.  So both mechanisms are tested.
                if (!cf.isDone()) {
                    cf.get();
                } else {
                    log.debug("Connection completed within wait time: " + discoveryPoint);
                }
                assert !cf.isCancelled();
                if (discoveryPoint == OPC_PUBLIC_SERVER_NO_GOOD || expectFailure) {
                    // This one will complete with an unreachable-style error
                    assert cf.isCompletedExceptionally();
                } else {
                    assert !cf.isCompletedExceptionally();
                }
                // Regardless of validity, by the time we get here, we should be completed.
                assert cf.isDone();
            } else {
                client.connect();
            }
        }
        catch (OpcExtConfigException e) {
            if (e.getMessage().contains(".serverUnavailable")) {
                if (discoveryPoint != OPC_PUBLIC_SERVER_NO_GOOD) {
                    // Then this endpoint is no good.  We'll fail, but with a more useful error
                    fail("Server (" + opcConfig.get(OpcConstants.CONFIG_DISCOVERY_ENDPOINT) + ") is not available.  If this continues, please update the test. Error: "
                            + Utils.errFromExc(e)
                            + (e.getCause() != null ? "\n    Caused by: " + Utils.errFromExc(e.getCause()) : ""));
                }
                // Otherwise, this is completely expected
            } else if (e.getMessage().contains("Bad_Timeout")) {
                // In this case, it probably means that one of the public servers is down for whatever reason.
                // We'll skip the remainder of this test for this server
                Assume.assumeTrue("Timeout connecting to external server.  Skipping remainder of connection test", e.getMessage().contains("Bad_Timeout"));
            }else {
                fail("Unexpected OpcExtConfig Failure for endpoint " + discoveryPoint + ": " + Utils.errFromExc(e));
            }
        }
        catch (ExecutionException e) {
            if (startProcessOnly) {
                throw e;    // In this case, exceptions handled by caller
            }
            if (runAsync) {
                // Then we can get these exceptions when attempting to connect to the bad server.  If that's our
                // connection target, this this is expected.  Otherwise, it's a failure.
                if (discoveryPoint != OPC_PUBLIC_SERVER_NO_GOOD || !e.getMessage().contains("UnresolvedAddressException")) {
                    throw(e);
                }
            } else {
                // These are bad...
                throw(e);
            }
        }
        catch (Exception e) {
            fail("Unexpected general exception: " + Utils.errFromExc(e));
        }
        return client;
    }

    /**
     * Clean up test environment...
     *
     * @param path -- Directory to recursively delete
     */
    public static void deleteStorage(String path) {
        try {
            Files.walk(Paths.get(path))
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(File::delete);
        } catch (Throwable t) {
            // Ignore for now
        }
    }

    /**
     * Fail the test after printing information about the exception.
     * @param e Exception causing the failure.
     */
    public static void unexpectedException(Exception e) {
        if (e.getCause() != null) {
            e.getCause().printStackTrace();
        }
        fail("Unexpected Exception: " + errFromExc(e));
    }
}
