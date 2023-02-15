This directory contains a simple example for connecting a Vantiq system to Salesforce using an Apache Camel application.

# SalesforceExample Project

## Contents

The project file is in `projects/salesforceExample.com`.  That project contains the following, all under the salesforceExample directory:

* **documents/sfGetTasks.yml** -- the YAML definition of the Apache Camel application to fetch 
the list of Tasks from the Salesforce instance.
* **sourceimpls/CAMEL_CONNECTOR.json** -- The Camel Connector source implementation definition.
* **sources/sfSource.json** -- a source definition for a source named `sfSource`.  This source makes use of the Camel Apache application, and can be queried to find the list of Salesforce Tasks.
* **procedures/getSFTasks.vail** -- a simple procedure that queries the Camel Connector for the list of Salesforce Tasks, then returns the one whose `Subject` is desired.


## Requirements/Assumptions

The `sources/sfSource.json` configuration assumes that there are three (3) secrets present:

* **sfClientId** -- the Client Id (_aka_ _Consumer Key_) to gain access to SalesForce
* **sfClientSecret** -- the Client Secret (_aka_ _Consumer Secret_) to gain access to Salesforce
* **sfRefreshToken** -- A _refresh token_ used (using the _refresh token flow_) to gain access to Salesforce.

These are used to configure the Salesforce component in the Camel Connector.
These secrets must be defined before the connector makes a connection to the Vantiq server.
They are used in the `sfSource` configuration.

## Running the Example Project

1. Import the project. This will create the source and add the configuration.
1. Add the secrets mentioned in the previous section.
1. Deploy the CamelConnector, setting the source name to `sfSource` and adding the appropriate connection information.
1. Start the connector.  It should connect to Vantiq, get the configuration, load the classes required, and start the Camel application.
1. Run the `getSFTasks` procedure.  This will get the list of tasks in your SalesForce instance.


