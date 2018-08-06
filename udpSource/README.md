## Repository Contents

*	[ConfigurableUDPSource](#udpSource) -- The main file that sets up Handlers for ExtensionWebSocketClient and interacts with UDP messages
*	[UDPPublishHandler](#publishHandler) -- A configurable Handler that details how to deal with publish requests
*	[UDPNotificationHandler](#notificationHandler) -- A configurable Handler that details how to deal with UDP messages
*	[MapTransformer](#mapTransformer) -- Shifts contents of Maps and nested Maps
*	[config.sample.json](#serverConfig) -- A sample configuration file for ConfigurableUDPSource

## How to Run ConfigurableUDPSource

1.	Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`
2.	Run `./gradlew udpSource:assemble` or `gradlew udpSource:assemble` depending on your OS.
3.	Then navigate to `<repo location>/vantiq-extension-sources/udpSource/build/distributions`. The zip and tar files both contain the same files, so choose whichever you prefer.
4. Uncompress the file in the location that you would like to install the program.
5. Run either `<install location>/udpSource/bin/udpSource` with a local config.json file or specifying the server config file as the first argument.

To change the logging settings, edit `<install location>/udpSource/logback.xml`. Here is its [documentation](https://logback.qos.ch/manual/configuration.html). The logger names for each class is the class's fully qualified class name, e.g. "io.vantiq.extjsdk.ExtensionWebSocketClient".

## Server Config File<a name="serverConfig" id="serverConfig"></a>

The server config file must be in JSON format. ConfigurableUDPSource runs using either the config file specified as the first argument or the file 'config.json' in the working directory.

### Vantiq Options
*	targetServer -- The Vantiq site that hosts the projects to which the sources will connect. Defaults to "dev.vantiq.com" when not set.
*	authToken -- The authentication token that will allow this server to connect to Vantiq. Be aware that this is namespace specific, so if you intend to connect to sources across several namespaces then multiple config files will be required, each with its own instance of ConfigurableUDPSource. Throws a RuntimeException when not set.
*	sources -- An array containing the names of the sources that will be connected to. Throws a RuntimeException when not set.

### UDP Options
*	defaultBindPort -- Sets the default port to which sources will bind if no other port is specified. Defaults to 3141 when not set
*	defaultBindAddress -- Sets the default address to which the sources will bind if no other address is specified. Attempts to find a valid local address if it cannot find the given address. Typically only localhost and the computer's IP address will work.
*	maxPacketSize -- Sets the maximum number of data bytes that the UDP socket can receive in a single message. Defaults to 1024 when not set.


## Source Configuration Document<a name="udpSource" id="udpSource"></a>

The Configuration document looks as below:

	{
		extSrcConfig:{
			general: {
				<general options>
			}
			incoming: {
				<incoming/Notification options>
			}
			outgoing: {
				<outgoing/Publish options>
			}
		}
	}

### Options Available for General
* listenAddress: Optional. A String representing the address on which UDP messages will be sent and received. Typically only the localhost and the host's assigned IP address will work. Default is set by the server config document.
* listenPort: Optional. The port number on which UDP messages will be sent and received. Default is set by the server config document.

### Options Available for Outgoing<a name="publishHandler" id="publishHandler"></a>
Options for Publishes (a.k.a. outgoing messages) are below. Options for different data types are mutually exclusive.

#### UDP options
These options specify where to send messages to. These are the only options that are required to be set.
* 	targetAddress: Required to send Publishes. A string containing either the URL or IP address to which outgoing UDP messages should be sent
* 	targetPort: Required to send Publishes. An integer that is the port # to which outgoing UDP messages should be sent

#### General Object Options
These options affect data to be sent in JSON(default) or XML. If none of these are set then the resulting object will be empty of data.
* 	passPureMapOut: Optional. A boolean specifying that the Json object in Publish messages should be passed through without changes. Default is false.
* 	passUnspecifiedOut: Optional. A boolean specifying that any values not transformed through the specified transformations should be sent as part of the outgoing message. If no transformations are specified then this is functionally identical to passPureMapOut. Default is false
* 	transformations: Optional. An array of transformations (see [MapTransformer](#mapTransformer)) to perform on the message to be sent. Any values not transformed will not be passed unless passUnspecifiedOut is set to true, and any values that are transformed will not appear in the final message regardless of settings. Default is null

#### XML Options
* 	sendXmlRoot: Optional. The name of the root element for the generated XML object. When set this will send the entire object received as XML. Default is null.

#### CSV Options
These options allow data to be sent in CSV format. Both must be set in order for it to work.
* 	passCsvOutFrom: Optional. A string specifying the location of an array of objects that will be converted into CSV format. Default is null
*	useCsvSchema: Optional. Defines the values that will be sent as CSV. Can be either an array of the names of the values that will be taken from the objects and placed in CSV, or the location in which such an array will be for *all* Publishes. The values in the array will be placed as the header for the CSV document. Default is null

#### Byte/String Options
These options send data out as a string in byte form. They do not include quotation marks unless they are part of the message. These two options are mutually exclusive.
* 	passBytesOutFrom: Optional. The location from which you would like to place the outgoing data. This will take in the String at the location and send it using the byte values of the characters contained within. Default is null.
* 	formatParser: Optional. The settings for sending data as Ascii encoded bytes using printf-style formatting.
    * 	pattern: Required. The printf-style pattern that you wish the data to be sent as. See java.util.Formatter for specifics on what is allowed.
    *	locations: Required. An array of the locations from which the format arguments should be pulled.
    *	altPatternLocation: Optional. The location in a Publish message in which alternate patterns may be placed. If a String is found in the given location of a Publish object, then that pattern will be used in place of pattern. This pattern may be included directly in the Published object, but it is recommended that it is placed in the object specified by the "Using" keyword, for purposes of readability.
    *	altLocations: Optional. The location in a Publish message in which an alternate set of locations may be placed. If an array of Strings is found in the given location of a Publish message, then those locations will be used instead of locations. These locations may be included directly in the Published object, but it is recommended that they are placed in the object specified by the "Using" keyword, for purposes of readability.
	

### Options Available for Incoming<a name="notificationHandler" id="notificationHandler"></a>
Options for Notifications (a.k.a. incoming messages) are as follows. If no options are valid then no Notifications will be sent, but if even one is set then their defaults are used. Options for different data types are mutually exclusive.

#### UDP Options
These select which ports and addresses to accept data from. If none of these are set but other incoming options are, then messages will be accepted from any port and address. Messages can be received from any address and port combination that exists in receiveAddresses and receivePorts, as well as any server specified in receiveServers
* receiveAddresses: Optional. An array of strings containing either the URL or IP addresses from which the source will receive messages. If left empty, it is ignored and the default of receiveAllAddresses is used.
* receiveAllAddresses: Optional. A boolean stating whether you would like to receive Notifications of UDP messages from any address. Overridden by a non-empty receivingAddress. Default is true, unless receiveServers is the only receive option set, i.e receiveServers is non-empty, receivePorts is empty, and receiveAllPorts is not explicitly set to true.
* receivePorts: Optional. An array of the port numbers from which the source will receive messages. If left empty, it is ignored and the default of receiveAllPorts is used.
* receiveAllPorts: Optional. A boolean stating whether you would like to receive Notifications of UDP messages from any port. Overridden by a non-empty receivingPorts. Default is true, unless receiveServers is the only receive option set, i.e receiveServers is non-empty, receiveAddresses is empty, and receiveAllAddresses is not explicitly set to true.
* receiveServers: Optional. An array of pairs that specify both an address and port to receive UDP messages from. A pair is formatted as an array containing first the address as either the URL or IP address, and second the port number. If left empty, defers to the other address and port settings

#### General Object Options
These options affect data received in JSON(default) or XML. If none of these are set then the resulting object will be empty of data. Note that passRecAddress and passRecPort occur for every datatype except CSV, and will overwrite any data already in those locations.
* passPureMapIn: Optional. A boolean specifying that the Json object in Publish messages should be passed through without changes. Default is false.
* passUnspecifiedIn: Optional. A boolean specifying that any values not transformed through the transformations specified in transformations should be sent as part of the outgoing message. If no transformations are specified in transformations then this is identical to passPureMapOut. Default is false
* passRecAddress: Optional. A string representation of the location in which you want the IP address from which the UDP message originated. Default is null, where the address will not be recorded
* passRecPort: Optional. A string representation of the location in which you want the port from which the UDP message originated. Default is null, where the port will not be recorded.
* transformations: Optional. An array of transformations (see [MapTransformer](#mapTransformer)) to perform on the message to be sent. Any values not transformed will not be passed unless passUnspecifiedIn is set to true, and any values that are transformed will not appear in the final message regardless of settings

#### XML Options
These options specify how XML is translated.
* expectXmlIn: Optional. Specifies that the data incoming from the UDP source will be in an XML format. Note that this will throw away the name of the root element. If data is contained in the root element, it will be placed in the location "" before transformations. Default is false.
* passXmlRootNameIn: Optional. Specifies the location to which the name of the root element should be placed. Does nothing if expectXmlIn is not set to true. Default is null.

#### CSV Options
This option specifies how CSV is translated.
* expectCsvIn: Optional. Specifies that the expected UDP data will be in CSV format. Expects that the data will use a header specifying the name of each object. Data will be received as an array of JSON Objects. Default is false.

#### Byte/String Options
These options interpret data as pure bytes or a string in byte form. These two options are mutually exclusive.
* passBytesInAs: Optional. The location to which you would like to place the incoming data. This will take in the raw bytes received from the source and place them as chars of the same value in a String. Default is null.
* regexParser: Optional. The settings to use for parsing the incoming byte data using regex. It contains the following options. 
	* pattern: Required. The regex pattern that will be used to parse the incoming data. The parser will use the first match that appears in the data. See java.util.regex.Pattern for specifics on what constitutes a valid pattern.
	* locations: Required. An array of the locations in which to place the capture groups from pattern.


## <a name="mapTransformer" id="mapTransformer"></a>MapTransformer

MapTransformer is designed to deal with getting and putting for nested Maps. 

### Locations
A location is written as period separated key names starting from the inital map, e.g. "level1.level2.level3" would be the location of "Hello World" in the Map represented by the following JSON object.
```
{
    level1: {
        level2: {
            level3: {
                "Hello World"
            }
        }
    }
}
```

### Transformations
A transformation copies an object from a location in the input Map to a location in the output Map, overwriting anything in the path for the output map. If the input location is invalid or null, nothing is written in the output Map. A transformation is written as an array with the location in the input as the first element and the desired location in the output as the second element.
Example: Using the transformation ["level1.level2", "lvl1.lvl2"] on the input map
```
{
	level1: {
		level2: "Hello World"
	}
}
```
and the output map
```
{
	lvl1: "Sir Not-Appearing-In-This-Result",
	string: "Hello"
}
```
would result in the output map looking like
```
{
	lvl1: {
		lvl2: "Hello World"
	},
	string: "Hello"
}
```
since the value in lvl1 was overwritten by the Map created to contain lvl2 and the transformed value.
<br>
Additionally, if the output map had instead been
```
{
	lvl1: {
		lvl2: {
			lvl3: "Sir Not-Appearing-In-This-Result"
		}
	},
	string: "Hello"
}
```
then the resulting map would be exactly the same, as the Map in lvl2 would have been overwritten by the new value.