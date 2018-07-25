package io.vantiq.extsrc.udp;





// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import io.vantiq.extjsdk.Handler;
import io.vantiq.extjsdk.ExtensionServiceMessage;

import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingFormatArgumentException;

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
 *                          send the output as XML instead of JSON. Default is {@code null}.</li>
 *      <li>{@code passCsvOutFrom}: Optional. A string specifying the location of an array of objects that will be 
 *                          converted into CSV format. Requires {@code useCsvSchema} to be set. Default is {@code null}
 *                          </li>
 *      <li>{@code useCsvSchema}: Optional. Defines the values that will be sent as CSV. Can be either A) an array of
 *                          the names of the values that will be taken from the objects and placed in CSV, or B) the 
 *                          location in which the previous will be for *all* Publishes. The values in the array will 
 *                          be placed as the header for the CSV document. Default is {@code null}</li>
 * </ul>
 */
public class UDPPublishHandler extends Handler<ExtensionServiceMessage>{
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
    private boolean passingPureMap = false;
    private boolean passingUnspecified = false;
    private String bytesLocation = null;
    private String formatPattern = null;
    private Formatter formatter = null;
    private String[] formatLocations = null;
    private StringBuilder formattedString = null;
    private String altPatternLocation = null;
    private String altLocations = null;
    private String csvSource = null;
    private CsvSchema csvSchema = null;
    private String csvSchemaLocation = null;
    
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
        if (outgoing.get("passPureMapOut") instanceof Boolean && (boolean) outgoing.get("passPureMapOut")) {
            passingPureMap = true;
        }
        if (outgoing.get("passUnspecifiedOut") instanceof Boolean && (boolean) outgoing.get("passUnspecifiedOut")) {
            passingUnspecified = true;
        }
        if (outgoing.get("passBytesOutFrom") instanceof String) {
            bytesLocation = (String) outgoing.get("passBytesOutFrom");
        }
        if (outgoing.get("formatParser") instanceof Map) {
            Map formatParser = (Map) outgoing.get("formatParser");
            
            if (formatParser.get("pattern") instanceof String && formatParser.get("locations") instanceof List) {
                formatPattern = (String) formatParser.get("pattern");
                formattedString = new StringBuilder();
                formatter = new Formatter(formattedString); 
                formatLocations = retrieveLocationsFrom((List)formatParser.get("locations"));
                
                if (formatParser.get("altPatternLocation") instanceof String) {
                    altPatternLocation = (String) formatParser.get("altPatternLocation");
                }
                if (formatParser.get("altLocations") instanceof String) {
                    altLocations = (String) formatParser.get("altLocations");
                }
            }
        }
        if (outgoing.get("passCsvOutFrom") instanceof String && outgoing.get("useCsvSchema") != null) {
            csvSource = (String) outgoing.get("passCsvOutFrom");
            if (outgoing.get("useCsvSchema") instanceof List) {
                CsvSchema.Builder csvSchemaBuilder = CsvSchema.builder();
                for (Object col : (List) outgoing.get("useCsvSchema")) {
                    if (col instanceof String) {
                        csvSchemaBuilder.addColumn((String) col);
                    }
                }
                csvSchema = csvSchemaBuilder.setUseHeader(true).build();
            }
            else if (outgoing.get("useCsvSchema") instanceof String) {
                csvSchemaLocation = (String) outgoing.get("useCsvSchema");
            }
            else { // We need either a schema or location for a potential schema
                csvSource = null;
            }
        }

        if (transforms != null && !passingPureMap) {
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
     * Takes in the data from the {@link ExtensionServiceMessage}, transforms it as requested by the configuration document
     * specified in the constructor, then sends it to the UDP server specified in the configuration document
     *
     * @param message   The Publish message received from this {@link Handler}'s source
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map receivedMsg = (Map) message.getObject();
        Map sendMsg = new LinkedHashMap<String,Object>();
        byte[] sendBytes = null;

        // Translate the message as requested in the Configuration document
        if (formatLocations != null) {
            sendBytes = getFormattedOutput(receivedMsg);
        }
        else if (csvSource != null) {
            String str = buildCsv(receivedMsg);
            sendBytes = str.getBytes();
        }
        else if (bytesLocation != null) {
            sendBytes = ((String)receivedMsg.get(bytesLocation)).getBytes();
        }
        else if (passingPureMap) {
            sendMsg = receivedMsg;
        }
        else if (passingUnspecified) {
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

        
        // Turn the message into bytes for a UDP message then send it
        try {
            if (sendBytes == null) {
                log.debug("Sending message to address " + address.getHostAddress() + " and port " + port
                        + " with contents: " + sendMsg);
                sendBytes = writer.writeValueAsBytes(sendMsg);
            }
            else {
                log.debug("Sending message to address " + address.getHostAddress() + " and port " + port
                        + " with contents: " + new String(sendBytes));
            }
            DatagramPacket packet = new DatagramPacket(sendBytes, sendBytes.length, address, port);
            socket.send(packet);
        }
        catch (Exception e) {
            log.warn("Failed trying to translate and send the message.", e);
        }
    }
    
    private String buildCsv(Map<String,Object> map) {
        String out = null;
        
        try {
            CsvMapper mapper = new CsvMapper();
            mapper.enable(CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING).enable(Feature.IGNORE_UNKNOWN);
            if (csvSchemaLocation != null && map.get(csvSchemaLocation) instanceof List) {
                CsvSchema.Builder csvSchemaBuilder = CsvSchema.builder();
                for (Object col : (List) outgoing.get(csvSchemaLocation)) {
                    if (col instanceof String) {
                        csvSchemaBuilder.addColumn((String) col);
                    }
                }
                csvSchema = csvSchemaBuilder.setUseHeader(true).build();
            }
            else if (csvSchemaLocation != null) { // A location is set but no valid schema given
                return null;
            }
            out = mapper.writer().with(csvSchema).writeValueAsString(map.get(csvSource));
        }
        catch (Exception e) {
            log.warn("Failed to create CSV message", e);
        }
        
        
        return out;
    }
    
    private byte[] getFormattedOutput(Map receivedData) {
        byte[] resultBytes = null;
        List<Object> args = new ArrayList<>();
        String[] locations = formatLocations;
        if (altLocations != null && receivedData.get(altLocations) instanceof List) {
            locations = retrieveLocationsFrom((List) receivedData.get(altLocations));
        }
        for (String loc: locations) {
            args.add(MapTransformer.getTransformVal(receivedData, loc));
        }
        
        // Synchronized so that messages will not be mixed if they are sent too fast and interrupt each other
        synchronized(this) {
            try {
                String pattern = formatPattern;
                if (altPatternLocation != null && receivedData.get(altPatternLocation) instanceof String) {
                    pattern = (String) receivedData.get(altPatternLocation);
                }
                formatter.format(pattern, args.toArray());
            }
            catch (MissingFormatArgumentException e) {
                log.error("Insufficient arguments for pattern '" + formatPattern + "'");
            }
            resultBytes = formattedString.toString().getBytes();
            log.debug(formattedString.toString());
            formattedString.setLength(0);;
        }
        
        return resultBytes;
    }
    
    private String[] retrieveLocationsFrom(List potentialLocations) {
        List<String> locations = new ArrayList<>();
        for (Object loc :potentialLocations) {
            if (loc instanceof String) {
                locations.add((String)loc);
            }
        }
        return locations.toArray(new String[0]);
    }
}
