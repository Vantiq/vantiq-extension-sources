/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * HCNetSDK.java
 *
 * Created on 2009-9-14, 19:31:34
 */

/**
 *
 * @author Xubinfeng
 */

package io.vantiq.extsrc.HikVisionSource;

import java.io.Serializable;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;
import com.sun.jna.examples.win32.GDI32.RECT;
import com.sun.jna.examples.win32.W32API;
import com.sun.jna.examples.win32.W32API.HANDLE;
import com.sun.jna.examples.win32.W32API.HDC;
import com.sun.jna.examples.win32.W32API.HWND;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.ShortByReference;

//SDK HCNetSDK.dll
public interface HCNetSDK extends Library {

	HCNetSDK INSTANCE = (HCNetSDK) Native.loadLibrary("hcnetsdk", HCNetSDK.class);

	public static final int MAX_NAMELEN = 16;
	public static final int MAX_RIGHT = 32;
	public static final int NAME_LEN = 32;
	public static final int PASSWD_LEN = 16;
	public static final int SERIALNO_LEN = 48;
	public static final int MACADDR_LEN = 6;
	public static final int MAX_ETHERNET = 2;
	public static final int PATHNAME_LEN = 128;
	public static final int MAX_TIMESEGMENT_V30 = 8;
	public static final int MAX_TIMESEGMENT = 4; // 8000
	public static final int MAX_SHELTERNUM = 4; // 8000
	public static final int MAX_DAYS = 7; //
	public static final int PHONENUMBER_LEN = 32; // pppoe
	public static final int MAX_DISKNUM_V30 = 33; // 9000 /* 33 ( 16 SATA 1 eSATA 16 NFS ) */
	public static final int MAX_DISKNUM = 16; // 8000
	public static final int MAX_DISKNUM_V10 = 8; // 1.2
	public static final int MAX_WINDOW_V30 = 32; // 9000
	public static final int MAX_WINDOW = 16; // 8000
	public static final int MAX_VGA_V30 = 4; // 9000 VGA
	public static final int MAX_VGA = 1; // 8000 VGA
	public static final int MAX_USERNUM_V30 = 32; // 9000
	public static final int MAX_USERNUM = 16; // 8000
	public static final int MAX_EXCEPTIONNUM_V30 = 32; // 9000
	public static final int MAX_EXCEPTIONNUM = 16; // 8000
	public static final int MAX_LINK = 6; // 8000
	public static final int MAX_DECPOOLNUM = 4; //
	public static final int MAX_DECNUM = 4; // ( , )
	public static final int MAX_TRANSPARENTNUM = 2; //
	public static final int MAX_CYCLE_CHAN = 16; //
	public static final int MAX_DIRNAME_LENGTH = 80; //
	public static final int MAX_STRINGNUM_V30 = 8; // 9000 OSD
	public static final int MAX_STRINGNUM = 4; // 8000 OSD
	public static final int MAX_STRINGNUM_EX = 8; // 8000
	public static final int MAX_AUXOUT_V30 = 16; // 9000
	public static final int MAX_AUXOUT = 4; // 8000
	public static final int MAX_HD_GROUP = 16; // 9000
	public static final int MAX_NFS_DISK = 8; // 8000 NFS
	public static final int IW_ESSID_MAX_SIZE = 32; // WIFI SSID
	public static final int IW_ENCODING_TOKEN_MAX = 32; // WIFI
	public static final int MAX_SERIAL_NUM = 64; //
	public static final int MAX_DDNS_NUMS = 10; // 9000 ddns
	public static final int MAX_DOMAIN_NAME = 64; /*    */

	public static final int MAX_EMAIL_ADDR_LEN = 48; // email
	public static final int MAX_EMAIL_PWD_LEN = 32; // email
	public static final int MAXPROGRESS = 100; //
	public static final int MAX_SERIALNUM = 2; // 8000 1-232, 2-485
	public static final int CARDNUM_LEN = 20; //
	public static final int MAX_VIDEOOUT_V30 = 4; // 9000
	public static final int MAX_VIDEOOUT = 2; // 8000
	public static final int MAX_PRESET_V30 = 256; /* 9000 */
	public static final int MAX_TRACK_V30 = 256; /* 9000 */
	public static final int MAX_CRUISE_V30 = 256; /* 9000 */
	public static final int MAX_PRESET = 128; /* 8000 */
	public static final int MAX_TRACK = 128; /* 8000 */
	public static final int MAX_CRUISE = 128; /* 8000 */
	public static final int CRUISE_MAX_PRESET_NUMS = 32; /*       */
	public static final int MAX_SERIAL_PORT = 8; // 9000 232
	public static final int MAX_PREVIEW_MODE = 8; /* 1 ,4 ,9 ,16 .... */
	public static final int MAX_MATRIXOUT = 16; /*       */
	public static final int LOG_INFO_LEN = 11840; /*    */
	public static final int DESC_LEN = 16; /*     */
	public static final int PTZ_PROTOCOL_NUM = 200; /* 9000 */
	public static final int MAX_AUDIO = 1; // 8000
	public static final int MAX_AUDIO_V30 = 2; // 9000
	public static final int MAX_CHANNUM = 16; // 8000
	public static final int MAX_ALARMIN = 16; // 8000
	public static final int MAX_ALARMOUT = 4; // 8000
	// 9000 IPC
	public static final int MAX_ANALOG_CHANNUM = 32; // 32
	public static final int MAX_ANALOG_ALARMOUT = 32; // 32
	public static final int MAX_ANALOG_ALARMIN = 32; // 32
	public static final int MAX_IP_DEVICE = 32; // IP
	public static final int MAX_IP_CHANNEL = 32; // IP
	public static final int MAX_IP_ALARMIN = 128; //
	public static final int MAX_IP_ALARMOUT = 64; //

	/* IP */
	public static final int MAX_CHANNUM_V30 = (MAX_ANALOG_CHANNUM + MAX_IP_CHANNEL);// 64
	public static final int MAX_ALARMOUT_V30 = (MAX_ANALOG_ALARMOUT + MAX_IP_ALARMOUT);// 96
	public static final int MAX_ALARMIN_V30 = (MAX_ANALOG_ALARMIN + MAX_IP_ALARMIN);// 160

	/******************* begin **********************/
	public static final int NET_DVR_NOERROR = 0; //
	public static final int NET_DVR_PASSWORD_ERROR = 1; //
	public static final int NET_DVR_NOENOUGHPRI = 2;//
	public static final int NET_DVR_NOINIT = 3;//
	public static final int NET_DVR_CHANNEL_ERROR = 4; //
	public static final int NET_DVR_OVER_MAXLINK = 5; // DVR
	public static final int NET_DVR_VERSIONNOMATCH = 6; //
	public static final int NET_DVR_NETWORK_FAIL_CONNECT = 7;//
	public static final int NET_DVR_NETWORK_SEND_ERROR = 8; //
	public static final int NET_DVR_NETWORK_RECV_ERROR = 9; //
	public static final int NET_DVR_NETWORK_RECV_TIMEOUT = 10; //
	public static final int NET_DVR_NETWORK_ERRORDATA = 11; //
	public static final int NET_DVR_ORDER_ERROR = 12; //
	public static final int NET_DVR_OPERNOPERMIT = 13; //
	public static final int NET_DVR_COMMANDTIMEOUT = 14; // DVR
	public static final int NET_DVR_ERRORSERIALPORT = 15; //
	public static final int NET_DVR_ERRORALARMPORT = 16; //
	public static final int NET_DVR_PARAMETER_ERROR = 17;//
	public static final int NET_DVR_CHAN_EXCEPTION = 18; //
	public static final int NET_DVR_NODISK = 19; //
	public static final int NET_DVR_ERRORDISKNUM = 20; //
	public static final int NET_DVR_DISK_FULL = 21; //
	public static final int NET_DVR_DISK_ERROR = 22;//
	public static final int NET_DVR_NOSUPPORT = 23;//
	public static final int NET_DVR_BUSY = 24;//
	public static final int NET_DVR_MODIFY_FAIL = 25;//
	public static final int NET_DVR_PASSWORD_FORMAT_ERROR = 26;//
	public static final int NET_DVR_DISK_FORMATING = 27; // ,
	public static final int NET_DVR_DVRNORESOURCE = 28; // DVR
	public static final int NET_DVR_DVROPRATEFAILED = 29; // DVR
	public static final int NET_DVR_OPENHOSTSOUND_FAIL = 30; // PC
	public static final int NET_DVR_DVRVOICEOPENED = 31; //
	public static final int NET_DVR_TIMEINPUTERROR = 32; //
	public static final int NET_DVR_NOSPECFILE = 33; //
	public static final int NET_DVR_CREATEFILE_ERROR = 34; //
	public static final int NET_DVR_FILEOPENFAIL = 35; //
	public static final int NET_DVR_OPERNOTFINISH = 36; //
	public static final int NET_DVR_GETPLAYTIMEFAIL = 37; //
	public static final int NET_DVR_PLAYFAIL = 38; //
	public static final int NET_DVR_FILEFORMAT_ERROR = 39;//
	public static final int NET_DVR_DIR_ERROR = 40; //
	public static final int NET_DVR_ALLOC_RESOURCE_ERROR = 41;//
	public static final int NET_DVR_AUDIO_MODE_ERROR = 42; //
	public static final int NET_DVR_NOENOUGH_BUF = 43; //
	public static final int NET_DVR_CREATESOCKET_ERROR = 44; // SOCKET
	public static final int NET_DVR_SETSOCKET_ERROR = 45; // SOCKET
	public static final int NET_DVR_MAX_NUM = 46; //
	public static final int NET_DVR_USERNOTEXIST = 47; //
	public static final int NET_DVR_WRITEFLASHERROR = 48;// FLASH
	public static final int NET_DVR_UPGRADEFAIL = 49;// DVR
	public static final int NET_DVR_CARDHAVEINIT = 50; //
	public static final int NET_DVR_PLAYERFAILED = 51; //
	public static final int NET_DVR_MAX_USERNUM = 52; //
	public static final int NET_DVR_GETLOCALIPANDMACFAIL = 53;// IP
	public static final int NET_DVR_NOENCODEING = 54; //
	public static final int NET_DVR_IPMISMATCH = 55; // IP
	public static final int NET_DVR_MACMISMATCH = 56;// MAC
	public static final int NET_DVR_UPGRADELANGMISMATCH = 57;//
	public static final int NET_DVR_MAX_PLAYERPORT = 58;//
	public static final int NET_DVR_NOSPACEBACKUP = 59;//
	public static final int NET_DVR_NODEVICEBACKUP = 60; //
	public static final int NET_DVR_PICTURE_BITS_ERROR = 61; // , 24
	public static final int NET_DVR_PICTURE_DIMENSION_ERROR = 62;// * , 128*256
	public static final int NET_DVR_PICTURE_SIZ_ERROR = 63; // , 100K
	public static final int NET_DVR_LOADPLAYERSDKFAILED = 64; // Player Sdk
	public static final int NET_DVR_LOADPLAYERSDKPROC_ERROR = 65; // Player Sdk
	public static final int NET_DVR_LOADDSSDKFAILED = 66; // DSsdk
	public static final int NET_DVR_LOADDSSDKPROC_ERROR = 67; // DsSdk
	public static final int NET_DVR_DSSDK_ERROR = 68; // DsSdk
	public static final int NET_DVR_VOICEMONOPOLIZE = 69; //
	public static final int NET_DVR_JOINMULTICASTFAILED = 70; //
	public static final int NET_DVR_CREATEDIR_ERROR = 71; //
	public static final int NET_DVR_BINDSOCKET_ERROR = 72; //
	public static final int NET_DVR_SOCKETCLOSE_ERROR = 73; // socket ,
	public static final int NET_DVR_USERID_ISUSING = 74; // ID
	public static final int NET_DVR_SOCKETLISTEN_ERROR = 75; //
	public static final int NET_DVR_PROGRAM_EXCEPTION = 76; //
	public static final int NET_DVR_WRITEFILE_FAILED = 77; //
	public static final int NET_DVR_FORMAT_READONLY = 78;//
	public static final int NET_DVR_WITHSAMEUSERNAME = 79;//
	public static final int NET_DVR_DEVICETYPE_ERROR = 80; /*      */
	public static final int NET_DVR_LANGUAGE_ERROR = 81; /*       */
	public static final int NET_DVR_PARAVERSION_ERROR = 82; /*      */
	public static final int NET_DVR_IPCHAN_NOTALIVE = 83; /* IP */
	public static final int NET_DVR_RTSP_SDK_ERROR = 84; /* IPC StreamTransClient.dll */
	public static final int NET_DVR_CONVERT_SDK_ERROR = 85; /*      */
	public static final int NET_DVR_IPC_COUNT_OVERFLOW = 86; /* ip */
	public static final int NET_PLAYM4_NOERROR = 500; // no error
	public static final int NET_PLAYM4_PARA_OVER = 501;// input parameter is invalid;
	public static final int NET_PLAYM4_ORDER_ERROR = 502;// The order of the function to be called is error.
	public static final int NET_PLAYM4_TIMER_ERROR = 503;// Create multimedia clock failed;
	public static final int NET_PLAYM4_DEC_VIDEO_ERROR = 504;// Decode video data failed.
	public static final int NET_PLAYM4_DEC_AUDIO_ERROR = 505;// Decode audio data failed.
	public static final int NET_PLAYM4_ALLOC_MEMORY_ERROR = 506; // Allocate memory failed.
	public static final int NET_PLAYM4_OPEN_FILE_ERROR = 507; // Open the file failed.
	public static final int NET_PLAYM4_CREATE_OBJ_ERROR = 508;// Create thread or event failed
	public static final int NET_PLAYM4_CREATE_DDRAW_ERROR = 509;// Create DirectDraw object failed.
	public static final int NET_PLAYM4_CREATE_OFFSCREEN_ERROR = 510;// failed when creating off-screen surface.
	public static final int NET_PLAYM4_BUF_OVER = 511; // buffer is overflow
	public static final int NET_PLAYM4_CREATE_SOUND_ERROR = 512; // failed when creating audio device.
	public static final int NET_PLAYM4_SET_VOLUME_ERROR = 513;// Set volume failed
	public static final int NET_PLAYM4_SUPPORT_FILE_ONLY = 514;// The function only support play file.
	public static final int NET_PLAYM4_SUPPORT_STREAM_ONLY = 515;// The function only support play stream.
	public static final int NET_PLAYM4_SYS_NOT_SUPPORT = 516;// System not support.
	public static final int NET_PLAYM4_FILEHEADER_UNKNOWN = 517; // No file header.
	public static final int NET_PLAYM4_VERSION_INCORRECT = 518; // The version of decoder and encoder is not adapted.
	public static final int NET_PALYM4_INIT_DECODER_ERROR = 519; // Initialize decoder failed.
	public static final int NET_PLAYM4_CHECK_FILE_ERROR = 520; // The file data is unknown.
	public static final int NET_PLAYM4_INIT_TIMER_ERROR = 521; // Initialize multimedia clock failed.
	public static final int NET_PLAYM4_BLT_ERROR = 522;// Blt failed.
	public static final int NET_PLAYM4_UPDATE_ERROR = 523;// Update failed.
	public static final int NET_PLAYM4_OPEN_FILE_ERROR_MULTI = 524; // openfile error, streamtype is multi
	public static final int NET_PLAYM4_OPEN_FILE_ERROR_VIDEO = 525; // openfile error, streamtype is video
	public static final int NET_PLAYM4_JPEG_COMPRESS_ERROR = 526; // JPEG compress error
	public static final int NET_PLAYM4_EXTRACT_NOT_SUPPORT = 527; // Don't support the version of this file.
	public static final int NET_PLAYM4_EXTRACT_DATA_ERROR = 528; // extract video data failed.
	/******************* end **********************/
	/*************************************************
	 * NET_DVR_IsSupport() 1-9 ( TRUE) ;
	 **************************************************/
	public static final int NET_DVR_SUPPORT_DDRAW = 0x01;// DIRECTDRAW, , ;
	public static final int NET_DVR_SUPPORT_BLT = 0x02;// BLT , , ;
	public static final int NET_DVR_SUPPORT_BLTFOURCC = 0x04;// BLT , , RGB ;
	public static final int NET_DVR_SUPPORT_BLTSHRINKX = 0x08;// BLT X ; , ;
	public static final int NET_DVR_SUPPORT_BLTSHRINKY = 0x10;// BLT Y ; , ;
	public static final int NET_DVR_SUPPORT_BLTSTRETCHX = 0x20;// BLT X ; , ;
	public static final int NET_DVR_SUPPORT_BLTSTRETCHY = 0x40;// BLT Y ; , ;
	public static final int NET_DVR_SUPPORT_SSE = 0x80;// CPU SSE ,Intel Pentium3 SSE ;
	public static final int NET_DVR_SUPPORT_MMX = 0x100;// CPU MMX ,Intel Pentium3 SSE ;
	/********************** begin *************************/
	public static final int LIGHT_PWRON = 2; /*    */
	public static final int WIPER_PWRON = 3; /*    */
	public static final int FAN_PWRON = 4; /*    */
	public static final int HEATER_PWRON = 5; /*      */
	public static final int AUX_PWRON1 = 6; /*     */
	public static final int AUX_PWRON2 = 7; /*     */
	public static final int SET_PRESET = 8; /*    */
	public static final int CLE_PRESET = 9; /*    */
	public static final int ZOOM_IN = 11; /* SS ( ) */
	public static final int ZOOM_OUT = 12; /* SS ( ) */
	public static final int FOCUS_NEAR = 13; /* SS */
	public static final int FOCUS_FAR = 14; /* SS */
	public static final int IRIS_OPEN = 15; /* SS */
	public static final int IRIS_CLOSE = 16; /* SS */
	public static final int TILT_UP = 21; /* SS */
	public static final int TILT_DOWN = 22; /* SS */
	public static final int PAN_LEFT = 23; /* SS */
	public static final int PAN_RIGHT = 24; /* SS */
	public static final int UP_LEFT = 25; /* SS */
	public static final int UP_RIGHT = 26; /* SS */
	public static final int DOWN_LEFT = 27; /* SS */
	public static final int DOWN_RIGHT = 28; /* SS */
	public static final int PAN_AUTO = 29; /* SS */
	public static final int FILL_PRE_SEQ = 30; /*       */
	public static final int SET_SEQ_DWELL = 31; /*     */
	public static final int SET_SEQ_SPEED = 32; /*    */
	public static final int CLE_PRE_SEQ = 33;/*      */
	public static final int STA_MEM_CRUISE = 34;/*    */
	public static final int STO_MEM_CRUISE = 35;/*    */
	public static final int RUN_CRUISE = 36; /*     */
	public static final int RUN_SEQ = 37; /*     */
	public static final int STOP_SEQ = 38; /*     */
	public static final int GOTO_PRESET = 39; /*      */
	public static final int DEL_SEQ = 43; //
	public static final int STOP_CRUISE = 44; /*    */
	public static final int DELETE_CRUISE = 45;/*    */
	public static final int DELETE_ALL_CRUISE = 46; /*    */
	public static final int NET_DVR_CONTROL_PTZ_PATTERN = 3313;/*     */

	/********************** end *************************/
	/*************************************************
	 * NET_DVR_PlayBackControl NET_DVR_PlayControlLocDisplay NET_DVR_DecPlayBackCtrl
	 **************************************************/
	public static final int NET_DVR_PLAYSTART = 1;//
	public static final int NET_DVR_PLAYSTOP = 2;//
	public static final int NET_DVR_PLAYPAUSE = 3;//
	public static final int NET_DVR_PLAYRESTART = 4;//
	public static final int NET_DVR_PLAYFAST = 5;//
	public static final int NET_DVR_PLAYSLOW = 6;//
	public static final int NET_DVR_PLAYNORMAL = 7;//
	public static final int NET_DVR_PLAYFRAME = 8;//
	public static final int NET_DVR_PLAYSTARTAUDIO = 9;//
	public static final int NET_DVR_PLAYSTOPAUDIO = 10;//
	public static final int NET_DVR_PLAYAUDIOVOLUME = 11;//
	public static final int NET_DVR_PLAYSETPOS = 12;//
	public static final int NET_DVR_PLAYGETPOS = 13;
	public static final int NET_DVR_PLAYGETTIME = 14;// ( )
	public static final int NET_DVR_PLAYGETFRAME = 15;// ( )
	public static final int NET_DVR_GETTOTALFRAMES = 16;// ( )
	public static final int NET_DVR_GETTOTALTIME = 17;// ( )
	public static final int NET_DVR_THROWBFRAME = 20;// B
	public static final int NET_DVR_SETSPEED = 24;//
	public static final int NET_DVR_KEEPALIVE = 25;// ( , 2 )
	// :
	/* key value send to CONFIG program */
	public static final int KEY_CODE_1 = 1;
	public static final int KEY_CODE_2 = 2;
	public static final int KEY_CODE_3 = 3;
	public static final int KEY_CODE_4 = 4;
	public static final int KEY_CODE_5 = 5;
	public static final int KEY_CODE_6 = 6;
	public static final int KEY_CODE_7 = 7;
	public static final int KEY_CODE_8 = 8;
	public static final int KEY_CODE_9 = 9;
	public static final int KEY_CODE_0 = 10;
	public static final int KEY_CODE_POWER = 11;
	public static final int KEY_CODE_MENU = 12;
	public static final int KEY_CODE_ENTER = 13;
	public static final int KEY_CODE_CANCEL = 14;
	public static final int KEY_CODE_UP = 15;
	public static final int KEY_CODE_DOWN = 16;
	public static final int KEY_CODE_LEFT = 17;
	public static final int KEY_CODE_RIGHT = 18;
	public static final int KEY_CODE_EDIT = 19;
	public static final int KEY_CODE_ADD = 20;
	public static final int KEY_CODE_MINUS = 21;
	public static final int KEY_CODE_PLAY = 22;
	public static final int KEY_CODE_REC = 23;
	public static final int KEY_CODE_PAN = 24;
	public static final int KEY_CODE_M = 25;
	public static final int KEY_CODE_A = 26;
	public static final int KEY_CODE_F1 = 27;
	public static final int KEY_CODE_F2 = 28;

	/* for PTZ control */
	public static final int KEY_PTZ_UP_START = KEY_CODE_UP;
	public static final int KEY_PTZ_UP_STO = 32;
	public static final int KEY_PTZ_DOWN_START = KEY_CODE_DOWN;
	public static final int KEY_PTZ_DOWN_STOP = 33;
	public static final int KEY_PTZ_LEFT_START = KEY_CODE_LEFT;
	public static final int KEY_PTZ_LEFT_STOP = 34;
	public static final int KEY_PTZ_RIGHT_START = KEY_CODE_RIGHT;
	public static final int KEY_PTZ_RIGHT_STOP = 35;
	public static final int KEY_PTZ_AP1_START = KEY_CODE_EDIT;/* + */
	public static final int KEY_PTZ_AP1_STOP = 36;
	public static final int KEY_PTZ_AP2_START = KEY_CODE_PAN;/* - */
	public static final int KEY_PTZ_AP2_STOP = 37;
	public static final int KEY_PTZ_FOCUS1_START = KEY_CODE_A;/* + */
	public static final int KEY_PTZ_FOCUS1_STOP = 38;
	public static final int KEY_PTZ_FOCUS2_START = KEY_CODE_M;/* - */
	public static final int KEY_PTZ_FOCUS2_STOP = 39;
	public static final int KEY_PTZ_B1_START = 40;/* + */
	public static final int KEY_PTZ_B1_STOP = 41;
	public static final int KEY_PTZ_B2_START = 42;/* - */
	public static final int KEY_PTZ_B2_STOP = 43;
	// 9000
	public static final int KEY_CODE_11 = 44;
	public static final int KEY_CODE_12 = 45;
	public static final int KEY_CODE_13 = 46;
	public static final int KEY_CODE_14 = 47;
	public static final int KEY_CODE_15 = 48;
	public static final int KEY_CODE_16 = 49;
	/************************* begin *******************************/
	// NET_DVR_SetDVRConfig NET_DVR_GetDVRConfig,
	public static final int NET_DVR_GET_DEVICECFG = 100; //
	public static final int NET_DVR_SET_DEVICECFG = 101; //
	public static final int NET_DVR_GET_NETCFG = 102; //
	public static final int NET_DVR_SET_NETCFG = 103; //
	public static final int NET_DVR_GET_PICCFG = 104; //
	public static final int NET_DVR_SET_PICCFG = 105; //
	public static final int NET_DVR_GET_COMPRESSCFG = 106; //
	public static final int NET_DVR_SET_COMPRESSCFG = 107; //
	public static final int NET_DVR_GET_RECORDCFG = 108; //
	public static final int NET_DVR_SET_RECORDCFG = 109; //
	public static final int NET_DVR_GET_DECODERCFG = 110; //
	public static final int NET_DVR_SET_DECODERCFG = 111; //
	public static final int NET_DVR_GET_RS232CFG = 112; // 232
	public static final int NET_DVR_SET_RS232CFG = 113; // 232
	public static final int NET_DVR_GET_ALARMINCFG = 114; //
	public static final int NET_DVR_SET_ALARMINCFG = 115; //
	public static final int NET_DVR_GET_ALARMOUTCFG = 116; //
	public static final int NET_DVR_SET_ALARMOUTCFG = 117; //
	public static final int NET_DVR_GET_TIMECFG = 118; // DVR
	public static final int NET_DVR_SET_TIMECFG = 119; // DVR
	public static final int NET_DVR_GET_PREVIEWCFG = 120; //
	public static final int NET_DVR_SET_PREVIEWCFG = 121; //
	public static final int NET_DVR_GET_VIDEOOUTCFG = 122; //
	public static final int NET_DVR_SET_VIDEOOUTCFG = 123; //
	public static final int NET_DVR_GET_USERCFG = 124; //
	public static final int NET_DVR_SET_USERCFG = 125; //
	public static final int NET_DVR_GET_EXCEPTIONCFG = 126; //
	public static final int NET_DVR_SET_EXCEPTIONCFG = 127; //
	public static final int NET_DVR_GET_ZONEANDDST = 128; //
	public static final int NET_DVR_SET_ZONEANDDST = 129; //
	public static final int NET_DVR_GET_SHOWSTRING = 130; //
	public static final int NET_DVR_SET_SHOWSTRING = 131; //
	public static final int NET_DVR_GET_EVENTCOMPCFG = 132; //
	public static final int NET_DVR_SET_EVENTCOMPCFG = 133; //
	public static final int NET_DVR_GET_AUXOUTCFG = 140; // (HS 2006-02-28)
	public static final int NET_DVR_SET_AUXOUTCFG = 141; // (HS 2006-02-28)
	public static final int NET_DVR_GET_PREVIEWCFG_AUX = 142; // -s (-s 2006-04-13)
	public static final int NET_DVR_SET_PREVIEWCFG_AUX = 143; // -s (-s 2006-04-13)
	public static final int NET_DVR_GET_PICCFG_EX = 200; // (SDK_V14 )
	public static final int NET_DVR_SET_PICCFG_EX = 201; // (SDK_V14 )
	public static final int NET_DVR_GET_USERCFG_EX = 202; // (SDK_V15 )
	public static final int NET_DVR_SET_USERCFG_EX = 203; // (SDK_V15 )
	public static final int NET_DVR_GET_COMPRESSCFG_EX = 204; // (SDK_V15 2006-05-15)
	public static final int NET_DVR_SET_COMPRESSCFG_EX = 205; // (SDK_V15 2006-05-15)
	public static final int NET_DVR_GET_NETAPPCFG = 222; // NTP/DDNS/EMAIL
	public static final int NET_DVR_SET_NETAPPCFG = 223; // NTP/DDNS/EMAIL
	public static final int NET_DVR_GET_NTPCFG = 224; // NTP
	public static final int NET_DVR_SET_NTPCFG = 225; // NTP
	public static final int NET_DVR_GET_DDNSCFG = 226; // DDNS
	public static final int NET_DVR_SET_DDNSCFG = 227; // DDNS
	public static final int NET_DVR_GET_DEVICECFG_V40 = 1100; // ( )
	public static final int NET_DVR_GET_AUDIO_INPUT = 3201; //
	public static final int NET_DVR_SET_AUDIO_INPUT = 3202; //
	// NET_DVR_EMAILPARA
	public static final int NET_DVR_GET_EMAILCFG = 228; // EMAIL
	public static final int NET_DVR_SET_EMAILCFG = 229; // EMAIL
	public static final int NET_DVR_GET_NFSCFG = 230; /* NFS disk config */
	public static final int NET_DVR_SET_NFSCFG = 231; /* NFS disk config */
	public static final int NET_DVR_GET_SHOWSTRING_EX = 238; // ( 8 )
	public static final int NET_DVR_SET_SHOWSTRING_EX = 239; // ( 8 )
	public static final int NET_DVR_GET_NETCFG_OTHER = 244; //
	public static final int NET_DVR_SET_NETCFG_OTHER = 245; //
	// NET_DVR_EMAILCFG
	public static final int NET_DVR_GET_EMAILPARACFG = 250; // Get EMAIL parameters
	public static final int NET_DVR_SET_EMAILPARACFG = 251; // Setup EMAIL parameters
	public static final int NET_DVR_GET_DDNSCFG_EX = 274;// DDNS
	public static final int NET_DVR_SET_DDNSCFG_EX = 275;// DDNS
	public static final int NET_DVR_SET_PTZPOS = 292; // PTZ
	public static final int NET_DVR_GET_PTZPOS = 293; // PTZ
	public static final int NET_DVR_GET_PTZSCOPE = 294;// PTZ
	// NET_DVR_GetDeviceConfig NET_DVR_SetDeviceConfig
	public static final int NET_DVR_GET_MULTI_STREAM_COMPRESSIONCFG = 3216;//
	public static final int NET_DVR_SET_MULTI_STREAM_COMPRESSIONCFG = 3217;//
	/***************************
	 * DS9000 (_V30) begin
	 *****************************/
	// (NET_DVR_NETCFG_V30 )
	public static final int NET_DVR_GET_NETCFG_V30 = 1000; //
	public static final int NET_DVR_SET_NETCFG_V30 = 1001; //
	// (NET_DVR_PICCFG_V30 )
	public static final int NET_DVR_GET_PICCFG_V30 = 1002; //
	public static final int NET_DVR_SET_PICCFG_V30 = 1003; //
	// (NET_DVR_RECORD_V30 )
	public static final int NET_DVR_GET_RECORDCFG_V30 = 1004; //
	public static final int NET_DVR_SET_RECORDCFG_V30 = 1005; //
	// (NET_DVR_USER_V30 )
	public static final int NET_DVR_GET_USERCFG_V30 = 1006; //
	public static final int NET_DVR_SET_USERCFG_V30 = 1007; //
	// 9000DDNS (NET_DVR_DDNSPARA_V30 )
	public static final int NET_DVR_GET_DDNSCFG_V30 = 1010; // DDNS(9000 )
	public static final int NET_DVR_SET_DDNSCFG_V30 = 1011; // DDNS(9000 )
	// EMAIL (NET_DVR_EMAILCFG_V30 )
	public static final int NET_DVR_GET_EMAILCFG_V30 = 1012;// EMAIL
	public static final int NET_DVR_SET_EMAILCFG_V30 = 1013;// EMAIL
	// (NET_DVR_CRUISE_PARA )
	public static final int NET_DVR_GET_CRUISE = 1020;
	public static final int NET_DVR_SET_CRUISE = 1021;
	// (NET_DVR_ALARMINCFG_V30 )
	public static final int NET_DVR_GET_ALARMINCFG_V30 = 1024;
	public static final int NET_DVR_SET_ALARMINCFG_V30 = 1025;
	// (NET_DVR_ALARMOUTCFG_V30 )
	public static final int NET_DVR_GET_ALARMOUTCFG_V30 = 1026;
	public static final int NET_DVR_SET_ALARMOUTCFG_V30 = 1027;
	// (NET_DVR_VIDEOOUT_V30 )
	public static final int NET_DVR_GET_VIDEOOUTCFG_V30 = 1028;
	public static final int NET_DVR_SET_VIDEOOUTCFG_V30 = 1029;
	// (NET_DVR_SHOWSTRING_V30 )
	public static final int NET_DVR_GET_SHOWSTRING_V30 = 1030;
	public static final int NET_DVR_SET_SHOWSTRING_V30 = 1031;
	// (NET_DVR_EXCEPTION_V30 )
	public static final int NET_DVR_GET_EXCEPTIONCFG_V30 = 1034;
	public static final int NET_DVR_SET_EXCEPTIONCFG_V30 = 1035;
	// 232 (NET_DVR_RS232CFG_V30 )
	public static final int NET_DVR_GET_RS232CFG_V30 = 1036;
	public static final int NET_DVR_SET_RS232CFG_V30 = 1037;
	// (NET_DVR_COMPRESSIONCFG_V30 )
	public static final int NET_DVR_GET_COMPRESSCFG_V30 = 1040;
	public static final int NET_DVR_SET_COMPRESSCFG_V30 = 1041;
	// 485 (NET_DVR_DECODERCFG_V30 )
	public static final int NET_DVR_GET_DECODERCFG_V30 = 1042; //
	public static final int NET_DVR_SET_DECODERCFG_V30 = 1043; //
	// (NET_DVR_PREVIEWCFG_V30 )
	public static final int NET_DVR_GET_PREVIEWCFG_V30 = 1044; //
	public static final int NET_DVR_SET_PREVIEWCFG_V30 = 1045; //
	// (NET_DVR_PREVIEWCFG_AUX_V30 )
	public static final int NET_DVR_GET_PREVIEWCFG_AUX_V30 = 1046; //
	public static final int NET_DVR_SET_PREVIEWCFG_AUX_V30 = 1047; //
	// IP (NET_DVR_IPPARACFG )
	public static final int NET_DVR_GET_IPPARACFG = 1048; // IP
	public static final int NET_DVR_SET_IPPARACFG = 1049; // IP
	// IP (NET_DVR_IPALARMINCFG )
	public static final int NET_DVR_GET_IPALARMINCFG = 1050; // IP
	public static final int NET_DVR_SET_IPALARMINCFG = 1051; // IP
	// IP (NET_DVR_IPALARMOUTCFG )
	public static final int NET_DVR_GET_IPALARMOUTCFG = 1052; // IP
	public static final int NET_DVR_SET_IPALARMOUTCFG = 1053; // IP
	// (NET_DVR_HDCFG )
	public static final int NET_DVR_GET_HDCFG = 1054; //
	public static final int NET_DVR_SET_HDCFG = 1055; //
	// (NET_DVR_HDGROUP_CFG )
	public static final int NET_DVR_GET_HDGROUP_CFG = 1056; //
	public static final int NET_DVR_SET_HDGROUP_CFG = 1057; //
	// (NET_DVR_COMPRESSION_AUDIO )
	public static final int NET_DVR_GET_COMPRESSCFG_AUD = 1058; //
	public static final int NET_DVR_SET_COMPRESSCFG_AUD = 1059; //
	//
	public static final int NET_DVR_GET_ISP_CAMERAPARAMCFG = 3255; //
	public static final int NET_DVR_SET_ISP_CAMERAPARAMCFG = 3256; //
	/*************************** DS9000 (_V30) end *****************************/
	/************************* end *******************************/
	/*******************      *************************/
	public static final int NET_DVR_FILE_SUCCESS = 1000; //
	public static final int NET_DVR_FILE_NOFIND = 1001; //
	public static final int NET_DVR_ISFINDING = 1002;//
	public static final int NET_DVR_NOMOREFILE = 1003;//
	public static final int NET_DVR_FILE_EXCEPTION = 1004;//
	/********************* begin ************************/
	public static final int COMM_ALARM = 0x1100; // 8000
	public static final int COMM_ALARM_RULE = 0x1102;// ׀׀־×·ײ־צ±¨¾¯׀ֵֿ¢£¬¶װ׃¦NET_VCA_RULE_ALARM Sagi^^^^^^

