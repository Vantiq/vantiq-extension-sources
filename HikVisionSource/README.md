# Overview

The following documentation outlines how to incorporate a HikVision source as part of your project.
This allows a user to construct applications that integrate with a HikVision camera and are able to detect notifications which contains images and information about face, movement, and heat.
The HikExtension source enables sending zoom and movement commands to the camera along with support for other scenarios such as monitoring applications. etc.
The extension creates, as part of handling incoming notification from the camera,
files in a defined folder and the uploading of the file content as events to Vantiq.
The extension source enables control on the names of the different attributes based on their order in the HikVision file and changing the name of or deleting after processing the file.
The extension source can handle multiple files in parallel;
this can be controlled by parameters outlined in the config section.
The extension source can support mutiple cameras in parallel, based on configuration.  

In order to incorporate this Extension Source, you will need to set up your local machine with a client JRE that can connect to your HikVision camera.
Once you have done this, you will need to create the Source in Vantiq.
The documentation
has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your VANTIQ](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

In order to compile correctly, you must download the Hikvision SDK from [*here*](https://www.hikvision.com/en/support/download/sdk/), choosing the Device Network SDK (for Linux 64 bit).
For windows based system, you should download the Device Network SDK (for Windoes 64 bit) as well from the same location.
Make certain to use the correct implementation for the environment within which you are running.

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

* **HikVisionMain** -- The main function for the program. Connects to sources as specified in the configuration file.
* **HikVisionCore** -- Coordinates the connections to Vantiq, responsible for recovery the connection with Vantiq Server
* **HikVisionHandleConfiguration** -- Sets up the source based on Vantiq configuration.
* **HikVision** -- Thie class implememnts the SDK of HikVision Cameras. Supporting multiple cameras, allwoing publish commands and manages the camera notifications.
* **HCNetSDK** -- The implementation of the HikVision jna interface.

## Prerequisite

For windows based systems, one should download the Device Network SDK (for Windows 64 bit) from [*here*](https://www.hikvision.com/en/support/download/sdk/), extract the lib folder, set it access using an environment parameter. 
please refer to `How to run for distribution` section

Define the environment variable **HIKVISION_LOC**.
This should be the directory where the jna.jar and example.jar files are found.
Those need to be extracted from the Device Network SDK (for Linux 64 bit) available as outlined above, under the folder LinuxJavaDemo direcory which included in the downloaded archive.

**NOTE** the above jar files are relevant to windows based system as well. 

The Linux version required the follwong files as well:

* **libhcnetsdk.so**
* **libhpr.so**
* **libHCCore.so**
* **libHCPreview.so** which is in the HCNetSDKCom directory of the lib path of the Device Network SDK (for Linux 64 bit)

**NOTE**  For an easier solution, one might include all the content of the lib directory from Hikvision SDK to the lib directory of extension source, including the subdirectories HCNetSDKCom content in that lib directory

## How to Run the Program

1. Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2. Run `./gradlew HikVisionSource:assemble`.
3. Navigate to `<repo location>/vantiq-extension-sources/HikVisionSource/build/distributions`. The zip and tar files both contain the same files, so choose whichever you prefer.
4. Uncompress the file in the location that you would like to install the program.
5. Run `<install location>/HikVisionSource/bin/HikVisionSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

## Logging

To change the logging settings, edit the logging config file `<install location>/HikVisionSource/src/main/resources/log4j2.xml`, 
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk. ExtensionWebSocketClient".  

## Server Config File

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:

``` 
authToken=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
sources=HikVision1
targetServer=https://dev.vantiq.com/
```

### Vantiq Options

* **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
* **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be removed when read.
* **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Your Vantiq Environment<a name="vantiq" id="vantiq"></a>

An understanding of the Vantiq Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more information.

In order to incorporate this Extension Source, you will need to create the Source Implementation in the Vantiq system.

## Source Implementation

When creating a HikVision Extension source, you must first create the source implementation. This is done by using the [*HikVisionImpl.json*](src/test/resources/HikVisionImpl.json) file found in src/test/resources/HikVisionImpl.json. To make the source type known to Vantiq, use the vantiq cli command

```
vantiq -s <profileName> load sourceimpls <fileName>
```

where <profileName> is replaced by the Vantiq profile name, and <fileName> is the file to be loaded.

(You can, of course, change the source implementation name from that provided in this definition file.)

That completed, you will need to create the Source in the Vantiq system.

## Source Configuration

To set up the Source in the Vantiq system, you will need to add a Source to your project.  Make sure you have properly added a Source Implementation to Vantiq. Once this is complete, you can select HikVision (or whatever you named your Source Definition) as the Source Type.
You will then need to fill out the Source Configuration Document.

The Configuration document may look similar to the following example:

The user must [define the HikVision Source implementation](../README.md#-defining-a-typeimplementation) in Vantiq.
For an example of the definition,
please see the [*hikVisionImpl.json*](src/test/resources/hikVisionImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *HikVisionExample.zip* can be found in the *src/test/resources* directory.

``` 
{
   "general":{
   		"sdkLogPath": "c:/tmp/log",
   		"DVRImageFolderPath": "c:/tmp/Thermo",
   		"VantiqDocumentPath": "public/image",
   		"VantiqResourcePath": "/resources/documents"},
   "cameras": [
      {
         "CameraId": "C1",
         "Enable": "true",
         "DVRIP": "192.168.1.2",
         "DVRPort": "8000",
         "DVRUserName": "admin",
         "DVRPassword": "pass1"
      },
      {
         "CameraId": "c2",
         "Enable": "false",
         "DVRIP": "realhikvisionthermal.dyndns.org",
         "DVRPort": "8000",
         "DVRUserName": "admin",
         "DVRPassword": "pass2"
      },
   ],
   "options": {
      "maxActiveTasks": 3,
      "maxQueuedTasks": 20
   }
}
```

### Configuration

*   **sdkLogPath**: Optional. The folder where the HikVision sdk log files are to be created. Default value `c:\tmp\log`
*   **DVRImageFolderPath**: Optional . The prefix of the file pattern to look for. if not set an empty string will be used as default value `c:/tmp/Thermo`
*   **VantiqDocumentPath**: Optional. Location in Vantiq Document , default is `public/image`
*   **VantiqResourcePath**: Optional. Define the Vantiq resource to be use for the upload (currently documents or images-`/resource/images`) default value : `/resource/documents`
*   **cameras**: list of cameras as source for notifications

### Options Available for camera

For each camera the following properties must be included - all properties are mandatory.

*   **CameraId**: Camera Name to be use for identify event source
*   **Enable**: "True"|"False" -- Disabled cameras will not included in the active camera list  
*   **DVRIP**: TCP address of the current camera
*   **DVRPort**: TCP port of the ucrrent camera
*   **DVRUserName**: username for authentication with the current camera
*   **DVRPassword**: password for authentication with the current camera

## Publish Statements

Using Publish statements one can change the direction and zoom of the Hikvision camera. 

``` 
PROCEDURE TestHikvisionCommands()
var r = {}
var zoomIn = 11 
var zoomOut = 12 
var tiltUp = 21 
var tiltDown = 22
var start = 0 
var stop = 1
var PAN_RIGHT = 24 
var PAN_LEFT = 23 
publish {"cameraId":"c1","command":tiltDown,"state":start} to SOURCE HikVision1 using {body=r}
```

## Messages from the Source

Messages that are sent to the source as Notifications from the camera. These are JSON objects with a similar format to the example above. The example below is the result of notification which include images as well. 

## Running the example

As noted above, the user must define the HikVision Source implementation in the Vantiq system. For an example of the definition, please see the `HikVisionImpl.json` file located in the `src/test/resources directory` .

Additionally, an example project named `HikVisionExample.zip` can be found in the `src/test/resources directory` . 

## Error Messages

Query errors originating from the source will always have the code be the fully-qualified class name with a small descriptor 
attached, and the message will include the exception causing it and the request that spawned it.

The exception thrown by the HikVision Class will always be a VantiqHikVisionException. This is a wrapper around the traditional Exception, and contains the Error Message, and Error Code from the original Exception.

## Licensing

The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
