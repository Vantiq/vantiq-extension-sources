package io.vantiq.extsrc.udp;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import io.vantiq.extjsdk.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO change to match README
/**
 * This class is a customizable handler that will convert UDP messages to a Notification message to a Vantiq deployment.
 * The target server and transformations are defined by a Configuration document passed into the constructor
 *
 *
 * Options for Notifications (a.k.a. incoming messages) are as follows. If no options are valid then no Notifications
 * will be sent, but if even one is set then their defaults are used. Options for different data types are mutually
 * exclusive.
 * 
 * <dl>
 * <dt><span class="strong">UDP Options</span></dt>
 * <dd>
 * These select which ports and addresses to accept data from. If none of these are set but other incoming options are,
 * then messages will be accepted from any port and address. Messages can be received from any address and port
 * combination that exists in receiveAddresses and receivePorts, as well as any server specified in receiveServers.
 *  <ul>
 *      <li>receiveAddresses: Optional. An array of strings containing either the URL or IP addresses from which the
 *                      source will receive messages. If left empty, it is ignored and the default of
 *                      receiveAllAddresses is used.
 *      <li>receiveAllAddresses: Optional. A boolean stating whether you would like to receive Notifications of UDP
 *                      messages from any address. Overridden by a non-empty receivingAddress. Default is true, unless
 *                      receiveServers is the only receive option set, i.e receiveServers is non-empty, receivePorts
 *                      is empty, and receiveAllPorts is not explicitly set to true.
 *      <li>receivePorts: Optional. An array of the port numbers from which the source will receive messages. If left
 *                      empty, it is ignored and the default of receiveAllPorts is used.
 *      <li>receiveAllPorts: Optional. A boolean stating whether you would like to receive Notifications of UDP
 *                      messages from any port. Overridden by a non-empty receivingPorts. Default is true, unless
 *                      receiveServers is the only receive option set, i.e receiveServers is non-empty, receiveAddresses
 *                      is empty, and receiveAllAddresses is not explicitly set to true.
 *      <li>receiveServers: Optional. An array of pairs that specify both an address and port to receive UDP messages
 *                      from. A pair is formatted as an array containing first the address as either the URL or IP
 *                      address, and second the port number. If left empty, defers to the other address and port
 *                      settings.
 * </ul></dd>
 * 
 * <dt><span class="strong">General Object Options</span></dt>
 * <dd>
 * These options affect data received in JSON(default) or XML. If none of these are set then the resulting object will
 * be empty of data. Note that passRecAddress and passRecPort occur for every datatype except CSV, and will overwrite
 * any data already in those locations.
 * <ul>
 *      <li>passPureMapIn: Optional. A boolean specifying that the Json object in Publish messages should be passed
 *                      through without changes. Default is false.
 *      <li>passUnspecifiedIn: Optional. A boolean specifying that any values not transformed through the
 *                      transformations specified in transformations should be sent as part of the outgoing message.
 *                      If no transformations are specified in transformations then this is identical to passPureMapOut.
 *                      Default is false.
 *      <li>passRecAddress: Optional. A string representation of the location in which you want the IP address from
 *                      which the UDP message originated. Default is null, where the address will not be recorded
 *      <li>passRecPort: Optional. A string representation of the location in which you want the port from which the
 *                      UDP message originated. Default is null, where the port will not be recorded.
 *      <li>transformations: Optional. An array of transformations (see [MapTransformer](#mapTransformer)) to perform
 *                      on the message to be sent. Any values not transformed will not be passed unless
 *                      passUnspecifiedIn is set to true, and any values that are transformed will not appear in the
 *                      final message regardless of settings.
 * </ul></dd>
 * 
 * <dt><span class="strong">XML Options</span></dt>
 * <dd>
 * These options specify how XML is translated.
 * <ul>
 *      <li>expectXmlIn: Optional. Specifies that the data incoming from the UDP source will be in an XML format. Note that this will throw away the name of the root element. If data is contained in the root element, it will be placed in the location "" before transformations. Default is false.
 *      <li>passXmlRootNameIn: Optional. Specifies the location to which the name of the root element should be placed. Does nothing if expectXmlIn is not set to true. Default is null.
 * </ul></dd>
 * 
 * <dt><span class="strong">CSV Options</span></dt>
 * <dd>
 * This option specifies how CSV is translated.
 * <ul>
 *      <li>expectCsvIn: Optional. Specifies that the expected UDP data will be in CSV format. Expects that the data will use a header specifying the name of each object. Data will be received as an array of JSON Objects. Default is false.
 * </ul></dd>
 * 
 * <dt><span class="strong">Byte/String Options</span></dt>
 * <dd>
 * These options interpret data as pure bytes or a string in byte form. These two options are mutually exclusive.
 * <ul>
 *      <li>passBytesInAs: Optional. The location to which you would like to place the incoming data. This will take in the raw bytes received from the source and place them as chars of the same value in a String. Default is null.
 *      <li>regexParser: Optional. The settings to use for parsing the incoming byte data using regex. It contains the following options.
 *      <ul>
 *          <li>pattern: Required. The regex pattern that will be used to parse the incoming data. The parser will use the first match that appears in the data. See java.util.regex.Pattern for specifics on what constitutes a valid pattern.
 *          <li>locations: Required. An array of the locations in which to place the capture groups from pattern.
 *      </ul>
 * </ul></dd>
 * </dl>
 */
