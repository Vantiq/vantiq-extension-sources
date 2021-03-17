/*
 * Copyright (c) 2020 VANTIQ, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */
package io.vantiq.extsrc.llrpConnector;

import io.vantiq.extsrc.llrpConnector.exception.VantiqLLRPException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONException;
import org.json.JSONObject;  // com.google.gson.JsonArray;
import org.json.JSONArray;
import org.json.XML;
import org.llrp.ltk.exceptions.InvalidLLRPMessageException;
import org.llrp.ltk.generated.LLRPMessageFactory;
import org.llrp.ltk.generated.enumerations.AISpecStopTriggerType;
import org.llrp.ltk.generated.enumerations.AccessReportTriggerType;
import org.llrp.ltk.generated.enumerations.AirProtocols;
import org.llrp.ltk.generated.enumerations.ConnectionAttemptStatusType;
import org.llrp.ltk.generated.enumerations.GetReaderCapabilitiesRequestedData;
import org.llrp.ltk.generated.enumerations.GetReaderConfigRequestedData;
import org.llrp.ltk.generated.enumerations.KeepaliveTriggerType;
import org.llrp.ltk.generated.enumerations.NotificationEventType;
import org.llrp.ltk.generated.enumerations.ROReportTriggerType;
import org.llrp.ltk.generated.enumerations.ROSpecStartTriggerType;
import org.llrp.ltk.generated.enumerations.ROSpecState;
import org.llrp.ltk.generated.enumerations.ROSpecStopTriggerType;
import org.llrp.ltk.generated.enumerations.StatusCode;
import org.llrp.ltk.generated.messages.ADD_ROSPEC;
import org.llrp.ltk.generated.messages.ADD_ROSPEC_RESPONSE;
import org.llrp.ltk.generated.messages.CLOSE_CONNECTION;
import org.llrp.ltk.generated.messages.DELETE_ROSPEC;
import org.llrp.ltk.generated.messages.DELETE_ROSPEC_RESPONSE;
import org.llrp.ltk.generated.messages.DISABLE_ROSPEC;
import org.llrp.ltk.generated.messages.DISABLE_ROSPEC_RESPONSE;
import org.llrp.ltk.generated.messages.ENABLE_EVENTS_AND_REPORTS;
import org.llrp.ltk.generated.messages.ENABLE_ROSPEC;
import org.llrp.ltk.generated.messages.ENABLE_ROSPEC_RESPONSE;
import org.llrp.ltk.generated.messages.GET_READER_CAPABILITIES;
import org.llrp.ltk.generated.messages.GET_READER_CAPABILITIES_RESPONSE;
import org.llrp.ltk.generated.messages.GET_READER_CONFIG_RESPONSE;
import org.llrp.ltk.generated.messages.KEEPALIVE_ACK;
import org.llrp.ltk.generated.messages.GET_READER_CONFIG;
import org.llrp.ltk.generated.messages.READER_EVENT_NOTIFICATION;
import org.llrp.ltk.generated.messages.RO_ACCESS_REPORT;
import org.llrp.ltk.generated.messages.SET_READER_CONFIG;
import org.llrp.ltk.generated.messages.SET_READER_CONFIG_RESPONSE;
import org.llrp.ltk.generated.messages.STOP_ROSPEC;
import org.llrp.ltk.generated.messages.STOP_ROSPEC_RESPONSE;
import org.llrp.ltk.generated.parameters.AISpec;
import org.llrp.ltk.generated.parameters.AISpecStopTrigger;
import org.llrp.ltk.generated.parameters.AccessReportSpec;
import org.llrp.ltk.generated.parameters.C1G2EPCMemorySelector;
import org.llrp.ltk.generated.parameters.EventsAndReports;
import org.llrp.ltk.generated.parameters.EventNotificationState;
import org.llrp.ltk.generated.parameters.InventoryParameterSpec;
import org.llrp.ltk.generated.parameters.KeepaliveSpec;
import org.llrp.ltk.generated.parameters.LLRPStatus;
import org.llrp.ltk.generated.parameters.ROBoundarySpec;
import org.llrp.ltk.generated.parameters.ROReportSpec;
import org.llrp.ltk.generated.parameters.ROSpec;
import org.llrp.ltk.generated.parameters.ROSpecStartTrigger;
import org.llrp.ltk.generated.parameters.ROSpecStopTrigger;
import org.llrp.ltk.generated.parameters.ReaderEventNotificationData;
import org.llrp.ltk.generated.parameters.ReaderEventNotificationSpec;
import org.llrp.ltk.generated.parameters.TagReportContentSelector;
import org.llrp.ltk.generated.parameters.TagReportData;
import org.llrp.ltk.types.Bit;
import org.llrp.ltk.types.LLRPMessage;
import org.llrp.ltk.types.UnsignedByte;
import org.llrp.ltk.types.UnsignedInteger;
import org.llrp.ltk.types.UnsignedShort;
import org.llrp.ltk.types.UnsignedShortArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This LLRP Enterprise Connector uses the Java LLRP Toolkit (llrp.org).
 * It creates a connection with the reader, sets up an ROSpec (Reader Operation
 * Specification) to start reading tag data in Field of View (FOV) of the antenna.
 * It publishes all tag data found in the FOV during a configured duration.
 * (Default 500 milliseconds).  Some readers may not report the accurate LastSeenTime
 * and insteads fill it with the FirstSeenTime (use this one for storing).
 *
 * Note: Requires an Hostname/IP Address and Port to communicate with the RFID Reader.
 *
 * @author Mark Silva msilva@vantiq.com
 *
 */
public class LLRPConnector  {
    Logger              log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    // Valid logLevels
    private static ArrayList<String> LogLevels = new ArrayList<String>(Arrays.asList("error", "warn", "info", "debug"));
    final static String INFO = "info";
    final static String WARN = "warn";
    final static String ERROR = "error";
    final static String DEBUG = "debug";
    final static String TAG_DATA_EVENT_NAME = "tagData";
    final static String READER_DATA_EVENT_NAME = "readerData";
    final static String READER_STATUS_EVENT_NAME = "readerStatus";
    final static String M_SUCCESS = "M_Success";  // String value for successful status
    final static Boolean READER_ONLINE = false;   // Used to set 'readerOffLine' property
    final static Boolean READER_OFFLINE = true;   // Used to set 'readerOffLine' property

    // Used to reconnect if necessary
    private String hostname = null;		    // Hostname/IP address of the reader
    private String sourceName = null;       // Vantiq Source name connected to
    private int readerPort = -1;			// Reader communication port
    private int tagReadInterval = 500;		// Frequency for ending AISpec
    private int logLevel = -1;              // Log level to send to Vantiq
    private static int ROSPEC_ID = 1;		// ID used for collecting tags

    private Socket connection;				// Socket connection to reader
    private DataOutputStream out;			// Stream when writing to reader
    private ReadThread rt = null;			// Reads LLRP msg and add to queue
    private Thread msgThread = null;		// Process regular msgs from reader
    private Thread tagThread = null;		// Process tag data msgs from reader
    private Thread readerAliveThread = null;	// Identify when reader is offline
    private LLRPConnectorCore llrpCore;
    private Boolean readerOffline = true;	// Indicates the Reader is Offline
    private Boolean vantiqSourceOffline = true;	// Indicates the connection to the Vantiq Source is Offline
    Instant lastKeepAlive = Instant.now();	// Last point we rcvd from reader

    /*
     * RECONNECT_INTERVAL is the duration to wait to check if Reader is online.
     * It may take 15-30 seconds to flush through stored messages on the reader
     * since we do not turn off the reading of tags (stored on reader and
     * processed on reconnect).
     * The KEEPALIVE_INTERVAL is the periodic interval the reader will use
     * to send a KEEPALIVE message.  If we don't receive a KEEPALIVE message
     * within the RECONNECT_INTERVAL, we drop the connection and try to
     * reconnect indefinitely.
     */
    final static int KEEPALIVE_INTERVAL = 5000;
    final static int RECONNECT_INTERVAL = 10000;

