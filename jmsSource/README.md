# Overview

The following documentation outlines how to incorporate a JMS Source as part of your project. This allows a user to construct
applications that interact with a JMS Server. These interactions include the ability to produce messages to and consume 
messages from JMS Topics and Queues.

In order to incorporate this Extension Source, you will need to set up your local machine with a JMS Server Driver that can be
used to connect to your JMS Server. Once you have done this, you will need to create the Source in the VANTIQ Modelo IDE. 
The documentation has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your VANTIQ Modelo IDE](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) 
for more information.

The user must [define the JMS Source implementation](../README.md#-defining-a-typeimplementation) in the VANTIQ Modelo IDE. 
For an example of the definition, please see the [*jmsImpl.json*](src/test/resources/jmsImpl.json) file located in the 
*src/test/resources* directory.

Additionally, an example project named *jmsExample.zip* can be found in the *src/test/resources* directory.

*   It should be noted that this example connects to a WebLogic JMS Server.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **JMSMain** -- The main function for the program. Connects to sources as specified in a configuration file.
*   **JMSCore** -- Coordinates the communincation between the VANTIQ IDE and the JMS Server.
*   **JMSHandleConfiguration** -- Sets up the JMS Server connection based on the source's configuration document, and
    initializes the queryHandler and publishHandler.
*   **JMS** -- The class that directly interacts with the JMS Server, executing the query and publish requests as sent
    by the VANTIQ Modelo IDE. Uses classes in the ```io.vantiq.extsrc.jmsSource.communication``` package to produce and 
    consume messages to or from the JMS Queues and Topics.

## How to Run the Program

1.  In order to effectively use the JMS Source, you will need to download the appropriate JMS Driver with respect to the
    JMS Server that you are using. Once you have installed this, you will need to create an environment variable named
    `JMS_DRIVER_LOC` that contains the location of the jar file (*i.e.* `/Users/yourName/somePath/wlthint3client.jar`)
2.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
3.  Run `./gradlew jmsSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/jmsSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/jmsSource/bin/jmsSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit the logging config file `<install location>/jmsSource/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient". 

*   **NOTE**: The log4j2.xml file has commented lines that, if uncommented, will create a log file in the same directory 
named "output.log".

## Server Config File

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:
```
authToken=vadfEarscQadfagdfjrKt7Fj1xjfjzBxliahO9adliiH-Dj--gNM=
sources=JMS1
targetServer=https://dev.vantiq.com/
```

### Vantiq Options
*   **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Your VANTIQ Modelo IDE <a name="vantiq" id="vantiq"></a>

## Source Configuration

To set up the Source in the VANTIQ Modelo IDE, you will need to add a Source to your project. Please check the [Prerequisites]
(#pre) to make sure you have properly added a Source Definition to VANTIQ Modelo. Once this is complete, you can select JMS
(or whatever you named your Source Definition) as the Source Type. You will then need to fill out the Source Configuration 
Document.

The Configuration document may look similar to the following example:
```
  {
     "jmsConfig": {
        "general": {
           "username": "myUsername",
           "password": "myPassword",
           "providerURL": "t3://localhost:7001",
           "connectionFactory": "com.namir.weblogic.base.cf",
           "initialContext": "weblogic.jndi.WLInitialContextFactory"
        },
        "sender": {
           "queues": [
              "queue1", "queue2"
           ],
           "topics": [
              "topic1", "topic2"
           ],
           "messageHandler": {
              "queues": {
                  "queue1": "my.custom.message.handler"
              },
              "topics": {
                  "topic2": "another.custom.message.handler"
              }
           }
        },
        "receiver": {
           "queues": [
              "queue2", "queue3"
           ],
           "queueListeners": [
              "queue1", "queue4"
           ],
           "topics": [
              "topic2", "topic3"
           ],
           "messageHandler": {
              "queues": {
                  "queue3": "my.custom.message.handler"
              },
              "queueListeners": {
                  "queue1": "different.custom.message.handler"
                  "queue2": "another.custom.message.handler"
              }
              "topics": {
                  "topic2": "yet.another.message.handler"
              }
           }
        }
     }
  }
```
### Options Available for jmsConfig
*   **username**: Optional. The username that will be used to connect to the JMS Server. If JMS Server is not password 
protected, then do not include username or password.
*   **password**: Optional. The password that will be used to connect to the JMS Server. If JMS Server is not password 
protected, then do not include username or password.
*   **providerURL**: Required. The URL corresponding to the JMS Server that you will connect to.
*   **connectionFactory**: Required. The name of the JMS Connection Factory used to create JMS Sessions, which in turn create 
JMS Message Producers/Consumers/Listeners.
*   **initialContext**: Required. The fully-qualified class name (FQCN) of the ```InitialContextFactory``` used to lookup the
```ConnectionFactory``` in the JNDI.

### Options Available for sender
**NOTE**: sender map is REQUIRED, but the following values are optional.

*   **queues**: Optional. A list of the queues that will be configured to send messages to.
*   **topics**: Optional. A list of the topics that will be configured to send messages to.
*   **messageHandler**: Optional. A map containing two sub-maps: *queues* and *topics*. These sub-maps should contain the name 
of a given queue or topic as a key, and the fully qualified class name of a custom Message Handler as the value. Creating a 
custom Message Handler is explained later in this document. This is completely optional, and if no custom message handlers are
specified, then the BaseMessageHandler will be used.

### Options Available for receiver
**NOTE**: receiver map is REQUIRED, but the following values are optional.

*   **queues**: Optional. A list of the queues that will be configured to receive messages. Messages from this list of
queues will only be read by querying the source from the VANTIQ IDE, which is explained later in this document.
*   **queueListeners**: Optional. A list of the queues that will be configured to receive messages. Messages from this 
list of queues will be read using a Message Listener, which will send the messages back to the source as a notification.
*   **topics**: Optional. A list of the topics that will be configured to receive messages. Messages from topics will be read 
using a Message Listener, which will send the messages back to the source as a notification.
*   **messageHandler**: Optional. A map containing three sub-maps: *queues*, *queueListeners*, and *topics*. These sub-maps 
should contain the name of a given queue or topic as a key, and the fully qualified class name of a custom Message Handler as 
the value. Creating a custom Message Handler is explained later in this document. This is completely optional, and if no custom message handlers are
specified, then the BaseMessageHandler will be used.

## Messages from the Source

Messages that are sent to the source as Notifications from either a queueListener or a topic are JSON objects in the following 
format:
```
{
    JMSMessage:<theReceivedMessage>, 
    JMSFormat:<theJMSMessageFormat>, 
    JMSDestination:<queueName/topicName>
}
```

The following example shows a rule that could be used to process incoming notifications to a JMS Source:

```
RULE jmsMessageListener

WHEN MESSAGE ARRIVES FROM JMS1 as msg
var myObj = {}
myObj.JMSMessage = msg.message
myObj.JMSFormat = msg.JMSFormat
myObj.JMSDestination = msg.destination
INSERT JMSMessageType(myObj)
```

## Select Statements

In order to read messages from a queue, (**NOT** a queueListener), a VAIL Select statement must be used. The Select statement 
must have two query parameters: *operation* and *queue*. Currently there is only one *operation* that is supported, which is 
the "read" operation. In the future, there may be different Select Operations. The *queue* parameter is the name of the queue
from which to read. The following is an example of a Procedure created in VANTIQ Modelo querying against a JMS Source:

```
PROCEDURE readMessageFromQueue()

SELECT * FROM SOURCE JMS1 AS msg WITH
	operation: "read",
	queue: "NamirJMSServer-0/NamirSystemModule-0!NamirJMSServer-0@/com/namir/weblogic/base/dq"
  
  {
      var myObj = {}
      myObj.JMSMessage = msg.message
      myObj.JMSFormat = msg.JMSFormat
      myObj.JMSDestination = msg.destination
      INSERT JMSMessageType(myObj)
  }
```

*   **NOTE**: Messages returned by a Select statement are in the exact same format as those sent to the source as a Notification.

## Publish Statements

In order to send messages to either a queue or a topic, a VAIL Publish statement must be used. The Publish statement will have
three parameters: *message*, *queue/topic*, *JMSFormat*. The *message* parameter is simply the message to be sent. The next
parameter is either *queue* or *topic*, depending on which type of destination you are sending the message to. The value of this
parameter is the name of the given queue/topic. Finally, the last parameter is the *JMSFormat*, which specifies the JMS Message
Type that will be used to send the message. If no custom Message Handler was implemented, the three values that we currently
support are "TextMessage", "MapMessage", and "Message". The "Message" type does not have any message body, so no message parameter
is needed. The following two examples show how to send all three supported message types.

### Sending Text Message ###

```
PROCEDURE sendTextMessage()

var msg = "A message sent from VANTIQ to a topic."
var dest = "NamirJMSServer-0/NamirSystemModule-0!NamirJMSServer-0@/com/namir/weblogic/base/dt"
var msgFormat = "TextMessage"

PUBLISH {message: msg, topic: dest, JMSFormat: msgFormat} to SOURCE JMS1 
```

### Sending Map Message ###
```
PROCEDURE sendMapMessage()

var msg = {}
msg.age = 22
msg.name = "Namir"
var dest = "NamirJMSServer-0/NamirSystemModule-0!NamirJMSServer-0@/com/namir/weblogic/base/dq"
var msgFormat = "MapMessage"

PUBLISH {message: msg, queue: dest, JMSFormat: msgFormat} to SOURCE JMS1 
```

### Sending Message ###
```
PROCEDURE sendMapMessage()

var dest = "NamirJMSServer-0/NamirSystemModule-0!NamirJMSServer-0@/com/namir/weblogic/base/dq"
var msgFormat = "Message"

PUBLISH {queue: dest, JMSFormat: msgFormat} to SOURCE JMS1 
```

## Custom Message Handlers ##

To create custom Message Handler, you must implement the 
```io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface.java``` interface. We recommend creating a
subclass of the BaseMessageHandler class, and adding extra message formatting for the JMS Message Types that we do not 
currently support. Once you have implemented the MessageHandlerInterface, you must add the class to the java classpath. If we 
cannot find the class in the java classpath, or if it is not a correct implementation of the interface, then we will not be
able to create a message producer/consumer/listener for the given queue or topic.

## Error Messages

Query errors originating from the source will always have the code be the fully-qualified class name with a small descriptor 
attached, and the message will include the exception causing it and the request that spawned it.

## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must create an environment variable named JMS\_DRIVER\_LOC which points to the 
appropriate JMS Driver .jar file. Additionally, you must have a JMS Server configured and running. You will need to specify the
following values in the gradle.properties file in the ~/.gradle directory:

* username (Optional)
* password (Optional)
* providerURL
* connectionFactory
* initialContext
* JMSQueue
* JMSTopic
* VantiqAuthToken
* VantiqTargetServer

The Target VANTIQ Server and Auth Token will be used to create a temporary VANTIQ Source, named testSourceName. These names can
optionally be configured by adding EntConTestSourceName to the gradle.properties file. The following shows what the 
gradle.properties file should look like:

```
    EntConJMSUsername=<yourUsername>
    EntConJMSPassword=<yourPassword>
    EntConJMSURL=<yourJMSURL>
    EntConJMSConnectionFactory=<yourJMSConnectionFactory>
    EntConJMSInitialContext=<yourJMSInitialContext>
    EntConJMSTopic=<yourJMSTopic>
    EntConJMSQueue=<yourJMSQueue>
    TestVantiqServer=<yourVantiqServer>
    TestAuthToken=<yourAuthToken>
```

* **NOTE:** We strongly encourage users to create a unique VANTIQ Namespace in order to ensure that tests do not accidentally 
override any existing Sources.

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
