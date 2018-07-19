package io.vantiq.extsrc.udp;



// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import io.vantiq.extjsdk.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a customizable handler that will convert Publish messages from a Vantiq deployment to a UDP message.
 * The target server and transformations are defined by a Configuration document passed into the constructor
 *
 * Options for Publishes (a.k.a. outgoing messages) are below. Note that if neither {@code passOut} option is set and
 *    {@code transformations} is not set or empty, then an empty Json message is sent to the target.
 * <ul>
 *      <li>{@code targetAddress}: Required to send Publishes. A {@link String} containing either the URL or IP address
 *                          to which outgoing UDP messages should be sent</li>
 *      <li>{@code targetPort}: Required to send Publishes. An integer that is the port # to which outgoing UDP messages
 *                          should be sent</li>
 *      <li>{@code passPureMapOut}: Optional. A boolean specifying that the Json object in Publish messages should be
 *                          passed through without changes. Default is {@code false}.</li>
 *      <li>{@code passUnspecifiedOut}: Optional. A boolean specifying that any values not transformed through the
 *                          transformations specified in {@code transformations} should be sent as part of the outgoing
 *                          message. If no transformations are specified in {@code transformations} then this is
 *                          identical to {@code passPureMapOut}. Overridden by {@code passPureMapOut}. Default is
 *                          {@code false}</li>
 *      <li>{@code transformations}: Optional. An array of transformations (see {@link MapTransformer}) to perform on
 *                          the message to be sent. Any values not transformed will not be passed unless
 *                          {@code passUnspecifiedOut} is set to {@code true}, and any values that are transformed will
 *                          not appear in their original location regardless of settings</li>
 *      <li>{@code passBytesOutFrom}: Optional. The location from which you would like to place the outgoing data. 
 *                          This will take in the String at the location and send it using the byte values of the 
 *                          characters contained within. This will not add quotation marks around the output. Default
 *                          is {@code null}.</li>
 *      <li>{@code sendXMLRoot}: Optional. The name of the root element for the generated XML object. When set this will
 *                          send the entire object received as XML. Default is {@code null}.
 * </ul>
 */
public class UDPPublishHandler extends Handler<Map>{
    /**
     * The {@link MapTransformer} used to perform transformations based on the {@code transformations} portion of the
     * configuration document.
     */
    private MapTransformer transformer = null;
    /**
     * The {@code outgoing} section of the source configuration document.
     */
    private Map outgoing;
    /**
     * The address to which messages should be sent.
     */
    private InetAddress address;
    /**
     * The port to which messages should be sent.
     */
    private int port;
    /**
     * The UDP socket used to send messages.
     */
    private DatagramSocket socket;
    /**
     * The {@link ObjectMapper} used to turn a message into bytes before sending it with {@link #socket}.
     */
    private ObjectWriter writer = new ObjectMapper().writer();

    /**
     * An Slf4j logger.
     */
    final private Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Sets up the handler based on the configuration document passed
     *
     * @param outgoing  The {@code outgoing} portion of the configuration document obtained from a Configuration message
     * @param socket    The {@link DatagramSocket} through which data will be sent
     */
    public UDPPublishHandler(Map outgoing, DatagramSocket socket) {
        this.outgoing = outgoing;
        this.socket = socket;
        try {
            this.address = InetAddress.getByName((String) outgoing.get("targetAddress"));
        }
        catch (Exception e) {
            log.error("Failed to retrieve IP of Address", e);
            return;
        }
        this.port = (Integer) outgoing.get("targetPort");

        List<List> transforms = null;
        if (hasOutgoingTransformations(outgoing)) {
            transforms = MapTransformer.getValidTransforms((List) outgoing.get("transformations"));
        }
        if (outgoing.get("sendXMLRoot") instanceof String) {
            writer = new XmlMapper().writer().withRootName((String)outgoing.get("sendXMLRoot"));
        }

        if (transforms != null && !isPassingPure(outgoing)) {
            transformer = new MapTransformer(transforms);
        }
    }

    /**
     * Checks to see if there are transformations specified in the configuration document. See {@link MapTransformer} to
     * see what a transformation should look like.
     *
     * @param outgoing  The {@code outgoing} portion in the configuration document from the serve
     * @return          {@code true} if the key {@code transformations} is a non-empty List, {@code false} otherwise
     */
    private static boolean hasOutgoingTransformations(Map outgoing) {
        return  (outgoing.get("transformations") instanceof List && ((List)outgoing.get("transformations")).size() > 0);
    }

    /**
     * Checks to see if the config says to pass the data on without changing it
     *
     * @param outgoing  The {@code incoming} object of the configuration document from the server
     * @return          {@code true} if outgoing.get("passPureMapOut") is a boolean and {@code true}, {@code false} otherwise
     */
    private static boolean isPassingPure(Map outgoing) {
        return (outgoing.get("passPureMapOut") instanceof Boolean && (boolean) outgoing.get("passPureMapOut"));
    }

    /**
     * Takes in the data from the {@link ExtensionServiceMessage}, transforms it as requested by the configuration document
     * specified in the constructor, then sends it to the UDP server specified in the configuration document
     *
     * @param message   The Publish message received from this {@link Handler}'s source
     */
    @Override
    public void handleMessage(Map message) {
        Map receivedMsg = (Map) message.get("object");
        Map sendMsg = new LinkedHashMap<String,Object>();
        byte[] sendBytes = null;

        // Translate the message as requested in the Configuration document
        if (outgoing.get("passBytesOutFrom") instanceof String && receivedMsg.get(outgoing.get("passBytesOutFrom")) instanceof String) {
            sendBytes = ((String)receivedMsg.get((String)outgoing.get("passBytesOutFrom"))).getBytes();
        }
        else if (outgoing.get("passPureMapOut") instanceof Boolean && (boolean) outgoing.get("passPureMapOut")) {
            sendMsg = receivedMsg;
        }
        else if (outgoing.get("passUnspecifiedOut") instanceof Boolean && (boolean) outgoing.get("passUnspecifiedOut")) {
            sendMsg = receivedMsg;
            if (this.transformer != null) {
                this.transformer.transform(receivedMsg, sendMsg, true);
            }
        }
        else if (transformer == null) {
            // This means that no transform was specified and neither were any pass___ parameters in which case
            // the design decision is to send an empty Map, so the UDP server is notified that a Publish was sent
            sendMsg = new LinkedHashMap<String,Object>();
        }
        else {
            this.transformer.transform(receivedMsg, sendMsg, false);
        }

        log.debug("Sending message to address " + address.getHostAddress() + " and port " + port
                + " with contents: " + sendMsg);
        // Turn the message into bytes for a UDP message then send it
        try {
            if (sendBytes == null) {
                sendBytes = writer.writeValueAsBytes(sendMsg);
            }
            DatagramPacket packet = new DatagramPacket(sendBytes, sendBytes.length, address, port);
            socket.send(packet);
        }
        catch (Exception e) {
            log.warn("Failed trying to translate and send the message.", e);
        }
    }
}
