# Overview

This document outlines how to incorporate a Vantiq Camel Component Source into your project. The Camel Component 
source allows a user to construct Apache Camel applications that interoperate with the Vantiq system. The Camel
Component uses the Apache Camel mechanism to specify the Vantiq connection details.

The documentation has been split into two parts, [Setting Up Your Machine](#machine)
and [Setting Up Your Vantiq](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

Apache Camel, briefly, is organized into a set of _components_ that are used to produce _endpoints_. 
In turn, the _endpoints_ can be used in the context of a _consumer_ or _producer_.
A Camel consumer sits at the start of a Camel route and converts a message from Vantiq
(_e.g._, received from a publish connector operation/publish to a Vantiq source)
into a Camel Exchange that can travel down the route. 
A Camel producer converts the Camel Exchange into a specific message 
that can be sent to Vantiq (_e.g._,
through a notification connector operation or query response).
These are from the Apache Camel point of view, so a _producer_ produces messages 
from the Camel application, and the _consumer_ takes them in _for_ the Camel application.

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
To change the logging settings, edit the logging config file 
`<install location>/camelComponent/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The 
logger name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.
ExtensionWebSocketClient".  

# Setting Up Vantiq <a name="vantiq" id="vantiq"></a>

An understanding of the Vantiq Extension Source SDK is assumed. Please read the 
[Extension Source README.md](../README.md) for more information.

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
correspond to the property names in the object sent from Vantiq. If desired, you change this behavior
by adding the `consumerOutputJsonStream` component endpoint option (see below) with a value of `true`.
When so specified, messages sent to a Vantiq consumer will arrive in a Camel Exchange as a JSON string.

Messages sent to Vantiq from the component will
arrive as Vail objects, where the property names correspond to the Map keys. For cases where the message to be sent
to Vantiq is not structured as a Vail Object (or Java Map), the component will use the property name `stringVal` for
data that can be naturally encoded as a string, and `byteVal` for binary data. In the case of
binary data, the actual data will be a Base64 encoded string.
The underlying communication is JSON, so binary data must be Base64 encoded.

### Structured Headers and Messages <a name="structuredMessages" id="structuredMessages"></a>

By adding the `structuredMessageHeader` component endpoint option with a value of `true` (see below), you can set or 
access the Camel message headers.  When this option is set to `true`, messages sent to or received from the
Camel Component will have two properties: `headers` and `message`, both containing Vail objects. The `message`
property will contain a Vail object where each property corresponds to same-named property in the underlying message.
The `headers` property will contain a Vail object where each property contains the value of the named message header.
If no headers are present in the underlying Camel message, no `headers` property will be present.

A schema type definition for this message structure is available in
`src/main/resources/types/com/vantiq/extsrc/camelcomp/message.json`.

#### Header Duplication

There are numerous components in the Apache Camel universe, and many include Camel Headers that convey the same 
information. For example, a number of components include headers whose name is of the form `Camel<componentn 
name>ContentType` (_e.g._, `CamelAzureServiceBusContentType` to name but one). 

If the Vantiq application wants to deal with these, it does not want to know all possible variations here -- that is,
after all, the purpose of an integration system: to manage these discrepancies.  To ease the handling of these 
situations, the Vantiq Camel Component can duplicate (NOT replace) these header values into alternative names. When 
this is requested, the original set of headers will be sent or received, but the duplicates with values will be sent 
or received as well.

To do this, the Camel system must be configured with a Bean of type `io.vantiq.extsrc.camel.HeaderDuplicationBean`, 
containing a Map<String, String> where the keys of the map represent the header names to be duplicated, and the 
values are the new header names into which the header value will be duplicated. (Note: we do this via a configured 
bean since the number of headers could be large, and having URI of great length is sometimes problematic.)

To use this, add the `headerDuplicationBeanName` component/endpoint option with the value being the name of the bean 
containing the map described above. Note that this header duplication is performed only when the URI parameter 
`structuredMessageHeader` is present with the value `true`. In other cases, it is silently ignored.

For example, using a Java example, if you want to duplicate the `CamelComponentContentType` header into the 
header `VantiqContentType`, we would configure things as follows:

```java
    ...
    HeaderDuplicationBean hdBean = new HeaderDuplicationBean();
    Map<String, String> hdMap = Map.of("CamelComponentContentType", "VantiqContentType");
    hdBean.setHeaderDuplicationMap(hdMap);
    context.getRegistry().bind("someBeanName", hdBean);
    
    // Now, a route endpoint could be defined such as
    String vantiqEndpoint = "vantiq://<host>:<port>/&sourceName=xxx?accessToken=YYY" +
        "?structuredMessageHeader=true" +
        "?headerDuplicationBeanName=someBeanName"
```

When this definition is in place, messages arriving (via the Azure ServiceBus) to that endpoint will contain the 
headers `CamelComponentContentType` & `VantiqContentType` with the same values. If the `CamelComponentContentType` 
header is not present, no duplication will happen.

Note that if multiple headers are specified to duplicate to a single duplicate (e.g., both header foo & bar
are both supposed to be duplicated to the header baz), the results are unpredictable.  Something will happen, 
but the results depend upon the order on which the headers are processed, and may vary from instance to
instance.  Callers should take care to avoid these situations.  There may be cases where components have 
alternatives that are used, both of which are desired to duplicate to the same header, so we allow such a 
definition. But the caller should take care with such definitions.

## Exchanges to Vantiq

The Vantiq Component expects messages in an exchange to arrive in the form of a Java Map.  However, messages arriving
in Json format will be accepted as well.

## Adding a Vantiq Component to an Apache Camel Applications

### URI Format

The URI for the Vantiq connection takes the following form:

```
    vantiq://host[:port]?sourceName=<source name>&accessToken=<access token>[&sendPings=<boolean>] \
    [&failedMessageQueueSize=<int>][&consumerOutputJsonStream=<boolean>][&structuredMessageHeader=<boolean>]
```

### Component Endpoint Options

The endpoint options apply to producer and consumer endpoints.  The following are supported.

* **host** is the host on which the Vantiq server is running
* **port** [optional] is the port on which the server is running
* **sourceName** is the name of the Camel component source to which to connect
* **accessToken** is the access token used for the connection
* **sendPings** [optional] a boolean value indicating whether to periodically ping the Vantiq server (default is false)
* **failedMessageQueueSize** [optional] an integer value indicating how many messages to hold for sending when the 
  connection to the Vantiq server fails.
* **consumerOutputJsonStream** [optional] a boolean value indicating whether messages sent from Vantiq to the Vantiq 
  component (_i.e._, a Vantiq Consumer) should be put into an Apache Camel Exchange as a JSON message.  If 
  false, messages are put into an exchange as a Java Map. Note that these Json messages are wrapped in a Camel 
  _StreamCache_. This matches what adding a _marshal_ step to the route would do, which tends to improve the 
  interoperability with other Camel components.
* **consumerOutputJson** [DEPRECATED, optional] same as **componentOutputJsonStream** but does not wrap the output 
  in a Camel _StreamCache_. Use is strongly discouraged. Instances where this is necessary should be reported.
* **structuredMessageHeader** [optional] a boolean value indicating whether messages sent to and from the Vantiq 
  component (Vantiq Consumers and Producers) should be structured as

    ```json
    {
        headers: { ... },
        message: { ... }
    }
    ```  


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
