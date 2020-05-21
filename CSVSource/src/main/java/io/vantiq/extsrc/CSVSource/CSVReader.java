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
    static public ArrayList<Map<String,String>> execute(String csvFile, Map<String, Object>     config,   ExtensionWebSocketClient oClient  ) {

        String line = "";
        String cvsSplitBy = ";";
        int numOfRecords ; 
        int numOfLine = 0 ; 
        Map<String,String> schema = null;

        if (config.get("schema") != null)
        {
            schema = (Map<String,String>) config.get("schema");
        }


        int numLinesInEvent = (int) config.get(MAX_LINES_IN_EVENT);

        ArrayList<Map<String,String>> file = new ArrayList<Map<String,String>>(); 

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {

            numOfRecords = 0 ; 
            while ((line = br.readLine()) != null) {
                numOfLine++;
                // use comma as separator
                String[] values = line.split(cvsSplitBy);

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
                    sendNotification(csvFile,numOfLine,file,oClient);
                    numLinesInEvent = 0 ; 
                        
                }

            }

            if (numLinesInEvent > 0 )
            {
                sendNotification(csvFile,numOfLine,file,oClient);
                numLinesInEvent = 0 ; 
                    
            }

            return file ; 

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null; 

    }

}