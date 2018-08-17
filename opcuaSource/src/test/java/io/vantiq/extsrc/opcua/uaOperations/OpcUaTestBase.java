/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.opcua.uaOperations;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.examples.server.ExampleServer;
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.fail;

/**
 * Base class for tests of the OPC UA source
 */
@Slf4j
public class OpcUaTestBase {
    public static final String STANDARD_STORAGE_DIRECTORY = "/tmp/opcua-storage";

    protected ExampleServer exampleServer = null;
    protected File pkiDir = null;
    protected KeyStoreManager ksm = null;
    protected KeyStoreManager clientKsm = null;

    protected List<String> trustedTestCerts = new ArrayList<>();
    protected List<String> untrustedTestCerts = new ArrayList<>();

    private static final Pattern IP_ADDR_PATTERN = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

//    static {
//        boolean removed = CryptoRestrictions.remove();
//        assert removed;
//
//        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
//        Security.addProvider(new BouncyCastleProvider());
//    }

    /**
     * Clear out any existing storage used in previous tests.
     */
    @BeforeClass
    public static void clearCruft() {
        Utils.deleteStorage(STANDARD_STORAGE_DIRECTORY);
        File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
        Utils.deleteStorage(securityTempDir.getPath());
    }

    /**
     * Perform set up for tests.
     *
     * Setup includes the following tasks:
     *   - create key store for server & client
     *   - create client certificates
     *   - create trusted & untrusted/invalid certificates for use in connection testing
     *   - start ExampleServer -- the OPC UA server against which we test
     */
    @Before
    public void setup() {
        try {
            File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
            if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
                throw new Exception("unable to find or create security temp dir: " + securityTempDir);
            }

            pkiDir = securityTempDir.toPath().resolve("pki").toFile();
            log.info("Example Server pki directory is: " + pkiDir.toString());

            ksm = new KeyStoreManager();

            File storage = new File(STANDARD_STORAGE_DIRECTORY);
            File secStore = new File(storage.getCanonicalPath() + File.separator + "security");
            if (!secStore.exists() && !secStore.mkdirs()) {
                fail("Could not create server's storage space");
            }
            ksm.load(secStore); // STANDARD_STORAGE_DIRECTORY));

            X509Certificate cert = ksm.getClientCertificate();

            trustCertificateOnServer(cert);

            // For testing purposes, we're going to some more trusted & untrusted certificate that we'll use to test
            // Cert-based authentication.


            for (int i = 1; i < 5; i++) {
                String alias = "TTC" + i;
                cert = createCertificate(alias, "Trusted Test Certificate " + i, "urn:io:vantiq:client:trusted:" + i, true);
                trustedTestCerts.add(alias);
            }

            for (int i = 1; i < 5; i++) {
                String alias = "UTC" + i;
                cert = createCertificate(alias,"Untrusted Test Certificate " + i, "urn:io:vantiq:client:untrusted:" + i, false);
                untrustedTestCerts.add(alias);
            }

            exampleServer = new ExampleServer();
            exampleServer.startup().get();
        }
        catch (Exception e) {
            fail("Trapped exception during ExampleServer startup: " + Utils.errFromExc(e));
        }

    }

    /**
     * Arrange for the client's certificate to be trusted by the server
     * @param theCert certificate to be trusted
     * @throws Exception
     */
    public void trustCertificateOnServer(X509Certificate theCert) throws Exception {
        DirectoryCertificateValidator certificateValidator = new DirectoryCertificateValidator(pkiDir);
        certificateValidator.addTrustedCertificate(theCert);
    }

    /**
     * Create certificate (self-signed) for use in the tests
     *
     * @param alias Alias/key by which the certificate in question can be referenced
     * @param commonName Name for the entity identified by the certificate
     * @param appUri URI for the application being used
     * @param trustIt Whether the certificate should be trusted or untrusted/invalid.  Untrusted used in tests.
     * @return The certificate created.
     */
    public X509Certificate createCertificate(String alias, String commonName, String appUri, boolean trustIt) {
        KeyPair keyPair;
        X509Certificate certificate = null;
        try {

            // If we're generating a key we trust, then use RSA keys as that's what our OPC server wants.
            // Otherwise, we'll use an EC key which it doesn't like.  This will allow us to test that we can
            // successfully connect with a reasonable key, and that we fail as expected with an unreasonable one.

            if (trustIt) {
                keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            } else {
                keyPair = SelfSignedCertificateGenerator.generateEcKeyPair(256);
            }

            SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                    .setCommonName(commonName)
                    .setOrganization("vantiq")
                    .setOrganizationalUnit("dev")
                    .setLocalityName("Walnut Creek")
                    .setStateName("CA")
                    .setCountryCode("US")
                    .setApplicationUri(appUri)
                    .addDnsName("localhost")
                    .addIpAddress("127.0.0.1");

            if (!trustIt) {
                // As outlined above
                builder.setSignatureAlgorithm("ECDSAwithSHA1");
            }

            // Get as many hostnames and IP addresses as we can listed in the certificate.
            for (String hostname : HostnameUtil.getHostnames("0.0.0.0")) {
                if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
                    builder.addIpAddress(hostname);
                } else {
                    builder.addDnsName(hostname);
                }
            }

            certificate = builder.build();
            ksm.addCert(alias, keyPair.getPrivate(), certificate);

            if (trustIt) {
                trustCertificateOnServer(certificate);
            }
        } catch (NoSuchAlgorithmException e) {
            Utils.unexpectedException(e);
        } catch (Exception e) {
            Utils.unexpectedException(e);
        }

        return certificate;
    }

    /**
     * Cleanup after tests
     *
     * Performs cleanup functions.  These include
     *   - Shutting down the test server
     */
    @After
    public void cleanup() {
        try {
            if (exampleServer != null) {
                exampleServer.shutdown().get();
            }
        } catch (Exception e) {
            fail("Trapped exception during shutdown: " + Utils.errFromExc(e));
        }
    }
}
