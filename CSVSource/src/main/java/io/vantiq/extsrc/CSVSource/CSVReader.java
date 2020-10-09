package io.vantiq.extsrc.CSVSource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


import io.vantiq.extjsdk.ExtensionWebSocketClient;

public class CSVReader {
    private static final String MAX_LINES_IN_EVENT = "maxLinesInEvent";


    static void sendNotification(String filename, int numPacket , ArrayList<Map<String,String>> file ,   ExtensionWebSocketClient oClient )
    {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("file", filename);
        m.put("segment", numPacket);
        m.put("lines", file.toArray());
        
        
        oClient.sendNotification(m);
    }
    static String setFieldName(int i , Map<String,String>schema)
    {
        String field = String.format("field%d",i); 
        if (schema != null)
        {
            if (schema.get(field)!= null)
            {
                field = schema.get(field) ; 
            }
        }

        return field ; 
    }
    @SuppressWarnings("unchecked")
    static public ArrayList<Map<String,String>> execute(String csvFile,  Map<String, Object>     config,   ExtensionWebSocketClient oClient  ) {

        String line = "";
        int numOfRecords ; 
        int numOfProcessedLinesInSegment = 0 ; 
        Map<String,String> schema = null;

        if (config.get("schema") != null)
        {
            schema = (Map<String,String>) config.get("schema");
        }

        String delimiter = ";";
        if (config.get("delimiter") != null)
        {
            delimiter = config.get("delimiter").toString();
        }


        int numLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);

        ArrayList<Map<String,String>> file = new ArrayList<Map<String,String>>(); 

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {

            numOfRecords = 0 ; 
            while ((line = br.readLine()) != null) {
                numOfProcessedLinesInSegment++;
                // use comma as separator
                String[] values = line.split(delimiter);

                Map<String, String> lineValues = new HashMap<String, String>(); 
                
                for (int i = 0 ; i < values.length ; i++)
                {
                    String currField = setFieldName(i,schema);
                    lineValues.put(currField, values[i]);
                }

                file.add(lineValues);
                numOfRecords++ ; 

                if (numOfRecords >= numLinesInEvent)
                {
                    //TODO : this is for testing logic. could have been smarter...
                    if (oClient != null) 
                        sendNotification(csvFile, numOfProcessedLinesInSegment, file, oClient);
                    else
                        return file; 
                    numLinesInEvent = 0 ; 
                        
                }

            }

            if (numLinesInEvent > 0 )
            {
                if (oClient != null) sendNotification(csvFile, numOfProcessedLinesInSegment, file, oClient);
                numLinesInEvent = 0 ; 
                    
            }

            return file ; 

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null; 

    }

}