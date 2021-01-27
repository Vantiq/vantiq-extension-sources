/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */
package io.vantiq.extsrc.CSVSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;

/**
 * Class responsible for the conversion of a line to a relevant json buffer .
 * its assign the name of the json attributes based on the schema object by
 * compering the offset of the field in the line to the value of the schema
 * field attributes name ( field0 , field1 etc). if it doesn't find any match it
 * just used those default vakues as the attribute name in the new jsn buffer .
 * 
 * each file contnet can be sent to Vatiq using multiple messages , based on the
 * numLinesInEvent
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
    static void sendNotification(String filename, int numPacket, ArrayList<Map<String, String>> file,
            ExtensionWebSocketClient oClient) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", filename);
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
     * @param i      - the current index to be assigend
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
        return recordSize + 1;// CR+LF
    }

    /**
     * Responsible for reading the lines from the file and converting it events to
     * be sent to server. Each line contains fixed record and then, based on the
     * schema object, determine the attribute name.
     * 
     * @param csvFile
     * @param config
     * @param oClient
     * @return
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    static public ArrayList<Map<String, String>> executeFixedRecord(String csvFile, Map<String, Object> config,
            ExtensionWebSocketClient oClient) throws InterruptedException {

        int numOfRecords; // This is the total number of records/lines processed from the file.
        int packetIndex = 0;
        Map<String, Map<String, String>> schema = null;
        Map<String, FixedRecordfieldInfo> recordMetaData = null;

        if (config.get("schema") != null) {
            schema = (Map<String, Map<String, String>>) config.get("schema");
        }

        recordMetaData = fixedRecord(schema);
        int recordSize = fixedRecordLength(recordMetaData);

        int MaxLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);

        int SleepBetweenPackets = 0;
        if (config.get("waitBetweenTx") != null)
            SleepBetweenPackets = (int) config.get("waitBetweenTx");

        ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();

        try (RandomAccessFile f = new RandomAccessFile(csvFile, "r")) {
            numOfRecords = 0;
            byte[] tempBuffer = new byte[recordSize];
            long fl = f.length();
            long currOffset = 0;

            Set<String> fieldList = recordMetaData.keySet();

            while (currOffset < fl) {
                f.read(tempBuffer);

                currOffset += recordSize;

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
                        sb.append(t);
                        sb = sb.reverse();
                        t = sb.toString();
                    }

                    lineValues.put(key, t);
                }

                file.add(lineValues);

                numOfRecords++;
                f.seek(recordSize * numOfRecords);

                if (file.size() >= MaxLinesInEvent) {
                    log.info("TX Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                            numOfRecords);
                    sendNotification(csvFile, packetIndex, file, oClient);
                    Thread.sleep(SleepBetweenPackets);
                    file = new ArrayList<Map<String, String>>();
                    packetIndex++;
                }
            }
            if (file.size() > 0) {
                log.info("TX Last Packet Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                        numOfRecords);
                sendNotification(csvFile, packetIndex, file, oClient);
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
    static public ArrayList<Map<String, String>> execute(String csvFile, Map<String, Object> config,
            ExtensionWebSocketClient oClient) {
        String line = "";
        int numOfRecords; // This is the total number of records/lines processed from the file.
        int packetIndex = 0;
        Map<String, String> schema = null;

        if (config.get("schema") != null) {
            schema = (Map<String, String>) config.get("schema");
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
                        log.info("TX Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                                numOfRecords);
                        sendNotification(csvFile, packetIndex, file, oClient);
                        file = new ArrayList<Map<String, String>>();
                        packetIndex++;
                    }
                } else {
                    skipFirstLine = false;
                }
            }
            if (file.size() > 0) {
                log.info("TX Last Packet Packet {} Size {} Total num of Records {}", packetIndex, MaxLinesInEvent,
                        numOfRecords);

                sendNotification(csvFile, packetIndex, file, oClient);
            }
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}