# Overview

The following documentation outlines how to incorporate a JDBC Source as part of your project. This allows a user to 
construct applications that interact with a SQL Database, and supports almost all standard SQL Commands. These interactions 
include the ability to run queries against the aforementioned SQL Database, periodically poll the database, and use all of the 
returned data in the given project.

In order to incorporate this Extension Source, you will need to set up your local machine with a JDBC Driver that can connect 
to your SQL Database. Once you have done this, you will need to create the Source in the VANTIQ Modelo IDE. The documentation 
has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your VANTIQ Modelo IDE](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more 
information.

The user must [define the JDBC Source implementation](../README.md#-defining-a-typeimplementation) in the VANTIQ Modelo IDE. For an example of the definition, 
please see the [*jbdcImpl.json*](src/test/resources/jdbcImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *jdbcExample.zip* can be found in the *src/test/resources* directory.

*   It should be noted that this example connects to a MySQL database.
*   In order to activate the pollTime/pollQuery, simply remove the comment prepending the pollTime.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **JDBCMain** -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   **JDBCCore** -- Coordinates the connections to the database, and sends the resulting data back to VANTIQ Modelo if
    necessary.
*   **JDBCHandleConfiguration** -- Sets up the JDBC connection based on the source's configuration document, and
    initializes the queryHandler and publishHandler.
*   **JDBC** -- The class that directly interacts with the JDBC Driver, executing the query and publish requests as sent
    by the VANTIQ Modelo IDE and appropriately formatting the results.

## How to Run the Program

1.  In order to effectively use the JDBC Source, you will need to download the appropriate JDBC Driver with respect to the 
    SQL Database you are using. Once you have installed this, you will need to create an environment variable named
    `JDBC_DRIVER_LOC` that contains the location of the jar file (*i.e.* `/Users/yourName/somePath/mysql-connector-java-8.0.12.jar`)
2.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
3.  Run `./gradlew jdbcSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/jdbcSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/jdbcSource/bin/jdbcSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit the logging config file `<install location>/jdbcSource/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:
```
authToken=vadfEarscQadfagdfjrKt7Fj1xjfjzBxliahO9adliiH-Dj--gNM=
sources=JDBC1
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
(#pre) to make sure you have properly added a Source Definition to VANTIQ Modelo. Once this is complete, you can select JDBC
(or whatever you named your Source Definition) as the Source Type. You will then need to fill out the Source Configuration 
Document.

The Configuration document may look similar to the following example:

    {
       "vantiq": { 
          "packageRows": "true"
       },
       "jdbcConfig": {
          "general": {
             "username": "sqlUsername",
             "password": "sqlPassword",
             "dbURL": "jdbc:mysql://localhost/myDB?useSSL=false&serverTimezone=UTC"
             "pollRate": 3000
             "pollQuery": "SELECT * FROM myTable"
          }
       }
    }

### Options Available for vantiq
*   **packageRows**: Required. This value **MUST** be set to "true". In future versions of the JDBC Source, setting this value 
    to "false" will allow the data to be processed as a stream of rows, instead of an array containing all the rows.

### Options Available for jdbcConfig
*   **username**: Required. This is the username that will be used to connect to the SQL Database.
*   **password**: Required. This is the password that will be used to connect to the SQL Database.
*   **dbURL**: Required. This is the URL corresponding to the SQL Database that you will connect to.
*   **pollRate**: Optional. If specified, you must specify the pollQuery as well. This option allows you to specify a polling 
    rate indicating the frequency (in milliseconds) at which the pollQuery will be executed. The value must be a positive
    number greater than 0, (*i.e.* 3000 --> executing every 3 seconds).
*   **pollQuery**: Optional. If specified, you must specify the pollRate as well. This option indicates the SQL Query that
    will be executed by the JDBC Source, (frequency assigned by the pollRate). The SQL Query must be a **SELECT** statement,
    and the returned data will be sent as a Notification to the source. The data can be captured by creating a Rule in the
    VANTIQ Modelo IDE, as in the following example:
    
    ```
    RULE checkSourceNotification
    WHEN MESSAGE ARRIVES FROM JDBC1 AS message
    try {  
        // Creating a map of each row, used to INSERT into our type
        var resultData = {}
        resultData.id = message.id
        resultData.age = message.age
        resultData.first = message.first
        resultData.last = message.last

        // Inserting the map into our JDBCType
        INSERT JDBCType(resultData)
    } catch (error) {
        // Catching any errors and throwing the exception.
        exception(error.code, error.message)
    }
    ```

## Messages from the Source

Messages that are sent to the source as Notifications from the pollQuery are JSON objects in the following format:
```
{
   {columnName:columnValue, columnName:columnValue, etc..}
}
```
The data is formatted as a HashMap which represents a row of data. Each map is a series of key-value pairs with the keys 
being the column name and the values being the column value. If multiple rows of data are returned by the pollQuery, each row
will be sent as a unique Notification. 

*   (**NOTE:** Multiple rows of data sent as Noticfications will be returned in a random order.)

## Select Statements

In order to interact with the JDBC Source, one option is to use VAIL to select from the source. To do this, you will need 
to specify the SQL Query you wish to execute against your database as part of the WITH clause. The SQL Queries used here must 
only be **SELECT STATEMENTS**. The data will be returned as an array of the resulting rows. The following is an 
example of a Procedure created in VANTIQ Modelo querying against a JDBC Source.

```
PROCEDURE queryJDBC()

try {        
    // Normal SELECT Statement in VAIL, but using WITH Clause is important.
    SELECT * FROM SOURCE JDBC1 AS results WITH
    // WITH Clause specifies the query, whose value is the SQL Query.
    // This parameter must be named 'query'.
    query: "SELECT id, first, last, age FROM Test" 

    {
        // Iterating over each row of data in 'results'
        FOR (row in results) {
            // Creating a map of each row, used to INSERT into our type
    	    var resultData = {}
    	    resultData.id = row.id
    	    resultData.age = row.age
    	    resultData.first = row.first
    	    resultData.last = row.last
            
	    // Inserting the map into our JDBCType
    	    INSERT JDBCType(resultData)
        }
    }
} catch (error) {
    // Catching any errors and throwing the exception.
    exception(error.code, error.message)
}
```

## Publish Statements

Another method to interact with the JDBC Source is to use VAIL to publish to the source. To do this, you will need to
specify the SQL Query you wish to execute against your database as part of the Publish Parameters. The SQL Queries used here 
can be **CREATE**, **INSERT**, **DELETE**, **DROP**, or other commands supported by your SQL database. The following are 
examples of Procedures created in VANTIQ Modelo publishing to a JDBC source.

**Creating a table:**

```
PROCEDURE createTable()

try {
    // The SQL Statement that the JDBC Source will execute
    var sqlQuery = "create table Test(id int not null, age int not null, first varchar (255), last varchar (255));"
    
    // Normal PUBLISH Statement in VAIL, passing the 'sqlQuery' as a parameter in the 
    // 'query' field. The field must be named 'query'.
    PUBLISH {"query":sqlQuery} to SOURCE JDBC1
} catch (error) {
    // Catching any errors and throwing the exception.
    exception(error.code, error.message)
}
```

**Inserting to table:**

```
PROCEDURE insertJDBC()

try {
    // The for-loop is used to insert multiple rows of data
    FOR i in range(0, 5) {
       	// Creating the values that will be inserted
        var id = i.toString()
	var age = (20+i).toString()
	var first = "Firstname" + i.toString()
	var last = "Lastname" + i.toString()

	// The SQL Statement that the JDBC Source will execute
	// Notice that when inserting Strings, the values must be surrounded by ''. This 
	// can be seen around the 'first' and 'last' values.
        var sqlQuery = "INSERT INTO Test VALUES (" + id + ", " + age + ", '" + first + "', '" + last + "');"

        // Normal PUBLISH Statement in VAIL, passing the 'sqlQuery' as a parameter in the 
        // 'query' field. The field must be named 'query'.
        PUBLISH {"query":sqlQuery} to SOURCE JDBC1
    }
} catch (error) {
    // Catching any errors and throwing the exception.
    exception(error.code, error.message)
}
```

## Error Messages

Query errors originating from the source will always have the code be the fully-qualified class name with a small descriptor 
attached, and the message will include the exception causing it and the request that spawned it.

The exception thrown by the JDBC Class will always be a VantiqSQLException. This is a wrapper around the traditional 
SQLException, and contains the Error Message, SQL State, and Error Code from the original SQLException.

## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must create an environment variable named JDBC\_DRIVER\_LOC which points to the 
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