    /**
     * Message queues for the tags and other messages
     */
    private LinkedBlockingQueue<LLRPMessage> queue
            = new LinkedBlockingQueue<LLRPMessage>();
    private LinkedBlockingQueue<LLRPMessage> tagQueue
            = new LinkedBlockingQueue<LLRPMessage>();

    /**
     * Current reader Capabilities and Configuration information.
     * Captured but not used directly for now.
     */
    private JSONObject readerCapabilities = null;
    private JSONObject readerConfiguration = null;
    private String readerId = "reader1";



    /**
     * The constructor creates a connection to the reader and sends LLRP
     * messages. Once connected, ideal communication is as follows:
     *     From Enterprise Connector	From the Reader
     *  1) GET_READER_CAPABILIITIES 	GET_READER_CAPABILITIES_RESPONSE
     *  2) SET_READER_CONFIG 			SET_READER_CONFIG_RESPONSE
     *  3) GET_READER_CONFIG 			GET_READER_CONFIG_RESPONSE
     *  4) ADD_ROSPEC				 	ADD_ROSPEC_RESPONSE
     *  5) ENABLE_ROSPEC				ENABLE_ROSPEC_RESPONSE
     *  6) ENABLE_EVENTS_AND_REPORTS	<NONE>
     *
     * During the above cycle, all other READER_EVENT_NOTIFICATION messages
     * are ignored.  After the reader starts up, it queues all messages
     * and processes them asynchronously.
     *
     * A KEEPALIVE message is configured to be sent from the reader
     * periodically and the LLRP Enterprise Connector sends back a
     * KEEPALIVE_ACK message to the reader.  If this ACK is not sent, the
     * reader will shutdown.
     *
     * Additionally, a timer task is used to assure the Reader stays alive.
     * If a KEEPALIVE message is not received at least once a minute the
     * LLRP Enterprise Connector will disconnect from the Reader and
     * continually attempt to reconnect with the Reader every minute.
     * Not all readers are alike, and it may get into a situation where
     * the Reader thinks another connection already exists.  In this case,
     * a reboot of the Reader should clear it up.
     *
     * @throws IOException
     * @throws VantiqLLRPException
     */
    public void setupLLRPConnector(LLRPConnectorCore llrpCore, String hostname,
                                   int readerPort, int tagReadInterval, String logLevel)
            throws IOException, VantiqLLRPException {

        this.hostname = hostname;
        this.readerPort = readerPort;
        this.tagReadInterval = tagReadInterval;
        this.logLevel = LogLevels.indexOf(logLevel);
        this.llrpCore = llrpCore;  // Used to send messages to VANTIQ
        this.sourceName = llrpCore.getSourceName();
        this.vantiqSourceOffline = false; // We are only called if connection to Vantiq Source is up
        
        systemPrint("setupLLRP: hostname-"+hostname+":"+readerPort+" tagReadInterval: "+tagReadInterval);

        // Connect and set up the Reader - attempt every minute
        connectAndSetupReader();

        // Create the queue processing and keepalive threads for the reader
        createAndStartThreads();

    }

    /**
     * Task used for initial connection and reconnecting to the Reader.
     * Running as a thread to be able to pause and wait for responses to messages
     * which is received on blocking reads.
     *
     */
    private void connectAndSetupReader () {

        Thread setup = new Thread(() -> {
            try {

                systemPrint("connectAndSetupReader started : " + Thread.currentThread().getName());
                sendLogMessage(INFO, "Attempting to connect to the the Reader");

                // If connected, nothing to do
                if (readerOffline && !vantiqSourceOffline) {

                    try {
                        // Clear any messages in the queue, keep tag messages
                        queue.clear();

                        // Try to establish a connection to the reader
                        connection = new Socket(hostname, readerPort);
                        out = new DataOutputStream(connection.getOutputStream());

                        // Start up the ReaderThread to read messages form socket to Console
                        rt = new ReadThread(connection);
                        rt.start();

                        // ReaderEventNotificationData is sent by the Reader for a connection attempt,
                        // though can be sent for other reader events. List of events:
                        //		<HoppingEventParameter>,
                        //		<GPIEvent Parameter>,
                        //		<ROSpecEvent Parameter>,
                        //		<ReportBufferLevelWarningEvent Parameter>,
                        //		<ReportBufferOverflowErrorEvent Parameter>,
                        //		<ReaderExceptionEvent Parameter>,
                        //		<RFSurveyEvent Parameter>,
                        //		<AISpecEvent Parameter>,
                        //		<AntennaEvent Parameter>,
                        //		<ConnectionAttemptEvent Parameter>,
                        //		<ConnectionCloseEvent Parameter> - unsolicited close by the reader
                        // Since we just tried to connect, we are expecting a ConnectionAttempEvent.
                        //	Possible Values:
                        //		  Value Definition
                        //		  ----- ----------
                        //			0	Success
                        //			1	Failed (a Reader initiated connection already exists)
                        //			2	Failed (a Client initiated connection already exists)
                        //			3	Failed (any reason other than a connection already exists
                        //			4	Another connection attempted
                        //
                        // We will skip all responses from the reader until a ConnectionAttempEvent is
                        // received.
                        LLRPMessage m = getNextMessage("CONNECTION_ATTEMPT");
                        READER_EVENT_NOTIFICATION readerEventNotification = (READER_EVENT_NOTIFICATION) m;
                        ReaderEventNotificationData eventData = readerEventNotification
                                .getReaderEventNotificationData();

                        ConnectionAttemptStatusType connectionStatus = eventData.getConnectionAttemptEvent().getStatus();
                        if (connectionStatus.toInteger() == ConnectionAttemptStatusType.Success) {
                            systemPrint("Connection attempt was successful\n");
                            sendLogMessage(INFO, "Connection to Reader was successful");
                        } else {
                            String msg = "Reader Connection Unsucessful: " + connectionStatus.toString();
                            systemPrint(msg);
                            sendLogMessage(WARN, msg);
                            if (connection != null && !connection.isClosed())
                                connection.close();
                            return;
                        }

                        // TODO: Could get the Reader supported version and set to an appropriate version

                        // Get the Reader Capabilities Response
                        readerCapabilities = getReaderCapabilities();

                        // Create/Send the Reader Configuration and Get the Response
                        readerConfiguration = sendReaderConfiguration();

                        // Create/Send ROSpec to start reading the tags
                        sendROSpec(tagReadInterval);

                        // Indicate success
                        changeReaderStatus(READER_ONLINE);

                        // Send an ENABLE_EVENTS_AND_REPORTS Message to start requesting tag data
                        ENABLE_EVENTS_AND_REPORTS report = new ENABLE_EVENTS_AND_REPORTS();
                        write(report, "ENABLE_EVENTS_AND_REPORTS");

                    } catch (VantiqLLRPException | IOException e) {
                        systemPrint("Unable to connect and startup the reader.");
                        sendLogMessage(WARN, "connectAndSetupReader: Unable to connect and startup the reader, retrying");
                        if (connection != null && !connection.isClosed())
                            try {
                                connection.close();
                            } catch (Exception e1) {
                                systemPrint("Unable to close open connection. ");
                                e.printStackTrace();
                                sendLogMessage(ERROR, "connectAndSetupReader: Unable to close open connection.\n"
                                        + ExceptionUtils.getStackTrace(e1));
                            }
                    } catch (Exception e) {
                        systemPrint("Unexpected error while attempting to connect to the reader");
                        e.printStackTrace();
                        sendLogMessage(ERROR, "connectAndSetupReader: Unexpected error.\n "
                                + ExceptionUtils.getStackTrace(e));
                    }
                } else {
                    systemPrint("connectAndSetupReader: reader is offline");
                    sendLogMessage(ERROR, "connectAndSetupReader: reader is offline");
                }
            } catch (Exception e) {
                systemPrint("connectAndSetupReader: Unexpected error");
                e.printStackTrace();
                sendLogMessage(ERROR, "connectAndSetupReader: Unexpected error.\n" + ExceptionUtils.getStackTrace(e));
            }
        });
        setup.start();

    }