public class UDPNotificationHandler extends Handler<DatagramPacket>{
    /**
     * The {@link MapTransformer} used to perform transformations based on the 'incoming' portion of the
     * configuration document.
     */
    private MapTransformer transformer = null;
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
    /**
     * An {@link ObjectMapper} used to translate received objects into {@link Map}
     */
    private ObjectMapper mapper = new ObjectMapper();
    /**
     * Whether the incoming data will be in XML format
     */
    private boolean expectingXml = false;
    /**
     * Where to place the name of the root object of an XML object
     */
    private String xmlRootLoc = null;
    /**
     * Whether the incoming data will be in CSV format
     */
    private boolean expectingCsv = false;
    /**
     * Where to place the Ascii translation of the incoming bytes.
     */
    private String bytesLocation = null;
    /**
     * Whether to pass the incoming data out unchanged, except for becoming a JSON object if it wasn't already
     */
    private boolean passingPureMap = false;
    /**
     * Whether to pass along unchanged any incoming data that was not specified in {@code transformations}
     */
    private boolean passingUnspecified = false;
    /**
     * The regex pattern to apply to incoming messages
     */
    private Pattern regexPattern = null;
    /**
     * The locations to which each capture group will be placed
     */
    private String[] patternLocations = null;

    /**
     * Sets up the handler based on the configuration document passed.
     *
     * @param incoming      The {@code incoming} portion configuration document obtained from a Configuration message
     * @param client        The {@link ExtensionWebSocketClient} through which data will be sent
     */
    public UDPNotificationHandler(Map incoming, ExtensionWebSocketClient client) {
        this.client = client;
        if (incoming.get("passRecAddress") instanceof String) {
            recAddressKey = (String) incoming.get("passRecAddress");
        }
        if (incoming.get("passRecPort") instanceof String) {
            recPortKey = (String) incoming.get("passRecPort");
        }
        if (incoming.get("expectXmlIn") instanceof Boolean && (boolean) incoming.get("expectXmlIn")) {
            expectingXml = true;
            mapper = new XmlMapper();
        }
        if (incoming.get("passXmlRootNameIn") instanceof String) {
            xmlRootLoc = (String) incoming.get("passXmlRootNameIn");
        }
        if (incoming.get("expectCsvIn") instanceof Boolean && (boolean) incoming.get("expectCsvIn")) {
            expectingCsv = true;
            mapper = new CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY);
        }
        if (incoming.get("passBytesInAs") instanceof String) {
            bytesLocation = (String) incoming.get("passBytesInAs");
        }
        if (incoming.get("passPureMapIn") instanceof Boolean && (boolean) incoming.get("passPureMapIn")) {
            passingPureMap = true;
        }
        if(incoming.get("passUnspecifiedIn") instanceof Boolean && (boolean) incoming.get("passUnspecifiedIn")) {
            passingUnspecified = true;
        }
        if (incoming.get("regexParser") instanceof Map) {
            Map regexParser = (Map) incoming.get("regexParser");
            
            if (regexParser.get("pattern") instanceof String && regexParser.get("locations") instanceof List) {
                try {
                    regexPattern = Pattern.compile( (String) regexParser.get("pattern"));
                    List<String> l = new ArrayList<>();
                    List locations = (List)regexParser.get("locations");
                    for (Object loc :locations) {
                        if (loc instanceof String) {
                            l.add((String)loc);
                        }
                    }
                    patternLocations = l.toArray(new String[0]);
                    
                    if (patternLocations.length != regexPattern.matcher("").groupCount()) {
                        regexPattern = null;
                        patternLocations = null;
                        log.error("The number of capture groups in the regex pattern and the number of locations do "
                                + "not match. Found " + regexPattern.matcher("").groupCount() + "capture groups and "
                                + patternLocations.length + "locations.");
                    }
                }
                catch (Exception e) {
                    log.error("Could not compile regex pattern", e);
                }
            } 
        }

