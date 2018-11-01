## Overview

The following documentation outlines how to incorporate a JDBC Source as part of your VANTIQ project. This allows a user to interact directly with a SQL Database from VANTIQ, and supports almost all standard SQL Commands.

## Prerequisites

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the Extension Source README.md for more information.

The user must define the JDBC Source implementation in VANTIQ. For an example of the definition, please see the *jbdcImpl.json* file located in the *src/test/resources* directory.

Additionally, an example VANTIQ project named *jdbcExample.zip* can be found in the *src/test/resources* directory.
*   It should be noted that this example connects to a MySQL database.

## Repository Contents

*   [JDBCMain](#jdbcMain) -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   [JDBCCore](#core) -- Coordinates the connections to the database, and sends the resulting data back to VANTIQ if
    necessary.
*   [JDBCHandleConfiguration](#srcConfig) -- Sets up the JDBC connection based on the source's configuration document, and
    initializes the queryHandler and publishHandler.
*   [JDBC](#jdbc) -- The class that directly interacts with the JDBC Driver, executing the query and publish requests as sent
    by VANTIQ and appropriately formatting the results.

## How to Run the Program<a name="jdbcMain" id="jdbcMain"></a>

1.  In order to effectively use the JDBC Source, you will need to download the appropriate JDBC Driver with respect to the 
    SQL Database you are using. Once you have installed this, you will need to create an environment variable named
    `JDBC_DRIVER_LOC` that contains the location of the jar file (i.e. `/Users/yourName/somePath/mysql-connector-java-
    8.0.12.jar`)
2.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
3.  Run `./gradlew jdbcSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/jdbcSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/jdbcSource/bin/jdbcSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit `<install location>/jdbcSource/src/main/resources/log4j2.xml`. Here is
its [documentation](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger names for each class is
the class's fully qualified class name, e.g. "io.vantiq.extjsdk.ExtensionWebSocketClient".  

To edit the logging for an IDE, change `<repo location>/jdbcSource/src/main/resources/log4j2.xml`. Changes
to this will be included in future distributions produced through gradle.

## Server Config File<a name="serverConfig" id="serverConfig"></a>

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:
```
authToken=vadfEarscQadfagdfjrKt7Fj1xjfjzBxliahO9adliiH-Dj--gNM=
sources=JDBC1
targetServer=https://dev.vantiq.com/
```

### Vantiq Options
*   authToken: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   sources: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   targetServer: Required. The Vantiq server hosting the sources.

## Adding to your Library

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew jdbcSource:assemble` or `.\gradlew jdbcSource:assemble` depending on the OS.
3.  Navigate to `<repo location>/vantiq-extension-sources/jdbcSource/build/libs` and copy `jdbcSource.jar` into your project.
4.  Add the dependencies found in `<repo location>/vantiq-extension-sources/jdbcSource/build.gradle` to your project.

## Source Configuration Document<a name="srcConfig" id="srcConfig"></a>

The Configuration document may look similar to the following example:

    {
       "jdbcConfig": {
          "general": {
             "username": "root",
             "password": "sqlPassword",
             "dbURL": "jdbc:mysql://localhost/myDB?useSSL=false&serverTimezone=UTC"
             "pollRate": 3000
             "pollQuery": "SELECT * FROM myTable"
          }
       }
    }

### Options Available for General
*   **username**: Required. This is the username that will be used to connect to the MySQL Database.
*   **password**: Required. This is the password that will be used to connect to the MySQL Database.
*   **dbURL**: Required. This is the URL corresponding to the MySQL Database that you will connect to.
*   **pollRate**: Optional. If specified, you must specify the pollQuery as well. This option allows you to specify a polling 
    rate indicating the frequency (in milliseconds) at which the pollQuery will be executed. The value must be a positive
    number greater than 0, (i.e. 3000 --> executing every 3 seconds).
*   **pollQuery**: Optional. If specified, you must specify the pollRate as well. This option indicates the SQL Query that
    will be executed by the JDBC Source, (frequency assigned by the pollRate). The SQL Query must be a **SELECT** statement,
    and the returned data will be sent as a VANTIQ Notification to the source. The data can be captured by creating a VANTIQ
    Rule, as per the following example:
    
    ```
    RULE checkSourceNotification
    WHEN MESSAGE ARRIVES FROM JDBC1 AS message

    log.info(message.queryResult.toString())
    ```

## Messages from the Source<a name="msgFormat" id="msgFormat"></a>

Messages from the source are JSON objects in the following format:
```
{
    queryResult: [{columnName:columnValue, columnName:columnValue, etc..}, etc..],
}
```
The queryResult field contains an ArrayList of Maps, each representing a row of data. Each map is a series of key-value pairs
with the keys being the column name and the values being the column value.

## Queries

In order to interact with the JDBC Source, one option is to write VAIL queries against the source. To do this, you will need 
to specify the SQL Query you wish to execute against your database as part of the WITH clause. The SQL Queries used here must 
only be **SELECT STATEMENTS**. Here is an example of a VANTIQ Procedure querying against a JDBC Source.

```
PROCEDURE queryJDBC()

try {
    SELECT * FROM SOURCE JDBC1 AS results WITH 
    query: "SELECT id, first, last, age FROM Employees"
    
    // Do whatever you want with the returned data stored in 'results'
    log.info(results.queryResult.toString())
} catch (error) {
    exception(error.code, error.message)
}
```

## Publishes

Another method to interact with the JDBC Source is to write VAIL publishes to the source. To do this, you will need to
specify the SQL Query you wish to execute against your database as part of the Publish Parameters. The SQL Queries used here 
can be **CREATE**, **INSERT**, **DELETE**, **DROP**, or other commands supported by your SQL database. Here is an example of a 
VANTIQ Procedure publishing to a JDBC source.

```
PROCEDURE updateJDBC()

try {
    var sqlQuery = "INSERT INTO Employees VALUES (1, 100, 'Santa', 'Claus');"
    PUBLISH {"query":sqlQuery} to SOURCE JDBC1
} catch (error) {
    exception(error.code, error.message)
}
```

## Error Messages

Query errors originating from the source will always have the code be the FQCN with a small descriptor attached, and
the message will include the exception causing it and the request that spawned it.

The exception thrown by the JDBC Class will always be a VantiqSQLException. This is a wrapper around the traditional 
SQLException, and contains the Error Message, SQL State, and Error Code from the original SQLException.

## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must create an environment variable named JDBC_DRIVER_LOC which points to the 
appropriate JDBC Driver .jar file. Additionally, you must first add the SQL Database username, password, and URL to your 
gradle.properties file in the ~/.gradle directory as follows:

```
    EntConJDBCUsername=<yourUsername>
    EntConJDBCPassword=<yourPassword>
    EntConJDBCURL=<yourURL>
```

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
