# Overview

This document outlines the Test Connector. This connector is used primarily for testing the Connector Deployment tools 
in the Vantiq server, but it is also a reasonable model for creating new connectors. It offers the ability to retrieve 
data from files or environment variables, which can be done either via publish/select requests, or by setting source 
configurations to poll for data from files.

To incorporate this Enterprise Connector (also known as an Extension Source), you will need to create the Source in the 
Vantiq system. The documentation has been split into two parts, [Setting Up Your Machine](#machine) and 
[Setting Up Your Vantiq Source](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) 
for more information.

The user must [define the TestConnector Source implementation](../README.md#-defining-a-typeimplementation) in the 
Vantiq IDE. For an example of the definition, please see the 
[*testConnectorImpl.json*](src/test/resources/testConnectorImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *testConnectorExample.zip* can be found in the *src/test/resources* directory.

*   To activate the polling feature, make sure to include both the "filenames" and "pollingInterval" source
configurations.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **TestConnectorMain** -- The main function for the program. Connects to sources as specified in the configuration 
file.
*   **TestConnectorCore** -- Does the work of reading data from files or environment variables, and sends it back to 
Vantiq.
*   **TestConnectorHandleConfiguration** -- Processes the source configuration and calls the code to poll for data from 
files if configured to do so.

## How to Run the Program

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew testConnector:assemble`.
3.  Navigate to `<repo location>/vantiq-extension-sources/testConnector/build/distributions`. The zip and tar files both 
contain the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/testConnector/bin/testConnector` with a local server.config file or specifying the 
[server config file](#serverConfig) as the first argument. Note that the `server.config` file can be placed in the 
`<install location>/testConnector/serverConfig/server.config` or `<install location>/testConnector/server.config` 
locations.

## Logging
To change the logging settings, edit the logging config file `<install location>/testConnector/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File
(Please read the [SDK's server config documentation](../extjsdk/README.md#serverConfig) first.)

### Vantiq Options
*   **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Your Vantiq Source <a name="vantiq" id="vantiq"></a>

## Source Configuration

To set up the Source in the Vantiq system, you will need to add a Source to your project. Please check the [Prerequisites](#pre) 
to make sure you have properly added a Source Implementation definition to your Vantiq namespace. Once this is complete, 
you can select TestConnector (or whatever you named your Source Implementation) as the Source Type. You will then need 
to fill out the Source Configuration Document.

The Configuration document may look similar to the following example:

    {
       "testConfig": {
          "general": {
             "filenames": ["file1.txt", "file2.txt", "file3.txt"],
             "pollingInterval": 5000
          }
       }
    }

### Options Available for testConfig
**Note:** The "testConfig" and "general" portions must be included in the source configuration, but they can be left
empty.
*   **filenames**: Optional. A list of String filenames from which to read data. The data will be sent back to Vantiq
as a notification arriving on the source.
*   **pollingInterval** Required if a list of `filenames` is provided. This parameter specifies the polling interval 
 (in milliseconds) for reading data from the list of `filenames`. If not specified, then no polling of data will occur.

Read the [Publish Statements](#publish) section to see how to process the returned data.

## Select Statements

Both Select and Publish statements can be used to retrieve data from files or environment variables. Using select 
statements will return the results (or any errors) directly do the caller. To retrieve data, the select statement must 
include a `WITH` clause that contains a `filenames` and/or an `environmentVariables` parameter. At least one of these 
parameters must be provided. Both parameters are defined as lists of Strings, representing filenames or environment 
variable names, respectively.

The following example uses a Vail Select Statement to retrieve data from files and environment variables:
```
PROCEDURE queryTestConnector()

try { 
    SELECT * FROM SOURCE TestConnector1 as results WITH
        filenames: ["testFile1.txt", "testFile2.txt"],
        environmentVariables: ["MY_ENV_VAR1", "MY_ENV_VAR2"]
    
    var fileResponse = results.files
    var envVarResponse = results.environmentVariables
    
    for (file in fileResponse) {
        // Could do work with object here but we just log as an example
        log.info(file.toString())
    }
    
    for (envVar in envVarResponse) {
        // Could do work with object here but we just log as an example
        log.info(envVar.toString())
    }
} catch (error) {
    // Catching any errors and throwing the exception.
    exception(error.code, error.message)
}
```

### Query Response Format
The query will return data for the files and/or environment variables as follows:

```
{
    files: {
        testFile1.txt: "The data in testFile1.txt",
        testFile2.txt: "The data in testFile1.txt"
    },
    environmentVariables {
        MY_ENV_VAR1: "The data in MY_ENV_VAR1",
        MY_ENV_VAR2: "The data in MY_ENV_VAR2"
    }
}
```

## Publish Statements <a name="publish" id="publish"></a>

The publish statements behave almost identically to the select statements, except that the data is sent to Vantiq as a 
notification arriving on the source. This data can be processed using a Rule. The publish request uses the same two 
optional parameters as the select statement.

The following example uses a Vail Publish Statement to retrieve data from files and environment variables:

```
PROCEDURE publishToTestConnector()

var publishParams = {
    filenames: ["testFile1.txt", "testFile2.txt"],
    environmentVariables: ["MY_ENV_VAR1", "MY_ENV_VAR2"]
}

PUBLISH publishParams TO SOURCE TestConnector1
```

The following example defines a Rule that could be used to process the data returned by the previous Publish example:
```
RULE listenFromTestConnector

WHEN EVENT OCCURS ON "/sources/TestConnector1" as sourceEvent

// Grab the actual value from the event
var message = sourceEvent.value

var fileResponse = message.files
var envVarResponse = message.environmentVariables

for (file in fileResponse) {
    // Could do work with object here but we just log as an example
    log.info(file.toString())
}

for (envVar in envVarResponse) {
    // Could do work with object here but we just log as an example
    log.info(envVar.toString())
}
```

### Publish Response Format
The response for publish requests should be identical to that of the select statements, except the data will arrive as 
a notification from the source.

## Error Messages

Query errors originating from the connector will include the appropriate fully-qualified class name with a small 
descriptor attached, and the message will include the exception or invalid request that caused it.

## Testing <a name="testing" id="testing"></a>

To run all the tests, you must add two properties to your _gradle.properties_ file in the _~/.gradle_ directory. These 
properties specify an existing file and environment variable that can be used for testing. The properties can be set in 
as the `gradle.properties` file as follows:
```
    TestConnectorFilename=<putFilenameHere>
    TestConnectorEnvVarName=<putEnvVarNameHere>
```

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
