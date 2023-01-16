# Overview

The following documentation outlines how to incorporate an Apache Camel Connector Source as part of your project.
This allows a user to 
construct Apache Camel applications that interact with Vantiq.

The Apache Camel Connector constructs the enviroment necessary to run Apache Camel Application.
It uses the [Vantiq Camel Component](../camelComponent/README.md) (if necessary). The building of an Apache Camel
application usually involves building the application, including the dependencies, using the Maven system.
The Vantiq Apache Camel Connector is configured with the Camel application desired, and, using that definition,
discovers, downloads, provisions the connector, and runs the application using the appropriate Apache Camel
Components as specified in the routes included in the configuration.  Within the Vantiq system, the connection point will be a Vantiq Source.

In order to incorporate this Extension Source, you will need to create the
Source in the VANTIQ Modelo IDE. The documentation 
has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your VANTIQ Modelo IDE](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more 
information.

The user must [define the Apache Camel Connector Source implementation](../README.md#-defining-a-typeimplementation)
in the Vantiq system. For an example of the definition, 
please see the [*camelConnectorImpl.json*](src/test/resources/camelConnectorImpl.json) file
located in the *src/test/resources* directory.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

The _connector_ directory ('src/main/java/io/vantiq/extsrc/camelconn/connector`) contains the following files:

*   **CamelMain** -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   **CamelCore** -- Coordinates the connections to the database, and sends the resulting data back to VANTIQ Modelo if
    necessary.
*   **CamelHandleConfiguration** -- Sets up the Apache Camel routes based on the source's configuration document, and
    initializes the queryHandler and publishHandler.

The _discovery_ directory ('src/main/java/io/vantiq/extsrc/camelconnec/discover`) contains a variety of classes
implementing the discovery, resolution, and provisioning of the components necessary to run the Apache Camel
application configured as part of the source.

## How to Run the Program <a name="runtheconnector" id="runtheconnector"></a>

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew camelConnector:assemble`.
3.  Navigate to `<repo location>/vantiq-extension-sources/camelConnector/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
5.  Run `<install location>/camelConnector/bin/camelConnector` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument. Note that the `server.config` file can be placed in the `<install location>/camelConnector/serverConfig/server.config` or `<install location>/camelConnector/server.config` locations.

## Logging
To change the logging settings, edit the logging config file
`<install location>/camelConnector/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File
(Please read the [SDK's server config documentation](../extjsdk/README.md#serverConfig) first.)

### Vantiq Options
*   **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Your VANTIQ Modelo IDE <a name="vantiq" id="vantiq"></a>

## Source Configuration

To set up the Source in the VANTIQ Modelo IDE, you will need to add a Source to your project. Please check the [Prerequisites]
(#pre) to make sure you have properly added a Source Definition to VANTIQ Modelo. Once this is complete,
you can select CAMEL_CONNECTOR
(or whatever you named your Source Definition) as the Source Type. You will then need to provide the
Source Configuration Document.

The Configuration document may look similar to the following example:

```json
    {
       "camelRuntime": {
         "appName": "myCamelAppName",
         "routesDocument": "someVantiqDocName.xml"
       },
      "general": {
      }
    }
```

### Options Available for camelRuntime

*   **appName**: Optional -- name of the app.  If not provided, default to _camelConnectorApp_.
*   **routesDocument** or **routesList** and **routesFormat**: Required. This is list of Camel routes to run.
    * **routesDocument** -- the name of a Vantiq Document containing the XML or YAML definition of the routes to run. The format of the document (XML or YAML) will be determined from the file name (_e.g._, `routes.xml`)
    * **routesList** and **routesFormat** -- the XML or YAML specification of the Camel routes to run.  The **routesList** property should contain the appropriate text, and the **routesFormat** should contain either `xml` or `yml` indicating the style used to specify the routes.
    * *Note*: You must specify either the **routesDocument** or **routesList** _AND_ **routesFormat** properties, but not both.

### Options available for general

*   **componentCacheDirectory** -- (Optional) the name of a directory into which to place the downloaded components for use later.  If absent, defaults to `componentCache`.
*   **componentLibraryDirectory** -- (Optional) The name of the directory into which to place component libraries (and associated dependencies) for running.  It is from here that the required component classes will be loaded.  If not present, defaults to `componentLib`.
*   **repoList** - (Optional) A list of URLs for Maven repositories from which to load components.  If absent, defaults to _maven central_.
*   **additionalLibraries** -- (Optional) A list of libraries to include _in addition to_ those found via discovery. These should be specified as "<group>:<name>:<revision>" triples.

### Defining Camel Routes that include Vantiq

The [Vantiq Camel Component](../camelComponent/README.md) has details on how to define a Vantiq Camel Endpoint.
The Vantiq Camel Component, itself, is not a full-connector -- it uses the connector protocol to
manifest the component.

That said, the Vantiq Camel Connector is a full connector, requiring the `server.config` file 
as outlined [here](#runtheconnector).
In most cases, the Camel Application will contain Vantiq endpoints,
and these endpoints will refer to the Vantiq server specified in the `server.config` file.

Rather than re-specifying this information, the Vantiq Camel Connector provides a mechanism
whereby routes including Vantiq endpoints can refer to the Vantiq specified in the `server.config` file. 
This is done by specifying a Vantiq endpoint
that uses `server.config` as the host name.  That is, specify a route something like the following route
that accepts message from the Vantiq server specified in the connector's `server.config` file and logs the result.

```xml
    <routes xmlns="http://camel.apache.org/schema/spring" >
        <route id="xml-route">/
            <from uri="vantiq://server.config"/>
            <to uri="log:INFO"/>
        </route>
    </routes>
```

## Messages from the Source

Messages are sent to the source as Notifications, and are delivered as _events_ to the associated source.
The data arrives as a VAIL object, where the properties in the event correspond to those sent from the Camel application.
being the column name and the values being the column value. If multiple rows of data are returned by the pollQuery, each row
will be sent as a unique Notification. 

## Select Statements

In order to interact with the Apache application, one option is to use VAIL to select from the source. To do this, you will need 
to specify query message using the WITH clause on the VAIL SELECT statement. The data will be returned to 
VANTIQ as a set of _rows_, where each _row_ contains containsa a set of properties..

The following example uses a Vail Select Statement to **query** an application:
```js
PROCEDURE queryCamel()

try {        
    // Normal SELECT Statement in VAIL, but using WITH Clause is important.
    SELECT ONE * from SOURCE camelConnectorSource as result WITH 
        value1: "some value",
        value2: 35
     
    // We expect a result with a property named "answer"...
    
    var ourAnswer = resul.answer
    
} catch (error) {
    // Catching any errors and throwing the exception.
    exception(error.code, error.message)
}
```

## Publish Statements <a name="publish" id="publish"></a>

Another method to interact with the Camel application is to use VAIL to publish to the source. 
To do this, you will need to send the desired VAIL object to your application using the Publish parameters.
The following VAIL procedure will send data to the Camel application by sending an event to the associated source.

```js
PROCEDURE publishToCamel(valueToSend Integer)
    PUBLISH {value: valueToSend} to SOURCE camelConnectorSource
```

## Error Messages

Query errors originating from the source will include the code and error message.
They may also include the cause (the underlying exception).


## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must add properties to your _gradle.properties_ file in the _~/.gradle_
directory. These properties include the Target VANTIQ Server URL, as well as an Access Token for that server. 
The Target VANTIQ Server and Auth Token will be used to create a temporary VANTIQ Source, VANTIQ Type, 
VANTIQ Topic, VANTIQ Procedure and VANTIQ Rule.

* **NOTE:** We strongly encourage users to create a unique VANTIQ Namespace in order to ensure that tests
accidentally override any existing Sources or Types.

## Licensing

The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

okhttp3, log4j, gson, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j and lombok are licensed under the [MIT License](https://opensource.org/licenses/MIT).  

Apache, Camel, and Apache Camel are trademarks of the Apache Software Foundation and are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). 