	public static final int COMM_TRADEINFO = 0x1500; // ATMDVR
	public static final int COMM_ALARM_V30 = 0x4000;// 9000
	public static final int COMM_ALARM_V40 = 0x4007;
	public static final int COMM_IPCCFG = 0x4001;// 9000 IPC
	public static final int COMM_ALARM_PDC = 0x1103; //
	public static final int COMM_UPLOAD_PLATE_RESULT = 0x2800;// ( )

	// Sagi^^^
	public static final int COMM_SNAP_MATCH_ALARM = 0x2902;
	public static final int COMM_UPLOAD_FACESNAP_RESULT = 0x1112;
	public static final int COMM_THERMOMETRY_ALARM = 0x5212; // sagi^^^
	public static final int COMM_ALARM_FACE_DETECTION = 4010; // sagi^^^

	/************* ( , ( )) ****************/
	public static final int EXCEPTION_EXCHANGE = 0x8000;//
	public static final int EXCEPTION_AUDIOEXCHANGE = 0x8001;//
	public static final int EXCEPTION_ALARM = 0x8002;//
	public static final int EXCEPTION_PREVIEW = 0x8003;//
	public static final int EXCEPTION_SERIAL = 0x8004;//
	public static final int EXCEPTION_RECONNECT = 0x8005; //
	public static final int EXCEPTION_ALARMRECONNECT = 0x8006;//
	public static final int EXCEPTION_SERIALRECONNECT = 0x8007;//
	public static final int EXCEPTION_PLAYBACK = 0x8010;//
	public static final int EXCEPTION_DISKFMT = 0x8011;//
	/********************    *********************/
	public static final int NET_DVR_SYSHEAD = 1;//
	public static final int NET_DVR_STREAMDATA = 2;// ( )
	public static final int NET_DVR_AUDIOSTREAMDATA = 3;//
	public static final int NET_DVR_STD_VIDEODATA = 4;//
	public static final int NET_DVR_STD_AUDIODATA = 5;//
	//
	public static final int NET_DVR_REALPLAYEXCEPTION = 111;//
	public static final int NET_DVR_REALPLAYNETCLOSE = 112;//
	public static final int NET_DVR_REALPLAY5SNODATA = 113;// 5s
	public static final int NET_DVR_REALPLAYRECONNECT = 114;//
	/********************    *********************/
	public static final int NET_DVR_PLAYBACKOVER = 101;//
	public static final int NET_DVR_PLAYBACKEXCEPTION = 102;//
	public static final int NET_DVR_PLAYBACKNETCLOSE = 103;//
	public static final int NET_DVR_PLAYBACK5SNODATA = 104; // 5s
	/********************* end ************************/
	// (DVR )
	/*     */
	public static final int DVR = 1; /* dvr NETRET_DVR */
	public static final int ATMDVR = 2; /* atm dvr */
	public static final int DVS = 3; /* DVS */
	public static final int DEC = 4; /* 6001D */
	public static final int ENC_DEC = 5; /* 6001F */
	public static final int DVR_HC = 6; /* 8000HC */
	public static final int DVR_HT = 7; /* 8000HT */
	public static final int DVR_HF = 8; /* 8000HF */
	public static final int DVR_HS = 9; /* 8000HS DVR(no audio) */
	public static final int DVR_HTS = 10; /* 8016HTS DVR(no audio) */
	public static final int DVR_HB = 11; /* HB DVR(SATA HD) */
	public static final int DVR_HCS = 12; /* 8000HCS DVR */
	public static final int DVS_A = 13; /* ATA DVS */
	public static final int DVR_HC_S = 14; /* 8000HC-S */
	public static final int DVR_HT_S = 15; /* 8000HT-S */
	public static final int DVR_HF_S = 16; /* 8000HF-S */
	public static final int DVR_HS_S = 17; /* 8000HS-S */
	public static final int ATMDVR_S = 18; /* ATM-S */
	public static final int LOWCOST_DVR = 19; /* 7000H */
	public static final int DEC_MAT = 20; /*    */
	public static final int DVR_MOBILE = 21; /* mobile DVR */
	public static final int DVR_HD_S = 22; /* 8000HD-S */
	public static final int DVR_HD_SL = 23; /* 8000HD-SL */
	public static final int DVR_HC_SL = 24; /* 8000HC-SL */
	public static final int DVR_HS_ST = 25; /* 8000HS_ST */
	public static final int DVS_HW = 26; /* 6000HW */
	public static final int IPCAM = 30; /* IP */
	public static final int MEGA_IPCAM = 31; /* X52MF ,752MF,852MF */
	public static final int IPCAM_X62MF = 32; /* X62MF 9000 ,762MF,862MF */
	public static final int IPDOME = 40; /* IP */
	public static final int MEGA_IPDOME = 41; /* IP */
	public static final int IPMOD = 50; /* IP */
	public static final int DS71XX_H = 71; /* DS71XXH_S */
	public static final int DS72XX_H_S = 72; /* DS72XXH_S */
	public static final int DS73XX_H_S = 73; /* DS73XXH_S */
	public static final int DS81XX_HS_S = 81; /* DS81XX_HS_S */
	public static final int DS81XX_HL_S = 82; /* DS81XX_HL_S */
	public static final int DS81XX_HC_S = 83; /* DS81XX_HC_S */
	public static final int DS81XX_HD_S = 84; /* DS81XX_HD_S */
	public static final int DS81XX_HE_S = 85; /* DS81XX_HE_S */
	public static final int DS81XX_HF_S = 86; /* DS81XX_HF_S */
	public static final int DS81XX_AH_S = 87; /* DS81XX_AH_S */
	public static final int DS81XX_AHF_S = 88; /* DS81XX_AHF_S */
	public static final int DS90XX_HF_S = 90; /* DS90XX_HF_S */
	public static final int DS91XX_HF_S = 91; /* DS91XX_HF_S */
	public static final int DS91XX_HD_S = 92; /* 91XXHD-S(MD) */

	/*   */
	//

	public static final int MAJOR_OPERATION = 0x3;
	public static final int MAJOR_EVENT = 0x5;

	//
	public static final int MINOR_START_DVR = 0x41; /*   */
	public static final int MINOR_STOP_DVR = 0x42;/*   */
	public static final int MINOR_STOP_ABNORMAL = 0x43;/*     */
	public static final int MINOR_REBOOT_DVR = 0x44; /*    */
	public static final int MINOR_LOCAL_LOGIN = 0x50; /*     */
	public static final int MINOR_LOCAL_LOGOUT = 0x51; /*    */
	public static final int MINOR_LOCAL_CFG_PARM = 0x52; /*    */
	public static final int MINOR_LOCAL_PLAYBYFILE = 0x53; /*       */
	public static final int MINOR_LOCAL_PLAYBYTIME = 0x54; /*       */
	public static final int MINOR_LOCAL_START_REC = 0x55; /*    */
	public static final int MINOR_LOCAL_STOP_REC = 0x56; /*    */
	public static final int MINOR_LOCAL_PTZCTRL = 0x57; /*    */
	public static final int MINOR_LOCAL_PREVIEW = 0x58;/* ( ) */
	public static final int MINOR_LOCAL_MODIFY_TIME = 0x59;/* ( ) */
	public static final int MINOR_LOCAL_UPGRADE = 0x5a;/*     */
	public static final int MINOR_LOCAL_RECFILE_OUTPUT = 0x5b; /*     */
	public static final int MINOR_LOCAL_FORMAT_HDD = 0x5c; /*      */
	public static final int MINOR_LOCAL_CFGFILE_OUTPUT = 0x5d; /*     */
	public static final int MINOR_LOCAL_CFGFILE_INPUT = 0x5e; /*     */
	public static final int MINOR_LOCAL_COPYFILE = 0x5f; /*    */
	public static final int MINOR_LOCAL_LOCKFILE = 0x60; /*     */
	public static final int MINOR_LOCAL_UNLOCKFILE = 0x61; /*     */
	public static final int MINOR_LOCAL_DVR_ALARM = 0x62; /*      */
	public static final int MINOR_IPC_ADD = 0x63; /* IPC */
	public static final int MINOR_IPC_DEL = 0x64; /* IPC */
	public static final int MINOR_IPC_SET = 0x65; /* IPC */
	public static final int MINOR_LOCAL_START_BACKUP = 0x66; /*    */
	public static final int MINOR_LOCAL_STOP_BACKUP = 0x67;/*    */
	public static final int MINOR_LOCAL_COPYFILE_START_TIME = 0x68;/*     */
	public static final int MINOR_LOCAL_COPYFILE_END_TIME = 0x69; /*     */
	public static final int MINOR_REMOTE_LOGIN = 0x70;/*     */
	public static final int MINOR_REMOTE_LOGOUT = 0x71;/*    */
	public static final int MINOR_REMOTE_START_REC = 0x72;/*    */
	public static final int MINOR_REMOTE_STOP_REC = 0x73;/*    */
	public static final int MINOR_START_TRANS_CHAN = 0x74;/*    */
	public static final int MINOR_STOP_TRANS_CHAN = 0x75; /*    */
	public static final int MINOR_REMOTE_GET_PARM = 0x76;/*    */
	public static final int MINOR_REMOTE_CFG_PARM = 0x77;/*    */
	public static final int MINOR_REMOTE_GET_STATUS = 0x78;/*    */
	public static final int MINOR_REMOTE_ARM = 0x79; /*     */
	public static final int MINOR_REMOTE_DISARM = 0x7a;/*     */
	public static final int MINOR_REMOTE_REBOOT = 0x7b; /*     */
	public static final int MINOR_START_VT = 0x7c;/*    */
	public static final int MINOR_STOP_VT = 0x7d;/*    */
	public static final int MINOR_REMOTE_UPGRADE = 0x7e; /*     */
	public static final int MINOR_REMOTE_PLAYBYFILE = 0x7f; /*      */
	public static final int MINOR_REMOTE_PLAYBYTIME = 0x80; /*      */
	public static final int MINOR_REMOTE_PTZCTRL = 0x81; /*    */
	public static final int MINOR_REMOTE_FORMAT_HDD = 0x82; /*      */
	public static final int MINOR_REMOTE_STOP = 0x83; /*     */
	public static final int MINOR_REMOTE_LOCKFILE = 0x84;/*    */
	public static final int MINOR_REMOTE_UNLOCKFILE = 0x85;/*    */
	public static final int MINOR_REMOTE_CFGFILE_OUTPUT = 0x86; /*     */
	public static final int MINOR_REMOTE_CFGFILE_INTPUT = 0x87; /*     */
	public static final int MINOR_REMOTE_RECFILE_OUTPUT = 0x88; /*     */
	public static final int MINOR_REMOTE_DVR_ALARM = 0x89; /*      */
	public static final int MINOR_REMOTE_IPC_ADD = 0x8a; /* IPC */
	public static final int MINOR_REMOTE_IPC_DEL = 0x8b;/* IPC */
	public static final int MINOR_REMOTE_IPC_SET = 0x8c; /* IPC */
	public static final int MINOR_REBOOT_VCA_LIB = 0x8d; /*    */

	/*    */
	//
	public static final int MAJOR_INFORMATION = 0x4; /*     */
	//
	public static final int MINOR_HDD_INFO = 0xa1;/*     */
	public static final int MINOR_SMART_INFO = 0xa2; /* SMART */
	public static final int MINOR_REC_START = 0xa3; /*     */
	public static final int MINOR_REC_STOP = 0xa4;/*     */
	public static final int MINOR_REC_OVERDUE = 0xa5;/*    */
	public static final int MINOR_LINK_START = 0xa6; // ivms,
	public static final int MINOR_LINK_STOP = 0xa7;// ivms,
	public static final int MINOR_NET_DISK_INFO = 0xa8;
	public static final int MINOR_RAID_INFO = 0xa9;
	public static final int MINOR_RUN_STATUS_INFO = 0xaa;
	// MAJOR_OPERATION=03, MINOR_LOCAL_CFG_PARM=0x52 MINOR_REMOTE_GET_PARM=0x76
	// MINOR_REMOTE_CFG_PARM=0x77 ,dwParaType: , :
	public static final int PARA_VIDEOOUT = 0x1;
	public static final int PARA_IMAGE = 0x2;
	public static final int PARA_ENCODE = 0x4;
	public static final int PARA_NETWORK = 0x8;
	public static final int PARA_ALARM = 0x10;
	public static final int PARA_EXCEPTION = 0x20;
	public static final int PARA_DECODER = 0x40; /*   */
	public static final int PARA_RS232 = 0x80;
	public static final int PARA_PREVIEW = 0x100;
	public static final int PARA_SECURITY = 0x200;
	public static final int PARA_DATETIME = 0x400;
	public static final int PARA_FRAMETYPE = 0x800; /*   */
	public static final int PARA_VCA_RULE = 0x1000; //

	//
	public static final int MAJOR_EXCEPTION = 0x2;
	//
	public static final int MINOR_RAID_ERROR = 0x20; /*     */
	public static final int MINOR_VI_LOST = 0x21;/*    */
	public static final int MINOR_ILLEGAL_ACCESS = 0x22;/*     */
	public static final int MINOR_HD_FULL = 0x23;/*   */
	public static final int MINOR_HD_ERROR = 0x24;/*     */
	public static final int MINOR_DCD_LOST = 0x25;/* MODEM ( ) */
	public static final int MINOR_IP_CONFLICT = 0x26; /* IP */
	public static final int MINOR_NET_BROKEN = 0x27; /*     */
	public static final int MINOR_REC_ERROR = 0x28; /*     */
	public static final int MINOR_IPC_NO_LINK = 0x29; /* IPC */
	public static final int MINOR_VI_EXCEPTION = 0x2a; /* ( ) */
	public static final int MINOR_IPC_IP_CONFLICT = 0x2b; /* ipc ip */
	public static final int MINOR_SENCE_EXCEPTION = 0x2c; //

	//
	public static final int MAJOR_ALARM = 0x1;
	//
	public static final int MINOR_ALARM_IN = 0x1; /*     */
	public static final int MINOR_ALARM_OUT = 0x2; /*     */
	public static final int MINOR_MOTDET_START = 0x3; /*     */
	public static final int MINOR_MOTDET_STOP = 0x4; /*     */
	public static final int MINOR_HIDE_ALARM_START = 0x5; /*    */
	public static final int MINOR_HIDE_ALARM_STOP = 0x6; /*    */
	public static final int MINOR_VCA_ALARM_START = 0x7; /*    */
	public static final int MINOR_VCA_ALARM_STOP = 0x8; /*    */
	public static final int MINOR_ITS_ALARM_START = 0x09; //
	public static final int MINOR_ITS_ALARM_STOP = 0x0A; //
	// 2010-11-10
	public static final int MINOR_NETALARM_START = 0x0b; /*    */
	public static final int MINOR_NETALARM_STOP = 0x0c; /*    */
	// 2010-12-16 , "MINOR_ALARM_IN"
	public static final int MINOR_NETALARM_RESUME = 0x0d; /*    */
	// 2012-4-5 IPC PIR
	public static final int MINOR_WIRELESS_ALARM_START = 0x0e; /*    */
	public static final int MINOR_WIRELESS_ALARM_STOP = 0x0f; /*    */
	public static final int MINOR_PIR_ALARM_START = 0x10; /*     */
	public static final int MINOR_PIR_ALARM_STOP = 0x11; /*     */
	public static final int MINOR_CALLHELP_ALARM_START = 0x12; /*    */
	public static final int MINOR_CALLHELP_ALARM_STOP = 0x13; /*    */
	public static final int MINOR_IPCHANNEL_ALARMIN_START = 0x14; //
	public static final int MINOR_IPCHANNEL_ALARMIN_STOP = 0x15; // :
	public static final int MINOR_DETECTFACE_ALARM_START = 0x16; /*     */
	public static final int MINOR_DETECTFACE_ALARM_STOP = 0x17; /*     */
	public static final int MINOR_VQD_ALARM_START = 0x18; // VQD
	public static final int MINOR_VQD_ALARM_STOP = 0x19; // VQD
	public static final int MINOR_VCA_SECNECHANGE_DETECTION = 0x1a; //
																	// 2013-07-16

	public static final int MINOR_SMART_REGION_EXITING_BEGIN = 0x1b; //
	public static final int MINOR_SMART_REGION_EXITING_END = 0x1c; //
	public static final int MINOR_SMART_LOITERING_BEGIN = 0x1d; //
	public static final int MINOR_SMART_LOITERING_END = 0x1e; //

	public static final int MINOR_VCA_ALARM_LINE_DETECTION_BEGIN = 0x20;
	public static final int MINOR_VCA_ALARM_LINE_DETECTION_END = 0x21;
	public static final int MINOR_VCA_ALARM_INTRUDE_BEGIN = 0x22; //
	public static final int MINOR_VCA_ALARM_INTRUDE_END = 0x23; //
	public static final int MINOR_VCA_ALARM_AUDIOINPUT = 0x24; //
	public static final int MINOR_VCA_ALARM_AUDIOABNORMAL = 0x25; //
	public static final int MINOR_VCA_DEFOCUS_DETECTION_BEGIN = 0x26; //
	public static final int MINOR_VCA_DEFOCUS_DETECTION_END = 0x27; //

	// NVR
	public static final int MINOR_EXT_ALARM = 0x28;/* IPC */
	public static final int MINOR_VCA_FACE_ALARM_BEGIN = 0x29; /*    */
	public static final int MINOR_SMART_REGION_ENTRANCE_BEGIN = 0x2a; //
	public static final int MINOR_SMART_REGION_ENTRANCE_END = 0x2b; //
	public static final int MINOR_SMART_PEOPLE_GATHERING_BEGIN = 0x2c; //
	public static final int MINOR_SMART_PEOPLE_GATHERING_END = 0x2d; //
	public static final int MINOR_SMART_FAST_MOVING_BEGIN = 0x2e; //
	public static final int MINOR_SMART_FAST_MOVING_END = 0x2f; //

	public static final int MINOR_VCA_FACE_ALARM_END = 0x30; /*    */
	public static final int MINOR_VCA_SCENE_CHANGE_ALARM_BEGIN = 0x31; /*     */
	public static final int MINOR_VCA_SCENE_CHANGE_ALARM_END = 0x32; /*     */
	public static final int MINOR_VCA_ALARM_AUDIOINPUT_BEGIN = 0x33; /*     */
	public static final int MINOR_VCA_ALARM_AUDIOINPUT_END = 0x34; /*     */
	public static final int MINOR_VCA_ALARM_AUDIOABNORMAL_BEGIN = 0x35; /*     */
	public static final int MINOR_VCA_ALARM_AUDIOABNORMAL_END = 0x36; /*     */

	public static final int MINOR_VCA_LECTURE_DETECTION_BEGIN = 0x37; //
	public static final int MINOR_VCA_LECTURE_DETECTION_END = 0x38; //
	public static final int MINOR_VCA_ALARM_AUDIOSTEEPDROP = 0x39; //
																	// 2014-03-21
	public static final int MINOR_VCA_ANSWER_DETECTION_BEGIN = 0x3a; //
	public static final int MINOR_VCA_ANSWER_DETECTION_END = 0x3b; //

	public static final int MINOR_SMART_PARKING_BEGIN = 0x3c; //
	public static final int MINOR_SMART_PARKING_END = 0x3d; //
	public static final int MINOR_SMART_UNATTENDED_BAGGAGE_BEGIN = 0x3e; //
	public static final int MINOR_SMART_UNATTENDED_BAGGAGE_END = 0x3f; //
	public static final int MINOR_SMART_OBJECT_REMOVAL_BEGIN = 0x40; //
	public static final int MINOR_SMART_OBJECT_REMOVAL_END = 0x41; //
	public static final int MINOR_SMART_VEHICLE_ALARM_START = 0x46; //
	public static final int MINOR_SMART_VEHICLE_ALARM_STOP = 0x47; //
	public static final int MINOR_THERMAL_FIREDETECTION = 0x48; //
	public static final int MINOR_THERMAL_FIREDETECTION_END = 0x49; //
	public static final int MINOR_SMART_VANDALPROOF_BEGIN = 0x50; //
	public static final int MINOR_SMART_VANDALPROOF_END = 0x51; //

	// 0x400-0x1000
	public static final int MINOR_ALARMIN_SHORT_CIRCUIT = 0x400; //
	public static final int MINOR_ALARMIN_BROKEN_CIRCUIT = 0x401; //
	public static final int MINOR_ALARMIN_EXCEPTION = 0x402; //
	public static final int MINOR_ALARMIN_RESUME = 0x403; //
	public static final int MINOR_HOST_DESMANTLE_ALARM = 0x404; //
	public static final int MINOR_HOST_DESMANTLE_RESUME = 0x405; //
	public static final int MINOR_CARD_READER_DESMANTLE_ALARM = 0x406; //
	public static final int MINOR_CARD_READER_DESMANTLE_RESUME = 0x407; //
	public static final int MINOR_CASE_SENSOR_ALARM = 0x408; //
	public static final int MINOR_CASE_SENSOR_RESUME = 0x409; //
	public static final int MINOR_STRESS_ALARM = 0x40a; //
	public static final int MINOR_OFFLINE_ECENT_NEARLY_FULL = 0x40b;// 90%
	public static final int MINOR_CARD_MAX_AUTHENTICATE_FAIL = 0x40c; //
	public static final int MINOR_SD_CARD_FULL = 0x40d; // SD
	public static final int MINOR_LINKAGE_CAPTURE_PIC = 0x40e; //
	// SDK_V222
	//
	public static final int DS6001_HF_B = 60;// :DS6001-HF/B
	public static final int DS6001_HF_P = 61;// :DS6001-HF/P
	public static final int DS6002_HF_B = 62;// :DS6002-HF/B
	public static final int DS6101_HF_B = 63;// :DS6101-HF/B
	public static final int IVMS_2000 = 64;//
	public static final int DS9000_IVS = 65;// 9000 DVR
	public static final int DS8004_AHL_A = 66;// ATM, DS8004AHL-S/A
	public static final int DS6101_HF_P = 67;// :DS6101-HF/P
	//
	public static final int VCA_DEV_ABILITY = 0x100;//
	public static final int VCA_CHAN_ABILITY = 0x110;//
	// /
	// (NET_VCA_PLATE_CFG);
	public static final int NET_DVR_SET_PLATECFG = 150;//

	public static final int NET_DVR_GET_PLATECFG = 151; //
	// (NET_VCA_RULECFG)
	public static final int NET_DVR_SET_RULECFG = 152; //
	public static final int NET_DVR_GET_RULECFG = 153;// ,
	// (NET_DVR_LF_CFG)
	public static final int NET_DVR_SET_LF_CFG = 160;//
	public static final int NET_DVR_GET_LF_CFG = 161;//
	//
	public static final int NET_DVR_SET_IVMS_STREAMCFG = 162; //
	public static final int NET_DVR_GET_IVMS_STREAMCFG = 163; //
	//
	public static final int NET_DVR_SET_VCA_CTRLCFG = 164; //
	public static final int NET_DVR_GET_VCA_CTRLCFG = 165; //
	// NET_VCA_MASK_REGION_LIST
	public static final int NET_DVR_SET_VCA_MASK_REGION = 166; //
	public static final int NET_DVR_GET_VCA_MASK_REGION = 167; //
	// ATM NET_VCA_ENTER_REGION
	public static final int NET_DVR_SET_VCA_ENTER_REGION = 168; //
	public static final int NET_DVR_GET_VCA_ENTER_REGION = 169; //
	// NET_VCA_LINE_SEGMENT_LIST
	public static final int NET_DVR_SET_VCA_LINE_SEGMENT = 170; //
	public static final int NET_DVR_GET_VCA_LINE_SEGMENT = 171; //
	// ivms NET_IVMS_MASK_REGION_LIST
	public static final int NET_DVR_SET_IVMS_MASK_REGION = 172; // IVMS
	public static final int NET_DVR_GET_IVMS_MASK_REGION = 173; // IVMS
	// ivms NET_IVMS_ENTER_REGION
	public static final int NET_DVR_SET_IVMS_ENTER_REGION = 174; // IVMS
	public static final int NET_DVR_GET_IVMS_ENTER_REGION = 175; // IVMS
	public static final int NET_DVR_SET_IVMS_BEHAVIORCFG = 176;//
	public static final int NET_DVR_GET_IVMS_BEHAVIORCFG = 177; //

	public static final int NET_ITC_GET_TRIGGERCFG = 3003;//
	public static final int NET_ITC_SET_TRIGGERCFG = 3004;//

	public static final int STREAM_ID_LEN = 32;
	public static final int NET_DVR_DEV_ADDRESS_MAX_LEN = 129;
	public static final int NET_DVR_LOGIN_USERNAME_MAX_LEN = 64;
	public static final int NET_DVR_LOGIN_PASSWD_MAX_LEN = 64;
	public static final int CARDNUM_LEN_OUT = 32;
	public static final int GUID_LEN = 16;
	public static final int MAX_IOSPEED_GROUP_NUM = 4;
	public static final int MAX_CHJC_NUM = 3;
	public static final int MAX_INTERVAL_NUM = 4;
	public static final int MAX_IOOUT_NUM = 4;
	public static final int MAX_LANEAREA_NUM = 2;
	public static final int ITC_MAX_POLYGON_POINT_NUM = 20;
	public static final int MAX_LICENSE_LEN = 16;
	public static final int MAX_AUDIO_V40 = 8;
	public static final int DEV_ID_LEN = 32;
	public static final int MAX_IP_DEVICE_V40 = 64;
	public static int MAX_DEVICES = 512;// max device number
	public static int MAX_CHANNUM_V40 = 512;

	public static final int ALARM_INFO_T = 0;
	public static final int OPERATION_SUCC_T = 1;
	public static final int OPERATION_FAIL_T = 2;
	public static final int PLAY_SUCC_T = 3;
	public static final int PLAY_FAIL_T = 4;

	/********************** end ***********************/

	/*************************************************
	 * ( _V30 9000 )
	 **************************************************/

	/////////////////////////////////////////////////////////////////////////
	//
	public static class NET_DVR_TIME extends Structure {//
		public int dwYear; //
		public int dwMonth; //
		public int dwDay; //
		public int dwHour; //
		public int dwMinute; //
		public int dwSecond; //

		public String toString() {
			return "NET_DVR_TIME.dwYear: " + dwYear + "\n" + "NET_DVR_TIME.dwMonth: \n" + dwMonth + "\n"
					+ "NET_DVR_TIME.dwDay: \n" + dwDay + "\n" + "NET_DVR_TIME.dwHour: \n" + dwHour + "\n"
					+ "NET_DVR_TIME.dwMinute: \n" + dwMinute + "\n" + "NET_DVR_TIME.dwSecond: \n" + dwSecond;
		}

		//
		public String toStringTime() {
			return String.format("%02d/%02d/%02d%02d:%02d:%02d", dwYear, dwMonth, dwDay, dwHour, dwMinute, dwSecond);
		}

		//
		public String toStringTitle() {
			return String.format("Time%02d%02d%02d%02d%02d%02d", dwYear, dwMonth, dwDay, dwHour, dwMinute, dwSecond);
		}
	}

	public static class NET_DVR_SCHEDTIME extends Structure {
		public byte byStartHour; //
		public byte byStartMin;
		public byte byStopHour; //
		public byte byStopMin;
	}

	public static class NET_DVR_HANDLEEXCEPTION_V30 extends Structure {
		public int dwHandleType; 
		public byte[] byRelAlarmOut = new byte[MAX_ALARMOUT_V30]; 
	}

	public static class NET_DVR_HANDLEEXCEPTION extends Structure {
		public int dwHandleType; 
		public byte[] byRelAlarmOut = new byte[MAX_ALARMOUT]; 
	}

	// DVR
	public static class NET_DVR_DEVICECFG extends Structure {
		public int dwSize;
		public byte[] sDVRName = new byte[NAME_LEN]; // DVR
		public int dwDVRID; // DVR ID, //V1.4(0-99), V1.5(0-255)
		public int dwRecycleRecord; // ,0: ; 1:
		//
		public byte[] sSerialNumber = new byte[SERIALNO_LEN]; //
		public int dwSoftwareVersion; // , 16 , 16
		public int dwSoftwareBuildDate; // ,0xYYYYMMDD
		public int dwDSPSoftwareVersion; // DSP , 16 , 16
		public int dwDSPSoftwareBuildDate; // DSP ,0xYYYYMMDD
		public int dwPanelVersion; // , 16 , 16
		public int dwHardwareVersion; // , 16 , 16
		public byte byAlarmInPortNum; // DVR
		public byte byAlarmOutPortNum; // DVR
		public byte byRS232Num; // DVR 232
		public byte byRS485Num; // DVR 485
		public byte byNetworkPortNum; //
		public byte byDiskCtrlNum; // DVR
		public byte byDiskNum; // DVR
		public byte byDVRType; // DVR , 1:DVR 2:ATM DVR 3:DVS ......
		public byte byChanNum; // DVR
		public byte byStartChan; // , DVS-1,DVR - 1
		public byte byDecordChans; // DVR
		public byte byVGANum; // VGA
		public byte byUSBNum; // USB
		public byte byAuxoutNum; //
		public byte byAudioNum; //
		public byte byIPChanNum; //
	}

	public static class NET_DVR_IPADDR extends Structure implements Serializable {
		public byte[] sIpV4 = new byte[16];
		public byte[] byRes = new byte[128];

		public String toString() {
			return "NET_DVR_IPADDR.sIpV4: " + new String(sIpV4) + "\n" + "NET_DVR_IPADDR.byRes: " + new String(byRes)
					+ "\n";
		}
	}

	// ( )(9000 )
	public static class NET_DVR_ETHERNET_V30 extends Structure {
		public NET_DVR_IPADDR struDVRIP = new NET_DVR_IPADDR();
		public NET_DVR_IPADDR struDVRIPMask = new NET_DVR_IPADDR();
		public int dwNetInterface;
		public short wDVRPort;
		public short wMTU;
		public byte[] byMACAddr = new byte[6];

