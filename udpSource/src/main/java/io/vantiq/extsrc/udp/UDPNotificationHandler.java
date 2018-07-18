package io.vantiq.sourcemgr.sampleExtensions;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.vantiq.sourcemgr.sampleExtensions.MapTransformer.getValidTransforms;

/**
 * This class is a customizable handler that will convert UDP messages to a Notification message to a Vantiq deployment.
 * The target server and transformations are defined by a Configuration document passed into the constructor
 *
 *
 * Options for Notifications (a.k.a. incoming messages) are as follows. If no options are valid then no Notifications
 *    will be sent, but if even one is set then their defaults are used. If none of the {@code passIn} options are set
 *    and {@code transformations} is not set or empty, then an empty Json message is sent to the Vantiq deployment, with
 *    {@code passRecAddress} or {@code passRecPort} added if they are set. Messages can be received from any address and
 *    port combination that exists in {@code receiveAddresses} and {@code receivePorts}, as well as any server specified
 *    in {@code receiveServers}
 * <ul>
 *      <li>{@code receiveAddresses}: Optional. An array of {@link String} containing either the URL or IP addresses
 *                          from which the source will receive messages. If left empty, defers to the other address
 *                          and server settings.</li>
 *      <li>{@code receiveAllAddresses}: Optional. A boolean stating whether you would like to receive Notifications of
 *                          UDP messages from any address. Overridden by a non-empty {@code receivingAddress}. Default
 *                          is {@code true}, unless {@code receiveServers} is the only {@code receive} option set, i.e
 *                          {@code receiveServers} is non-empty, {@code receivePorts} is empty,
 *                          and {@code receiveAllPorts} is not explicitly set to true.</li>
 *      <li>{@code receivePorts}: Optional. An array of the port numbers from which the source will receive messages.
 *                          If left empty, defers to the other port and server settings.</li>
 *      <li>{@code receiveAllPorts}: Optional. A boolean stating whether you would like to receive Notifications of
 *                          UDP messages from any port. Overridden by a non-empty {@code receivingPorts}. Default is
 *                          {@code true}, unless {@code receiveServers} is the only {@code receive} option set, i.e
 *                          {@code receiveServers} is non-empty, {@code receiveAddresses} is empty,
 *                          and {@code receiveAllAddresses} is not explicitly set to true.</li>
 *      <li>{@code receiveServers}: Optional. An array of pairs that specify both an address and port to receive
 *                          UDP messages from. A pair is formatted as an array containing first the address as a
 *                          {@link String} containing either the URL or IP address, and second the port number. If left
 *                          empty, defers to the other address and port settings</li>
 *      <li>{@code passPureMapIn}: Optional. A boolean specifying that the Json object in Notification messages should be
 *                          passed through without changes. Default is {@code false}.</li>
 *      <li>{@code passUnspecifiedIn}: Optional. A boolean specifying that any values not transformed through the
 *                          transformations specified in {@code transformations} should be sent as part of the outgoing
 *                          message. If no transformations are specified in {@code transformations} then this is
 *                          identical to {@code passPureMapIn}. Overridden by {@code passPureMapIn}. Default is
 *                          {@code false}</li>
 *      <li>{@code passRecAddress}: Optional. A {@link String} representation of the location in which you want the
 *                          IP address from which the UDP message originated. Default is {@code null}, where the address
 *                          will not be recorded.</li>
 *      <li>{@code passRecPort}: Optional. A {@link String} representation of the location in which you want the port
 *                          from which the UDP message originated. Default is {@code null}, where the port will not be
 *                          recorded.</li>
 *      <li>{@code transformations}: Optional. An array of transformations (see {@link MapTransformer}) to perform on
 *                          the message to be sent. Any values not transformed will only be passed if
 *                          {@code passUnspecifiedIn} is set to {@code true}, and any values that are transformed will
 *                          not appear in their original location regardless of settings</li>
 * </ul>
 */
public class UDPNotificationHandler extends Handler<DatagramPacket>{
    /**
     * The {@link MapTransformer} used to perform transformations based on the 'incoming' portion of the
     * configuration document.
     */
    private MapTransformer transformer = null;
    /**
     * The {@code incoming} section of the source configuration document.
     */
    private Map incoming;
    /**
     * The {@link ExtensionWebSocketClient} for the source this handler is associated with. Used to send the message
     * to the source.
     */
    private ExtensionWebSocketClient client;
    /**
     * The location in the output message where the address of the message sender should be placed. null if not requested.
     */
    private String recAddressKey = null;
    /**
     * The location in the output message where the port of the message sender should be placed. null if not requested.
     */
    private String recPortKey = null;

