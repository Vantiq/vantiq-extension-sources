package io.vantiq.extsrc.CSVSource;

import java.util.Map;

public class FixedRecordfieldInfo {
    int offset ; 
    int length ; 
    String type ; 
    String charSet;
    boolean reversed ; 


    public static FixedRecordfieldInfo Create(Map<String,String> field){
        FixedRecordfieldInfo o = new FixedRecordfieldInfo(); 

        Object v1 = field.get("offset");
        o.offset = Integer.parseInt(v1.toString());
        Object v2 = field.get("length");
        o.length = Integer.parseInt(v2.toString());
        o.type = field.get("type");


        o.charSet = field.get("charset") ;
        Object v3 = field.get("reversed") ;
        if (v3 != null) {
            o.reversed = Boolean.parseBoolean(v3.toString());
        } else {
            o.reversed = false; 
        }
        
        return o ; 
    }
    
}