		public String toString() {
			return "NET_DVR_ETHERNET_V30.struDVRIP: \n" + struDVRIP + "\n" + "NET_DVR_ETHERNET_V30.struDVRIPMask: \n"
					+ struDVRIPMask + "\n" + "NET_DVR_ETHERNET_V30.dwNetInterface: " + dwNetInterface + "\n"
					+ "NET_DVR_ETHERNET_V30.wDVRPort: " + wDVRPort + "\n" + "NET_DVR_ETHERNET_V30.wMTU: " + wMTU + "\n"
					+ "NET_DVR_ETHERNET_V30.byMACAddr: " + new String(byMACAddr) + "\n";
		}
	}

	public static class NET_DVR_ETHERNET extends Structure {// ( )
		public byte[] sDVRIP = new byte[16]; // DVR IP
		public byte[] sDVRIPMask = new byte[16]; // DVR IP
		public int dwNetInterface; // 1-10MBase-T 2-10MBase-T 3-100MBase-TX 4-100M 5-10M/100M
		public short wDVRPort; //
		public byte[] byMACAddr = new byte[MACADDR_LEN]; //
	}

	public static class NET_DVR_PPPOECFG extends Structure {// PPPoe
		public int dwPPPoE;
		public byte[] sPPPoEUser = new byte[32];
		public byte[] sPPPoEPassword = new byte[16];
		public NET_DVR_IPADDR struPPPoEIP = new NET_DVR_IPADDR();
	}

	public static class NET_DVR_NETCFG_V30 extends Structure {
		public int dwSize;
		public NET_DVR_ETHERNET_V30[] struEtherNet = new NET_DVR_ETHERNET_V30[2];
		public NET_DVR_IPADDR[] struRes1 = new NET_DVR_IPADDR[2];
		public NET_DVR_IPADDR struAlarmHostIpAddr;
		public short[] wRes2 = new short[2];
		public short wAlarmHostIpPort;
		public byte byUseDhcp;
		public byte byRes3;
		public NET_DVR_IPADDR struDnsServer1IpAddr = new NET_DVR_IPADDR();
		public NET_DVR_IPADDR struDnsServer2IpAddr = new NET_DVR_IPADDR();
		public byte[] byIpResolver = new byte[64];
		public short wIpResolverPort;
		public short wHttpPortNo;
		public NET_DVR_IPADDR struMulticastIpAddr = new NET_DVR_IPADDR();
		public NET_DVR_IPADDR struGatewayIpAddr = new NET_DVR_IPADDR();
		public NET_DVR_PPPOECFG struPPPoE = new NET_DVR_PPPOECFG();
		public byte[] byRes = new byte[64];

		public String toString() {
			return "NET_DVR_NETCFG_V30.dwSize: " + dwSize + "\n" + "NET_DVR_NETCFG_V30.struEtherNet[0]: \n"
					+ struEtherNet[0] + "\n" + "NET_DVR_NETCFG_V30.struAlarmHostIpAddr: \n" + struAlarmHostIpAddr + "\n"
					+ "NET_DVR_NETCFG_V30.wAlarmHostIpPort: " + wAlarmHostIpPort + "\n"
					+ "NET_DVR_NETCFG_V30.wHttpPortNo: " + wHttpPortNo + "\n"
					+ "NET_DVR_NETCFG_V30.struGatewayIpAddr: \n" + struGatewayIpAddr + "\n";
		}

		public NET_DVR_NETCFG_V30() {
			for (int i = 0; i < 2; ++i) {
				struEtherNet[i] = new NET_DVR_ETHERNET_V30();
				struRes1[i] = new NET_DVR_IPADDR();
			}
		}
	}

	public static class NET_DVR_NETCFG extends Structure {//
		public int dwSize;
		public NET_DVR_ETHERNET[] struEtherNet = new NET_DVR_ETHERNET[MAX_ETHERNET]; /*     */
		public byte[] sManageHostIP = new byte[16]; //
		public short wManageHostPort; //
		public byte[] sIPServerIP = new byte[16]; // IPServer
		public byte[] sMultiCastIP = new byte[16]; //
		public byte[] sGatewayIP = new byte[16]; //
		public byte[] sNFSIP = new byte[16]; // NFS IP
		public byte[] sNFSDirectory = new byte[PATHNAME_LEN];// NFS
		public int dwPPPOE; // 0- ,1-
		public byte[] sPPPoEUser = new byte[NAME_LEN]; // PPPoE
		public byte[] sPPPoEPassword = new byte[PASSWD_LEN];// PPPoE
		public byte[] sPPPoEIP = new byte[16]; // PPPoE IP ( )

		public NET_DVR_NETCFG() {
			for (int i = 0; i < MAX_ETHERNET; ++i) {
				struEtherNet[i] = new NET_DVR_ETHERNET();
			}
		}
	}

	//
	public static class NET_DVR_SCHEDTIMEWEEK extends Structure {
		public NET_DVR_SCHEDTIME[] struAlarmTime = new NET_DVR_SCHEDTIME[8];

		public NET_DVR_SCHEDTIMEWEEK() {
			for (int i = 0; i < 8; ++i) {
				struAlarmTime[i] = new NET_DVR_SCHEDTIME();
			}
		}
	}

	public static class byte96 extends Structure {
		public byte[] byMotionScope = new byte[96];
	}

	public static class NET_DVR_MOTION_V30 extends Structure {// ( )(9000 )
		public byte96[] byMotionScope = new byte96[64]; /* ,0-96 , 64 , 96*64 , 1 ,0- */
		public byte byMotionSensitive; /* , 0 - 5, ,oxff */
		public byte byEnableHandleMotion; /* 0- 1- */
		public byte byPrecision; /* : 0--16*16, 1--32*32, 2--64*64 ... */
		public byte reservedData;
		public NET_DVR_HANDLEEXCEPTION_V30 struMotionHandleType = new NET_DVR_HANDLEEXCEPTION_V30(); /*     */
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmTime = new NET_DVR_SCHEDTIMEWEEK[MAX_DAYS]; /*     */
		public byte[] byRelRecordChan = new byte[64]; /*     */

		public NET_DVR_MOTION_V30() {
			for (int i = 0; i < 64; ++i) {
				byMotionScope[i] = new byte96();
			}

			for (int i = 0; i < MAX_DAYS; ++i) {
				struAlarmTime[i] = new NET_DVR_SCHEDTIMEWEEK();
			}
		}
	}

	public static class NET_DVR_MOTION extends Structure {// ( )
		public byte[][] byMotionScope = new byte[18][22]; /* , 22*18 , 1 ,0- */
		public byte byMotionSensitive; /* , 0 - 5, ,0xff */
		public byte byEnableHandleMotion; /*     */
		public byte[] reservedData = new byte[2];
		public NET_DVR_HANDLEEXCEPTION strMotionHandleType = new NET_DVR_HANDLEEXCEPTION(); /*     */
		public byte[] byRelRecordChan = new byte[MAX_CHANNUM]; // , 1
	}

	public static class NET_DVR_HIDEALARM_V30 extends Structure {//
		public int dwEnableHideAlarm; /* ,0- ,1- 2- 3- */
		public short wHideAlarmAreaTopLeftX; /* x */
		public short wHideAlarmAreaTopLeftY; /* y */
		public short wHideAlarmAreaWidth; /*    */
		public short wHideAlarmAreaHeight; /*    */
		public NET_DVR_HANDLEEXCEPTION_V30 strHideAlarmHandleType = new NET_DVR_HANDLEEXCEPTION_V30(); /*     */
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmTime = new NET_DVR_SCHEDTIMEWEEK[MAX_DAYS];//

		public NET_DVR_HIDEALARM_V30() {
			for (int i = 0; i < MAX_DAYS; ++i) {
				struAlarmTime[i] = new NET_DVR_SCHEDTIMEWEEK();
			}
		}
	}

	public static class NET_DVR_HIDEALARM extends Structure {// ( ) 704*576
		public int dwEnableHideAlarm; /* ,0- ,1- 2- 3- */
		public short wHideAlarmAreaTopLeftX; /* x */
		public short wHideAlarmAreaTopLeftY; /* y */
		public short wHideAlarmAreaWidth; /*    */
		public short wHideAlarmAreaHeight; /*    */
		public NET_DVR_HANDLEEXCEPTION strHideAlarmHandleType = new NET_DVR_HANDLEEXCEPTION(); /*     */
	}

	public static class NET_DVR_VILOST_V30 extends Structure { // ( )(9000 )
		public byte byEnableHandleVILost; /*       */
		public NET_DVR_HANDLEEXCEPTION_V30 strVILostHandleType = new NET_DVR_HANDLEEXCEPTION_V30(); /*     */
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmTime = new NET_DVR_SCHEDTIMEWEEK[MAX_DAYS];//

		public NET_DVR_VILOST_V30() {
			for (int i = 0; i < MAX_DAYS; ++i) {
				struAlarmTime[i] = new NET_DVR_SCHEDTIMEWEEK();
			}
		}
	}

	public static class NET_DVR_VILOST extends Structure { // ( )
		public byte byEnableHandleVILost; /*       */
		public NET_DVR_HANDLEEXCEPTION strVILostHandleType = new NET_DVR_HANDLEEXCEPTION(); /*     */
	}

	public static class NET_DVR_SHELTER extends Structure { // ( )
		public short wHideAreaTopLeftX; /* x */
		public short wHideAreaTopLeftY; /* y */
		public short wHideAreaWidth; /*    */
		public short wHideAreaHeight; /*    */
	}

	public static class NET_DVR_COLOR extends Structure {
		public byte byBrightness; /* ,0-255 */
		public byte byContrast; /* ,0-255 */
		public byte bySaturation; /* ,0-255 */
		public byte byHue; /* ,0-255 */
	}

	public static class NET_DVR_VICOLOR extends Structure {
		public NET_DVR_COLOR[] struColor = new NET_DVR_COLOR[MAX_TIMESEGMENT_V30];/* ( , ) */
		public NET_DVR_SCHEDTIME[] struHandleTime = new NET_DVR_SCHEDTIME[MAX_TIMESEGMENT_V30];/* ( ) */

		public NET_DVR_VICOLOR() {
			for (int i = 0; i < MAX_TIMESEGMENT_V30; ++i) {
				struColor[i] = new NET_DVR_COLOR();
				struHandleTime[i] = new NET_DVR_SCHEDTIME();
			}
		}
	};

	public static class NET_DVR_PICCFG_V30 extends Structure {
		public int dwSize;
		public byte[] sChanName = new byte[NAME_LEN];
		public int dwVideoFormat; /* 1-NTSC 2-PAL */
		public NET_DVR_VICOLOR struViColor = new NET_DVR_VICOLOR(); //
		public int dwShowChanName; // ,0- ,1- 704*576
		public short wShowNameTopLeftX; /* x */
		public short wShowNameTopLeftY; /* y */
		public NET_DVR_VILOST_V30 struVILost = new NET_DVR_VILOST_V30(); //
		public NET_DVR_VILOST_V30 struAULost = new NET_DVR_VILOST_V30(); /* ( ) */
		public NET_DVR_MOTION_V30 struMotion = new NET_DVR_MOTION_V30(); //
		public NET_DVR_HIDEALARM_V30 struHideAlarm = new NET_DVR_HIDEALARM_V30();//
		public int dwEnableHide; /* ( 704*576) ,0- ,1- */
		public NET_DVR_SHELTER[] struShelter = new NET_DVR_SHELTER[4];
		public int dwShowOsd; // OSD,0- ,1- 704*576
		public short wOSDTopLeftX; /* OSD x */
		public short wOSDTopLeftY; /* OSD y */
		public byte byOSDType; /* OSD ( ) */
		public byte byDispWeek; /*    */
		public byte byOSDAttrib; /* OSD : , */
		public byte byHourOSDType; /* OSD :0-24 ,1-12 */
		public byte[] byRes = new byte[64];

		public NET_DVR_PICCFG_V30() {
			for (int i = 0; i < 4; ++i) {
				struShelter[i] = new NET_DVR_SHELTER();
			}
		}
	}

	public static class NET_DVR_PICCFG_EX extends Structure {// SDK_V14
		public int dwSize;
		public byte[] sChanName = new byte[NAME_LEN];
		public int dwVideoFormat; /* 1-NTSC 2-PAL */
		public byte byBrightness; /* ,0-255 */
		public byte byContrast; /* ,0-255 */
		public byte bySaturation; /* ,0-255 */
		public byte byHue; /* ,0-255 */
		//
		public int dwShowChanName; // ,0- ,1- 704*576
		public short wShowNameTopLeftX; /* x */
		public short wShowNameTopLeftY; /* y */
		//
		public NET_DVR_VILOST struVILost = new NET_DVR_VILOST();
		//
		public NET_DVR_MOTION struMotion = new NET_DVR_MOTION();
		//
		public NET_DVR_HIDEALARM struHideAlarm = new NET_DVR_HIDEALARM();
		// 704*576
		public int dwEnableHide; /* ,0- ,1- */
		public NET_DVR_SHELTER[] struShelter = new NET_DVR_SHELTER[MAX_SHELTERNUM];
		// OSD
		public int dwShowOsd;// OSD,0- ,1- 704*576
		public short wOSDTopLeftX; /* OSD x */
		public short wOSDTopLeftY; /* OSD y */
		public byte byOSDType; /* OSD ( ) */
		/* 0: XXXX-XX-XX */
		/* 1: XX-XX-XXXX */
		/* 2: XXXX XX XX */
		/* 3: XX XX XXXX */
		/* 4: XX-XX-XXXX */
		/* 5: XX XX XXXX */
		public byte byDispWeek; /*    */
		public byte byOSDAttrib; /* OSD : , */
		/* 0: OSD */
		/* 1: , */
		/* 2: , */
		/* 3: , */
		/* 4: , */
		public byte byHourOsdType; // :0 24 ,1-12 am/pm

		public NET_DVR_PICCFG_EX() {
			for (int i = 0; i < MAX_SHELTERNUM; ++i) {
				struShelter[i] = new NET_DVR_SHELTER();
			}
		}
	}

	public static class NET_DVR_PICCFG extends Structure { // (SDK_V13 )
		public int dwSize;
		public byte[] sChanName = new byte[NAME_LEN];
		public int dwVideoFormat; /* 1-NTSC 2-PAL */
		public byte byBrightness; /* ,0-255 */
		public byte byContrast; /* ,0-255 */
		public byte bySaturation; /* ,0-255 */
		public byte byHue; /* ,0-255 */
		//
		public int dwShowChanName; // ,0- ,1- 704*576
		public short wShowNameTopLeftX; /* x */
		public short wShowNameTopLeftY; /* y */
		//
		public NET_DVR_VILOST struVILost = new NET_DVR_VILOST();
		//
		public NET_DVR_MOTION struMotion = new NET_DVR_MOTION();
		//
		public NET_DVR_HIDEALARM struHideAlarm = new NET_DVR_HIDEALARM();
		// 704*576
		public int dwEnableHide; /* ,0- ,1- */
		public short wHideAreaTopLeftX; /* x */
		public short wHideAreaTopLeftY; /* y */
		public short wHideAreaWidth; /*    */
		public short wHideAreaHeight; /*    */
		// OSD
		public int dwShowOsd;// OSD,0- ,1- 704*576
		public short wOSDTopLeftX; /* OSD x */
		public short wOSDTopLeftY; /* OSD y */
		public byte byOSDType; /* OSD ( ) */
		/* 0: XXXX-XX-XX */
		/* 1: XX-XX-XXXX */
		/* 2: XXXX XX XX */
		/* 3: XX XX XXXX */
		/* 4: XX-XX-XXXX */
		/* 5: XX XX XXXX */
		byte byDispWeek; /*    */
		byte byOSDAttrib; /* OSD : , */
		/* 0: OSD */
		/* 1: , */
		/* 2: , */
		/* 3: , */
		/* 4: , */
		public byte reservedData2;
	}

	// ( )(9000 )
	public static class NET_DVR_COMPRESSION_INFO_V30 extends Structure {
		public byte byStreamType; // :0- ,1- ,0xfe- ( )
		public byte byResolution; //
		public byte byBitrateType; // :0- ,1-
		public byte byPicQuality; // :0- ,1- ,2- ,3- ,4- ,5- ,0xfe- ( )
		public int dwVideoBitrate; //
		public int dwVideoFrameRate; //
		public short wIntervalFrameI; // I ,0xfffe- ( ),0xffff-
		public byte byIntervalBPFrame; // :0-BBP ,1-BP ,2- P ,0xff-
		public byte byres1; // , 0
		public byte byVideoEncType; // :0- 264,1- h264,2- mpeg4,7-M-JPEG,8-MPEG2,9-SVAC,10- h265,0xfe-
									// ( ),0xff-
		public byte byAudioEncType; // :0-G722,1-G711_U,2-G711_A,5-MP2L2,6-G726,7-AAC,8-PCM,0xfe-
									// ( ),0xff-
		public byte byVideoEncComplexity; // :0- ,1- ,2- ,0xfe- ( )
		public byte byEnableSvc; // 0- SVC ,1- SVC ,2- SVC SVC: Scalable Video Coding,
		public byte byFormatType; // :1- ,2-RTP ,3-PS ,4-TS ,5- ,6-FLV,7-ASF,8-3GP,9-RTP+PS( :GB28181),0xff-
		public byte byAudioBitRate; //
		public byte bySteamSmooth; // , :1~100,1 (Clear),100 (Smooth)
		public byte byAudioSamplingRate; // :0- ,1- 16kHZ,2- 32kHZ,3- 48kHZ, 4- 44.1kHZ,5- 8kHZ
		public byte bySmartCodec; // (Smart264):0- ,1-
									// , (dwVideoBitrate) , (wAverageVideoBitrate)
		public byte byres; // , 0
		public short wAverageVideoBitrate; // ( SmartCodec )
	}

	// (9000 )
	public static class NET_DVR_COMPRESSIONCFG_V30 extends Structure {
		public int dwSize;
		public NET_DVR_COMPRESSION_INFO_V30 struNormHighRecordPara = new NET_DVR_COMPRESSION_INFO_V30(); // 8000
		public NET_DVR_COMPRESSION_INFO_V30 struRes = new NET_DVR_COMPRESSION_INFO_V30(); // String[28];
		public NET_DVR_COMPRESSION_INFO_V30 struEventRecordPara = new NET_DVR_COMPRESSION_INFO_V30(); //
		public NET_DVR_COMPRESSION_INFO_V30 struNetPara = new NET_DVR_COMPRESSION_INFO_V30(); // ( )
	}

	public static class NET_DVR_COMPRESSION_INFO extends Structure {// ( )
		public byte byStreamType; // 0- ,1- ,
		public byte byResolution; // 0-DCIF 1-CIF, 2-QCIF, 3-4CIF, 4-2CIF, 5-2QCIF(352X144)( )
		public byte byBitrateType; // 0: ,1:
		public byte byPicQuality; // 0- 1- 2- 3- 4- 5-
		public int dwVideoBitrate; // 0- 1-16K( ) 2-32K 3-48k 4-64K 5-80K 6-96K 7-128K 8-160k 9-192K
									// 10-224K 11-256K 12-320K
		// 13-384K 14-448K 15-512K 16-640K 17-768K 18-896K 19-1024K 20-1280K 21-1536K
		// 22-1792K 23-2048K
		// (31 ) 1 , 0-30 (MIN-32K MAX-8192K)
		public int dwVideoFrameRate; // 0- ; 1-1/16; 2-1/8; 3-1/4; 4-1/2; 5-1; 6-2; 7-4; 8-6; 9-8; 10-10; 11-12;
										// 12-16; 13-20;
	}

	public static class NET_DVR_COMPRESSIONCFG extends Structure {//
		public int dwSize;
		public NET_DVR_COMPRESSION_INFO struRecordPara = new NET_DVR_COMPRESSION_INFO(); // /
		public NET_DVR_COMPRESSION_INFO struNetPara = new NET_DVR_COMPRESSION_INFO(); // /
	}

	public static class NET_DVR_COMPRESSION_INFO_EX extends Structure {// ( )( ) I
		public byte byStreamType; // 0- , 1-
		public byte byResolution; // 0-DCIF 1-CIF, 2-QCIF, 3-4CIF, 4-2CIF, 5-2QCIF(352X144)( )
		public byte byBitrateType; // 0: ,1:
		public byte byPicQuality; // 0- 1- 2- 3- 4- 5-
		public int dwVideoBitrate; // 0- 1-16K( ) 2-32K 3-48k 4-64K 5-80K 6-96K 7-128K 8-160k 9-192K
									// 10-224K 11-256K 12-320K
		// 13-384K 14-448K 15-512K 16-640K 17-768K 18-896K 19-1024K 20-1280K 21-1536K
		// 22-1792K 23-2048K
		// (31 ) 1 , 0-30 (MIN-32K MAX-8192K)
		public int dwVideoFrameRate; // 0- ; 1-1/16; 2-1/8; 3-1/4; 4-1/2; 5-1; 6-2; 7-4; 8-6; 9-8; 10-10; 11-12;
										// 12-16; 13-20, //V2.0 14-15, 15-18, 16-22;
		public short wIntervalFrameI; // I
		// 2006-08-11 P ,
		public byte byIntervalBPFrame;// 0-BBP ; 1-BP ; 2- P
		public byte byENumber;// E
	}

	public static class NET_DVR_COMPRESSIONCFG_EX extends Structure {// ( )
		public int dwSize;
		public NET_DVR_COMPRESSION_INFO_EX struRecordPara = new NET_DVR_COMPRESSION_INFO_EX(); //
		public NET_DVR_COMPRESSION_INFO_EX struNetPara = new NET_DVR_COMPRESSION_INFO_EX(); //
	}

	public static class NET_DVR_RECCOMPRESSIONCFG_EX extends Structure {// (GE )2006-09-18
		int dwSize;
		NET_DVR_COMPRESSION_INFO_EX[][] struRecTimePara = new NET_DVR_COMPRESSION_INFO_EX[MAX_DAYS][MAX_TIMESEGMENT]; //

		public NET_DVR_RECCOMPRESSIONCFG_EX() {
			for (int i = 0; i < MAX_DAYS; ++i) {
				for (int j = 0; j < MAX_TIMESEGMENT; ++j) {
					struRecTimePara[i][j] = new NET_DVR_COMPRESSION_INFO_EX();
				}
			}
		}
	}

	public static class NET_DVR_RECORDSCHED extends Structure // ( )
	{
		public NET_DVR_SCHEDTIME struRecordTime = new NET_DVR_SCHEDTIME();
		public byte byRecordType; // 0: ,1: ,2: ,3: | ,4: & , 5: , 6:
		public byte[] reservedData = new byte[3];
	}

	public static class NET_DVR_RECORDDAY extends Structure // ( )
	{
		public short wAllDayRecord; /* 0- 1- */
		public byte byRecordType; /* 0: ,1: ,2: ,3: | ,4: & 5: , 6: */
		public byte reservedData;
	}

	public static class NET_DVR_RECORDSCHEDWEEK extends Structure {
		public NET_DVR_RECORDSCHED[] struRecordSched = (NET_DVR_RECORDSCHED[]) new NET_DVR_RECORDSCHED()
				.toArray(MAX_TIMESEGMENT_V30);
	}

	public static class NET_DVR_RECORD_V30 extends Structure { // (9000 )
		public int dwSize;
		public int dwRecord; /* 0- 1- */
		public NET_DVR_RECORDDAY[] struRecAllDay = (NET_DVR_RECORDDAY[]) new NET_DVR_RECORDDAY().toArray(MAX_DAYS);
		public NET_DVR_RECORDSCHEDWEEK[] struRecordSched = (NET_DVR_RECORDSCHEDWEEK[]) new NET_DVR_RECORDSCHEDWEEK()
				.toArray(MAX_DAYS);
		public int dwRecordTime; /* 0-5 , 1-20 , 2-30 , 3-1 , 4-2 , 5-5 , 6-10 */
		public int dwPreRecordTime; /* 0- 1-5 2-10 3-15 4-20 5-25 6-30 7-0xffffffff( ) */
		public int dwRecorderDuration; /*     */
		public byte byRedundancyRec; /* , :0/1 */
		public byte byAudioRec; /* : */
		public byte[] byReserve = new byte[10];
	}

	public static class NET_DVR_RECORD extends Structure { //
		public int dwSize;
		public int dwRecord; /* 0- 1- */
		public NET_DVR_RECORDDAY[] struRecAllDay = (NET_DVR_RECORDDAY[]) new NET_DVR_RECORDDAY().toArray(MAX_DAYS);
		public NET_DVR_RECORDSCHEDWEEK[] struRecordSched = (NET_DVR_RECORDSCHEDWEEK[]) new NET_DVR_RECORDSCHEDWEEK()
				.toArray(MAX_DAYS);
		public int dwRecordTime; /* 0-5 , 1-20 , 2-30 , 3-1 , 4-2 , 5-5 , 6-10 */
		public int dwPreRecordTime; /* 0- 1-5 2-10 3-15 4-20 5-25 6-30 7-0xffffffff( ) */
	}

	//
	public static class NET_DVR_PTZ_PROTOCOL extends Structure {
		public int dwType; /* , 1 */
		public byte[] byDescribe = new byte[DESC_LEN]; /* , 8000 */
	}

	public static class NET_DVR_PTZCFG extends Structure {
		public int dwSize;
		public NET_DVR_PTZ_PROTOCOL[] struPtz = (NET_DVR_PTZ_PROTOCOL[]) new NET_DVR_PTZ_PROTOCOL()
				.toArray(PTZ_PROTOCOL_NUM);/* 200 PTZ */
		public int dwPtzNum; /* ptz , 0 ( 1) */
		public byte[] byRes = new byte[8];
	}

	/*************************** (end) ******************************/
	public static class NET_DVR_DECODERCFG_V30 extends Structure {// ( ) (9000 )
		public int dwSize;
		public int dwBaudRate; // (bps),0-50,1-75,2-110,3-150,4-300,5-600,6-1200,7-2400,8-4800,9-9600,10-19200,
								// 11-38400,12-57600,13-76800,14-115.2k;
		public byte byDataBit; // 0-5 ,1-6 ,2-7 ,3-8 ;
		public byte byStopBit; // 0-1 ,1-2 ;
		public byte byParity; // 0- ,1- ,2- ;
		public byte byFlowcontrol; // 0- ,1- ,2-
		public short wDecoderType; // , 0-YouLi,1-LiLin-1016,2-LiLin-820,3-Pelco-p,4-DM
									// DynaColor,5-HD600,6-JC-4116,7-Pelco-d WX,8-Pelco-d PICO
		public short wDecoderAddress; /* :0 - 255 */
		public byte[] bySetPreset = new byte[MAX_PRESET_V30]; /* ,0- ,1- */
		public byte[] bySetCruise = new byte[MAX_CRUISE_V30]; /* : 0- ,1- */
		public byte[] bySetTrack = new byte[MAX_TRACK_V30]; /* ,0- ,1- */
	}

	public static class NET_DVR_DECODERCFG extends Structure {// ( )
		public int dwSize;
		public int dwBaudRate; // (bps),0-50,1-75,2-110,3-150,4-300,5-600,6-1200,7-2400,8-4800,9-9600,10-19200,
								// 11-38400,12-57600,13-76800,14-115.2k;
		public byte byDataBit; // 0-5 ,1-6 ,2-7 ,3-8 ;
		public byte byStopBit; // 0-1 ,1-2 ;
		public byte byParity; // 0- ,1- ,2- ;
		public byte byFlowcontrol; // 0- ,1- ,2-
		public short wDecoderType; // , 0-YouLi,1-LiLin-1016,2-LiLin-820,3-Pelco-p,4-DM
									// DynaColor,5-HD600,6-JC-4116,7-Pelco-d WX,8-Pelco-d PICO
		public short wDecoderAddress; /* :0 - 255 */
		public byte[] bySetPreset = new byte[MAX_PRESET]; /* ,0- ,1- */
		public byte[] bySetCruise = new byte[MAX_CRUISE]; /* : 0- ,1- */
		public byte[] bySetTrack = new byte[MAX_TRACK]; /* ,0- ,1- */
	}

	public static class NET_DVR_PPPCFG_V30 extends Structure {// ppp ( )
		public NET_DVR_IPADDR struRemoteIP = new NET_DVR_IPADDR(); // IP
		public NET_DVR_IPADDR struLocalIP = new NET_DVR_IPADDR(); // IP
		public byte[] sLocalIPMask = new byte[16]; // IP
		public byte[] sUsername = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public byte byPPPMode; // PPP , 0- ,1-
		public byte byRedial; // :0- ,1-
		public byte byRedialMode; // ,0- ,1-
		public byte byDataEncrypt; // ,0- ,1-
		public int dwMTU; // MTU
		public byte[] sTelephoneNumber = new byte[PHONENUMBER_LEN]; //
	}

	public static class NET_DVR_PPPCFG extends Structure {// ppp ( )
		public byte[] sRemoteIP = new byte[16]; // IP
		public byte[] sLocalIP = new byte[16]; // IP
		public byte[] sLocalIPMask = new byte[16]; // IP
		public byte[] sUsername = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public byte byPPPMode; // PPP , 0- ,1-
		public byte byRedial; // :0- ,1-
		public byte byRedialMode; // ,0- ,1-
		public byte byDataEncrypt; // ,0- ,1-
		public int dwMTU; // MTU
		public byte[] sTelephoneNumber = new byte[PHONENUMBER_LEN]; //
	}

	public static class NET_DVR_SINGLE_RS232 extends Structure {// RS232 (9000 )
		public int dwBaudRate; /*
								 * (bps),0-50,1-75,2-110,3-150,4-300,5-600,6-1200,7-2400,8-4800,9-9600,10-
								 * 19200, 11-38400,12-57600,13-76800,14-115.2k;
								 */
		public byte byDataBit; /* 0-5 ,1-6 ,2-7 ,3-8 */
		public byte byStopBit; /* 0-1 ,1-2 */
		public byte byParity; /* 0- ,1- ,2- */
		public byte byFlowcontrol; /* 0- ,1- ,2- */
		public int dwWorkMode; /* ,0-232 PPP ,1-232 ,2- */
	}

	public static class NET_DVR_RS232CFG_V30 extends Structure {// RS232 (9000 )
		public int dwSize;
		public NET_DVR_SINGLE_RS232 struRs232 = new NET_DVR_SINGLE_RS232();/* , , */
		public byte[] byRes = new byte[84];
		public NET_DVR_PPPCFG_V30 struPPPConfig = new NET_DVR_PPPCFG_V30();/* ppp */
	}

	public static class NET_DVR_RS232CFG extends Structure {// RS232
		public int dwSize;
		public int dwBaudRate;// (bps),0-50,1-75,2-110,3-150,4-300,5-600,6-1200,7-2400,8-4800,9-9600,10-19200,
								// 11-38400,12-57600,13-76800,14-115.2k;
		public byte byDataBit;// 0-5 ,1-6 ,2-7 ,3-8 ;
		public byte byStopBit;// 0-1 ,1-2 ;
		public byte byParity;// 0- ,1- ,2- ;
		public byte byFlowcontrol;// 0- ,1- ,2-
		public int dwWorkMode;// ,0- (232 PPP ),1- (232 ),2-
		public NET_DVR_PPPCFG struPPPConfig = new NET_DVR_PPPCFG();
	}

	public static class NET_DVR_ALARMINCFG_V30 extends Structure {// (9000 )
		public int dwSize;
		public byte[] sAlarmInName = new byte[NAME_LEN]; /*   */
		public byte byAlarmType; // ,0: ,1:
		public byte byAlarmInHandle; /* 0- 1- */
		public byte[] reservedData = new byte[2];
		public NET_DVR_HANDLEEXCEPTION_V30 struAlarmHandleType = new NET_DVR_HANDLEEXCEPTION_V30(); /*     */
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmTime = (NET_DVR_SCHEDTIMEWEEK[]) new NET_DVR_SCHEDTIMEWEEK()
				.toArray(MAX_DAYS);//
		public byte[] byRelRecordChan = new byte[MAX_CHANNUM_V30]; // , 1
		public byte[] byEnablePreset = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byPresetNo = new byte[MAX_CHANNUM_V30]; /* , , 0xff */
		public byte[] byEnablePresetRevert = new byte[MAX_CHANNUM_V30]; /* ( ) */
		public short[] wPresetRevertDelay = new short[MAX_CHANNUM_V30]; /* ( ) */
		public byte[] byEnableCruise = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byCruiseNo = new byte[MAX_CHANNUM_V30]; /*   */
		public byte[] byEnablePtzTrack = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byPTZTrack = new byte[MAX_CHANNUM_V30]; /*       */
		public byte[] byRes = new byte[16];
	}

