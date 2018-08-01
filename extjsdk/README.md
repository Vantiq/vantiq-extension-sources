## Repository Contents

*	ExtensionWebSocketClient -- Acts as a translator between Vantiq and the generic source. Formats and sends messages to Vantiq.
*	ExtensionWebSocketListener -- A listener for the Vantiq deployment. Translates Vantiq messages before passing them off to handlers. Also helps ExtensionWebSocketClient keep track of what stage in the connection it is at.
*	Handler -- A simple message handler interface, designed for simple anonymous implementation. Used by ExtensionWebSocketListener to handle various message types.
*	ExtensionServiceMessage -- A class that represents source-related messages sent from the Vantiq server.
*	Response -- A class that represents non source-related messages sent from the Vantiq server.

## How to Include In Your Own Project

### Method 1 - .jar with dependencies included
1.	Clone this repository and navigate to <repo location>/vantiq-extension-sources.
2.	Call `gradlew extjsdk:shadowJar` or `gradlew.bat extjsdk:shadowJar` depending on your OS.
3.	Navigate to <repo location>/vantiq-extension-sources/extjsdk/build/libs
4.	Copy and connect extjsdk-all.jar to your project.

### Method 2 - .jar with no dependencies included
1.	Clone this repository and navigate to <repo location>/vantiq-extension-sources
2.	Call `gradlew extjsdk:jar` or `gradlew.bat extjsdk:jar` depending on your OS
3.	Navigate to <repo location>/vantiq-extension-sources/extjsdk/build/libs
4.	Copy and connect extjsdk.jar to your project.
5.	Include the following dependencies through Gradle, Maven, or whatever build manager you are using. Links to the dependencies on mvnrepository.com are included.
	*	[com.squareup.okhttp3:okhttp Version 3.4.1](https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp/3.4.1)
	*	[com.squareup.okhttp3:okhttp-ws Version 3.4.1](https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp-ws/3.4.1)
	*	[com.fasterxml.jackson.core:jackson-databind Version 2.9.3](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.9.3)


## Using ExtensionWebSocketClient
Every ExtensionWebSocketClient (Client) deals with a single source across its own WebSocket connection. 

### Connecting to a source
Connection is as simple as creating a client with `new ExtensionWebSocketClient(<source name>)` and then calling `client.inititiateFullConnection(<vantiq url>, <authentication token>)`, where the authentication token is created by an admin using Vantiq's browser interface. The call returns a CompletableFuture that will return whether or not the connection succeeded. If it succeeds, you can now send and receive messages from the source. If it fails, you can either try connecting again, or call `isOpen()`, `isAuthed()`, and `isConnected()` to see if the connection succeeded or failed at the WebSocket, authentication, and source levels respectively.

### Sending Messages
There are three types of messages that can be sent to a source: Notifications, Query responses, and Query errors. 

#### Notifications
Notifications are messages, typically Maps, that the source will pass on to any Vantiq rules saying `WHEN MESSAGE ARRIVES FROM SOURCE <source name>`. To send one, simply call `client.sendNotification(<object to be sent>)`, which will translate the message into JSON and add everything Vantiq needs to recognize the message. 

#### Query Responses
Query responses are responses to a `SELECT` request from Vantiq that targets a source, and can either be a Map or an array of Maps. They only mean anything in relation to an intial Query message received from Vantiq, and thus should only be sent as part of a [Query handler](#queryHandler). To send a Query response, call `client.sendQueryResponse(<HTTP status code>, <Query address>, <Map>/<Map array>)`. The Query address can be obtained using `ExtensionServiceMessage.extractReplyAddress(<Query message>)`. The HTTP status code can be one of 
*	100 - This indicates that there is more data to be delivered. If more than one Map is delivered across multiple iterations they will all be placed into a single array before the Vantiq `SELECT` command receives the data.
*	200 - There is some data in the message, and this is the last or only message. This can be used to complete a string of responses that used code 100.
*	204 - There were no problems, but there is also no data to be sent. When this code is used any data sent along with it will be ignored. This can be used to complete a string of responses that used code 100.

#### Query Errors
Query errors are sent when a Query cannot be completed successfully. To send a Query error, call `client.sendQueryError(<Query address>, <error code>, <message template>, <message parametares>)`. 
*	The Query address is obtained the same way as for a Query response. 
*	The error code is a string intended to help identify where the error occurred. Possible error codes are the full class name of the class that caused the error, the class name of the Exception that caused it, or the name of the server where the error occurred. 
*	The message template is the message that will accompany the error, ideally describing what happened and why. The message template can carry parameters, which can be specified using `{#}` inside the string, where `#` is the location of the intended parameter in the message parameters section, beginning at 0.
*	The parameters