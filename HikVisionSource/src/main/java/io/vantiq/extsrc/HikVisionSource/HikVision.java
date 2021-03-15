/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.client.ResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.HikVisionSource.HCNetSDK.FMSGCallBack;
import io.vantiq.extsrc.HikVisionSource.HCNetSDK.NET_DVR_ALARMER;
import io.vantiq.extsrc.HikVisionSource.HCNetSDK.NET_DVR_DEVICEINFO_V30;
import io.vantiq.extsrc.HikVisionSource.HCNetSDK.NET_DVR_SETUPALARM_PARAM;
import io.vantiq.extsrc.HikVisionSource.exception.VantiqHikVisionException;

/**
 * Thie class implememnt the SDK with HikVision Cameras , from one hand it
 * activate the different cameras and from the other hand it hanldes the
 * different notifications accpeted by the different cameras.
 */
public class HikVision {

    /**
     * Call back inteface used to receive alarm from the camera using the call back
     * inteface in the SDK
     */
    public class FMSGCallBack_V31 implements HCNetSDK.FMSGCallBack {
        // alarm info call back function

        public void invoke(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo,
                int dwBufLen, Pointer pUser) {
            System.out.println("Receive notification on class FMSGCallBack_V31");

            executeInPool(lCommand.intValue(), pAlarmer, pAlarmInfo, dwBufLen, pUser);
        }
    }

    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    static HCNetSDK hCNetSDK = HCNetSDK.INSTANCE;
    FMSGCallBack falarmData_V31 = null;
    ExtensionWebSocketClient oClient;
    List<CameraEntry> cameras;
    Map<String, Object> config;
    Map<String, Object> general;
    Map<String, Object> options;
    boolean bContinue = true;

    ExecutorService executionPool = null;

    String vantiqServer;
    String authToken;

    String sdkLogPath = "c:/tmp/log";
    String DVRImageFolderPath = "c:/tmp/Thermo";
    String m_ListenIP;
    String VantiqDocumentPath = "public/image";
    String vantiqResourcePath = "documants";

    NativeLong lUserID;
    int iLastErr;

    private boolean DumpToFile(String filePath, Pointer pBuffer, int size) {
        try (FileOutputStream fos = new FileOutputStream(filePath);) {
            // log.error("Size {}",pBuffer.SIZE);
            byte[] buffer = pBuffer.getByteArray(0, size);

            fos.write(buffer);
            fos.close();

            return true;
        } catch (Exception ex) {
            log.error("writing {} failed {}", filePath, ex);
            return false;
        }

    }

    private void ProcessCommAlarm_PDC(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
            Pointer pUser) {
        HCNetSDK.NET_DVR_PDC_ALRAM_INFO struPDCInfo = new HCNetSDK.NET_DVR_PDC_ALRAM_INFO();
        int dwSize = struPDCInfo.size();
        struPDCInfo.write();
        Pointer pInfo = struPDCInfo.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struPDCInfo.size());
        struPDCInfo.read();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();

        int lUserID = pAlarmer.lUserID.intValue();

        ThermalNotification o = new ThermalNotification();
        CameraEntry camera = findCamera(lUserID);
        if (camera == null) {
            log.error("Receive notification on unknown lUserID {}", lUserID);
            o.CameraId = "Unknown";
            camera = new CameraEntry();
            camera.CameraId = "Unknown";
        } else
            o.CameraId = camera.CameraId;

        o.EventType = "PeopleCounterAlarm";
        o.ImageName = "";
        o.ThermalImageName = "";

        String stringAlarm = "";

        stringAlarm = String.format("%d: byMode:%d, byChannel:%d,bySmart:%d; dwLeaveNum:%d; dwEnterNum:%d", dwSize,
                struPDCInfo.byMode, struPDCInfo.byChannel, struPDCInfo.bySmart,
                // public NET_VCA_DEV_INFO struDevInfo = new NET_VCA_DEV_INFO();
                // public uStatModeParam ustateModeParam = new uStatModeParam();
                struPDCInfo.dwLeaveNum, struPDCInfo.dwEnterNum);

        // struPDCInfo.ustateModeParam.strustatFrame.dwAbsTime
        log.info("Camera {} : {}", camera.CameraId, stringAlarm);
        o.Extended = struPDCInfo;