	public static class NET_DVR_ALARMINCFG extends Structure {//
		public int dwSize;
		public byte[] sAlarmInName = new byte[NAME_LEN]; /*   */
		public byte byAlarmType; // ,0: ,1:
		public byte byAlarmInHandle; /* 0- 1- */
		public NET_DVR_HANDLEEXCEPTION struAlarmHandleType = new NET_DVR_HANDLEEXCEPTION(); /*     */
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmTime = (NET_DVR_SCHEDTIMEWEEK[]) new NET_DVR_SCHEDTIMEWEEK()
				.toArray(MAX_DAYS);//
		public byte[] byRelRecordChan = new byte[MAX_CHANNUM]; // , 1
		public byte[] byEnablePreset = new byte[MAX_CHANNUM]; /* 0- ,1- */
		public byte[] byPresetNo = new byte[MAX_CHANNUM]; /* , , 0xff */
		public byte[] byEnableCruise = new byte[MAX_CHANNUM]; /* 0- ,1- */
		public byte[] byCruiseNo = new byte[MAX_CHANNUM]; /*   */
		public byte[] byEnablePtzTrack = new byte[MAX_CHANNUM]; /* 0- ,1- */
		public byte[] byPTZTrack = new byte[MAX_CHANNUM]; /*       */
	}

	public static class NET_DVR_ADDIT_POSITION extends Structure {// GPS (2007-12-27)
		public byte[] sDevName = new byte[32]; /*     */
		public int dwSpeed; /*   */
		public int dwLongitude; /*   */
		public int dwLatitude; /*   */
		public byte[] direction = new byte[2]; /* direction[0]:'E'or'W'( / ), direction[1]:'N'or'S'( / ) */
		public byte[] res = new byte[2]; /*   */
	}

	public static class struIOAlarm extends Structure {
		public int dwAlarmInputNo;
		public int dwTrigerAlarmOutNum;
		public int dwTrigerRecordChanNum;
	}

	public static class NET_DVR_TIME_EX extends Structure {
		public short wYear;
		public byte byMonth;
		public byte byDay;
		public byte byHour;
		public byte byMinute;
		public byte bySecond;
		public byte byRes;
	}

	public static class struRecordingHost extends Structure {
		public byte bySubAlarmType;
		public byte[] byRes1 = new byte[3];
		public NET_DVR_TIME_EX struRecordEndTime = new NET_DVR_TIME_EX();
		public byte[] byRes = new byte[116];
	}

	public static class struAlarmHardDisk extends Structure {
		public int dwAlarmHardDiskNum;
	}

	public static class struAlarmChannel extends Structure {
		public int dwAlarmChanNum;
	}

	public static class uStruAlarm extends Union {
		public byte[] byUnionLen = new byte[128];
		public struIOAlarm struioAlarm = new struIOAlarm();
		public struAlarmHardDisk strualarmHardDisk = new struAlarmHardDisk();
		public struAlarmChannel sstrualarmChannel = new struAlarmChannel();
		public struRecordingHost strurecordingHost = new struRecordingHost();
	}

	public static class NET_DVR_ALRAM_FIXED_HEADER extends Structure {
		public int dwAlarmType;
		public NET_DVR_TIME_EX struAlarmTime = new NET_DVR_TIME_EX();
		public uStruAlarm ustruAlarm = new uStruAlarm();
	}

	public static class NET_DVR_ALARMINFO_V40 extends Structure {
		public NET_DVR_ALRAM_FIXED_HEADER struAlarmFixedHeader = new NET_DVR_ALRAM_FIXED_HEADER();
		public Pointer pAlarmData;
	}

	public static class NET_DVR_ALARMINFO_V30 extends Structure {// (9000 )
		public int dwAlarmType;/*
								 * 0- ,1- ,2- ,3- ,4- ,5- ,6- ,7- , 8- , 0xa-GPS ( )
								 */
		public int dwAlarmInputNumber;/*    */
		public byte[] byAlarmOutputNumber = new byte[MAX_ALARMOUT_V30];/* , 1 */
		public byte[] byAlarmRelateChannel = new byte[MAX_CHANNUM_V30];/*
																		 * , 1 , dwAlarmRelateChannel[0] 1
																		 */
		public byte[] byChannel = new byte[MAX_CHANNUM_V30];/* dwAlarmType 2 3,6 , ,dwChannel[0] 1 */
		public byte[] byDiskNumber = new byte[MAX_DISKNUM_V30];/* dwAlarmType 1,4,5 , , dwDiskNumber[0] 1 */
	}

	public static class NET_DVR_ALARMINFO extends Structure {
		public int dwAlarmType;/*
								 * 0- ,1- ,2- ,3- ,4- ,5- ,6- ,7- , 8- , 9- , 0xa-GPS ( )
								 */
		public int dwAlarmInputNumber;/* , 9 0 , -1 */
		public int[] dwAlarmOutputNumber = new int[MAX_ALARMOUT];/* , 1 */
		public int[] dwAlarmRelateChannel = new int[MAX_CHANNUM];/* ,dwAlarmRelateChannel[0] 1 1 */
		public int[] dwChannel = new int[MAX_CHANNUM];/* dwAlarmType 2 3,6 , ,dwChannel[0] 1 */
		public int[] dwDiskNumber = new int[MAX_DISKNUM];/* dwAlarmType 1,4,5 , , dwDiskNumber[0] 1 */
	}

	public static class NET_DVR_ALARMINFO_EX extends Structure {// ( 2006-07-28)
		public int dwAlarmType;/* 0- ,1- ,2- ,3- ,4- ,5- ,6- ,7- , 8- */
		public int dwAlarmInputNumber;/*    */
		public int[] dwAlarmOutputNumber = new int[MAX_ALARMOUT];/* , 1 */
		public int[] dwAlarmRelateChannel = new int[MAX_CHANNUM];/*
																	 * , 1 ,dwAlarmRelateChannel [0] 1
																	 */
		public int[] dwChannel = new int[MAX_CHANNUM];/* dwAlarmType 2 3,6 , ,dwChannel[0] 0 */
		public int[] dwDiskNumber = new int[MAX_DISKNUM];/* dwAlarmType 1,4,5 , */
		public byte[] sSerialNumber = new byte[SERIALNO_LEN]; //
		public byte[] sRemoteAlarmIP = new byte[16]; // IP ;
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// IPC
	public static class NET_DVR_IPDEVINFO extends Structure {/* IP */
		public int dwEnable; /* IP */
		public byte[] sUserName = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public NET_DVR_IPADDR struIP = new NET_DVR_IPADDR(); /* IP */
		public short wDVRPort; /*   */
		public byte[] byres = new byte[34]; /*   */
	}

	public static class NET_DVR_IPCHANINFO extends Structure {/* IP */
		public byte byEnable; /*      */
		public byte byIPID; /* IP ID 1- MAX_IP_DEVICE */
		public byte byChannel; /*   */
		public byte[] byres = new byte[33]; /*   */
	}

	public static class NET_DVR_IPPARACFG extends Structure {/* IP */
		public int dwSize; /*     */
		public NET_DVR_IPDEVINFO[] struIPDevInfo = (NET_DVR_IPDEVINFO[]) new NET_DVR_IPDEVINFO()
				.toArray(MAX_IP_DEVICE); /* IP */
		public byte[] byAnalogChanEnable = new byte[MAX_ANALOG_CHANNUM]; /* , 1-32 ,0 1 */
		public NET_DVR_IPCHANINFO[] struIPChanInfo = (NET_DVR_IPCHANINFO[]) new NET_DVR_IPCHANINFO()
				.toArray(MAX_IP_CHANNEL); /* IP */
	}

	public static class NET_DVR_IPALARMOUTINFO extends Structure {/*    */
		public byte byIPID; /* IP ID 1- MAX_IP_DEVICE */
		public byte byAlarmOut; /*    */
		public byte[] byRes = new byte[18]; /*   */
	}

	public static class NET_DVR_IPALARMOUTCFG extends Structure {/* IP */
		public int dwSize; /*     */
		public NET_DVR_IPALARMOUTINFO[] struIPAlarmOutInfo = (NET_DVR_IPALARMOUTINFO[]) new NET_DVR_IPALARMOUTINFO()
				.toArray(MAX_IP_ALARMOUT);/* IP */
	}

	public static class NET_DVR_IPALARMININFO extends Structure {/*    */
		public byte byIPID; /* IP ID 1- MAX_IP_DEVICE */
		public byte byAlarmIn; /*    */
		public byte[] byRes = new byte[18]; /*   */
	}

	public static class NET_DVR_IPALARMINCFG extends Structure {/* IP */
		public int dwSize; /*     */
		public NET_DVR_IPALARMININFO[] struIPAlarmInInfo = (NET_DVR_IPALARMININFO[]) new NET_DVR_IPALARMININFO()
				.toArray(MAX_IP_ALARMIN);/* IP */
	}

	public static class NET_DVR_IPALARMINFO extends Structure {// ipc alarm info
		public NET_DVR_IPDEVINFO[] struIPDevInfo = (NET_DVR_IPDEVINFO[]) new NET_DVR_IPDEVINFO()
				.toArray(MAX_IP_DEVICE); /* IP */
		public byte[] byAnalogChanEnable = new byte[MAX_ANALOG_CHANNUM]; /* ,0- 1- */
		public NET_DVR_IPCHANINFO[] struIPChanInfo = (NET_DVR_IPCHANINFO[]) new NET_DVR_IPCHANINFO()
				.toArray(MAX_IP_CHANNEL); /* IP */
		public NET_DVR_IPALARMININFO[] struIPAlarmInInfo = (NET_DVR_IPALARMININFO[]) new NET_DVR_IPALARMININFO()
				.toArray(MAX_IP_ALARMIN); /* IP */
		public NET_DVR_IPALARMOUTINFO[] struIPAlarmOutInfo = (NET_DVR_IPALARMOUTINFO[]) new NET_DVR_IPALARMOUTINFO()
				.toArray(MAX_IP_ALARMOUT); /* IP */
	}

	public static class NET_DVR_SINGLE_HD extends Structure {//
		public int dwHDNo; /* , 0~MAX_DISKNUM_V30-1 */
		public int dwCapacity; /* ( ) */
		public int dwFreeSpace; /* ( ) */
		public int dwHdStatus; /* ( ) 0- , 1- , 2- , 3-SMART , 4- , 5- */
		public byte byHDAttr; /* 0- , 1- ; 2- */
		public byte[] byRes1 = new byte[3];
		public int dwHdGroup; /* 1-MAX_HD_GROUP */
		public byte[] byRes2 = new byte[120];
	}

	public static class NET_DVR_HDCFG extends Structure {
		public int dwSize;
		public int dwHDCount; /* ( ) */
		public NET_DVR_SINGLE_HD[] struHDInfo = (NET_DVR_SINGLE_HD[]) new NET_DVR_SINGLE_HD().toArray(MAX_DISKNUM_V30);// ;
	}

	public static class NET_DVR_SINGLE_HDGROUP extends Structure {//
		public int dwHDGroupNo; /* ( ) 1-MAX_HD_GROUP */
		public byte[] byHDGroupChans = new byte[64]; /* , 0- ,1- */
		public byte[] byRes = new byte[8];
	}

	public static class NET_DVR_HDGROUP_CFG extends Structure {
		public int dwSize;
		public int dwHDGroupCount; /* ( ) */
		public NET_DVR_SINGLE_HDGROUP[] struHDGroupAttr = (NET_DVR_SINGLE_HDGROUP[]) new NET_DVR_SINGLE_HDGROUP()
				.toArray(MAX_HD_GROUP);// ;
	}

	public static class NET_DVR_SCALECFG extends Structure {//
		public int dwSize;
		public int dwMajorScale; /* 0- ,1- */
		public int dwMinorScale; /* 0- ,1- */
		public int[] dwRes = new int[2];
	}

	public static class NET_DVR_ALARMOUTCFG_V30 extends Structure {// DVR (9000 )
		public int dwSize;
		public byte[] sAlarmOutName = new byte[NAME_LEN]; /*   */
		public int dwAlarmOutDelay; /* (-1 , ) */
		// 0-5 ,1-10 ,2-30 ,3-1 ,4-2 ,5-5 ,6-10 ,7-
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmOutTime = (NET_DVR_SCHEDTIMEWEEK[]) new NET_DVR_SCHEDTIMEWEEK()
				.toArray(MAX_DAYS);/*     */
		public byte[] byRes = new byte[16];
	}

	public static class NET_DVR_ALARMOUTCFG extends Structure {// DVR
		public int dwSize;
		public byte[] sAlarmOutName = new byte[NAME_LEN]; /*   */
		public int dwAlarmOutDelay; /* (-1 , ) */
		// 0-5 ,1-10 ,2-30 ,3-1 ,4-2 ,5-5 ,6-10 ,7-
		public NET_DVR_SCHEDTIMEWEEK[] struAlarmOutTime = (NET_DVR_SCHEDTIMEWEEK[]) new NET_DVR_SCHEDTIMEWEEK()
				.toArray(MAX_DAYS);/*     */
	}

	public static class NET_DVR_PREVIEWCFG_V30 extends Structure {// DVR (9000 )
		public int dwSize;
		public byte byPreviewNumber;// ,0-1 ,1-4 ,2-9 ,3-16 , 4-6 , 5-8 , 0xff:
		public byte byEnableAudio;// ,0- ,1-
		public short wSwitchTime;// ,0- ,1-5s,2-10s,3-20s,4-30s,5-60s,6-120s,7-300s
		public byte[][] bySwitchSeq = new byte[MAX_PREVIEW_MODE][MAX_WINDOW_V30];// , lSwitchSeq[i] 0xff
		public byte[] byRes = new byte[24];
	}

	public static class NET_DVR_PREVIEWCFG extends Structure {// DVR
		public int dwSize;
		public byte byPreviewNumber;// ,0-1 ,1-4 ,2-9 ,3-16 ,0xff:
		public byte byEnableAudio;// ,0- ,1-
		public short wSwitchTime;// ,0- ,1-5s,2-10s,3-20s,4-30s,5-60s,6-120s,7-300s
		public byte[] bySwitchSeq = new byte[MAX_WINDOW];// , lSwitchSeq[i] 0xff
	}

	public static class NET_DVR_VGAPARA extends Structure {// DVR
		public short wResolution; /*   */
		public short wFreq; /*     */
		public int dwBrightness; /*   */
	}

	/*
	 * MATRIX
	 */
	public static class NET_DVR_MATRIXPARA_V30 extends Structure {
		public short[] wOrder = new short[MAX_ANALOG_CHANNUM]; /* , 0xff */
		public short wSwitchTime; /*    */
		public byte[] res = new byte[14];
	}

	public static class NET_DVR_MATRIXPARA extends Structure {
		public short wDisplayLogo; /* ( ) */
		public short wDisplayOsd; /* ( ) */
	}

	public static class NET_DVR_VOOUT extends Structure {
		public byte byVideoFormat; /* ,0-PAL,1-NTSC */
		public byte byMenuAlphaValue; /*       */
		public short wScreenSaveTime; /* 0- ,1-1 ,2-2 ,3-5 ,4-10 ,5-20 ,6-30 */
		public short wVOffset; /*    */
		public short wBrightness; /*    */
		public byte byStartMode; /* (0: ,1: ) */
		public byte byEnableScaler; /* (0- , 1- ) */
	}

	public static class NET_DVR_VIDEOOUT_V30 extends Structure {// DVR (9000 )
		public int dwSize;
		public NET_DVR_VOOUT[] struVOOut = (NET_DVR_VOOUT[]) new NET_DVR_VOOUT().toArray(MAX_VIDEOOUT_V30);
		public NET_DVR_VGAPARA[] struVGAPara = (NET_DVR_VGAPARA[]) new NET_DVR_VGAPARA().toArray(MAX_VGA_V30); /* VGA */
		public NET_DVR_MATRIXPARA_V30[] struMatrixPara = (NET_DVR_MATRIXPARA_V30[]) new NET_DVR_MATRIXPARA_V30()
				.toArray(MAX_MATRIXOUT); /* MATRIX */
		public byte[] byRes = new byte[16];
	}

	public static class NET_DVR_VIDEOOUT extends Structure {// DVR
		public int dwSize;
		public NET_DVR_VOOUT[] struVOOut = (NET_DVR_VOOUT[]) new NET_DVR_VOOUT().toArray(MAX_VIDEOOUT);
		public NET_DVR_VGAPARA[] struVGAPara = (NET_DVR_VGAPARA[]) new NET_DVR_VGAPARA().toArray(MAX_VGA); /* VGA */
		public NET_DVR_MATRIXPARA struMatrixPara = new NET_DVR_MATRIXPARA(); /* MATRIX */
	}

	public static class NET_DVR_USER_INFO_V30 extends Structure {// ( )(9000 )
		public byte[] sUserName = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public byte[] byLocalRight = new byte[MAX_RIGHT]; /*     */
		public byte[] byRemoteRight = new byte[MAX_RIGHT];/*     */
		public byte[] byNetPreviewRight = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byLocalPlaybackRight = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byNetPlaybackRight = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byLocalRecordRight = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byNetRecordRight = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public byte[] byLocalPTZRight = new byte[MAX_CHANNUM_V30]; /* PTZ 0- ,1- */
		public byte[] byNetPTZRight = new byte[MAX_CHANNUM_V30]; /* PTZ 0- ,1- */
		public byte[] byLocalBackupRight = new byte[MAX_CHANNUM_V30]; /* 0- ,1- */
		public NET_DVR_IPADDR struUserIP = new NET_DVR_IPADDR(); /* IP ( 0 ) */
		public byte[] byMACAddr = new byte[MACADDR_LEN]; /*     */
		public byte byPriority; /* ,0xff- ,0-- ,1-- ,2-- */
		public byte[] byRes = new byte[17];
	}

	public static class NET_DVR_USER_INFO_EX extends Structure {// (SDK_V15 )( )
		public byte[] sUserName = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public int[] dwLocalRight = new int[MAX_RIGHT]; /*   */
		public int dwLocalPlaybackRight; /* bit0 -- channel 1 */
		public int[] dwRemoteRight = new int[MAX_RIGHT]; /*   */
		public int dwNetPreviewRight; /* bit0 -- channel 1 */
		public int dwNetPlaybackRight; /* bit0 -- channel 1 */
		public byte[] sUserIP = new byte[16]; /* IP ( 0 ) */
		public byte[] byMACAddr = new byte[MACADDR_LEN]; /*     */
	}

	public static class NET_DVR_USER_INFO extends Structure {// ( )
		public byte[] sUserName = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public int[] dwLocalRight = new int[MAX_RIGHT]; /*   */
		public int[] dwRemoteRight = new int[MAX_RIGHT]; /*   */
		public byte[] sUserIP = new byte[16]; /* IP ( 0 ) */
		public byte[] byMACAddr = new byte[MACADDR_LEN]; /*     */
	}

	public static class NET_DVR_USER_V30 extends Structure {// DVR (9000 )
		public int dwSize;
		public NET_DVR_USER_INFO_V30[] struUser = (NET_DVR_USER_INFO_V30[]) new NET_DVR_USER_INFO_V30()
				.toArray(MAX_USERNUM_V30);
	}

	public static class NET_DVR_USER_EX extends Structure {// DVR (SDK_V15 )
		public int dwSize;
		public NET_DVR_USER_INFO_EX[] struUser = (NET_DVR_USER_INFO_EX[]) new NET_DVR_USER_INFO_EX()
				.toArray(MAX_USERNUM);
	}

	public static class NET_DVR_USER extends Structure {// DVR
		public int dwSize;
		public NET_DVR_USER_INFO[] struUser = (NET_DVR_USER_INFO[]) new NET_DVR_USER_INFO().toArray(MAX_USERNUM);
	}

	public static class NET_DVR_EXCEPTION_V30 extends Structure {// DVR (9000 )
		public int dwSize;
		public NET_DVR_HANDLEEXCEPTION_V30[] struExceptionHandleType = (NET_DVR_HANDLEEXCEPTION_V30[]) new NET_DVR_HANDLEEXCEPTION_V30()
				.toArray(MAX_EXCEPTIONNUM_V30);
	}

	public static class NET_DVR_EXCEPTION extends Structure {// DVR
		public int dwSize;
		public NET_DVR_HANDLEEXCEPTION[] struExceptionHandleType = (NET_DVR_HANDLEEXCEPTION[]) new NET_DVR_HANDLEEXCEPTION()
				.toArray(MAX_EXCEPTIONNUM);
	}

	public static class NET_DVR_CHANNELSTATE_V30 extends Structure {// (9000 )
		public byte byRecordStatic; // ,0- ,1-
		public byte bySignalStatic; // ,0- ,1-
		public byte byHardwareStatic;// ,0- ,1- , DSP
		public byte reservedData; //
		public int dwBitRate;//
		public int dwLinkNum;//
		public NET_DVR_IPADDR[] struClientIP = (NET_DVR_IPADDR[]) new NET_DVR_IPADDR().toArray(MAX_LINK);// IP
		public int dwIPLinkNum;// IP , IP
		public byte[] byRes = new byte[12];
	}

	public static class NET_DVR_CHANNELSTATE extends Structure {//
		public byte byRecordStatic; // ,0- ,1-
		public byte bySignalStatic; // ,0- ,1-
		public byte byHardwareStatic;// ,0- ,1- , DSP
		public byte reservedData; //
		public int dwBitRate;//
		public int dwLinkNum;//
		public int[] dwClientIP = new int[MAX_LINK];// IP
	}

	public static class NET_DVR_DISKSTATE extends Structure {//
		public int dwVolume;//
		public int dwFreeSpace;//
		public int dwHardDiskStatic; // , :1- ,2- ,3-
	}

	public static class NET_DVR_WORKSTATE_V30 extends Structure {// DVR (9000 )
		public int dwDeviceStatic; // ,0- ,1-CPU , 85%,2- ,
		public NET_DVR_DISKSTATE[] struHardDiskStatic = (NET_DVR_DISKSTATE[]) new NET_DVR_DISKSTATE()
				.toArray(MAX_DISKNUM_V30);
		public NET_DVR_CHANNELSTATE_V30[] struChanStatic = (NET_DVR_CHANNELSTATE_V30[]) new NET_DVR_CHANNELSTATE_V30()
				.toArray(MAX_CHANNUM_V30);//
		public byte[] byAlarmInStatic = new byte[MAX_ALARMIN_V30]; // ,0- ,1-
		public byte[] byAlarmOutStatic = new byte[MAX_ALARMOUT_V30]; // ,0- ,1-
		public int dwLocalDisplay;// ,0- ,1-
		public byte[] byAudioChanStatus = new byte[MAX_AUDIO_V30];// 0- ,1- , 0xff
		public byte[] byRes = new byte[10];
	}

	public static class NET_DVR_WORKSTATE extends Structure {// DVR
		public int dwDeviceStatic; // ,0- ,1-CPU , 85%,2- ,
		public NET_DVR_DISKSTATE[] struHardDiskStatic = (NET_DVR_DISKSTATE[]) new NET_DVR_DISKSTATE()
				.toArray(MAX_DISKNUM);
		public NET_DVR_CHANNELSTATE[] struChanStatic = (NET_DVR_CHANNELSTATE[]) new NET_DVR_CHANNELSTATE()
				.toArray(MAX_CHANNUM);//
		public byte[] byAlarmInStatic = new byte[MAX_ALARMIN]; // ,0- ,1-
		public byte[] byAlarmOutStatic = new byte[MAX_ALARMOUT]; // ,0- ,1-
		public int dwLocalDisplay;// ,0- ,1-
	}

	public static class NET_DVR_LOG_V30 extends Structure {// (9000 )
		public NET_DVR_TIME strLogTime = new NET_DVR_TIME();
		public int dwMajorType; // 1- ; 2- ; 3- ; 0xff-
		public int dwMinorType;// 0- ;
		public byte[] sPanelUser = new byte[MAX_NAMELEN]; //
		public byte[] sNetUser = new byte[MAX_NAMELEN];//
		public NET_DVR_IPADDR struRemoteHostAddr = new NET_DVR_IPADDR();//
		public int dwParaType;//
		public int dwChannel;//
		public int dwDiskNumber;//
		public int dwAlarmInPort;//
		public int dwAlarmOutPort;//
		public int dwInfoLen;
		public byte[] sInfo = new byte[LOG_INFO_LEN];
	}

	//
	public static class NET_DVR_LOG extends Structure {
		public NET_DVR_TIME strLogTime = new NET_DVR_TIME();
		public int dwMajorType; // 1- ; 2- ; 3- ; 0xff-
		public int dwMinorType;// 0- ;
		public byte[] sPanelUser = new byte[MAX_NAMELEN]; //
		public byte[] sNetUser = new byte[MAX_NAMELEN];//
		public byte[] sRemoteHostAddr = new byte[16];//
		public int dwParaType;//
		public int dwChannel;//
		public int dwDiskNumber;//
		public int dwAlarmInPort;//
		public int dwAlarmOutPort;//
	}

	/************************ DVR end ***************************/
	public static class NET_DVR_ALARMOUTSTATUS_V30 extends Structure {// (9000 )
		public byte[] Output = new byte[MAX_ALARMOUT_V30];
	}

	public static class NET_DVR_ALARMOUTSTATUS extends Structure {//
		public byte[] Output = new byte[MAX_ALARMOUT];
	}

	public static class NET_DVR_TRADEINFO extends Structure {//
		public short m_Year;
		public short m_Month;
		public short m_Day;
		public short m_Hour;
		public short m_Minute;
		public short m_Second;
		public byte[] DeviceName = new byte[24]; //
		public int dwChannelNumer; //
		public byte[] CardNumber = new byte[32]; //
		public byte[] cTradeType = new byte[12]; //
		public int dwCash; //
	}

	public static class NET_DVR_FRAMETYPECODE extends Structure {/*   */
		public byte[] code = new byte[12]; /*   */
	}

	public static class NET_DVR_FRAMEFORMAT_V30 extends Structure {// ATM (9000 )
		public int dwSize;
		public NET_DVR_IPADDR struATMIP = new NET_DVR_IPADDR(); /* ATM IP */
		public int dwATMType; /* ATM */
		public int dwInputMode; /* 0- 1- 2- 3- ATM */
		public int dwFrameSignBeginPos; /*       */
		public int dwFrameSignLength; /*     */
		public byte[] byFrameSignContent = new byte[12]; /*     */
		public int dwCardLengthInfoBeginPos; /*      */
		public int dwCardLengthInfoLength; /*     */
		public int dwCardNumberInfoBeginPos; /*     */
		public int dwCardNumberInfoLength; /*      */
		public int dwBusinessTypeBeginPos; /*     */
		public int dwBusinessTypeLength; /*      */
		public NET_DVR_FRAMETYPECODE[] frameTypeCode = (NET_DVR_FRAMETYPECODE[]) new NET_DVR_FRAMETYPECODE()
				.toArray(10); /*   */
		public short wATMPort; /* ( ) ( )0xffff */
		public short wProtocolType; /* ( ) 0xffff */
		public byte[] byRes = new byte[24];
	}

	public static class NET_DVR_FRAMEFORMAT extends Structure {// ATM
		public int dwSize;
		public byte[] sATMIP = new byte[16]; /* ATM IP */
		public int dwATMType; /* ATM */
		public int dwInputMode; /* 0- 1- 2- 3- ATM */
		public int dwFrameSignBeginPos; /*       */
		public int dwFrameSignLength; /*     */
		public byte[] byFrameSignContent = new byte[12]; /*     */
		public int dwCardLengthInfoBeginPos; /*      */
		public int dwCardLengthInfoLength; /*     */
		public int dwCardNumberInfoBeginPos; /*     */
		public int dwCardNumberInfoLength; /*      */
		public int dwBusinessTypeBeginPos; /*     */
		public int dwBusinessTypeLength; /*      */
		public NET_DVR_FRAMETYPECODE[] frameTypeCode = (NET_DVR_FRAMETYPECODE[]) new NET_DVR_FRAMETYPECODE()
				.toArray(10); /*   */
	}

	public static class NET_DVR_FTPTYPECODE extends Structure {
		public byte[] sFtpType = new byte[32]; /*     */
		public byte[] sFtpCode = new byte[8]; /*       */
	}

	public static class NET_DVR_FRAMEFORMAT_EX extends Structure {// ATM FTP , , 2006-11-17
		public int dwSize;
		public byte[] sATMIP = new byte[16]; /* ATM IP */
		public int dwATMType; /* ATM */
		public int dwInputMode; /* 0- 1- 2- 3- ATM */
		public int dwFrameSignBeginPos; /*       */
		public int dwFrameSignLength; /*     */
		public byte[] byFrameSignContent = new byte[12]; /*     */
		public int dwCardLengthInfoBeginPos; /*      */
		public int dwCardLengthInfoLength; /*     */
		public int dwCardNumberInfoBeginPos; /*     */
		public int dwCardNumberInfoLength; /*      */
		public int dwBusinessTypeBeginPos; /*     */
		public int dwBusinessTypeLength; /*      */
		public NET_DVR_FRAMETYPECODE[] frameTypeCode = (NET_DVR_FRAMETYPECODE[]) new NET_DVR_FRAMETYPECODE()
				.toArray(10); /*   */
		public byte[] sFTPIP = new byte[16]; /* FTP IP */
		public byte[] byFtpUsername = new byte[NAME_LEN]; /*   */
		public byte[] byFtpPasswd = new byte[PASSWD_LEN]; /*   */
		public byte[] sDirName = new byte[NAME_LEN]; /*    */
		public int dwATMSrvType; /* ATM ,0--wincor ,1--diebold */
		public int dwTimeSpace; /* 1.2.3.4.5.10 */
		public NET_DVR_FTPTYPECODE[] sFtpTypeCodeOp = (NET_DVR_FTPTYPECODE[]) new NET_DVR_FTPTYPECODE()
				.toArray(300); /*   */
		public int dwADPlay; /* 1 ,0 */
		public int dwNewPort; //
	}

	public static class Bind extends Structure {
		public boolean bind;
	}

	/**************************** ATM(end) ***************************/

	/***************************** DS-6001D/F(begin) ***************************/
	// DS-6001D Decoder
	public static class NET_DVR_DECODERINFO extends Structure {
		public byte[] byEncoderIP = new byte[16]; // IP
		public byte[] byEncoderUser = new byte[16]; //
		public byte[] byEncoderPasswd = new byte[16]; //
		public byte bySendMode; //
		public byte byEncoderChannel; //
		public short wEncoderPort; //
		public byte[] reservedData = new byte[4]; //
	}

	public static class NET_DVR_DECODERSTATE extends Structure {
		public byte[] byEncoderIP = new byte[16]; // IP
		public byte[] byEncoderUser = new byte[16]; //
		public byte[] byEncoderPasswd = new byte[16]; //
		public byte byEncoderChannel; //
		public byte bySendMode; //
		public short wEncoderPort; //
		public int dwConnectState; //
		public byte[] reservedData = new byte[4]; //
	}

	public static class NET_DVR_DECCHANINFO extends Structure {
		public byte[] sDVRIP = new byte[16]; /* DVR IP */
		public short wDVRPort; /*   */
		public byte[] sUserName = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public byte byChannel; /*   */
		public byte byLinkMode; /*     */
		public byte byLinkType; /* 0- 1- */
	}

	public static class NET_DVR_DECINFO extends Structure {/*     */
		public byte byPoolChans; /* , 4 0 */
		public NET_DVR_DECCHANINFO[] struchanConInfo = (NET_DVR_DECCHANINFO[]) new NET_DVR_DECCHANINFO()
				.toArray(MAX_DECPOOLNUM);
		public byte byEnablePoll; /* 0- 1- */
		public byte byPoolTime; /* 0- 1-10 2-15 3-20 4-30 5-45 6-1 7-2 8-5 */
	}

	public static class NET_DVR_DECCFG extends Structure {/*     */
		public int dwSize;
		public int dwDecChanNum; /*      */
		public NET_DVR_DECINFO[] struDecInfo = (NET_DVR_DECINFO[]) new NET_DVR_DECINFO().toArray(MAX_DECNUM);
	}

	// 2005-08-01
	public static class NET_DVR_PORTINFO extends Structure {/*       */
		public int dwEnableTransPort; /* 0- 1- */
		public byte[] sDecoderIP = new byte[16]; /* DVR IP */
		public short wDecoderPort; /*   */
		public short wDVRTransPort; /* DVR 485/232 ,1 232 ,2 485 */
		public byte[] cReserve = new byte[4];
	}

	public static class NET_DVR_PORTCFG extends Structure {
		public int dwSize;
		public NET_DVR_PORTINFO[] struTransPortInfo = (NET_DVR_PORTINFO[]) new NET_DVR_PORTINFO()
				.toArray(MAX_TRANSPARENTNUM); /* 0 232 1 485 */
	}

	/*
	 * https://jna.dev.java.net/javadoc/com/sun/jna/Union.html#setType(java.lang.
	 * Class) see how to use the JNA Union
	 */
	public static class NET_DVR_PLAYREMOTEFILE extends Structure {/*     */
		public int dwSize;
		public byte[] sDecoderIP = new byte[16]; /* DVR IP */
		public short wDecoderPort; /*   */
		public short wLoadMode; /* 1- 2- */
		public byte[] byFile = new byte[100];

		public static class mode_size extends Union {
			public byte[] byFile = new byte[100]; //

			public static class bytime extends Structure {
				public int dwChannel;
				public byte[] sUserName = new byte[NAME_LEN]; //
				public byte[] sPassword = new byte[PASSWD_LEN]; //
				public NET_DVR_TIME struStartTime = new NET_DVR_TIME(); //
				public NET_DVR_TIME struStopTime = new NET_DVR_TIME(); //
			}
		}
	}