        List<List> transforms = null;
        if (hasIncomingTransformations(incoming)) {
            transforms = MapTransformer.getValidTransforms((List) incoming.get("transformations"));
        }

        // We only need a transformer if the Configuration doc has transforms for us and it does not want us to
        // pass the object along untransformed
        if (transforms != null && !transforms.isEmpty() && !passingPureMap && bytesLocation == null 
                && regexPattern == null && expectingCsv == false) {
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
        if (bytesLocation != null || regexPattern != null) {
            // Can't be parsed with Object mapper 
        }
        else if (expectingCsv) {
            try  {
                List<Object> csv = mapper.readerFor(List.class).with(CsvSchema.emptySchema().withHeader())
                        .readValue(packet.getData());
                client.sendNotification(csv);
            }
            catch (Exception e){
                log.warn("Failed to interpret UDP message as CSV.", e);
            }
            return;
        }
        else{
            try {
                receivedMsg = mapper.readValue(packet.getData(), Map.class);
            } 
            catch (Exception e) {
                if (expectingXml) {
                    log.warn("Failed to interpret UDP message as a XML object.", e);
                }
                else {  // expecting JSON
                    log.warn("Failed to interpret UDP message as a JSON object.", e);
                }
                return;
            }
        }
        

        // Transforms the message as requested by the Configuration document
        if (regexPattern != null) {
            
            // Create a String from the byte data, but remove unused bytes
            String recString = new String(packet.getData());
            int endIndex = recString.indexOf("\0");
            if (endIndex == -1) {endIndex = packet.getData().length;}
            recString = new String(packet.getData(), 0, endIndex, Charset.forName("UTF-8"));
            
            sendMsg = getRegexResults(recString);
        }
        else if (bytesLocation != null) {
            // Create a String from the byte data, but remove unused bytes
            String recString = new String(packet.getData());
            recString = new String(packet.getData(), 0, recString.indexOf("\0"), Charset.forName("UTF-8"));
            
            MapTransformer.createTransformVal(sendMsg, bytesLocation, recString);
        }
        else if (passingPureMap) {
            sendMsg = receivedMsg;
        }
        else if (passingUnspecified) {
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

        if (expectingXml && xmlRootLoc != null) {
            try {
                FromXmlParser p = (FromXmlParser) mapper.getFactory().createParser(packet.getData());
                MapTransformer.createTransformVal(sendMsg, xmlRootLoc, p.getStaxReader().getLocalName());
            }
            catch (Exception e) {
                log.error("Failed to interpret name of root", e);
            }
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

    /**
     * Returns a map created from the results of {@link #regexPattern} being applied to the input string. The capture 
     * groups are placed in the locations specified by {@link #patternLocations}.
     * 
     * @param str   The string to check for matches to the regex.
     * @return      A {@code Map} that contains the capture groups, or null if the pattern could not be found.
     */
    private Map<String,Object> getRegexResults(String str) {
        Map<String,Object> output = new LinkedHashMap<>();
        
        Matcher regexMatcher = regexPattern.matcher(str);
        if (!regexMatcher.find()) {
            log.warn("Message failed to match the requested pattern for source '" + client.getSourceName() + "'");
            log.debug("String '" + str + "' \nfailed to match pattern '" + regexPattern.pattern() + "'");
            return null;
        }
        
        for (int i = 0; i < patternLocations.length; i++) {
            MapTransformer.createTransformVal(output, patternLocations[i], regexMatcher.group(i + 1));
        }
        
        return output;
    }
}
