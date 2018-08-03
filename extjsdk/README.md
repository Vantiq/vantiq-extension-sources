## Repository Contents

*	[ExtensionWebSocketClient](#client) -- Acts as a translator between Vantiq and the generic source. Formats and sends messages to Vantiq.
*	[ExtensionWebSocketListener](#listener) -- A listener for the Vantiq deployment. Translates Vantiq messages before passing them off to handlers. Also helps ExtensionWebSocketClient keep track of what stage in the connection it is at. 
*	[Handler](#handler) -- A simple message handler interface, designed for simple anonymous implementation. Used by ExtensionWebSocketListener to handle various message types.
*	[ExtensionServiceMessage](#extSvcMsg) -- A class that represents source-related messages sent from the Vantiq server.
*	[Response](#httpResponse) -- A class that represents non source-related messages sent from the Vantiq server.

## How to Include In Your Own Project

### Method 1 - .jar with dependencies included
1.	Clone this repository and navigate to <repo location>/vantiq-extension-sources.
2.	Call `./gradlew extjsdk:shadowJar` or `gradlew extjsdk:shadowJar` depending on your OS.
3.	Navigate to <repo location>/vantiq-extension-sources/extjsdk/build/libs
4.	Copy and connect extjsdk-all.jar to your project.

### Method 2 - .jar with no dependencies included
1.	Clone this repository and navigate to <repo location>/vantiq-extension-sources
2.	Call `./gradlew extjsdk:jar` or `gradlew extjsdk:jar` depending on your OS
3.	Navigate to <repo location>/vantiq-extension-sources/extjsdk/build/libs
4.	Copy and connect extjsdk.jar to your project.
5.	Include the following dependencies through Gradle, Maven, or whatever build manager you are using. Links to the dependencies on mvnrepository.com are included.
	*	[com.squareup.okhttp3:okhttp Version 3.4.1](https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp/3.4.1)
	*	[com.squareup.okhttp3:okhttp-ws Version 3.4.1](https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp-ws/3.4.1)
	*	[com.fasterxml.jackson.core:jackson-databind Version 2.9.3](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.9.3)


## Program Flow
1.	A new ExtensionWebSocketClient is created.
2.	Setup and add any handlers through `client.set<type>Handler()`. See the [Receiving Messages](#handler) section for more information on what they do. The handlers can be set and reset at any point in time, but you should do so here, or in step 6 through the configuration handler if its setup depends on the contents configuration document.
3.	Call `client.initiateFullConnection()`. Note that all the following steps happen asynchronously relative to the caller, but in the order specified relative to each other.
4.	The WebSocket connection either succeeds or fails. If it succeeds, the Future returned by `client.getWebSocketFuture()` completes as true and the program continues. If it fails, the CompletableFuture returned from the call in step 3 will complete as false and you should exit or restart at step 3.
5.	The authentication either succeeds or fails. If it succeeds, the Future returned by `client.getAuthenticationFuture()` completes as true. If it fails, the Future from step 3 completes as false and you should exit or call `client.authenticate(); client.connectToSource()` or `client.initiateFullConnection()` to retry step 5. Regardless of success or failure, authHandler is then called. If the authentication succeeded, the program continues.
6.	The source connection either succeeds or fails. If it succeeds, the Future returned by `client.getSourceConnectionFuture()` completes as true as does the Future from step 3, configHandler is called, and the program continues. If it fails, the Future from step 3 completes as false, httpHandler is called, and then you should exit or call `client.connectToSource()` or `client.initiateFullConnection()` to retry step 6.
7.	The source is now connected. Query, Publish, and reconnection messages will be received by their respective handlers. Notification and Query responses/errors can be sent. If a reconnect message is received then the program returns to just before step 6 (but will not proceed to 6 unless you call `client.connectToSource()` or have set autoReconnect to true), reconnectHandler is called, and if the Client's autoReconnect option has been set to true it will proceed to step 6.

If the WebSocket connection closes through any means other than a call of `client.stop()` then all Futures are forcibly completed as false, the program is reset to just before step 3 (maintaining any changes to handlers) and closeHandler is called. At this point, you should start step 3 again.

### Details on initiateFullConnection
The call `future = client.initiateFullConnection()` is identical to `client.initiateWebSocketConnection(); client.authenticate(); future = client.connectToSource()`. Each of these functions returns their own CompletableFuture, which is created whenever a call to it is made and either no previous call has been made or the previous call has already failed. These Futures can be obtained without the risk of (re)creating them through their own getters. Before the first call and after a Websocket connection closure each Future is null. <br>
The Futures for these three functions are chained together. That is, if all three are called one of the three fails, then its Future completes as false and so do the futures of all the subsequent steps. Also, if a step is called before the previous connection step completes (but after the previous function has been called) then that step will automatically startup once the previous Future completes successfully. <br>
There is a version of `client.authenticate()` that users username and password instead of an authentication token. This option is not available for `client.initiateFullConnection()` because unlike tokens, user credentials are not typically associated with a single namespace and can potentially connect you to any namespace the user has access to.

	
## <a name="client" id="client"></a>Using ExtensionWebSocketClient
Every ExtensionWebSocketClient (Client) deals with a single source across its own WebSocket connection. 

### Connecting to a source
Connection is as simple as creating a client with `new ExtensionWebSocketClient(<source name>)` and then calling `client.initiateFullConnection(<vantiq url>, <authentication token>)`, where the authentication token is created by an admin using the Vantiq client. The call returns a CompletableFuture that will return whether or not the connection succeeded. If it succeeds, you can now send and receive messages from the source. If it fails, you can either try connecting again, or call `isOpen()`, `isAuthed()`, and `isConnected()` to see if the connection succeeded or failed at the WebSocket, authentication, and source levels respectively.

### Sending Messages
There are three types of messages that can be sent to a source: Notifications, Query responses, and Query errors. 

#### Notifications
Notifications are messages, typically Maps, that the source will pass on to any Vantiq rules saying `WHEN MESSAGE ARRIVES FROM SOURCE <source name>`. To send one, simply call `client.sendNotification(<object to be sent>)`, which will translate the message into JSON and add everything Vantiq needs to recognize the message. 

#### <a name="queryResponse" id="queryResponse"></a>Query Responses
Query responses are responses to a `SELECT` request from Vantiq that targets a source, and can either be a Map or an array of Maps. They only mean anything in relation to an initial Query message received from Vantiq, and thus should only be sent as part of a [Query handler](#queryHandler). To send a Query response, call `client.sendQueryResponse(<HTTP status code>, <Query address>, <Map>/<Map array>)`. The Query address can be obtained using `ExtensionServiceMessage.extractReplyAddress(<Query message>)`. The HTTP status code can be one of 
*	100 - This indicates that there is more data to be delivered. If more than one Map is delivered across multiple iterations they will all be placed into a single array before the Vantiq `SELECT` command receives the data.
*	200 - There is some data in the message, and this is the last or only message. This can be used to complete a string of responses that used code 100.
*	204 - There were no problems, but there is also no data to be sent. When this code is used any data sent along with it will be ignored. This can be used to complete a string of responses that used code 100.

#### <a name="queryError" id="queryError"></a>Query Errors
Query errors are sent when a Query cannot be completed successfully. To send a Query error, call `client.sendQueryError(<Query address>, <error code>, <message template>, <message parameters>)`. 
*	The Query address is obtained the same way as for a Query response, `ExtensionServiceMessage.extractReplyAddress(<Query message>)`.
*	The error code is a string that specifies where the error came from. This code should be unique to the location reporting the error, for debugging purposes.
*	The message template is the message that will accompany the error, ideally describing what happened and why. The message template can carry parameters, which can be specified using `{#}` inside the string, where `#` is the location of the intended parameter in the message parameters section, beginning at 0.
*	The parameters are an Object array of that will be translated into strings and inserted into the message template based anywhere that `{<array index>}` is located.


### <a name="handler" id="handler"></a>Receiving Messages
All messages received from the Vantiq server are dealt with using handlers attached to the ExtensionWebSocketListener, typically through setters in Client. There are seven different handlers, three for the WebSocket connection, two for the source connection, and two for the source messages. The three WebSocket handlers are authentication, HTTP, closure. The source connection handlers are configuration and reconnection. The source message handlers are Publish and Query. It is strongly advised that any handlers you wish to use are set before attempting to connect to the source.

#### Authentication
The authentication handler receives all messages until and including the message that marks the authentication as successful. A response to a successful authentication has a status code of 200 and includes a mass of information about the connection and its privileges inside the message body, none of which is necessary to know for the purposes of connecting to the source. A response to a failed authentication will have a status code of 300+, typically 400, and the body may contain a message specifying why the authentication failed. Every Client has a default authentication handler that logs whether the authentication succeeded or failed. The authentication handler receives Response objects.

#### HTTP
The HTTP handler receives all non source-related messages after authentication has succeeded. The majority of these messages will be confirmations of receipt for a message sent to the Vantiq server, and consist of a status of 200 and little else. Any other messages are likely to be an error of some sort, with status code 300+ and a body containing a message describing the error. The HTTP handler receives Response objects.

#### <a name="closeHandler" id="closeHandler"></a>Closure
The closure handler does not deal with a specific message or type of message, but instead is called when either your code calls `client.close()` or the WebSocket connection is forced to close, most likely due to a problem with the connection. This handler is called after everything but the handlers are reset, essentially creating a new Client targeting the same source. If you have saved a reference to the Client's Listener, be aware that the Listener is stopped and replaced with a functionally identical Listener just before this handler is called. The closure handler receives the Client whose connection closed.

#### Configuration
The configuration handler receives the message containing a configuration document created on the Vantiq client. This message is only received upon a successful source connection, and contains the source's configuration document as a Map at `((Map) <message>.getObject()).get("config")`. The configuration handler receives an ExtensionServiceMessage.

#### Reconnection
Under certain circumstances, typically a change to the source's configuration document, the Vantiq server may disconnect you from the source with the intent that you will immediately reconnect. The reconnection handler is called when this occurs. Reconnecting will cause a new (and likely different) configuration document to be sent through the configuration handler, so you should reset any changes caused by the original configuration document in this handler. Once everything is ready, reconnect using `client.connectToSource()`. If you do not want to send the reconnect message yourself or do not wish to create your own handler, then calling `client.setAutoReconnect(true)` will tell the Client to automatically try to reconnect after receiving a reconnect message. The reconnection handler receives an ExtensionServiceMessage.

#### Publish
The Publish handler is called when a message is received that was generated by Vantiq with `PUBLISH <object> TO SOURCE <source name>`. The published object can be retrieved through `<message>.getObject()`. Note that anything declared with the `USING` keyword will also be placed into the same location, e.g. `PUBLISH {"hello":"world"} TO SOURCE <sourceName> USING {"option":"one"}` will generate an object that looks like `{"hello":"world","option":"one"}`. The Publish handler receives an ExtensionServiceMessage.

#### <a name="queryHandler" id="queryHandler"></a>Query
The Query handler is called when a message is received that was generated by Vantiq with `SELECT <keys> FROM SOURCE <source name>`. Query messages expect a response, either [data](#queryResponse) or an [error](#queryError), so every Client has a default handler is created that sends back an error stating that no Query handler was set. If your source doesn't use queries, you should leave the default handler, so the Vantiq server isn't stuck waiting for a response. Options specified using the `WITH` keyword are received as a Map obtained with `<message>.getObject()`.

### <a name="listener" id="listener"></a>ExtensionWebSocketListener
The ExtensionWebSocketListener class should only be accessed and used indirectly through handlers. If you do find a reason to access it directly, you can use `ExtensionWebSocketClient.getListener()`, but all functionality interactions with a Listener should be performed through an ExtensionWebSocketClient. If you do need to save a reference to a listener, be aware that a Client's Listener is stopped and replaced with a functionally identical Listener just before the [close handler](#closeHandler) is called. Call `listener.isClosed()` to check if this has occurred.

### <a name="extSvcMsg" id="extSvcMsg"></a>ExtensionServiceMessage
The ExtensionServiceMessage class is the message sent to or from a source, and has getters for the most relevant properties. 
*	`getSourceName()` returns the name of the source that sent or is receiving the message. This can be useful for identifying which Client received a message.
*	`getObject()` returns the object that is included in many messages. 
*	`getOp()` returns a string that states what operation is requested. Constants for each operation are provided if you wish to compare the messages.
*	`ExtensionServiceMessage.extractReplyAddress(<message>)` returns the reply address for operations that require a reply. Currently this is only relevant for Query messages.

### <a name="httpResponse" id="httpResponse"></a>Response
The Response class is a helper that defines what can be in a WebSocket message to or from the Vantiq server, and has getters for each of its properties.
*	`getStatus()` returns the HTTP code number for the message.
*	`getBody()` the object contained in the body of the message.
*	`getHeader(<header name>)` returns the String value of the requested header.
*	`getContentType()` returns the MIME type of the message body. Currently, only JSON is possible for sent or received messages.

## Licenses
This library uses three licensed libraries: slf4j, okhttp3, and jackson-databind. okhttp3 and jackson-databind are both licensed under [Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). slf4j is licensed under [terms](https://www.slf4j.org/license.html) identical to the [MIT License](https://opensource.org/licenses/MIT). This SDK will be licensed under the the Apache Version 2.0 License in order to conform with the used libraries.