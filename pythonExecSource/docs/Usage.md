# Python Execution Connector/Extension Source

This module contains the source code for the [VANTIQ](https://vantiq.com) Python Execution Connector or Extension Source.

The Python Execution Connector (Extension Source) is a Connector that provides for the execution of Python code. The connector is used to allow Vantiq/VAIL procedures to request the execution
of Python code and the return of the results to the Vantiq server.

This document describes the operation of the source itself as well as how to make use of it within a VANTIQ system.

> Note: VANTIQ Extension Sources are first supported in VANTIQ version 1.23.

## Connectors or Extension Sources

Vantiq Connectors (_aka_ Extension Sources) support the creation of a Vantiq source.
Within a Vantiq system, a *source* is the means by which the Vantiq system communicates with other systems.

Briefly, extension sources connect to the Vantiq system via a websocket connection, and interact with that system based on five (5) operations.

 - connectExtension -- how the extension source identifies itself to Vantiq
 - configureExtension -- Vantiq provides source-specific configuration to the extension source
 - publish -- data sent from Vantiq to the source
   - Result of a PUBLISH to the source
   - (Note: the Python Execution connector does not respond to _publish_ operations.)
 - query -- queries sent from Vantiq to the source with a response required.
   - SELECT statements in Vantiq
 - notification -- data sent from the source to VANTIQ
   - These appear to Vantiq as a message from the source

More information about how to work with these operations can be found in the [Extension Source SDK for Java](../../extjsdk/README.md) documentation.

Note: The term _extension source_ is an older term, but it is still evident in the API. That said, this document will use the term _connector_.

## Python Execution Connector Operation

### Defining the Source in VANTIQ

#### Creating the Source

When creating a source for the Python Execution connector,
you must first create the Python Execution source type or implementation.
This is done by using the `pythonExecImpl.json` file found in
`src/main/resources/pythonExecImpl.json`.
To make the source type known to VANTIQ, use the `vantiq` cli command

```
vantiq -s <profileName> load sourceimpls <fileName>`
```

where `<profileName>` is replaced by the VANTIQ profile name, and `<fileName>` is the file to be loaded, in this case, the `pythonExecImpl.json` file mentioned above.

Once that type is loaded,
you can create a source of that type.
This is done by first selecting the `PYTHONEXECUTION` type for the source,

![Selecting a type for a source](docs/images/pyExecExampleSelectType.png)

and then providing a configuration.  We will examine the configuration just below.

![Providing a Python Execution Source Configuration](docs/images/pyExecExampleConfiguration.png)

#### <a name="configuration" id="configuration></a>Configuration

> Note:  It is assumed here that the reader is familiar with Python concepts and terminology.
> This source tries to use terminology familiar to an Python practitioner where possible.

The configuration of a Vantiq connector is a JSON document.

For Python Execution sources, the config document format parallels those of other connectors.
Specifically, the document contains a root named `config`, which, in turn, contains an element
named `'pythonExecConfig`, which contains the `general` section. This section can contain the following properties:

- `codeCacheSize` -- this is the number of compiled python code entries this connector will contain. The default is 128, and that should be sufficient for most purposes.
- `returnRuntimeInformation` -- this instructs the the connector to return runtime information as part of the return from code execution. This is primarily for collecting performance information. It defaults to `false`.
    - The return of runtime information is supported only when the connector is returning the results directly.  In cases where the executed code is sending results (see `codeHandlesReturn` [below](#withParameters), there is no vehicle for the return of runtime information.

An example configuration document specifying the return of runtime information would look like the following:

```
{ "config": {
    "pythonExecConfig": {
        "general": {
            "returnRuntimeInformation" : true
        }
    }
}
    
```

If no changes to the defaults are required, no configuration is necessary.


### Working with VANTIQ

To a Vantiq user,
an extension source is indistinguishable from a source provided within the system.
Details about how to interact with a Vantiq source can be found in the Vantiq documentation;
here we will provide a few examples.


#### Query from VANTIQ

To execute some Python code,
you can run a query using the SELECT statement.

```js
    var pythonCode = "print('loop count', loop_count)"

    var result = SELECT * FROM SOURCE MyPythonExecSource
        WITH name = "py_code_example", code = pythonCode, presetValues = { loop_count: 20 }
    log.info("Got result: {{}}", [result])
```

This query runs the code in the variable `pythonCode`, in this case, a `print` statement.

The following works in a similar way, but instructs the connector to fetch the Python script from a Vantiq Document (this is probably a more common case).

```js
  var result
                var allResults = []
                for (result in select * from source {PY_EXEC_SOURCE_NAME}
                    with script = "myPythonDoc",
                        codeHandlesReturn = true,
                        presetValues = {{ loop_count: 20 }}) {
                      log.info("Got (partial) document result: {{}}", [result])
                      allResults.push(result)
                }
``` 

This query runs the python code contained in the document _myPythonDoc_.

#####  <a name="withParameters" id="withParameters"></a>Query With Parameters

You will note that the the SELECT queries above each have an associated _WITH_ clause.
The _WITH_ is used to specify special instructions for the execution of the query, and the items specified in the _WITH_ clause are sent to the connector.  The following _WITH_ clause parameters are defined as part of this connector.

- `code` (String) -- This specifies the code to run. 
- `name` (String) -- This specifies the name to associate with the `code` to be run. The `name` specified here is used for naming the code in the cache.
- `script` (String) -- This is the name of the Vantiq document from which to obtain the code to run.

Note that `code` and `name` can be provided together, but when `script` is used, that document name is used as the cached code's name.  Thus, `name` cannot be specified along with `script`.

- `presetValues` (Object) -- This is used to set Python global variables for the executed code to access. This parameter should be a Vantiq object.  Each property within the object will create a Python global variable whose name is the property name, and whose value is the property value.  In the examples shown above, you can see that the python code contains a reference to `loop_count`, and the associated query sets that value via the `presetValues` parameter.
- `codeHandlesReturn` (boolean) -- When `true`, this specifies that the executed code returns its own results (and errors, if necessary).  When `false` or absent, the connector assumes responsibility to returning the results (as outlined [below](#returningData).
- `limitReturnTo` (List of String) -- When `codeHandlesReturns` is `false`, this instructs the connector to limit the values it returns to the (Python) variables in this list.

##### <a name="returningData" id="returningData"></a>Returning Data

When code is executed, it is generally the case that the caller is interested in the result.
Data can be returned in two ways.

When `codeHandlesReturn` is `false` or missing, the connector will, upon completion of the execution, return the values of the Python global variables at the end of execution. Specifically,
the connector will return single result consisting of the following properties;

- `pythonCallResults` (Object) -- this is a VAIL object containing properties corresponding to the Python global variables of the code executed.  All Python global variables that can be encoded in JSON are returned unless limited by the `limitReturnTo` parameter.  If `limitReturnTo` is specified, then only the Python global variables listed are returned.
- `connectorRuntimeInfo` (Object) -- this is a VAIL object containing some runtime information if 1) the connector has been configured to return such information (see `returnRuntimeInformation` [above](#configuration)), and 2) that `codeHandlesReturn` is absent of `false`.  When present, the value returned may contain some of the following properties:
    - `totalTime` -- the amount of time used to run the code
    - `executionTime` -- the amount of time spent executing the code sent down
    - `compileTime` -- the amount of time spent compiling the code.  If missing, no compilation was performed.
    - `currentCacheSize`-- the number of items in the compiled code cache
    - `cacheCapacity`-- the size (in terms of number of items) in the compiled code cache.

When `codeHandlesReturns` is `true`, the executed code takes on the responsibility for sending data back. This is done by calling the Python Connector SDK calls `send_query_response()` or `send_query_error()`. Please see that documentation for details about these calls.

To provide context for the operation of your code, the following Python global variables will be set.

- `__name__` (String) -- set to `__main__`
- `__file__` (String) -- set to the name of the _file_ being executed.  If `code` and `name` are provided, `__file__` will be the `name` value.  If `script` is provided, that will be used as the name.  If no name is otherwise available, the connector will generate a name.  
- `connector_connection` (`VantiqSourceConnection`) -- this is the source connection that can be used to send results back.
- `connector_context` (dict) -- this is the _context_ parameter required by the `send_query_response()` and `send_query_error()` methods for associating the results of the operation with the caller in the Vantiq server.

In addition, any values specified in the `presetValues` parameter will be set as global variables.

#### Receiving Data from the Source

Although probably unusual, the Python code being executed can send events to the associated Vantiq source. This is done using the `send_notificaiton()` method in the Vantiq Connector SDK. This is a method associate with the `connector_connection` global variable outlined above.  To make such a call, use

```python
    await connector_connection.send_notification({'id': str(uuid.uuid4())})
```

That will send an event from the connector's source containing the values specified.

On the Vantiq side, assuming our source is named `pythonSource`, we would define a rule as follows:

```
RULE pythonSourceEvebt
WHEN EVENT OCCURS ON "/sources/pythonSource" as sourceEvent

// Grab the actual value from the event
var message = sourceEvent.value

log.info("Got a message: {}", [message])

```

### Deploying a Python Execution Connector

You can build the Python execution connector yourself, or, more commonly, simple install using `pip` (or equivalent).

#### Install

```shell
    pip install vantiqPythonExecConnector
```

Once this is done, you can execute the connector by running the command

```
    vantiqPythonExecConnector
```

once the configuration file is in place (see [below](#runningConnector)).

#### Build the Connector

To build the connector, clone this [Github Repository](https://github.com/Vantiq/vantiq-extension-sources).
Once that has been done, run

```
./gradlew pythonExecSource:clean pythonExecSource:assemble
```

This will create the distribution in `dist`.

#### <a name="runningConnector" id="runningConnector"></a>Running the Connector

Once you have obtained the connector and created a source in Vantiq,
you are ready to run the extension.

The underlying sequence of events is as follows:

 - Source is created in Vantiq
   - When that happens, the source is *up* but not *connected*.
 - The source extension connects to Vantiq, providing the name of the source on whose behalf it is operating.
   - At this point, the source is *connected* and operational.
 - Once connected, the source can be the target of SELECT statements.

The sequence above requires that the connector be told of the Vantiq installation to which to connect, appropriate credentials for doing so, and the name of the source it to perform the `connectExtension` operation.

This information should be provided in a configuraiton file. 
(Please read the [Connector SDK's server config documentation](../extpsdk/README.md#serverConfig) first.)
Specifically, create a file named `server.config` on a `serverConfig` directory within the working directory in which the connector run.

The information required is placed in that file as follows:

```
targetServer = ...
authToken = ...
source = ...
```

An example file might be

```
targetServer = https://dev.vantiq.com
authToken = _cDWBfZLNO9FkXd-twjwKnVIBZSGwns35nF4nQFV_ps=
source = pythonSource
```

For users who may not want to write the `authToken` property to a file because of its sensitive nature, set the environment variable `CONNECTOR_AUTH_TOKEN` to its value. If the `authToken` is specified in the `server.config` document, that value will take precedence.
Otherwise, if the `authToken` is not set in the configuration file, the system will retrieve whatever value is provided in the environment variable.

> Note that this token will not work -- you will need to create your own
> within a VANTIQ installation

You should also provide an approriate `logger.ini` file in the same directory.
An example one is provided at `src/test/resources/logger.ini` in this project.

# Copyright and License

Copyright &copy; 2022 Vantiq, Inc.  Code released under the [MIT license](../LICENSE.txt).