	public static class NET_DVR_DECCHANSTATUS extends Structure {/*       */
		public int dwWorkType; /* :1: 2: 3: 4: */
		public byte[] sDVRIP = new byte[16]; /* ip */
		public short wDVRPort; /*    */
		public byte byChannel; /*   */
		public byte byLinkMode; /*     */
		public int dwLinkType; /* 0- 1- */
		public byte[] sUserName = new byte[NAME_LEN]; /*      */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public byte[] cReserve = new byte[52];

		public static class objectInfo extends Union {
			public static class userInfo extends Structure {
				public byte[] sUserName = new byte[NAME_LEN]; //
				public byte[] sPassword = new byte[PASSWD_LEN]; //
				public byte[] cReserve = new byte[52];
			}

			public static class fileInfo extends Structure {
				public byte[] fileName = new byte[100];
			}

			public static class timeInfo extends Structure {
				public int dwChannel;
				public byte[] sUserName = new byte[NAME_LEN]; //
				public byte[] sPassword = new byte[PASSWD_LEN]; //
				public NET_DVR_TIME struStartTime = new NET_DVR_TIME(); //
				public NET_DVR_TIME struStopTime = new NET_DVR_TIME(); //
			}
		}
	}

	public static class NET_DVR_DECSTATUS extends Structure {
		public int dwSize;
		public NET_DVR_DECCHANSTATUS[] struDecState = (NET_DVR_DECCHANSTATUS[]) new NET_DVR_DECCHANSTATUS()
				.toArray(MAX_DECNUM);
	}

	/***************************** DS-6001D/F(end) ***************************/

	public static class NET_DVR_SHOWSTRINGINFO extends Structure {// ( )
		public short wShowString; // ,0- ,1- 704*576, 32*32
		public short wStringSize; /* , 44 */
		public short wShowStringTopLeftX; /* x */
		public short wShowStringTopLeftY; /* y */
		public byte[] sString = new byte[44]; /*     */
	}

	// (9000 )
	public static class NET_DVR_SHOWSTRING_V30 extends Structure {
		public int dwSize;
		public NET_DVR_SHOWSTRINGINFO[] struStringInfo = (NET_DVR_SHOWSTRINGINFO[]) new NET_DVR_SHOWSTRINGINFO()
				.toArray(MAX_STRINGNUM_V30); /*     */
	}

	// (8 )
	public static class NET_DVR_SHOWSTRING_EX extends Structure {
		public int dwSize;
		public NET_DVR_SHOWSTRINGINFO[] struStringInfo = (NET_DVR_SHOWSTRINGINFO[]) new NET_DVR_SHOWSTRINGINFO()
				.toArray(MAX_STRINGNUM_EX); /*     */
	}

	//
	public static class NET_DVR_SHOWSTRING extends Structure {
		public int dwSize;
		public NET_DVR_SHOWSTRINGINFO[] struStringInfo = (NET_DVR_SHOWSTRINGINFO[]) new NET_DVR_SHOWSTRINGINFO()
				.toArray(MAX_STRINGNUM); /*     */
	}

	/**************************** DS9000 (begin) ******************************/

	/*
	 * EMAIL
	 */
	public static class NET_DVR_SENDER extends Structure {
		public byte[] sName = new byte[NAME_LEN]; /*    */
		public byte[] sAddress = new byte[MAX_EMAIL_ADDR_LEN]; /*    */
	}

	public static class NET_DVRRECEIVER extends Structure {
		public byte[] sName = new byte[NAME_LEN]; /*    */
		public byte[] sAddress = new byte[MAX_EMAIL_ADDR_LEN]; /*    */
	}

	public static class NET_DVR_EMAILCFG_V30 extends Structure {
		public int dwSize;
		public byte[] sAccount = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[MAX_EMAIL_PWD_LEN]; /*   */
		public NET_DVR_SENDER struSender = new NET_DVR_SENDER();
		public byte[] sSmtpServer = new byte[MAX_EMAIL_ADDR_LEN]; /* smtp */
		public byte[] sPop3Server = new byte[MAX_EMAIL_ADDR_LEN]; /* pop3 */
		public NET_DVRRECEIVER[] struReceiver = (NET_DVRRECEIVER[]) new NET_DVRRECEIVER().toArray(3); /* 3 */
		public byte byAttachment; /*    */
		public byte bySmtpServerVerify; /*      */
		public byte byMailInterval; /* mail interval */
		public byte[] res = new byte[77];
	}

	/*
	 * DVR
	 */
	public static class NET_DVR_CRUISE_PARA extends Structure {
		public int dwSize;
		public byte[] byPresetNo = new byte[CRUISE_MAX_PRESET_NUMS]; /*     */
		public byte[] byCruiseSpeed = new byte[CRUISE_MAX_PRESET_NUMS]; /*     */
		public short[] wDwellTime = new short[CRUISE_MAX_PRESET_NUMS]; /*     */
		public byte[] byEnableThisCruise; /*     */
		public byte[] res = new byte[15];
	}

	/**************************** DS9000 (end) ******************************/

	//
	public static class NET_DVR_TIMEPOINT extends Structure {
		public int dwMonth; // 0-11 1-12
		public int dwWeekNo; // 0- 1 1- 2 2- 3 3- 4 4-
		public int dwWeekDate; // 0- 1- 2- 3- 4- 5- 6-
		public int dwHour; // 0-23 1-23
		public int dwMin; // 0-59
	}

	//
	public static class NET_DVR_ZONEANDDST extends Structure {
		public int dwSize;
		public byte[] byRes1 = new byte[16]; //
		public int dwEnableDST; // 0- 1-
		public byte byDSTBias; // ,30min, 60min, 90min, 120min, ,
		public byte[] byRes2 = new byte[3];
		public NET_DVR_TIMEPOINT struBeginPoint = new NET_DVR_TIMEPOINT(); //
		public NET_DVR_TIMEPOINT struEndPoint = new NET_DVR_TIMEPOINT(); //
	}

	//
	public static class NET_DVR_JPEGPARA extends Structure {
		/*
		 * : VGA , 0=CIF, 1=QCIF, 2=D1 , 3=UXGA(1600x1200), 4=SVGA(800x600),
		 * 5=HD720p(1280x720),6=VGA,7=XVGA, 8=HD900p
		 */
		public short wPicSize; /*
								 * 0=CIF, 1=QCIF, 2=D1 3=UXGA(1600x1200), 4=SVGA(800x600),
								 * 5=HD720p(1280x720),6=VGA
								 */
		public short wPicQuality; /* 0- 1- 2- */
	}

	/* aux video out parameter */
	//
	public static class NET_DVR_AUXOUTCFG extends Structure {
		public int dwSize;
		public int dwAlarmOutChan; /* :1 : 0: /1: 1/2: 2/3: 3/4: 4 */
		public int dwAlarmChanSwitchTime; /* :1 - 10:10 */
		public int[] dwAuxSwitchTime = new int[MAX_AUXOUT]; /*
															 * : 0- ,1-5s,2-10s,3-20s,4-30s,5-60s,6-120s,7-300s
															 */
		public byte[][] byAuxOrder = new byte[MAX_AUXOUT][MAX_WINDOW]; /* , 0xff */
	}

	// ntp
	public static class NET_DVR_NTPPARA extends Structure {
		public byte[] sNTPServer = new byte[64]; /* Domain Name or IP addr of NTP server */
		public short wInterval; /* adjust time interval(hours) */
		public byte byEnableNTP; /* enable NPT client 0-no,1-yes */
		public byte cTimeDifferenceH; /* -12 ... +13 */
		public byte cTimeDifferenceM;/* 0, 30, 45 */
		public byte res1;
		public short wNtpPort; /* ntp server port 9000 123 */
		public byte[] res2 = new byte[8];
	}

	// ddns
	public static class NET_DVR_DDNSPARA extends Structure {
		public byte[] sUsername = new byte[NAME_LEN]; /* DDNS / */
		public byte[] sPassword = new byte[PASSWD_LEN];
		public byte[] sDomainName = new byte[64]; /*   */
		public byte byEnableDDNS; /* 0- ,1- */
		public byte[] res = new byte[15];
	}

	public static class NET_DVR_DDNSPARA_EX extends Structure {
		public byte byHostIndex; /* 0-Hikvision DNS 1-Dyndns 2-PeanutHull( ), 3- 3322 */
		public byte byEnableDDNS; /* DDNS 0- ,1- */
		public short wDDNSPort; /* DDNS */
		public byte[] sUsername = new byte[NAME_LEN]; /* DDNS */
		public byte[] sPassword = new byte[PASSWD_LEN]; /* DDNS */
		public byte[] sDomainName = new byte[MAX_DOMAIN_NAME]; /*     */
		public byte[] sServerName = new byte[MAX_DOMAIN_NAME]; /* DDNS , IP */
		public byte[] byRes = new byte[16];
	}

	public static class NET_DVR_DDNS extends Structure {
		public byte[] sUsername = new byte[NAME_LEN]; /* DDNS */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public byte[] sDomainName = new byte[MAX_DOMAIN_NAME]; /*     */
		public byte[] sServerName = new byte[MAX_DOMAIN_NAME]; /* DDNS , IP */
		public short wDDNSPort; /*   */
		public byte[] byRes = new byte[10];
	}

	// 9000
	public static class NET_DVR_DDNSPARA_V30 extends Structure {
		public byte byEnableDDNS;
		public byte byHostIndex;/* 0-Hikvision DNS( ) 1-Dyndns 2-PeanutHull( ) 3- 3322 */
		public byte[] byRes1 = new byte[2];
		public NET_DVR_DDNS[] struDDNS = (NET_DVR_DDNS[]) new NET_DVR_DDNS().toArray(MAX_DDNS_NUMS);// 9000 3 ,
		public byte[] byRes2 = new byte[16];
	}

	// email
	public static class NET_DVR_EMAILPARA extends Structure {
		public byte[] sUsername = new byte[64]; /* / */
		public byte[] sPassword = new byte[64];
		public byte[] sSmtpServer = new byte[64];
		public byte[] sPop3Server = new byte[64];
		public byte[] sMailAddr = new byte[64]; /* email */
		public byte[] sEventMailAddr1 = new byte[64]; /* / email */
		public byte[] sEventMailAddr2 = new byte[64];
		public byte[] res = new byte[16];
	}

	public static class NET_DVR_NETAPPCFG extends Structure {//
		public int dwSize;
		public byte[] sDNSIp = new byte[16]; /* DNS */
		public NET_DVR_NTPPARA struNtpClientParam = new NET_DVR_NTPPARA(); /* NTP */
		public NET_DVR_DDNSPARA struDDNSClientParam = new NET_DVR_DDNSPARA(); /* DDNS */
		// NET_DVR_EMAILPARA struEmailParam; /* EMAIL */
		public byte[] res = new byte[464]; /*   */
	}

	public static class NET_DVR_SINGLE_NFS extends Structure {// nfs
		public byte[] sNfsHostIPAddr = new byte[16];
		public byte[] sNfsDirectory = new byte[PATHNAME_LEN]; // PATHNAME_LEN = 128
	}

	public static class NET_DVR_NFSCFG extends Structure {
		public int dwSize;
		public NET_DVR_SINGLE_NFS[] struNfsDiskParam = (NET_DVR_SINGLE_NFS[]) new NET_DVR_SINGLE_NFS()
				.toArray(MAX_NFS_DISK);
	}

	// (HIK IP )
	public static class NET_DVR_CRUISE_POINT extends Structure {
		public byte PresetNum; //
		public byte Dwell; //
		public byte Speed; //
		public byte Reserve; //
	}

	public static class NET_DVR_CRUISE_RET extends Structure {
		public NET_DVR_CRUISE_POINT[] struCruisePoint = (NET_DVR_CRUISE_POINT[]) new NET_DVR_CRUISE_POINT().toArray(32); // 32
	}

	/************************************
	 * (begin)
	 ***************************************/
	// added by zxy 2007-05-23
	public static class NET_DVR_NETCFG_OTHER extends Structure {
		public int dwSize;
		public byte[] sFirstDNSIP = new byte[16];
		public byte[] sSecondDNSIP = new byte[16];
		public byte[] sRes = new byte[32];
	}

	public static class NET_DVR_MATRIX_DECINFO extends Structure {
		public byte[] sDVRIP = new byte[16]; /* DVR IP */
		public short wDVRPort; /*   */
		public byte byChannel; /*   */
		public byte byTransProtocol; /* 0-TCP 1-UDP */
		public byte byTransMode; /* 0- 1- */
		public byte[] byRes = new byte[3];
		public byte[] sUserName = new byte[NAME_LEN]; /*     */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*    */
	}

	public static class NET_DVR_MATRIX_DYNAMIC_DEC extends Structure {// /
		public int dwSize;
		public NET_DVR_MATRIX_DECINFO struDecChanInfo = new NET_DVR_MATRIX_DECINFO(); /*     */
	}

	public static class NET_DVR_MATRIX_DEC_CHAN_STATUS extends Structure {// 2007-12-13 modified by zxy
																			// NET_DVR_MATRIX_DEC_CHAN_STATUS
		public int dwSize;// 2008-1-16 modified by zxy dwIsLinked 0- 1-
		public int dwIsLinked; /* 0- 1- 2- 3- */
		public int dwStreamCpRate; /* Stream copy rate, X kbits/second */
		public byte[] cRes = new byte[64]; /*   */
	}
	// end 2007-12-13 modified by zxy

	public static class NET_DVR_MATRIX_DEC_CHAN_INFO extends Structure {
		public int dwSize;
		public NET_DVR_MATRIX_DECINFO struDecChanInfo = new NET_DVR_MATRIX_DECINFO(); /*    */
		public int dwDecState; /* 0- 1- 2- 3- */
		public NET_DVR_TIME StartTime = new NET_DVR_TIME(); /*     */
		public NET_DVR_TIME StopTime = new NET_DVR_TIME(); /*     */
		public byte[] sFileName = new byte[128]; /*     */
	}

	// 2007-11-05
	public static class NET_DVR_MATRIX_DECCHANINFO extends Structure {
		public int dwEnable; /* 0- 1- */
		public NET_DVR_MATRIX_DECINFO struDecChanInfo = new NET_DVR_MATRIX_DECINFO(); /*     */
	}

	// 2007-11-05
	public static class NET_DVR_MATRIX_LOOP_DECINFO extends Structure {
		public int dwSize;
		public int dwPoolTime; /*     */
		public NET_DVR_MATRIX_DECCHANINFO[] struchanConInfo = (NET_DVR_MATRIX_DECCHANINFO[]) new NET_DVR_MATRIX_DECCHANINFO()
				.toArray(MAX_CYCLE_CHAN);
	}

	// 2007-05-25
	// 2007-12-28
	public static class NET_DVR_MATRIX_ROW_ELEMENT extends Structure {
		public byte[] sSurvChanName = new byte[128]; /* , */
		public int dwRowNum; /*   */
		public NET_DVR_MATRIX_DECINFO struDecChanInfo = new NET_DVR_MATRIX_DECINFO(); /*    */
	}

	public static class NET_DVR_MATRIX_ROW_INDEX extends Structure {
		public byte[] sSurvChanName = new byte[128]; /* , */
		public int dwRowNum; /*   */
	}

	// 2007-12-28
	public static class NET_DVR_MATRIX_COLUMN_ELEMENT extends Structure {
		public int dwLocalDispChanNum; /*      */
		public int dwGlobalDispChanNum; /*      */
		public int dwRes; /*   */
	}

	public static class NET_DVR_MATRIX_GLOBAL_COLUMN_ELEMENT extends Structure {
		public int dwConflictTag; /* ,0: ,1: */
		public int dwConflictGloDispChan; /*       */
		public NET_DVR_MATRIX_COLUMN_ELEMENT struColumnInfo = new NET_DVR_MATRIX_COLUMN_ELEMENT();/*     */
	}

	// 2007-12-28
	public static class NET_DVR_MATRIX_ROW_COLUMN_LINK extends Structure {
		public int dwSize;
		/*
		 * ,
		 */
		public int dwRowNum; /* -1 , 0 */
		public byte[] sSurvChanName = new byte[128]; /* , */
		public int dwSurvNum; /* , , */
		/*
		 * ,
		 */
		public int dwGlobalDispChanNum; /*       */
		public int dwLocalDispChanNum;
		/*
		 * 0 , 1 2
		 */
		public int dwTimeSel;
		public NET_DVR_TIME StartTime = new NET_DVR_TIME();
		public NET_DVR_TIME StopTime = new NET_DVR_TIME();
		public byte[] sFileName = new byte[128];
	}

	public static class NET_DVR_MATRIX_PREVIEW_DISP_CHAN extends Structure {
		public int dwSize;
		public int dwGlobalDispChanNum; /*       */
		public int dwLocalDispChanNum; /*     */
	}

	public static class NET_DVR_MATRIX_LOOP_PLAY_SET extends Structure {// 2007-12-28
		public int dwSize;
		/* ,-1 , LocalDispChanNum */
		public int dwLocalDispChanNum; /*     */
		public int dwGlobalDispChanNum; /*       */
		public int dwCycTimeInterval; /*    */
	}

	public static class NET_DVR_MATRIX_LOCAL_HOST_INFO extends Structure {// 2007-12-28
		public int dwSize;
		public int dwLocalHostProperty; /* 0- 1- */
		public int dwIsIsolated; /* ,0: ,1: */
		public int dwLocalMatrixHostPort; /*     */
		public byte[] byLocalMatrixHostUsrName = new byte[NAME_LEN]; /*     */
		public byte[] byLocalMatrixHostPasswd = new byte[PASSWD_LEN]; /*     */
		public int dwLocalMatrixCtrlMedia; /* 0x1 0x2 0x4 0x8PC */
		public byte[] sMatrixCenterIP = new byte[16]; /* IP */
		public int dwMatrixCenterPort; /*      */
		public byte[] byMatrixCenterUsrName = new byte[NAME_LEN]; /*     */
		public byte[] byMatrixCenterPasswd = new byte[PASSWD_LEN]; /*     */
	}

	// 2007-12-22
	public static class TTY_CONFIG extends Structure {
		public byte baudrate; /*   */
		public byte databits; /*   */
		public byte stopbits; /*   */
		public byte parity; /*    */
		public byte flowcontrol; /*   */
		public byte[] res = new byte[3];
	}

	public static class NET_DVR_MATRIX_TRAN_CHAN_INFO extends Structure {
		public byte byTranChanEnable; /* 0: 1: */
		/*
		 * 1 485 ,1 232 , : 0 RS485 1 RS232 Console
		 */
		public byte byLocalSerialDevice; /* Local serial device */
		/*
		 * , RS232, RS485 1 232 2 485
		 */
		public byte byRemoteSerialDevice; /* Remote output serial device */
		public byte res1; /*   */
		public byte[] sRemoteDevIP = new byte[16]; /* Remote Device IP */
		public short wRemoteDevPort; /* Remote Net Communication Port */
		public byte[] res2 = new byte[2]; /*   */
		public TTY_CONFIG RemoteSerialDevCfg = new TTY_CONFIG();
	}

	public static class NET_DVR_MATRIX_TRAN_CHAN_CONFIG extends Structure {
		public int dwSize;
		public byte by232IsDualChan; /* 232 1 MAX_SERIAL_NUM */
		public byte by485IsDualChan; /* 485 1 MAX_SERIAL_NUM */
		public byte[] res = new byte[2]; /*   */
		public NET_DVR_MATRIX_TRAN_CHAN_INFO[] struTranInfo = (NET_DVR_MATRIX_TRAN_CHAN_INFO[]) new NET_DVR_MATRIX_TRAN_CHAN_INFO()
				.toArray(MAX_SERIAL_NUM);/* MAX_SERIAL_NUM */
	}

	// 2007-12-24 Merry Christmas Eve...
	public static class NET_DVR_MATRIX_DEC_REMOTE_PLAY extends Structure {
		public int dwSize;
		public byte[] sDVRIP = new byte[16]; /* DVR IP */
		public short wDVRPort; /*   */
		public byte byChannel; /*   */
		public byte byReserve;
		public byte[] sUserName = new byte[NAME_LEN]; /*   */
		public byte[] sPassword = new byte[PASSWD_LEN]; /*   */
		public int dwPlayMode; /* 0- 1- */
		public NET_DVR_TIME StartTime = new NET_DVR_TIME();
		public NET_DVR_TIME StopTime = new NET_DVR_TIME();
		public byte[] sFileName = new byte[128];
	}

	public static class NET_DVR_MATRIX_DEC_REMOTE_PLAY_CONTROL extends Structure {
		public int dwSize;
		public int dwPlayCmd; /*          */
		public int dwCmdParam; /*    */
	}

	public static class NET_DVR_MATRIX_DEC_REMOTE_PLAY_STATUS extends Structure {
		public int dwSize;
		public int dwCurMediaFileLen; /*      */
		public int dwCurMediaFilePosition; /*      */
		public int dwCurMediaFileDuration; /*       */
		public int dwCurPlayTime; /*     */
		public int dwCurMediaFIleFrames; /*       */
		public int dwCurDataType; /* ,19- ,20- , 21- */
		public byte[] res = new byte[72];
	}

	public static class NET_DVR_MATRIX_PASSIVEMODE extends Structure {
		public short wTransProtol; // ,0-TCP, 1-UDP, 2-MCAST
		public short wPassivePort; // TCP,UDP TCP,UDP , MCAST MCAST
		public byte[] sMcastIP = new byte[16]; // TCP,UDP , MCAST
		public byte[] res = new byte[8];
	}

	/************************************
	 * (end)
	 ***************************************/

	/************************************
	 * (end)
	 ***************************************/

	public static class NET_DVR_EMAILCFG extends Structure { /* 12 bytes */
		public int dwSize;
		public byte[] sUserName = new byte[32];
		public byte[] sPassWord = new byte[32];
		public byte[] sFromName = new byte[32]; /* Sender */// "@", "@"
		public byte[] sFromAddr = new byte[48]; /* Sender address */
		public byte[] sToName1 = new byte[32]; /* Receiver1 */
		public byte[] sToName2 = new byte[32]; /* Receiver2 */
		public byte[] sToAddr1 = new byte[48]; /* Receiver address1 */
		public byte[] sToAddr2 = new byte[48]; /* Receiver address2 */
		public byte[] sEmailServer = new byte[32]; /* Email server address */
		public byte byServerType; /* Email server type: 0-SMTP, 1-POP, 2-IMTP… */
		public byte byUseAuthen; /* Email server authentication method: 1-enable, 0-disable */
		public byte byAttachment; /* enable attachment */
		public byte byMailinterval; /* mail interval 0-2s, 1-3s, 2-4s. 3-5s */
	}

	public static class NET_DVR_COMPRESSIONCFG_NEW extends Structure {
		public int dwSize;
		public NET_DVR_COMPRESSION_INFO_EX struLowCompression = new NET_DVR_COMPRESSION_INFO_EX(); //
		public NET_DVR_COMPRESSION_INFO_EX struEventCompression = new NET_DVR_COMPRESSION_INFO_EX(); //
	}

	//
	public static class NET_DVR_PTZPOS extends Structure {
		public short wAction;//
		public short wPanPos;//
		public short wTiltPos;//
		public short wZoomPos;//
	}

	//
	public static class NET_DVR_PTZSCOPE extends Structure {
		public short wPanPosMin;// min
		public short wPanPosMax;// max
		public short wTiltPosMin;// min
		public short wTiltPosMax;// max
		public short wZoomPosMin;// min
		public short wZoomPosMax;// max
	}

	// rtsp ipcamera
	public static class NET_DVR_RTSPCFG extends Structure {
		public int dwSize; //
		public short wPort; // rtsp
		public byte[] byReserve = new byte[54]; //
	}

	/********************************
	 * (begin)
	 *********************************/

	public static class DEMO_CHANNEL_TYPE {
		public final static int DEMO_CHANNEL_TYPE_INVALID = -1;
		public final static int DEMO_CHANNEL_TYPE_ANALOG = 0;
		public final static int DEMO_CHANNEL_TYPE_IP = 1;
		public final static int DEMO_CHANNEL_TYPE_MIRROR = 2;
	};

	public static class STRU_CHANNEL_INFO extends Structure {
		public int iDeviceIndex; // device index
		public int iChanIndex; // channel index

		public int iChanType;
		public int iChannelNO; // channel NO.

		public byte[] chChanName = new byte[100]; // channel name
		public int dwProtocol; // network protocol

		public int dwStreamType; // ,0- ,1- ,2- 3,
		public int dwLinkMode;// : 0:TCP ,1:UDP ,2: ,3 - RTP ,4-RTP/RTSP,5-RSTP/HTTP

		public boolean bPassbackRecord; // 0- ,1
		public int dwPreviewMode; // 0- 1-
		public int iPicResolution; // resolution
		public int iPicQuality; // image quality
		public NativeLong lRealHandle; // preview handle
		public boolean bLocalManualRec; // manual record
		public boolean bAlarm; // alarm
		public boolean bEnable; // enable
		public int dwImageType; // channel status icon
		public byte[] chAccessChanIP = new byte[16];// ip addr of IP channel
		public int nPreviewProtocolType;
		public Pointer pNext;

		public void init() {
			iDeviceIndex = -1;
			iChanIndex = -1;
			iChannelNO = -1;
			iChanType = DEMO_CHANNEL_TYPE.DEMO_CHANNEL_TYPE_INVALID;
			chChanName = null;
			dwProtocol = 0;

			dwStreamType = 0;
			dwLinkMode = 0;

			iPicResolution = 0;
			iPicQuality = 2;

			lRealHandle = new NativeLong(-1);
			bLocalManualRec = false;
			bAlarm = false;
			bEnable = false;
			dwImageType = 6;
			chAccessChanIP = null;
			pNext = null;
			dwPreviewMode = 0;
			bPassbackRecord = false;
			nPreviewProtocolType = 0;
		}
	}

	public static class NET_DVR_IPDEVINFO_V31 extends Structure {
		public byte byEnable; // IP
		public byte byProType; // ,0- ,1- ,2-
		public byte byEnableQuickAdd; // 0 1
		// IP ,
		public byte byRes1; // , 0
		public byte[] sUserName = new byte[NAME_LEN]; //
		public byte[] sPassword = new byte[PASSWD_LEN]; //
		public byte[] byDomain = new byte[MAX_DOMAIN_NAME]; //
		public NET_DVR_IPADDR struIP = new NET_DVR_IPADDR(); // IP
		public short wDVRPort; //
		public byte[] szDeviceID = new byte[DEV_ID_LEN]; // ID
		public byte[] byRes2 = new byte[2]; // , 0
	}

	public static class NET_DVR_IPSERVER_STREAM extends Structure {
		public byte[] byEnable; //
		public byte[] byRes = new byte[3]; //
		public NET_DVR_IPADDR struIPServer = new NET_DVR_IPADDR(); // IPServer
		public short wPort; // IPServer
		public short wDvrNameLen; // DVR
		public byte[] byDVRName = new byte[NAME_LEN]; // DVR
		public short wDVRSerialLen; //
		public short[] byRes1 = new short[2]; //
		public byte[] byDVRSerialNumber = new byte[SERIALNO_LEN]; // DVR
		public byte[] byUserName = new byte[NAME_LEN]; // DVR
		public byte[] byPassWord = new byte[PASSWD_LEN]; // DVR
		public byte byChannel; // DVR
		public byte[] byRes2 = new byte[11]; //
	}

	public static class NET_DVR_STREAM_MEDIA_SERVER_CFG extends Structure {
		public byte byValid; /*     */
		public byte[] byRes1 = new byte[3];
		public NET_DVR_IPADDR struDevIP = new NET_DVR_IPADDR();
		public short wDevPort; /*     */
		public byte byTransmitType; /* 0-TCP,1-UDP */
		public byte[] byRes2 = new byte[69];
	}

	public static class NET_DVR_DEV_CHAN_INFO extends Structure {
		public NET_DVR_IPADDR struIP = new NET_DVR_IPADDR(); // DVR IP
		public short wDVRPort; //
		public byte byChannel; //
		public byte byTransProtocol; // 0-TCP,1-UDP
		public byte byTransMode; // 0- 1-
		public byte byFactoryType; /* , */
		public byte byDeviceType; // ( ),1- ( byVcaSupportChanMode ),2-
		public byte byDispChan;// ,
		public byte bySubDispChan;// ,
		public byte byResolution; // ; 1-CIF 2-4CIF 3-720P 4-1080P 5-500w ,
		public byte[] byRes = new byte[2];
		public byte[] byDomain = new byte[MAX_DOMAIN_NAME]; //
		public byte[] sUserName = new byte[NAME_LEN]; //
		public byte[] sPassword = new byte[PASSWD_LEN]; //
	}

	public static class NET_DVR_PU_STREAM_CFG extends Structure {
		public int dwSize;
		public NET_DVR_STREAM_MEDIA_SERVER_CFG struStreamMediaSvrCfg = new NET_DVR_STREAM_MEDIA_SERVER_CFG();
		public NET_DVR_DEV_CHAN_INFO struDevChanInfo = new NET_DVR_DEV_CHAN_INFO();
	}

	public static class NET_DVR_DDNS_STREAM_CFG extends Structure {
		public byte byEnable; //
		public byte[] byRes1 = new byte[3];
		public NET_DVR_IPADDR struStreamServer = new NET_DVR_IPADDR(); //
		public short wStreamServerPort; //
		public byte byStreamServerTransmitType; // 0-TCP,1-UDP
		public byte byRes2;
		public NET_DVR_IPADDR struIPServer = new NET_DVR_IPADDR(); // IPSERVER
		public short wIPServerPort; // IPserver
		public byte[] byRes3 = new byte[2];
		public byte[] sDVRName = new byte[NAME_LEN]; // DVR
		public short wDVRNameLen; // DVR
		public short wDVRSerialLen; //
		public byte[] sDVRSerialNumber = new byte[SERIALNO_LEN]; // DVR
		public byte[] sUserName = new byte[NAME_LEN]; // DVR
		public byte[] sPassWord = new byte[PASSWD_LEN]; // DVR
		public short wDVRPort; // DVR
		public byte[] byRes4 = new byte[2];
		public byte byChannel; // DVR
		public byte byTransProtocol; // 0-TCP,1-UDP
		public byte byTransMode; // 0- 1-
		public byte byFactoryType; // ,
	}

	public static class NET_DVR_PU_STREAM_URL extends Structure {
		public byte byEnable;
		public byte[] strURL = new byte[240];
		public byte byTransPortocol; // 0-tcp 1-UDP
		public short wIPID; // ID ,wIPID = iDevInfoIndex + iGroupNO*64 +1
		public byte byChannel; //
		public byte[] byRes = new byte[7];
	}

	public static class NET_DVR_HKDDNS_STREAM extends Structure {
		public byte byEnable; //
		public byte[] byRes = new byte[3]; //
		public byte[] byDDNSDomain = new byte[64]; // hiDDNS
		public short wPort; // hiDDNS
		public short wAliasLen; //
		public byte[] byAlias = new byte[NAME_LEN]; //
		public short wDVRSerialLen; //
		public byte[] byRes1 = new byte[2]; //
		public byte[] byDVRSerialNumber = new byte[SERIALNO_LEN]; // DVR
		public byte[] byUserName = new byte[NAME_LEN]; // DVR
		public byte[] byPassWord = new byte[PASSWD_LEN]; // DVR
		public byte byChannel; // DVR
		public byte[] byRes2 = new byte[11]; //
	}

	public static class NET_DVR_IPCHANINFO_V40 extends Structure {
		public byte byEnable; /*      */
		public byte byRes1;
		public short wIPID; // IP ID
		public int dwChannel; //
		public byte byTransProtocol; // 0-TCP,1-UDP
		public byte byTransMode; // 0- 1-
		public byte byFactoryType; /* , */
		public byte[] byRes = new byte[241];
	}

	public static class NET_DVR_GET_STREAM_UNION extends Union {
		public NET_DVR_IPCHANINFO struChanInfo = new NET_DVR_IPCHANINFO(); /* IP */
		public NET_DVR_IPSERVER_STREAM struIPServerStream = new NET_DVR_IPSERVER_STREAM(); // IPServer
		public NET_DVR_PU_STREAM_CFG struPUStream = new NET_DVR_PU_STREAM_CFG(); //
		public NET_DVR_DDNS_STREAM_CFG struDDNSStream = new NET_DVR_DDNS_STREAM_CFG(); // IPServer
		public NET_DVR_PU_STREAM_URL struStreamUrl = new NET_DVR_PU_STREAM_URL(); // url
		public NET_DVR_HKDDNS_STREAM struHkDDNSStream = new NET_DVR_HKDDNS_STREAM(); // hiDDNS
		public NET_DVR_IPCHANINFO_V40 struIPChan = new NET_DVR_IPCHANINFO_V40(); // ( )
	}

