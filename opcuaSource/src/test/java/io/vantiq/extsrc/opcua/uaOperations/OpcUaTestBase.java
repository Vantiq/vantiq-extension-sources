package io.vantiq.extsrc.opcua.uaOperations;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.examples.server.ExampleServer;
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator;
import org.eclipse.milo.opcua.stack.core.util.CryptoRestrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.security.Security;
import java.security.cert.X509Certificate;

import static org.junit.Assert.fail;

@Slf4j
public class OpcUaTestBase {
    public static final String STANDARD_STORAGE_DIRECTORY = "/tmp/opcua-storage";

    protected  ExampleServer exampleServer = null;
    protected  File pkiDir = null;
//    static {
//        boolean removed = CryptoRestrictions.remove();
//        assert removed;
//
//        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
//        Security.addProvider(new BouncyCastleProvider());
//    }

    @BeforeClass
    public static void clearCruft() {
        Utils.deleteStorage(STANDARD_STORAGE_DIRECTORY);
    }

    @Before
    public void setup() {
        try {
            File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
            if (!securityTempDir.exists()) {
                throw new Exception("unable to find security temp dir: " + securityTempDir);
            }

            pkiDir = securityTempDir.toPath().resolve("pki").toFile();
            log.info("Example Server pki directory is: " + pkiDir.toString());

            KeyStoreManager ksm = new KeyStoreManager();

            File storage = new File(STANDARD_STORAGE_DIRECTORY);
            File secStore = new File(storage.getCanonicalPath() + File.separator + "security");
            if (!secStore.exists() && !secStore.mkdirs()) {
                fail("Could not create client's storage space");
            }
            ksm.load(secStore); // STANDARD_STORAGE_DIRECTORY));

            X509Certificate cert = ksm.getClientCertificate();

            trustCertificate(cert);

            exampleServer = new ExampleServer();
            exampleServer.startup().get();
        }
        catch (Exception e) {
            fail("Trapped exception during ExampleServer startup: " + Utils.errFromExc(e));
        }

    }

    public void trustCertificate(X509Certificate theCert) throws Exception {
        DirectoryCertificateValidator certificateValidator = new DirectoryCertificateValidator(pkiDir);
        certificateValidator.addTrustedCertificate(theCert);
    }

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
