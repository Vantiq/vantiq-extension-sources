# Overview

This document outlines how to incorporate an FTPClient Source into your project. The FTPClient source allows a user to construct applications that upload or download files from an FTP server, those can be analyzed using a CSV extension source to be received as alerts in Vantiq. 
that is the same for the upload process, the file can be created by the CSVextension source or any other component which creates the file, once the file is created, a command can be issued which will upload it to the FTP server. 

The FTPClient Extension source supports multiple FTP server parallel, that enables a single instance, at a store level, to be responsible for multiple pieces of equipment, scales for example, whose method of communication is FTP. The configuration contains a default section, when a specific server definition is lacking configuration, the equivalent values from the default section will be used instead.

The documentation has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your Vantiq](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **FTPClientMain** -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   **FTPClientCore** -- Coordinates the connections to Vantiq, responsible for managing the connection with Vantiq Server
*   **FTPClientHandleConfiguration** -- Sets up the trigger to the file system for detect and processed new FTPClient file 
*   **FTPClient** -- The class that directly interacts with the file system watch service, detects the file and processes it. 
*   **FTPUtil** -- The basic functionlity requiers for FTP. login, logoff , upload file, download file,upload folder, download folder,
                  check communication and more. 

## How to Run the Program

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew FTPClientSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/FTPClientSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/FTPClientSource/bin/FTPClientSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument. Note that the `server.config` file can be placed in the `<install location>/FTPClientSource/serverConfig/server.config` or `<install location>/FTPClientSource/server.config` locations.

## Logging
To change the logging settings, edit the logging config file `<install location>/FTPClientSource/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File
(Please read the [SDK's server config documentation](../extjsdk/README.md#serverConfig) first.)

### Vantiq Options

* **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
* **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be removed when read.
* **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Vantiq <a name="vantiq" id="vantiq"></a>

An understanding of the Vantiq Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more information.

In order to incorporate this Extension Source, you will need to create the Source Implementation in the Vantiq system.

## Source Implementation

When creating a FTPClientSource Extension source,
you must first create the source implementation.
This is done by using the `FTPClientImpl.json` file found in `src/test/resources/FTPClientImpl.json`.
To make the source type known to Vantiq, use the `vantiq` cli command

```
vantiq -s <profileName> load sourceimpls <fileName>
```

where `<profileName>` is replaced by the Vantiq profile name, and `<fileName>` is the file to be loaded.

(You can, of course, change the source implementation name from that provided in this definition file.)

That completed, you will need to create the Source in the Vantiq system. 

## Source Configuration

To set up the Source in the Vantiq system, you will need to add a Source to your project. Make sure you have properly added a Source Implementation to Vantiq. Once this is complete, you can select FTPClient (or whatever you named your Source Implementation) as the Source Type. You will then need to fill out the Source Configuration Document.

The Configuration document may look similar to the following example:

```{
   "ftpClientConfig": {
      "name":"defualt",
      "server": "192.168.1.187",
      "port": 21,
      "username": "user1",
      "password": "pass1",
      "remoteFolderPath": "/bizerba/edv/work",
      "localFolderPath":"c:/tmp",
      "connectTimeout":1000, 
      "ageInDays":10,
      "servers":[
      	{
        "name":"scale01",
        "server":"192.168.1.187",
        "enable" : "true"
        },
      	{
        "name":"scale02",
        "server":"192.168.1.188",
        "enable" : "false"
        }
      ]
   },
   "options": {
      "maxActiveTasks": 2,
      "maxQueuedTasks": 4,
      "processExistingFiles": true,
      "extensionAfterProcessing": "done",
      "deleteAfterProcessing": false,
      "pollTime": 100000
   }
```

### Configuration
*   **name**:Required. The name of the set, default name for the default configuration section, each specific section must be named. 
*   **server**: Required. the ip address of the ftp server. 
*   **port**: Required. the port address (usually 21 for regular FTP) 
*   **username**: Required. login user for the FTP server
*   **password**: Required. login password for the FTP Server
*   **remoteFolderPath**: Required, default remote folder in the FTP server
*   **localFolderPath**: Required, default local folder at host filesystem. 
*   **ageInDays**: Requierd, default age of file in days, older files will be deleted in a cleaning operation
*   **connectTimeout**: Required, connection timeout. 
*   **enable**: Optional, disable flag for a specific FTP configuration entry. 

### Execution Options

* **maxActiveTasks**: Optional. The maximum number of threads running at any given point. This is the number of FTPClient files being processed simultaneously. This value must be a positive integer. Default value is 5.
* **maxQueuedTasks**: Optional. The maximum number of queued tasks at any given point for FTPClient files, overflowing that number might cause some new files to be missed. This value must be a positive integer. Default value is 10.
* **processExistingFiles**: Optional. If set to `true`, the service will process all files already existing in the folder `fileFolderPath` (filtered using `filePrefix` and the `fileExtension`). Otherwise the service will process only new files.  Default is`false`.
* **extensionAfterProcessing**: Optional. Rename the file after it has been processed to avoid reprocessing (_e.g._ for cases where `processExistingFiles` is set to `true`).  The default value is combination of the 'fileExtension' and `done`.  For example `.FTPClient.done` when `fileExtension` set to `.FTPClient`.
* **deleteAfterProcessing**: Optional. Delete the processed file only if processed successfully to avoid reprocessing in cases where `processExistingFiles` is set to `true`. Default value is `false`.
* **pollTime**: Optional. Default is 30000 milliseconds. The frequency at which the connector checks the target folder for new files to process.

**Note**: the sum of **maxActiveTask** and **maxQueuedTasks** is the maximum number of files that can be processed simultaneously.
If more than this number is attempted,
they will be ignored.

## Messages from the Source

currentlty there are no notifications from the ftp extension source, in case of downoad files as part of the background process, it is expected that the CSV extension source will handle the case.

## Running the example

As noted above, the user must [define the FTPClient Source implementation](../README.md#-defining-a-typeimplementation) in the Vantiq system.
For an example of the definition, 
please see the [*FTPClientImpl.json*](src/test/resources/FTPClientImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *FTPClientExample.zip* can be found in the *src/test/resources* directory, together with *ejesmall.FTPClient* which is a FTPClient file with structure relates to the FTPClientExample project types.

* It should be noted that this example looks for FTPClient files in folder c:\tmp.
You will probably need to change this for your environment.

## getting and putting FTPClient Files

FTPCllient can initiate upload (put) or download(get) ftp commands using vail. 
Upload, download, remove, check Communication is done via a **select** statement.

Given a VAIL object defined as follows,this one will check communication (a succesful login process) using the information suppied form the 'scale01' specific segment. 

```
var m = {}
m.name = "scale01"
select * from source FTP1 with body = m ,op="checkcomm" 
```
The following select statement will upload the file to the ftp server, the IP addrsss, port and credentials will be taken from the relevant entry in the configuration file.
```
var m = {}
m.local = "d:\\tmp\\bizerba\test.txt"
m.remote = "/bizerba/etc"
m.name = "scale01"
select * from source FTP1 with body = m , op="upload"
```
The following select statement will download all the files in the folder '/bizerba/etc' from the ftp server to local folder 'd:\tmp\bizerba\out', the IP addrsss, port and credentials will be taken from the relevant entry in the configuration file.
```
var m = {}
m.remote = "/bizerba/etc" 
m.local = "d:\\tmp\\bizerba\\out"
m.name = "scale01"
select * from source FTP1 with body = m , op="download"
```
To remove old files, whose age is above 10 days, the following select statement can be used. 
```
var m = {}
m.remote = "/bizerba/etc" 
m.ageInDays = 10
m.name = "scale02"
select * from source FTP1 with body = m , op="clean"
```
For all select statements, a response will be returned with the following structure:
```
[
   {
      "message": "communication extablished Successfully",
      "value": "[scale01] 192.168.1.187:21",
      "code": "io.vantiq.extsrc.FTPClientsource.success"
   }
]
```
code table is
io.vantiq.extsrc.FTPClientsource.success - success .
io.vantiq.extsrc.FTPClientsource.communicationFailure - no communication to requested server
io.vantiq.extsrc.FTPClientsource.uploadFolderFailure - upload folder failure
io.vantiq.extsrc.FTPClientsource.cleanFolderFailure - clean folder failure
io.vantiq.extsrc.FTPClientsource.downloadFolderFailure - download folder failure

## Error Messages

Parsing FTPClient errors originating from the source will always have the code be the fully-qualified class name with a small descriptor 
attached,
and the message will include the exception causing the problem and the originating request.

The exception thrown by the FTPClient Class will always be a VantiqFTPClientException.
This is a wrapper around the traditional exception, and contains the Error Message,
and Error Code from the original Exception.

## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must add properties to your _gradle.properties_ file in the _~/.gradle_ directory. These properties include the Vantiq server against which the tests will be run.

One can control the configuration parameter for the testing by customizing gradle.properties file. The following shows what the gradle.properties file should look like:

```
    TestVantiqServer=<yourVantiqServer>
    TestAuthToken=<yourAuthToken>
    EntServerIPAddress=<yourFtpServerAddress>
    EntIPPort=<yourFtpServerPort>
    EntUsername=<yourUsername>
    EntPassword=<yourPassword>
    EntLocalFolderPath=<yourLocalPath>
    EntRemoteFolderPath=<YourDefautFTPPath>
```

possible set of values might be:

```
    EntServerIPAddress="127.0.0.1"
    EntIPPort=21
    EntUsername="user"
    EntPassword="pass"
    EntLocalFolderPath="c:/tmp"
    EntRemoteFolderPath="/"
```

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

HikariCP, okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
