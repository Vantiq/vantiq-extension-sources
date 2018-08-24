
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.udp;

// Author: Alex Blumer

import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * This class creates a UDP source customizable by a Configuration document. It can send and receive in JSON, XML, or 
 * pure bytes.
 * <br>
 * The configuration document for this program is a JSON file either in the working directory or specified as the 
 * first argument when running this program.
 * 
 * <dl>
 * <dt><span class="strong">Vantiq Options</span></dt>
 * <dd><ul>
 * <li>{@code targetServer} -- The Vantiq site that hosts the projects to which the sources will connect. 
 * <li>{@code authToken} -- The authentication token that will allow this server to connect to Vantiq. Be aware that 
 *              this is namespace specific, so if you intend to connect to sources across several namespaces then 
 *              multiple config files will be required, each with its own instance of ConfigurableUDPSource. 
 *              Throws a RuntimeException when not set.
 * <li>{@code sources} -- An array containing the names of the sources that will be connected to. 
 *              Throws a RuntimeException when not set.
 * </ul></dd>
 * 
 * <dt><span class="strong">UDP Options</span></dt>
 * <dd><ul>
 * <li>{@code defaultBindPort} -- Sets the default port to which sources will bind if no other port is specified.
 *              Defaults to 3141 when not set
 * <li>{@code defaultBindAddress} -- Sets the default address to which the sources will bind if no other address is
 *              specified. Attempts to find a valid local address if it cannot find the given address. Typically only
 *              localhost and the computer's IP address will work.
 * <li>{@code maxPacketSize} -- Sets the maximum number of data bytes that the UDP socket can receive in a single 
 *              message. Defaults to 1024 when not set.
 * </ul></dd>
 * </dl>
 * 
 * The source Configuration document looks as below:<br>
 *     
 * <pre>{
 *     udpSourceConfig: {
 *         general:{
 *             &lt;general options&gt;
 *         },
 *         incoming:{
 *             &lt;incoming/Notification options&gt;
 *         },
 *         outgoing:{
 *             &lt;outgoing/Publish options&gt;
 *         }
 *     }
 *}</pre>
 * 
 *
 * <br>
 * The options for general are below:<br>
 * <ul>
 *      <li>{@code listenAddress}: Optional. A String representing the address on which UDP messages will be sent and 
 *                      received. Typically only the localhost and the host's assigned IP address will work.
 *                      Default is set by the server config document.
 *      <li>{@code listenPort}: Optional. The port number on which UDP messages will be sent and received. 
 *                      Default is set by the server config document.
 * </ul>
 * <br>
 * The options for incoming and outgoing are described in {@link UDPNotificationHandler} and {@link UDPPublishHandler}
 * respectively.
 */
public class ConfigurableUDPSource {