        sendNotificationWithUpload(o);
    }

    private void ProcessCommAlarm_FaceDetect(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo,
            int dwBufLen, Pointer pUser) {
        HCNetSDK.NET_DVR_FACE_DETECTION struFaceDetectInfo = new HCNetSDK.NET_DVR_FACE_DETECTION();
        int dwSize = struFaceDetectInfo.size();

        struFaceDetectInfo.write();
        Pointer pInfo = struFaceDetectInfo.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struFaceDetectInfo.size());
        struFaceDetectInfo.read();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        String stringAlarm = "";

        int lUserID = pAlarmer.lUserID.intValue();

        ThermalNotification o = new ThermalNotification();
        CameraEntry camera = findCamera(lUserID);
        if (camera == null) {
            log.error("Receive notification on unknown lUserID {}", lUserID);
            o.CameraId = "Unknown";
            camera = new CameraEntry();
            camera.CameraId = "Unknown";
        } else {
            o.CameraId = camera.CameraId;
        }

        o.EventType = "FaceAlarm";
        o.ImageName = "a";
        o.ThermalImageName = "b";

        String szRegionInfo = "";
        int iPointNum = struFaceDetectInfo.byFacePicNum;

        for (int j = 0; j < iPointNum; j++) {
            float fX = struFaceDetectInfo.struFacePic[j].fX;
            float fY = struFaceDetectInfo.struFacePic[j].fY;
            szRegionInfo += String.format("%sX%d:%f,Y%d:%f;\n", szRegionInfo, j + 1, fX, j + 1, fY);
        }

        stringAlarm = String.format(
                "%d: dwRelativeTime:%d, dwAbsTime:%d,dwBackgroundPicLen:%d; byFacePicNum:%d;  Region[%s]", dwSize,
                struFaceDetectInfo.dwRelativeTime, struFaceDetectInfo.dwAbsTime, struFaceDetectInfo.dwBackgroundPicLen,
                struFaceDetectInfo.byFacePicNum, szRegionInfo);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HHmmssSSS");
        String time = dtf.format(LocalDateTime.now());

        if ((struFaceDetectInfo.dwBackgroundPicLen != 0)
                && (struFaceDetectInfo.pBackgroundPicpBuffer != Pointer.NULL)) {
            String str = String.format("Device_Background_Pic_[%s]_lUerID_[%d]_%s.%s", strIP,
                    pAlarmer.lUserID.intValue(), time, (struFaceDetectInfo.dwBackgroundPicLen != 0) ? "data" : "jpg");
            String fullFileName = String.format("%s/%s", DVRImageFolderPath, str);

            DumpToFile(fullFileName, struFaceDetectInfo.pBackgroundPicpBuffer, struFaceDetectInfo.dwBackgroundPicLen);
            o.ImageName = str;
        }

        log.info("Camera {} : {}", camera.CameraId, stringAlarm);
        o.Extended = struFaceDetectInfo;

        sendNotificationWithUpload(o);

    }

    private void ProcessCommAlarm_UploadFaceSnapResult(HCNetSDK.NET_DVR_ALARMER pAlarmer,
            HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen, Pointer pUser) {
        HCNetSDK.NET_VCA_FACESNAP_RESULT struUploadFaceSnapResult = new HCNetSDK.NET_VCA_FACESNAP_RESULT();
        int dwSize = struUploadFaceSnapResult.size();

        struUploadFaceSnapResult.write();
        Pointer pInfo = struUploadFaceSnapResult.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struUploadFaceSnapResult.size());
        struUploadFaceSnapResult.read();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        String stringAlarm = "";

        int lUserID = pAlarmer.lUserID.intValue();

        ThermalNotification o = new ThermalNotification();
        CameraEntry camera = findCamera(lUserID);
        if (camera == null) {
            log.error("Receive notification on unknown lUserID {}", lUserID);
            o.CameraId = "Unknown";
            camera = new CameraEntry();
            camera.CameraId = "Unknown";
        } else {
            o.CameraId = camera.CameraId;
        }

        o.EventType = "UploadFaceSnapResult";
        o.ImageName = "";
        o.ThermalImageName = "";

        stringAlarm = String.format(
                "%d: dwRelativeTime:%d, dwAbsTime:%d,dwFacePicID:%d; dwFaceScore:%d;  bySmart:%d, byAlarmEndMark:%d, byRepeatTimes:%d",
                dwSize, struUploadFaceSnapResult.dwRelativeTime, struUploadFaceSnapResult.dwAbsTime,
                struUploadFaceSnapResult.dwFacePicID, struUploadFaceSnapResult.dwFaceScore,
                struUploadFaceSnapResult.bySmart, struUploadFaceSnapResult.byAlarmEndMark,
                struUploadFaceSnapResult.byRepeatTimes);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HHmmssSSS");
        String time = dtf.format(LocalDateTime.now());

        if ((struUploadFaceSnapResult.dwFacePicLen != 0) && (struUploadFaceSnapResult.pBuffer1 != Pointer.NULL)) {
            String str = String.format("Device_ID_UploadFace_[%s]_lUserID_[%d]_%s.%s", strIP,
                    pAlarmer.lUserID.intValue(), time, "jpg");

            String fullFileName = String.format("%s/%s", DVRImageFolderPath, str);

            log.info("Before dumping to {}", fullFileName);
            try {
                DumpToFile(fullFileName, struUploadFaceSnapResult.pBuffer1, struUploadFaceSnapResult.dwFacePicLen);
                log.info("After dumping to {}", fullFileName);
                o.ImageName = str;
            } catch (Exception ex) {
                log.error("Error while dumping {}", ex);
            }
        }

        if ((struUploadFaceSnapResult.dwBackgroundPicLen != 0) && (struUploadFaceSnapResult.pBuffer2 != Pointer.NULL)) {
            String str = String.format("Device_ID_UploadFaceBackGround_[%s]_lUserID_[%d]_%s.%s", strIP,
                    pAlarmer.lUserID.intValue(), time, "jpg");

            String fullFileName = String.format("%s/%s", DVRImageFolderPath, str);

            log.info("Before dumping to {}", fullFileName);
            try {
                DumpToFile(fullFileName, struUploadFaceSnapResult.pBuffer2,
                        struUploadFaceSnapResult.dwBackgroundPicLen);
                log.info("After dumping to {}", fullFileName);
                o.ThermalImageName = str;
            } catch (Exception ex) {
                log.error("Error while dumping {}", ex);
            }
        }

        log.info("Camera {} : {}", camera.CameraId, stringAlarm);
        o.Extended = struUploadFaceSnapResult;

        sendNotificationWithUpload(o);

    }

    private void ProcessCommAlarm_SnapMatchAlarm(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo,
            int dwBufLen, Pointer pUser) {

        HCNetSDK.NET_VCA_FACESNAP_MATCH_ALARM struFaceSnapMatchAlarm = new HCNetSDK.NET_VCA_FACESNAP_MATCH_ALARM();
        int dwSize = struFaceSnapMatchAlarm.size();

        struFaceSnapMatchAlarm.write();
        Pointer pInfo = struFaceSnapMatchAlarm.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struFaceSnapMatchAlarm.size());
        struFaceSnapMatchAlarm.read();

        testDumpObjToBin(struFaceSnapMatchAlarm, "d:/tmp/thermo/struFaceSnapMatchAlarm.bin");

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        String stringAlarm = "";

        int lUserID = pAlarmer.lUserID.intValue();

        ThermalNotification o = new ThermalNotification();
        CameraEntry camera = findCamera(lUserID);
        if (camera == null) {
            log.error("Receive notification on unknown lUserID {}", lUserID);
            o.CameraId = "Unknown";
            camera = new CameraEntry();
            camera.CameraId = "Unknown";
        } else
            o.CameraId = camera.CameraId;

        o.EventType = "SnapMatchAlarm";
        o.ImageName = "";
        o.ThermalImageName = "";

        stringAlarm = String.format(
                "%d: fSimilarity:%f, byModelingStatus:%d,byLivenessDetectionStatus:%d; cTimeDifferenceH:%d;  cTimeDifferenceM:%d, byMask:%d, bySmile:%d",
                dwSize, struFaceSnapMatchAlarm.fSimilarity, struFaceSnapMatchAlarm.byModelingStatus,
                struFaceSnapMatchAlarm.byLivenessDetectionStatus, struFaceSnapMatchAlarm.cTimeDifferenceH,
                struFaceSnapMatchAlarm.cTimeDifferenceM, struFaceSnapMatchAlarm.byMask, struFaceSnapMatchAlarm.bySmile);

        // struFaceSnapMatchAlarm.dwSnapPicLen
        // struFaceSnapMatchAlarm.pSnapPicBuffer
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HHmmssSSS");
        String time = dtf.format(LocalDateTime.now());

        if ((struFaceSnapMatchAlarm.dwSnapPicLen != 0) && (struFaceSnapMatchAlarm.pSnapPicBuffer != Pointer.NULL)) {
            String str = String.format("Device_ID_SnapPic_[%s]_lUerID_[%d]_%s.%s", strIP, pAlarmer.lUserID.intValue(),
                    time, "jpg");

            String fullFileName = String.format("%s/%s", DVRImageFolderPath, str);

            log.info("Before dumping to {}", fullFileName);
            try {
                DumpToFile(fullFileName, struFaceSnapMatchAlarm.pSnapPicBuffer, struFaceSnapMatchAlarm.dwSnapPicLen);
                log.info("After dumping to {}", fullFileName);
                o.ImageName = str;
            } catch (Exception ex) {
                log.error("Error while dumping {}", ex);
            }
        }

        log.info("Camera {} : {}", camera.CameraId, stringAlarm);
        o.Extended = struFaceSnapMatchAlarm;

        sendNotificationWithUpload(o);

    }

    private void ProcessCommAlarm_ThermoetryAlarm(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo,
            int dwBufLen, Pointer pUser) {

        HCNetSDK.NET_DVR_THERMOMETRY_ALARM struThermometryAlarm = new HCNetSDK.NET_DVR_THERMOMETRY_ALARM();
        int dwSize = struThermometryAlarm.size();

        struThermometryAlarm.write();
        Pointer pInfo = struThermometryAlarm.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struThermometryAlarm.size());
        struThermometryAlarm.read();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        String stringAlarm = "";

        int lUserID = pAlarmer.lUserID.intValue();

        ThermalNotification o = new ThermalNotification();
        CameraEntry camera = findCamera(lUserID);
        if (camera == null) {
            log.error("Receive notification on unknown lUserID {}", lUserID);
            o.CameraId = "Unknown";
            camera = new CameraEntry();
            camera.CameraId = "Unknown";
        } else
            o.CameraId = camera.CameraId;

        o.EventType = "ThermalAlarm";
        o.ImageName = "a";
        o.ThermalImageName = "b";

        if (0 == struThermometryAlarm.byRuleCalibType) {
            stringAlarm = String.format(
                    "%d: Channel:%d, RuleID:%d,TemperatureSuddenChangeCycle:%d; TemperatureSuddenChangeValue:%f;  ToleranceTemperature:%f, AlertFilteringTime:%s, AlarmFilteringTime:%s,ThermometryUnit:%d, PresetNo:%d, RuleTemperature:%f, CurrTemperature:%f, PTZ Info[Pan:%f, Tilt:%f, Zoom:%d], AlarmLevel:%d,   AlarmType:%s, AlarmRule:%d, RuleCalibType:%d, Point[x:%f, y:%f], PicLen:%d, ThermalPicLen:%d, ThermalInfoLen:%d",
                    dwSize, struThermometryAlarm.dwChannel, struThermometryAlarm.byRuleID,
                    struThermometryAlarm.dwTemperatureSuddenChangeCycle,
                    struThermometryAlarm.fTemperatureSuddenChangeValue, struThermometryAlarm.fToleranceTemperature,
                    struThermometryAlarm.dwAlertFilteringTime, struThermometryAlarm.dwAlarmFilteringTime,
                    struThermometryAlarm.byThermometryUnit, struThermometryAlarm.wPresetNo,
                    struThermometryAlarm.fRuleTemperature, struThermometryAlarm.fCurrTemperature,
                    struThermometryAlarm.struPtzInfo.fPan, struThermometryAlarm.struPtzInfo.fTilt,
                    struThermometryAlarm.struPtzInfo.fZoom, struThermometryAlarm.byAlarmLevel,
                    struThermometryAlarm.byAlarmType, struThermometryAlarm.byAlarmRule,
                    struThermometryAlarm.byRuleCalibType, struThermometryAlarm.struPoint.fX,
                    struThermometryAlarm.struPoint.fY, struThermometryAlarm.dwPicLen,
                    struThermometryAlarm.dwThermalPicLen, struThermometryAlarm.dwThermalInfoLen);

        } else if (1 == struThermometryAlarm.byRuleCalibType || 2 == struThermometryAlarm.byRuleCalibType) {
            String szRegionInfo = "";
            int iPointNum = struThermometryAlarm.struRegion.dwPointNum;

            for (int j = 0; j < iPointNum; j++) {
                float fX = struThermometryAlarm.struRegion.struPos[j].fX;
                float fY = struThermometryAlarm.struRegion.struPos[j].fY;
                szRegionInfo += String.format("%sX%d:%f,Y%d:%f;\n", szRegionInfo, j + 1, fX, j + 1, fY);
            }

            stringAlarm = String.format("%d: Channel:%d, RuleID:%d,TemperatureSuddenChangeCycle:%d", dwSize,
                    struThermometryAlarm.dwChannel, struThermometryAlarm.byRuleID,
                    struThermometryAlarm.dwTemperatureSuddenChangeCycle);

            /*
             * stringAlarm = String.
             * format("%d: Channel:%d, RuleID:%d,TemperatureSuddenChangeCycle:%d; TemperatureSuddenChangeValue:%f;  ToleranceTemperature:%f, AlertFilteringTime:%s, AlarmFilteringTime:%s,HighestPoint[x:%f, y:%f],ThermometryUnit:%d, PresetNo:%d, RuleTemperature:%f, CurrTemperature:%f, PTZ Info[Pan:%f, Tilt:%f, Zoom:%d], AlarmLevel:%d,   AlarmType:%s, AlarmRule:%d, RuleCalibType:%d, Region[%d], PicLen:%d, ThermalPicLen:%d, ThermalInfoLen:%d"
             * , dwSize, struThermometryAlarm.dwChannel, struThermometryAlarm.byRuleID,
             * struThermometryAlarm.dwTemperatureSuddenChangeCycle,
             * struThermometryAlarm.fTemperatureSuddenChangeValue,
             * struThermometryAlarm.fToleranceTemperature,
             * struThermometryAlarm.dwAlertFilteringTime,
             * struThermometryAlarm.dwAlarmFilteringTime,
             * struThermometryAlarm.struHighestPoint.fX,
             * struThermometryAlarm.struHighestPoint.fY,
             * struThermometryAlarm.byThermometryUnit, struThermometryAlarm.wPresetNo,
             * struThermometryAlarm.fRuleTemperature, struThermometryAlarm.fCurrTemperature,
             * struThermometryAlarm.struPtzInfo.fPan,
             * struThermometryAlarm.struPtzInfo.fTilt,
             * struThermometryAlarm.struPtzInfo.fZoom, struThermometryAlarm.byAlarmLevel,
             * struThermometryAlarm.byAlarmType, struThermometryAlarm.byAlarmRule,
             * struThermometryAlarm.byRuleCalibType, szRegionInfo,
             * struThermometryAlarm.dwPicLen, struThermometryAlarm.dwThermalPicLen,
             * struThermometryAlarm.dwThermalInfoLen);
             */
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HHmmssSSS");
        String time = dtf.format(LocalDateTime.now());

        if ((struThermometryAlarm.dwPicLen != 0) && (struThermometryAlarm.pPicBuff != Pointer.NULL)) {
            String str = String.format("Device_ID_Pic_[%s]_lUerID_[%d]_%s.%s", strIP, pAlarmer.lUserID.intValue(), time,
                    (struThermometryAlarm.byPicTransType != 0) ? "data" : "jpg");
            String fullFileName = String.format("%s/%s", DVRImageFolderPath, str);

            DumpToFile(fullFileName, struThermometryAlarm.pPicBuff, struThermometryAlarm.dwPicLen);
            o.ImageName = str;
        }

        if ((struThermometryAlarm.dwThermalPicLen != 0) && (struThermometryAlarm.pThermalPicBuff != Pointer.NULL)) {
            String str = String.format("Device_ID_ThermoPic_[%s]_lUerID_[%d]_%s.%s", strIP, pAlarmer.lUserID.intValue(),
                    time, (struThermometryAlarm.byPicTransType != 0) ? "data" : "jpg");

            String fullFileName = String.format("%s/%s", DVRImageFolderPath, str);

            DumpToFile(fullFileName, struThermometryAlarm.pThermalPicBuff, struThermometryAlarm.dwThermalPicLen);
            o.ThermalImageName = str;
        }

        if (struThermometryAlarm.dwThermalInfoLen > 0 && struThermometryAlarm.pThermalInfoBuff != Pointer.NULL) {
            String str = String.format("%s/Device_ID_ThermalInfoPic_[%s]_lUerID_[%d]_%s.%s", DVRImageFolderPath, strIP,
                    pAlarmer.lUserID.intValue(), time, "data");

            DumpToFile(str, struThermometryAlarm.pThermalInfoBuff, struThermometryAlarm.dwThermalInfoLen);

        }

        log.info("Camera {} : {}", camera.CameraId, stringAlarm);
        o.Extended = struThermometryAlarm;

        sendNotificationWithUpload(o);

    }

    public void uploadThermalImage(ThermalNotification notification, ImageUtil iu) {
        HikUploadHelper responseHandler = new HikUploadHelper() {
            @Override
            public void onSuccess(Object body, okhttp3.Response response) {
                log.info("Upload Thermal before send notification");
                oClient.sendNotification(notification);

            }

            @Override
            public void onError(List<VantiqError> errors, okhttp3.Response response) {
                // TODO Auto-generated method stub

            }
        };

        log.info("Upload Thermal image {}", notification.ThermalImageName);

        if (!(notification.ThermalImageName == null || notification.ThermalImageName == "")) {
            File f = new File(String.format("%s/%s", DVRImageFolderPath, notification.ThermalImageName));
            String target = String.format("%s/%s", VantiqDocumentPath, notification.ThermalImageName);
            iu.uploadToVantiq(f, vantiqResourcePath, target, responseHandler);
            notification.ThermalImageName = target; // notification.ThermalImageName;
        } else
            oClient.sendNotification(notification);

    }

    public void uploadImage(ThermalNotification notification, ImageUtil iu) {
        HikUploadHelper responseHandler = new HikUploadHelper() {
            @Override
            public void onSuccess(Object body, okhttp3.Response response) {
                uploadThermalImage(notification, iu);

            }

            @Override
            public void onError(List<VantiqError> errors, okhttp3.Response response) {
                log.error("uploadImage failed {}", errors);
            }
        };

        log.info("Upload image {}", notification.ImageName);

        if (!(notification.ImageName == null || notification.ImageName == "")) {
            File f = new File(String.format("%s/%s", DVRImageFolderPath, notification.ImageName));
            String target = String.format("%s/%s", VantiqDocumentPath, notification.ImageName);

            iu.uploadToVantiq(f, vantiqResourcePath, target, responseHandler);

            notification.ImageName = target;
        } else {
            uploadThermalImage(notification, iu);
        }

    }

    ///
    /// This class is used to continue the synchrouns process of Upoload Image ,
    /// upload Thermal
    /// and only after that , send the notification
    /// done by adding an callback which is being called by the internal callback of
    /// the uploadfile methos.
    /// ImageUtil . using preexisting interface which is well known in the class
    /// heirarchy .
    ///
    public abstract class HikUploadHelper implements ResponseHandler {

        @Override
        public void onSuccess(Object body, okhttp3.Response response) {

        }

        @Override
        public void onError(List<VantiqError> errors, okhttp3.Response response) {

        }

        @Override
        public void onFailure(Throwable t) {
        }

    }

    /**
     * Procudure responsible for uploading images accpeted by notificatio from
     * Camera .
     * 
     * @param notification
     */
    private void sendNotificationWithUpload(ThermalNotification notification) {
        ImageUtil iu = new ImageUtil();
        iu.vantiq = new Vantiq((String) config.get("vantiqServer"));
        iu.vantiq.setAccessToken((String) config.get("authToken"));

        uploadImage(notification, iu);
        /*
         * SH responseHandler = new SH();
         * 
         * if (!(notification.ImageName == null || notification.ImageName == "")) { File
         * f = new File(String.format("%s/%s", DVRImageFolderPath
         * ,notification.ImageName)); String target = String.format("%s/%s",
         * VantiqDocumentPath ,notification.ImageName);
         * iu.uploadToVantiq(f,vantiqResourcePath, target,responseHandler);
         * 
         * notification.ImageName = target; }
         * 
         * if (!(notification.ThermalImageName == null || notification.ThermalImageName
         * == "")) { File f = new File(String.format("%s/%s", DVRImageFolderPath
         * ,notification.ThermalImageName)); String target = String.format("%s/%s",
         * VantiqDocumentPath ,notification.ThermalImageName); iu.uploadToVantiq(f,
         * vantiqResourcePath,target);//,responseHandler); notification.ThermalImageName
         * = target ; // notification.ThermalImageName; }
         * 
         * 
         * 
         * oClient.sendNotification(notification);
         */
    }

    /**
     * Hanlde all type oc communication probelm raised by the Camera
     * 
     * @param pAlarmer
     * @param pAlarmInfo
     * @param dwBufLen
     * @param pUser
     */
    public void ProcessCommAlarm(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
            Pointer pUser) {
        HCNetSDK.NET_DVR_ALARMINFO struAlarmInfo = new HCNetSDK.NET_DVR_ALARMINFO();

        struAlarmInfo.write();
        Pointer pInfo = struAlarmInfo.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struAlarmInfo.size());
        struAlarmInfo.read();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        String stringAlarm = "";
        int i = 0;

        switch (struAlarmInfo.dwAlarmType) {
        case 0:
            stringAlarm = "IO alarm, alarm input number" + struAlarmInfo.dwAlarmInputNumber
                    + ",triggered recording channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM; i++) {
                if (struAlarmInfo.dwAlarmRelateChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 1:
            stringAlarm = "HDD full,alarm disk number:";
            for (i = 0; i < HCNetSDK.MAX_DISKNUM; i++) {
                if (struAlarmInfo.dwDiskNumber[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 2:
            stringAlarm = "video loss,alarm channel number:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM; i++) {
                if (struAlarmInfo.dwChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 3:
            stringAlarm = "motion detection,alarm channel number:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM; i++) {
                if (struAlarmInfo.dwChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 4:
            stringAlarm = "HDD unformatted, alarm disk number:";
            for (i = 0; i < HCNetSDK.MAX_DISKNUM; i++) {
                if (struAlarmInfo.dwDiskNumber[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 5:
            stringAlarm = "Read or Write HDD error,alarm disk number:";
            for (i = 0; i < HCNetSDK.MAX_DISKNUM; i++) {
                if (struAlarmInfo.dwDiskNumber[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 6:
            stringAlarm = "Tampering alarm,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM; i++) {
                if (struAlarmInfo.dwChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 7:
            stringAlarm = "Input or Output video standard mismatch,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM; i++) {
                if (struAlarmInfo.dwChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 8:
            stringAlarm = "illegal access";
            break;
        default:
            stringAlarm = "other unknown alarm info";
            break;
        }

        log.info(stringAlarm);

    }

    /**
     * Managing Equipment level notifications received by Camera
     * 
     * @param pAlarmer
     * @param pAlarmInfo
     * @param dwBufLen
     * @param pUser
     */
    private void ProcessCommAlarm_V30(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
            Pointer pUser) {

        HCNetSDK.NET_DVR_ALARMINFO_V30 struAlarmInfoV30 = new HCNetSDK.NET_DVR_ALARMINFO_V30();

        struAlarmInfoV30.write();
        Pointer pInfo = struAlarmInfoV30.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struAlarmInfoV30.size());
        struAlarmInfoV30.read();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        String stringAlarm = "";
        int i = 0;

        switch (struAlarmInfoV30.dwAlarmType) {
        case 0:
            stringAlarm = "IO alarm,alarm input number:" + struAlarmInfoV30.dwAlarmInputNumber
                    + ",triggered recording channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byAlarmRelateChannel[i] == 1) {
                    stringAlarm += (i + 1) + "\\";
                }
            }
            break;
        case 1:
            stringAlarm = "HDD full,alarm disk number:";
            for (i = 0; i < HCNetSDK.MAX_DISKNUM_V30; i++) {
                if (struAlarmInfoV30.byDiskNumber[i] == 1) {
                    stringAlarm += (i + 1) + " ";
                }
            }
            break;
        case 2:
            stringAlarm = "Video loss,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 3:
            stringAlarm = "Motion detection,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 4:
            stringAlarm = "HDD unformatted, alarm disk number:";
            for (i = 0; i < HCNetSDK.MAX_DISKNUM_V30; i++) {
                if (struAlarmInfoV30.byDiskNumber[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 5:
            stringAlarm = "Read or Write HDD error,alarm disk number:";
            for (i = 0; i < HCNetSDK.MAX_DISKNUM_V30; i++) {
                if (struAlarmInfoV30.byDiskNumber[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 6:
            stringAlarm = "Tampering alarm,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 7:
            stringAlarm = "Input or Output video standard mismatch,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 8:
            stringAlarm = "illegal access";
            break;
        case 9:
            stringAlarm = "Video siganl exception, alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 10:
            stringAlarm = "recording/capture is abnormal,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 11:
            stringAlarm = "Intelligent scene changed,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 12:
            stringAlarm = "RAID is abnormal";
            break;
        case 13:
            stringAlarm = "recording resolution does not match with which of front-end camera,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        case 15:
            stringAlarm = "VCA,alarm channel:";
            for (i = 0; i < HCNetSDK.MAX_CHANNUM_V30; i++) {
                if (struAlarmInfoV30.byChannel[i] == 1) {
                    stringAlarm += (i + 1) + " \\ ";
                }
            }
            break;
        default:
            stringAlarm = "other unknown alarm info";
            break;
        }
        log.info(stringAlarm);
    }

    CameraEntry findCamera(int lUserId) {
        for (CameraEntry camera : cameras) {
            if (camera.lUserID == lUserId) {
                return camera;
            }
        }
        return null;
    }

    CameraEntry findCamera(String IPAdress) {
        for (CameraEntry camera : cameras) {
            if (camera.DVRIPAddress.equals(IPAdress)) {
                return camera;
            }
        }
        return null;
    }

    /**
     * Basic Face recognition notification , can contans IR imgae and a regular
     * optic image . those are being loaded to vantiq , once its uploaded
     * successfully, it send notificatio to the source topic which contains all the
     * information received from the camera includiong the accepted path in the
     * vantiq document sectio .
     *
     * @param pAlarmer
     * @param pAlarmInfo
     * @param dwBufLen
     * @param pUser
     */
    private void ProcessCommAlarm_RULE(HCNetSDK.NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
            Pointer pUser) {
        HCNetSDK.NET_VCA_RULE_ALARM struRuleAlarmInfo = new HCNetSDK.NET_VCA_RULE_ALARM();

        struRuleAlarmInfo.write();
        Pointer pInfo = struRuleAlarmInfo.getPointer();
        pInfo.write(0, pAlarmInfo.RecvBuffer, 0, struRuleAlarmInfo.size());
        struRuleAlarmInfo.read();
        int lUserID = pAlarmer.lUserID.intValue();

        String strIP = new String(pAlarmer.sDeviceIP, StandardCharsets.US_ASCII).trim();
        CameraEntry camera = findCamera(lUserID);
        if (camera == null) {
            log.error("Receive notification on unknown lUserID {}", lUserID);
        }
        String stringAlarm = "";
        int i = 0;

        // testDumpObjToBin(struRuleAlarmInfo,"d:/tmp/thermo/rule.bin");

        switch (struRuleAlarmInfo.struRuleInfo.wEventTypeEx) {
        case 1: // (ushort)CHCNetSDK.VCA_RULE_EVENT_TYPE_EX.ENUM_VCA_EVENT_TRAVERSE_PLANE:
            stringAlarm = "Line crossing,Object ID:" + struRuleAlarmInfo.struTargetInfo.dwID;
            break;
        case 2: // (ushort)CHCNetSDK.VCA_RULE_EVENT_TYPE_EX.ENUM_VCA_EVENT_ENTER_AREA:
            stringAlarm = "Target entering area,Object ID:" + struRuleAlarmInfo.struTargetInfo.dwID;
            break;
        case 3:// (ushort)CHCNetSDK.VCA_RULE_EVENT_TYPE_EX.ENUM_VCA_EVENT_EXIT_AREA:
            stringAlarm = "Target leaving area,Object ID:" + struRuleAlarmInfo.struTargetInfo.dwID;
            break;
        case 4:// (ushort)CHCNetSDK.VCA_RULE_EVENT_TYPE_EX.ENUM_VCA_EVENT_INTRUSION:
        {
            HCNetSDK.NET_VCA_INTRUSION m_struIntrusion = new HCNetSDK.NET_VCA_INTRUSION();
            m_struIntrusion.write();
            Pointer pInfot = m_struIntrusion.getPointer();
            pInfot.write(0, struRuleAlarmInfo.struRuleInfo.uEventParam, 0, m_struIntrusion.size());
            m_struIntrusion.read();
            /*
             * IntPtr ptrIntrusionInfo = Marshal.AllocHGlobal((Int32)dwSize);
             * Marshal.StructureToPtr(struRuleAlarmInfo.struRuleInfo.uEventParam,
             * ptrIntrusionInfo, false); m_struIntrusion =
             * (CHCNetSDK.NET_VCA_INTRUSION)Marshal.PtrToStructure(ptrIntrusionInfo,
             * typeof(CHCNetSDK.NET_VCA_INTRUSION));
             */

            i = 0;
            String strRegion = "";
            for (i = 0; i < m_struIntrusion.struRegion.dwPointNum; i++) {
                strRegion = strRegion + "(" + m_struIntrusion.struRegion.struPos[i].fX + ","
                        + m_struIntrusion.struRegion.struPos[i].fY + ")";
            }
            stringAlarm = "Intrusion detection,Object ID:" + struRuleAlarmInfo.struTargetInfo.dwID + ",Region range:"
                    + strRegion;
            // m_struIntrusion.struRegion
        }
            break;
        default:
            stringAlarm = "other behaviour analysis alarm,Object ID:" + struRuleAlarmInfo.struTargetInfo.dwID;
            break;
        }

        //
        if (struRuleAlarmInfo.dwPicDataLen > 0) {
            String time = new SimpleDateFormat("HHmmssSSS").format(new Date()); // DateTime.Now.ToString("HHmmssffff");

            String str = String.format("%s/Device_ID_[%s]_lUerID_[%d]_%s_Behavior_alarm_capture.%s", DVRImageFolderPath,
                    strIP, pAlarmer.lUserID.intValue(), time, "jpg");

            if (struRuleAlarmInfo.pImage != Pointer.NULL) {
                DumpToFile(str, struRuleAlarmInfo.pImage, struRuleAlarmInfo.dwPicDataLen);
            } else
                log.error("ProcessCommAlarm_RULE receive size for image but pImgae is null");
        }

        // :
        /*
         * string strTimeYear = ((struRuleAlarmInfo.dwAbsTime >> 26) + 2000).ToString();
         * string strTimeMonth = ((struRuleAlarmInfo.dwAbsTime >> 22) &
         * 15).ToString("d2"); string strTimeDay = ((struRuleAlarmInfo.dwAbsTime >> 17)
         * & 31).ToString("d2"); string strTimeHour = ((struRuleAlarmInfo.dwAbsTime >>
         * 12) & 31).ToString("d2"); string strTimeMinute =
         * ((struRuleAlarmInfo.dwAbsTime >> 6) & 63).ToString("d2"); string
         * strTimeSecond = ((struRuleAlarmInfo.dwAbsTime >> 0) & 63).ToString("d2");
         * string strTime = strTimeYear + "-" + strTimeMonth + "-" + strTimeDay + " " +
         * strTimeHour + ":" + strTimeMinute + ":" + strTimeSecond;
         */
        String strTimeYear = Integer.toString((struRuleAlarmInfo.dwAbsTime >> 26) + 2000); // should be 2 digits
        String strTimeMonth = Integer.toString((struRuleAlarmInfo.dwAbsTime >> 22) & 15);
        String strTimeDay = Integer.toString((struRuleAlarmInfo.dwAbsTime >> 17) & 31);
        String strTimeHour = Integer.toString((struRuleAlarmInfo.dwAbsTime >> 12) & 31);
        String strTimeMinute = Integer.toString((struRuleAlarmInfo.dwAbsTime >> 6) & 63);
        String strTimeSecond = Integer.toString((struRuleAlarmInfo.dwAbsTime >> 0) & 63);
        String strTime = strTimeYear + "-" + strTimeMonth + "-" + strTimeDay + " " + strTimeHour + ":" + strTimeMinute
                + ":" + strTimeSecond;

        log.info("Camera {} : {}", camera.CameraId, stringAlarm);

        ThermalNotification o = new ThermalNotification();

        o.CameraId = (camera == null) ? "Unknown" : camera.CameraId;
        o.EventType = "RuleAlarm";
        o.ImageName = "";
        o.ThermalImageName = "";
        o.Extended = struRuleAlarmInfo;

        oClient.sendNotification(o);

    }

    /**
     * the callback registed to the SDK , based on the commanbd type its activate
     * the relavent procedure
     * 
     * @param lCommand   - command enumeration
     * @param pAlarmer   - memory block contans general information
     * @param pAlarmInfo - memory block with different structure per error code
     * @param dwBufLen   - the size of the dynamic buffer
     * @param pUser      - user id
     */
    public void AlarmMessageHandle(int lCommand, NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
            Pointer pUser) {
        // lCommand , lCommand pAlarmInfo
        switch (lCommand) {
        case HCNetSDK.COMM_ALARM: // (DS-8000 ) IO
            ProcessCommAlarm(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_ALARM_V30:// IO
            ProcessCommAlarm_V30(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_ALARM_RULE://
            ProcessCommAlarm_RULE(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_ALARM_PDC://
            ProcessCommAlarm_PDC(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_ALARM_FACE_DETECTION://
            ProcessCommAlarm_FaceDetect(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_THERMOMETRY_ALARM:// sagi^^^^
            ProcessCommAlarm_ThermoetryAlarm(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_SNAP_MATCH_ALARM:// sagi^^^^
            ProcessCommAlarm_SnapMatchAlarm(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        case HCNetSDK.COMM_UPLOAD_FACESNAP_RESULT:// sagi^^^^
            ProcessCommAlarm_UploadFaceSnapResult(pAlarmer, pAlarmInfo, dwBufLen, pUser);
            break;
        default: {
            String stringAlarm = "upload alarm,alarm message type:" + lCommand;
            log.error("upload alarm,alarm message type:" + lCommand);
            break;
        }
        }
    }

    boolean initCamera() {

        boolean m_bInitSDK = hCNetSDK.NET_DVR_Init();
        if (m_bInitSDK == false) {
            log.error("NET_DVR_Init error!");
            return false;
        } else {
            byte[] strIP = new byte[16 * 16];
            int dwValidNum = 0;
            IntByReference pdwValidNum = new IntByReference((Integer) dwValidNum);
            ByteByReference pbEnableBind = new ByteByReference();

            /*
             * This portion was remorked as it created isue when server had two ethernet
             * card as we are using callback and not listenung mnode for accepting
             * notification , remarking it it didnt effect badly however , you can never
             * know
             * 
             * if (hCNetSDK.NET_DVR_GetLocalIP(strIP, pdwValidNum, pbEnableBind)) {
             * dwValidNum = pdwValidNum.getValue(); int b = pbEnableBind.getValue();
             * 
             * if (dwValidNum > 0) { m_ListenIP = new String(strIP,
             * StandardCharsets.US_ASCII);
             * 
             * log.info("Listen IP {}", m_ListenIP); hCNetSDK.NET_DVR_SetValidIP(0, true);
             * // }
             * 
             * }
             */

            // To save the SDK log
            if (!hCNetSDK.NET_DVR_SetLogToFile(true, sdkLogPath, true)) {
                log.error("hCNetSDK.NET_DVR_SetLogToFile failed");
            }

            if (falarmData_V31 == null) {
                falarmData_V31 = new FMSGCallBack_V31();
            }
            Pointer pUser = null;
            if (!hCNetSDK.NET_DVR_SetDVRMessageCallBack_V31(falarmData_V31, pUser)) {
                iLastErr = hCNetSDK.NET_DVR_GetLastError();
                log.error("NET_DVR_SetDVRMessageCallBack_V31 failed, error code= {} ", iLastErr);
                return false;
            } else {
                log.info("NET_DVR_SetDVRMessageCallBack_V31 succeed");
            }

        }

        return true;
    }

    /**
     * Login Camera using extened verion
     * 
     * @param camera camera entry to login to
     * @return
     */
    private boolean LoginV40(CameraEntry camera) {
        if (camera.DVRIPAddress == "" || camera.DVRPortNumber == 0 || camera.DVRUserName == ""
                || camera.DVRPassword == "") {
            log.error("Please input IP, Port, User name and Password! for {}", camera.CameraId);
            return false;
        }

        NET_DVR_DEVICEINFO_V30 DeviceInfo = new NET_DVR_DEVICEINFO_V30();
        HCNetSDK.NET_DVR_USER_LOGIN_INFO struLoginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();

        log.warn("Trying to loginV40 to camera {} {}:{}", camera.CameraId, camera.DVRIPAddress, camera.DVRPortNumber);

        struLoginInfo.bUseAsynLogin = 0;
        struLoginInfo.wPort = Integer.valueOf(camera.DVRPortNumber).shortValue();
        struLoginInfo.sDeviceAddress = camera.DVRIPAddress.getBytes();
        struLoginInfo.sUserName = camera.DVRUserName.getBytes();
        struLoginInfo.sPassword = camera.DVRPassword.getBytes();

        Pointer pstruLoginInfo = struLoginInfo.getPointer();
        Pointer pDeviceInfo = DeviceInfo.getPointer();

        lUserID = hCNetSDK.NET_DVR_Login_V40(pstruLoginInfo, pDeviceInfo);
        camera.lUserID = lUserID.intValue();
        if (camera.lUserID < 0) {
            iLastErr = hCNetSDK.NET_DVR_GetLastError();
            log.error("NET_DVR_Login_V40 failed, error code= {} ", iLastErr);
            return false;
        } else {
            log.info("DVR Login Successfully : Camera {} userId {}", camera.CameraId, camera.lUserID);
        }
        return true;

    }

    /**
     * login using version 30
     * 
     * @param camera - camera entry to login to .
     * @return
     */
    private boolean Login(CameraEntry camera) {
        if (camera.DVRIPAddress == "" || camera.DVRPortNumber == 0 || camera.DVRUserName == ""
                || camera.DVRPassword == "") {
            log.error("Please input IP, Port, User name and Password! for {}", camera.CameraId);
            return false;
        }

        NET_DVR_DEVICEINFO_V30 DeviceInfo = new NET_DVR_DEVICEINFO_V30();

        log.warn("Trying to login to camera {} {}:{}", camera.CameraId, camera.DVRIPAddress, camera.DVRPortNumber);

        lUserID = hCNetSDK.NET_DVR_Login_V30(camera.DVRIPAddress, camera.DVRPortNumber, camera.DVRUserName,
                camera.DVRPassword, DeviceInfo);
        camera.lUserID = lUserID.intValue();
        if (camera.lUserID < 0) {
            iLastErr = hCNetSDK.NET_DVR_GetLastError();
            log.error("NET_DVR_Login_V30 failed, error code= {} ", iLastErr);
            return false;
        } else {
            log.info("DVR Login Successfully : Camera {} userId {}", camera.CameraId, camera.lUserID);
        }
        return true;

    }

    public boolean logoutV40(CameraEntry camera) {
        boolean bValid = hCNetSDK.NET_DVR_Logout(new NativeLong(camera.lUserID));
        if (bValid) {
            camera.lUserID = -1;
        }
        return bValid;
    }

    public boolean logout(CameraEntry camera) {
        boolean bValid = hCNetSDK.NET_DVR_Logout_V30(new NativeLong(camera.lUserID));
        if (bValid) {
            camera.lUserID = -1;
        }
        return bValid;
    }

    private boolean SetAlarm(CameraEntry camera) {
        NET_DVR_SETUPALARM_PARAM struAlarmParam = new NET_DVR_SETUPALARM_PARAM();

        struAlarmParam.dwSize = struAlarmParam.size(); // (uint)Marshal.SizeOf(struAlarmParam);
        struAlarmParam.byLevel = 1; // 0- ,1-
        struAlarmParam.byAlarmInfoType = 1;// ,
        struAlarmParam.byFaceAlarmDetection = 1;// 1-

        NativeLong m_lAlarmHandle1 = hCNetSDK.NET_DVR_SetupAlarmChan_V41(new NativeLong(camera.lUserID),
                struAlarmParam);
        camera.lAlarmHandle = m_lAlarmHandle1.intValue();
        if (camera.lAlarmHandle < 0) {
            iLastErr = hCNetSDK.NET_DVR_GetLastError();
            log.error("Failed to arm camera {}, Error code: {}", camera.CameraId, iLastErr);
            return false;
        } else
            log.info("Setup Alarm succeed for camera {}", camera.CameraId);

        return true;
    }

    void executeInPool(int lCommand, NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
            Pointer pUser) {

        executionPool.execute(new Runnable() {
            @Override
            public void run() {
                log.info("start executing command {}", lCommand);
                try {
                    AlarmMessageHandle(lCommand, pAlarmer, pAlarmInfo, dwBufLen, pUser);
                } catch (RejectedExecutionException e) {
                    log.error("The queue of tasks has filled, and as a result the request was unable to be processed.",
                            e);
                }
            }

        });

    }

    void prepareConfigurationData() {
        this.vantiqServer = (String) config.get("vantiqServer");
        this.authToken = (String) config.get("authToken");

        general = (Map<String, Object>) config.get("general");

        this.sdkLogPath = (String) general.get("sdkLogPath");
        this.DVRImageFolderPath = (String) general.get("DVRImageFolderPath"); // "d:/tmp/Thermo";
        this.VantiqDocumentPath = (String) general.get("VantiqDocumentPath"); // "public/image";
        this.vantiqResourcePath = (String) general.get("VantiqResourcePath"); // "/resources/documents";

        int maxActiveTasks = 5;
        int maxQueuedTasks = 10;

        options = (Map<String, Object>) config.get("options");

        if (options != null) {
            if (options.get("maxActiveTasks") != null) {
                maxActiveTasks = (int) options.get("maxActiveTasks");
            }
            if (options.get("maxQueuedTasks") != null) {
                maxQueuedTasks = (int) options.get("maxQueuedTasks");
            }
        }

        executionPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

    }

    public Boolean StartRealPlayer(CameraEntry camera) {

        HCNetSDK.NET_DVR_PREVIEWINFO lpPreviewInfo = new HCNetSDK.NET_DVR_PREVIEWINFO();

        NativeLong nlUserId = new NativeLong(camera.lUserID);
        NativeLong nlChannel = new NativeLong(camera.Channel);

        lpPreviewInfo.hPlayWnd = null; // RealPlayWnd.Handle; - UI control .
        lpPreviewInfo.lChannel = nlChannel;
        lpPreviewInfo.dwStreamType = 0;
        lpPreviewInfo.dwLinkMode = 0;
        lpPreviewInfo.bBlocked = true;
        lpPreviewInfo.dwDisplayBufNum = 1;
        lpPreviewInfo.byProtoType = 0;
        lpPreviewInfo.byPreviewMode = 0;

        Pointer pUser;
        pUser = null;

        NativeLong m_lRealHandle = hCNetSDK.NET_DVR_RealPlay_V40(nlUserId, lpPreviewInfo, null/* RealData */, pUser);
        camera.lRealPlayHandle = m_lRealHandle.intValue();
        if (camera.lRealPlayHandle < 0) {
            iLastErr = hCNetSDK.NET_DVR_GetLastError();
            log.error("hCNetSDK.NET_DVR_RealPlay_V40 failed Error {}", iLastErr);
            return false;
        }

        log.info("StartRealPlayer succeed for camera {}", camera.CameraId);

        return true;

    }

    public void setupHikVision(ExtensionWebSocketClient oClient, Map<String, Object> config, boolean asyncProcessing,
            boolean IsInTest) throws VantiqHikVisionException {
        if (!IsInTest) {
            setupHikVision(oClient, config, asyncProcessing);
        }
    }

    public void setupHikVision(ExtensionWebSocketClient oClient, Map<String, Object> config, boolean asyncProcessing)
            throws VantiqHikVisionException {
        boolean bValid = true;
        this.cameras = (List<CameraEntry>) config.get("cameraList");
        this.oClient = oClient;
        this.config = config;

        boolean useV40 = false;

        prepareConfigurationData();

        try {

            // TODO: Remove that once unitest are working properly on incoming
            // notifications.
            // testVantiq();

            if (!initCamera())
                bValid = false;

            if (bValid) {

                for (CameraEntry camera : cameras) {

                    if (camera.Enable) {
                        if (useV40)
                            bValid = LoginV40(camera);
                        else
                            bValid = Login(camera);

                        if (bValid) {
                            bValid = StartRealPlayer(camera);
                        }

                        if (bValid) {
                            bValid = SetAlarm(camera);
                        }

                        if (!bValid)
                            break;
                    }
                }
            }

            if (!bValid) {
                throw new VantiqHikVisionException("setupHikVision failed");
            }

        } catch (Exception e) {

            log.error("HikVision::setupHikVision failed {}", e);
            reportHikVisionError(e);
        }
    }

    /**
     * function responsible for activating the command based of the publish from
     * Vantiq , mainy for changing the andle and the zoom of the camera.
     * 
     * @param message
     * @return
     * @throws VantiqHikVisionException
     */
    int hanldeUpdateCommand(ExtensionServiceMessage message) throws VantiqHikVisionException {
        if (!oClient.isConnected()) {
            throw new VantiqHikVisionException(String.format("EasyDombus is not connected Code %d", 10));

        }
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String cameraId = (String) request.get("cameraId");
        int command = (Integer) request.get("command");
        int state = (Integer) request.get("state");

        log.info("HikVision::hanldeUpdateCommand Rx Command {} State {} on camera id {}", command, state, cameraId);

        CameraEntry ce = (CameraEntry) this.cameras.stream().filter(camera -> cameraId.equals(camera.CameraId))
                .findFirst().orElseGet(null);

        if (ce == null) {
            iLastErr = -2;
            throw new VantiqHikVisionException(String.format("Camera Id %s not found", cameraId));

        }

        if (!ce.Enable) {
            iLastErr = -1;
            throw new VantiqHikVisionException(String.format("Camera Id %s is not enabled", cameraId));
        }

        NativeLong userId = new NativeLong(ce.lUserID);
        Boolean rc = hCNetSDK.NET_DVR_PTZControl(userId, command, state);

        if (!rc) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            iLastErr = errorCode;
            log.error("HikVision::hanldeUpdateCommand failed on camera id {} error {}", cameraId, errorCode);

        }

        return 0;
    }

    /// --------------------------- Helper fucntions
    public void close() {
        // Close single connection if open

        bContinue = false;

        if (cameras != null) {
            for (CameraEntry camera : cameras) {
                if (camera.Enable) {
                    logout(camera);
                }
            }
        }

        if (executionPool != null) {
            executionPool.shutdownNow();
            executionPool = null;
        }

    }

    public void reportHikVisionError(Exception e) throws VantiqHikVisionException {
        String message = this.getClass().getCanonicalName() + ": A HikVision error occurred: " + e.getMessage()
                + ", Error Code: " + e.getCause();
        throw new VantiqHikVisionException(message);
    }

    void testVantiq() {
        // HCNetSDK.NET_VCA_RULE_ALARM struRuleAlarmInfo = new
        // HCNetSDK.NET_VCA_RULE_ALARM();
        HCNetSDK.NET_DVR_THERMOMETRY_ALARM struThermometryAlarm = new HCNetSDK.NET_DVR_THERMOMETRY_ALARM();
        int dwSize = struThermometryAlarm.size();

        struThermometryAlarm.dwSize = dwSize;

        ThermalNotification o = new ThermalNotification();
        o.CameraId = "test";
        o.EventType = "Test";
        // o.ThermalImageName =
        // "D:/TMP/Thermo/Device_ID_ThermoPic_[92.200.245.81]_lUerID_[0]_1718124008.jpg";
        // o.ImageName =
        // "D:/TMP/Thermo/Device_ID_Pic_[211.140.29.11]_lUerID_[0]_0100014302.jpg";
        o.ThermalImageName = "Device_ID_ThermoPic_[92.200.217.162]_lUerID_[0]_1335121507.jpg";
        o.ImageName = "Device_ID_Pic_[92.200.217.162]_lUerID_[0]_1335138206.jpg";

        HCNetSDK.NET_VCA_RULE_ALARM o1 = (HCNetSDK.NET_VCA_RULE_ALARM) testReadFromBinary("d:/tmp/thermo/rule.bin");
        o.Extended = struThermometryAlarm;

        // testDumpObjToBin(struThermometryAlarm,"d:/tmp/thermo/thermo.bin");

        sendNotificationWithUpload(o);
    }

    void testDumpObjToBin(Object obj, String filename) {
        try (OutputStream outputStream = new FileOutputStream(filename);) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            byte[] data = bos.toByteArray();

            outputStream.write(data, 0, data.length);
        } catch (IOException ex) {
            log.error("failed to write binary code {}", ex);
        }
    }

    Object testReadFromBinary(String fileName) {
        Object obj = null;
        try (
                // Reading the object from a file
                FileInputStream file = new FileInputStream(fileName);
                ObjectInputStream in = new ObjectInputStream(file);) {
            // Method for deserialization of object
            try {
                obj = in.readObject();
                System.out.println("Object has been deserialized ");
            } catch (ClassNotFoundException ex1) {

            }
        } catch (IOException ex) {
            System.out.println("IOException is caught");
        }
        return obj;
    }
}