    /**
     *  Send the reader a "GET_READER_CAPABILTIES" message and wait for the response.
     *
     *  	Possible request data values:
     *  		Value   Definition
     *  		-----   ----------
     *  		  0	    All
     *  		  1		General Device Capabilities
     *  		  2		LLRP Capabilities
     *  		  3		Regulatory Capabilities
     *  		  4		Air Protocol LLRP Capabilities
     *
     * @return GET_READER_CAPABILITIES_RESPONSE with the reader capabilities
     * @throws VantiqLLRPException
     */
    private JSONObject getReaderCapabilities() throws VantiqLLRPException {

       // Send the message to the reader to get it's capabilities
        GET_READER_CAPABILITIES getReaderCap = new GET_READER_CAPABILITIES();
        getReaderCap.setRequestedData(new GetReaderCapabilitiesRequestedData
                                            (GetReaderCapabilitiesRequestedData.LLRP_Capabilities));
        write(getReaderCap, "GET_READER_CAPABILITIES");
        pause(500);

        JSONObject returnJSON = new JSONObject();
        try {
            LLRPMessage m = getNextMessage("GET_READER_CAPABILITIES_RESPONSE");
            GET_READER_CAPABILITIES_RESPONSE readerCap = (GET_READER_CAPABILITIES_RESPONSE) m;

            returnJSON = XML.toJSONObject(readerCap.toXMLString());
            sendLogMessage(DEBUG, "READER_CAPABILITIES: "+ returnJSON);
        } catch (InvalidLLRPMessageException e) {
            reportLLRPError(e);
        } catch (Exception e) {
            e.printStackTrace();
            sendLogMessage(ERROR, "getReaderCapabilities: Unexpected error.\n"+ExceptionUtils.getStackTrace(e));
        }

        return returnJSON;
    }

    /**
     *  Create and send the reader a "SET_READER_CONFIG" message and wait for the response.
     *
     *  	Possible requested data values for GET_READER_CONFIG:
     *  		Value   Definition
     *  		-----   ----------
     *  		  0	    All
     *			  1		Identification
     *			  2		AntennaProperties
     *			  3		AntennaConfiguration
     *			  4		ROReportSpec
     *			  5 	ReaderEventNotificationSpec
     *			  6 	AccessReportSpec
     *			  7 	LLRPConfigurationStateValue
     *			  8		KeepaliveSpec
     *			  9		GPIPortCurrentState
     *			  10	GPOWriteData
     *			  11	EventsAndReports
     *
     * @return GET_READER_CONFIG_RESPONSE with the reader configuration
     * @throws VantiqLLRPException
     */
    private JSONObject sendReaderConfiguration() throws VantiqLLRPException {

        // Create and send a SET_READER_CONFIG Message with default ROReportSpec
        // and AccessReportSpec
        SET_READER_CONFIG setReaderConfig = createSetReaderConfig();
        write(setReaderConfig, "SET_READER_CONFIG");
        pause(250);

        // Wait for the response
        LLRPMessage m = getNextMessage("SET_READER_CONFIG_RESPONSE");
        SET_READER_CONFIG_RESPONSE resp = (SET_READER_CONFIG_RESPONSE) m;
        sendLogMessage(DEBUG, "SET_READER_CONFIG_RESPONSE: " + getLLRPStatus(resp.getLLRPStatus()));

        // Send GET_READER_CONFIG after after set to store current settings
        GET_READER_CONFIG getReaderConfig = new GET_READER_CONFIG();
        getReaderConfig.setRequestedData(new GetReaderConfigRequestedData(
                GetReaderConfigRequestedData.All));
        getReaderConfig.setAntennaID(new UnsignedShort(0));  // get configuration on ALL antennas
        getReaderConfig.setGPIPortNum(new UnsignedShort(0)); // get GPI port current state for all GPI ports
        getReaderConfig.setGPOPortNum(new UnsignedShort(0)); // get GPO port current state for all GPO ports
        write(getReaderConfig, "GET_READER_CONFIG");
        pause(250);

        JSONObject returnJSON = new JSONObject();
        try {
            LLRPMessage rm = getNextMessage("GET_READER_CONFIG_RESPONSE");
            GET_READER_CONFIG_RESPONSE readerConfig = (GET_READER_CONFIG_RESPONSE) rm;

            returnJSON = XML.toJSONObject(readerConfig.toXMLString());
            //systemPrint("GET_READER_CONFIG_RESPONSE: " + returnJSON.toString(2));
            sendLogMessage(DEBUG, "READER_CONFIG: "+ returnJSON);

            // Get the reader and antennas
            JSONObject config = returnJSON.getJSONObject("llrp:GET_READER_CONFIG_RESPONSE");
            Map<String, Object> tagObj = new HashMap<>();
            tagObj.put("eventType", READER_DATA_EVENT_NAME);
            if (readerConfig != null) {
                // Get the reader ID
                JSONObject identification = config.getJSONObject("llrp:Identification");
                if (identification != null) {
                    readerId = identification.getString("llrp:ReaderID").toString();
                    tagObj.put("readerId", readerId);
                }
                // Get the Antenna IDs
                JSONArray antennas = config.getJSONArray("llrp:AntennaProperties");
                if (antennas != null) {
                    ArrayList<Integer> antennaArr = new ArrayList<Integer>();
                    for (int i = 0; i < antennas.length(); i++) {
                        JSONObject obj = antennas.getJSONObject(i);
                        Integer antenna = obj.getInt("llrp:AntennaID");
                        antennaArr.add(antenna);
                    }
                    tagObj.put("antennaIds", antennaArr);
                }
                // Send the tag data back to VANTIQ
                this.llrpCore.sendMessage(tagObj);
            }
        } catch (InvalidLLRPMessageException e) {
            reportLLRPError(e);
        } catch (Exception e) {
            e.printStackTrace();
            sendLogMessage(ERROR, "sendReaderConfiguration: Unexpected error.\n"+ExceptionUtils.getStackTrace(e));
        }

        return returnJSON;
    }

