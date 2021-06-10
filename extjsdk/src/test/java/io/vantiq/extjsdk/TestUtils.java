package io.vantiq.extjsdk;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class TestUtils  {

    private static final String FAKE_URL = "http://somewhere/else";
    private static final String FAKE_TOKEN = "xxxx====";
    private static final String FAKE_SOURCE = "someSource";
    private static final String FAKE_PORT = "8000";
    private static final String FAKE_PINGS = "true";
    public static final String TARGET_SERVER_PROP = "targetServer";
    public static final String AUTH_TOKEN_PROP = "authToken";
    public static final String OTHER_PROP = "otherProperty";

    public static String SECRET_CREDENTIALS = "CONNECTOR_AUTH_TOKEN";
    String envVarAuthToken;

    @Before
    public void setup() {
        envVarAuthToken = System.getenv(SECRET_CREDENTIALS);
    }

    @Test
    public void testGetConfigAlone() throws Exception {
        BufferedWriter bw = null;
        File f = null;
        try {
            Path p = Files.createFile( Paths.get("server.config"));
            f = new File(p.toString());
            f.deleteOnExit();
            
            bw = fillProps(p, true);

            checkPropBeforeObtainingServer();
            checkProps();
            Utils.clearServerConfigProperties();
            checkPropBeforeObtainingServer();
        } finally {
            if (bw != null) {
                bw.close();
            }
            if (f != null) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    @Test
    public void testGetConfigInDir() throws Exception {
        BufferedWriter bw = null;
        File f = null;
        File dir = null;
        try {
            dir = new File("serverConfig");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdir();
            dir.deleteOnExit();
            Path p = Files.createFile(Paths.get("serverConfig/server.config"));
            f = new File(p.toString());
            f.deleteOnExit();
            bw = fillProps(p, true);

            checkPropBeforeObtainingServer();
            checkProps();
            Utils.clearServerConfigProperties();
            checkPropBeforeObtainingServer();
        } finally {
            if (bw != null) {
                bw.close();
            }
            if (f != null) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
            if (dir != null) {
                //noinspection ResultOfMethodCallIgnored
                dir.delete();
            }
        }
    }

    @Test
    public void testGetEnvVar() throws Exception {
        assumeTrue("\"CONNECTOR_AUTH_TOKEN\" environment variable must be set equal to \"xxxx====\"",
                envVarAuthToken != null && envVarAuthToken.equals(FAKE_TOKEN));
        doEnvVarTests(false);
    }

    private void doEnvVarTests(boolean includeAuthToken) throws Exception {
        BufferedWriter bw = null;
        File f = null;
        File dir = null;
        try {
            dir = new File("serverConfig");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdir();
            dir.deleteOnExit();
            Path p = Files.createFile(Paths.get("serverConfig/server.config"));
            f = new File(p.toString());
            f.deleteOnExit();
            bw = fillProps(p, includeAuthToken);

            checkPropBeforeObtainingServer();
            checkProps();
            Utils.clearServerConfigProperties();
            checkPropBeforeObtainingServer();
        } finally {
            if (bw != null) {
                bw.close();
            }
            if (f != null) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
            if (dir != null) {
                //noinspection ResultOfMethodCallIgnored
                dir.delete();
            }
        }
    }
    
    private BufferedWriter fillProps(Path p, boolean includeAuthToken) throws IOException {
        BufferedWriter bw = Files.newBufferedWriter(p);
        bw.append(TARGET_SERVER_PROP + " = " + FAKE_URL + "\n");
        if (includeAuthToken) {
            bw.append(AUTH_TOKEN_PROP + " = " + FAKE_TOKEN + "\n");
        }
        bw.append(OTHER_PROP + " = " + FAKE_SOURCE + "\n");
        bw.append(Utils.SEND_PING_PROPERTY_NAME + " = " + FAKE_PINGS + "\n");
        bw.append(Utils.PORT_PROPERTY_NAME + " = " + FAKE_PORT + "\n");
        bw.close();
        return bw;
    }
    
    private void checkProps() {
        Properties props = Utils.obtainServerConfig();
        assert props.getProperty(TARGET_SERVER_PROP) != null;
        assert props.getProperty(TARGET_SERVER_PROP).contains(FAKE_URL);
        assert props.getProperty(AUTH_TOKEN_PROP) != null;
        assert props.getProperty(AUTH_TOKEN_PROP).contains(FAKE_TOKEN);
        assert props.getProperty(OTHER_PROP) != null;
        assert props.getProperty(OTHER_PROP).contains(FAKE_SOURCE);
        assert props.getProperty(Utils.SEND_PING_PROPERTY_NAME) != null;
        assert props.getProperty(Utils.SEND_PING_PROPERTY_NAME).contains(FAKE_PINGS);
        assert props.getProperty(Utils.PORT_PROPERTY_NAME) != null;
        assert props.getProperty(Utils.PORT_PROPERTY_NAME).contains(FAKE_PORT);
    }

    private void checkPropBeforeObtainingServer() {
        try {
            Utils.obtainSendPingStatus();
            fail("We should not get here, an exception should be thrown first");
        } catch (Exception e) {
            // Expected to catch exception here.
        }

        try {
            Utils.obtainTCPProbePort();
            fail("We should not get here, an exception should be thrown first");
        } catch (Exception e) {
            // Expected to catch exception here.
        }
    }
    
    @Before
    public void cleanupFiles() {
        Path p = Paths.get("server.config");
        File f = new File(p.toString());
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }

        File dir = new File("serverConfig");
        f = new File("serverConfig/server.config");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
