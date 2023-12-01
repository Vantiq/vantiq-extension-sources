
# Vantiq Python Execution Connector

The [Vantiq](http://www.vantiq.com) Python Execution Connector is a Python package that provides the ability 
to execute Python code as directed by a Vantiq server

## Installation

The connector is installed from the PyPI repo.  To install this into your system,
use
```commandline
    pip install vantiqPythonExecConnector
```

Note: depending on your local environment, you may need to use `pip3`
instead of `pip`, or whatever is appropriate to install into your
virtual environment.

The Vantiq Python Execution Connector requires Python version 3.10 or better.

## Quick Start

### Connector Configuration

You will need valid credentials on a Vantiq server in the form of an
access token.  If you have a private Vantiq server,
contact your administrator for credentials.  If you wish to use the
Vantiq public cloud, contact [support@vantiq.com](mailto:support@vantiq.com).

The information required is placed in a `server.config` file in the `serverConfig` directory below the working directory from which the connector will be run. The format is as follows:

```
targetServer = ...
authToken = ...
sources = ...
```

An example file might be

```
targetServer = https://dev.vantiq.com
authToken = _cDWBfZLNO9FkXd-twjwKnVIBZSGwns35nF4nQFV_ps=
sources = pythonSource
```

For users who may not want to write the `authToken` property to a file because of its sensitive nature, set the environment variable `CONNECTOR_AUTH_TOKEN` to its value. If the `authToken` is specified in the `server.config` document, that value will take precedence.
Otherwise, if the `authToken` is not set in the configuration file, the system will retrieve whatever value is provided in the environment variable.

> Note that this token will not work -- you will need to create your own
> within a VANTIQ installation

You should also provide an appropriate `logger.ini` file in the same directory.
An example file is provided at `src/test/resources/logger.ini` in this project's github repository.

### Running the Connector

Once you have the connector configuration (`serverConfig/server.config`) and logging configuration (`serverConfig/logger.ini`) files set up, you can run the connector using the command

```shell
    vantiqPythonExecConnector
```

On startup, the connector will connect to the configured Vantiq server awaiting calls to run Python code. No other interaction is necessary.


## Documentation

For the full documentation on the SDK, see the documentation available at the github repository.  This README file provides the basics;  more detailed information is available in [docs/Usage.md](https://github.com/Vantiq/vantiq-extension-sources/blob/master/pythonExecSource/docs/Usage.md).

## Developers

The project is set up as a `gradle` project.  To run the tests, use

```commandline
./gradlew test
```

or

```commandline
./gradlew.bat test
```

in a Windows environment.

The tests run will run a mocked version. To execute tests against a _live_ server,
define the following gradle properties:

```properties
# Python project values
TestVantiqServer=<Vantiq server url>
TestAccessToken=<access token from that Vantiq system>
TestVantiqUsername=<Vantiq user name>
TestVantiqPassword=<Password for that Vantiq user>
```

Alternatively, when running directly, use the following environment variables:

```commandline
VANTIQ_URL <Vantiq erver url>
VANTIQ_ACCESS_TOKEN <Access token from that Vantiq system>
VANTIQ_USERNAME <Vantiq user name>
VANTIQ_PASSWORD <Password for that Vantiq user>
```

## Copyright and License

Copyright &copy; 2022 Vantiq, Inc.  Code released under the
[MIT license](https://github.com/Vantiq/vantiq-extension-sources/blob/master/pythonExecSource/LICENSE.txt).
