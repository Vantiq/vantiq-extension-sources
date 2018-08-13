package io.vantiq.extsrc.opcua.uaOperations;

import static org.junit.Assert.fail;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.Assert;
import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".discoveryError");
            Assert.assertTrue("Incorrect exception cause", o.getCause() instanceof ExecutionException);
            ExecutionException e = (ExecutionException) o.getCause();
            Assert.assertTrue("Missing UaException", e.getMessage().contains("UaException"));
            Assert.assertTrue("Missing bad status clause", e.getMessage().contains("status=Bad_"));
            Assert.assertTrue("Missing exception cause data", e.getMessage().contains("TcpEndpointUrlInvalid"));
            Assert.assertTrue("Missing message clause", e.getMessage().contains("message="));
            Assert.assertTrue("Improperly formatted Opc Exception", o.getMessage().contains(OpcUaESClient.ERROR_PREFIX));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        String invalidUPw = "Wynken, Blynken, and Nod";
        opcConfig.put(OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD, invalidUPw);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidUserPasswordSpecification");
            Assert.assertTrue("Wrong Message", o.getMessage().contains("must contain only a username AND password separated by a comma"));
            Assert.assertTrue("Missing Information", o.getMessage().contains(invalidUPw));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS, "anonymous");
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message", o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2)", o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS, ""); // Presence is sufficient.  Verify
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message", o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2)", o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        String bogusCertAlias = "someCertificate alias that is not there";
        opcConfig.put(OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE, bogusCertAlias);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message", o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2)", o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.remove(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message", o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2)", o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW", o.getMessage().contains(OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.remove(OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtKeyStoreException o) {
            checkException(o, ".fetchCertByAliasNoSuchCertificate");
            Assert.assertTrue("Wrong Message: " + o.getMessage(), o.getMessage().contains("no X509 certificate for alias"));
            Assert.assertTrue("Missing Info -- Cert Alias", o.getMessage().contains(bogusCertAlias));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;
    }

    @Test
    public void testConnectionSecNone() {
        makeConnection(false,
                SecurityPolicy.None.getSecurityPolicyUri(),
                null,
                false);
        makeConnection(false,
                SecurityPolicy.None.getSecurityPolicyUri(),
                MessageSecurityMode.None.toString(),
                false);

    }

    @Test
    public void testConnectionSecureUpw() {
        EnumSet<SecurityPolicy> serverSecPols = exampleServer.getServer().getConfig().getSecurityPolicies();
        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        EndpointDescription[] eps = exampleServer.getServer().getEndpointDescriptions();
        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);

        for (EndpointDescription ep : eps) {
            serverMsgModes.add(ep.getSecurityMode());
        }

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None) && !secPol.equals(SecurityPolicy.Aes256_Sha256_RsaPss)) {
                // TODO: don't know why the Aes256... policy fails, even with BouncyCastle added.
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {
                        log.info("Attempting sync connection using [{}, {}]", secPol, msgSec);
                        makeConnection(false,
                                secPol.getSecurityPolicyUri(),
                                msgSec.toString(),
                                true);

                        log.info("Attempting sync connection using [{}, {}]", secPol, "(missing)");
                        makeConnection(false,
                                secPol.getSecurityPolicyUri(),
                                null,           // Also check that the defaulting works correctly
                                true);

                        log.info("Attempting async connection using [{}, {}]", secPol, msgSec);
                        makeConnection(true,
                                secPol.getSecurityPolicyUri(),
                                msgSec.toString(),
                                true);

                        log.info("Attempting async connection using [{}, {}] with explicit anonymous user", secPol, msgSec);
                        makeConnection(true,
                                secPol.getSecurityPolicyUri(),
                                msgSec.toString(),
                                OpcUaESClient.CONFIG_IDENTITY_ANONYMOUS,
                                null,
                                true);

                        // Valid user/pw combos from ExampleServer:
                        // "user, password1" & "admin, password2"
                        String[] upwCombos = {"user, password1", "admin,password2", "user,                 password1"};

                        for (String uPw : upwCombos) {
                            log.info("Attempting sync connection using [{}, {}] using username/password: '{}'", secPol, msgSec, uPw);
                            makeConnection(false,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    uPw,
                                    true);
                        }

                        for (String uPw : upwCombos) {
                            log.info("Attempting async connection using [{}, {}] using username/password: '{}'", secPol, msgSec, uPw);
                            makeConnection(true,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    uPw,
                                    true);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testConnectionSecureCert() {
        EnumSet<SecurityPolicy> serverSecPols = exampleServer.getServer().getConfig().getSecurityPolicies();
        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        EndpointDescription[] eps = exampleServer.getServer().getEndpointDescriptions();
        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);

        for (EndpointDescription ep : eps) {
            serverMsgModes.add(ep.getSecurityMode());
        }

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None) && !secPol.equals(SecurityPolicy.Aes256_Sha256_RsaPss)) {
                // TODO: don't know why the Aes256... policy fails, even with BouncyCastle added.
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {

                        // Defaults tested in *Upw test...
                        for (String certKey : trustedTestCerts) {
                            log.info("Attempting sync connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(false,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);

                            log.info("Attempting async connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(true,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testConnectionSecureBadCert() {
        EnumSet<SecurityPolicy> serverSecPols = exampleServer.getServer().getConfig().getSecurityPolicies();
        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        EndpointDescription[] eps = exampleServer.getServer().getEndpointDescriptions();
        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);

        for (EndpointDescription ep : eps) {
            serverMsgModes.add(ep.getSecurityMode());
        }

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None) && !secPol.equals(SecurityPolicy.Aes256_Sha256_RsaPss)) {
                // TODO: don't know why the Aes256... policy fails, even with BouncyCastle added.
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {

                        // Defaults tested in *Upw test...
                        for (String certKey : untrustedTestCerts) {
                            try {
                                log.info("Attempting sync connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                                makeRawConnection(false,
                                        secPol.getSecurityPolicyUri(),
                                        msgSec.toString(),
                                        OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE,
                                        certKey);
                            } catch (ExecutionException e) {
                                assert e.getMessage().contains("UaException");
                                assert e.getMessage().contains("status=Bad_");
                                assert e.getMessage().contains("message=java.security.InvalidKeyException: Not an RSA key: EC");
                            }

                            try {
                                log.info("Attempting async connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                                makeRawConnection(true,
                                        secPol.getSecurityPolicyUri(),
                                        msgSec.toString(),
                                        OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE,
                                        certKey);
                            }
                            catch (ExecutionException e) {
                                Utils.unexpectedException(e);
                            }
                            catch (CompletionException e) {
                                assert e.getMessage().contains("UaException");
                                assert e.getMessage().contains("status=Bad_");
                                assert e.getMessage().contains("message=java.security.InvalidKeyException: Not an RSA key: EC");
                            }

                        }
                    }
                }
            }
        }
    }

    public void runCertTest(List<String> certList, boolean expectFailure) {
        EnumSet<SecurityPolicy> serverSecPols = exampleServer.getServer().getConfig().getSecurityPolicies();
        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        EndpointDescription[] eps = exampleServer.getServer().getEndpointDescriptions();
        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);

        for (EndpointDescription ep : eps) {
            serverMsgModes.add(ep.getSecurityMode());
        }

        boolean runSync = expectFailure;    // If expecting failure, act as if async so we can catch exceptions

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None) && !secPol.equals(SecurityPolicy.Aes256_Sha256_RsaPss)) {
                // TODO: don't know why the Aes256... policy fails, even with BouncyCastle added.
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {

                        // Defaults tested in *Upw test...
                        for (String certKey : certList) {
                            log.info("Attempting sync connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(runSync,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);

                            log.info("Attempting async connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(true,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);
                        }
                    }
                }
            }
        }

    }

    @Test
    public void testConnectionSecureBadIdentity() {
        EnumSet<SecurityPolicy> serverSecPols = exampleServer.getServer().getConfig().getSecurityPolicies();
        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        EndpointDescription[] eps = exampleServer.getServer().getEndpointDescriptions();
        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);

        for (EndpointDescription ep : eps) {
            serverMsgModes.add(ep.getSecurityMode());
        }

        String invalidCreds = "bogus1, bogus2";
        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None) && !secPol.equals(SecurityPolicy.Aes256_Sha256_RsaPss)) {
                // TODO: don't know why the Aes256... policy fails, even with BouncyCastle added.
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {
                        log.info("Attempting sync connection using [{}, {}] using username/password: '{}'", secPol, msgSec, invalidCreds);

                        try {
                            OpcUaESClient client = makeRawConnection(false,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    invalidCreds);
                            fail("Expected exception for invalid identity token");
                        } catch (ExecutionException e) {
                            assert e.getMessage().contains("UaServiceFaultException");
                            assert e.getMessage().contains("status=Bad_IdentityTokenInvalid");
                            assert e.getMessage().contains("message=The user identity token is not valid");
                        } catch (Exception e) {
                            Utils.unexpectedException(e);
                        }

                        log.info("Attempting aSync connection using [{}, {}] using username/password: '{}'", secPol, msgSec, invalidCreds);

                        try {
                            OpcUaESClient client = makeRawConnection(true,
                                    secPol.getSecurityPolicyUri(),
                                    msgSec.toString(),
                                    OpcUaESClient.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    invalidCreds);
                            CompletableFuture<Void> cf = client.getConnectFuture();
                            cf.join();  // Force exception to be thrown now...
                        } catch (CompletionException e) {
                            assert e.getMessage().contains("UaServiceFaultException");
                            assert e.getMessage().contains("status=Bad_IdentityTokenInvalid");
                            assert e.getMessage().contains("message=The user identity token is not valid");
                        } catch (Exception e) {
                            Utils.unexpectedException(e);
                        }

                    }
                }
            }
        }
    }

    @Test
    public void testConnectionSecNoneAsync() {
        makeConnection(true,
                SecurityPolicy.None.getSecurityPolicyUri(),
                null,
                false);
    }

    public OpcUaESClient makeRawConnection(boolean runAsync, String secPolicy, String msgSecMode, String identityType, String identityValue) throws ExecutionException {
        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, secPolicy);
        opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        if (msgSecMode != null && !msgSecMode.isEmpty()) {
            opcConfig.put(OpcUaESClient.CONFIG_MESSAGE_SECURITY_MODE, msgSecMode);
        }

        if (identityType != null) {
            opcConfig.put(identityType, (identityValue != null ? identityValue : ""));
        }
        OpcUaESClient client = Utils.makeConnection(config, runAsync, this, true);
        return client;
    }

    public void makeConnection(boolean runAsync, String secPolicy, String msgSecMode, boolean inProcessOnly) {
        makeConnection(runAsync, secPolicy, msgSecMode, null, null, inProcessOnly);

    }

    public void makeConnection(boolean runAsync, String secPolicy, String msgSecMode, String identityType, String identityValue, boolean inProcessOnly) {
        try {
            makeConnection(runAsync, secPolicy, msgSecMode, identityType, identityValue, inProcessOnly, false);
        } catch (ExecutionException e) {
            fail("Unexpected Exception: " + e.getClass().getName() + " -- " + e.getMessage());
        }
    }

    public void makeConnection(boolean runAsync, String secPolicy, String msgSecMode,
                               String identityType, String identityValue, boolean inProcessOnly, boolean startProcessOnly) throws ExecutionException {
        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcUaESClient.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcUaESClient.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcUaESClient.CONFIG_SECURITY_POLICY, secPolicy);
        if (msgSecMode != null && !msgSecMode.isEmpty()) {
            opcConfig.put(OpcUaESClient.CONFIG_MESSAGE_SECURITY_MODE, msgSecMode);
        }

        if (identityType != null) {
            opcConfig.put(identityType, (identityValue != null ? identityValue : ""));
        }

        // We'll test against a set of servers.  Hopefully, this will allow us to verify if things work
        // externally as well as internally.
        List<String> pubServers;
        if (!inProcessOnly) {
            pubServers = Arrays.asList(Utils.OPC_INPROCESS_SERVER,
                    Utils.OPC_PUBLIC_SERVER_1,
                    Utils.OPC_PUBLIC_SERVER_2,
                    Utils.OPC_PUBLIC_SERVER_3,
                    Utils.OPC_PUBLIC_SERVER_NO_GOOD
            );
        } else {
            pubServers = Arrays.asList(Utils.OPC_INPROCESS_SERVER);
        }

        for (String discEP : pubServers) {
            log.info("Attempting connection to public server: " + discEP);
            opcConfig.put(OpcUaESClient.CONFIG_DISCOVERY_ENDPOINT, discEP);
            try {
                performConnection(config, runAsync, startProcessOnly);
            } catch (ExecutionException e) {
                if (startProcessOnly) {
                    throw e;
                } else {
                    Utils.unexpectedException(e);
                }
            }
        }

    }

    public void performConnection(Map config, boolean runAsync, boolean startProcessOnly) throws ExecutionException {
        OpcUaESClient client = Utils.makeConnection(config, runAsync, this, startProcessOnly);
        if (client != null) {
            client.disconnect();
        }
    }

    private static void checkException(Exception e, String tagName) {
        Assert.assertTrue("Incorrect exception tag", e.getMessage().contains(tagName));
        String prefix = "bogusValue";
        if (e instanceof OpcExtConfigException) {
            prefix = OpcUaESClient.ERROR_PREFIX;
        } else if (e instanceof OpcExtKeyStoreException) {
            prefix = KeyStoreManager.ERROR_PREFIX;
        } else {
            Utils.unexpectedException(e);
        }
        Assert.assertTrue("Improperly formatted Opc Config Exception", e.getMessage().contains(prefix));
    }
}
