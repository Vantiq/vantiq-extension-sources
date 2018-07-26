package io.vantiq.extsrc.udp;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

import java.io.File;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * This class creates a UDP source customizable by a Configuration document. It can send and receive in JSON, XML, or 
 * pure bytes.
 * 
 * The Configuration document looks as below:<br>
 *     
 * <pre>{
 *     extSrcConfig: {
 *         general:{
 *             &lt;options&gt;
 *         },
 *         incoming:{
 *             &lt;options&gt;
 *         },
 *         outgoing:{
 *             &lt;options&gt;
 *         }
 *     }
 *}</pre>
 * 
 *
 * <br>
 * The options for general are below:<br>
 * <ul>
 *      <li>{@code listenAddress}: Optional. A String representing address on which UDP messages will be sent and received.
 *                      Typically only the localhost and the host's assigned IP address will work. Default is localhost</li>
 *      <li>{@code listenPort}: Optional. The port number on which UDP messages will be sent and received. Default is 3141.</li>
 * </ul>
 * <br>
 * Options for Publishes (a.k.a. outgoing messages) are below. Note that if neither {@code passOut} option is set and
 *    {@code transformations} is not set or empty, then an empty Json message is sent to the target.
 * <ul>
 *      <li>{@code targetAddress}: Required to send Publishes. A {@link String} containing either the URL or IP address
 *                          to which outgoing UDP messages should be sent</li>
 *      <li>{@code targetPort}: Required to send Publishes. An integer that is the port # to which outgoing UDP messages
 *                          should be sent</li>
 *      <li>{@code passPureMapOut}: Optional. A boolean specifying that the Json object in Publish messages should be
 *                          passed through without changes. Default is {@code false}.</li>
 *      <li>{@code passUnspecifiedOut}: Optional. A boolean specifying that any values not transformed through the transformations specified in
 *                          {@code transformations} should be sent as part of the outgoing message. If no transformations are
 *                          specified in {@code transformations} then this is identical to passPureMapOut. Default is
 *                          {@code false}</li>
 *      <li>{@code transformations}: Optional. An array of transformations (see {@link MapTransformer}) to perform on
 *                          the message to be sent. Any values not transformed will not be passed unless
 *                          {@code passUnspecifiedOut} is set to {@code true}, and any values that are transformed will
 *                          not appear in the final message regardless of settings. Default is {@code null}</li>
 *      <li>{@code passBytesOutFrom}: Optional. The location from which you would like to place the outgoing data. 
 *                          This will take in the String at the location and send it using the byte values of the 
 *                          characters contained within. This will not add quotation marks around the output. Default
 *                          is null.</li>
 *      <li>{@code formatParser}: Optional. The settings for sending data as Ascii encoded bytes.
 *                          <ul>
 *                              <li>{@code pattern}: Required. The printf-style pattern that you wish the data to be 
 *                                      sent as. Any format arguments that are replaced with "null". See 
 *                                      {@link Formatter} for specifics on what is allowed.</li>
 *                              <li>{@code locations}: Required. An array of the locations from which the format 
 *                                      arguments should be pulled.</li>
 *                              <li>{@code altPatternLocation}: Optional. The location in a Publish message in which alternate 
 *                                      patterns may be placed. If a String is found in the given location of a Publish
 *                                      object, then that pattern will be used in place of {@code pattern}. This 
 *                                      pattern may be included directly in the Published object, but it is 
 *                                      recommended that it is placed in the object specified by the "Using" keyword,
 *                                      for purposes of readability.</li>
 *                              <li>{@code altLocations}: Optional. The location in a Publish message in which an 
 *                                      alternate set of locations may be placed. If an array of Strings is found in the
 *                                      given location of a Publish message, then those locations will be used instead 
 *                                      of {@code locations}. These locations may be included directly in the Published 
 *                                      object, but it is recommended that they are placed in the object specified by 
 *                                      the "Using" keyword, for purposes of readability.</li>
 *                          </ul>
 *      <li>{@code sendXMLRoot}: Optional. The name of the root element for the generated XML object. When set this will
 *                          send the entire object received as XML. Default is {@code null}.
 *      <li>{@code passCsvOutFrom}: Optional. A string specifying the location of an array of objects that will be 
 *                          converted into CSV format. Requires {@code useCsvSchema} to be set. Default is {@code null}
 *                          </li>
 *      <li>{@code useCsvSchema}: Optional. Defines the values that will be sent as CSV. Can be either A) an array of
 *                          the names of the values that will be taken from the objects and placed in CSV, or B) the 
 *                          location in which the previous will be for *all* Publishes. The values in the array will 
 *                          be placed as the header for the CSV document. Default is {@code null}</li>
 * </ul>
 * <br>
 * Options for Notifications (a.k.a. incoming messages) are as follows. If no options are valid then no Notifications will be sent,
 *    but if even one is set then their defaults are used. If none of the {@code passIn} options are set and
 *    {@code transformations} is not set or empty, then an empty Json message is sent to the Vantiq deployment, with
 *    {@code passRecAddress} or {@code passRecPort} added if they are set. Messages can be received from any address and
 *    port combination that exists in {@code receiveAddresses} and {@code receivePorts}, as well as any server specified
 *    in {@code receiveServers}
 * <ul>
 *      <li>{@code receiveAddresses}: Optional. An array of {@link String} containing either the URL or IP addresses
 *                        from which the source will receive messages. If left empty, it is ignored and the default
 *                        of {@code receiveAllAddresses} is used.</li>
 *      <li>{@code receiveAllAddresses}: Optional. A boolean stating whether you would like to receive Notifications of UDP messages
 *                          from any address. Overridden by a non-empty {@code receivingAddress}. Default is
 *                          {@code true}, unless {@code receiveServers} is the only {@code receive} option set, i.e
 *                          {@code receiveServers} is non-empty, {@code receivePorts} is empty,
 *                          and {@code receiveAllPorts} is not explicitly set to true.</li>
 *      <li>{@code receivePorts}: Optional. An array of the port numbers from which the source will receive messages.
 *                      If left empty, it is ignored and the default of {@code receiveAllPorts} is used.</li>
 *      <li>{@code receiveAllPorts}: Optional. A boolean stating whether you would like to receive Notifications of UDP messages
 *                          from any port. Overridden by a non-empty {@code receivingPorts}. Default is {@code true},
 *                          unless {@code receiveServers} is the only {@code receive} option set, i.e
 *                          {@code receiveServers} is non-empty, {@code receiveAddresses} is empty,
 *                          and {@code receiveAllAddresses} is not explicitly set to true.</li>
 *      <li>{@code receiveServers}: Optional. An array of pairs that specify both an address and port to receive
 *                          UDP messages from. A pair is formatted as an array containing first the address as a
 *                          {@link String} containing either the URL or IP address, and second the port number. If left
 *                          empty, defers to the other address and port settings</li>
 *      <li>{@code passPureMapIn}: Optional. A boolean specifying that the Json object in Publish messages should be passed through without changes.
 *                      Default is {@code false}.</li>
 *      <li>{@code passUnspecifiedIn}: Optional. A boolean specifying that any values not transformed through the transformations specified in
 *                          {@code transformations} should be sent as part of the outgoing message. If no transformations are
 *                          specified in {@code transformations} then this is identical to passPureMapOut. Default is {@code false}</li>
 *      <li>{@code passRecAddress}: Optional. A {@link String} representation of the location in which you want the
 *                          IP address from which the UDP message originated. Default is {@code null}, where the address
 *                          will not be recorded</li>
 *      <li>{@code passRecPort}: Optional. A {@link String} representation of the location in which you want the port
 *                          from which the UDP message originated. Default is {@code null}, where the port will not be
 *                          recorded.</li>
 *      <li>{@code transformations}: Optional. An array of transformations (see {@link MapTransformer}) to perform on
 *                     the message to be sent. Any values not transformed will not be passed unless
 *                     {@code passUnspecifiedIn} is set to {@code true}, and any values that are transformed will
 *                     not appear in the final message regardless of settings</li>
 *      <li>{@code passBytesInAs}: Optional. The location to which you would like to place the incoming data. 
 *                          This will take in the raw bytes received from the source and place them as chars of
 *                          the same value in a String. This is only useful if the source does not send JSON. 
 *                          Default is null.</li>
 *      <li>{@code regexParser}: Optional. The settings to use for parsing the incoming byte data using regex. This is
 *                          not used when {@code passBytesInAs} is not set. It contains the following options.
 *                          <ul>
 *                              <li>{@code pattern}: Required. The regex pattern that will be used to parse the incoming data.
 *                                      The parser will use the first match that appears in the data. See {@link Pattern}
 *                                      for specifics on what constitutes a valid pattern.</li>
 *                              <li>{@code locations}: Required. An array of the locations in which to place the capture groups 
 *                                      from {@code pattern}. These will override the location in {@code passBytesInAs}.
 *                                      </li>
 *                              <li>{@code flags}: Not yet implemented. An array of the regex flags you would like 
 *                                      enabled. See {@link Pattern} for descriptions of the flags available.</li>
 *                          </ul> 
 *      <li>{@code expectXMLIn}: Optional. Specifies that the data incoming from the UDP source will be in an XML format.
 *                          Note that this will throw away the name of the root element. If data is contained in the
 *                          root element, it will be placed in the location "" before transformations.
 *                          Default is false.</li>
 *      <li>{@code passXmlRootNameIn}: Optional. Specifies the location to which the name of the root element should
 *                          be placed. Does nothing if {@code expectXMLIn} is not set to {@code true}. 
 *                          Default is {@code null}.</li>
 *      <li>{@code expectCsvIn}: Optional. Specifies that the expected UDP data will be in CSV format. Expects that the
 *                          data will use a header specifying the name of each object. Default is {@code false}.</li>
 * </ul>
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
            log.warn("Vantiq requesting message sent from" +  message.getSourceName()
                    + ", but no handler is set up for it");
        }
    };

    /**
     *  Shuts down the server when a query is received. This is largely a debug decision, as a) queries are not expected
     *  for UDP sources, and b) problems occur when the WebSocket connection is violently shut down
     */
    static Handler<ExtensionServiceMessage> UDPDefaultQuery = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage msg) {
            String srcName = msg.getSourceName();
            // Prepare a response with an empty body, so that the query doesn't wait for a timeout
            clients.get(srcName).sendQueryResponse(200,
                    ExtensionServiceMessage.extractReplyAddress(msg),
                    new LinkedHashMap<>());

            // Allow the system to stop
            stopLatch.countDown();
        }
    };

    /**
     * Creates publish and notification handlers for any messages relating to a source based on the configuration
     * document
     */
    static Handler<ExtensionServiceMessage> UDPConfig = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            String sourceName = message.getSourceName();
            Map srcConfig = (Map) ((Map)message.getObject()).get("config");
            if (!(srcConfig.get("extSrcConfig") instanceof Map)) {
                log.error("Unable to obtain server configuration for '" + sourceName + "'. Source '" +
                        sourceName  + "' is terminating.");
                clients.get(sourceName).close();
                return;
            }
            Map config = (Map) srcConfig.get("extSrcConfig");
            log.trace("Creating handlers for '" + sourceName + "'");

            // Acquire the general settings Map and the listening port and address
            Map general = null;
            if (config.get("general") instanceof Map) {
                general = (Map) config.get("general");
            }
            int port = getListeningPort(general, sourceName);
            InetAddress address = getListeningAddress(general, sourceName);
            
            Map incoming = null;
            Map outgoing = null;
            if (config.get("incoming") instanceof Map) {
                incoming = (Map) config.get("incoming");
            }
            if (config.get("outgoing") instanceof Map) {
                outgoing = (Map) config.get("outgoing");
            }

            // Synchronization necessary because createUDPSocket creates a list which findUDPSocket will add to.
            // If threads change between the creation of the UDP socket and the assignation of the list or while
            // a source is being added to the list, an error is likely to occur
            DatagramSocket socket;
            synchronized (this) {
                // Create the socket that the source will use for UDP messages
                socket = createUDPSocket(port, address, sourceName);
                if (socket == null) { // No socket could be created for the given port and address
                    socket = findUDPSocket(port, address, sourceName);
                }
                if (socket == null) {
                    log.error("Failed to obtain UDP socket at address '" + address + "' and port '" + port +
                            "' for source '" + sourceName + "'");
                    clients.get(sourceName).close();
                    return;
                }
            }

            // Setup Publish handler as the configuration document requests
            if (isConfiguredToSend(outgoing)) {
                UDPPublishHandler handler = new UDPPublishHandler(outgoing, socket);
                clients.get(sourceName).setPublishHandler(handler);
                log.debug("Publish handler created for source '" + sourceName + "'");
            }

            // Setup Notification handler as the configuration document requests
            if (isConfiguredToReceive(incoming)) {
                UDPNotificationHandler handler = new UDPNotificationHandler(incoming, clients.get(sourceName));
                setNotificationHandler(handler, sourceName, incoming); // This function is part of ConfigurableUDPSource
                log.debug("Notification handler created for source '" + sourceName + "'");
            }
            log.info("Source '" + sourceName + "' setup and ready to go.");
        }

        private int getListeningPort(Map general,String sourceName) {
            Object port = null;
            if (general != null) {
                port = general.get("listenPort");
            }
            if (port instanceof Integer && (int) port >= 0 && (int) port <= 65535) {
                return (int) port;
            }
            else {
                log.debug("No valid listening port specified for source '" + sourceName + "'. Using default port '"
                        + LISTENING_PORT + "'.");
                return LISTENING_PORT;
            }
        }

        private InetAddress getListeningAddress(Map general, String sourceName) {
            Object address = null;
            if (general != null) {
                address = general.get("listenAddress");
            }

            if (address instanceof String) {
                try {
                    return InetAddress.getByName((String) address);
                }
                catch (UnknownHostException e) {
                    log.warn("Requested listening address '" + address + "' specified for source '" + sourceName + "'" +
                            "could not be found. Using default address '" + LISTENING_ADDRESS + "'.");
                    return LISTENING_ADDRESS;
                }
            }
            else {
                log.debug("No listening address specified for source '" + sourceName + "'. Using default address '" +
                        LISTENING_ADDRESS + "'.");
                return LISTENING_ADDRESS;
            }
        }

        boolean isConfiguredToSend(Map outgoing) {
            return outgoing != null && outgoing.get("targetAddress") instanceof String &&
                    outgoing.get("targetPort") instanceof Integer;
        }

        boolean isConfiguredToReceive(Map incoming) {
            return incoming != null &&
                    (incoming.get("receivePorts") instanceof List || incoming.get("receiveAddresses") instanceof List ||
                    valueIsTrue(incoming, "receiveAllPorts") || valueIsTrue(incoming, "receiveAllAddresses") ||
                    incoming.get("receiveServers") instanceof List ||
                    valueIsTrue(incoming, "passPureMapIn") || valueIsTrue(incoming, "passUnspecifiedIn") ||
                    incoming.get("passRecAddress") instanceof String || incoming.get("passRecPort") instanceof String
                    || incoming.get("transformations") instanceof List);
        }
    };
    
    static Handler<ExtensionServiceMessage> UDPReconnectHandler = new Handler<ExtensionServiceMessage>() {

        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            String sourceName = (String) message.getSourceName();
            ExtensionWebSocketClient client = clients.get(sourceName);

            synchronized (socketLock) { // Don't want to edit while another is adding to or removing from UDPSocket
                // Disassociate the source from the socket
                for (Map.Entry<DatagramSocket,List<String>> entry : udpSocketToSources.entrySet()) {
                    List<String> list = entry.getValue();
                    if (list.contains(client)) {
                        list.remove(client);
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
            
            client.connectToSource();
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
            log.debug("Source '" + sourceName + "' succeeded in creating UDP socket on " +
                    "port '" + port + "' and address '" + address + "'");
            return socket;
        }
        catch (Exception e) {
            log.debug("Source '" + sourceName + "' failed to create UDP socket on port '" + port +
                    "' and address '" + address + "'. Will try to share with other sources.");
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
    public static DatagramSocket findUDPSocket(int port, InetAddress address, String sourceName) {
        for (Map.Entry<DatagramSocket,List<String>> entry : udpSocketToSources.entrySet()) {
            DatagramSocket sock = entry.getKey();
            if (sock.getLocalAddress().equals(address) && sock.getLocalPort() == port) {
                List<String> sources = entry.getValue();
                log.debug("Source '" + sourceName + "' is sharing its UDP socket with sources " + sources);
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
     * @param general        The 'general' section source's Configuration document
     */
    public static void setNotificationHandler(UDPNotificationHandler handler, String sourceName, Map general) {
        log.trace("Setting Notification handler for '" + sourceName + "'");
        notificationHandlers.put(sourceName, handler);

        List<InetAddress> addresses = null;
        List<Integer> ports = null;
        List<List> servers = null;

        // Obtain all valid addresses, ports, and servers from the respective arrays specified in the configuration document
        if (general != null) {
            if (general.get("receiveAddresses") instanceof List) {
                addresses = getValidInetAddresses((List) general.get("receiveAddresses"), sourceName);
            }
            if (general.get("receivePorts") instanceof List) {
                ports = getValidPorts((List) general.get("receivePorts"));
            }
            if (general.get("receiveServers") instanceof List) {
                servers = getValidServers((List) general.get("receiveServers"));
            }
        }

        // Use the addresses specified
        if (addresses != null) {
            sourceAddresses.put(sourceName, addresses);
            log.debug("Source '" + sourceName + "' listening for addresses " + addresses);
        }
        // We should ignore all addresses only if there are requested servers and no valid settings for addresses or ports
        else if (servers != null && !valueIsTrue(general, "receiveAllAddresses")
                && !valueIsTrue(general, "receiveAllPorts") && ports == null) {
            sourceAddresses.put(sourceName, NO_ADDR);
        }
        // Either receiveAllAddresses is set to true with no addresses specified
        // or the settings allow defaulting to all addresses
        else {
            sourceAddresses.put(sourceName, ALL_ADDR);
            log.debug("Source '" + sourceName + "' listening for all addresses");
        }

        // Use the ports if specified
        if (ports != null) {
            sourcePorts.put(sourceName, ports);
            log.debug("Source '" + sourceName + "' listening on ports " + ports);
        }
        // We should ignore all ports only if there are requested servers and no valid settings for addresses or ports
        else if (servers != null && !valueIsTrue(general, "receiveAllAddresses")
                && !valueIsTrue(general, "receiveAllPorts") && addresses == null) {
            sourcePorts.put(sourceName, NO_PORTS);
        }
        // Either receiveAllPorts is set to true with no ports specified or the settings allow defaulting to all ports
        else {
            sourcePorts.put(sourceName, ALL_PORTS);
            log.debug("Source '" + sourceName + "' listening on all ports");
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
    private static List<InetAddress> getValidInetAddresses(List potentialAddresses, String sourceName) {
        List<InetAddress> addresses = new ArrayList<>();
        for (Object name : potentialAddresses) {
            if (name instanceof String) {
                try {
                    InetAddress a = InetAddress.getByName((String) name);
                    addresses.add(a);
                }
                catch (UnknownHostException e) {
                    log.warn("Requested receiving address '" + name + "' specified for source '" + sourceName +
                            "'" + "could not be found. This address will be ignored");
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
    private static List<Integer> getValidPorts(List potentialPorts) {
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
    private static List<List> getValidServers(List potentialServers) {
        if (!(potentialServers != null && !potentialServers.isEmpty())) {
            return null;
        }

        List<List> servers = new ArrayList<>();
        for (Object server : potentialServers) {
            if (isValidServer(server)) {
                List<Object> s = (List) server;
                String addressName = (String) ((List) server).get(0);
                try {
                    s.set(0, InetAddress.getByName(addressName));
                }
                catch (UnknownHostException e) {
                    log.warn("Requested receiving address '" + addressName + "' specified for server '" + server +
                            "'" + "could not be found. This address will be ignored");
                }
            }
        }
        return !servers.isEmpty() ? servers : null;
    }

    /**
     * Checks to see if the given {@code Object} is a valid description of a server
     *
     * @param server    The {@code Object} to check for validity
     * @return          {@code true} if {@code server} is a {@link List} of size 2 whose first entry is a {@link String} and
     *                  whose second entry is an integer between 0 and 65535, inclusive
     */
    private static boolean isValidServer(Object server) {
        return server instanceof List && ((List) server).size() == 2 && ((List) server).get(0) instanceof String &&
                ((List) server).get(1) instanceof Integer && (int) ((List) server).get(1) >= 0 &&
                (int) ((List) server).get(1) <= 65535;
    }

    /**
     * A {@link CountDownLatch} used to keep the program from ending until we want it to. Currently, allows the program
     * to gracefully end when a query is received on any connected source.
     */
    private static CountDownLatch stopLatch = new CountDownLatch(1);
    /**
     * A set of {@link UDPNotificationHandler} keyed to the name of the source it is connected to
     */
    private static Map<String, UDPNotificationHandler> notificationHandlers = new ConcurrentHashMap<>();
    /**
     * A map keyed by source containing either a {@link List} of {@link InetAddress} to accept transmissions from,
     * the value {@link #ALL_ADDR}, or the value {@link #NO_ADDR}.
     */
    private static Map<String, Object> sourceAddresses = new LinkedHashMap<>();
    /**
     * A map keyed by source containing either a {@link List} of ports to accept transmissions from, the value
     * {@link #ALL_PORTS}, or the value {@link #NO_PORTS}.
     */
    private static Map<String, Object> sourcePorts = new LinkedHashMap<>();
    /**
     * A map keyed by source containing either a {@link List} of servers to accept transmissions from or null if
     * no servers were specified in the configuration document.
     */
    private static Map<String, List<List>> sourceServers = new LinkedHashMap<>();
    /**
     * A {@link Map} keyed by source that contains the socket the source is using
     */
    private static Map<DatagramSocket, List<String>> udpSocketToSources = new LinkedHashMap<>();
    /**
     * An Object used to ensure only one source edits udpSocketToSources at once
     */
    private static Object socketLock = new Object();
    /**
     * The {@link ExtensionWebSocketClient} that communicates with Vantiq
     */
    private static Map<String, ExtensionWebSocketClient> clients = new LinkedHashMap<>();
    /**
     * The default address for a UDP server to listen on.
     */
    private static InetAddress LISTENING_ADDRESS = null;
    /**
     * The default port for the UDP server to listen on
     */
    private static int LISTENING_PORT = 3141;
    /**
     * Max byte size receivable through UDP servers
     */
    private static int MAX_UDP_DATA = 1024;
    /**
     * An Slf4j logger
     */
    private static final Logger log = LoggerFactory.getLogger(ConfigurableUDPSource.class);
    /**
     * A constant used to signify that a source is listening to all addresses. Used as a key in {@link #sourceAddresses}
     */
    private static final String ALL_ADDR = "_ALL";
    /**
     * A constant used to signify that a source is listening to all ports. Used as a key in {@link #sourceAddresses}
     */
    private static final int ALL_PORTS = -1;
    /**
     * A constant used to signify that a source is listening to all addresses. Used as a key in {@link #sourceAddresses}
     */
    private static final String NO_ADDR = "_NONE";
    /**
     * A constant used to signify that a source is listening to all ports. Used as a key in {@link #sourceAddresses}
     */
    private static final int NO_PORTS = -2;
    /**
     * The desired level of log statements
     */
    private static String targetVantiqServer = null;
    /**
     * The authentication token used to connect to Vantiq
     */
    private static String authToken = null;

    /**
     * Sets the log level and logfile of the UDP source and the Extension Source SDK.
     * 
     * @param logLevel  An instance of {@link Level} at which the log will be reporting
     * @param logTarget The location of the output file. {@code null} will not create a file
     */
    private static void setupLogger(Level logLevel, String logTarget) {
        
        
        // Sets logging level for the example. You may wish to use a different logger or setup method
        String loggerName = ConfigurableUDPSource.class.getPackage().getName();
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Set logging level for both the UDP source and the Extension Source SDK
        loggerContext.getLogger(loggerName).setLevel(logLevel);
        loggerContext.getLogger("io.vantiq.extjsdk").setLevel(logLevel);
        
        if (logTarget != null && !logTarget.equalsIgnoreCase("STDOUT")) {
            // setup pattern for the file logger. This is the same as the basic pattern in logback.xml
            PatternLayoutEncoder e = new PatternLayoutEncoder();
            e.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            e.setContext(loggerContext);
            e.start();
            
            // Setup the Appender that will write to the target file
            FileAppender<ILoggingEvent> a = new FileAppender<>();
            a.setFile(logTarget);
            a.setName("FILE");
            a.setContext(loggerContext);
            a.setEncoder(e);
            a.start();
            
            // Add the Appender to the UDP source and the Extension Source SDK
            loggerContext.getLogger(loggerName).addAppender(a);
            loggerContext.getLogger("io.vantiq.extjsdk").addAppender(a);
        }
    }

    /**
     * Turn the given JSON file into a {@link Map}. 
     * 
     * @param fileName  The name of the JSON file holding the server configuration.
     * @return          A {@link Map} that holds the contents of the JSON file.
     */
    private static Map<String, Object> obtainServerConfig(String fileName) {
        File configFile = new File(fileName);
        log.debug(configFile.getAbsolutePath());
        Map<String, Object>  config = new LinkedHashMap();
        ObjectMapper mapper = new ObjectMapper();
        try {
            config = mapper.readValue(configFile, Map.class);
        } catch (Exception e) {
            log.error("Could not find valid server config file. Expected location: '" + configFile.getAbsolutePath() + "'", e);
        }

        return config;

    }

    /**
     * Sets up the defaults for the server based on the configuration file
     *
     * @param config    The {@link Map} obtained from the config file
     */
    private static void setupServer(Map config) {
        targetVantiqServer = config.get("targetServer") instanceof String ? (String) config.get("targetServer") :
                "wss://dev.vantiq.com/api/v1/wsock/websocket";
        MAX_UDP_DATA = config.get("maxPacketSize") instanceof Integer ? (int) config.get("maxPacketSize") : 1024;
        LISTENING_PORT = config.get("defaultListenPort") instanceof Integer ? (int) config.get("defaultListenPort") :
                3141;
        authToken = config.get("authToken") instanceof String ? (String) config.get("authToken") : "";

        if (config.get("logLevel") instanceof String || config.get("logTarget") instanceof String) {
            Level logLevel = Level.toLevel((String) config.get("logLevel"), Level.WARN);
            String logTarget = (String) config.get("logTarget");
            setupLogger(logLevel, logTarget);
        }

        if (config.get("defaultListenAddress") instanceof String) {
            try {
                String address = (String) config.get("defaultListenAddress");
                LISTENING_ADDRESS = InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                log.warn("Given default listening address could not be found. Using 'localhost' instead");
            }
        }
        // There was no valid defaultListenAddress use the default of localhost
        if (LISTENING_ADDRESS == null) {
            try {
                LISTENING_ADDRESS = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                log.error("Failed to identify localhost", e);
            }
        }
    }

    /**
     * The main function of the app. Here it: <br>
     * 1) Sets up the WebSocket connection through {@link ExtensionWebSocketClient} and sets up the UDP socket<br>
     * 2) Stalls until the WebSocket connection completes then authenticates, exiting on a failure<br>
     * 3) Stalls until it connects as a source. Once connected, any information received from publish messages
     *      is automatically dealt with through handlers in {@link ExtensionWebSocketListener}<br>
     * 4) Reads data through the UDP connection, passing it through {@link #sendFromDatagram} to notify Vantiq as a source.<br>
     *    The current maximum size for the received data is {@link #MAX_UDP_DATA}, and is set in {@link #MAX_UDP_DATA}<br>
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
            log.error("There must be an array of sources");
            log.error("Exiting...\n");
            return;
        }
        ((List<Object>) config.get("sources")).removeIf((obj) -> !(obj instanceof String));
        List<String> sources = ((List<String>) config.get("sources"));


        /*
         * 1) Sets up the WebSocket connection through ExtensionWebSocketClient and sets up the UDP socket.
         *
         * It opens the UDP socket on the localhost at port 3141 and opens ExtensionWebSocketClient at the target address
         * Then, it sets the default {@link Handler} defined earlier for several situations
         */
        // A CompletableFuture that will return false unless every source connects fully
        CompletableFuture<Boolean> connectionWaiter = CompletableFuture.completedFuture(true);
        for (String sourceName : sources) {
            // Create an ExtensionWebSocketClient for the source
            ExtensionWebSocketClient client = new ExtensionWebSocketClient(sourceName);

            // Set the handlers for the client
            client.setPublishHandler(UDPDefaultPublish);
            client.setQueryHandler(UDPDefaultQuery);
            client.setConfigHandler(UDPConfig);
            client.setReconnectHandler(UDPReconnectHandler);

            // Initiate the WebSocket connection, authentication, and source connection for the source
            CompletableFuture<Boolean> future;
            client.initiateWebsocketConnection(targetVantiqServer);
            client.authenticate(authToken);
            future = client.connectToSource();

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
                    log.error("Failed to connect to '" + targetVantiqServer + "' for source '" + sourceName + "'");
                }
                else if (!clients.get(sourceName).isAuthed()) {
                    log.error("Failed to auth within 10 seconds using the given auth data for source '" + sourceName + "'");
                }
                else if (!clients.get(sourceName).isConnected()) {
                    log.error("Failed to connect to '" + sourceName + "' within 5 seconds");
                }
                clients.get(sourceName).close();
            }
            log.error("All clients closed. Done exiting.\n");
            return;
        }

        /*
         * 4) Stall until told to shutdown
         *
         * Waits until {@link #stopLatch} counts down, which occurs when a query is sent to any source controlled by the program.
         * See {@link UDPDefaultQuery} for the code that orders the latch released
         */
        try {
            stopLatch.await();
        }
        catch (InterruptedException e) {
            log.error("Stop latch interrupted while waiting for exit signal ", e);
        }

        /*
         * 5) Exits upon receiving a signal
         *
         * The signal is received from a publish message using {@link ConfigurableUDPSource#UDPDefaultPublish} {@link Handler}. Once it exits,
         * it simply closes the {@link DatagramSocket} and {@link ExtensionWebSocketClient}
         */
        for (String sourceName : sources) {
            if (clients.get(sourceName).isOpen()) {
                clients.get(sourceName).close();
            }
        }
        for (Map.Entry<DatagramSocket,List<String>> entry : udpSocketToSources.entrySet()) {
            entry.getKey().close();
        }
        log.info("WebSocket closed");
    }

    /**
     * An internal class intended to act as an asynchronous listener on a {@link DatagramSocket}.
     * Sends results to {@link ConfigurableUDPSource#sendFromDatagram}
     */
    private static class UDPListener implements Runnable {
        /**
         * The {@link DatagramSocket} to which this listener will receive data from.
         */
        private DatagramSocket socket;

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
                if (!e.getMessage().equals("socket closed")) { // "socket closed" is expected on shutdown, we can ignore
                    log.error("Error occurred on listening to UDP. No longer listening for sources " +
                            udpSocketToSources.get(socket), e);
                }
            }
            catch (Exception e) {
                log.warn("Error occurred on notification attempt for " + udpSocketToSources.get(socket), e);
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
        log.trace (new String(packet.getData()));
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        log.debug("UDP message received from address '" + address + "' and port '" + port + "' for sources " + sources);

        for (String sourceName : sources) {
            // Notify
            if (receivingFromServer(sourceName, port, address)) {
                log.debug("Sending Notification for source '" + sourceName + "'");
                log.debug(notificationHandlers.toString());
                log.debug(notificationHandlers.get(sourceName).toString());
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
    private static boolean receivingFromServer(String sourceName, int port, InetAddress address) {
        return (matchesPort(sourceName, port) && matchesAddress(sourceName, address)) ||
                matchesServer(sourceName, port, address);
    }

    /**
     * Checks if the source is configured to receive from the given port
     *
     * @param sourceName    Name of the source
     * @param port          The port number to check
     * @return              {@code true} if the source's config document said to receive from the port, {@code false} otherwise
     */
    private static boolean matchesPort(String sourceName, int port) {
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
     * @return              {@code true} if the source's config document said to receive from the address, {@code false} otherwise
     */
    private static boolean matchesAddress(String sourceName, InetAddress address) {
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
    private static boolean matchesServer(String sourceName, int port, InetAddress address) {
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
     * @return      {@code true} if {@code map.get(key)} is either a boolean and {@code true}, or not a boolean and non-null,
     *              {@code false} otherwise
     */
    private static boolean valueIsTrue(Map map, Object key) {
        Object val = map.get(key);
        if (val instanceof Boolean) {
            return (boolean) val;
        }
        else {
            return val != null;
        }
    }
}