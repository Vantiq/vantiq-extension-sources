package io.vantiq.extsrc.opcua.uaOperations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;

import static org.junit.Assert.fail;

@Slf4j
public class Utils {

    // This set of public servers is known to work as of 5 July 2018
    // In the future, errors
    //
    // At present, we'll check the write value stuff only on our internal server.  Not sure of permissions,
    // etc.  // FIXME

    public static String OPC_PUBLIC_SERVER_1 = "opc.tcp://opcuaserver.com:48010";
    public static String OPC_PUBLIC_SERVER_2 = "opc.tcp://opcuademo.sterfive.com:26543";
    public static String OPC_PUBLIC_SERVER_3 = "http://opcua.demo-this.com:51211/UA/SampleServer";
    public static String OPC_INPROCESS_SERVER = "opc.tcp://localhost:12686/example";
    public static String OPC_PUBLIC_SERVER_NO_GOOD = "opc.tcp://opcuaserver.com:4840";

    public static String EXAMPLE_NS_SCALAR_INT32_IDENTIFIER = "HelloWorld/ScalarTypes/Int32";
    public static String EXAMPLE_NS_SCALAR_INT32_TYPE = "Int32";
    public static String EXAMPLE_NS_SCALAR_STRING_IDENTIFIER = "HelloWorld/ScalarTypes/String";
    public static String EXAMPLE_NS_SCALAR_STRING_TYPE = "String";

    public static String OPC_UA_CORE_NAMESPACE = Namespaces.OPC_UA;


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
        Map<String, String> opcConfig = (Map<String, String>) config.get(OpcUaESClient.CONFIG_OPC_UA_INFORMATION);
        deleteStorage(opcConfig.get(OpcUaESClient.CONFIG_STORAGE_DIRECTORY));
        String discoveryPoint = opcConfig.get(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT);

        OpcUaESClient client = null;
        try {
            client = new OpcUaESClient(config);
            assert client != null;
            if (runAsync) {
                CompletableFuture<Void> cf = client.connectAsync();
                Thread.sleep(1500);  // We'll stall here to let some complete on their own...
                // 1.5 seconds usually has the first one connecting within time
                // and the others having to perform the get.  So both mechanisms are tested.
                if (!cf.isDone()) {
                    cf.get();
                } else {
                    log.debug("Connection completed within wait time: " + discoveryPoint);
                }
                assert cf.isCancelled() == false;
                if (discoveryPoint == OPC_PUBLIC_SERVER_NO_GOOD) {
                    // This one will complete with an unreachable-style error
                    assert cf.isCompletedExceptionally();
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
                    fail("Server (" + opcConfig.get(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT) + ") is not available.  If this continues, please update the test. Error: "
                            + Utils.errFromExc(e)
                            + (e.getCause() != null ? "\n    Caused by: " + Utils.errFromExc(e.getCause()) : ""));
                }
                // Otherwise, this is completely expected
            } else {
                fail("Unexpected OpcExtConfig Failure: " + Utils.errFromExc(e));
            }
        }
        catch (ExecutionException e) {
            if (runAsync) {
                // Then we can get these exceptions when attempting to connect to the bad server.  If that's our
                // connection target, this this is expected.  Otherwise, it's a failure.
                if (discoveryPoint != OPC_PUBLIC_SERVER_NO_GOOD || !e.getMessage().contains("UnresolvedAddressException")) {
                    fail("Unexpected general exception: " + Utils.errFromExc(e));
                }
            } else {
                // These are bad...
                fail("Unexpected general exception: " + Utils.errFromExc(e));
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
    private static void deleteStorage(String path) {
        try {
            Files.walk(Paths.get(path))
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(File::delete);
        } catch (Throwable t) {
            // Ignore for now
        }
    }
}
