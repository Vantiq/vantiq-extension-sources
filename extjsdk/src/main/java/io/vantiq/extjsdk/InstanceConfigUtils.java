package io.vantiq.extjsdk;

import static io.vantiq.extjsdk.Utils.AUTH_TOKEN_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.PORT_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.SECRET_CREDENTIALS;
import static io.vantiq.extjsdk.Utils.SEND_PING_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.SERVER_CONFIG_DIR;
import static io.vantiq.extjsdk.Utils.SERVER_CONFIG_FILENAME;
import static io.vantiq.extjsdk.Utils.SOURCES_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.TARGET_SERVER_PROPERTY_NAME;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class InstanceConfigUtils {
    public static final String CONFIG_WAS_PROGRAMMATIC = "configWasProgrammatic";

    // The properties object containing the data from the server configuration file
    private Properties serverConfigProperties;
    
    /**
     * Provide server config properties programmatically.
     *
     * Used when the configuration properties necessary to connect to Vantiq are provided via a means outside
     * the reading of the server.config file.  This also sets a property telling this class that the information
     * was provided programmatically so that subsequent calls to obtainServerConfig will not overwrite things from
     * the server.config file or generate an error when that file doesn't exist.
     *
     * If this is called repeatedly, each will override the last.
     *
     * @param targetServer  String URL for the Vantiq server
     * @param authToken     String indicating the access token to use to connect to the server at targetServer
*    * @param sources       String containing a comma separated list of source names to use.
     * @param sendPings     Boolean indicating whether the client connection should ping the Vantiq server to maintain
     *                      the connection.
     * @param tcpProbePort  Integer providing a port to open for TCP probes when running in a Kubernetes environment.
     *
     * @return              Properties the server config properties as provided.
     */
    public Properties provideServerConfig(String targetServer, String authToken, String sources,
                                                 Boolean sendPings, Integer tcpProbePort) {
        Properties localSCP = new Properties();
        if (StringUtils.isAnyEmpty(targetServer, authToken, sources)) {
            throw new IllegalArgumentException("The targetServer, authToken, and sources parameters " +
                    "must be non-null & non-empty.");
        }
        localSCP.put(TARGET_SERVER_PROPERTY_NAME, targetServer);
        localSCP.put(AUTH_TOKEN_PROPERTY_NAME, authToken);
        localSCP.put(SOURCES_PROPERTY_NAME, sources);
        localSCP.put(SEND_PING_PROPERTY_NAME, (sendPings == null ? Boolean.FALSE.toString() : sendPings));
        if (tcpProbePort != null) {
            localSCP.put(PORT_PROPERTY_NAME, tcpProbePort.toString());
        }
        localSCP.put(CONFIG_WAS_PROGRAMMATIC, true);
        
        synchronized (this) {
            serverConfigProperties = localSCP;
        }
        return serverConfigProperties;
    }

    public Properties obtainServerConfig() {
        return obtainServerConfig(SERVER_CONFIG_FILENAME);
    }
    
    /**
     * Turn the given configuration file into a {@link Properties} object.
     *
     * @param fileName  The name of the configuration file holding the server configuration.
     * @return          The properties specified in the file.
     */
    public Properties obtainServerConfig(String fileName) {
        synchronized (this) {
            boolean wasProgrammatic = false;
            if (serverConfigProperties != null) {
                if (serverConfigProperties.get(CONFIG_WAS_PROGRAMMATIC) instanceof Boolean) {
                    wasProgrammatic = (Boolean) serverConfigProperties.get(CONFIG_WAS_PROGRAMMATIC);
                }
            }
            // If config props were provided programmatically, don't override them.  Simply return
            // what's already there.
            if (wasProgrammatic) {
                return serverConfigProperties;
            }
            
            // Otherwse, re-read the file
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
                        && serverConfigProperties.getProperty(AUTH_TOKEN_PROPERTY_NAME) == null) {
                    serverConfigProperties.setProperty(AUTH_TOKEN_PROPERTY_NAME, secretAuthToken);
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
    public Integer obtainTCPProbePort() {
        Properties localServerConfigProps;

        // Get a local copy of the props while synchronized
        synchronized (this) {
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
    public boolean obtainSendPingStatus() {
        Properties localServerConfigProps;

        // Get a local copy of the props while synchronized
        synchronized (this) {
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
    public void clearServerConfigProperties() {
        synchronized (this) {
            serverConfigProperties = null;
        }
    }
}