	public static class NET_DVR_STREAM_MODE extends Structure {
		public byte byGetStreamType; // GET_STREAM_TYPE,0- ,1- 2- IPServer ip ,3. IPServer ,
		// 4- URL ,5- hkDDNS ,6- ( ), NET_DVR_IPCHANINFO_V40 ,
		// 7- RTSP
		public byte[] byRes = new byte[3]; //
		public NET_DVR_GET_STREAM_UNION uGetStream; //
	}

	public static class NET_DVR_IPPARACFG_V40 extends Structure {
		public int dwSize; /*     */
		public int dwGroupNum; //
		public int dwAChanNum; //
		public int dwDChanNum; //
		public int dwStartDChan; //
		public byte[] byAnalogChanEnable = new byte[MAX_CHANNUM_V30]; /* , 1-64 ,0 1 */
		public NET_DVR_IPDEVINFO_V31[] struIPDevInfo = (NET_DVR_IPDEVINFO_V31[]) new NET_DVR_IPDEVINFO_V31()
				.toArray(MAX_IP_DEVICE_V40); /* IP */
		public NET_DVR_STREAM_MODE[] struStreamMode = (NET_DVR_STREAM_MODE[]) new NET_DVR_STREAM_MODE()
				.toArray(MAX_CHANNUM_V30);
		public byte[] byRes2 = new byte[20]; //
	}

	public static class NET_DVR_IPALARMININFO_V40 extends Structure {
		public int dwIPID; /* IP ID */
		public int dwAlarmIn; /* IP ID */
		public byte[] byRes = new byte[32]; /*   */
	}

	public static class NET_DVR_IPALARMINCFG_V40 extends Structure {
		public int dwSize; //
		public int dwCurIPAlarmInNum; //
		public NET_DVR_IPALARMININFO_V40[] struIPAlarmInInfo = (NET_DVR_IPALARMININFO_V40[]) new NET_DVR_IPALARMININFO_V40()
				.toArray(4096);/* IP */
		public byte[] byRes = new byte[256];
	}

	public static class NET_DVR_IPALARMOUTINFO_V40 extends Structure {
		public int dwIPID; /* IP ID */
		public int dwAlarmOut; /* IP ID */
		public byte[] byRes = new byte[32]; /*   */
	}

	public static class NET_DVR_IPALARMOUTCFG_V40 extends Structure {
		public int dwSize; //
		public int dwCurIPAlarmOutNum;
		public NET_DVR_IPALARMOUTINFO_V40[] struIPAlarmOutInfo = (NET_DVR_IPALARMOUTINFO_V40[]) new NET_DVR_IPALARMOUTINFO_V40()
				.toArray(4096);/* IP */
		public byte[] byRes = new byte[256];
	}

	public static class PASSIVEDECODE_CHANINFO extends Structure {
		public NativeLong lPassiveHandle;
		public NativeLong lRealHandle;
		public NativeLong lUserID;
		public NativeLong lSel;
		public Pointer hFileThread;
		public Pointer hFileHandle;
		public Pointer hExitThread;
		public Pointer hThreadExit;
		String strRecordFilePath;

		public void init() {
			lPassiveHandle = new NativeLong(-1);
			lRealHandle = new NativeLong(-1);
			lUserID = new NativeLong(-1);
			lSel = new NativeLong(-1);
			hFileThread = null;
			hFileHandle = null;
			hExitThread = null;
			hThreadExit = null;
			strRecordFilePath = null;
		}
	}

	// NET_DVR_Login()
	public static class NET_DVR_DEVICEINFO extends Structure {
		public byte[] sSerialNumber = new byte[SERIALNO_LEN]; //
		public byte byAlarmInPortNum; // DVR
		public byte byAlarmOutPortNum; // DVR
		public byte byDiskNum; // DVR
		public byte byDVRType; // DVR , 1:DVR 2:ATM DVR 3:DVS ......
		public byte byChanNum; // DVR
		public byte byStartChan; // , DVS-1,DVR - 1
	}

	// NET_DVR_Login_V30()
	public static class NET_DVR_DEVICEINFO_V30 extends Structure {
		public byte[] sSerialNumber = new byte[SERIALNO_LEN]; //
		public byte byAlarmInPortNum; //
		public byte byAlarmOutPortNum; //
		public byte byDiskNum; //
		public byte byDVRType; // , 1:DVR 2:ATM DVR 3:DVS ......
		public byte byChanNum; //
		public byte byStartChan; // , DVS-1,DVR - 1
		public byte byAudioChanNum; //
		public byte byIPChanNum; //
		public byte[] byRes1 = new byte[24]; //
	}

	// NET_DVR_Login_V40()
	// public static class NET_DVR_USER_LOGIN_INFO extends Structure
	// {
	// public byte[] sDeviceAddress = new byte[NET_DVR_DEV_ADDRESS_MAX_LEN];
	// public byte byRes1;
	// public short wPort;
	// public byte[] sUserName = new byte[NET_DVR_LOGIN_USERNAME_MAX_LEN];
	// public byte[] sPassword = new byte[NET_DVR_LOGIN_PASSWD_MAX_LEN];
	// public FLoginResultCallBack cbLoginResult;
	// public Pointer pUser;
	// public boolean bUseAsynLogin;
	// public byte[] byRes2 = new byte[128];
	//
	// }

	public static class NET_DVR_USER_LOGIN_INFO extends Structure {
		public byte[] sDeviceAddress = new byte[NET_DVR_DEV_ADDRESS_MAX_LEN];
		public byte byUseTransport;
		public short wPort;
		// public fLoginResultCallBack cbLoginResult;
		public byte[] sUserName = new byte[NET_DVR_LOGIN_USERNAME_MAX_LEN];
		public byte[] sPassword = new byte[NET_DVR_LOGIN_PASSWD_MAX_LEN];
		public FLoginResultCallBack cbLoginResult;
		Pointer pUser;
		public int bUseAsynLogin;
		public byte[] byRes2 = new byte[128];
	}

	// NET_DVR_Login_V40()
	public static class NET_DVR_DEVICEINFO_V40 extends Structure {
		public NET_DVR_DEVICEINFO_V30 struDeviceV30 = new NET_DVR_DEVICEINFO_V30();
		public byte bySupportLock;
		public byte byRetryLoginTime;
		public byte byPasswordLevel;
		public byte byRes1;
		public int dwSurplusLockTime;
		public byte[] byRes2 = new byte[256];
	}

	// sdk ,
	enum _SDK_NET_ENV {
		LOCAL_AREA_NETWORK, WIDE_AREA_NETWORK
	}

	//
	enum ENUM_UPGRADE_TYPE {
		ENUM_UPGRADE_DVR, //
		ENUM_UPGRADE_ADAPTER, // DVR
		ENUM_UPGRADE_VCALIB, //
		ENUM_UPGRADE_OPTICAL, //
		ENUM_UPGRADE_ACS, //
		ENUM_UPGRADE_AUXILIARY_DEV, //
		ENUM_UPGRADE_LED // LED
	};

	//
	enum DISPLAY_MODE {
		NORMALMODE, OVERLAYMODE
	}

	//
	enum SEND_MODE {
		PTOPTCPMODE, PTOPUDPMODE, MULTIMODE, RTPMODE, RESERVEDMODE
	};

	//
	enum CAPTURE_MODE {
		BMP_MODE, // BMP
		JPEG_MODE // JPEG
	};

	//
	enum REALSOUND_MODE {
		NONE, // SDK , 0
		MONOPOLIZE_MODE, // 1
		SHARE_MODE // 2
	};

	//
	public static class NET_DVR_CLIENTINFO extends Structure {
		public NativeLong lChannel;
		public NativeLong lLinkMode;
		public HWND hPlayWnd;
		public String sMultiCastIP;
	}

	public static class NET_DVR_PREVIEWINFO extends Structure {
		public NativeLong lChannel;
		public int dwStreamType;
		public int dwLinkMode;
		public HWND hPlayWnd;
		public boolean bBlocked;
		public boolean bPassbackRecord;
		public byte byPreviewMode;
		public byte[] byStreamID = new byte[STREAM_ID_LEN];
		public byte byProtoType;
		public byte[] byRes1 = new byte[2];
		public int dwDisplayBufNum;
		public byte[] byRes = new byte[216];
	}

	// SDK (9000 )
	public static class NET_DVR_SDKSTATE extends Structure {
		public int dwTotalLoginNum; // login
		public int dwTotalRealPlayNum; // realplay
		public int dwTotalPlayBackNum; //
		public int dwTotalAlarmChanNum; //
		public int dwTotalFormatNum; //
		public int dwTotalFileSearchNum; //
		public int dwTotalLogSearchNum; //
		public int dwTotalSerialNum; //
		public int dwTotalUpgradeNum; //
		public int dwTotalVoiceComNum; //
		public int dwTotalBroadCastNum; //
		public int[] dwRes = new int[10];
	}

	// SDK (9000 )
	public static class NET_DVR_SDKABL extends Structure {
		public int dwMaxLoginNum; // login MAX_LOGIN_USERS
		public int dwMaxRealPlayNum; // realplay WATCH_NUM
		public int dwMaxPlayBackNum; // WATCH_NUM
		public int dwMaxAlarmChanNum; // ALARM_NUM
		public int dwMaxFormatNum; // SERVER_NUM
		public int dwMaxFileSearchNum; // SERVER_NUM
		public int dwMaxLogSearchNum; // SERVER_NUM
		public int dwMaxSerialNum; // SERVER_NUM
		public int dwMaxUpgradeNum; // SERVER_NUM
		public int dwMaxVoiceComNum; // SERVER_NUM
		public int dwMaxBroadCastNum; // MAX_CASTNUM
		public int[] dwRes = new int[10];
	}

	//
	public static class NET_DVR_ALARMER extends Structure {
		public byte byUserIDValid; /* userid 0- ,1- */
		public byte bySerialValid; /* 0- ,1- */
		public byte byVersionValid; /* 0- ,1- */
		public byte byDeviceNameValid; /* 0- ,1- */
		public byte byMacAddrValid; /* MAC 0- ,1- */
		public byte byLinkPortValid; /* login 0- ,1- */
		public byte byDeviceIPValid; /* IP 0- ,1- */
		public byte bySocketIPValid; /* socket ip 0- ,1- */
		public NativeLong lUserID; /* NET_DVR_Login() , */
		public byte[] sSerialNumber = new byte[SERIALNO_LEN]; /*   */
		public int dwDeviceVersion; /* 16 , 16 */
		public byte[] sDeviceName = new byte[NAME_LEN]; /*     */
		public byte[] byMacAddr = new byte[MACADDR_LEN]; /* MAC */
		public short wLinkPort; /* link port */
		public byte[] sDeviceIP = new byte[128]; /* IP */
		public byte[] sSocketIP = new byte[128]; /* socket IP */
		public byte byIpProtocol; /* Ip 0-IPV4, 1-IPV6 */
		public byte[] byRes2 = new byte[11];
	}

	// ( )
	public static class NET_DVR_DISPLAY_PARA extends Structure {
		public NativeLong bToScreen;
		public NativeLong bToVideoOut;
		public NativeLong nLeft;
		public NativeLong nTop;
		public NativeLong nWidth;
		public NativeLong nHeight;
		public NativeLong nReserved;
	}

	//
	public static class NET_DVR_CARDINFO extends Structure {
		public NativeLong lChannel;//
		public NativeLong lLinkMode; // (31) 0 , 1 ,0-30 :0:TCP ,1:UDP ,2: ,3 -
										// RTP ,4- ,5-128k ,6-256k ,7-384k ,8-512k ;
		public String sMultiCastIP;
		public NET_DVR_DISPLAY_PARA struDisplayPara = new NET_DVR_DISPLAY_PARA();
	}

	//
	public static class NET_DVR_FIND_DATA extends Structure {
		public byte[] sFileName = new byte[100];//
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();//
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();//
		public int dwFileSize;//
	}

	// (9000)
	public static class NET_DVR_FINDDATA_V30 extends Structure {
		public byte[] sFileName = new byte[100];//
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();//
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();//
		public int dwFileSize;//
		public byte[] sCardNum = new byte[32];
		public byte byLocked;// 9000 ,1 ,0
		public byte[] byRes = new byte[3];
	}

	// ( )
	public static class NET_DVR_FINDDATA_CARD extends Structure {
		public byte[] sFileName = new byte[100];//
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();//
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();//
		public int dwFileSize;//
		public byte[] sCardNum = new byte[32];
	}

	public static class NET_DVR_STREAM_INFO extends Structure {
		public int dwSize;
		public byte[] byID = new byte[STREAM_ID_LEN];
		public int dwChannel;
		public byte[] byRes = new byte[32];
	}

	public static class NET_DVR_VOD_PARA extends Structure {
		public int dwSize;
		public NET_DVR_STREAM_INFO struIDInfo = new NET_DVR_STREAM_INFO();
		public NET_DVR_TIME struBeginTime = new NET_DVR_TIME();
		public NET_DVR_TIME struEndTime = new NET_DVR_TIME();
		public HWND hWnd;
		public byte byDrawFrame;
		public byte byVolumeType;
		public byte byVolumeNum;
		public byte byStreamType;
		public int dwFileIndex;
		public byte byAudioFile;
		public byte[] byRes2 = new byte[23];
	}

	public static class NET_DVR_FILECOND extends Structure //
	{
		public NativeLong lChannel;//
		public int dwFileType;// 0xff- ,0- ,1- ,2- ,3- | 4- & 5- 6-
		public int dwIsLocked;// 0- ,1- , 0xff
		public int dwUseCardNo;//
		public byte[] sCardNumber = new byte[32];//
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();//
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();//
	}

	public static class NET_DVR_FILECOND_V40 extends Structure {
		public NativeLong lChannel;
		public int dwFileType;
		public int dwIsLocked;
		public int dwUseCardNo;
		public byte[] sCardNumber = new byte[CARDNUM_LEN_OUT];
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();
		public byte byDrawFrame;
		public byte byFindType;
		public byte byQuickSearch;
		public byte bySpecialFindInfoType;
		public int dwVolumeNum;
		public byte[] byWorkingDeviceGUID = new byte[GUID_LEN];
		public NET_DVR_SPECIAL_FINDINFO_UNION uSpecialFindInfo = new NET_DVR_SPECIAL_FINDINFO_UNION();
		public byte byStreamType;
		public byte byAudioFile;
		public byte[] byRes2 = new byte[30];

	}

	public static class NET_DVR_SPECIAL_FINDINFO_UNION extends Structure {
		public byte[] byLength = new byte[8];
		public NET_DVR_ATMEINDINFO struATMFindInfo = new NET_DVR_ATMEINDINFO();
	}

	public static class NET_DVR_ATMEINDINFO extends Structure {
		public byte byTransactionType;
		public byte[] byRes = new byte[3];
		public int dwTransationAmount;

	}

	public static class NET_DVR_FINDDATA_V40 extends Structure {
		public byte[] sFileName = new byte[100];
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();
		public int dwFileSize;
		public byte[] sCardNum = new byte[32];
		public byte byLocked;
		public byte[] byRes1 = new byte[127];

		public byte byFileType;
		public byte byQuickSearch;
		public byte byRes;
		public int dwFileIndex;
		public byte byStreamType;

	}

	public static class NET_DVR_PLAYCOND extends Structure {
		public int dwChannel;
		public NET_DVR_TIME struStartTime = new NET_DVR_TIME();
		public NET_DVR_TIME struStopTime = new NET_DVR_TIME();
		public byte byDrawFrame;
		public byte byStreamType;
		public byte[] byStreamID = new byte[STREAM_ID_LEN];
		public byte[] byRes = new byte[30];

	}

	// (HIK )
	public static class NET_DVR_POINT_FRAME extends Structure {
		public int xTop; // x
		public int yTop; // y
		public int xBottom; // x
		public int yBottom; // y
		public int bCounter; //
	}

	//
	public static class NET_DVR_COMPRESSION_AUDIO extends Structure {
		public byte byAudioEncType; // 0-G722; 1-G711
		public byte[] byres = new byte[7];//
	}

	//
	// public static class RECV_ALARM extends Structure{
	public class RECV_ALARM extends Structure {
		// public byte[] RecvBuffer = new byte[400];// 400
		public byte[] RecvBuffer = new byte[800];// 400
	}

	//
	public static class NET_DVR_PTZ_PATTERN extends Structure {
		public int dwSize;
		public int dwChannel;
		public int dwPatternCmd;
		public int dwPatternID;
		public byte[] byRes = new byte[64];
	}

	public static class NET_DVR_FUZZY_UPGRADE extends Structure {
		public int dwSize;
		public byte[] sUpgradeInfo = new byte[48];
		public byte[] byRes = new byte[64];
	}

	public static class NET_DVR_SERIALSTART_V40 extends Structure {
		public int dwSize;
		public int dwSerialType;
		public byte bySerialNum;
		public byte[] byRes = new byte[255];
	}

	public static class NET_DVR_AUXILIARY_DEV_UPGRADE_PARAM extends Structure {
		public int dwSize;
		public int dwDevNo;
		public byte byDevType;
		public byte[] byRes = new byte[131];

	}

	//

	public static class struStatFrame extends Structure {
		public int dwRelativeTime;
		public int dwAbsTime;
		public byte[] byRes = new byte[92];
	}

	public static class struStartTime extends Structure {
		public NET_DVR_TIME tmStart = new NET_DVR_TIME();
		public NET_DVR_TIME tmEnd = new NET_DVR_TIME();
		public byte[] byRes = new byte[92];
	}

	public static class uStatModeParam extends Union {
		public struStatFrame strustatFrame = new struStatFrame();
		public struStartTime strustartTime = new struStartTime();
	}

	public static class NET_VCA_DEV_INFO extends Structure implements Serializable {
		public NET_DVR_IPADDR struDevIP = new NET_DVR_IPADDR();
		public short wPort;
		public byte byChannel;
		public byte byIvmsChannel;
	}

	public static class NET_DVR_PDC_ALRAM_INFO extends Structure {
		public int dwSize;
		public byte byMode;
		public byte byChannel;
		public byte bySmart;
		public byte byRes1;
		public NET_VCA_DEV_INFO struDevInfo = new NET_VCA_DEV_INFO();
		public uStatModeParam ustateModeParam = new uStatModeParam();
		public int dwLeaveNum;
		public int dwEnterNum;
		public byte[] byRes2 = new byte[40];
	}

	//

	public static class NET_DVR_PLATE_INFO extends Structure {
		public byte byPlateType;
		public byte byColor;
		public byte byBright;
		public byte byLicenseLen;
		public byte byEntireBelieve;
		public byte byRegion;
		public byte byCountry;
		public byte[] byRes = new byte[33];
		public NET_VCA_RECT struPlateRect = new NET_VCA_RECT();
		public String sLicense;
		public byte[] byBelieve = new byte[MAX_LICENSE_LEN];

	}

	public static class NET_DVR_VEHICLE_INFO extends Structure {
		public int dwIndex;
		public byte byVehicleType;
		public byte byColorDepth;
		public byte byColor;
		public byte byRes1;
		public short wSpeed;
		public short wLength;
		public byte byIllegalType;
		public byte byVehicleLogoRecog;
		public byte byVehicleSubLogoRecog;
		public byte byVehicleModel;
		public byte[] byCustomInfo = new byte[16];
		public short wVehicleLogoRecog;
		public byte[] byRes3 = new byte[14];
	}

	public static class NET_DVR_PLATE_RESULT extends Structure {
		public int dwSize;
		public byte byResultType;
		public byte byChanIndex;
		public short wAlarmRecordID;
		public int dwRelativeTime;
		public byte[] byAbsTime = new byte[32];
		public int dwPicLen;
		public int dwPicPlateLen;
		public int dwVideoLen;
		public byte byTrafficLight;
		public byte byPicNum;
		public byte byDriveChan;
		public byte byVehicleType;
		public int dwBinPicLen;
		public int dwCarPicLen;
		public int dwFarCarPicLen;
		public ByteByReference pBuffer3;
		public ByteByReference pBuffer4;
		public ByteByReference pBuffer5;
		public byte byRelaLaneDirectionType;
		public byte[] byRes3 = new byte[7];
		public NET_DVR_PLATE_INFO struPlateInfo = new NET_DVR_PLATE_INFO();
		public NET_DVR_VEHICLE_INFO struVehicleInfo = new NET_DVR_VEHICLE_INFO();
		public ByteByReference pBuffer1;
		public ByteByReference pBuffer2;
	}

	//

	public static class NET_ITC_PLATE_RECOG_PARAM extends Structure {
		public byte[] byDefaultCHN = new byte[MAX_CHJC_NUM];
		public byte byEnable;
		public int dwRecogMode;
		public byte byVehicleLogoRecog;
		public byte byProvince;
		public byte byRegion;
		public byte byRes1;
		public short wPlatePixelWidthMin;
		public short wPlatePixelWidthMax;
		public byte[] byRes = new byte[24];
	}

	public static class NET_VCA_RECT extends Structure implements Serializable {
		public float fX;
		public float fY;
		public float fWidth;
		public float fHeight;
	}

	public static class NET_VCA_POINT extends Structure implements Serializable {
		public float fX;
		public float fY;
	}

	public static class NET_ITC_POLYGON extends Structure implements Serializable {
		public int dwPointNum;
		public NET_VCA_POINT[] struPos = new NET_VCA_POINT[ITC_MAX_POLYGON_POINT_NUM];

		public NET_ITC_POLYGON() {
			for (int i = 0; i < ITC_MAX_POLYGON_POINT_NUM; i++) {
				struPos[i] = new NET_VCA_POINT();
			}
		}
	}

	public static class B extends Structure {
		public int b1;
		public int b2;
		public int b3;
	}

	public static class A extends Union {
		public int[] arr = new int[2];
		public B b;

		public A() {
			arr[0] = 1;
			arr[1] = 1;
		}
	}

	public static class uRegion extends Union {
		public NET_VCA_RECT struRect = new NET_VCA_RECT();
		public NET_ITC_POLYGON struPolygon = new NET_ITC_POLYGON();
	}

	public static class NET_ITC_PLATE_RECOG_REGION_PARAM extends Structure {
		public byte byMode;
		public byte[] byRes1 = new byte[3];
		public uRegion uregion = new uRegion();
		public byte[] byRes = new byte[16];
	}

	public static class NET_ITC_SINGLE_IOSPEED_PARAM extends Structure {
		public byte byEnable;
		public byte byTrigCoil1;
		public byte byCoil1IOStatus;
		public byte byTrigCoil2;
		public byte byCoil2IOStatus;
		public byte byRelatedDriveWay;
		public byte byTimeOut;
		public byte byRelatedIOOutEx;
		public int dwDistance;
		public byte byCapSpeed;
		public byte bySpeedLimit;
		public byte bySpeedCapEn;
		public byte bySnapTimes1;
		public byte bySnapTimes2;
		public byte byBigCarSpeedLimit;
		public byte byBigCarSignSpeed;
		public byte byIntervalType;
		public short[] wInterval1 = new short[MAX_INTERVAL_NUM];
		public short[] wInterval2 = new short[MAX_INTERVAL_NUM];
		public byte[] byRelatedIOOut = new byte[MAX_IOOUT_NUM];
		public byte byFlashMode;
		public byte byLaneType;
		public byte byCarSignSpeed;
		public byte byUseageType;
		public NET_ITC_PLATE_RECOG_REGION_PARAM[] struPlateRecog = new NET_ITC_PLATE_RECOG_REGION_PARAM[MAX_LANEAREA_NUM];
		public byte byRelaLaneDirectionType;
		public byte byLowSpeedLimit;
		public byte byBigCarLowSpeedLimit;
		public byte byLowSpeedCapEn;
		public byte[] byRes = new byte[28];

		public NET_ITC_SINGLE_IOSPEED_PARAM() {
			for (int i = 0; i < MAX_LANEAREA_NUM; i++) {
				struPlateRecog[i] = new NET_ITC_PLATE_RECOG_REGION_PARAM();
			}
		}
	}

	public static class NET_ITC_POST_IOSPEED_PARAM extends Structure {
		public NET_ITC_PLATE_RECOG_PARAM struPlateRecog = new NET_ITC_PLATE_RECOG_PARAM();
		public NET_ITC_SINGLE_IOSPEED_PARAM[] struSingleIOSpeed = new NET_ITC_SINGLE_IOSPEED_PARAM[MAX_IOSPEED_GROUP_NUM];
		public byte[] byRes = new byte[32];

		public NET_ITC_POST_IOSPEED_PARAM() {
			for (int i = 0; i < MAX_IOSPEED_GROUP_NUM; i++) {
				struSingleIOSpeed[i] = new NET_ITC_SINGLE_IOSPEED_PARAM();
			}
		}
	}

	public static class NET_ITC_TRIGGER_PARAM_UNION extends Union {
		public NET_ITC_POST_IOSPEED_PARAM struIOSpeed = new NET_ITC_POST_IOSPEED_PARAM();
		public int[] uLen = new int[1070];
	}

	public static class NET_ITC_SINGLE_TRIGGERCFG extends Structure {
		public byte byEnable;
		public byte[] byRes1 = new byte[3];
		public int dwTriggerType;
		public NET_ITC_TRIGGER_PARAM_UNION uTriggerParam = new NET_ITC_TRIGGER_PARAM_UNION();
		public byte[] byRes = new byte[64];
	}

	public static class NET_ITC_TRIGGERCFG extends Structure {
		public int dwSize;
		public NET_ITC_SINGLE_TRIGGERCFG struTriggerParam = new NET_ITC_SINGLE_TRIGGERCFG();
		public byte[] byRes = new byte[32];
	}

	public static class NET_DVR_AUDIO_INPUT_PARAM extends Structure {
		public byte byAudioInputType;
		public byte byVolume;
		public byte byEnableNoiseFilter;
		public byte[] byres = new byte[5];
	}

	public static class NET_DVR_MULTI_STREAM_COMPRESSIONCFG_COND extends Structure {
		public int dwSize;
		public NET_DVR_STREAM_INFO struStreamInfo = new NET_DVR_STREAM_INFO();
		public int dwStreamType;
		public byte[] byRes = new byte[32];
	}

	public static class NET_DVR_MULTI_STREAM_COMPRESSIONCFG extends Structure {
		public int dwSize;
		public int dwStreamType;
		public NET_DVR_COMPRESSION_INFO_V30 struStreamPara = new NET_DVR_COMPRESSION_INFO_V30();
		public byte[] byRes = new byte[80];
	}

	//
	public static class NET_DVR_DAYTIME extends Structure {
		public byte byHour; // , :0~24
		public byte byMinute; // , :0~60
		public byte bySecond; // , :0~60
		public byte byRes; // , 0
		public short wMilliSecond; // , :0~1000
		public byte[] byRes1 = new byte[2]; // , 0
	}

	//
	public static class NET_DVR_SCHEDULE_DAYTIME extends Structure {
		public NET_DVR_DAYTIME struStartTime = new NET_DVR_DAYTIME(); //
		public NET_DVR_DAYTIME struStopTime = new NET_DVR_DAYTIME(); //
	}

	//
	public static class NET_DVR_VIDEOEFFECT extends Structure {
		public byte byBrightnessLevel; // , [0,100]
		public byte byContrastLevel; // , [0,100]
		public byte bySharpnessLevel; // , [0,100]
		public byte bySaturationLevel; // , [0,100]
		public byte byHueLevel; // , [0,100],
		public byte byEnableFunc; // , bit0-SMART
		public byte byLightInhibitLevel; // , :[1,3]
		public byte byGrayLevel; // :0-[0,255],1-[16,235]
	}

	//
	public static class NET_DVR_GAIN extends Structure {
		public byte byGainLevel; // , dB, [0,100]
		public byte byGainUserSet; // , dB, [0,100], , CCD
		public byte[] byRes = new byte[2]; // , 0
		public int dwMaxGainValue; // , dB
	}

	//
	public static class NET_DVR_WHITEBALANCE extends Structure {
		public byte byWhiteBalanceMode; // 0- (MWB),1- 1(AWB1, ),2- 2(AWB2, ,2200K-15000K),3-
										// (Locked WB),4- ,5- ,6- ,7- ,8- (Auto-Track),9- (One
										// Push),10- (Auto-Outdoor),11- (Auto-Sodiumlight),12- (Mercury
										// Lamp),13- (Auto),14- (IncandescentLamp),15- (Warm Light
										// Lamp),16- (Natural Light)
		public byte byWhiteBalanceModeRGain; // , R
		public byte byWhiteBalanceModeBGain; // , B
		public byte[] byRes = new byte[5]; //
	}

	// CCD
	public static class NET_DVR_EXPOSURE extends Structure {
		public byte byExposureMode; // 0- ,1-
		public byte byAutoApertureLevel; // , :0~10
		public byte[] byRes = new byte[2]; //
		public int dwVideoExposureSet; // ( us),
		public int dwExposureUserSet; // CCD , ,( us)
		public int dwRes; //
	}

	// Gamma
	public static class NET_DVR_GAMMACORRECT extends Structure {
		public byte byGammaCorrectionEnabled; // Gamma ,0- ,1-
		public byte byGammaCorrectionLevel; // 0-100
		public byte[] byRes = new byte[6]; // , 0
	}

	//
	public static class NET_DVR_WDR extends Structure {
		public byte byWDREnabled; // ,0- ,1- ,2-
		public byte byWDRLevel1; // 0-F
		public byte byWDRLevel2; // 0-F
		public byte byWDRContrastLevel; // 0-100
		public byte[] byRes = new byte[16]; //
	}

	//
	public static class NET_DVR_DAYNIGHT extends Structure {
		public byte byDayNightFilterType; // :0- ,1- ,2- ,3- ,4-
		public byte bySwitchScheduleEnabled; // 0- , 1- ( )
		public byte byBeginTime; // ( ), :0~23
		public byte byEndTime; // ( ), :0~23
		public byte byDayToNightFilterLevel; // :0~7, :1~3
		public byte byNightToDayFilterLevel; // :0~7, :1~3
		public byte byDayNightFilterTime; // 60
		public byte byBeginTimeMin; // ( ), :0~59
		public byte byBeginTimeSec; // ( ), :0~59
		public byte byEndTimeMin; // ( ), :0~59
		public byte byEndTimeSec; // ( ), :0~59
		public byte byAlarmTrigState; // :0- ,1-
	}

	//
	public static class NET_DVR_BACKLIGHT extends Structure {
		public byte byBacklightMode; // :0-off,1-UP,2-DOWN,3-LEFT,4-RIGHT,5-MIDDLE,6- ,10- ,11- ,12-
		public byte byBacklightLevel; // :0x0~0xF
		public byte[] byRes1 = new byte[2]; //
		public int dwPositionX1; // X 1
		public int dwPositionY1; // Y 1
		public int dwPositionX2; // X 2
		public int dwPositionY2; // Y 2
		public byte[] byRes2 = new byte[4]; //
	}

	//
	public static class NET_DVR_NOISEREMOVE extends Structure {
		public byte byDigitalNoiseRemoveEnable; // ,0- ,1- ,2-
		public byte byDigitalNoiseRemoveLevel; // :0x0~0xF
		public byte bySpectralLevel; // :0~100
		public byte byTemporalLevel; // :0~100
		public byte byDigitalNoiseRemove2DEnable; // 2D :0- ,1- ,
		public byte byDigitalNoiseRemove2DLevel; // 2D , :0~100,
		public byte[] byRes = new byte[2]; // , 0
	}

	// CMOS
	public static class NET_DVR_CMOSMODECFG extends Structure {
		public byte byCaptureMod; // :0- 1;1- 2
		public byte byBrightnessGate; //
		public byte byCaptureGain1; // 1,0-100
		public byte byCaptureGain2; // 2,0-100
		public int dwCaptureShutterSpeed1; // 1
		public int dwCaptureShutterSpeed2; // 2
		public byte[] byRes = new byte[4]; //
	}

	//
	public static class NET_DVR_DEFOGCFG extends Structure {
		public byte byMode; // :0- ,1- ,2-
		public byte byLevel; // , :0~100
		public byte[] byRes = new byte[6]; //
	}

	//
	public static class NET_DVR_ELECTRONICSTABILIZATION extends Structure {
		public byte byEnable; // :0- ,1-
		public byte byLevel; // , :0~100
		public byte[] byRes = new byte[6]; //
	}

	//
	public static class NET_DVR_CORRIDOR_MODE_CCD extends Structure {
		public byte byEnableCorridorMode; // :0- ,1-
		public byte[] byRes = new byte[11]; //
	}

	// SMART IR( )
	public static class NET_DVR_SMARTIR_PARAM extends Structure {
		public byte byMode; // SMART IR :0- ,1-
		public byte byIRDistance; // ( , ):1~100, :50,
		public byte byShortIRDistance; // , :1~100
		public byte byLongIRDistance; // , :1~100
	}

	// P-Iris
	public static class NET_DVR_PIRIS_PARAM extends Structure {
		public byte byMode; // P-Iris :0- ,1-
		public byte byPIrisAperture; // ( , ):1~100, :50,
		public byte[] byRes = new byte[6]; // , 0
	}

