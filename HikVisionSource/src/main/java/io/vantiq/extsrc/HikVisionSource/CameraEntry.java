package io.vantiq.extsrc.HikVisionSource;

import java.util.Map;

public class CameraEntry {
 
    public String CameraId ;
    public int Channel ; 
    public Boolean Enable ; 
    public String DVRIPAddress ;
    public Integer DVRPortNumber ;
    public String DVRUserName ;
    public String DVRPassword ;
    public int lAlarmHandle ; 
    public int lUserID ; 
    public RealDataCallBack RealData;
    public int lRealPlayHandle;

    public CameraEntry(Map<String,Object> o ,int channel)
    {
        CameraId = (String) o.get("CameraId");
        Channel = channel ; 
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