/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.opcua.uaOperations;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;

/**
 * Run tests testing connection to OPC UA Server
 */

@Slf4j
public class Connection extends OpcUaTestBase {

    // List of status that means the server's a mess so we'll ignore it in this test...
    public static final List<String> serverHosedStatus =
            Arrays.asList(
                    "Bad_ConnectionClosed",
                    "Bad_ServiceUnsupported"
            );

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

        config.put(OpcConstants.CONFIG_OPC_UA_INFORMATION, opcConfig);

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noStorageSpecified");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        String osName = System.getProperty("os.name");
        String fileNameWeCannotCreate = "/this/directory/should/never/really/and/truly/exist";
        if (osName != null && osName.toLowerCase().contains("windows")) {

            // For windows, we need some different bogus string.  On windows, it's OK to create a top-level
            // directory whereas on most Unix-based systems (linux, Mac OS) it's not.  So we'll craft a file
            // name which I think is not creatable...

            fileNameWeCannotCreate = "UtterlyBogusDiskLabelName:\\<?>\\I:\\Cannot:\\Exist:\\Due\\To\\Invalid\\Characters";
        }
        opcConfig.put(OpcConstants.CONFIG_STORAGE_DIRECTORY, fileNameWeCannotCreate);

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noDiscoveryEndpoint");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_DISCOVERY_ENDPOINT, "opc.tcp://somwhere.over.the.rainbow/dorothy");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noSecurityPolicy");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        // Now check that we'll accept a server address...
        opcConfig.put(OpcConstants.CONFIG_SERVER_ENDPOINT, "opc.tcp://rudolph.the.rednosed/reindeer");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, "noSecurityPolicy");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_SECURITY_POLICY, "foo bar");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {//
            checkException(o, ".invalidSecurityPolicySyntax");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_SECURITY_POLICY, SecurityPolicy.Aes128_Sha256_RsaOaep + "I_WILL_MAKE_THINGS_BOGUS");

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidSecurityPolicy");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_SECURITY_POLICY, SecurityPolicy.None.getUri());

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidStorageDirectory");
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);

        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".discoveryError");
            Assert.assertTrue("Incorrect exception cause", o.getCause() instanceof ExecutionException);
            ExecutionException e = (ExecutionException) o.getCause();
            // Here, we expect OPCUA exceptions, so check them.  However, the SDK we use appears
            // to sometimes let pure Java exceptions out, so don't be as picky about those...
            if (!e.getClass().getPackage().getName().startsWith("java")) {
                // Then, the underlying implementation appears to have returned a "plain old" java exception.
                // It's not supposed to, I don't think, but this isn't a test of the platform on which we build...
                Assert.assertTrue("Missing UaException: " + e.getMessage(), e.getMessage().contains("UaException"));
                Assert.assertTrue("Missing bad status clause: " + e.getMessage(), e.getMessage().contains("status=Bad_"));
                Assert.assertTrue("Missing exception cause data: " + e.getMessage(), e.getMessage().contains("TcpEndpointUrlInvalid"));
                Assert.assertTrue("Missing message clause: " + e.getMessage(), e.getMessage().contains("message="));
                Assert.assertTrue("Improperly formatted Opc Exception: " + o.getMessage(), o.getMessage().contains(OpcUaESClient.ERROR_PREFIX));
            }
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        String invalidUPw = "Wynken, Blynken, and Nod";
        opcConfig.put(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD, invalidUPw);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidUserPasswordSpecification");
            Assert.assertTrue("Wrong Message:" + o.getMessage(), o.getMessage().contains("must contain only a username AND password separated by a comma"));
            Assert.assertTrue("Missing Information:" + o.getMessage(), o.getMessage().contains(invalidUPw));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_IDENTITY_ANONYMOUS, "anonymous");
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message:" + o.getMessage(), o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2):" + o.getMessage(), o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.put(OpcConstants.CONFIG_IDENTITY_ANONYMOUS, ""); // Presence is sufficient.  Verify
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message:" + o.getMessage(), o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2):" + o.getMessage(), o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        String bogusCertAlias = "someCertificate alias that is not there";
        opcConfig.put(OpcConstants.CONFIG_IDENTITY_CERTIFICATE, bogusCertAlias);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message:" + o.getMessage(), o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2):" + o.getMessage(), o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.remove(OpcConstants.CONFIG_IDENTITY_ANONYMOUS);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtConfigException o) {
            checkException(o, ".invalidIdentitySpecification");
            Assert.assertTrue("Wrong Message:" + o.getMessage(), o.getMessage().contains("exactly one identity specification"));
            Assert.assertTrue("Wrong Message(2):" + o.getMessage(), o.getMessage().contains("is required."));
            Assert.assertTrue("Missing Info -- Anon:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_ANONYMOUS));
            Assert.assertTrue("Missing Info -- Cert:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_CERTIFICATE));
            Assert.assertTrue("Missing Info -- UPW:" + o.getMessage(), o.getMessage().contains(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;

        opcConfig.remove(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD);
        try {
            client = new OpcUaESClient(config);
        } catch (OpcExtKeyStoreException o) {
            checkException(o, ".fetchCertByAliasNoSuchCertificate");
            Assert.assertTrue("Wrong Message: " + o.getMessage(), o.getMessage().contains("no X509 certificate for alias"));
            Assert.assertTrue("Missing Info -- Cert Alias:" + o.getMessage(), o.getMessage().contains(bogusCertAlias));
        } catch (Throwable e) {
            fail("Unexpected exception thrown: " + Utils.errFromExc(e));
        }

        assert client == null;
    }

    @Test
    public void testConnectionSecNone() {
        makeConnection(false,
                SecurityPolicy.None.getUri(),
                null,
                false);
        makeConnection(false,
                SecurityPolicy.None.getUri(),
                MessageSecurityMode.None.toString(),
                false);

    }

    @Test
    public void testConnectionSecNoneWithLocalhostReplacement() {

        try {
            makeConnection(false,
                    SecurityPolicy.None.getUri(),
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    true);
            makeConnection(false,
                    SecurityPolicy.None.getUri(),
                    MessageSecurityMode.None.toString(),
                    null,
                    null,
                    false,
                    false,
                    false,
                    true);
        } catch (ExecutionException e) {
            fail("Unexpected Exception: " + e.getClass().getName() + " -- " + e.getMessage());
        }
    }

    @Test
    public void testConnectionSecNoneWithServerOverride() {

        try {
            makeConnection(false,
                    SecurityPolicy.None.getUri(),
                    null,
                    null,
                    null,
                    false,
                    false,
                    true);
            makeConnection(false,
                    SecurityPolicy.None.getUri(),
                    MessageSecurityMode.None.toString(),
                    null,
                    null,
                    false,
                    false,
                    true);
        } catch (ExecutionException e) {
            fail("Unexpected Exception: " + e.getClass().getName() + " -- " + e.getMessage());
        }
    }

    @Test
    public void testConnectionSecureUpw() throws Exception {

        List<EndpointDescription> eps = exampleServer.getServer().getEndpointDescriptions();

        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);
        EnumSet<SecurityPolicy> serverSecPols = EnumSet.noneOf(SecurityPolicy.class);

        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        for (EndpointDescription ep : eps) {
            if (ep.getEndpointUrl().startsWith("opc.tpc")) {
                // At present, these are all we test
                serverSecPols.add(SecurityPolicy.fromUri(ep.getSecurityPolicyUri()));
                serverMsgModes.add(ep.getSecurityMode());
            }
        }
        log.debug("For example server found secPols: {}, msgSec: {}", serverSecPols, serverMsgModes);

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None)) {
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {
                        log.info("Attempting sync connection using [{}, {}]", secPol, msgSec);
                        makeConnection(false,
                                secPol.getUri(),
                                msgSec.toString(),
                                true);

                        log.info("Attempting sync connection using [{}, {}]", secPol, "(missing)");
                        makeConnection(false,
                                secPol.getUri(),
                                null,           // Also check that the defaulting works correctly
                                true);

                        log.info("Attempting async connection using [{}, {}]", secPol, msgSec);
                        makeConnection(true,
                                secPol.getUri(),
                                msgSec.toString(),
                                true);

                        log.info("Attempting async connection using [{}, {}] with explicit anonymous user", secPol, msgSec);
                        makeConnection(true,
                                secPol.getUri(),
                                msgSec.toString(),
                                OpcConstants.CONFIG_IDENTITY_ANONYMOUS,
                                null,
                                true);

                        // Valid user/pw combos from ExampleServer:
                        // "user, password1" & "admin, password2"
                        String[] upwCombos = {"user, password1", "admin,password2", "user,                 password1"};

                        for (String uPw : upwCombos) {
                            log.info("Attempting sync connection using [{}, {}] using username/password: '{}'", secPol, msgSec, uPw);
                            makeConnection(false,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    uPw,
                                    true);
                        }

                        for (String uPw : upwCombos) {
                            log.info("Attempting async connection using [{}, {}] using username/password: '{}'", secPol, msgSec, uPw);
                            makeConnection(true,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    uPw,
                                    true);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testConnectionSecureCert() throws Exception {

        List<EndpointDescription> eps = exampleServer.getServer().getEndpointDescriptions();

        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);
        EnumSet<SecurityPolicy> serverSecPols = EnumSet.noneOf(SecurityPolicy.class);

        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        for (EndpointDescription ep : eps) {
            if (ep.getEndpointUrl().startsWith("opc.tpc")) {
                // At present, these are all we test
                serverSecPols.add(SecurityPolicy.fromUri(ep.getSecurityPolicyUri()));
                serverMsgModes.add(ep.getSecurityMode());
            }
        }

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None)) {
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {

                        // Defaults tested in *Upw test...
                        for (String certKey : trustedTestCerts) {
                            log.info("Attempting sync connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(false,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);

                            log.info("Attempting async connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(true,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testConnectionSecureBadCert() throws Exception {

        List<EndpointDescription> eps = exampleServer.getServer().getEndpointDescriptions();

        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);
        EnumSet<SecurityPolicy> serverSecPols = EnumSet.noneOf(SecurityPolicy.class);

        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        for (EndpointDescription ep : eps) {
            if (ep.getEndpointUrl().startsWith("opc.tpc")) {
                // At present, these are all we test
                serverSecPols.add(SecurityPolicy.fromUri(ep.getSecurityPolicyUri()));
                serverMsgModes.add(ep.getSecurityMode());
            }
        }

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None)) {
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {

                        // Defaults tested in *Upw test...
                        for (String certKey : untrustedTestCerts) {
                            try {
                                log.info("Attempting sync connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                                makeRawConnection(false,
                                        secPol.getUri(),
                                        msgSec.toString(),
                                        OpcConstants.CONFIG_IDENTITY_CERTIFICATE,
                                        certKey);
                            } catch (ExecutionException e) {
                                assert e.getMessage().contains("UaException");
                                assert e.getMessage().contains("status=Bad_");
                                assert e.getMessage().contains("message=java.security.InvalidKeyException: Not an RSA key: EC");
                            }

                            try {
                                log.info("Attempting async connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                                makeRawConnection(true,
                                        secPol.getUri(),
                                        msgSec.toString(),
                                        OpcConstants.CONFIG_IDENTITY_CERTIFICATE,
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

    public void runCertTest(List<String> certList, boolean expectFailure) throws Exception {

        List<EndpointDescription> eps = exampleServer.getServer().getEndpointDescriptions();

        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);
        EnumSet<SecurityPolicy> serverSecPols = EnumSet.noneOf(SecurityPolicy.class);

        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        for (EndpointDescription ep : eps) {
            if (ep.getEndpointUrl().startsWith("opc.tpc")) {
                // At present, these are all we test
                serverSecPols.add(SecurityPolicy.fromUri(ep.getSecurityPolicyUri()));
                serverMsgModes.add(ep.getSecurityMode());
            }
        }

        boolean runSync = expectFailure;    // If expecting failure, act as if async so we can catch exceptions

        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None)) {
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {

                        // Defaults tested in *Upw test...
                        for (String certKey : certList) {
                            log.info("Attempting sync connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(runSync,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);

                            log.info("Attempting async connection using [{}, {}] using certificate: '{}'", secPol, msgSec, certKey);
                            makeConnection(true,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_CERTIFICATE,
                                    certKey,
                                    true);
                        }
                    }
                }
            }
        }

    }

    @Test
    public void testConnectionSecureBadIdentity()  throws Exception {

        List<EndpointDescription> eps = exampleServer.getServer().getEndpointDescriptions();

        EnumSet<MessageSecurityMode> serverMsgModes = EnumSet.noneOf(MessageSecurityMode.class);
        EnumSet<SecurityPolicy> serverSecPols = EnumSet.noneOf(SecurityPolicy.class);

        // Unfortunately, no good way to find out what security modes there are.  So we'll
        // traverse the endpoints and act appropriately.

        for (EndpointDescription ep : eps) {
            if (ep.getEndpointUrl().startsWith("opc.tpc")) {
                // At present, these are all we test
                serverSecPols.add(SecurityPolicy.fromUri(ep.getSecurityPolicyUri()));
                serverMsgModes.add(ep.getSecurityMode());
            }
        }

        String invalidCreds = "bogus1, bogus2";
        // Below, we'll traverse the valid combinations.  None's must be paired and are tested elsewhere
        for (SecurityPolicy secPol : serverSecPols) {
            if (!secPol.equals(SecurityPolicy.None)) {
                for (MessageSecurityMode msgSec : serverMsgModes) {
                    if (!msgSec.equals(MessageSecurityMode.None)) {
                        log.info("Attempting sync connection using [{}, {}] using username/password: '{}'", secPol, msgSec, invalidCreds);

                        try {
                            OpcUaESClient client = makeRawConnection(false,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    invalidCreds);
                            fail("Expected exception for invalid identity token");
                        } catch (ExecutionException e) {
                            Assert.assertTrue("Message should contain 'UaServiceFaultException: " + e.getMessage(), e.getMessage().contains("UaServiceFaultException"));
                            Assert.assertTrue( "Message should contain 'Bad_IdentityTokenInvalid': " + e.getMessage(), e.getMessage().contains("status=Bad_IdentityTokenInvalid"));
                            Assert.assertTrue( "Message should contain 'message=The user identity token is not valid': " + e.getMessage(), e.getMessage().contains("message=The user identity token is not valid"));
                        } catch (Exception e) {
                            Utils.unexpectedException(e);
                        }

                        log.info("Attempting aSync connection using [{}, {}] using username/password: '{}'", secPol, msgSec, invalidCreds);

                        try {
                            OpcUaESClient client = makeRawConnection(true,
                                    secPol.getUri(),
                                    msgSec.toString(),
                                    OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD,
                                    invalidCreds);
                            CompletableFuture<Void> cf = client.getConnectFuture();
                            cf.join();  // Force exception to be thrown now...
                        } catch (CompletionException e) {
                            Assert.assertTrue("Message should contain 'UaServiceFaultException: " + e.getMessage(), e.getMessage().contains("UaServiceFaultException"));
                            Assert.assertTrue( "Message should contain 'Bad_IdentityTokenInvalid': " + e.getMessage(), e.getMessage().contains("status=Bad_IdentityTokenInvalid"));
                            Assert.assertTrue( "Message should contain 'message=The user identity token is not valid': " + e.getMessage(), e.getMessage().contains("message=The user identity token is not valid"));
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
                SecurityPolicy.None.getUri(),
                null,
                false);
    }

    public OpcUaESClient makeRawConnection(boolean runAsync, String secPolicy, String msgSecMode, String identityType, String identityValue) throws ExecutionException {
        HashMap config = new HashMap();
        Map<String, String> opcConfig = new HashMap<>();

        config.put(OpcConstants.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcConstants.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcConstants.CONFIG_SECURITY_POLICY, secPolicy);
        opcConfig.put(OpcConstants.CONFIG_DISCOVERY_ENDPOINT, Utils.OPC_INPROCESS_SERVER);

        if (msgSecMode != null && !msgSecMode.isEmpty()) {
            opcConfig.put(OpcConstants.CONFIG_MESSAGE_SECURITY_MODE, msgSecMode);
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
        try {
            makeConnection(runAsync,
                    secPolicy,
                    msgSecMode,
                    identityType,
                    identityValue,
                    inProcessOnly,
                    startProcessOnly,
                    false);
        } catch (ExecutionException e) {
            fail("Unexpected Exception: " + e.getClass().getName() + " -- " + e.getMessage());
        }
    }
    public void makeConnection(boolean runAsync,
                               String secPolicy,
                               String msgSecMode,
                               String identityType,
                               String identityValue,
                               boolean inProcessOnly,
                               boolean startProcessOnly,
                               boolean useServerAddress) throws ExecutionException {
        try {
            makeConnection(runAsync,
                    secPolicy,
                    msgSecMode,
                    identityType,
                    identityValue,
                    inProcessOnly,
                    startProcessOnly,
                    useServerAddress,
                    false);
        } catch (ExecutionException e) {
            fail("Unexpected Exception: " + e.getClass().getName() + " -- " + e.getMessage());
        }
    }

    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.GuardLogStatement"})
    public void makeConnection(boolean runAsync,
                               String secPolicy,
                               String msgSecMode,
                               String identityType,
                               String identityValue,
                               boolean inProcessOnly,
                               boolean startProcessOnly,
                               boolean useServerAddress,
                               boolean fakeUnreachable) throws ExecutionException {
        HashMap config = new HashMap();
        Map<String, Object> opcConfig = new HashMap<>();

        config.put(OpcConstants.CONFIG_OPC_UA_INFORMATION, opcConfig);
        opcConfig.put(OpcConstants.CONFIG_STORAGE_DIRECTORY, STANDARD_STORAGE_DIRECTORY);
        opcConfig.put(OpcConstants.CONFIG_SECURITY_POLICY, secPolicy);
        if (fakeUnreachable) {
            opcConfig.put(OpcConstants.CONFIG_TEST_DISCOVERY_UNREACHABLE, true);
        }

        if (msgSecMode != null && !msgSecMode.isEmpty()) {
            opcConfig.put(OpcConstants.CONFIG_MESSAGE_SECURITY_MODE, msgSecMode);
        }

        if (identityType != null) {
            opcConfig.put(identityType, (identityValue != null ? identityValue : ""));
        }

        // We'll test against a set of servers.  Hopefully, this will allow us to verify if things work
        // externally as well as internally.
        List<String> pubServers;
        if (!inProcessOnly) {
            pubServers = Utils.OPC_PUBLIC_SERVERS;
        } else {
            pubServers = Arrays.asList(Utils.OPC_INPROCESS_SERVER);
        }

        int workingServers = 0;
        int successfulConnections = 0;
        for (String discEP : pubServers) {
            log.info("Attempting connection to public server: " + discEP);
            try {
                URI opcuri = new URI(discEP);
                // We cannot just use the URL since we don't have handling for the opc.tcp scheme.
                // We'll just do a basic socket connection as that's where most of the issues w/r/t availability are.
                String host = opcuri.getHost();
                int port = opcuri.getPort();
                log.info("Making socket connection to {}:{}", host, port);

                // We don't use the result so we won't save it.  We just care that it completes w/o error
                new Socket(host, port);
                // If we get this far, the server has accepted the connection so there's something out there.
                // We'll assume it's our OPC server & let the test proceed
            } catch (UnknownHostException | URISyntaxException badURL) {
                fail("URL " + discEP + " has unknown host or invalid syntax.  " +
                                "Probably means that the list of free servers has changed... ");
            } catch (IOException ioe) {
                // Could be bad connection or socket timeout (a subclass of ioexception)
                // In either case, this means our free server is not currently available (you get what you pay for)
                // so skip this one...
                log.warn("Discovery Endpoint: {} not responding to open.  Skipping it.", discEP);
                continue;
            }

            workingServers += 1;
            opcConfig.put(OpcConstants.CONFIG_DISCOVERY_ENDPOINT, discEP);

            if (useServerAddress && Utils.OPC_PUBLIC_SERVER_1.equals(discEP)) {
                opcConfig.put(OpcConstants.CONFIG_SERVER_ENDPOINT, discEP);
            }

            try {
                performConnection(config, runAsync, startProcessOnly);
                successfulConnections += 1;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UaException) {
                    UaException uae = (UaException) e.getCause();


                    log.error("Got error: {}", uae.getStatusCode().toString());
                    // Unfortunately, not all errors are reported reasonably.  So we'll look for our "acceptable errors"
                    // in the returned strings...

                    boolean okError = false;
                    for (String err: serverHosedStatus) {
                        okError = uae.getStatusCode().toString().contains(err);
                        if (okError) {
                            break;
                        }
                    }
                    if (!okError) {
                        Utils.unexpectedException(uae);
                    }
                } else if (startProcessOnly) {
                    throw e;
                } else {
                    Utils.unexpectedException(e);
                }
            }
        }

        log.info("Found {} servers to which we may be able to connect.", workingServers);
        log.info("Successfully connected to {} servers.", successfulConnections);
        assertNotEquals("No responding servers found to test.", 0, workingServers);
        assertNotEquals("No successful connections: (count: " + successfulConnections + ")", 0,
                successfulConnections);
    }

    public void performConnection(Map config, boolean runAsync, boolean startProcessOnly) throws ExecutionException {
        OpcUaESClient client = Utils.makeConnection(config, runAsync, this, startProcessOnly);
        if (client != null) {
            client.disconnect();
        }
    }

    private static void checkException(Exception e, String tagName) {
        Assert.assertTrue("Incorrect exception tag: '" + e.getMessage() + "' should contain '" + tagName + "'",
                e.getMessage().contains(tagName));
        String prefix = "bogusValue";
        if (e instanceof OpcExtConfigException) {
            prefix = OpcUaESClient.ERROR_PREFIX;
        } else if (e instanceof OpcExtKeyStoreException) {
            prefix = KeyStoreManager.ERROR_PREFIX;
        } else {
            Utils.unexpectedException(e);
        }
        Assert.assertTrue("Improperly formatted Opc Config Exception: '" + e.getMessage() + "' should contain prefix: '" + prefix + "'",
                e.getMessage().contains(prefix));
    }
}