    /**
     * A Slf4j logger.
     */
    final private Logger log = LoggerFactory.getLogger(this.getClass());
    final private ObjectMapper mapper = new ObjectMapper();

    /**
     * Sets up the handler based on the configuration document passed.
     *
     * @param incoming      The {@code incoming} portion configuration document obtained from a Configuration message
     * @param client        The {@link ExtensionWebSocketClient} through which data will be sent
     */
    public UDPNotificationHandler(Map incoming, ExtensionWebSocketClient client) {
        this.incoming = incoming;
        this.client = client;
        if (incoming.get("passRecAddress") instanceof String) {
            recAddressKey = (String) incoming.get("passRecAddress");
        }
        if (this.incoming.get("passRecPort") instanceof String) {
            recPortKey = (String) incoming.get("passRecPort");
        }

        List<List> transforms = null;
        if (hasIncomingTransformations(incoming)) {
            transforms = getValidTransforms((List) incoming.get("transformations"));
        }

        // We only need a transformer if the Configuration doc has transforms for us and it does not want us to
        // pass the object along
        if (transforms != null && !transforms.isEmpty() && !isPassingPure(incoming)) {
            transformer = new MapTransformer(transforms);
        }
    }

    /**
     * Checks to see if there are transformations specified in the configuration document.
     *
     * @param incoming  The {@code incoming} object in the configuration document from the serve
     * @return          {@code true} if the {@code transformations} is a non-empty List, {@code false} otherwise
     */
    private static boolean hasIncomingTransformations(Map incoming) {
        return  (incoming.get("transformations") instanceof List && ((List)incoming.get("transformations")).size() > 0);
    }

    /**
     * Checks to see if the configuration document says to pass the data on without changing it.
     *
     * @param incoming  The {@code incoming} object in the configuration document from the server
     * @return          {@code true} if {@code passPureMapIn} is true, {@code false} otherwise
     */
    private static boolean isPassingPure(Map incoming) {
        return (incoming.get("passPureMapIn") instanceof Boolean && (boolean) incoming.get("passPureMapIn"));
    }

    /**
     * Takes in the data from the {@link DatagramPacket}, transforms it as requested by the configuration document
     * specified in the constructor, then sends it to the source using the
     * {@link ExtensionWebSocketClient} specified in the constructor
     *
     * @param packet    The {@link DatagramPacket} containing
     */
    @Override
    public void handleMessage(DatagramPacket packet) {
        Map receivedMsg = null;
        Map<String,Object> sendMsg = new LinkedHashMap<>();
        try {
            receivedMsg = mapper.readValue(packet.getData(), Map.class);
        }
        catch (Exception e) {
        	if (!(incoming.get("passBytesInAs") instanceof String)) {
        		log.warn("Failed to interpret UDP message as Map. Most likely the data was not sent as a Json object.", e);
        		return;
        	}
        }

        // Transforms the message as requested by the Configuration document
        if (incoming.get("passBytesInAs") instanceof String) {
        	sendMsg.put((String) incoming.get("passBytesInAs"), new String(packet.getData()));
        }
        else if (incoming.get("passPureMapIn") instanceof Boolean && (boolean) incoming.get("passPureMapIn")) {
            sendMsg = receivedMsg;
        }
        else if (incoming.get("passUnspecifiedIn") instanceof Boolean && (boolean) incoming.get("passUnspecifiedIn")) {
            // Set the messages to the same map so any untransformed values stay, and tell it to destroy any values removed
            sendMsg = receivedMsg;
            if (this.transformer != null) {
                this.transformer.transform(receivedMsg, sendMsg, true);
            }
        }
        else if (transformer == null) {
            // This means that no transform was specified and neither were any pass___ parameters in which case
            // the design decision is to send an empty Map, so the source is notified that a UDP message was received
            sendMsg = new LinkedHashMap<>();
        }
        else {
            this.transformer.transform(receivedMsg, sendMsg, false);
        }

        // Add the address and port that the packet came from if the config demands it
        if (recAddressKey != null) {
            MapTransformer.createTransformVal(sendMsg, recAddressKey, packet.getAddress().getHostAddress());
        }
        if (recPortKey != null) {
            MapTransformer.createTransformVal(sendMsg, recPortKey, packet.getPort());
        }

        client.sendNotification(sendMsg);
    }
}