    /**
     * A handler for dealing with publishes for sources without a configured handler setup. Will log a warning noting
     * which source sent the Publish.
     */
    // Passes data to the UDP server or tells the program to stop
    static Handler<ExtensionServiceMessage> UDPDefaultPublish = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            // Translate the data from the Publish message to what we want it to be
            log.warn("Vantiq requesting message sent from {}, but no handler is set up for it", message.getSourceName());
        }
    };

    /**
     * Creates publish and notification handlers for any messages relating to a source based on the configuration
     * document
     */
    static UDPConfigHandler UDPConfig = new UDPConfigHandler();
    
    static void clearSourceHandlers(String sourceName) {
        ExtensionWebSocketClient client = clients.get(sourceName);

        synchronized (socketLock) { // Don't want to edit while another is adding to or removing from the udpSocket Map
            // Disassociate the source from the socket
            for (Map.Entry<DatagramSocket,List<String>> entry : udpSocketToSources.entrySet()) {
                List<String> list = entry.getValue();
                if (list.contains(client.getSourceName())) {
                    list.remove(client.getSourceName());
                    if (list.isEmpty()) {
                        // Get rid of the socket if it's the only one left
                        DatagramSocket socket = entry.getKey();
                        udpSocketToSources.remove(socket);
                        socket.close();
                    }
                    
                    break;
                }
            }
        }
        // Clear the handlers associated with the source
        notificationHandlers.remove(sourceName);
        client.setPublishHandler(UDPDefaultPublish);
        
        // Clear all source address ports
        sourceAddresses.remove(sourceName);
        sourcePorts.remove(sourceName);
        sourceServers.remove(sourceName);
    }

    static Handler<ExtensionServiceMessage> UDPReconnectHandler = new Handler<ExtensionServiceMessage>() {

        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            clearSourceHandlers(message.getSourceName());
            
            ExtensionWebSocketClient client = clients.get(message.getSourceName());
            client.connectToSource();
            try {
                if (client.getSourceConnectionFuture().get(10, TimeUnit.SECONDS) == false) {
                    client.stop();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                client.stop();
                e.printStackTrace();
            }
        }
        
    };
    
    static Handler<ExtensionWebSocketClient> UDPCloseHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient client) {
            clearSourceHandlers(client.getSourceName());
            
            client.initiateFullConnection(targetVantiqServer, authToken);
            try {
                if (client.getSourceConnectionFuture().get(10, TimeUnit.SECONDS) == false) {
                    client.stop();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                client.stop();
                e.printStackTrace();
            }
        }
    };
    /**
     * Attempts to create a {@link DatagramSocket} on a given port and address. If successful it will create a
     * {@link List} with {@code sourceName} in it, and add it to {@link #udpSocketToSources} to keep track of which
     * sources are associated with which socket.
     * <p>
     * Logs a debug message describing the success or failure of the attempt.
     *
     * @param port          The port that the {@link DatagramSocket} tries to open on.
     * @param address       The address on which the {@link DatagramSocket} tries to open.
     * @param sourceName    The name of the source trying to create the {@link DatagramSocket}
     * @return              A {@link DatagramSocket} listening on the given port and address, or null if it could not
     *                      be created
     */
    public static DatagramSocket createUDPSocket(int port, InetAddress address, String sourceName) {
        try {
            DatagramSocket socket = new DatagramSocket(port, address);
            List<String> sources = new ArrayList<>();
            sources.add(sourceName);
            udpSocketToSources.put(socket, sources);
            new Thread(new UDPListener(socket)).start();
            log.debug("Source '{}' succeeded in creating UDP socket on port '{}' and address '{}'"
                    , sourceName, port, address);
            return socket;
        }
        catch (Exception e) {
            log.debug("Source '{}' failed to create UDP socket on port '{}' and address '{}'. Will try to share with "
                    + "other sources.", sourceName, port, address);
            log.trace("Error for binding failure is ", e);
            return null;
        }
    }

    /**
     * Searches for a {@link DatagramSocket} already on the given port and address. If successful, it will add
     * {@code sourceName} to {@link #udpSocketToSources} for the {@link DatagramSocket} found, and will log a debug
     * message stating the sources it is sharing the socket with
     *
     * @param port          The port which the {@link DatagramSocket} should be on
     * @param address       The address which the {@link DatagramSocket} should be on
     * @param sourceName    The name of the source trying to find the {@link DatagramSocket}
     * @return              The {@link DatagramSocket} listening on the given port and address, or null if none could
     *                      be found
     */
    public static DatagramSocket listenOnUDPSocket(int port, InetAddress address, String sourceName) {
        for (Map.Entry<DatagramSocket,List<String>> entry : udpSocketToSources.entrySet()) {
            DatagramSocket sock = entry.getKey();
            if (sock.getLocalAddress().equals(address) && sock.getLocalPort() == port) {
                List<String> sources = entry.getValue();
                log.debug("Source '{}' is sharing its UDP socket with sources {}", sourceName, sources);
                sources.add(sourceName);
                return sock;
            }
        }
        return null;
    }



    /**
     * Attach the {@link UDPNotificationHandler} for a source to the port and address specified in the Configuration
     * document
     *
     * @param handler       The {@link UDPNotificationHandler} for the source to use
     * @param sourceName    The name of the source
     * @param incoming      The 'incoming' section source's Configuration document
     */
    public static void setNotificationHandler(UDPNotificationHandler handler, String sourceName, Map incoming) {
        log.trace("Setting Notification handler for '{}'", sourceName);
        notificationHandlers.put(sourceName, handler);

        List<InetAddress> addresses = null;
        List<Integer> ports = null;
        List<List> servers = null;

        // Obtain all valid addresses, ports, and servers from the respective arrays specified in the configuration document
        if (incoming != null) {
            if (incoming.get("receiveAddresses") instanceof List) {
                addresses = getValidInetAddresses((List) incoming.get("receiveAddresses"), sourceName);
            }
            if (incoming.get("receivePorts") instanceof List) {
                ports = getValidPorts((List) incoming.get("receivePorts"));
            }
            if (incoming.get("receiveServers") instanceof List) {
                servers = getValidServers((List) incoming.get("receiveServers"));
            }
        }

        // Use the addresses specified
        if (addresses != null) {
            sourceAddresses.put(sourceName, addresses);
            log.debug("Source '{}' listening for addresses {}", sourceName, addresses);
        }
        // We should ignore all addresses only if there are requested servers and no valid settings for addresses or ports
        else if (servers != null && !valueIsTrue(incoming, "receiveAllAddresses")
                && !valueIsTrue(incoming, "receiveAllPorts") && ports == null) {
            sourceAddresses.put(sourceName, NO_ADDR);
        }
        // Either receiveAllAddresses is set to true with no addresses specified
        // or the settings allow defaulting to all addresses
        else {
            sourceAddresses.put(sourceName, ALL_ADDR);
            log.debug("Source '{}' listening for all addresses", sourceName);
        }

        // Use the ports if specified
        if (ports != null) {
            sourcePorts.put(sourceName, ports);
            log.debug("Source '{}' listening on ports {}", sourceName, ports);
        }
        // We should ignore all ports only if there are requested servers and no valid settings for addresses or ports
        else if (servers != null && !valueIsTrue(incoming, "receiveAllAddresses")
                && !valueIsTrue(incoming, "receiveAllPorts") && addresses == null) {
            sourcePorts.put(sourceName, NO_PORTS);
        }
        // Either receiveAllPorts is set to true with no ports specified or the settings allow defaulting to all ports
        else {
            sourcePorts.put(sourceName, ALL_PORTS);
            log.debug("Source '{}' listening on all ports", sourceName);
        }

        // Use the servers specified
        if (servers != null) {
            sourceServers.put(sourceName, servers);
        }
    }

    /**
     * Creates a {@link List} of InetAddresses from a {@link List} of Strings.
     * Any non-string objects or invalid addresses in the input {@link List} will be ignored.
     *
     * @param potentialAddresses    A {@link List} of Strings that represent URLs or IP addresses
     * @param sourceName            The name of the source for which this operation is being performed. Used only for
     *                              warning messages when hosts cannot be found
     * @return                      A {@link List}&lt;{@link InetAddress}&gt; containing all addresses obtained from
     *                              {@code potentialAddresses}, or null if no such addresses could be found
     */
    static List<InetAddress> getValidInetAddresses(List potentialAddresses, String sourceName) {
        List<InetAddress> addresses = new ArrayList<>();
        for (Object name : potentialAddresses) {
            if (name instanceof String) {
                try {
                    InetAddress a = InetAddress.getByName((String) name);
                    addresses.add(a);
                }
                catch (UnknownHostException e) {
                    log.warn("Requested receiving address '{}' specified for source '{}' "
                            + "could not be found. This address will be ignored", name, sourceName);
                }
            }
        }
        return !addresses.isEmpty() ? addresses : null;
    }

    /**
     * Creates a {@link List} of port numbers from a {@link List} of integers.
     * Any non-integers or invalid ports in the input {@link List} will be ignored.
     *
     * @param potentialPorts    A {@link List} of port numbers
     * @return                  A {@link List}&lt;Integer&gt; containing all ports obtained from
     *                          {@code potentialPorts}, or null if no such ports could be found
     */
    static List<Integer> getValidPorts(List potentialPorts) {
        List<Integer> ports = new ArrayList<>();
        for (Object port : potentialPorts) {
            if (port instanceof Integer && (int) port >= 0 && (int) port <= 65535) {
                ports.add((int) port);
            }
        }
        return !ports.isEmpty() ? ports : null;
    }

    /**
     * If potentialServers is a non-empty {@link List}, it removes all objects that are not servers
     * (see {@link #isValidServer}) and turns the String component of any valid servers into an {@link InetAddress}
     * based on the String component
     *
     * @param potentialServers      The {@link List} that contains values to convert into InetAddresses
     * @return                      A {@link List}&lt;{@link List}&gt; that contains all the valid servers from
     *                              {@code potentialServers}, or null if no valid servers were found
     */
    static List<List> getValidServers(List potentialServers) {
        if (potentialServers == null || potentialServers.isEmpty()) {
            return null;
        }

        List<List> servers = new ArrayList<>();
        for (Object server : potentialServers) {
            if (isValidServer(server)) {
                List<Object> s = (List) server;
                String addressName = (String) ((List) server).get(0);
                try {
                    s.set(0, InetAddress.getByName(addressName));
                    servers.add(s);
                }
                catch (UnknownHostException e) {
                    log.warn("Requested receiving address '{}' specified for server '{}'" + "could not be found. "
                            + "This address will be ignored", addressName, server);
                }
            }
        }
        return !servers.isEmpty() ? servers : null;
    }

    /**
     * Checks to see if the given {@code Object} is a valid description of a server
     *
     * @param server    The {@code Object} to check for validity
     * @return          {@code true} if {@code server} is a {@link List} of size 2 whose first entry is a 
     *                  {@link String} and whose second entry is an integer between 0 and 65535, inclusive
     */
    static boolean isValidServer(Object server) {
        return server instanceof List && ((List) server).size() == 2 && ((List) server).get(0) instanceof String &&
                ((List) server).get(1) instanceof Integer && (int) ((List) server).get(1) >= 0 &&
                (int) ((List) server).get(1) <= 65535;
    }

    /**
     * A set of {@link UDPNotificationHandler} keyed to the name of the source it is connected to
     */
    static Map<String, UDPNotificationHandler> notificationHandlers = new ConcurrentHashMap<>();
    /**
     * A map keyed by source containing either a {@link List} of {@link InetAddress} to accept transmissions from,
     * the value {@link #ALL_ADDR}, or the value {@link #NO_ADDR}.
     */
    static Map<String, Object> sourceAddresses = new LinkedHashMap<>();
    /**
     * A map keyed by source containing either a {@link List} of ports to accept transmissions from, the value
     * {@link #ALL_PORTS}, or the value {@link #NO_PORTS}.
     */
    static Map<String, Object> sourcePorts = new LinkedHashMap<>();
    /**
     * A map keyed by source containing either a {@link List} of servers to accept transmissions from or null if
     * no servers were specified in the configuration document.
     */
    static Map<String, List<List>> sourceServers = new LinkedHashMap<>();
    /**
     * A {@link Map} keyed by source that contains the socket the source is using
     */
    static Map<DatagramSocket, List<String>> udpSocketToSources = new LinkedHashMap<>();
    /**
     * An Object used to ensure only one source edits udpSocketToSources at once
     */
    static Object socketLock = new Object();
    /**
     * The {@link ExtensionWebSocketClient} that communicates with Vantiq
     */
    static Map<String, ExtensionWebSocketClient> clients = new LinkedHashMap<>();
    /**
     * The default address for a UDP server to listen on.
     */
    static InetAddress LISTENING_ADDRESS = null;
    /**
     * The default port for the UDP server to listen on
     */
    static int LISTENING_PORT = 3141;
    /**
     * Max byte size receivable through UDP servers
     */
    static int MAX_UDP_DATA = 1024;
    /**
     * An Slf4j logger
     */
    static final Logger log = LoggerFactory.getLogger(ConfigurableUDPSource.class);
    /**
     * A constant used to signify that a source is listening to all addresses. Used as a key in {@link #sourceAddresses}
     */
    static final String ALL_ADDR = "_ALL";
    /**
     * A constant used to signify that a source is listening to all ports. Used as a key in {@link #sourceAddresses}
     */
    static final int ALL_PORTS = -1;
    /**
     * A constant used to signify that a source is listening to all addresses. Used as a key in {@link #sourceAddresses}
     */
    static final String NO_ADDR = "_NONE";
    /**
     * A constant used to signify that a source is listening to all ports. Used as a key in {@link #sourceAddresses}
     */
    static final int NO_PORTS = -2;
    /**
     * The desired level of log statements
     */
    static String targetVantiqServer = null;
    /**
     * The authentication token used to connect to Vantiq
     */
    static String authToken = null;

    /**
     * Turn the given JSON file into a {@link Map}. 
     * 
     * @param fileName  The name of the JSON file holding the server configuration.
     * @return          A {@link Map} that holds the contents of the JSON file.
     */
    static Map<String, Object> obtainServerConfig(String fileName) {
        File configFile = new File(fileName);
        log.debug("{}", configFile.getAbsolutePath());
        Map<String, Object>  config = new LinkedHashMap();
        ObjectMapper mapper = new ObjectMapper();
        try {
            config = mapper.readValue(configFile, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not find valid server config file. Expected location: '" 
                    + configFile.getAbsolutePath() + "'", e);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when trying to read the server config file. "
                    + "Please ensure it is proper JSON.", e);
        }

        return config;

    }

    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The {@link Map} obtained from the config file
     */
    static void setupServer(Map config) {
        MAX_UDP_DATA = config.get("maxPacketSize") instanceof Integer ? (int) config.get("maxPacketSize") : 1024;
        LISTENING_PORT = config.get("defaultBindPort") instanceof Integer ? (int) config.get("defaultBindPort") :
                3141;
        
        if (config.get("targetServer") instanceof String) {
            targetVantiqServer = (String) config.get("targetServer") ;
        } else {
            throw new RuntimeException("Missing authentication token in config file. Please place in 'authToken'.");
        }
        
        if (config.get("authToken") instanceof String) {
            authToken = (String) config.get("authToken") ;
        } else {
            throw new RuntimeException("Missing authentication token in config file. Please place in 'authToken'.");
        }

        if (config.get("defaultBindAddress") instanceof String) {
            try {
                String address = (String) config.get("defaultBindAddress");
                LISTENING_ADDRESS = InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                log.error("Given default bind address could not be found. Trying to find local address");
            }
        }
        // There was no valid defaultBindAddress use the default of localhost
        if (LISTENING_ADDRESS == null) {
            try {
                LISTENING_ADDRESS = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                throw new RuntimeException("Could not find a valid local address for the default bind address.",e);
            }
        }
    }

    /**
     * The main function of the app. Here it: <br>
     * 1) Sets up the WebSocket connection through {@link ExtensionWebSocketClient} and sets up the UDP socket<br>
     * 2) Stalls until the WebSocket connection completes then authenticates, exiting on a failure<br>
     * 3) Stalls until it connects as a source. Once connected, any information received from publish messages
     *      is automatically dealt with through handlers in {@link ExtensionWebSocketClient}<br>
     * 4) Reads data through the UDP connection, passing it through {@link #sendFromDatagram} to notify Vantiq
     * 5) Exits upon receiving a query from any source
     *
     * @param args  Not used
     */
    public static void main(String[] args) {
        Map config;
        if (args != null && args.length != 0) {
            config = obtainServerConfig(args[0]);
        }
        else {
            config = obtainServerConfig("config.json");
        }
        setupServer(config);
        /*
         * The names of the sources we want to connect to and the address to which {@link ExtensionWebSocketClient} should
         * connect. For target, replace "ws" with "wss" to access the secure connection, and "localhost:8080" with
         * your Vantiq deployment's address, typically "dev.vantiq.com"
         */
        if (!(config.get("sources") instanceof List)) {
            throw new RuntimeException("No source names given in server config file.");
        }
        ((List<Object>) config.get("sources")).removeIf((obj) -> !(obj instanceof String));
        List<String> sources = ((List<String>) config.get("sources"));
        
        if (sources.isEmpty()) {
            throw new RuntimeException("No source names given.");
        }


        /*
         * 1) Sets up the WebSocket connection through ExtensionWebSocketClient and sets up the UDP socket.
         *
         * It opens the UDP socket on the localhost at port 3141 and opens ExtensionWebSocketClient at the target address
         * Then, it sets the default {@link Handler} defined earlier for several situations
         */
        // A CompletableFuture that will return true only if every source connects fully
        CompletableFuture<Boolean> connectionWaiter = CompletableFuture.completedFuture(true);
        for (String sourceName : sources) {
            // Create an ExtensionWebSocketClient for the source
            ExtensionWebSocketClient client = new ExtensionWebSocketClient(sourceName);

            // Set the handlers for the client
            client.setPublishHandler(UDPDefaultPublish);
            client.setConfigHandler(UDPConfig);
            client.setReconnectHandler(UDPReconnectHandler);
            client.setCloseHandler(UDPCloseHandler);

            // Initiate the WebSocket connection, authentication, and source connection for the source
            CompletableFuture<Boolean> future;
            future = client.initiateFullConnection(targetVantiqServer, authToken);

            // Add the result of the source connection to the chain
            connectionWaiter = connectionWaiter.thenCombineAsync(future,
                    (prevSucceeded, succeeded) -> prevSucceeded && succeeded);

            // Save the client
            clients.put(sourceName, client);
        }
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = connectionWaiter.get(10, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            log.error("Timeout: not all WebSocket connections succeeded within 10 seconds.");
        }
        catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources. Exiting...");
            for (String sourceName : sources) {
                if (!clients.get(sourceName).isOpen()) {
                    log.error("Failed to connect to '{}' for source '{}'", targetVantiqServer, sourceName);
                }
                else if (!clients.get(sourceName).isAuthed()) {
                    log.error("Failed to auth within 10 seconds using the given auth data for source '{}'", sourceName);
                }
                else if (!clients.get(sourceName).isConnected()) {
                    log.error("Failed to connect to '{}' within 10 seconds", sourceName);
                }
                clients.get(sourceName).stop();
            }
            log.error("All clients closed. Done exiting.\n");
            return;
        }

        // The ExtensionWebSocketClients have separate threads, thanks to the WebSocket being its own thread
        // Because of this, exiting main means that the program will only end once all Clients close connections
    }

    /**
     * An internal class intended to act as an asynchronous listener on a {@link DatagramSocket}.
     * Sends results to {@link ConfigurableUDPSource#sendFromDatagram}
     */
    static class UDPListener implements Runnable {
        /**
         * The {@link DatagramSocket} to which this listener will receive data from.
         */
        DatagramSocket socket;

        /**
         * Creates a {@link UDPListener} that will listen on the given {@code socket}.
         *
         * @param socket    The socket that this instance will listen to.
         */
        public UDPListener(DatagramSocket socket) {
            this.socket = socket;
        }

        /**
         * Listen for a UDP packet on the {@link DatagramSocket} specified in the constructor, then notifies the sources.
         * On receipt of a packet, it starts a new version of itself so it can keep listening to the server, then sends
         * the packet on to {@link ConfigurableUDPSource#sendFromDatagram}.
         */
        public void run() {
            DatagramPacket packet = new DatagramPacket(new byte[MAX_UDP_DATA], MAX_UDP_DATA);
            List<String> sources = udpSocketToSources.get(socket);
            try {
                socket.receive(packet);  // A blocking call to receive a packet
                new Thread(new UDPListener(socket)).start(); // Asynchronously start listening again
                ConfigurableUDPSource.sendFromDatagram(packet, sources);
            }
            catch (SocketException e) {
                // "socket closed" is expected on shutdown, we can ignore
                if (!e.getMessage().equalsIgnoreCase("socket closed")) {
                    log.error("Error occurred on listening to UDP. No longer listening for sources " +
                            udpSocketToSources.get(socket), e);
                }
            }
            catch (Exception e) {
                log.warn("Error occurred on notification attempt for sources " + udpSocketToSources.get(socket), e);
            }

        }
    }

    /**
     * A method that checks to see if the sources in {@code sources} are configured to receive from the source of
     * {@code packet}, and if it is then it sends {@code packet} to the source's {@link UDPNotificationHandler}
     *
     * @param packet    The {@link DatagramPacket} that received the message
     * @param sources   A {@link List} containing the names of the sources which will be notified
     */
    // Translates the UDP DatagramPacket into a format that the Vantiq server expects
    public static void sendFromDatagram(DatagramPacket packet, List<String> sources) {
        log.debug("UDP Packet received");
        log.trace("{}", new String(packet.getData()));
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        log.debug("UDP message received from address '{}' and port '{}' for sources {}", address, port, sources);

        for (String sourceName : sources) {
            // Notify
            if (receivingFromServer(sourceName, port, address)) {
                log.debug("Sending Notification for source '{}'", sourceName);
                notificationHandlers.get(sourceName).handleMessage(packet);
            }
        }
    }

    /**
     * Checks if the source was configured to receive from the given port and address
     *
     * @param sourceName    Name of the source
     * @param port          The port number to check
     * @param address       The {@link InetAddress} to check
     * @return              {@code true} if any server matches both the port and address, {@code false} otherwise
     */
    static boolean receivingFromServer(String sourceName, int port, InetAddress address) {
        return (matchesPort(sourceName, port) && matchesAddress(sourceName, address)) ||
                matchesServer(sourceName, port, address);
    }

    /**
     * Checks if the source is configured to receive from the given port
     *
     * @param sourceName    Name of the source
     * @param port          The port number to check
     * @return              {@code true} if the source's config document said to receive from the port, {@code false}
     *                      otherwise
     */
    static boolean matchesPort(String sourceName, int port) {
        Object srcPorts = sourcePorts.get(sourceName);
        if (srcPorts instanceof Integer) {
            return (int) srcPorts == ALL_PORTS;
        }
        else if (srcPorts instanceof List) {
            return ((List) srcPorts).contains(port);
        }
        else {
            return false;
        }
    }

    /**
     * Checks if the source is configured to receive from the given address
     *
     * @param sourceName    Name of the source
     * @param address       The {@link InetAddress} to check
     * @return              {@code true} if the source's config document said to receive from the address,
     *                      {@code false} otherwise
     */
    static boolean matchesAddress(String sourceName, InetAddress address) {
        Object srcAddr = sourceAddresses.get(sourceName);
        if (srcAddr instanceof String) {
            return srcAddr.equals(ALL_ADDR);
        }
        else if (srcAddr instanceof List) {
            return ((List<InetAddress>)sourceAddresses.get(sourceName))
                    .stream().anyMatch((addr) -> addr.equals(address));
        }
        else {
            return false;
        }
    }

    /**
     * Checks if the given port and address match any servers specified in the source's config document
     *
     * @param sourceName    Name of the source to check for servers
     * @param port          The port to compare against the servers
     * @param address       The {@link InetAddress} to compare against the servers
     * @return              {@code true} if any server matches both the port and address, {@code false} otherwise
     */
    static boolean matchesServer(String sourceName, int port, InetAddress address) {
        if (sourceServers.get(sourceName) == null) {
            return false;
        }
        for (List server : sourceServers.get(sourceName)) {
            if (server.get(0).equals(address) && (int) server.get(1) == port) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the key in a given map exists, and if it is boolean also whether it is true.
     *
     * @param map   The {@link Map} which you would like to check
     * @param key   The key which you would like to check
     * @return      {@code true} if {@code map.get(key)} is either a boolean and {@code true}, or not a boolean and
     *              non-null, {@code false} otherwise
     */
    static boolean valueIsTrue(Map map, Object key) {
        Object val = map.get(key);
        if (val instanceof Boolean) {
            return (boolean) val;
        }
        else {
            return false;
        }
    }
}