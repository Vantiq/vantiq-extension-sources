package io.vantiq.extjsdk;

import java.util.Properties;

/**
 * Maintains a static configuration instance.  This class is used when the configuration
 * details are shared by all sources in the connector.
 *
 * Makes use of the InstanceConfigUtils class to manage the static instance.
 */

public class Utils {
    public static final String SEND_PING_PROPERTY_NAME = "sendPings";
    public static final String PORT_PROPERTY_NAME = "tcpProbePort";
    public static final String SERVER_CONFIG_DIR = "serverConfig";
    public static final String SERVER_CONFIG_FILENAME = "server.config";
    public static final String SECRET_CREDENTIALS = "CONNECTOR_AUTH_TOKEN";
    public static final String TARGET_SERVER_PROPERTY_NAME = "targetServer";
    public static final String AUTH_TOKEN_PROPERTY_NAME = "authToken";
    public static final String SOURCES_PROPERTY_NAME = "sources";
    public static final String CONFIG_WAS_PROGRAMMATIC = "configWasProgrammatic";

    // The properties object containing the data from the server configuration file
    private Properties serverConfigProperties;
    
    static InstanceConfigUtils staticInstance = null;
    
    private static void ensureStaticInstance() {
        if (staticInstance == null) {
            staticInstance = new InstanceConfigUtils();
        }
    }
    
    public static InstanceConfigUtils getInstanceUtilsConfigInstance() {
        ensureStaticInstance();
        return staticInstance;
    }

    public static Properties obtainServerConfig() {
        ensureStaticInstance();
        return staticInstance.obtainServerConfig(SERVER_CONFIG_FILENAME);
    }
    
    /**
     * Turn the given configuration file into a {@link Properties} object.
     *
     * @param fileName  The name of the configuration file holding the server configuration.
     * @return          The properties specified in the file.
     */
    public static Properties obtainServerConfig(String fileName) {
        ensureStaticInstance();
        return staticInstance.obtainServerConfig(fileName);
    }

    /**
     * Helper method used to get the TCP Probe Port if specified in the server.config
     *
     * @return An Integer for the port value provided in the server.config file, or null if non was specified.
     */
    public static Integer obtainTCPProbePort() {
        ensureStaticInstance();
        return staticInstance.obtainTCPProbePort();
    }

    /**
     * Helper method used to get the sendPings property if specified in the server.config
     *
     * @return The boolean value for the sendPings property, or false if it wasn't specified
     */
    public static boolean obtainSendPingStatus() {
        ensureStaticInstance();
        return staticInstance.obtainSendPingStatus();
    }

    /**
     * Method used to clear the local copy of server.config properties
     */
    public static void clearServerConfigProperties() {
        ensureStaticInstance();
        staticInstance.clearServerConfigProperties();
    }
}
