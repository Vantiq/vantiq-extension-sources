package io.vantiq.extjsdk;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class Utils {

    // String used by methods to synch on
    private static final String SYNCH_LOCK = "synchLockString";

    public static final String SEND_PING_PROPERTY_NAME = "sendPings";
    public static final String PORT_PROPERTY_NAME = "tcpProbePort";
    public static final String SERVER_CONFIG_DIR = "serverConfig";
    public static final String SERVER_CONFIG_FILENAME = "server.config";
    public static final String SECRET_CREDENTIALS = "CONNECTOR_AUTH_TOKEN";

    // The properties object containing the data from the server configuration file
    private static Properties serverConfigProperties;

    public static Properties obtainServerConfig() {
        return obtainServerConfig(SERVER_CONFIG_FILENAME);
    }

    /**
     * Turn the given configuration file into a {@link Properties} object.
     *
     * @param fileName  The name of the configuration file holding the server configuration.
     * @return          The properties specified in the file.
     */
    public static Properties obtainServerConfig(String fileName) {
        synchronized (SYNCH_LOCK) {
            File configFile = new File(SERVER_CONFIG_DIR, fileName);
            serverConfigProperties = new Properties();

            try {
                if (!configFile.exists()) {
                    configFile = new File(fileName);
                }
                serverConfigProperties.load(Files.newInputStream(configFile.toPath()));

                // Next we check for the existence of an environment variable containing a secret reference to the authToken
                // We only set it if the value is not empty and if the authToken wasn't already specified in the
                // server.config
                String secretAuthToken = System.getenv(SECRET_CREDENTIALS);
                if (secretAuthToken != null && !StringUtils.isBlank(secretAuthToken)
                        && serverConfigProperties.getProperty("authToken") == null) {
                    serverConfigProperties.setProperty("authToken", secretAuthToken);
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not find valid server configuration file. Expected location: '"
                        + configFile.getAbsolutePath() + "'", e);
            } catch (Exception e) {
                throw new RuntimeException("Error occurred when trying to read the server configuration file. "
                        + "Please ensure it is formatted properly.", e);
            }

            return serverConfigProperties;
        }
    }

    /**
     * Helper method used to get the TCP Probe Port if specified in the server.config
     *
     * @return An Integer for the port value provided in the server.config file, or null if non was specified.
     */
    public static Integer obtainTCPProbePort() {
        Properties localServerConfigProps;

        // Get a local copy of the props while synchronized
        synchronized (SYNCH_LOCK) {
            localServerConfigProps = serverConfigProperties;
        }

        if (localServerConfigProps != null) {
            // Grab the property and return result
            String portString = localServerConfigProps.getProperty(PORT_PROPERTY_NAME);
            if (portString != null) {
                return Integer.valueOf(portString);
            }
        } else {
            throw new RuntimeException("Error occurred when checking for the tcpProbePort property. The " +
                    "server.config properties have not yet been captured. Before checking for specific properties, " +
                    "the 'obtainServerConfig' method must first be called.");
        }

        return null;
    }

    /**
     * Helper method used to get the sendPings property if specified in the server.config
     *
     * @return The boolean value for the sendPings property, or false if it wasn't specified
     */
    public static boolean obtainSendPingStatus() {
        Properties localServerConfigProps;

        // Get a local copy of the props while synchronized
        synchronized (SYNCH_LOCK) {
            localServerConfigProps = serverConfigProperties;
        }

        if (localServerConfigProps != null) {
            String sendPingString = localServerConfigProps.getProperty(SEND_PING_PROPERTY_NAME);
            if (sendPingString != null) {
                return Boolean.parseBoolean(sendPingString);
            }
        } else {
            throw new RuntimeException("Error occurred when checking for the sendPings property. The server.config " +
                    "properties have not yet been captured. Before checking for specific properties, the " +
                    "'obtainServerConfig' method must first be called.");
        }

        return false;
    }

    /**
     * Method used to clear the local copy of server.config properties
     */
    public static void clearServerConfigProperties() {
        synchronized (SYNCH_LOCK) {
            serverConfigProperties = null;
        }
    }
}
