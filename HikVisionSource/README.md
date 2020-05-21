# Overview

The following documentation outlines how to incorporate a HikVision  Source as part of your project. This allows a user to construct applications that detect creation of hikVision files in pre defined folder and upload the file content as events to vantiq. the extension source enable control on the names of the idfferent attribute base on the order in the hikVision file and change the name or delete after procsiing the file. 
The extension source can handle multiple files parralel , this can be controled by parameters in the config section . 

In order to incorporate this Extension Source, you will need to create the Source in the VANTIQ Modelo IDE. The documentation has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your VANTIQ Modelo IDE](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more 
information.

The user must [define the HikVision Source implementation](../README.md#-defining-a-typeimplementation) in the VANTIQ Modelo IDE. For an example of the definition, 
please see the [*hikVisionImpl.json*](src/test/resources/hikVisionImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *HikVisionExample.zip* can be found in the *src/test/resources* directory.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **HikVisionMain** -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   **HikVisionCore** -- Coordinates the connections to Vantiq , responsible for recovery the connection with Vantiq Server
*   **HikVisionHandleConfiguration** -- Sets up the trigger to the file system for detect and processed new hikVision file 
*   **HikVision** -- The class that directly interacts with the file system watch service , detect the file and process it . 

*  **HCNetSDK** -- The implemetation of the hikVoision jna interface. 

## How to Run the Program

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew HikVisionSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/HikVisionSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/HikVisionSource/bin/HikVisionSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit the logging config file `<install location>/HikVisionSource/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:
```
authToken=vadfEarscQadfagdfjrKt7Fj1xjfjzBxliahO9adliiH-Dj--gNM=
sources=HikVision1
targetServer=https://dev.vantiq.com/
```

### Vantiq Options
*   **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Your VANTIQ Modelo IDE <a name="vantiq" id="vantiq"></a>

## Source Configuration

To set up the Source in the VANTIQ Modelo IDE, you will need to add a Source to your project. Please check the [Prerequisites](#pre)  to make sure you have properly added a Source Definition to VANTIQ Modelo. Once this is complete, you can select HikVision (or whatever you named your Source Definition) as the Source Type. You will then need to fill out the Source Configuration 
Document.

The Configuration document may look similar to the following example:
```
{
   "sdkLogPath": "d:/tmp/log",
   "DVRImageFolderPath": "D:/TMP/Thermo",
   "VantiqDocumentPath": "public/image",
   "VantiqResourcePath": "/resource/documents",
   "cameras": [
      {
         "CameraId": "ThomasCamera",
         "Enable": "true",
         "DVRIP": "realhikvisionthermal.dyndns.org",
         "DVRIP9": "92.201.182.251",
         "DVRPort": "8000",
         "DVRUserName": "admin",
         "DVRPassword": "abcd1234"
      },
      {
         "CameraId": "HikVision1",
         "Enable": "false",
         "DVRIP": "123.157.208.25",
         "DVRPort": "48104",
         "DVRUserName": "admin",
         "DVRPassword": "Hik123456"
      }
   ],
   "options": {
      "maxActiveTasks": 2,
      "maxQueuedTasks": 4
   }
}
```

### Options Available for configuration root. 
*   **sdkLogPath**: Optional. the folder where the HikVision sdk log files are going to be created , default value `c:\tmp\log`
*   **DVRImageFolderPath**: Optional . The prefix of the file pattern to look for ,if not set an empty string will be used as default value `c:/tmp/Thermo`
*   **VantiqDocumentPath**: Optional. Location in Vantiq Document , default is `public/image`
*   **VantiqResourcePath**: Optional. define the vantiq resource to be use for the upload (currently documents or images-`/resource/images`) default value : `/resource/documents`
*   **cameras**: list of cameras as source for notifications
### Options Available for camera
for each camera the following properties must be included - all fieds are mandatory 

*   **CameraId**: Camera Name to be use for identify event source .
*   **Enable**: "True"|"False" , disabled camera will not included in the active camera list .  
*   **DVRIP**: TCP address of the current camera
*   **DVRPort**: TCP port of the ucrrent camera
*   **DVRUserName**: username for authentication with the current camera
*   **DVRPassword**: password for authentication with the current camera

## Messages from the Source

Messages that are sent to the source as Notifications are JSON objects in the following format:
```
{
   "CameraId": "HikVision1",
   "EventType": "RuleAlarm",
   "ImageName": "",
   "ThermalImageName": "",
   "Extended": {
      "autoRead": true,
      "autoWrite": true,
      "dwSize": 352,
      "dwRelativeTime": 0,
      "dwAbsTime": 1364287986,
      "struRuleInfo": {
         "autoRead": true,
         "autoWrite": true,
         "byRuleID": 2,
         "byRes": 0,
         "wEventTypeEx": 1,
         "byRuleName": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
         "dwEventType": 1,
         "uEventParam": [
            1060974363,
            1063155401,
            1059531522,
            1059229532,
            0,
            131072,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
         ],
         "pointer": {
            "size": 332,
            "valid": true
         }
      },
      "struTargetInfo": {
         "autoRead": true,
         "autoWrite": true,
         "dwID": 42252,
         "struRect": {
            "autoRead": true,
            "autoWrite": true,
            "fX": 0.591,
            "fY": 0.666,
            "fWidth": 0.158,
            "fHeight": 0.103,
            "pointer": {
               "size": 196,
               "valid": true
            }
         },
         "byRes": "AAAAAA==",
         "pointer": {
            "size": 200,
            "valid": true
         }
      },
      "struDevInfo": {
         "autoRead": true,
         "autoWrite": true,
         "struDevIP": {
            "autoRead": true,
            "autoWrite": true,
            "sIpV4": "MTAuOS45OS4xMDQAAAAAAA==",
            "byRes": "OjoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pointer": {
               "size": 176,
               "valid": true
            }
         },
         "wPort": 8104,
         "byChannel": 1,
         "byIvmsChannel": 1,
         "pointer": {
            "size": 176,
            "valid": true
         }
      },
      "dwPicDataLen": 167236,
      "byPicType": 0,
      "byRes": "AAEA",
      "dwRes": [
         0,
         1,
         0
      ],
      "pImage": null,
      "pointer": {
         "size": 344,
         "valid": true
      }
   }
}
```
The data is formatted as a HashMap which represents a row of data. Each map is a series of key-value pairs with the keys 


## Error Messages

Query errors originating from the source will always have the code be the fully-qualified class name with a small descriptor 
attached, and the message will include the exception causing it and the request that spawned it.

The exception thrown by the HikVision Class will always be a VantiqHikVisionException. This is a wrapper around the traditional Exception, and contains the Error Message, and Error Code from the original Exception.


## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

HikariCP, okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
