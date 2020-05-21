package io.vantiq.extsrc.HikVisionSource;

import java.util.Map;

public class CameraEntry {
 
    public String CameraId ;
    public Boolean Enable ; 
    public String DVRIPAddress ;
    public Integer DVRPortNumber ;
    public String DVRUserName ;
    public String DVRPassword ;
    public int lAlarmHandle ; 
    public int lUserID ; 


    public CameraEntry(Map<String,Object> o )
    {
        CameraId = (String) o.get("CameraId");
        Enable = Boolean.parseBoolean((String)o.get("Enable"));
        DVRIPAddress = (String) o.get("DVRIP");
        DVRPortNumber = Integer.parseInt((String) o.get("DVRPort"));
        DVRUserName = (String) o.get("DVRUserName");
        DVRPassword = (String) o.get("DVRPassword");
    }

    public CameraEntry()
    {
        
    }
    
}