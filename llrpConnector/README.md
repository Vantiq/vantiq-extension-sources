# Overview

This document outlines the Low-Level Reader Protocol (LLRP) Connector. This connector is used to connect with an 
RFID Readers supporting the LLRP Standard version 1.1.

To incorporate this Enterprise Connector (also known as an Extension Source), you will need to create one or more
Sources in the Vantiq system. Each Vantiq Source will connect to **one** RFID Reader. The Vantiq Source configuration 
contains the hostname of the RFID Reader.  Though, this Enterprise Connector may connect to multiple RFID Readers by 
by creating multiple Vantiq Source(and associated Vantiq Source). The documentation has been split into two parts, [Setting Up Your Machine](#machine) and 
[Setting Up Your Vantiq Source](#vantiq).

This connector will only connect to the RFID Reader upon successful connection to the Vantiq
Source.  If the Vantiq Source is disabled or disconnected, the connection the associated RFID
Reader will also be closed.  This is done primarily to allow the RFID Reader to buffer all Tag Reads that cannot be 
sent to the Vantiq Source.

# Prerequisites <a name="pre" id="pre"></a>

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) 
for more information.

A general understanding of the [LLRP standard v1.1](https://www.gs1.org/standards/epc-rfid/llrp/1-1) and specifics to 
the desired RFID Reader is recommended.  Additional developer information is provided in the
[LLRP Toolkit](http://llrp.org/index.html), which also point to the LLRP Tool Kit [javadoc](http://llrp.org/docs/javaapidoc/).

The user must [define the LLRP Source implementation](#vantiq) in the 
Vantiq IDE. For an example of the definition, please see the 
[*llrpConnectorImpl.json*](src/test/resources/llrpConnectorImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *llrpConnectorExample.zip* can be found in the *src/test/resources* directory.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **LLRPConnector** -- The main interface to the RFID reader based on the LLRP version 1.1 of the standard. Connection
    to the RFID Reader is only made after a successful connection to the Vantiq source.
*   **LLRPConnectorMain** -- The main function for the program. Connects to sources as specified in the configuration 
file.
*   **LLRPConnectorCore** -- Does the work of reading RFID tags, and send the ID back to 
Vantiq.
*   **LLRPConnectorHandleConfiguration** -- Processes the source configuration and calls the code to poll for data from 
files if configured to do so.

## How to Run the Program

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew llrpConnector:assemble`.
3.  Navigate to `<repo location>/vantiq-extension-sources/llrpConnector/build/distributions`. The zip and tar files 
    both contain the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/llrpConnector/bin/llrpConnector` with a local server.config file or specifying the 
[server config file](#serverConfig) as the first argument. Note that the `server.config` file can be placed in the 
`<install location>/llrpConnector/serverConfig/server.config` or `<install location>/llrpConnector/server.config` 
locations.

## Logging
To change the logging settings, edit the logging config file `<install location>/llrpConnector/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File
An understanding of the Vantiq Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md)
for more information.

### Vantiq Options
*   **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   **sources**: A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   **targetServer**: Required. The Vantiq server hosting the sources (e.g., "dev.vantiq.com").

# Setting Up Your Vantiq Source <a name="vantiq" id="vantiq"></a>

In order to incorporate this Extension Source, you will need to create the Source Implementation in the Vantiq system.

## Source Implementation

When creating a Vantiq source using an LLRPConnector Extension source, you must first load a source implementation.
This is done by using the `llrpConnectorImpl.json` file found in `src/test/resources/llrpConnectorImpl.json`.
To make the source type known to Vantiq, use the `vantiq` cli command

```
vantiq -s <profileName> load sourceimpls <fileName>
```

where `<profileName>` is replaced by the Vantiq profile name, and `<fileName>` is the file to be loaded.

Once loaded, you will be able to create the Source in the Vantiq system.

## Source Configuration <a name="sourceConfig" id="sourceConfig"></a>

To set up the Source in the Vantiq system, you will need to add a Source to your project. Please check the [Prerequisites](#pre) 
to make sure you have properly added a Source Implementation definition to your Vantiq namespace. Once this is complete, 
you can select LLRP (or whatever you named your Source Implementation) as the Source Type. You will then need 
to fill out the Source Configuration Document.

The Configuration document may look similar to the following example:

    {
       "llrpConfig": {
          "general": {
             "hostname": "fx7500fcc3e9",
             "readerPort": 5084,
             "tagReadInterval": 500,
             "logLevel": "debug"
          }
       }
    }

### Options Available for llrpConfig
**Note:** The "llrpConfig" and "general" portions must be included in the source configuration.
*  **hostame** - Required. The hostame or IP Address of the RFID Reader
*  **readerPort** - Required. The readerPort to connect to the RFID Reader, which the LLRP protocol specification 
   specifies port 5084 is used for data transfer.
*  **tagReadInterval** - Optional. Interval in milliseconds to receive collected Tag Data from the RFID Reader. 
   (Default: 500)
*  **logLevel** - Optional. Indicates log level for messages to be sent to Vantiq ('info', 'warn', 'error', 'debug' 
   are allowed). (Default: send no log messages)
   
## Notification Messages Sent To Vantiq Source
Once a connection is successfully made, messages will automatically be sent to Vantiq based on the Vantiq Source 
configuration properties.

### Log Messages

Receipt of any log messages sent from the LLRP Connector will be based on the `logLevel` property in the [Vantiq Source 
Configuration](#sourceConfig).  This property is optional and if not specified no messages will be sent to the Vantiq 
Source. The following log level values are supported:
* **error** - Unexpected error conditions
* **warn** - Disconnection information
* **info** - Successful connection information
* **debug** - General messages sent/received to/from the RFID Reader

The `loglevel` is hierarchical in that specififying a lower level will send all log levels up to the desired level.  
For example, specifying `info` will send `error`, `warn`, and `info` log messages.

The JSON format of a Log message contains the following properties:
* **eventType** - Either `errorLog`, `warnLog`, `infoLog`, or `debugLog`, depending on the specified `logLevel` in the 
  Vantiq source configuration.
* **sourceName** - The Vantiq source name which is used to differentiate when multiple RFID Readers are connected to 
  the same LLRP Connector.
* **hostname** - The RFID Reader's hostname 
* **readerId** - The RFID Reader's ID
* **msg** - Log message provided

Log Message Example:
```
{   "eventType": "errorLog",
    "sourceName": "ZebraRFID",
    "hostname": "fx7500fcc3e9",
    "readerId": "84248dfffffcc3e9",
    "msg": "DEBUG: Sending Reader: GET_READER_CAPABILITIES"
}
```
**Note**: To capture the Reader Capabilities and Reader Configuration, set the `logLevel` to `debug`.  This information 
can be used when using a new RFID Reader and want to understand the capabilities of the RFID Reader and configuration 
settings it was set up to use.

### Reader Status Message

Whenever the connection between the LLRP Connector and the RFID Reader changes, a Reader Status message is sent to the 
Vantiq Source.

The JSON format of Reader Status message contains the following properties:
* **eventType** - `readerStatus`
* **readerId** - The RFID Reader's ID
* **readerOnline** - Boolean value where `True` is `Online` and 'False` is `Offline`

Reader Status Message Example:
```
{   "eventType": "readerStatus",
    "readerId": "84248dfffffcc3e9",
    "readerOnline": True
}
```
### Reader Antenna Data Message

This message is sent during the initial connection of the LLRP Connector to the RFID Reader.

The JSON format of Reader Antenna Data message contains the following properties:
* **eventType** - `readerData`
* **readerId** - The RFID Reader's ID
* **antennaIds** - An array of the potential antenna IDs possible for the RFID Reader.  While an RFID Reader may 
  support multiple antennas, not all ports will have an actual antenna connected to the RFID Reader.`

Reader Antenna Data Message Example:
```
{   "eventType": "readerData",
    "readerId": "84248dfffffcc3e9",
    "antennaIds": [1,2,3,4]
}
```

### RFID Reader Tag Data Message

The RFID Reader Tag Data is sent to the Vantiq Source as the LLRP Connector receives the data from the RFID Reader.  
When the connection is down between the RFID Reader and the LLRP Connector, the RFID Reader caches any Tags found in 
the antenna's Field Of View (FOV). Once reconnected, the cached tags read will be sent to the Vantiq source.

RFID Tags will continually send their ID while charged by the RFID Reader through an antenna.  The RFID Reader will 
count the number of times a tag reports since the last time it was asked to report.  The configured `tagReadInterval` 
value specified in the Vantiq source, identifies the frequency the RFID Reader should report its findings from all 
antennas.  This reported findings is received by the LLRP Connector and the RFID Reader Tag Data Message is sent 
to the Vantiq Source.

The JSON format of RFID Reader Tag Data message contains the following properties:
* **eventType** - `readerData`
* **readerId** - The RFID Reader's ID
* **tags** - An array of the Tags found in the FOV reported by the RFID Reader.  Properties capture include:
  * **tagId** - Tag ID
  * **antennaId** - RFID Reader's antenna ID
  * **firstSeenTimestampUTC** - When the tag was first seen in the FOV by the reporting Antenna. UTC Microseconds.
  * **lastSeenTimestampUTC** - When the tag was last seen in the FOV by the reporting Antenna. UTC Microseconds.
  * **tagSeenCount** - Number of times the tag responded during the `tagReadInterval`.
  * **peakRSSI** - The peak received power of the tag in dBm.
  * **accessSpecID** - As defined in the LLRP specification.
  * **accessSpecIDName** - As defined in the LLRP specification.
  * **ROSpecID** - As defined in the LLRP specification.
  * **ROSpecIDName** - As defined in the LLRP specification.
  * **channelIndex** - As defined in the LLRP specification.
  * **specIndex** - As defined in the LLRP specification.

RFID Reader Tag Data Message Example:
```
{   "eventType": "readerData",
    "readerId": "84248dfffffcc3e9",
    "tags":   [{"tagId":"ca462000045c00000000",
                "antennaId":2,
                "tagSeenCount":18,
                "peakRSSI":-92,
                "firstSeenTime":1615962710571000,
                "lastSeenTime":1615962710571000,
                "ROSpecIDName": "ROSpecID", 
                "accessSpecIDName": "AccessSpecID",
                "specIndex": 1, 
                "ROSpecID": 1,
                "accessSpecID": 0, 
                "channelIndex": 50},
                {"tagId":"ca462000045c00000000",
                "antennaId":3,
                "tagSeenCount":38,
                "peakRSSI":-61,
                "firstSeenTime":1615962711171000,
                "lastSeenTime":1615962711171000,
                "ROSpecIDName": "ROSpecID", 
                "accessSpecIDName": "AccessSpecID",
                "specIndex": 1, 
                "ROSpecID": 1,
                "accessSpecID": 0, 
                "channelIndex": 50}]
}
```

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

xerces-j, mina, common-lang3, ltkjava, okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).

json is licensed under the [JSON License](https://www.json.org/license.html).

jdom is licensed under the [JDOM License](http://jdom.org/dist/binary/archive/jdom-1.0.zip LICENSE.txt).

jargs is licensed under the [JArgs License](https://sourceforge.net/projects/jargs/files/jargs/1.0/jargs-1.0.zip LICENSE.txt).