package io.vantiq.extsrc.CSVSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RTLReverse {

    //Any series of 1-2 digits followed by a colon followed by 1-2 digits
    private static Pattern timePat = Pattern.compile("\\d{1,2}:\\d{1,2}");
    
    /// <summary>
    ///  Regular expression built for C# on: Tue, Nov 22, 2011, 07:58:22 PM
    ///  Using Expresso Version: 3.0.4334, http://www.ultrapico.com
    ///  
    ///  A description of the regular expression:
    ///  
    ///  Match a prefix but exclude it from the capture. [[^:]|^]
    ///      Select from 2 alternatives
    ///          Any character that is NOT in this class: [:]
    ///          Beginning of line or string
    ///  Any digit, at least 2 repetitions
    ///  Match a suffix but exclude it from the capture. [[^:]|$]
    ///      Select from 2 alternatives
    ///          Any character that is NOT in this class: [:]
    ///          End of line or string
    ///  
    private static Pattern longNumber = Pattern.compile("(?<=[^:]|^)\\d{2,}(?=[^:]|$)");

    public static String rToLNumberRepair(String input){
        return reverseIt(reverseIt(input, timePat), longNumber);
    }
    
    private static String reverseIt(String input, Pattern p){
        int position = 0;
        int length = input.length();
        String newString = "";
        
        Matcher matcher = p.matcher(input);
        
        while(matcher.find()){
            int start = matcher.start();
            int end = matcher.end();
            
            //Copy the content before the match so long as there is content (start > 0)
            if(start > 0)
                newString += input.substring(position, start);
            
            /*
             * Reverse the time found, for example:
             * Given the time 17:03, android incorrectly writes this as 30:71
             */
            for(int i = end - 1; i >= start; i--){
                newString += input.charAt(i);
            }
            
            //Update position
            position = end;
        }
        
        if(position < length)
            newString += input.substring(position, length);
        
        return newString;
    }
}