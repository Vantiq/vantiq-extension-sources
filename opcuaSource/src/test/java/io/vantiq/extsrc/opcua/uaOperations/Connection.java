package io.vantiq.extsrc.opcua.uaOperations;

import static org.junit.Assert.fail;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Connection extends OpcUaTestBase {

    @Test
    public void testMissingConfig() {
        HashMap config = new HashMap();
        OpcUaESClient client = null;

        try {
            client = new OpcUaESClient(null);
        } catch (OpcExtConfigException o) {
            checkException(o, ".nullConfig");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }
        assert client == null;

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noOPCInformation");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

    }

    @Test
    public void testBadConfig() {
        HashMap config = new HashMap();
        OpcUaESClient client = null;
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noStorageSpecified");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/this/directory/should/never/really/and/truly/exist");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noDiscoveryEndpoint");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, "/tmp/opcua-storage");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noSecurityPolicy");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, "foo bar");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {//
            checkException(o, ".invalidSecurityPolicySyntax");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.Aes128_Sha256_RsaOaep + "I_WILL_MAKE_THINGS_BOGUS");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidSecurityPolicy");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidStorageDirectory");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/tmp/opcua-storage");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".discoveryError");
            Assert.assertTrue("Incorrect exception cause", o.getCause() instanceof ExecutionException);
            ExecutionException e = (ExecutionException) o.getCause();
            Assert.assertTrue("Missing UaException",  e.getMessage().contains("UaException"));
            Assert.assertTrue("Missing bad status clause", e.getMessage().contains("status=Bad_"));
            Assert.assertTrue("Missing exception cause data", e.getMessage().contains("TcpEndpointUrlInvalid"));
            Assert.assertTrue("Missing message clause", e.getMessage().contains("message="));
            Assert.assertTrue("Improperly formatted Opc Exception", o.getMessage().contains(OpcUaESClient.ERROR_PREFIX));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;
    }

    @Test
    public void testConnectionSecNone() {
        doSecNone(false);
    }

    @Test
    public void testConnectionSecNoneAsync() {
        doSecNone(true);
    }


    public void doSecNone(boolean runAsync) {
        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, "/tmp/opcua-storage");
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getSecurityPolicyUri());

        // We'll test against a set of servers.  Hopefully, this will allow us to verify if things work
        // externally as well as internally.
        List<String> pubServers = Arrays.asList(Utils.OPC_INPROCESS_SERVER,
                Utils.OPC_PUBLIC_SERVER_1,
                Utils.OPC_PUBLIC_SERVER_2,
                Utils.OPC_PUBLIC_SERVER_3,
                Utils.OPC_PUBLIC_SERVER_NO_GOOD
        );

        for (String discEP : pubServers) {
            log.info("Attempting connection to public server: " + discEP);
            opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, discEP);
            performConnection(config, runAsync);
        }

    }
    public void performConnection(Map config, boolean runAsync) {
        OpcUaESClient client = Utils.makeConnection(config, runAsync);
        if (client != null) {
            client.disconnect();
        }
    }

    private static void checkException(Exception e, String tagName) {
        Assert.assertTrue("Incorrect exception tag", e.getMessage().contains(tagName));
        Assert.assertTrue("Improperly formatted Opc Config Exception", e.getMessage().contains(OpcUaESClient.ERROR_PREFIX));

    }

}
