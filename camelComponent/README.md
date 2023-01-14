# Overview

This document outlines how to incorporate a Vantiq Camel Component Source into your project. The Camel Component source allows a user to construct Apache Camel applications that interoperate with the Vantiq system. The Camel Component uses the Apache Camel
mechanism to specify the Vantiq connection details.

The documentation has been split into two parts, [Setting Up Your Machine](#machine)
and [Setting Up Your Vantiq](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **VantiqComponent** -- The base definition of the Vantiq Apache Camel Component.
*   **VantiqConsumer** -- The part of the component that receives messages from Vantiq.
*   **VantiqEndpoint** -- The base connecting the Apache Component infrastrucure to Vantiq.
*   **VantiqProducer** -- The part of the component that can send messages to Vantiq.

## How to Run the Program

### Method 1 -- Directly from/as an Apache Camel application

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew camelComponent:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/camelComponent/build/libs`.
5.  Use the camelComponent jar file to build your Apache Camel Application.  See the [Apache Camel documentation](https://camel.apache.org) for details.

### Method 2 -- Use the Vantiq Apache Camel Connector.

The Vantiq Camel Connector simplifies the configuration and construction of an Apache Camel application.
See the [Vantiq Camel Connector documentation](../camelConnector/README.md) for information.

## Logging
To change the logging settings, edit the logging config file `<install location>/camelComponent/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

# Setting Up Vantiq <a name="vantiq" id="vantiq"></a>

An understanding of the Vantiq Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more information.

In order to incorporate this Extension Source, you will need to create the Source Implementation in the Vantiq system.

## Source Implementation

When creating a Camel Component Extension source,
you must first create the source implementation.
This is done by using the `camelComponentImpl.json` file found in `src/test/resources/camelComponentImpl.json`.
To make the source type known to Vantiq, use the `vantiq` cli command

```
vantiq -s <profileName> load sourceimpls <fileName>
```

where `<profileName>` is replaced by the Vantiq profile name, and `<fileName>` is the file to be loaded.

(You can, of course, change the source implementation name from that provided in this definition file.)

That completed, you will need to create the Source in the Vantiq system. 

## Source Configuration

To set up the Source in the Vantiq system, you will need to add a Source to your project. Make sure you have properly
added a Source Implementation to Vantiq. Once this is complete, you can select CAMEL_COMPONENT (or whatever you named
your Source Implementation) as the Source Type. No configuration information is required.

## Messages from the Source

Messages that are sent to the component will arrive in a Camel Exchange as a Java Map, where the keys in the map
correspond to the property names in the object sent from Vantiq.  Messages sent to Vantiq from the component will
arrive as Vail objects, where the property names correspond to the Map keys.

## Adding a Vantiq Component tp an Apache Camel Applications

### URI Format

The URI for the Vantiq connection takes the following form:

```
    vantiq://host[:port]?sourceName=<source name>&accessToken=<access token>[&sendPings=<boolean>][&faileMessageQueueSize=<int>]
```

### Component?Endpoint Options

The endpoint options apply to producer and consumer endpoints.  The following are supported.

* **host** is the host on which the Vantiq server is running
* **port** [optional] is the port on which the server is running
* **sourceName** is the name of the Camel component source to which to connect
* **accessToken** is the access token used for the connection
* **sendPings** [optional] a boolean value indicating whether to periodically ping the Vantiq server (default is false)
* **failedMessageQueueSize** [optional] an integer value indicating how many messages to hold for sending when the connection to the Vantiq server fails.

For example, to create an Apache Camel application that receives messages from a Vantiq system at `vantiq.example.com`
from source `exampleSource` and access token `FD10s0x...`, we would use something like the following:

```js
    from("vantiq://vantiq.example.com?accessToken=FD10s0x...&sourceName=exampleSource")
        .to("log:INFO")
```

(expressed here using the Apache Camel Java DSL).

## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must add properties to your _gradle.properties_ file in the _~/.gradle_
directory (or to the gradle command line).
These properties include the Vantiq server against which the tests will be run.

One can control the configuration parameter for the testing by customizing gradle.properties file.
The following shows what the gradle.properties file should look like:

```
    TestVantiqServer=<yourVantiqServer>
    TestAuthToken=<yourAuthToken>
```

## Licensing

The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

okhttp3, log4j, gson, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j and lombok are licensed under the [MIT License](https://opensource.org/licenses/MIT).  

Apache, Camel, and Apache Camel are trademarks of the Apache Software Foundation and are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). 
