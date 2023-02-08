This directory contains a simple example for connecting a Vantiq system to Salesforce using an Apache Camel application.

# SalesforceExample Project

## Contents

The project file is in `projects/salesforceExample.com`.  That project contains the following:

* **sourceimpls/CAMEL_CONNECTOR.json** -- The Camel Connector source implementation definition.
* **sources/sfSource.json** -- a source definition for a source named `sfSource`.  This source makes use of the Camel Apache application, and can be queried to find the list of Salesforce Tasks.
* **procedures/getSFTasks.vail** -- a simple procedure that queries the Camel Connector for the list of Salesforce Tasks, then returns the one whose `Subject` is desired.

## Additional Contents

Also included is a file `camelDocs/sfGetTasks.yml`.
This contains the YAML definition of the Apache Camel application to fetch the list of Tasks from the Salesforce instance.
This document must be loaded into the Vantiq instance before importing the project mentioned above.

## Requirements/Assumptions

The `sources/sfSource.json` configuration assumes that there are three (3) secrets present:

* **sfClientId** -- the Client Id (_aka_ _Consumer Key_) to gain access to SalesForce
* **sfClientSecret** -- the Client Secret (_aka_ _Consumer Secret_) to gain access to Salesforce
* **sfRefreshToken** -- A _refresh token_ used (using the _refresh token flow_) to gain access to Salesforce.

These are used to configure the Salesforce component in the Camel Connector.
These secrets must be defined before the connector makes a connection to the Vantiq server.
They are used in the `sfSource` configuration.