    /**
     * This method creates a SET_READER_CONFIG message to be sent to the reader.
     *
     *  	Possible configuration data parameters for SET_READER_CONFIG:
     *  		*<ReaderEventNotificationSpec Parameter> - see below
     *  		<Antenna Properties Parameter>
     *  		<Antenna Configuration Parameter>
     *  		*<ROReportSpec Parameter> - see below
     *  		*<AccessReportSpec Parameter> - see below
     *  		*<KeepaliveSpec Parameter> - carries the specification for the keepalive
     *  									message generation by the Reader. Includes
     *  									periodic trigger to send the keepalive message
     *  		<GPOWriteData Parameter>
     *  		<GPIPortCurrentState Parameter>
     *  		*<EventsAndReports Parameter> - used to enable or disable the holding of
     *  									    events and reports upon connection using
     *  									    the HoldEventsAndReportsUponReconnect field
     *
     * Only the '*' parameters highlighted above are being included in the SET_READER_CONFIG
     *
     * @return SET_READER_CONFIG message
     */
    private SET_READER_CONFIG createSetReaderConfig() {
        SET_READER_CONFIG setReaderConfig = new SET_READER_CONFIG();

        // ResetToFactoryDefault: If true, the Reader will set all configurable values
        //		to factory defaults before applying the remaining parameters.
        setReaderConfig.setResetToFactoryDefault(new Bit(0));


        // ReaderEventNotificationSpec Parameter - composed of a list of EventNotificationStates
        // -------------------------------------
        // The following are possible EventNotificationStates EventTypes:
        //      Value   Definition
        //      -----   -------------------------------------------
        //        0     Upon hopping to next channel (e.g., in FCC regulatory region)
        //        1     GPI event
        //        2     ROSpec event (start/end/preemption)
        //        3     Report buffer fill warning
        //        4     Reader exception event
        //        5     RFSurvey event (start/end)
        //        6     AISpec event (end)
        //        7     AISpec event (end) with singulation details
        //        8     Antenna event (disconnect/connect)
        //        9     SpecLoop event
        //  For each EventType, they can be enabled=true or disabled=false

        //	- Set up reporting for AISpec events, and ROSpec events
        ReaderEventNotificationSpec eventNoteSpec = new ReaderEventNotificationSpec();
        // GPI event - Disable
        EventNotificationState noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.GPI_Event));
        noteState.setNotificationState(new Bit(0));  // disable
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // ROSpec event - Enable
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.ROSpec_Event));
        noteState.setNotificationState(new Bit(1));  // start(0)/end(1)/preemption(2)
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // Report buffer fill warning - Disable (ReportBufferPercentageFull value: 0-100)
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.Report_Buffer_Fill_Warning));
        noteState.setNotificationState(new Bit(0));
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // Reader exception event - Disable
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.Reader_Exception_Event));
        noteState.setNotificationState(new Bit(0));
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // RFSurvey event - Disable
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.RFSurvey_Event));
        noteState.setNotificationState(new Bit(0));
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // AISpec event - Enable
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.AISpec_Event));
        noteState.setNotificationState(new Bit(1));
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // AISpec event (end) with singulation details - Enable
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.AISpec_Event_With_Details));
        noteState.setNotificationState(new Bit(1));
        eventNoteSpec.addToEventNotificationStateList(noteState);

        // Antenna event - Disable
        noteState = new EventNotificationState();
        noteState.setEventType(new NotificationEventType(NotificationEventType.Antenna_Event));
        noteState.setNotificationState(new Bit(0));
        eventNoteSpec.addToEventNotificationStateList(noteState);

        setReaderConfig.setReaderEventNotificationSpec(eventNoteSpec);


        // ROReportSpec Parameter
        // ----------------------
        // - Create a default RoReportSpec so that reports are sent at the end of ROSpecs
        // - Default for ALL ROSpecs, though can be overridden as part of the ROSpec
        ROReportSpec roReportSpec = new ROReportSpec();
        roReportSpec.setN(new UnsignedShort(0));  // Unlimited tags, since ROSpec ends periodically
        roReportSpec.setROReportTrigger(new ROReportTriggerType(
                ROReportTriggerType.Upon_N_Tags_Or_End_Of_ROSpec));

        // TagReportContentSelector: used to identify what to include in the report
        //	- All options listed below, just turn on/off to identify which one.
        //	Note: first/LastSeenTimestamp are epoch microseconds
        //	Note: When TagReportData is returned from the reader, If an optional parameter is
        //		  enabled, and is absent in the report, the Client SHALL assume that the value is
        //		  identical to the last parameter of the same type received. For example, this
        //		  allows the Readers to not send a parameter in the report whose value has not
        //		  changed since the last time it was sent by the Reader.
        TagReportContentSelector tagReportContentSelector = new TagReportContentSelector();
        tagReportContentSelector.setEnableAccessSpecID(new Bit(1));
        tagReportContentSelector.setEnableAntennaID(new Bit(1));
        tagReportContentSelector.setEnableChannelIndex(new Bit(1));
        tagReportContentSelector.setEnableFirstSeenTimestamp(new Bit(1));
        tagReportContentSelector.setEnableInventoryParameterSpecID(new Bit(1));
        tagReportContentSelector.setEnableLastSeenTimestamp(new Bit(1));
        tagReportContentSelector.setEnablePeakRSSI(new Bit(1));
        tagReportContentSelector.setEnableROSpecID(new Bit(1));
        tagReportContentSelector.setEnableSpecIndex(new Bit(1));
        tagReportContentSelector.setEnableTagSeenCount(new Bit(1));
        C1G2EPCMemorySelector epcMemSel = new C1G2EPCMemorySelector();
        epcMemSel.setEnableCRC(new Bit(0));     // Only sending EPC in RO Report, disable CRC
        epcMemSel.setEnablePCBits(new Bit(0));  // Only sending EPC in RO Report, disable PC Bits
        tagReportContentSelector.addToAirProtocolEPCMemorySelectorList(epcMemSel);

        roReportSpec.setTagReportContentSelector(tagReportContentSelector);
        setReaderConfig.setROReportSpec(roReportSpec);


        // AccessReportSpec Parameter
        // --------------------------
        //	AccessReportTrigger - 0 = Whenever ROReport is generated for the RO that triggered
        //							  the execution of this AccessSpec
        //						  1 = End of AccessSpec
        AccessReportSpec accessReportSpec = new AccessReportSpec();
        accessReportSpec.setAccessReportTrigger(new AccessReportTriggerType(
                AccessReportTriggerType.Whenever_ROReport_Is_Generated));
        setReaderConfig.setAccessReportSpec(accessReportSpec);


        // KeepaliveSpec Parameter
        // -----------------------
        //	keepaliveTriggerType - (0 = Null no ack sent; 1 = Periodic, send based on Trigger Value)
        //	periodicTriggerValue - Time in milliseconds of no activity to send a Keepalive Ack message
        KeepaliveSpec keepaliveSpec = new KeepaliveSpec();
        keepaliveSpec.setKeepaliveTriggerType(new KeepaliveTriggerType (KeepaliveTriggerType.Periodic));
        keepaliveSpec.setPeriodicTriggerValue(new UnsignedInteger(KEEPALIVE_INTERVAL));
        setReaderConfig.setKeepaliveSpec(keepaliveSpec);

        // EventsAndReports Parameter
        // -----------------------
        //	HoldEventsAndReportsUponReconnect - (0 = do not hold reports; 1 = holds reports and events)
        //
        // The reader will not deliver any reports or events (except the ConnectionAttmptEvent)
        // when true.  Once the ENABLE_EVENTS_AND_REPORTS message is received the reader ceases its
        // hold on events and reports for the duration of the connection.
        //
        EventsAndReports eventsAndReportsSpec = new EventsAndReports();
        eventsAndReportsSpec.setHoldEventsAndReportsUponReconnect(new Bit(1));
        setReaderConfig.setEventsAndReports(eventsAndReportsSpec);

        return setReaderConfig;

    }

    /**
     * Create and send the ROSpec to the Reader - once this is sent, tag data will be returned
     *
     * Reader Operations (RO) define the parameters for operations such as Antenna Inventory
     * and RF Survey. Access Operations define the parameters for performing data access
     * operations to and from a tag.
     *
     * Note: If a connection terminates while currently processing an ROSpec,
     * 		 that is, it wasn't deleted, then you will not be able to add a
     * 		 new ROSpec with the same ID.  So, review the response and upon
     * 		 errors, stop and delete before trying to add again.
     *
     * @param duration in milliseconds for returning tag data
     * @throws VantiqLLRPException
     */
    private void sendROSpec (int tagReadInterval) throws VantiqLLRPException {

        // Create the ROSpec to be used
        ROSpec roSpec = createROSpec(new UnsignedInteger(tagReadInterval));  // Report on the tags every x milliseconds

        // Send an ADD_ROSPEC Message with ROSpec Disabled state
        ADD_ROSPEC addROSpec = new ADD_ROSPEC();
        addROSpec.setROSpec(roSpec);
        write(addROSpec, "ADD_ROSPEC");
        try {
            sendLogMessage(DEBUG, "Sent ADD_ROSPEC: "+XML.toJSONObject(addROSpec.toXMLString()).toString());
        } catch (InvalidLLRPMessageException e) {
            reportLLRPError(e);
        } catch (Exception e) {
            e.printStackTrace();
            sendLogMessage(ERROR, "sendROSpec: Unexpected error.\n"+ExceptionUtils.getStackTrace(e));
        }
        pause(250);

        LLRPMessage m = getNextMessage("ADD_ROSPEC_RESPONSE");
        ADD_ROSPEC_RESPONSE addResponse = (ADD_ROSPEC_RESPONSE) m;
        sendLogMessage(DEBUG, "ADD_ROSPEC_RESPONSE: " + getLLRPStatus(addResponse.getLLRPStatus()));

        // if an ROSpec is still active from a previous connector start, the ADD_ROSPEC will fail.
        // Clear out the current ROSpec in the reader and re-add it again.
        if (addResponse.getLLRPStatus().getStatusCode().toString() != M_SUCCESS) {
            systemPrint("** ADD_ROSPEC NOT SUCCESS: " + addResponse.getLLRPStatus().getStatusCode().toString()
                                + ", retry");

            // Send a STOP_ROSPEC Message to set ROSpec to InActive state
            STOP_ROSPEC stopROSpec = new STOP_ROSPEC();
            stopROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
            write(stopROSpec, "STOP_ROSPEC");
            pause(250);
            m = getNextMessage("STOP_ROSPEC_RESPONSE");
            STOP_ROSPEC_RESPONSE smr = (STOP_ROSPEC_RESPONSE) m;
            sendLogMessage(DEBUG, "STOP_ROSPEC_RESPONSE: " + getLLRPStatus(smr.getLLRPStatus()));

            // Send a DISABLE_ROSPEC Message to set ROSpec to Disabled state
            DISABLE_ROSPEC disableROSpec = new DISABLE_ROSPEC();
            disableROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
            write(disableROSpec, "DISABLE_ROSPEC");
            pause(250);
            m = getNextMessage("DISABLE_ROSPEC_RESPONSE");
            DISABLE_ROSPEC_RESPONSE dmr = (DISABLE_ROSPEC_RESPONSE) m;
            sendLogMessage(DEBUG, "DISABLE_ROSPEC_RESPONSE: " + getLLRPStatus(dmr.getLLRPStatus()));

            // Send a DELETE_ROSPEC Message to set remove ROSpec
            DELETE_ROSPEC deleteROSpec = new DELETE_ROSPEC();
            deleteROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
            write(deleteROSpec, "DELETE_ROSPEC");
            pause(250);
            m = getNextMessage("DELETE_ROSPEC_RESPONSE");
            DELETE_ROSPEC_RESPONSE dlmr = (DELETE_ROSPEC_RESPONSE) m;
            sendLogMessage(DEBUG, "DELETE_ROSPEC_RESPONSE: " + getLLRPStatus(dlmr.getLLRPStatus()));

            // Resend ADD_ROSPEC Message with ROSpec now in the Disabled state
            write(addROSpec, "ADD_ROSPEC");
            pause(250);
            m = getNextMessage("ADD_ROSPEC_RESPONSE");
            addResponse = (ADD_ROSPEC_RESPONSE) m;
            sendLogMessage(DEBUG, "ADD_ROSPEC_RESPONSE: " + getLLRPStatus(addResponse.getLLRPStatus()));
            systemPrint("** ADD_ROSPEC_RESPONSE: " + addResponse.getLLRPStatus().getStatusCode().toString());
        }

        // Send an ENABLE_ROSPEC Message to set ROSpec to Inactive state
        ENABLE_ROSPEC enableROSpec = new ENABLE_ROSPEC();
        enableROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        write(enableROSpec, "ENABLE_ROSPEC");
        pause(250);
        m = getNextMessage("ENABLE_ROSPEC_RESPONSE");
        ENABLE_ROSPEC_RESPONSE emr = (ENABLE_ROSPEC_RESPONSE) m;
        sendLogMessage(DEBUG, "ENABLE_ROSPEC_RESPONSE: " + getLLRPStatus(emr.getLLRPStatus()));

        // Send a START_ROSPEC Message to set ROSpec to Active state
        // (Note: Not needed if ROSpec is Immediate or Periodic)
        //START_ROSPEC startROSpec = new START_ROSPEC();
        //startROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        //write(startROSpec, "START_ROSPEC");
        //pause(250);

    }

    /**
     * Create a new ROSpec Parameter
     *
     * Reader Operations (RO) define the parameters for operations such as Antenna Inventory
     * and RF Survey. Access Operations define the parameters for performing data access
     * operations to and from a tag.
     *
     * The timing control of an operation is specified using boundary specification, which
     * specifies how the beginning (using start trigger) and the end (using stop trigger) of the
     * operation is to be determined.
     *
     *		ROSpecStartCondition = 	ROSpecStartTrigger or START_ROSPEC received
     *		ROSpecDoneCondition  = 	AllSpecsDone or ROSpecStopTrigger or preempted or
     *								(STOP_ROSPEC message for the ROSpec from the Client)
     *	 Note: Even if you have complex start/stop triggers, the reader will respond to
     *		   START_ROSPEC and STOP_ROSPEC (useful when testing application)
     *
     * @param duration in milliseconds between each AISpec trigger
     * @return ROSpec message
     */
    private ROSpec createROSpec(UnsignedInteger duration) {

        ROSpec roSpec = new ROSpec();
        roSpec.setPriority(new UnsignedByte(0));  // 0-7, with 0 being highest
        roSpec.setCurrentState(new ROSpecState(ROSpecState.Disabled));  // Must be added in Disable state
        roSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID)); // Only using one ROSpec ID

        //set up ROBoundary (start and stop triggers)
        ROBoundarySpec roBoundarySpec = new ROBoundarySpec();

        // ROSpecStartTrigger Parameter
        //	Possible Values:
        //		Value   Definition
        //		-----   ----------
        //		  0	    Null - No start trigger. The only way to start the ROSpec is with a
        //              START_ROSPEC from the Client.
        //		  1	    Immediate - Start reading tags as soon as the ROSpec is enabled
        //		  2	    Periodic - set period to run every 'duration' milliseconds
        //                  UTCTimestamp, Period (milliseconds), Offset (to UTC when msg received)
        //	      3     GPI - see spec for more details
        //
        ROSpecStartTrigger startTrig = new ROSpecStartTrigger();
        startTrig.setROSpecStartTriggerType(new ROSpecStartTriggerType(ROSpecStartTriggerType.Immediate));
        roBoundarySpec.setROSpecStartTrigger(startTrig);

        // ROSpecStopTrigger Parameter
        //	Possible Values:
        //		Value   Definition
        //		-----   ----------
        //		  0	    Null - Stop when all Specs are done (including any looping as required by
        //				a LoopSpec parameter), or when preempted, or with a STOP_ROSPEC from the
        //				Client.
        //		  1	    Duration - Stop after DurationTriggerValue milliseconds, or when all Specs
        //				are done (including any looping as required by a LoopSpec parameter), or
        //				when preempted, or with a STOP_ROSPEC from the Client.
        //		  2	    GPI with a timeout value - Stop when a GPI "fires", or after Timeout
        //				milliseconds, or when all Specs are done (including any looping as
        //				required by a LoopSpec parameter), or when preempted, or with a STOP_ROSPEC
        //				from the Client.
        //
        ROSpecStopTrigger stopTrig = new ROSpecStopTrigger();
        // No stop trigger.  Respond after the AISpec completes (which is set with a duration)
        stopTrig.setROSpecStopTriggerType(new ROSpecStopTriggerType(ROSpecStopTriggerType.Null));
        stopTrig.setDurationTriggerValue(new UnsignedInteger(0)); // required, but ignored with Trigger type is Null
        roBoundarySpec.setROSpecStopTrigger(stopTrig);
        roSpec.setROBoundarySpec(roBoundarySpec);

        // Add an Antenna Inventory Spec (AISpec) Parameter - contains the following:
        //      - AISpecStopTrigger - identifies the stop of the antenna inventory operation
        //      - AntennaIds - array of antenna IDs for operation (0=all are utilized)
        //      - InventoryParameterSpecs - list of inventory parameters
        //
        AISpec aispec = new AISpec();

        // AISpecStopTrigger Parameter
        //	Possible Values:
        //		Value   Definition
        //		-----   ----------
        //		  0	    Null - Stop when ROSpec is done
        //		  1		Duration - in milliseconds (reads tags for x duration)
        //		  2		GPI trigger with a timeout value
        //		  3		Tag observation (i.e., seeing N tags, no tags for T ms, etc.)

        // Sed to read based on a duration value
        AISpecStopTrigger aiStopTrigger = new AISpecStopTrigger();
        aiStopTrigger.setAISpecStopTriggerType(new AISpecStopTriggerType(AISpecStopTriggerType.Duration));
        aiStopTrigger.setDurationTrigger(duration);  // ignored when type is not Duration
        aispec.setAISpecStopTrigger(aiStopTrigger);

        // Antenna IDs: set to 0 to utilize all antennas of the Reader
        UnsignedShortArray antennaIDs = new UnsignedShortArray();
        antennaIDs.add(new UnsignedShort(0));
        aispec.setAntennaIDs(antennaIDs);

        // InventoryParameterSpec
        InventoryParameterSpec inventoryParam = new InventoryParameterSpec();
        inventoryParam.setProtocolID(new AirProtocols(AirProtocols.EPCGlobalClass1Gen2));  // Really the only valid option today
        inventoryParam.setInventoryParameterSpecID(new UnsignedShort(1));  // Any ID, must not be zero
        aispec.addToInventoryParameterSpecList(inventoryParam);

        roSpec.addToSpecParameterList(aispec);

        return roSpec;
    }

    /**
     * Create and start the threads to managing the messages from the reader and
     * the reader connectivity.
     */
    private void createAndStartThreads() {

        sendLogMessage(INFO, "createAndStartThreads: Starting Threads");
        if (msgThread == null) {
            // Thread for processing regular messages
            msgThread = new Thread(() -> {
                systemPrint("msgThread started : " + Thread.currentThread().getName());
                while (true) {
                    try {
                        // May have to check if connection to reader is stopped
                        if (readerOffline) {
                            Thread.sleep(RECONNECT_INTERVAL);  // if not connected
                        } else {
                            LLRPMessage m = getNextMessage();
                            processRegularMessage (m);
                        }
                    } catch (Exception e) {
                        systemPrint("msgThread interrupted. ");
                        e.printStackTrace();
                        sendLogMessage(ERROR, "createAndStartThreads-msgThread: Unexpected error.\n"
                                + ExceptionUtils.getStackTrace(e));
                    }
                }
            });
            msgThread.start();
        }

        if (tagThread == null) {
            // Thread for processing tag data messages
            tagThread = new Thread(() -> {
                systemPrint("tagThread started : " + Thread.currentThread().getName());
                while (true) {
                    try {
                        // May have to check if connection to reader is stopped
                        if (readerOffline) {
                            Thread.sleep(RECONNECT_INTERVAL);  // if not connected
                        } else {
                            LLRPMessage m = getNextTagMessage();
                            processTagDataMessage (m);
                        }
                    } catch (Exception e) {
                        systemPrint("tagThread interrupted. ");
                        e.printStackTrace();
                        sendLogMessage(ERROR, "createAndStartThreads-tagThread: Unexpected error.\n"
                                + ExceptionUtils.getStackTrace(e));
                    }
                }
            });
            tagThread.start();
        }

        if (readerAliveThread == null) {
            // Thread to assure connection to the reader is good
            readerAliveThread = new Thread(() -> {
                systemPrint("readerAliveThread started : " + Thread.currentThread().getName());
                while (true) {
                    try {
                        Thread.sleep(RECONNECT_INTERVAL);
                        long duration = Duration.between(lastKeepAlive, Instant.now()).toMillis();
                        // We only want to connect if the connection to the Vantiq Source is online
                        // since we are not buffering any tag read messages -- letting the Reader buffer
                        if (duration > RECONNECT_INTERVAL && !vantiqSourceOffline) {
                            systemPrint("readerAliveThread: Reader is offline, try to reconnect");
                            readerOffline = true;
                            changeReaderStatus(READER_OFFLINE);
                            try {
                                if (connection != null && !connection.isClosed()) {
                                    systemPrint("connection OPEN, CLOSING");
                                    connection.close();
                                    Thread.sleep(1000);
                                }
                            } catch (Exception e) {
                                systemPrint("Prepare to reconnect, can't close socket: " + e);
                                sendLogMessage(ERROR, "createAndStartThreads-readerAliveThread: Can't close socket.\n"
                                        + ExceptionUtils.getStackTrace(e));
                            }
                            connectAndSetupReader();
                        }
                    } catch (Exception e) {
                        systemPrint("readerAliveThread interrupted. ");
                        e.printStackTrace();
                        sendLogMessage(ERROR, "createAndStartThreads-readerAliveThread: Unexpected error.\n"
                                + ExceptionUtils.getStackTrace(e));
                    }
                }
            });
            readerAliveThread.start();
        }
    }

    /**
     * This method causes the calling thread to sleep for a specified number of milliseconds
     * @param ms
     */
    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
            sendLogMessage(ERROR, "pause: Unexpected error.\n"+ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Send an LLRP message to the reader
     *
     * @param msg - Message to be sent
     * @param message - Message name for logging
     */
    private void write(LLRPMessage msg, String message) {
        try {
            systemPrint("\nSending message: " + message);
            sendLogMessage(DEBUG, "Sending Reader: " + message);
            //systemPrint(" Sending message: \n" + msg.toXMLString());
            out.write(msg.encodeBinary());
        } catch (IOException e) {
            systemPrint("Couldn't send Command "+ e);
            sendLogMessage(ERROR, "write: Couldn't send Command\n"+ExceptionUtils.getStackTrace(e));
        } catch (InvalidLLRPMessageException e) {
            systemPrint("Couldn't send Command "+ e);
            sendLogMessage(ERROR, "write: Couldn't send Command\n"+ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * This reads LLRP messages on a separate thread and stores
     * Tag Data reports in one queue and other messages in another
     * message queue for processing.
     *
     */
    class ReadThread extends Thread {

        private DataInputStream inStream = null;				 // Data from the reader
        private Socket socket = null;							 // Socket connection to the reader

        /**
         * Thread used to stream data from the reader
         *
         * @param socket stream reader input
         */
        public ReadThread(Socket socket) {
            this.socket = socket;
            try {
                systemPrint("\nAttempt to connect to DataInputStream ...");
                this.inStream = new DataInputStream(socket.getInputStream());
                systemPrint("\nDataInputStream connection made!");
            } catch (IOException e) {
                systemPrint("Cannot get input stream: " + e);
                sendLogMessage(ERROR, "ReadThread: Cannot get input stream:\n"+ExceptionUtils.getStackTrace(e));
            }
        }

        @Override
        public void run() {
            super.run();
            systemPrint("ReadThread starting");
            if (socket != null && socket.isConnected()) {
                while (socket != null && !socket.isClosed()) {
                    LLRPMessage message = null;
                    try {
                        message = read();
                        if (message != null) {
                            String msgName = message.getName();
							if (msgName.equalsIgnoreCase("CLOSE_CONNECTION_RESPONSE") ) {
								systemPrint("\nConnection Closed.");
								socket.close();
                                sendLogMessage(WARN, "ReadThread-run: Connection Closed.");
							}
                            // Add all "RO_ACCESS_REPORT" messages to the tag collection queue
                            if (msgName == "RO_ACCESS_REPORT") {
                                RO_ACCESS_REPORT roAccessRpt = (RO_ACCESS_REPORT) message;
                                List<TagReportData> tagDataList = roAccessRpt.getTagReportDataList();
                                if (!tagDataList.isEmpty())
                                    tagQueue.put(message);
                            } else {
                                queue.put(message);
                            }
                        } else {
                            systemPrint("\nMessage is null, Connection Closed.");
                            socket.close();
                            sendLogMessage(WARN, "ReadThread-run: Message is null, Connection Closed:");
                        }

                    } catch (IOException | InvalidLLRPMessageException | InterruptedException e) {
                        systemPrint("Error reading message: "+ e);
                        e.printStackTrace();
                        sendLogMessage(ERROR, "ReadThread-run: Error reading message:\n"+ExceptionUtils.getStackTrace(e));
                        break;
                    }
                }
            }
            systemPrint("ReadThread closed.   (msgQueue=" + queue.size()
                    + "\ttagQueue="+ tagQueue.size()+")");

        }

        /**
         * Read everything from the stream until the socket is closed
         *
         * @throws IOException, InvalidLLRPMessageException
         */
        public LLRPMessage read() throws IOException, InvalidLLRPMessageException {

            LLRPMessage m = null;
            // message header
            byte[] first = new byte[6];

            // complete message
            byte[] msg;

            // Read in the message header. If -1 is read, there is no more
            // data available, so close the socket
            if (inStream.read(first, 0, 6) == -1) { // read is blocking until data is available, EOF, or exception thrown
                return null;
            }
            int msgLength = 0;

            try {
                // calculate message length
                msgLength = calculateLLRPMessageLength(first);
            } catch (IllegalArgumentException e) {
                throw new IOException("Incorrect Message Length");
            }

            /*
             * the rest of bytes of the message will be stored in here before
             * they are put in the accumulator. If the message is short, all
             * messageLength-6 bytes will be read in here at once. If it is
             * long, the data might not be available on the socket all at once,
             * so it make take a couple of iterations to read in all the bytes
             */
            byte[] temp = new byte[msgLength - 6]; // msgLength includes header

            // all the rest of the bytes will be put into the accumulator
            ArrayList<Byte> accumulator = new ArrayList<Byte>();

            // add the first six bytes to the accumulator so that it will
            // contain all the bytes at the end
            for (byte b : first) {
                accumulator.add(b);
            }

            // the number of bytes read on the last call to read()
            int numBytesRead = 0;

            // read from the input stream and put bytes into the accumulator
            // while there are still bytes left to read on the socket and
            // the entire message has not been read
            while (((msgLength - accumulator.size()) != 0)
                    && numBytesRead != -1) {

                numBytesRead = inStream.read(temp, 0, msgLength
                        - accumulator.size());

                for (int i = 0; i < numBytesRead; i++) {
                    accumulator.add(temp[i]);
                }
            }

            if ((msgLength - accumulator.size()) != 0) {
                throw new IOException("Error: Discrepency between message size"
                        + " in header and actual number of bytes read");
            }

            msg = new byte[msgLength];

            // copy all bytes in the accumulator to the msg byte array
            for (int i = 0; i < accumulator.size(); i++) {
                msg[i] = accumulator.get(i);
            }

            // turn the byte array into an LLRP Message Object
            m = LLRPMessageFactory.createLLRPMessage(msg);
            return m;
        }

        /**
         * Send in the first 6 bytes of an LLRP Message
         *    Reserved(3 Bits)
         *    Version (3 Bits)
         *    Message Type (10 Bits)
         *    Message Length (32 Bits)|Parameters
         *
         * @param bytes
         * @return message length from message header (byte array)
         */
        private int calculateLLRPMessageLength(byte[] bytes) throws IllegalArgumentException {
            long msgLength = 0;
            int num1 = 0;
            int num2 = 0;
            int num3 = 0;
            int num4 = 0;

            num1 = ((unsignedByteToInt(bytes[2])));
            num1 = num1 << 32;
            if (num1 > 127) {
                throw new RuntimeException(
                        "Cannot construct a message greater than "
                        + "2147483647 bytes (2^31 - 1), due to the fact that there are "
                        + "no unsigned ints in java");
            }

            num2 = ((unsignedByteToInt(bytes[3])));
            num2 = num2 << 16;

            num3 = ((unsignedByteToInt(bytes[4])));
            num3 = num3 << 8;

            num4 = (unsignedByteToInt(bytes[5]));

            msgLength = num1 + num2 + num3 + num4;

            if (msgLength < 0) {
                throw new IllegalArgumentException(
                        "LLRP message length is less than 0");
            } else {
                return (int) msgLength;
            }
        }

        /**
         * From http://www.rgagnon.com/javadetails/java-0026.html
         *
         * @param b
         * @return unsigned java byte (java bytes are signed by default)
         */
        private int unsignedByteToInt(byte b) {
            return (int) b & 0xFF;
        }

    }  // End of the ReaderThread class

    /**
     * Receive the next tag data message from the queue, blocks if no message in queue.
     *
     * @return returns the message or blocks for message
     */
    private LLRPMessage getNextTagMessage() {
        LLRPMessage msg = null;
        try {
            msg = tagQueue.take();
        } catch (InterruptedException e) {
        }
        return msg;
    }

    /**
     * Receive the next message from the queue, blocks if no message in queue.
     *
     * @return returns the message or blocks for message
     */
    private LLRPMessage getNextMessage() {
        LLRPMessage msg = null;
        try {
            msg = queue.take();
        } catch (InterruptedException e) {
        }
        return msg;
    }

    /**
     * Receive the next 'specific' message from the queue, blocks if no message in queue.
     * Ignore all other messages until desired message found
     *
     * @return returns the message or blocks for message
     */
    private LLRPMessage getNextMessage(String msgName) {
        LLRPMessage msg = null;
        Boolean connectionMsg = false;
        systemPrint("getNextMessage: " + msgName + "\t (Queue="+queue.size()+")");
        try {
            // Connection Attempt is an Event Type for the READER_EVENT_NOTIFICATION message
            // Though there are other responses returned for READER_EVENT_NOTIFICATION, so
            // if looking for a CONNECTION_ATTEMPT, then only return the message when a
            // READER_EVENT_NOTIFICATION is received with a ConnectionAttemptEvent property
            if (msgName.equalsIgnoreCase("CONNECTION_ATTEMPT")) {
                connectionMsg = true;
                msgName = "READER_EVENT_NOTIFICATION";
            }
            while (true) {
                msg = queue.take();
                // Check if it is the desired message
                String currentName = msg.getName();
                if (currentName.equalsIgnoreCase(msgName)) {
                    // If waiting on a CONNECTION_ATTEMPT, verify READER_EVENT_NOTIFICATION
                    // is the right type
                    if (connectionMsg) {
                        READER_EVENT_NOTIFICATION readerEventNotification = (READER_EVENT_NOTIFICATION) msg;
                        ReaderEventNotificationData eventData = readerEventNotification
                                .getReaderEventNotificationData();
                        if (eventData.getConnectionAttemptEvent() != null)
                            break;
                    } else
                        break;
                }
            }
        } catch (InterruptedException e) {
        }
        return msg;
    }

    /**
     * Process regular queue messages
     */
    private void processRegularMessage(LLRPMessage msg) {
        if (!msg.getName().equalsIgnoreCase("READER_EVENT_NOTIFICATION"))
            systemPrint("processRegularMessage:" + msg.getName());
        try {

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            InputStream stream = new ByteArrayInputStream(msg.toXMLString().getBytes(StandardCharsets.UTF_8));
            Document doc = dBuilder.parse(stream);
            doc.getDocumentElement().normalize();

            String msgType = doc.getDocumentElement().getNodeName().substring(5);
            NodeList nList = null;
            Element eElement = null;

            switch (msgType) {
                case "KEEPALIVE":
                    // Send the message to the reader for each KEEPALIVE, otherwise it closes connection
                    KEEPALIVE_ACK keepaliveAck = new KEEPALIVE_ACK();
                    write(keepaliveAck, "KEEPALIVE_ACK");
                    // Update last time we heard from reader
                    lastKeepAlive = Instant.now();
                    break;

                case "READER_EVENT_NOTIFICATION":
                    /**
                     * Uncomment for debugging, though every (tagReadInterval) milliseconds a message is received.
                     * /
                    nList = doc.getDocumentElement().getElementsByTagName("llrp:ReaderEventNotificationData");

                    eElement = (Element) nList.item(0);
                    String timestamp = eElement.getElementsByTagName("llrp:UTCTimestamp").item(0).getTextContent().trim();
                    Node sibling = nList.item(0).getFirstChild().getNextSibling().getNextSibling();
                    while (sibling != null) {
                        if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                            // get node name and value - only go 1 deep for children
                            systemPrint("\nMessage Type: " + msgType + "-" + sibling.getNodeName().substring(5));
                            systemPrint("  UTCTimestamp -> " + timestamp);
                            if (sibling.hasChildNodes()) {
                                NodeList childList = sibling.getChildNodes();
                                for (int i=0; i<childList.getLength(); i++) {
                                    if (childList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                                        systemPrint("  " + childList.item(i).getNodeName().substring(5) + "-> "
                                                            + childList.item(i).getTextContent().trim());
                                    }
                                }
                            }// else
                            //	systemPrint("Node Value =" + sibling.getTextContent().trim());

                        }
                        sibling = sibling.getNextSibling();
                    }*/
                    break;

                case "GET_READER_CONFIG_RESPONSE":
                    systemPrint("Received Message: ("+msgType+") /n" + msg.toXMLString());
                    break;
                default:
                    nList = doc.getDocumentElement().getElementsByTagName("llrp:LLRPStatus");
                    Boolean found = false;
                    for ( int i = 0; i < nList.getLength(); i++ ) {
                        //systemPrint("\nCurrent Element :" + i + ": " + nList.item(i).getNodeName());
                        if ( nList.item(i).getNodeType() == Node.ELEMENT_NODE ) {
                            found = true;
                            eElement = (Element) nList.item(i);
                            String statusCode = eElement.getElementsByTagName("llrp:StatusCode").item(0).getTextContent().trim();

                            systemPrint("\nMessage Type: " + msgType + " ->" + statusCode + "<");
                            if (!statusCode.equalsIgnoreCase("M_Success") ) {
                                systemPrint("  ErrorDescription -> "
                                        + eElement.getElementsByTagName("llrp:ErrorDescription").item(0).getTextContent().trim());
                            }
                            break;
                        }
                    }
                    if (!found)
                        systemPrint("\nMessage Type: " + msgType);
                    break;
            }

        } catch (Exception e) {
        }
    }

    /**
     * Process tag queue messages
     */
    private void processTagDataMessage(LLRPMessage msg) {

        if (!msg.getName().equalsIgnoreCase("RO_ACCESS_REPORT"))
            systemPrint("processTagDataMessage: ERROR expecting 'RO_ACCESS_REPORT', got " + msg.getName());
        else {
            RO_ACCESS_REPORT roAccessRpt = (RO_ACCESS_REPORT) msg;
            List<TagReportData> tagDataList = roAccessRpt.getTagReportDataList();
            if (tagDataList.isEmpty())
                systemPrint("processTagDataMessage: 'RO_ACCESS_REPORT' msg has NO Tag Data");
            else {
                Map<String, Object> tagMessage = new HashMap<>();
                List<Map<String, Object>> tagList = new ArrayList<Map<String, Object>>();
                tagMessage.put("eventType", TAG_DATA_EVENT_NAME);
                tagMessage.put("readerId",  readerId);  // Get IDENTIFICATION for Reader Config
                for (TagReportData tagData: tagDataList) {
                    Map<String, Object> tagObj = new HashMap<>();
                    String epc = tagData.getEPCParameter().toString();
                    tagObj.put("tagId", epc.substring(epc.indexOf("ePC: ")+5));
                    tagObj.put("antennaID", tagData.getAntennaID().getAntennaID().intValue());
                    tagObj.put("firstSeenTimestampUTC", tagData.getFirstSeenTimestampUTC().getMicroseconds().toLong());
                    tagObj.put("lastSeenTimestampUTC", tagData.getLastSeenTimestampUTC().getMicroseconds().toLong());
                    tagObj.put("tagSeenCount", tagData.getTagSeenCount().getTagCount().intValue());
                    tagObj.put("peakRSSI", tagData.getPeakRSSI().getPeakRSSI().intValue());

                    // misc properties not used by Vantiq
                    tagObj.put("accessSpecID", tagData.getAccessSpecID().getAccessSpecID().intValue());
                    tagObj.put("accessSpecIDName", tagData.getAccessSpecID().getName());
                    tagObj.put("ROSpecID", tagData.getROSpecID().getROSpecID().intValue());
                    tagObj.put("ROSpecIDName", tagData.getROSpecID().getName());
                    tagObj.put("channelIndex", tagData.getChannelIndex().getChannelIndex().intValue());
                    tagObj.put("specIndex", tagData.getSpecIndex().getSpecIndex().intValue());

                    tagList.add(tagObj);
                }
                tagMessage.put("tags",  tagList);

                // Send the tag data back to VANTIQ
                this.llrpCore.sendMessage(tagMessage);
            }
        }
    }

    /**
     * Stop reading tags and close the connection.  Called when Vantiq Connection
     * goes down.
     * This method will also delete the ROSpec in the reader so it stops reading.
     *
     */
    public void close() {

        vantiqSourceOffline = true;

        if (!readerOffline) {
            systemPrint("*** CLOSE LLRP PROCEDURE CALLED ****");
            sendLogMessage(DEBUG, "** Closing connection to the Reader **");

            // Send a STOP_ROSPEC Message to set ROSpec to InActive state
            STOP_ROSPEC stopROSpec = new STOP_ROSPEC();
            stopROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
            write(stopROSpec, "STOP_ROSPEC");
            pause(200);

            // Send a DISABLE_ROSPEC Message to set ROSpec to Disabled state
            DISABLE_ROSPEC disableROSpec = new DISABLE_ROSPEC();
            disableROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
            write(disableROSpec, "DISABLE_ROSPEC");
            pause(200);

            // Send a DELETE_ROSPEC Message to set remove ROSpec
            DELETE_ROSPEC deleteROSpec = new DELETE_ROSPEC();
            deleteROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
            write(deleteROSpec, "DELETE_ROSPEC");
            pause(200);

            // Not sure if needed, but pause before shutting down
            pause(500);

            // Send a CLOSE_CONNECTION to with the reader
            CLOSE_CONNECTION cc = new CLOSE_CONNECTION();
            write(cc, "CLOSE_CONNECTION");

            // Wait to receive the CLOSE_CONNECTION_RESPONSE which closes the thread
            synchronized (rt) {
                try {
                    systemPrint("\nWaiting for the Reader response of closure...");
                    rt.wait();
                } catch (InterruptedException e) {
                    systemPrint("\nProcess interupted, aborting...");
                }
            }

            // set reader to Offline
            readerOffline = true;
        }

    }

    /**
     * Notification that the connection to the Vantiq Source is either online.
     *
     */
    public void vantiqSourceConnectionOnline() {
        vantiqSourceOffline = false;
    }

    /**
     * Notification that the connection to the Vantiq Source is either offline.
     *
     */
    public void vantiqSourceConnectionOffline() {
        vantiqSourceOffline = true;
    }

    /**
     * Change the status of the Reader and send a message to Vantiq if logging.
     *
     * @param offlineStatus Indicates if reader is offline (true) or online (false)
     */
    public void changeReaderStatus(Boolean offlineStatus) {

        readerOffline = offlineStatus;

        // send a formatted data message to Vantiq
        Map<String, Object> vantiqMessage = new HashMap<>();
        vantiqMessage.put("eventType", READER_STATUS_EVENT_NAME);
        vantiqMessage.put("readerId", readerId);
        vantiqMessage.put("readerOnline", !readerOffline);
        this.llrpCore.sendMessage(vantiqMessage);

        if (readerOffline)
            sendLogMessage(INFO, "Reader is OFFLINE");
        else
            sendLogMessage(INFO, "Reader is ONLINE");

    }

    /**
     * Check the response for the LLRPStatus
     *
     * @param status    The Response message to interrogate for the LLRPStatus
     * @return String   Either "Success" or "(StatusCode): (ErrorDescription)"
     */
    private String getLLRPStatus(LLRPStatus status) {

        if (status == null) {
            return "Invalid Status: null";
        } else if (status.getStatusCode().toString() == M_SUCCESS) {
            return "Success";
        } else {
            return status.getStatusCode().toString()+": "+status.getErrorDescription();
        }
    }

    /**
     * Method used to include the hostname as a prefix to normally a
     * System.out.println message.  Since there could be multiple sources
     * connected to the same connector, it helps with readability of the logs.
     */
    private void systemPrint(String msg) {
        if (msg.startsWith("\n"))
            System.out.println("\n"+hostname+": "+msg.substring(1));
        else
            System.out.println(hostname+": "+msg);
    }

    /**
     * Method used to send log messages to Vantiq server.
     */
    private void sendLogMessage(String logLevel, String msg) {

        if (LogLevels.indexOf(logLevel) <= this.logLevel) {
            Map<String, Object> vantiqMessage = new HashMap<>();
            vantiqMessage.put("eventType", logLevel+"Log");
            vantiqMessage.put("sourceName", sourceName);
            vantiqMessage.put("hostname", hostname);
            vantiqMessage.put("readerId", readerId);
            vantiqMessage.put("msg", msg);
            this.llrpCore.sendMessage(vantiqMessage);
        }
    }

    /**
     * Method used to throw the VantiqLLRPException whenever is necessary
     * @param e The Exception caught by the calling method
     * @throws VantiqLLRPException
     */
    public void reportLLRPError(Exception e) throws VantiqLLRPException {
        String message = this.getClass().getCanonicalName() + ": " + e.getMessage() +
                "\n" + e.getStackTrace();
        sendLogMessage(ERROR, message);
        throw new VantiqLLRPException(message);
    }

}