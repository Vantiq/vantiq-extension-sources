package io.vantiq.extjsdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class Utils {

    public static String PORT_PROPERTY_NAME = "tcpProbePort";
    public static String SERVER_CONFIG_DIR = "serverConfig";
    public static String SERVER_CONFIG_FILENAME = "server.config";
    public static String SECRET_CREDENTIALS = "CONNECTOR_AUTH_TOKEN";

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

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
        File configFile = new File(SERVER_CONFIG_DIR, fileName);
        Properties properties = new Properties();

        try {
            if (!configFile.exists()) {
                configFile = new File(fileName);
            }
            properties.load(Files.newInputStream(configFile.toPath()));

            // Next we check for the existence of an environment variable containing a secret reference to the authToken
            // We only set it if the value is not empty and if the authToken wasn't already specified in the
            // server.config
            String secretAuthToken = System.getenv(SECRET_CREDENTIALS);
            if (secretAuthToken != null && !secretAuthToken.trim().isEmpty()
                    && properties.getProperty("authToken") == null) {
                properties.setProperty("authToken", secretAuthToken);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not find valid server configuration file. Expected location: '"
                    + configFile.getAbsolutePath() + "'", e);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when trying to read the server configuration file. "
                    + "Please ensure it is formatted properly.", e);
        }

        return properties;
    }

    /**
     * Helper method used to
     * @return An Integer for the port value provided in the server.config file, or null if non was specified.
     * @throws Exception
     */
    public static Integer obtainTCPProbePort() {
        File configFile = new File(SERVER_CONFIG_DIR, SERVER_CONFIG_FILENAME);
        Properties properties = new Properties();

        try {
            if (!configFile.exists()) {
                configFile = new File(SERVER_CONFIG_FILENAME);
                if (!configFile.exists()) {
                    return null;
                }
            }
            properties.load(Files.newInputStream(configFile.toPath()));
            String portString = properties.getProperty(PORT_PROPERTY_NAME);
            if (portString != null) {
                return Integer.valueOf(portString);
            } else {
                return null;
            }
        } catch (IOException e) {
            log.error("An error occurred while trying to retrieve the TCP Probe port number.", e);
            return null;
        }
    }
}
