/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */
package io.vantiq.extsrc.CSVSource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
/**
 * Class responsible for the conversion of a line to a relevant json buffer .
 * its assign the name of the json attributes based on the schema object by compering the offset of the field 
 * in the line to the value of the schema field attributes name ( field0 , field1 etc). 
 * if it doesn't find any match it just used those default vakues as the attribute name in the 
 * new jsn buffer . 
 * 
 * each file contnet can be sent to Vatiq using multiple messages , based on the numLinesInEvent 
 */
public class CSVReader {
    private static final String MAX_LINES_IN_EVENT = "maxLinesInEvent";
    public static ArrayList segmentList = new ArrayList<>();
    /**
     * Send message containing the segment of event t
     * @param filename - the original filename , for possible processing in the Server
     * @param numPacket - number of events being sent to the Server while processing the current file.  
     * @param file - the list of events to be sent .
     * @param oClient
     */
    static void sendNotification(String filename, int numPacket, ArrayList<Map<String,String>> file, ExtensionWebSocketClient oClient)
    {
        Map<String,Object> m = new LinkedHashMap<>();
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
     * Return the attribute to be used for the current field based on the index of the field in the line 
     * in case no match it used the default attribute "FieldX" where X is the field index
     * 
     * @param i - the current index to be assigend
     * @param schema = schema object
     * @return
     */
    static String setFieldName(int i, Map<String,String>schema)
    {
        String field = String.format("field%d", i); 
        if (schema != null) {
            if (schema.get(field)!= null) {
                field = schema.get(field) ; 
            }
        }
        return field ; 
    }

    /**
     * Responsible for reading the lines from the fike and convert to events to be sent to server 
     * each line is being splite by the delimiter and then based on the schema object , determine the 
     * attribute name. 
     * @param csvFile
     * @param config
     * @param oClient
     * @return
     */
    @SuppressWarnings("unchecked")
    static public ArrayList<Map<String,String>> execute(String csvFile, Map<String, Object> config, ExtensionWebSocketClient oClient) {
        String line = "";
        int numOfRecords ;  // This is the total number of records/lines processed from the file. 
        int packetIndex = 0 ; 
        Map<String,String> schema = null;

        if (config.get("schema") != null) {
            schema = (Map<String,String>) config.get("schema");
        }

        String delimiter = ",";
        if (config.get("delimiter") != null) {
            delimiter = config.get("delimiter").toString();
        }
        boolean processNullValues = false;
        if (config.get("processNullValues") != null) {
            processNullValues = Boolean.parseBoolean(config.get("processNullValues").toString());
        }

        int MaxLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);
        ArrayList<Map<String,String>> file = new ArrayList<Map<String,String>>(); 

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            numOfRecords = 0 ; 
            while ((line = br.readLine()) != null) {
                
                // use comma as separator
                String[] values = line.split(delimiter);
                Map<String, String> lineValues = new HashMap<String, String>(); 
                
                int schemaFieldIndex = 0 ; 
                for (int i = 0 ; i < values.length ; i++) {
                    if (values[i].length() != 0 ) {
                        String currField = setFieldName(schemaFieldIndex,schema);
                        lineValues.put(currField, values[i]);
                        schemaFieldIndex++;
                    } else if ( processNullValues) {
                        schemaFieldIndex++;
                    }
                }

                file.add(lineValues);
                numOfRecords++ ; 
                


                if (file.size() >= MaxLinesInEvent ) {
                    sendNotification(csvFile, packetIndex, file, oClient);
                    file = new ArrayList<Map<String,String>>();
                    packetIndex++;
                }
            }
            if (file.size() > 0 ) {
                sendNotification(csvFile, packetIndex, file, oClient);
            }
            return file ; 
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; 
    }

}