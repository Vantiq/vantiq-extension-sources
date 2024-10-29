## Repository Contents

This repository contains the SDK for building Vantiq connectors (also known as extension sources)
using Python. At a high level, a connector is configured and then run, responding or sending messages to messages the Vantiq server. Each connector acts as a _source_ in a Vantiq namespace.

You will need credentials to a Vantiq server to use this SDK.  Please contact your Vantiq system administrator to obtain these credentials.

## Overview of a Connector

For details about the overall structure of a connector and the protocol used between the connector and the Vantiq source, please see
[here](https://github.com/Vantiq/vantiq-extension-sources#readme).  

Each running instance of this code represents a set of connectors that connect to a single Vantiq namespace. The set of sources for the connector & the connection information is found in the server config file.  We will describe the details later.

Using the Python VantiqConnector SDK, the overall operation is as follows:

* Create a `VantiqConnectorSet` that manages the set of connections (one per source listed in the `server.config` file.
* Establish a set of _handlers_ or callbacks that will allow the connectors to respond to messages from the Vantiq server. There are callbacks for the following situations:
    * `connect` -- when your connection to the server is established, the source configuration is provided to the connector. This can be used to tailor the behavior or local work.
    * `publish` -- when the Vantiq server sends an event to the source, that event is delivered to the `publish` handler
    * `query` -- when the Vantiq server queries the source, that query is delivered to the `query` handler.  The query handler can return information or errors to the application running on the server.
    * `close` -- when the connection to the Vantiq server ends, the `close` handler is called, giving the connector a chance to clean up resources.

The connector runtime will maintain the connection, reconnecting as needed.

The following sections describe the use of this SDK to construct a connector.

## Building a Connector

Running this code required Python 3.10 or later.

To use the SDK, install it into your Python environment.  Using `pip`, 

```commandline
pip install vantiqconnectorsdk
```

Other Python environment tools may use different commands.

Once it is installed, you can import it.

```python

from vantiqconnectorsdk import VantiqConnector, VantiqConnectorSet, VantiqConnectorException, VantiqSourceConnection
```

The SDK is built using _asyncio_.  For details about working with _asyncio_, please see [Python `asyncio` documentation](https://docs.python.org/3/library/asyncio.html).

The Vantiq Connector SDK includes the following items:

* VantiqConnector -- a set of constants defined as part of the interface.
    * a set of constants to help in the use of these two classes:
        * For construction errors:
            * VantiqConnector.ERROR_CODE -- the short name for an error
            * VantiqConnector.ERROR_TEMPLATE -- the template for the message itself
            * VantiqConnector.ERROR_PARAMETERS -- parameters for the error message
        * For context reference during callbacks:
            * VantiqConnector.SOURCE_NAME -- the name of the source for which this callback is intended
            * VantiqConnector.RESPONSE_ADDRESS -- routing information to ensure that a query response is delivered to the correct query
        * Status codes for query responses:
            * VantiqConnector.QUERY_COMPLETE -- the last response for this query
            * VantiqConnector.QUERY_PARTIAL -- a partial response to a query
            * VantiqConnector.QUERY_EMPTY -- an indication that there is no response available for this query
* VantiqConnectorSet -- a class that manages the connections for this set of connectors
* VantiqSourceConnection -- a class that manages the message flow and state for a single connector

### Logging

The SDK uses the standard Python logging. Logger names are constructed from the class name.
If you need to setup logging, you can call the `VantiqConnector.setup_logging()` call.  This
will look for a `logger.ini` file in the `serverConfig` directory.  This is the same place we look
for the `server.config` file (see [below](#serverConfig)).

### <a name="serverConfig" id="serverConfig"></a>Connector Startup Configuration
Connectors need a minimum of three configuration properties at startup:

*   `targetServer`: The URL of the Vantiq server to which the connector should connect.
*   `authToken`: The access token that the connector will use to authenticate to the desired namespace.
*   `sources`: The name of the source or sources (comma-separated list) managed by this set of connectors.

In addition to the configuration properties shown above, the `server.config` file can also include the following property:

*   `sendPings`: A boolean property that, if set to `true`, enables the SDK to send ping messages to the Vantiq Server. 
The ping messages are handled by the underlying websockets library.
*   `connectKWArgs`: A string property that, if set, contains a JSON string representing keyword arguments to be passed
to the `websockets.client.connect()` call. A common case is alter SSL processing in development environments. Here, you
can pass either a set of keyword arguments to the `connect()` call, or the special argument `disableSslVerification`
with a value of `true`.  This _special_ argument allows the connector SDK to perform the internal setup required to
disable SSL verification.

The system expects the `server.config` file to be located in a directory named `serverConfig` in 
the working directory of the connector.

For users who may not want to write the `authToken` property to a file because of its sensitive nature, set the environment variable `CONNECTOR_AUTH_TOKEN` to its value. If the `authToken` is specified in the `server.config` document, that value will take precedence.
Otherwise, if the `authToken` is not set in the configuration file, the system will retrieve whatever value is provided in the environment variable.

The server config file is written as `property=value`, with each property on its own line. The following is an example 
of a valid `server.config` file (including the `authToken`, but that can be omitted and specified as an environment 
variable instead):

```
authToken=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
sources=MySourceName
targetServer=https://dev.vantiq.com/
sendPings=true
```

In a development environment, one might disable SSL processing by providing the following config file:

```
authToken=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
sources=MySourceName
targetServer=https://dev.vantiq.com/
sendPings=true
connectKWArgs={ "disableSslVerification": true }
```

(Note that although this is Python code, the `connectKWArgs` property contains JSON, so the `true` must be
provided as JSON -- lowercase.)

### Program Flow

1. Create a `VantiqConnectorSet`. This will read the config file and establish (but not start) a `VantiqSourceConnection` for each source listed in the config file.
2. Define the handlers to be used.  You can use a single set of handlers for all sources (using `VantiqConnectorSet.configure_handlers_for_all()`, or you can establish handlers for each source (by iterating through the connections provided by `VantiqConnectorSet.get_connections()` and then calling `VantiqSourceConnection.configure_handlers()` for each connection.
3. Once the environment is set, you can start the connector operations.  This is done by calling `VantiqConnectorSet.run_connectors()`.  Once this call is made (and awaited), the SDK will take over management of the connections.
4. Each source connection can perform its work by responding to calls to the `publish` and `query` handlers.
5. If you need to send an event to the server, use `VantiqSourceConnection.send_notification()`.

If the connection closes, the SDK will automatically re-initiate the connection.  This will result in calls the `connect` handlers again, etc.  Such a reconnection can happen due to network or connection issues.  It can also happen if the source configuration on the Vantiq side is changed.  A changed configuration requires a that the connector obtain the new configuration, and that is done by simply making a new connection.


	
## <a name="VantiqSourceConnection" id="sourceConnection"></a>Using VantiqSourceConnection
Every `VantiqSourceConnection` deals with a single source via its own WebSocket connection. 

### Setup

To operate, each `VantiqSourceConnection` must be configured with a set of handlers.  These handlers configured as follows.  (Note that a similar call can be made using the `VantiqConnectorSet` if all connections should use the same handler.

Call `VantiqSourceConnection.configure_handlers(close_handler, connect_handler, publish_handler, query_handler)`.  These handlers are defined as follows.

* handle_close : Callable[[dict], Awaitable[None]]
* handle_connect : Callable[[dict, dict], Awaitable[None]
* handle_publish : Callable[[dict, dict], Awaitable[None]
* handle_query : Callable[[dict, dict], Awaitable[None]

These parameters specify the callbacks to handle close, connect, publish, and query calls, respectively. For each, the first parameter is a dict providing the context for the operation.  The context will contain an entry keyed with `VantiqConnector.SOURCE_NAME` whose value is the name of the source for which this is a callback.  For query calls, there will be an additional entry keyed with `VantiqConnector.RESPONSE_ADDRESS`. The value here is used to route the query response back to the caller in the Vantiq server.
 
This context parameter is to be used in the various query response calls.

For the handle_connect, handle_query, and handle_publish calls, there will be a second dict parameter. This will contain the message sent from Vantiq to the connector (source).

Note that the handlers all return an _Awaitable_.  This means that they can operate using _asyncio_ to do other things.  However, _asyncio_ operations should be careful not to block the event loop. If an handler is going to take a long time to run, you will want to set it running separately.

#### <a name="connect" id="connect"></a>Connection (Configuration)

The `handle_connect` callback is called as part of the Vantiq connection sequence, delivering the source configuration to the connector. receives the message containing a configuration document created on the Vantiq client.

This callback will be called with two parameter: the context and the configuration document.
The _context_ parameter was outlined in the previous section.
The _configuration_ document is a `dict` object containing the source's configuration document. There is no format specification of the configuration document -- its content is defined by the connector.  Generally, the configuration document allows the connector to be tailored to specific purposes.  It may contain database connection information, machine learning models to run, or operational information like cache sizes, etc.

##### Reconnection
Under certain circumstances (_e.g._, a change to the source's configuration document or some network connection issue), the Vantiq server may disconnect
you from the source with the intent that you will immediately reconnect.

This operation is unavoidable, but is generally transparent to the connector.  When this happens, the [`handle_close` callback](#closeHandler) will be called, and then the
[`handle_connect` callback](#closeHandler).  Since this may be an indication that the source configuration has changed, each _connect_ call should be treated as a new connection/configuration.

This process is automatic.  No special work by the connector is required.

### Processing Messages

Messages involving a source come in three (3) flavors:

* Messages (events) sent by a source
* Query messages sent to a source. Query messages, in turn, have responses.
* Messages published to a source

The following sections outline the work involved here.

#### Notifications

Notifications are messages that the source will pass on to Vantiq rules that include 
`WHEN EVENT OCCURS ON "/sources/<source name>"`. To send one, simply call
`VantiqSourceConnection.send_notification(<message to be sent>)`, which will translate the message into JSON and add everything Vantiq needs to recognize the message.  The _message to be sent_ shoud be a `dict` object.

#### Queries

Queries are sent to the source via VAIL code of the form

```
row = SELECT * FROM SOURCE someSource with prop1 = value1, prop2 = value2, ...
```

A query like this will result in a query message to the source (and, thus, to the connector via the
`handle_query` callback). The parameter value passed to the callback will be a dict object
`{ prop1: value1, prop2: value2, ... }`.  That is, the parameter values are those passed from the _with_ clause in the VAIL SELECT statement.

Once the query message is received (_i.e._, delivered to the callback), the connector to which the
query was delivered must respond. It responds by making calls to return a query response or a query error.

##### Query Context

Query responses are responses to a `SELECT` request from Vantiq that targets a source, and can either be a `dict` or a series of `dict` objects in the form of a `list` or `array`. They only mean anything in relation to an initial Query message received from Vantiq, and thus should
only be sent as part response to a query via the callback. 

To enable this behavior, the callback is passed a _context_ parameter that provides information about the context of the query. This parameter will take the form of a `dict` object with two (2)
items: a `VantiqConnector.SOURCE_NAME` item that contains a string specifying the source toward which this query is
sent, and a `VantiqConnector.RESPONSE_ADDRESS` item that contains the routing information to allow the query resposnes (or error) to find their way back the the Vantiq code that issued the query.

##### <a name="queryResponse" id="queryResponse"></a>Query Responses

To send a query response, call

```
VantiqSourceConnection.send_query_response(ctx, code, message)
```

The `ctx` or context parameter is passed in to the `handle_query` callback.  Simply pass that back unchanged.

The code is an HTTP status code. You can use the following:

* `VantiqConnector.QUERY_EMPTY` to indicate that this query has an empty response.  In this case, the message parameter must be None.
* `VantiqConnector.QUERY_PARTIAL` to indicate that this is one of a number of responses to this query. This is used if you are dynamically producing results to return.
* `VantiqConnector.QUERY_COMPLETE` to indicate that this is the last (or only) response to this query.

For the latter two (2) codes, the `message` parameter must be either a `dict` or a `list` of `dict` objects.  These will be sent to the Vantiq server in a JSON format.

##### <a name="queryError" id="queryError"></a>Query Errors

Query errors are sent when a Query cannot be completed successfully. To send a Query error, call
`VantiqSourceConnection.send_query_error(ctx, message)`. 

The `ctx` or query context was passed in to the `handle_query` callback.
The `message` (`dict`) parameter must contain the following items.

* `VantiqConnector.ERROR_CODE` -- the short name for the error
* `VantiqConnector.ERROR_TEMPLATE` -- template for the error message.  Templates contain zero-based indexed references for parameters list (_e.g._,`{0}` to refer to the first parameter, `{1}` the second, etc.).
* `VantiqConnector.ERROR_PARAMETERS` -- a list of parameters for the template.

##### <a name="publish" id="publish"></a>Published Messsages

When the Vantiq server publishes an event to a source that is implemented using a connector, it is delivered via the `handle_publish` callback. As with a query message, the handler will be passed two (2) parameter:  A context and the message.

The context in this case will contain the `VantiqConnector.SOURCE_NAME` item.  It need not contain the `VantiqConnector.RESPONSE_ADDRESS` item.  You can not respond to messages delivered via the `handle_publish` callback.

If the Vantiq server executes VAIL code like

```
    PUBLISH { prop1: value1, prop2: value2, ... } TO SOURCE connectorSource
```

the connector will receive a `handle_callback` call containing the context parameter

```python
{ 
    'source_name': 'connectorSource'
}
```

and a message parameter containing

```python
{ 
    'prop1': value1,
    'prop2': value2
}
```

The connector can then act on the message as it deems appropriate.

#### Kubernetes Operation and TCP Probe

In the case that the connector is deployed within a Kubernetes cluster, the `VantiqConnectorSet` offers support for TCP Startup/Liveness/Readiness probes. The class defines two methods: `declare_healthy()` and `declare_unhealthy()`, that can be used to make the respective declarations.

Internally, `declare_healthy()` and `declare_unhealthy()` open and close a TCP socket, respectively; the Kubernetes probes attempt to connect to that port. If connection is successful, the probe is considered successful; if not, the probe is unsuccessful. 

The socket is opened on port 8000 by default, but this can be changed by including tcpProbePort:<portNumberHere> in the connector's `server.config` document. It is the responsibility of the connector developer to manage when the connector is healthy and when it is not.

Note that `declare_healthy()` and `declare_unhealthy()` are available from the `VantiqSourceConnection` as well.  These make calls to the containing `VantiqConnectorSet`. Health status is available only on a `VantiqConnectorSet` basis.

The `VantiqConnectorSet` and `VantiqSourceConnection` classes both offer the `is_healthy()` method.  This will return `True` if the connector set has been declared healthy, `False` if it has been declared unhealthy, and `None` if no health declaration has been made.

##### <a name="closeHandler" id="closeHandler"></a>Close
The closure handler does not deal with a specific message or type of message, but instead is called when either your
code calls `VantiqSourceConnection.close()` or the WebSocket connection is forced to close, most likely due to a problem with the connection.

The `handle_close` is passed a context containing the source name (see [publish](#publish)). At the time the handler is called, the connection has already been closed.  No interaction with the Vantiq server is possible;  this handler call allows the connector to perform any resource deallocation or other cleanup necessary.

## License
The source code in this project is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
