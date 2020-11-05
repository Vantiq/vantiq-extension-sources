/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */
package io.vantiq.extsrc.HikVisionSource;

import java.util.Map;
/**
 * Hold the information gatherd from the configuration and the actual activation of a camera.
 * Class holds the hanldes which are created as part of working process of a camera .
 */
public class CameraEntry {

    public String CameraId;
    public int Channel;
    public Boolean Enable;
    public String DVRIPAddress;
    public Integer DVRPortNumber;
    public String DVRUserName;
    public String DVRPassword;
    public int lAlarmHandle;
    public int lUserID;
    public RealDataCallBack RealData;
    public int lRealPlayHandle;

    public CameraEntry(Map<String, Object> o, int channel) {
        CameraId = (String) o.get("CameraId");
        Channel = channel;
        Enable = Boolean.parseBoolean((String) o.get("Enable"));
        DVRIPAddress = (String) o.get("DVRIP");
        DVRPortNumber = Integer.parseInt((String) o.get("DVRPort"));
        DVRUserName = (String) o.get("DVRUserName");
        DVRPassword = (String) o.get("DVRPassword");
    }

    public CameraEntry() {

    }

}