	//
	public static class NET_DVR_LASER_PARAM_CFG extends Structure {
		public byte byControlMode; // :0- ,1- ,2- , :
		public byte bySensitivity; // , :0~100, :50
		public byte byTriggerMode; // :0- ,1- ,2- , :
		public byte byBrightness; // , , :0~255, :100
		public byte byAngle; // ,0 , :1~36, :12 ,
		public byte byLimitBrightness; // , , :0~100
		public byte[] byRes = new byte[10]; //
	}

	// FFC
	public static class NET_DVR_FFC_PARAM extends Structure {
		public byte byMode; // 1- ,2- ,3-
		public byte byRes1; // , 0
		public short wCompensateTime; // ( ), : , , :10 20 30 40 50 60 120 180 240
		public byte[] byRes2 = new byte[4]; // , 0
	}

	// DDE
	public static class NET_DVR_DDE_PARAM extends Structure {
		public byte byMode; // 1- ,2- ,3-
		public byte byNormalLevel; // , :[1,100],
		public byte byExpertLevel; // , :[1,100],
		public byte[] byRes = new byte[5]; // , 0
	}

	// AGC
	public static class NET_DVR_AGC_PARAM extends Structure {
		public byte bySceneType; // 1- ,2- ,3-
		public byte byLightLevel; // , :[1,100],
		public byte byGainLevel; // , :[1,100],
		public byte[] byRes = new byte[5]; // , 0
	}

	// CCD
	public static class NET_DVR_SNAP_CAMERAPARAMCFG extends Structure {
		public byte byWDRMode; // :0- ,1- ,2-
		public byte byWDRType; // :0- ,1- ,2-
		public byte byWDRLevel; // , 0~6 1~7, 2( 3 )
		public byte byRes1; //
		public NET_DVR_TIME_EX struStartTime = new NET_DVR_TIME_EX(); //
		public NET_DVR_TIME_EX struEndTime = new NET_DVR_TIME_EX(); //
		public byte byDayNightBrightness; // , :0~100, :50
		public byte[] byRes = new byte[43]; //
	}

	//
	public static class NET_DVR_CAMERAPARAMCFG_EX extends Structure {
		public int dwSize;
		public NET_DVR_VIDEOEFFECT struVideoEffect = new NET_DVR_VIDEOEFFECT();
		public NET_DVR_GAIN struGain = new NET_DVR_GAIN();
		public NET_DVR_WHITEBALANCE struWhiteBalance = new NET_DVR_WHITEBALANCE();
		public NET_DVR_EXPOSURE struExposure = new NET_DVR_EXPOSURE();
		public NET_DVR_GAMMACORRECT struGammaCorrect = new NET_DVR_GAMMACORRECT();
		public NET_DVR_WDR struWdr = new NET_DVR_WDR();
		public NET_DVR_DAYNIGHT struDayNight = new NET_DVR_DAYNIGHT();
		public NET_DVR_BACKLIGHT struBackLight = new NET_DVR_BACKLIGHT();
		public NET_DVR_NOISEREMOVE struNoiseRemove = new NET_DVR_NOISEREMOVE();
		public byte byPowerLineFrequencyMode;
		public byte byIrisMode;
		public byte byMirror;
		public byte byDigitalZoom;
		public byte byDeadPixelDetect;
		public byte byBlackPwl;
		public byte byEptzGate;
		public byte byLocalOutputGate;
		public byte byCoderOutputMode;
		public byte byLineCoding;
		public byte byDimmerMode;
		public byte byPaletteMode;
		public byte byEnhancedMode;
		public byte byDynamicContrastEN;
		public byte byDynamicContrast;
		public byte byJPEGQuality;
		public NET_DVR_CMOSMODECFG struCmosModeCfg = new NET_DVR_CMOSMODECFG();
		public byte byFilterSwitch;
		public byte byFocusSpeed;
		public byte byAutoCompensationInterval;
		public byte bySceneMode;
		public NET_DVR_DEFOGCFG struDefogCfg = new NET_DVR_DEFOGCFG();
		public NET_DVR_ELECTRONICSTABILIZATION struElectronicStabilization = new NET_DVR_ELECTRONICSTABILIZATION();
		public NET_DVR_CORRIDOR_MODE_CCD struCorridorMode = new NET_DVR_CORRIDOR_MODE_CCD();
		public byte byExposureSegmentEnable;
		public byte byBrightCompensate;
		public byte byCaptureModeN;
		public byte byCaptureModeP;
		public NET_DVR_SMARTIR_PARAM struSmartIRParam = new NET_DVR_SMARTIR_PARAM();
		public NET_DVR_PIRIS_PARAM struPIrisParam = new NET_DVR_PIRIS_PARAM();
		public NET_DVR_LASER_PARAM_CFG struLaserParam = new NET_DVR_LASER_PARAM_CFG();
		public NET_DVR_FFC_PARAM struFFCParam = new NET_DVR_FFC_PARAM();
		public NET_DVR_DDE_PARAM struDDEParam = new NET_DVR_DDE_PARAM();
		public NET_DVR_AGC_PARAM struAGCParam = new NET_DVR_AGC_PARAM();
		public byte byLensDistortionCorrection;
		public byte[] byRes1 = new byte[3];
		public NET_DVR_SNAP_CAMERAPARAMCFG struSnapCCD = new NET_DVR_SNAP_CAMERAPARAMCFG();
		public byte[] byRes2 = new byte[188];
	}

	// ISP
	public static class NET_DVR_ISP_CAMERAPARAMCFG extends Structure {
		public int dwSize;
		public byte byWorkType;
		public byte[] byRes = new byte[3];
		public NET_DVR_SCHEDULE_DAYTIME struDayNightScheduleTime = new NET_DVR_SCHEDULE_DAYTIME();
		public NET_DVR_CAMERAPARAMCFG_EX struSelfAdaptiveParam = new NET_DVR_CAMERAPARAMCFG_EX();
		public NET_DVR_CAMERAPARAMCFG_EX struDayIspAdvanceParam = new NET_DVR_CAMERAPARAMCFG_EX();
		public NET_DVR_CAMERAPARAMCFG_EX struNightIspAdvanceParam = new NET_DVR_CAMERAPARAMCFG_EX();
		public byte[] byRes1 = new byte[512];
	}

	/*** API , API ***/
	public static interface FRealDataCallBack_V30 extends Callback {
		public void invoke(NativeLong lRealHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize,
				Pointer pUser);
	}

	public static interface FMSGCallBack extends Callback {
		public void invoke(NativeLong lCommand, NET_DVR_ALARMER pAlarmer, HCNetSDK.RECV_ALARM pAlarmInfo, int dwBufLen,
				Pointer pUser);
	}

	public static interface FMessCallBack extends Callback {
		public boolean invoke(NativeLong lCommand, String sDVRIP, String pBuf, int dwBufLen);
	}

	public static interface FMessCallBack_EX extends Callback {
		public boolean invoke(NativeLong lCommand, NativeLong lUserID, String pBuf, int dwBufLen);
	}

	public static interface FMessCallBack_NEW extends Callback {
		public boolean invoke(NativeLong lCommand, String sDVRIP, String pBuf, int dwBufLen, short dwLinkDVRPort);
	}

	public static interface FMessageCallBack extends Callback {
		public boolean invoke(NativeLong lCommand, String sDVRIP, String pBuf, int dwBufLen, int dwUser);
	}

	public static interface FExceptionCallBack extends Callback {
		public void invoke(int dwType, NativeLong lUserID, NativeLong lHandle, Pointer pUser);
	}

	public static interface FDrawFun extends Callback {
		public void invoke(NativeLong lRealHandle, HDC hDc, int dwUser);
	}

	public static interface FStdDataCallBack extends Callback {
		public void invoke(NativeLong lRealHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize, int dwUser);
	}

	public static interface FPlayDataCallBack extends Callback {
		public void invoke(NativeLong lPlayHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize, int dwUser);
	}

	public static interface FVoiceDataCallBack extends Callback {
		public void invoke(NativeLong lVoiceComHandle, String pRecvDataBuffer, int dwBufSize, byte byAudioFlag,
				int dwUser);
	}

	public static interface FVoiceDataCallBack_V30 extends Callback {
		public void invoke(NativeLong lVoiceComHandle, String pRecvDataBuffer, int dwBufSize, byte byAudioFlag,
				Pointer pUser);
	}

	public static interface FVoiceDataCallBack_MR extends Callback {
		public void invoke(NativeLong lVoiceComHandle, String pRecvDataBuffer, int dwBufSize, byte byAudioFlag,
				int dwUser);
	}

	public static interface FVoiceDataCallBack_MR_V30 extends Callback {
		public void invoke(NativeLong lVoiceComHandle, String pRecvDataBuffer, int dwBufSize, byte byAudioFlag,
				String pUser);
	}

	public static interface FVoiceDataCallBack2 extends Callback {
		public void invoke(String pRecvDataBuffer, int dwBufSize, Pointer pUser);
	}

	public static interface FSerialDataCallBack extends Callback {
		public void invoke(NativeLong lSerialHandle, String pRecvDataBuffer, int dwBufSize, int dwUser);
	}

	public static interface FSerialDataCallBack_V40 extends Callback {
		public void invoke(NativeLong lSerialHandle, NativeLong lChannel, Pointer pRecvDataBuffer, int dwBufSize,
				Pointer pUser);
	}

	public static interface FRowDataCallBack extends Callback {
		public void invoke(NativeLong lUserID, String sIPAddr, NativeLong lRowAmout, String pRecvDataBuffer,
				int dwBufSize, int dwUser);
	}

	public static interface FColLocalDataCallBack extends Callback {
		public void invoke(NativeLong lUserID, String sIPAddr, NativeLong lColumnAmout, String pRecvDataBuffer,
				int dwBufSize, int dwUser);
	}

	public static interface FColGlobalDataCallBack extends Callback {
		public void invoke(NativeLong lUserID, String sIPAddr, NativeLong lColumnAmout, String pRecvDataBuffer,
				int dwBufSize, int dwUser);
	}

	public static interface FJpegdataCallBack extends Callback {
		public int invoke(NativeLong lCommand, NativeLong lUserID, String sDVRIP, String sJpegName, String pJpegBuf,
				int dwBufLen, int dwUser);
	}

	public static interface FPostMessageCallBack extends Callback {
		public int invoke(int dwType, NativeLong lIndex);
	}

	public static interface FLoginResultCallBack extends Callback {
		public int invoke(NativeLong lUserID, int dwResult, Pointer lpDeviceinfo, Pointer pUser);
	}

	boolean NET_DVR_Init();

	boolean NET_DVR_Cleanup();

	boolean NET_DVR_SetDVRMessage(int nMessage, int hWnd);

	// NET_DVR_SetDVRMessage
	boolean NET_DVR_SetExceptionCallBack_V30(int nMessage, int hWnd, FExceptionCallBack fExceptionCallBack,
			Pointer pUser);

	boolean NET_DVR_SetDVRMessCallBack(FMessCallBack fMessCallBack);

	boolean NET_DVR_SetDVRMessCallBack_EX(FMessCallBack_EX fMessCallBack_EX);

	boolean NET_DVR_SetDVRMessCallBack_NEW(FMessCallBack_NEW fMessCallBack_NEW);

	boolean NET_DVR_SetDVRMessageCallBack(FMessageCallBack fMessageCallBack, int dwUser);

	boolean NET_DVR_SetDVRMessageCallBack_V30(FMSGCallBack fMessageCallBack, Pointer pUser);

	boolean NET_DVR_SetDVRMessageCallBack_V31(FMSGCallBack fMessageCallBack, Pointer pUser);

	boolean NET_DVR_SetConnectTime(int dwWaitTime, int dwTryTimes);

	boolean NET_DVR_SetReconnect(int dwInterval, boolean bEnableRecon);

	int NET_DVR_GetSDKVersion();

	int NET_DVR_GetSDKBuildVersion();

	int NET_DVR_IsSupport();

	boolean NET_DVR_StartListen(String sLocalIP, short wLocalPort);

	boolean NET_DVR_StopListen();

	NativeLong NET_DVR_StartListen_V30(String sLocalIP, short wLocalPort, FMSGCallBack DataCallback, Pointer pUserData);

	boolean NET_DVR_StopListen_V30(NativeLong lListenHandle);

	NativeLong NET_DVR_Login(String sDVRIP, short wDVRPort, String sUserName, String sPassword,
			NET_DVR_DEVICEINFO lpDeviceInfo);

	// NativeLong NET_DVR_Login_V30(String sDVRIP, short wDVRPort, String sUserName,
	// String sPassword, NET_DVR_DEVICEINFO_V30 lpDeviceInfo);
	NativeLong NET_DVR_Login_V30(String sDVRIP, int wDVRPort, String sUserName, String sPassword,
			NET_DVR_DEVICEINFO_V30 lpDeviceInfo);

	NativeLong NET_DVR_Login_V40(Pointer pLoginInfo, Pointer lpDeviceInfo);

	boolean NET_DVR_Logout(NativeLong lUserID);

	boolean NET_DVR_Logout_V30(NativeLong lUserID);

	int NET_DVR_GetLastError();

	String NET_DVR_GetErrorMsg(NativeLongByReference pErrorNo);

	boolean NET_DVR_SetShowMode(int dwShowType, int colorKey);

	boolean NET_DVR_GetDVRIPByResolveSvr(String sServerIP, short wServerPort, String sDVRName, short wDVRNameLen,
			String sDVRSerialNumber, short wDVRSerialLen, String sGetIP);

	boolean NET_DVR_GetDVRIPByResolveSvr_EX(String sServerIP, short wServerPort, String sDVRName, short wDVRNameLen,
			String sDVRSerialNumber, short wDVRSerialLen, String sGetIP, IntByReference dwPort);

	//
	NativeLong NET_DVR_RealPlay(NativeLong lUserID, NET_DVR_CLIENTINFO lpClientInfo);

	NativeLong NET_DVR_RealPlay_V30(NativeLong lUserID, NET_DVR_CLIENTINFO lpClientInfo,
			FRealDataCallBack_V30 fRealDataCallBack_V30, Pointer pUser, boolean bBlocked);

	NativeLong NET_DVR_RealPlay_V40(NativeLong lUserID, NET_DVR_PREVIEWINFO lpPreviewInfo,
			FRealDataCallBack_V30 fRealDataCall, Pointer pUser);

	boolean NET_DVR_StopRealPlay(NativeLong lRealHandle);

	boolean NET_DVR_RigisterDrawFun(NativeLong lRealHandle, FDrawFun fDrawFun, int dwUser);

	boolean NET_DVR_SetPlayerBufNumber(NativeLong lRealHandle, int dwBufNum);

	boolean NET_DVR_ThrowBFrame(NativeLong lRealHandle, int dwNum);

	boolean NET_DVR_SetAudioMode(int dwMode);

	boolean NET_DVR_OpenSound(NativeLong lRealHandle);

	boolean NET_DVR_CloseSound();

	boolean NET_DVR_OpenSoundShare(NativeLong lRealHandle);

	boolean NET_DVR_CloseSoundShare(NativeLong lRealHandle);

	boolean NET_DVR_Volume(NativeLong lRealHandle, short wVolume);

	boolean NET_DVR_SaveRealData(NativeLong lRealHandle, String sFileName);

	boolean NET_DVR_StopSaveRealData(NativeLong lRealHandle);

	boolean NET_DVR_SetRealDataCallBack(NativeLong lRealHandle, FRowDataCallBack fRealDataCallBack, int dwUser);

	boolean NET_DVR_SetStandardDataCallBack(NativeLong lRealHandle, FStdDataCallBack fStdDataCallBack, int dwUser);

	boolean NET_DVR_CapturePicture(NativeLong lRealHandle, String sPicFileName);// bmp

	// I
	boolean NET_DVR_MakeKeyFrame(NativeLong lUserID, NativeLong lChannel);//

	boolean NET_DVR_MakeKeyFrameSub(NativeLong lUserID, NativeLong lChannel);//

	//
	boolean NET_DVR_PTZControl(NativeLong lRealHandle, int dwPTZCommand, int dwStop);

	boolean NET_DVR_PTZControl_Other(NativeLong lUserID, NativeLong lChannel, int dwPTZCommand, int dwStop);

	boolean NET_DVR_TransPTZ(NativeLong lRealHandle, String pPTZCodeBuf, int dwBufSize);

	boolean NET_DVR_TransPTZ_Other(NativeLong lUserID, NativeLong lChannel, String pPTZCodeBuf, int dwBufSize);

	boolean NET_DVR_PTZPreset(NativeLong lRealHandle, int dwPTZPresetCmd, int dwPresetIndex);

	boolean NET_DVR_PTZPreset_Other(NativeLong lUserID, NativeLong lChannel, int dwPTZPresetCmd, int dwPresetIndex);

	boolean NET_DVR_TransPTZ_EX(NativeLong lRealHandle, String pPTZCodeBuf, int dwBufSize);

	boolean NET_DVR_PTZControl_EX(NativeLong lRealHandle, int dwPTZCommand, int dwStop);

	boolean NET_DVR_PTZPreset_EX(NativeLong lRealHandle, int dwPTZPresetCmd, int dwPresetIndex);

	boolean NET_DVR_PTZCruise(NativeLong lRealHandle, int dwPTZCruiseCmd, byte byCruiseRoute, byte byCruisePoint,
			short wInput);

	boolean NET_DVR_PTZCruise_Other(NativeLong lUserID, NativeLong lChannel, int dwPTZCruiseCmd, byte byCruiseRoute,
			byte byCruisePoint, short wInput);

	boolean NET_DVR_PTZCruise_EX(NativeLong lRealHandle, int dwPTZCruiseCmd, byte byCruiseRoute, byte byCruisePoint,
			short wInput);

	boolean NET_DVR_PTZTrack(NativeLong lRealHandle, int dwPTZTrackCmd);

	boolean NET_DVR_PTZTrack_Other(NativeLong lUserID, NativeLong lChannel, int dwPTZTrackCmd);

	boolean NET_DVR_PTZTrack_EX(NativeLong lRealHandle, int dwPTZTrackCmd);

	boolean NET_DVR_PTZControlWithSpeed(NativeLong lRealHandle, int dwPTZCommand, int dwStop, int dwSpeed);

	boolean NET_DVR_PTZControlWithSpeed_Other(NativeLong lUserID, NativeLong lChannel, int dwPTZCommand, int dwStop,
			int dwSpeed);

	boolean NET_DVR_PTZControlWithSpeed_EX(NativeLong lRealHandle, int dwPTZCommand, int dwStop, int dwSpeed);

	boolean NET_DVR_GetPTZCruise(NativeLong lUserID, NativeLong lChannel, NativeLong lCruiseRoute,
			NET_DVR_CRUISE_RET lpCruiseRet);

	boolean NET_DVR_PTZMltTrack(NativeLong lRealHandle, int dwPTZTrackCmd, int dwTrackIndex);

	boolean NET_DVR_PTZMltTrack_Other(NativeLong lUserID, NativeLong lChannel, int dwPTZTrackCmd, int dwTrackIndex);

	boolean NET_DVR_PTZMltTrack_EX(NativeLong lRealHandle, int dwPTZTrackCmd, int dwTrackIndex);

	boolean NET_DVR_RemoteControl(NativeLong lUserID, int dwCommand, Structure lpParam, int size);

	//
	NativeLong NET_DVR_FindFile(NativeLong lUserID, NativeLong lChannel, int dwFileType, NET_DVR_TIME lpStartTime,
			NET_DVR_TIME lpStopTime);

	NativeLong NET_DVR_FindNextFile(NativeLong lFindHandle, NET_DVR_FIND_DATA lpFindData);

	boolean NET_DVR_FindClose(NativeLong lFindHandle);

	NativeLong NET_DVR_FindNextFile_V30(NativeLong lFindHandle, NET_DVR_FINDDATA_V30 lpFindData);

	NativeLong NET_DVR_FindFile_V30(NativeLong lUserID, NET_DVR_FILECOND pFindCond);

	boolean NET_DVR_FindClose_V30(NativeLong lFindHandle);

	NativeLong NET_DVR_FindFile_V40(NativeLong lUserID, NET_DVR_FILECOND_V40 pFindCond);

	NativeLong NET_DVR_FindNextFile_V40(NativeLong lFindHandle, NET_DVR_FINDDATA_V40 lpFindData);

	// 2007-04-16
	NativeLong NET_DVR_FindNextFile_Card(NativeLong lFindHandle, NET_DVR_FINDDATA_CARD lpFindData);

	NativeLong NET_DVR_FindFile_Card(NativeLong lUserID, NativeLong lChannel, int dwFileType, NET_DVR_TIME lpStartTime,
			NET_DVR_TIME lpStopTime);

	boolean NET_DVR_LockFileByName(NativeLong lUserID, String sLockFileName);

	boolean NET_DVR_UnlockFileByName(NativeLong lUserID, String sUnlockFileName);

	NativeLong NET_DVR_PlayBackByName(NativeLong lUserID, String sPlayBackFileName, HWND hWnd);

	NativeLong NET_DVR_PlayBackReverseByName(NativeLong lUserID, String sPlayBackFileName, HWND hwnd);

	NativeLong NET_DVR_PlayBackByTime(NativeLong lUserID, NativeLong lChannel, NET_DVR_TIME lpStartTime,
			NET_DVR_TIME lpStopTime, HWND hWnd);

	NativeLong NET_DVR_PlayBackByTime_V40(NativeLong lUserID, NET_DVR_VOD_PARA pVodPara);

	NativeLong NET_DVR_PlayBackReverseByTime_V40(NativeLong lUserID, HWND hWnd, NET_DVR_PLAYCOND pPlayCond);

	boolean NET_DVR_PlayBackControl(NativeLong lPlayHandle, int dwControlCode, int dwInValue,
			IntByReference LPOutValue);

	boolean NET_DVR_PlayBackControl_V40(NativeLong lPlayHandle, int dwControlCode, Pointer lpInBuffer, int dwInLen,
			Pointer lpOutBuffer, IntByReference LPOutValue);

	boolean NET_DVR_StopPlayBack(NativeLong lPlayHandle);

	boolean NET_DVR_SetPlayDataCallBack(NativeLong lPlayHandle, FPlayDataCallBack fPlayDataCallBack, int dwUser);

	boolean NET_DVR_PlayBackSaveData(NativeLong lPlayHandle, String sFileName);

	boolean NET_DVR_StopPlayBackSave(NativeLong lPlayHandle);

	boolean NET_DVR_GetPlayBackOsdTime(NativeLong lPlayHandle, NET_DVR_TIME lpOsdTime);

	boolean NET_DVR_PlayBackCaptureFile(NativeLong lPlayHandle, String sFileName);

	NativeLong NET_DVR_GetFileByName(NativeLong lUserID, String sDVRFileName, String sSavedFileName);

	NativeLong NET_DVR_GetFileByTime(NativeLong lUserID, NativeLong lChannel, NET_DVR_TIME lpStartTime,
			NET_DVR_TIME lpStopTime, String sSavedFileName);

	NativeLong NET_DVR_GetFileByTime_V40(NativeLong lUserID, String sSavedFileName, NET_DVR_PLAYCOND pDownloadCond);

	boolean NET_DVR_StopGetFile(NativeLong lFileHandle);

	int NET_DVR_GetDownloadPos(NativeLong lFileHandle);

	int NET_DVR_GetPlayBackPos(NativeLong lPlayHandle);

	//
	NativeLong NET_DVR_Upgrade(NativeLong lUserID, String sFileName);

	int NET_DVR_GetUpgradeState(NativeLong lUpgradeHandle);

	int NET_DVR_GetUpgradeProgress(NativeLong lUpgradeHandle);

	boolean NET_DVR_CloseUpgradeHandle(NativeLong lUpgradeHandle);

	boolean NET_DVR_SetNetworkEnvironment(int dwEnvironmentLevel);

	//
	NativeLong NET_DVR_FormatDisk(NativeLong lUserID, NativeLong lDiskNumber);

	boolean NET_DVR_GetFormatProgress(NativeLong lFormatHandle, NativeLongByReference pCurrentFormatDisk,
			NativeLongByReference pCurrentDiskPos, NativeLongByReference pFormatStatic);

	boolean NET_DVR_CloseFormatHandle(NativeLong lFormatHandle);

	//
	NativeLong NET_DVR_SetupAlarmChan(NativeLong lUserID);

	boolean NET_DVR_CloseAlarmChan(NativeLong lAlarmHandle);

	NativeLong NET_DVR_SetupAlarmChan_V30(NativeLong lUserID);

	boolean NET_DVR_CloseAlarmChan_V30(NativeLong lAlarmHandle);

	// Sagi ^^^^

	public static class NET_VCA_LINE extends Structure implements Serializable {
		public NET_VCA_POINT struStart;// ֶנµד
		public NET_VCA_POINT struEnd; // ײױµד
	}

	/*
	 * public enum VCA_CROSS_DIRECTION { VCA_BOTH_DIRECTION,// ֻ«ֿע
	 * VCA_LEFT_GO_RIGHT,// ׃ֹ׳ףײֱ׃ׂ VCA_RIGHT_GO_LEFT,// ׃ֹ׃ׂײֱ׳ף }
	 */
	public static class NET_VCA_INTRUSION extends Structure implements Serializable {
		public NET_VCA_POLYGON struRegion;// ַר׃ע·¶־§
		public short wDuration;
		public byte bySensitivity;
		public byte byRate;
		public byte[] byRes = new byte[4];
	}

	public static class NET_VCA_TRAVERSE_PLANE extends Structure implements Serializable {
		public NET_VCA_LINE struPlaneBottom;
		public int dwCrossDirection;
		public byte byRes1;
		public byte byPlaneHeight;
		public byte[] byRes2 = new byte[38];
	}

	public static class NET_VCA_RULE_INFO extends Structure implements Serializable {
		public byte byRuleID;
		public byte byRes;
		public short wEventTypeEx;
		public byte[] byRuleName = new byte[NAME_LEN];
		public int dwEventType;
		public int[] uEventParam = new int[23]; // used to be public NET_VCA_EVENT_UNION uEventParam; which is vector of
												// 23 int
	}

	public static class NET_VCA_TARGET_INFO extends Structure implements Serializable {
		public int dwID;
		public NET_VCA_RECT struRect;
		public byte[] byRes = new byte[4];
	}

	public static class NET_VCA_RULE_ALARM extends Structure implements Serializable {
		public int dwSize;
		public int dwRelativeTime;
		public int dwAbsTime;
		public NET_VCA_RULE_INFO struRuleInfo;
		public NET_VCA_TARGET_INFO struTargetInfo;
		public NET_VCA_DEV_INFO struDevInfo;
		public int dwPicDataLen;
		public byte byPicType;
		public byte[] byRes = new byte[3];
		public int[] dwRes = new int[3];
		public transient Pointer pImage;
	}

	public static class NET_PTZ_INFO extends Structure implements Serializable {

		public float fPan;
		public float fTilt;
		public float fZoom;
		public int dwFocus;
		public byte[] byRes = new byte[4];
	}

	public int VCA_MAX_POLYGON_POINT_NUM = 10;

	public static class NET_VCA_POLYGON extends Structure implements Serializable {
		/// DWORD->unsigned int
		public int dwPointNum;
		public NET_VCA_POINT[] struPos = new NET_VCA_POINT[VCA_MAX_POLYGON_POINT_NUM];
	}

	public static class NET_DVR_FACE_DETECTION extends Structure implements Serializable {
		public int dwSize;
		public int dwRelativeTime;
		public int dwAbsTime;
		public int dwBackgroundPicLen;
		public NET_VCA_DEV_INFO struDevInfo;
		public NET_VCA_RECT[] struFacePic = new NET_VCA_RECT[30];
		public byte byFacePicNum;
		public byte[] byRes = new byte[255];
		public transient Pointer pBackgroundPicpBuffer;

	}

	// sagi^^^
	public static class NET_VCA_HUMAN_FEATURE extends Structure implements Serializable {
		/*
		 * struct{ BYTE byAgeGroup; BYTE bySex; BYTE byEyeGlass; BYTE byAge; BYTE
		 * byAgeDeviation; BYTE byEthnic; BYTE byMask; BYTE bySmile; BYTE byRes8];
		 * }NET_VCA_HUMAN_FEATURE, *LPNET_VCA_HUMAN_FEATURE;
		 */
		public byte byAgeGroup;
		public byte bySex;
		public byte byEyeGlass;
		public byte byAge;
		public byte byAgeDeviation;
		public byte byEthnic;
		public byte byMask;
		public byte bySmile;
		public byte byFaceExpression; /* FACE_EXPRESSION_GROUP_ENUM */
		public byte byBeard;
		public byte byRace;
		public byte byHat; // hat
		public byte[] byRes = new byte[4];

	}

	public static class NET_VCA_FACESNAP_RESULT extends Structure implements Serializable {
		/*
		 * struct{ DWORD dwSize; DWORD dwRelativeTime; DWORD dwAbsTime; DWORD
		 * dwFacePicID; DWORD dwFaceScore; NET_VCA_TARGET_INFO struTargetInfo;
		 * NET_VCA_RECT struRect; NET_VCA_DEV_INFO struDevInfo; DWORD dwFacePicLen;
		 * DWORD dwBackgroundPicLen; BYTE bySmart; BYTE byAlarmEndMark; BYTE
		 * byRepeatTimes; BYTE byUploadEventDataType; NET_VCA_HUMAN_FEATURE struFeature;
		 * float fStayDuration; char sStorageIP[16]; WORD wStoragePort; WORD
		 * wDevInfoIvmsChannelEx; BYTE byRes1[15]; BYTE byBrokenNetHttp; BYTE *pBuffer1;
		 * BYTE *pBuffer2; }NET_VCA_FACESNAP_RESULT, *LPNET_VCA_FACESNAP_RESULT;
		 */

		public int dwSize;
		public int dwRelativeTime;
		public int dwAbsTime;
		public int dwFacePicID;
		public int dwFaceScore;
		public NET_VCA_TARGET_INFO struTargetInfo;
		public NET_VCA_RECT struRect;
		public NET_VCA_DEV_INFO struDevInfo;
		public int dwFacePicLen;
		public int dwBackgroundPicLen;
		public byte bySmart;
		public byte byAlarmEndMark;
		public byte byRepeatTimes;
		public byte byUploadEventDataType;
		public NET_VCA_HUMAN_FEATURE struFeature;
		public float fStayDuration;
		public char[] sStorageIP = new char[16];
		public Short wStoragePort;
		public Short wDevInfoIvmsChannelEx;
		public byte[] byRes1 = new byte[15];
		public byte byBrokenNetHttp;
		public transient Pointer pBuffer1;
		public transient Pointer pBuffer2;

	}

	public static class NET_VCA_FACESNAP_INFO_ALARM extends Structure implements Serializable {
		/*
		 * typedef struct tagNET_VCA_FACESNAP_INFO_ALARM { DWORD dwRelativeTime;
		 * //Relative time DWORD dwAbsTime; //Absolute time DWORD dwSnapFacePicID;
		 * //Snapshot face picture ID DWORD dwSnapFacePicLen; //Length of face subgraph:
		 * 0- no picture, larger than 0- there is related picture NET_VCA_DEV_INFO
		 * struDevInfo; //Front-end device information BYTE byFaceScore;
		 * //FaceScore,0-100 BYTE bySex;//Sex,0-unknow, 1-boy,2-girl BYTE
		 * byGlasses;//Glasses,0-unknow, 1-y, 2-n, 3-sunglasses BYTE byAge;//Age BYTE
		 * byAgeDeviation;//AgeDeviation BYTE byAgeGroup;//age group, 0xff - unknown
		 * BYTE byFacePicQuality;//face snap subpicture quality BYTE byEthnic; DWORD
		 * dwUIDLen; //UIDLen BYTE *pUIDBuffer; //UIDBuffer float fStayDuration; //stay
		 * duration BYTE *pBuffer1; //Snapshot face subgraph data
		 * }NET_VCA_FACESNAP_INFO_ALARM, *LPNET_VCA_FACESNAP_INFO_ALARM;
		 */
		public int dwRelativeTime; // Relative time
		public int dwAbsTime; // Absolute time
		public int dwSnapFacePicID; // Snapshot face picture ID
		public int dwSnapFacePicLen; // Length of face subgraph: 0- no picture, larger than 0- there is related
										// picture
		public NET_VCA_DEV_INFO struDevInfo; // Front-end device information
		public byte byFaceScore; // FaceScore,0-100
		public byte bySex;// Sex,0-unknow, 1-boy,2-girl
		public byte byGlasses;// Glasses,0-unknow, 1-y, 2-n, 3-sunglasses
		public byte byAge;// Age
		public byte byAgeDeviation;// AgeDeviation
		public byte byAgeGroup;// age group, 0xff - unknown
		public byte byFacePicQuality;// face snap subpicture quality
		public byte byEthnic;
		public int dwUIDLen; // UIDLen
		public transient Pointer pUIDBuffer; // UIDBuffer
		public float fStayDuration; // stay duration
		public transient Pointer pBuffer1; // Snapshot face subgraph data

	}

