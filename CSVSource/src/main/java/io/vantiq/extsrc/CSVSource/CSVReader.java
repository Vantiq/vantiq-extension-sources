/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */
package io.vantiq.extsrc.CSVSource;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.ui.Console;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.CSVSource.exception.VantiqCSVException;

/**
 * Class responsible for the conversion of a line to a relevant json buffer . It
 * assigns the name of the json attributes based on the schema object by
 * comparing the offset of the field in the line to the value of the schema
 * field attributes name ( field0, field1, etc). if it doesn't find any match
 * (use those default values as the attribute name in the new VAIL object).
 * 
 * Each file's content can be sent to Vantiq using multiple messages, based on
 * `numLinesInEvent`
 */
public class CSVReader {
    private static final String MAX_LINES_IN_EVENT = "maxLinesInEvent";
    public static ArrayList segmentList = new ArrayList<>();
    static final Logger log = LoggerFactory.getLogger(CSVMain.class);

    /**
     * Send message containing the segment of event t
     * 
     * @param filename  - the original filename , for possible processing in the
     *                  Server
     * @param numPacket - number of events being sent to the Server while processing
     *                  the current file.
     * @param file      - the list of events to be sent .
     * @param oClient
     */
    static void sendNotification(String filename, String type, int numPacket, ArrayList<Map<String, String>> file,
            ExtensionWebSocketClient oClient) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", filename);
        m.put("type", type);
        m.put("segment", numPacket);
        m.put("lines", file);
        if (oClient != null) {
            oClient.sendNotification(m);
        } else {
            segmentList.add(m); // this for auto testing only , will not allocate space in production
        }
    }

    /**
     * Return the attribute to be used for the current field based on the index of
     * the field in the line in case no match it used the default attribute "FieldX"
     * where X is the field index
     * 
     * @param i      - the current index to be assigned
     * @param schema = schema object
     * @return
     */
    static String setFieldName(int i, Map<String, String> schema) {
        String field = String.format("field%d", i);
        if (schema != null) {
            if (schema.get(field) != null) {
                field = schema.get(field);
            }
        }
        return field;
    }

    static Map<String, FixedRecordfieldInfo> fixedRecord(Map<String, Map<String, String>> schema) {

        Map<String, FixedRecordfieldInfo> recordInfo = new HashMap<String, FixedRecordfieldInfo>();
        Set<String> fieldList = schema.keySet();
        int recordSize = 0;

        for (String field : fieldList) {
            Map<String, String> fieldInf = schema.get(field);
            FixedRecordfieldInfo o = FixedRecordfieldInfo.Create(fieldInf);

            recordInfo.put(field, o);

        }
        return recordInfo;
    }

    static int fixedRecordLength(Map<String, FixedRecordfieldInfo> schema) {

        Set<String> fieldList = schema.keySet();
        int recordSize = 0;

        for (String field : fieldList) {
            FixedRecordfieldInfo o = schema.get(field);

            if (recordSize < o.offset + o.length) {
                recordSize = o.offset + o.length;
            }

        }
        return recordSize;// + 1;// CR+LF
    }

    /**
     * Responsible for reading records from the file and converting to events to be
     * sent to server. Each record is a fixed record and, based on the schema
     * object, we extract the field.
     * 
     * @param csvFile
     * @param config
     * @param oClient
     * @return
     * @throws InterruptedException
     * @throws VantiqCSVException
     */
    // @SuppressWarnings("unchecked")
    static public ArrayList<Map<String, String>> executeXMLFile(String csvFile, Map<String, Object> config,
            ExtensionWebSocketClient oClient) throws InterruptedException, VantiqCSVException {

        int packetIndex = 0;

        boolean extendedLogging = false;
        if (config.get("extendedLogging") != null) {
            extendedLogging = Boolean.parseBoolean(config.get("extendedLogging").toString());
        }
        try {
            String xmlString = "";
            // give time to writer to close the file - FTP should be merged with CSV
            // extension source.
            Thread.sleep(100);

            long fileSize = Files.size(Paths.get(csvFile));
            log.info("file {} attempt:{} fileSize {}", csvFile, 0, fileSize);
            for (int t = 0; t < 10 && fileSize < 10; t++) {
                Thread.sleep(100);
                fileSize = Files.size(Paths.get(csvFile));
                log.info("file {} attempt:{} fileSize {}", csvFile, t, fileSize);
            }

            xmlString = new String(Files.readAllBytes(Paths.get(csvFile)));

            try {
                JSONObject json = XML.toJSONObject(xmlString); // converts xml to json
                String jsonPrettyPrintString = json.toString(); // json pretty print
                if (extendedLogging) {
                    log.info(jsonPrettyPrintString);
                }

                ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();
                Map<String, String> lineValues = new HashMap<String, String>();
                lineValues.put("xml", jsonPrettyPrintString);
                file.add(lineValues);

                sendNotification(csvFile, "XML", packetIndex, file, oClient);
            } catch (JSONException je) {
                log.error("Convert2Json failed", je);
            }
        } catch (IOException iex) {

        }

        return null;
    }

    /**
     * Responsible for reading records from the file and converting to events to be
     * sent to server. Each record is a fixed record and, based on the schema
     * object, we extract the field.
     * 
     * @param csvFile
     * @param config
     * @param oClient
     * @return
     * @throws InterruptedException
     * @throws VantiqCSVException
     */
    @SuppressWarnings("unchecked")
    static public ArrayList<Map<String, String>> executeFixedRecord(String csvFile, Map<String, Object> config,
            ExtensionWebSocketClient oClient) throws InterruptedException, VantiqCSVException {

        int numOfRecords; // This is the total number of records/lines processed from the file.
        int packetIndex = 0;
        Map<String, Map<String, String>> schema = null;
        Map<String, FixedRecordfieldInfo> recordMetaData = null;

        if (config.get("schema") != null) {
            schema = (Map<String, Map<String, String>>) config.get("schema");
        }

        recordMetaData = fixedRecord(schema);

        int calculatedRecordSize = fixedRecordLength(recordMetaData);
        int recordSize = 0;
        if (config.get("fixedRecordSize") != null) {
            recordSize = (int) config.get("fixedRecordSize");
            if (calculatedRecordSize > recordSize) {
                String s = String.format("Calculated Record length (%d) is higher then fixedRecordSize value (%d)",
                        calculatedRecordSize, recordSize);
                log.error(s);
                throw new VantiqCSVException(s);
            }

        } else {
            String s = String.format(
                    "fixedRecordSize must be set (minimum to size (%d),  Make certain  to include EOL characters",
                    calculatedRecordSize);
            log.error(s);
            throw new VantiqCSVException(s);
        }

        boolean extendedLogging = false;
        if (config.get("extendedLogging") != null) {
            extendedLogging = Boolean.parseBoolean(config.get("extendedLogging").toString());
        }

        int MaxLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);

        int SleepBetweenPackets = 0;
        if (config.get("waitBetweenTx") != null) {
            SleepBetweenPackets = (int) config.get("waitBetweenTx");
        }

        ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();
        Set<String> fieldList = recordMetaData.keySet();

        try (InputStream inputStream = new FileInputStream(csvFile)) {
            numOfRecords = 0;
            byte[] tempBuffer = new byte[recordSize];
            int len = inputStream.read(tempBuffer);
            while (len == recordSize) {
                Map<String, String> lineValues = new HashMap<String, String>();

                for (String key : fieldList) {

                    FixedRecordfieldInfo o = recordMetaData.get(key);
                    String t;
                    if (o.charSet != null) {
                        t = new String(tempBuffer, o.offset, o.length, o.charSet).trim();
                    } else {
                        t = new String(tempBuffer, o.offset, o.length).trim();
                    }

                    if (o.reversed) {

                        StringBuilder sb = new StringBuilder();
                        sb.append(RTLReverse.rToLNumberRepair(t));
                        sb = sb.reverse();
                        t = sb.toString();
                    }

                    lineValues.put(key, t);
                }

                file.add(lineValues);

                numOfRecords++;

                if (file.size() >= MaxLinesInEvent) {
                    if (extendedLogging) {
                        log.info("TX Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                                numOfRecords);
                    }
                    sendNotification(csvFile, "FixedLength", packetIndex, file, oClient);
                    if (SleepBetweenPackets > 0) {
                        Thread.sleep(SleepBetweenPackets);
                    }
                    file = new ArrayList<Map<String, String>>();
                    packetIndex++;
                }
                Arrays.fill(tempBuffer, (byte) 0);
                len = inputStream.read(tempBuffer);

            }
            if (file.size() > 0) {
                if (extendedLogging) {
                    log.info("TX Last Packet Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                            numOfRecords);
                }
                sendNotification(csvFile, "FixedLength", packetIndex, file, oClient);
            }
            return file;

        } catch (IOException ex) {
            log.error("executeFixedRecord - {}", ex);
        }
        return null;
    }

    /**
     * Responsible for reading the lines from the file and converting it events to
     * be sent to server. Each line is split by the delimiter and then, based on the
     * schema object, determine the attribute name.
     * 
     * @param csvFile
     * @param config
     * @param oClient
     * @return
     */
    @SuppressWarnings("unchecked")
    static public ArrayList<Map<String, String>> execute(String csvFile, Map<String, Object> config,
            ExtensionWebSocketClient oClient) {
        String line = "";
        int numOfRecords; // This is the total number of records/lines processed from the file.
        int packetIndex = 0;
        Map<String, String> schema = null;

        if (config.get("schema") != null) {
            schema = (Map<String, String>) config.get("schema");
        }

        boolean extendedLogging = false;
        if (config.get("extendedLogging") != null) {
            extendedLogging = Boolean.parseBoolean(config.get("extendedLogging").toString());
        }

        String delimiter = ",";
        if (config.get("delimiter") != null) {
            delimiter = config.get("delimiter").toString();
        }
        boolean processNullValues = false;
        if (config.get("processNullValues") != null) {
            processNullValues = Boolean.parseBoolean(config.get("processNullValues").toString());
        }

        boolean skipFirstLine = false;
        if (config.get("skipFirstLine") != null) {
            skipFirstLine = Boolean.parseBoolean(config.get("skipFirstLine").toString());
        }

        int MaxLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);
        ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            numOfRecords = 0;
            while ((line = br.readLine()) != null) {

                if (!skipFirstLine) {
                    // use comma as separator
                    String[] values = line.split(delimiter);
                    Map<String, String> lineValues = new HashMap<String, String>();

                    int schemaFieldIndex = 0;
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].length() != 0) {
                            String currField = setFieldName(schemaFieldIndex, schema);
                            lineValues.put(currField, values[i]);
                            schemaFieldIndex++;
                        } else if (processNullValues) {
                            schemaFieldIndex++;
                        }
                    }

                    file.add(lineValues);
                    numOfRecords++;

                    if (file.size() >= MaxLinesInEvent) {
                        if (extendedLogging) {
                            log.info("TX Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                                    numOfRecords);
                        }
                        sendNotification(csvFile, "Delimited", packetIndex, file, oClient);
                        file = new ArrayList<Map<String, String>>();
                        packetIndex++;
                    }
                } else {
                    skipFirstLine = false;
                }
            }
            if (file.size() > 0) {
                if (extendedLogging) {
                    log.info("TX Last Packet Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                            numOfRecords);
                }

                sendNotification(csvFile, "Delimited", packetIndex, file, oClient);
            }
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Responsible for reading the lines from the file and converting it events to
     * be sent to server. Each line is split by the delimiter and then, based on the
     * schema object, determine the attribute name.
     * 
     * @param csvFile
     * @param config
     * @param oClient
     * @return
     */
    @SuppressWarnings("unchecked")
    static public ArrayList<Map<String, String>> executeMixedSize(String csvFile, Map<String, Object> config,
            ExtensionWebSocketClient oClient) throws InterruptedException, VantiqCSVException {

        String line = "";
        int numOfRecords; // This is the total number of records/lines processed from the file.
        int packetIndex = 0;
        Map<String, Map<String, String>> schema = null;
        Map<String, FixedRecordfieldInfo> recordMetaData = null;

        if (config.get("schema") != null) {
            schema = (Map<String, Map<String, String>>) config.get("schema");
        }

        recordMetaData = fixedRecord(schema);

        int calculatedRecordSize = fixedRecordLength(recordMetaData);
        int recordSize = 0;
        if (config.get("fixedRecordSize") != null) {
            recordSize = (int) config.get("fixedRecordSize");
            if (calculatedRecordSize > recordSize) {
                String s = String.format("Calculated Record length (%d) is higher then fixedRecordSize value (%d)",
                        calculatedRecordSize, recordSize);
                log.error(s);
                throw new VantiqCSVException(s);
            }

        } else {
            String s = String.format(
                    "fixedRecordSize must be set (minimum to size (%d),  Make certain  to include EOL characters",
                    calculatedRecordSize);
            log.error(s);
            throw new VantiqCSVException(s);
        }
        int SmallRecSize = 0;
        if (config.get("shortRecordSize") != null) {
            SmallRecSize = (int) config.get("shortRecordSize");
        }

        if (SmallRecSize == 0) {
            SmallRecSize = recordSize;
            String s = String.format(
                    "shortRecordSize is not set , default would be fixedRecordSize size",
                    calculatedRecordSize);
            log.warn(s);

        }

        boolean extendedLogging = false;
        if (config.get("extendedLogging") != null) {
            extendedLogging = Boolean.parseBoolean(config.get("extendedLogging").toString());
        }

        int MaxLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);

        int SleepBetweenPackets = 0;
        if (config.get("waitBetweenTx") != null) {
            SleepBetweenPackets = (int) config.get("waitBetweenTx");
        }

        ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();
        Set<String> fieldList = recordMetaData.keySet();

        try (InputStream inputStream = new FileInputStream(csvFile)) {
            numOfRecords = 0;
            byte[] tempBuffer = new byte[recordSize];
            int len = inputStream.read(tempBuffer, 0, SmallRecSize);
            if (!(tempBuffer[len - 2] == 13 && tempBuffer[len - 1] == 10)) {
                len += inputStream.read(tempBuffer, SmallRecSize, recordSize - SmallRecSize);
            }

            while (len == recordSize || len == SmallRecSize) {
                Map<String, String> lineValues = new HashMap<String, String>();

                for (String key : fieldList) {

                    FixedRecordfieldInfo o = recordMetaData.get(key);
                    String t;
                    if (o.offset + o.length < len - 2) {
                        if (o.charSet != null) {
                            t = new String(tempBuffer, o.offset, o.length, o.charSet).trim();
                        } else {
                            t = new String(tempBuffer, o.offset, o.length).trim();
                        }

                        if (o.reversed) {

                            StringBuilder sb = new StringBuilder();
                            sb.append(RTLReverse.rToLNumberRepair(t));
                            sb = sb.reverse();
                            t = sb.toString();
                        }

                        lineValues.put(key, t);
                    }
                }

                file.add(lineValues);

                numOfRecords++;

                if (file.size() >= MaxLinesInEvent) {
                    if (extendedLogging) {
                        log.info("TX Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                                numOfRecords);
                    }
                    sendNotification(csvFile, "FixedLength", packetIndex, file, oClient);
                    if (SleepBetweenPackets > 0) {
                        Thread.sleep(SleepBetweenPackets);
                    }
                    file = new ArrayList<Map<String, String>>();
                    packetIndex++;
                }
                Arrays.fill(tempBuffer, (byte) 0);
                len = inputStream.read(tempBuffer, 0, SmallRecSize);

                if ((len == recordSize || len == SmallRecSize)) {
                    if (!(tempBuffer[len - 2] == 13 && tempBuffer[len - 1] == 10)) {
                        len += inputStream.read(tempBuffer, SmallRecSize, recordSize - SmallRecSize);
                    }
                }

            }
            if (file.size() > 0) {
                if (extendedLogging) {
                    log.info("TX Last Packet Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                            numOfRecords);
                }
                sendNotification(csvFile, "FixedLength", packetIndex, file, oClient);
            }
            return file;

        } catch (IOException ex) {
            log.error("executeFixedRecord - {}", ex);
        }
        return null;
    }

}