	public static final int MAX_HUMAN_PICTURE_NUM = 10; // The total number of pictures
	public static final int MAX_HUMAN_BIRTHDATE_LEN = 10; // The max length of birthday

	public static class NET_DVR_AREAINFOCFG extends Structure implements Serializable {
		/*
		 * typedef struct tagNET_DVR_AREAINFOCFG { WORD wNationalityID; //Nationality
		 * WORD wProvinceID; //Province WORD wCityID; //City WORD wCountyID; //County
		 * DWORD dwCode; //government standard code }NET_DVR_AREAINFOCFG,
		 * *LPNET_DVR_AREAINFOCFG;
		 */
		public Short wNationalityID; // Nationality
		public Short wProvinceID; // Province
		public Short wCityID; // City
		public Short wCountyID; // County
		public int dwCode; // government standard code
	}

	public static class NET_VCA_HUMAN_ATTRIBUTE extends Structure implements Serializable {
		/*
		 * typedef struct tagNET_VCA_HUMAN_ATTRIBUTE { BYTE bySex; //Gender: 0- man, 1-
		 * woman, 0xff-unknown BYTE byCertificateType; //Certificate type: 0- identity
		 * card,1- police certificate,2 - military officer certificate, 3 - passport, 4
		 * - other, 0xff-unknown BYTE byBirthDate[MAX_HUMAN_BIRTHDATE_LEN]; //Birthday,
		 * for example: 201106 BYTE byName[NAME_LEN]; //Name NET_DVR_AREAINFOCFG
		 * struNativePlace; //Birthplace parameter BYTE byCertificateNumber[NAME_LEN];
		 * //Certificate number DWORD dwPersonInfoExtendLen;// PersonInfoExtendLen BYTE
		 * *pPersonInfoExtend; //PersonInfoExtend BYTE byAgeGroup;//age group,
		 * 0xff-unknown BYTE byRes2[11]; }NET_VCA_HUMAN_ATTRIBUTE,
		 * *LPNET_VCA_HUMAN_ATTRIBUTE;
		 * 
		 */
		public byte bySex; // Gender: 0- man, 1- woman, 0xff-unknown
		public byte byCertificateType; // Certificate type: 0- identity card,1- police certificate,2 - military officer
										// certificate, 3 - passport, 4 - other, 0xff-unknown
		public byte[] byBirthDate = new byte[MAX_HUMAN_BIRTHDATE_LEN]; // Birthday, for example: 201106
		public byte[] byName = new byte[NAME_LEN]; // Name
		public NET_DVR_AREAINFOCFG struNativePlace; // Birthplace parameter
		public byte[] byCertificateNumber = new byte[NAME_LEN]; // Certificate number
		public int dwPersonInfoExtendLen;// PersonInfoExtendLen
		public transient Pointer pPersonInfoExtend; // PersonInfoExtend
		public byte byAgeGroup;// age group, 0xff-unknown
		public byte[] byRes2 = new byte[11];

	}

	public static class NET_VCA_BLACKLIST_INFO extends Structure implements Serializable {
		/*
		 * typedef struct tagNET_VCA_BLACKLIST_INFO { DWORD dwSize; //Structure size
		 * DWORD dwRegisterID; //Register ID (read-only) DWORD dwGroupNo; //Group number
		 * BYTE byType; //Black and white list flag: 0- all, 1- white list, 2- black
		 * list BYTE byLevel; //Black list level: 0- all, 1- low, 2- middle, 3- high
		 * BYTE byRes1[2]; //Reserved NET_VCA_HUMAN_ATTRIBUTE struAttribute; //Personnel
		 * information BYTE byRemark[NAME_LEN]; //Remark information DWORD
		 * dwFDDescriptionLen;//face lib desc len BYTE *pFDDescriptionBuffer;//face lib
		 * desc ptr DWORD dwFCAdditionInfoLen;//Snapshot library additional information
		 * length BYTE *pFCAdditionInfoBuffer;//Snapshot library additional information
		 * data pointer BYTE byRes2[4]; }NET_VCA_BLACKLIST_INFO,
		 * *LPNET_VCA_BLACKLIST_INFO;
		 */

		public int dwSize; // Structure size
		public int dwRegisterID; // Register ID (read-only)
		public int dwGroupNo; // Group number
		public byte byType; // Black and white list flag: 0- all, 1- white list, 2- black list
		public byte byLevel; // Black list level: 0- all, 1- low, 2- middle, 3- high
		public byte[] byRes1 = new byte[2]; // Reserved
		public NET_VCA_HUMAN_ATTRIBUTE struAttribute; // Personnel information
		public byte[] byRemark = new byte[NAME_LEN]; // Remark information
		public int dwFDDescriptionLen;// face lib desc len
		public transient Pointer pFDDescriptionBuffer;// face lib desc ptr
		public int dwFCAdditionInfoLen;// Snapshot library additional information length
		public transient Pointer pFCAdditionInfoBuffer;// Snapshot library additional information data pointer
		public byte[] byRes2 = new byte[4];

	}

	public static class NET_VCA_BLACKLIST_INFO_ALARM extends Structure implements Serializable {
		/*
		 * typedef struct tagNET_VCA_BLACKLIST_INFO_ALARM { NET_VCA_BLACKLIST_INFO
		 * struBlackListInfo; //Blacklist basic information DWORD dwBlackListPicLen;
		 * //Length of blacklist face subgraph: 0- no picture, larger than 0- there is
		 * related picture DWORD dwFDIDLen;// Face library length ID BYTE *pFDID; //Face
		 * library ID buffer DWORD dwPIDLen;//Face library P length ID BYTE *pPID;
		 * //Face library PID buffer WORD wThresholdValue; //ThresholdValue[0,100] BYTE
		 * byIsNoSaveFDPicture;//0-save FD picture 1-not save FD picture BYTE
		 * byRealTimeContrast;//Whether real time alarm 0- real time 1- non-real time
		 * BYTE *pBuffer1; //Blacklist face subgraph data }NET_VCA_BLACKLIST_INFO_ALARM,
		 * *LPNET_VCA_BLACKLIST_INFO_ALARM;
		 */
		public NET_VCA_BLACKLIST_INFO struBlackListInfo; // Blacklist basic information
		public int dwBlackListPicLen; // Length of blacklist face subgraph: 0- no picture, larger than 0- there is
										// related picture
		public int dwFDIDLen;// Face library length ID
		public transient Pointer pFDID; // Face library ID buffer
		public int dwPIDLen;// Face library P length ID
		public transient Pointer pPID; // Face library PID buffer
		public Short wThresholdValue; // ThresholdValue[0,100]
		public byte byIsNoSaveFDPicture;// 0-save FD picture 1-not save FD picture
		public byte byRealTimeContrast;// Whether real time alarm 0- real time 1- non-real time
		public transient Pointer pBuffer1; // Blacklist face subgraph data

	}

	public static class NET_VCA_FACESNAP_MATCH_ALARM extends Structure implements Serializable {
		/*
		 * typedef struct tagNET_VCA_FACESNAP_MATCH_ALARM { DWORD dwSize; //Structure
		 * size float fSimilarity; //Similarity, value range: [0.001,1]
		 * NET_VCA_FACESNAP_INFO_ALARM struSnapInfo; //Snapshot information
		 * NET_VCA_BLACKLIST_INFO_ALARM struBlackListInfo; //Blacklist information char
		 * sStorageIP[16]; WORD wStoragePort; BYTE byMatchPicNum; //MatchPicNum BYTE
		 * byPicTransType;//PicTransType: 0-byte;1-url DWORD dwSnapPicLen;//SnapPicLen
		 * BYTE *pSnapPicBuffer;//SnapPicBuffer NET_VCA_RECT struRegion;//Region DWORD
		 * dwModelDataLen;//ModelDataLen BYTE *pModelDataBuffer;//ModelDataBuffer BYTE
		 * byModelingStatus;//Modeling status BYTE
		 * byLivenessDetectionStatus;//LivenessDetectionStatus char cTimeDifferenceH;
		 * /*Time difference(HOUR) from UTC * / char cTimeDifferenceM; /*Time
		 * difference(MINUTE) from UTC* / BYTE byMask; //Whether to wear a mask, 0-
		 * retention, 1- unknown, 2- no mask, 3- wear a mask. BYTE bySmile; //Whether
		 * the snapshot is smiling, 0- retention, 1- unknown, 2- not smiling, 3- smile.
		 * BYTE byContrastStatus; //Contrast results, 0- retention, 1- contrast success,
		 * 2- contrast failure. BYTE byBrokenNetHttp; //Broken Net Http,0-Not a
		 * retransmission data,1- retransmission data }NET_VCA_FACESNAP_MATCH_ALARM,
		 * *LPNET_VCA_FACESNAP_MATCH_ALARM;
		 */

		public int dwSize; // Structure size
		public float fSimilarity; // Similarity, value range: [0.001,1]
		public NET_VCA_FACESNAP_INFO_ALARM struSnapInfo; // Snapshot information
		public NET_VCA_BLACKLIST_INFO_ALARM struBlackListInfo; // Blacklist information
		public byte[] sStorageIP = new byte[16];
		public Short wStoragePort;
		public byte byMatchPicNum; // MatchPicNum
		public byte byPicTransType;// PicTransType: 0-byte;1-url
		public int dwSnapPicLen;// SnapPicLen
		public transient Pointer pSnapPicBuffer;// SnapPicBuffer
		public NET_VCA_RECT struRegion;// Region
		public int dwModelDataLen;// ModelDataLen
		public transient Pointer pModelDataBuffer;// ModelDataBuffer
		public byte byModelingStatus;// Modeling status
		public byte byLivenessDetectionStatus;// LivenessDetectionStatus
		public byte cTimeDifferenceH; /* Time difference(HOUR) from UTC */
		public byte cTimeDifferenceM; /* Time difference(MINUTE) from UTC */
		public byte byMask; // Whether to wear a mask, 0- retention, 1- unknown, 2- no mask, 3- wear a mask.
		public byte bySmile; // Whether the snapshot is smiling, 0- retention, 1- unknown, 2- not smiling, 3-
								// smile.
		public byte byContrastStatus; // Contrast results, 0- retention, 1- contrast success, 2- contrast failure.
		public byte byBrokenNetHttp; // Broken Net Http,0-Not a retransmission data,1- retransmission data

	}

	public static class NET_DVR_THERMOMETRY_ALARM extends Structure implements Serializable {
		public int dwSize;
		public int dwChannel;// Channel
		public byte byRuleID;// Rule ID
		public byte byThermometryUnit;
		public short wPresetNo; // Preset No.
		public NET_PTZ_INFO struPtzInfo;// ptz
		public byte byAlarmLevel;// 0-Advance Alarm 1-Alarm
		public byte byAlarmType;/*
								 * Alarm Type 0-Max Temperature 1-Min Temperature 2-Average Temperature
								 * 3-Temperature Difference 4-sudden up 5-sudden down
								 */
		public byte byAlarmRule;// 0-Greater than,1-Less than
		public byte byRuleCalibType;// Regular calibration type 0-Point,1-Region, 2-Line
		public NET_VCA_POINT struPoint;// Point
		public NET_VCA_POLYGON struRegion;// Region
		public float fRuleTemperature;/* Rule Temperature */
		public float fCurrTemperature;/* current Temperature */
		public int dwPicLen;// Picture Len
		public int dwThermalPicLen;// Thermal Picture Len
		public int dwThermalInfoLen;// Thermal Info Len
		public transient Pointer pPicBuff;// Picture
		public transient Pointer pThermalPicBuff;// Thermal Picture
		public transient Pointer pThermalInfoBuff;// Thermal Info
		public NET_VCA_POINT struHighestPoint;// Highest Point
		public float fToleranceTemperature;//
		public int dwAlertFilteringTime;//
		public int dwAlarmFilteringTime;//
		public int dwTemperatureSuddenChangeCycle;//
		public float fTemperatureSuddenChangeValue;//
		public byte byPicTransType; // Image data transmission: 0-binary; 1-url
		public byte[] byRes = new byte[39];

	}

	public static class NET_DVR_SETUPALARM_PARAM extends Structure {

		public int dwSize;
		public byte byLevel;
		public byte byAlarmInfoType;
		public byte byRetAlarmTypeV40;
		public byte byRetDevInfoVersion;
		public byte byRetVQDAlarmType;
		public byte byFaceAlarmDetection;
		public byte bySupport;
		public byte byBrokenNetHttp;
		public short wTaskNo;
		public byte[] byRes = new byte[6];
	}

	NativeLong NET_DVR_SetupAlarmChan_V41(NativeLong lUserID, NET_DVR_SETUPALARM_PARAM struAlarmParam);

	//
	NativeLong NET_DVR_StartVoiceCom(NativeLong lUserID, FVoiceDataCallBack fVoiceDataCallBack, int dwUser);

	NativeLong NET_DVR_StartVoiceCom_V30(NativeLong lUserID, int dwVoiceChan, boolean bNeedCBNoEncData,
			FVoiceDataCallBack_V30 fVoiceDataCallBack, Pointer pUser);

	boolean NET_DVR_SetVoiceComClientVolume(NativeLong lVoiceComHandle, short wVolume);

	boolean NET_DVR_StopVoiceCom(NativeLong lVoiceComHandle);

	//
	NativeLong NET_DVR_StartVoiceCom_MR(NativeLong lUserID, FVoiceDataCallBack_MR fVoiceDataCallBack, int dwUser);

	NativeLong NET_DVR_StartVoiceCom_MR_V30(NativeLong lUserID, int dwVoiceChan,
			FVoiceDataCallBack_MR_V30 fVoiceDataCallBack, Pointer pUser);

	boolean NET_DVR_VoiceComSendData(NativeLong lVoiceComHandle, String pSendBuf, int dwBufSize);

	//
	boolean NET_DVR_ClientAudioStart();

	boolean NET_DVR_ClientAudioStart_V30(FVoiceDataCallBack2 fVoiceDataCallBack2, Pointer pUser);

	boolean NET_DVR_ClientAudioStop();

	boolean NET_DVR_AddDVR(NativeLong lUserID);

	NativeLong NET_DVR_AddDVR_V30(NativeLong lUserID, int dwVoiceChan);

	boolean NET_DVR_DelDVR(NativeLong lUserID);

	boolean NET_DVR_DelDVR_V30(NativeLong lVoiceHandle);

	////////////////////////////////////////////////////////////
	//
	NativeLong NET_DVR_SerialStart(NativeLong lUserID, NativeLong lSerialPort, FSerialDataCallBack fSerialDataCallBack,
			int dwUser);

	NativeLong NET_DVR_SerialStart_V40(NativeLong lUserID, Pointer lpInBuffer, NativeLong dwInBufferSize,
			FSerialDataCallBack_V40 cbSerialDataCallBack, Pointer pUser);

	// 485 , , 485 ( )
	boolean NET_DVR_SerialSend(NativeLong lSerialHandle, NativeLong lChannel, String pSendBuf, int dwBufSize);

	boolean NET_DVR_SerialStop(NativeLong lSerialHandle);

	boolean NET_DVR_SendTo232Port(NativeLong lUserID, String pSendBuf, int dwBufSize);

	boolean NET_DVR_SendToSerialPort(NativeLong lUserID, int dwSerialPort, int dwSerialIndex, String pSendBuf,
			int dwBufSize);

	// nBitrate = 16000
	Pointer NET_DVR_InitG722Decoder(int nBitrate);

	void NET_DVR_ReleaseG722Decoder(Pointer pDecHandle);

	boolean NET_DVR_DecodeG722Frame(Pointer pDecHandle, String pInBuffer, String pOutBuffer);

	//
	Pointer NET_DVR_InitG722Encoder();

	boolean NET_DVR_EncodeG722Frame(Pointer pEncodeHandle, String pInBuff, String pOutBuffer);

	void NET_DVR_ReleaseG722Encoder(Pointer pEncodeHandle);

	//
	boolean NET_DVR_ClickKey(NativeLong lUserID, NativeLong lKeyIndex);

	//
	boolean NET_DVR_StartDVRRecord(NativeLong lUserID, NativeLong lChannel, NativeLong lRecordType);

	boolean NET_DVR_StopDVRRecord(NativeLong lUserID, NativeLong lChannel);

	//
	boolean NET_DVR_InitDevice_Card(NativeLongByReference pDeviceTotalChan);

	boolean NET_DVR_ReleaseDevice_Card();

	boolean NET_DVR_InitDDraw_Card(int hParent, int colorKey);

	boolean NET_DVR_ReleaseDDraw_Card();

	NativeLong NET_DVR_RealPlay_Card(NativeLong lUserID, NET_DVR_CARDINFO lpCardInfo, NativeLong lChannelNum);

	boolean NET_DVR_ResetPara_Card(NativeLong lRealHandle, NET_DVR_DISPLAY_PARA lpDisplayPara);

	boolean NET_DVR_RefreshSurface_Card();

	boolean NET_DVR_ClearSurface_Card();

	boolean NET_DVR_RestoreSurface_Card();

	boolean NET_DVR_OpenSound_Card(NativeLong lRealHandle);

	boolean NET_DVR_CloseSound_Card(NativeLong lRealHandle);

	boolean NET_DVR_SetVolume_Card(NativeLong lRealHandle, short wVolume);

	boolean NET_DVR_AudioPreview_Card(NativeLong lRealHandle, boolean bEnable);

	NativeLong NET_DVR_GetCardLastError_Card();

	Pointer NET_DVR_GetChanHandle_Card(NativeLong lRealHandle);

	boolean NET_DVR_CapturePicture_Card(NativeLong lRealHandle, String sPicFileName);

	// , GetBoardDetail (2005-12-08 )
	boolean NET_DVR_GetSerialNum_Card(NativeLong lChannelNum, IntByReference pDeviceSerialNo);

	//
	NativeLong NET_DVR_FindDVRLog(NativeLong lUserID, NativeLong lSelectMode, int dwMajorType, int dwMinorType,
			NET_DVR_TIME lpStartTime, NET_DVR_TIME lpStopTime);

	NativeLong NET_DVR_FindNextLog(NativeLong lLogHandle, NET_DVR_LOG lpLogData);

	boolean NET_DVR_FindLogClose(NativeLong lLogHandle);

	NativeLong NET_DVR_FindDVRLog_V30(NativeLong lUserID, NativeLong lSelectMode, int dwMajorType, int dwMinorType,
			NET_DVR_TIME lpStartTime, NET_DVR_TIME lpStopTime, boolean bOnlySmart);

	NativeLong NET_DVR_FindNextLog_V30(NativeLong lLogHandle, NET_DVR_LOG_V30 lpLogData);

	boolean NET_DVR_FindLogClose_V30(NativeLong lLogHandle);

	// 2004 8 5 , 113
	// ATM DVR
	NativeLong NET_DVR_FindFileByCard(NativeLong lUserID, NativeLong lChannel, int dwFileType, int nFindType,
			String sCardNumber, NET_DVR_TIME lpStartTime, NET_DVR_TIME lpStopTime);
	// 2004 10 5 , 116

	// 2005-09-15
	boolean NET_DVR_CaptureJPEGPicture(NativeLong lUserID, NativeLong lChannel, NET_DVR_JPEGPARA lpJpegPara,
			String sPicFileName);

	// JPEG
	boolean NET_DVR_CaptureJPEGPicture_NEW(NativeLong lUserID, NativeLong lChannel, NET_DVR_JPEGPARA lpJpegPara,
			String sJpegPicBuffer, int dwPicSize, IntByReference lpSizeReturned);

	// 2006-02-16
	int NET_DVR_GetRealPlayerIndex(NativeLong lRealHandle);

	int NET_DVR_GetPlayBackPlayerIndex(NativeLong lPlayHandle);

	// 2006-08-28 704-640
	boolean NET_DVR_SetScaleCFG(NativeLong lUserID, int dwScale);

	boolean NET_DVR_GetScaleCFG(NativeLong lUserID, IntByReference lpOutScale);

	boolean NET_DVR_SetScaleCFG_V30(NativeLong lUserID, NET_DVR_SCALECFG pScalecfg);

	boolean NET_DVR_GetScaleCFG_V30(NativeLong lUserID, NET_DVR_SCALECFG pScalecfg);

	// 2006-08-28 ATM
	boolean NET_DVR_SetATMPortCFG(NativeLong lUserID, short wATMPort);

	boolean NET_DVR_GetATMPortCFG(NativeLong lUserID, ShortByReference LPOutATMPort);

	// 2006-11-10
	boolean NET_DVR_InitDDrawDevice();

	boolean NET_DVR_ReleaseDDrawDevice();

	NativeLong NET_DVR_GetDDrawDeviceTotalNums();

	boolean NET_DVR_SetDDrawDevice(NativeLong lPlayPort, int nDeviceNum);

	boolean NET_DVR_PTZSelZoomIn(NativeLong lRealHandle, NET_DVR_POINT_FRAME pStruPointFrame);

	boolean NET_DVR_PTZSelZoomIn_EX(NativeLong lUserID, NativeLong lChannel, NET_DVR_POINT_FRAME pStruPointFrame);

	// DS-6001D/DS-6001F
	boolean NET_DVR_StartDecode(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECODERINFO lpDecoderinfo);

	boolean NET_DVR_StopDecode(NativeLong lUserID, NativeLong lChannel);

	boolean NET_DVR_GetDecoderState(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECODERSTATE lpDecoderState);

	// 2005-08-01
	boolean NET_DVR_SetDecInfo(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECCFG lpDecoderinfo);

	boolean NET_DVR_GetDecInfo(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECCFG lpDecoderinfo);

	boolean NET_DVR_SetDecTransPort(NativeLong lUserID, NET_DVR_PORTCFG lpTransPort);

	boolean NET_DVR_GetDecTransPort(NativeLong lUserID, NET_DVR_PORTCFG lpTransPort);

	boolean NET_DVR_DecPlayBackCtrl(NativeLong lUserID, NativeLong lChannel, int dwControlCode, int dwInValue,
			IntByReference LPOutValue, NET_DVR_PLAYREMOTEFILE lpRemoteFileInfo);

	boolean NET_DVR_StartDecSpecialCon(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECCHANINFO lpDecChanInfo);

	boolean NET_DVR_StopDecSpecialCon(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECCHANINFO lpDecChanInfo);

	boolean NET_DVR_DecCtrlDec(NativeLong lUserID, NativeLong lChannel, int dwControlCode);

	boolean NET_DVR_DecCtrlScreen(NativeLong lUserID, NativeLong lChannel, int dwControl);

	boolean NET_DVR_GetDecCurLinkStatus(NativeLong lUserID, NativeLong lChannel, NET_DVR_DECSTATUS lpDecStatus);

	//
	// 2007-11-30 V211 //11
	boolean NET_DVR_MatrixStartDynamic(NativeLong lUserID, int dwDecChanNum, NET_DVR_MATRIX_DYNAMIC_DEC lpDynamicInfo);

	boolean NET_DVR_MatrixStopDynamic(NativeLong lUserID, int dwDecChanNum);

	boolean NET_DVR_MatrixGetDecChanInfo(NativeLong lUserID, int dwDecChanNum, NET_DVR_MATRIX_DEC_CHAN_INFO lpInter);

	boolean NET_DVR_MatrixSetLoopDecChanInfo(NativeLong lUserID, int dwDecChanNum, NET_DVR_MATRIX_LOOP_DECINFO lpInter);

	boolean NET_DVR_MatrixGetLoopDecChanInfo(NativeLong lUserID, int dwDecChanNum, NET_DVR_MATRIX_LOOP_DECINFO lpInter);

	boolean NET_DVR_MatrixSetLoopDecChanEnable(NativeLong lUserID, int dwDecChanNum, int dwEnable);

	boolean NET_DVR_MatrixGetLoopDecChanEnable(NativeLong lUserID, int dwDecChanNum, IntByReference lpdwEnable);

	boolean NET_DVR_MatrixGetLoopDecEnable(NativeLong lUserID, IntByReference lpdwEnable);

	boolean NET_DVR_MatrixSetDecChanEnable(NativeLong lUserID, int dwDecChanNum, int dwEnable);

	boolean NET_DVR_MatrixGetDecChanEnable(NativeLong lUserID, int dwDecChanNum, IntByReference lpdwEnable);

	boolean NET_DVR_MatrixGetDecChanStatus(NativeLong lUserID, int dwDecChanNum,
			NET_DVR_MATRIX_DEC_CHAN_STATUS lpInter);

	// 2007-12-22 //18
	boolean NET_DVR_MatrixSetTranInfo(NativeLong lUserID, NET_DVR_MATRIX_TRAN_CHAN_CONFIG lpTranInfo);

	boolean NET_DVR_MatrixGetTranInfo(NativeLong lUserID, NET_DVR_MATRIX_TRAN_CHAN_CONFIG lpTranInfo);

	boolean NET_DVR_MatrixSetRemotePlay(NativeLong lUserID, int dwDecChanNum, NET_DVR_MATRIX_DEC_REMOTE_PLAY lpInter);

	boolean NET_DVR_MatrixSetRemotePlayControl(NativeLong lUserID, int dwDecChanNum, int dwControlCode, int dwInValue,
			IntByReference LPOutValue);

	boolean NET_DVR_MatrixGetRemotePlayStatus(NativeLong lUserID, int dwDecChanNum,
			NET_DVR_MATRIX_DEC_REMOTE_PLAY_STATUS lpOuter);

	// end
	boolean NET_DVR_RefreshPlay(NativeLong lPlayHandle);

	//
	boolean NET_DVR_RestoreConfig(NativeLong lUserID);

	//
	boolean NET_DVR_SaveConfig(NativeLong lUserID);

	//
	boolean NET_DVR_RebootDVR(NativeLong lUserID);

	// DVR
	boolean NET_DVR_ShutDownDVR(NativeLong lUserID);

	// begin
	boolean NET_DVR_GetDVRConfig(NativeLong lUserID, int dwCommand, NativeLong lChannel, Pointer lpOutBuffer,
			int dwOutBufferSize, IntByReference lpBytesReturned);

	boolean NET_DVR_SetDVRConfig(NativeLong lUserID, int dwCommand, NativeLong lChannel, Pointer lpInBuffer,
			int dwInBufferSize);

	boolean NET_DVR_GetDeviceConfig(NativeLong lUserID, int dwCommand, int dwCount, Pointer lpInBuffer,
			int dwInBufferSize, Pointer lpStatusList, Pointer lpOutBuffer, int dwOutBufferSize);

	boolean NET_DVR_SetDeviceConfig(NativeLong lUserID, int dwCommand, int dwCount, Pointer lpInBuffer,
			int dwInBufferSize, Pointer lpStatusList, Pointer lpInParamBuffer, int dwInParamBufferSize);

	boolean NET_DVR_GetDVRWorkState_V30(NativeLong lUserID, NET_DVR_WORKSTATE_V30 lpWorkState);

	boolean NET_DVR_GetDVRWorkState(NativeLong lUserID, NET_DVR_WORKSTATE lpWorkState);

	boolean NET_DVR_SetVideoEffect(NativeLong lUserID, NativeLong lChannel, int dwBrightValue, int dwContrastValue,
			int dwSaturationValue, int dwHueValue);

	boolean NET_DVR_GetVideoEffect(NativeLong lUserID, NativeLong lChannel, IntByReference pBrightValue,
			IntByReference pContrastValue, IntByReference pSaturationValue, IntByReference pHueValue);

	boolean NET_DVR_ClientGetframeformat(NativeLong lUserID, NET_DVR_FRAMEFORMAT lpFrameFormat);

	boolean NET_DVR_ClientSetframeformat(NativeLong lUserID, NET_DVR_FRAMEFORMAT lpFrameFormat);

	boolean NET_DVR_ClientGetframeformat_V30(NativeLong lUserID, NET_DVR_FRAMEFORMAT_V30 lpFrameFormat);

	boolean NET_DVR_ClientSetframeformat_V30(NativeLong lUserID, NET_DVR_FRAMEFORMAT_V30 lpFrameFormat);

	boolean NET_DVR_GetAlarmOut_V30(NativeLong lUserID, NET_DVR_ALARMOUTSTATUS_V30 lpAlarmOutState);

	boolean NET_DVR_GetAlarmOut(NativeLong lUserID, NET_DVR_ALARMOUTSTATUS lpAlarmOutState);

	boolean NET_DVR_SetAlarmOut(NativeLong lUserID, NativeLong lAlarmOutPort, NativeLong lAlarmOutStatic);

	//
	boolean NET_DVR_ClientSetVideoEffect(NativeLong lRealHandle, int dwBrightValue, int dwContrastValue,
			int dwSaturationValue, int dwHueValue);

	boolean NET_DVR_ClientGetVideoEffect(NativeLong lRealHandle, IntByReference pBrightValue,
			IntByReference pContrastValue, IntByReference pSaturationValue, IntByReference pHueValue);

	//
	boolean NET_DVR_GetConfigFile(NativeLong lUserID, String sFileName);

	boolean NET_DVR_SetConfigFile(NativeLong lUserID, String sFileName);

	boolean NET_DVR_GetConfigFile_V30(NativeLong lUserID, String sOutBuffer, int dwOutSize, IntByReference pReturnSize);

	boolean NET_DVR_GetConfigFile_EX(NativeLong lUserID, String sOutBuffer, int dwOutSize);

	boolean NET_DVR_SetConfigFile_EX(NativeLong lUserID, String sInBuffer, int dwInSize);

	//
	boolean NET_DVR_SetLogToFile(boolean bLogEnable, String strLogDir, boolean bAutoDel);

	boolean NET_DVR_GetSDKState(NET_DVR_SDKSTATE pSDKState);

	boolean NET_DVR_GetSDKAbility(NET_DVR_SDKABL pSDKAbl);

	boolean NET_DVR_GetPTZProtocol(NativeLong lUserID, NET_DVR_PTZCFG pPtzcfg);

	//
	boolean NET_DVR_LockPanel(NativeLong lUserID);

	boolean NET_DVR_UnLockPanel(NativeLong lUserID);

	boolean NET_DVR_SetRtspConfig(NativeLong lUserID, int dwCommand, NET_DVR_RTSPCFG lpInBuffer, int dwInBufferSize);

	boolean NET_DVR_GetRtspConfig(NativeLong lUserID, int dwCommand, NET_DVR_RTSPCFG lpOutBuffer, int dwOutBufferSize);

	//
	NativeLong NET_DVR_AdapterUpgrade(NativeLong lUserID, String sFileName);

	NativeLong NET_DVR_VcalibUpgrade(NativeLong lUserID, NativeLong lChannel, String sFileName);

	NativeLong NET_DVR_Upgrade_V40(NativeLong lUserID, ENUM_UPGRADE_TYPE dwUpgradeType, String sFileName,
			Pointer lpInBufer, int dwBufferSize);

	// IP,
	boolean NET_DVR_GetLocalIP(byte sIP[], IntByReference pValidNum, ByteByReference pEnableBind);

	boolean NET_DVR_SetValidIP(int dwIPIndex, boolean bEnableBind);

}

// ,PlayCtrl.dll
interface PlayCtrl extends Library {
	PlayCtrl INSTANCE = (PlayCtrl) Native.loadLibrary("PlayCtrl", PlayCtrl.class);

	public static final int STREAME_REALTIME = 0;
	public static final int STREAME_FILE = 1;

	boolean PlayM4_GetPort(NativeLongByReference nPort);

	boolean PlayM4_OpenStream(NativeLong nPort, ByteByReference pFileHeadBuf, int nSize, int nBufPoolSize);

	boolean PlayM4_InputData(NativeLong nPort, ByteByReference pBuf, int nSize);

	boolean PlayM4_CloseStream(NativeLong nPort);

	boolean PlayM4_SetStreamOpenMode(NativeLong nPort, int nMode);

	boolean PlayM4_Play(NativeLong nPort, HWND hWnd);

	boolean PlayM4_Stop(NativeLong nPort);

	boolean PlayM4_SetSecretKey(NativeLong nPort, NativeLong lKeyType, String pSecretKey, NativeLong lKeyLen);
}

// windows gdi ,gdi32.dll in system32 folder, ,
interface GDI32 extends W32API {
	GDI32 INSTANCE = (GDI32) Native.loadLibrary("gdi32", GDI32.class, DEFAULT_OPTIONS);

	public static final int TRANSPARENT = 1;

	int SetBkMode(HDC hdc, int i);

	HANDLE CreateSolidBrush(int icolor);
}

// windows user32 ,user32.dll in system32 folder, ,
interface USER32 extends W32API {

	USER32 INSTANCE = (USER32) Native.loadLibrary("user32", USER32.class, DEFAULT_OPTIONS);

	public static final int BF_LEFT = 0x0001;
	public static final int BF_TOP = 0x0002;
	public static final int BF_RIGHT = 0x0004;
	public static final int BF_BOTTOM = 0x0008;
	public static final int BDR_SUNKENOUTER = 0x0002;
	public static final int BF_RECT = (BF_LEFT | BF_TOP | BF_RIGHT | BF_BOTTOM);

	boolean DrawEdge(HDC hdc, RECT qrc, int edge, int grfFlags);

	int FillRect(HDC hDC, RECT lprc, HANDLE hbr);
}
