# Overview

This document outlines how to incorporate a CSV Source into your project. The CSV source allows a user to construct applications that detect creation of CSV files in pre-defined folder and upload the file content as events to Vantiq. The extension source enables control on the names of the different attribute based on the order in the CSV file, and change the name or delete the file after processing. 
The extension source can handle multiple files parallel;
this is controlled by parameters in the config section. 

The extension source supports only notifications from source to Vantiq, those contain the file content converted to json messages based on the schema definition given in the configuration. 

When new file is created in pre configured folder (with a name matching the configurable pattern), the file content is parsed and  converted to json.
That json is then sent to Vantiq as incoming event. 

When the process starts, based on configuration, 
the extension can process all files exist in the input folder which support the name file pattern.
In this way, in cases where the extension is not running during file updates, it will resend all accumulated files once it restarted. 

In addition to handling delimiter-separated fields, this connector can extract fields from fixed positions in the record.
This is configured as described below.

It is also possible to have the connector write files. This operation is described below.

The documentation has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your Vantiq](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **CSVMain** -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   **CSVCore** -- Coordinates the connections to Vantiq, responsible for managing the connection with Vantiq Server
*   **CSVHandleConfiguration** -- Sets up the trigger to the file system for detect and processed new csv file 
*   **CSV** -- The class that directly interacts with the file system watch service, detects the file and processes it. 

## How to Run the Program

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew CSVSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/CSVSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/CSVSource/bin/CSVSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument. Note that the `server.config` file can be placed in the `<install location>/CSVSource/serverConfig/server.config` or `<install location>/CSVSource/server.config` locations.

## Logging
To change the logging settings, edit the logging config file `<install location>/CSVSource/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File
(Please read the [SDK's server config documentation](../extjsdk/README.md#serverConfig) first.)

### Vantiq Options

* **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
* **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be removed when read.
* **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Vantiq <a name="vantiq" id="vantiq"></a>

An understanding of the Vantiq Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more information.

In order to incorporate this Extension Source, you will need to create the Source Implementation in the Vantiq system.

## Source Implementation

When creating a CSVSource Extension source,
you must first create the source implementation.
This is done by using the `csvImpl.json` file found in `src/test/resources/csvImpl.json`.
To make the source type known to Vantiq, use the `vantiq` cli command

```
vantiq -s <profileName> load sourceimpls <fileName>
```

where `<profileName>` is replaced by the Vantiq profile name, and `<fileName>` is the file to be loaded.

(You can, of course, change the source implementation name from that provided in this definition file.)

That completed, you will need to create the Source in the Vantiq system. 

## Source Configuration

To set up the Source in the Vantiq system, you will need to add a Source to your project. Make sure you have properly added a Source Implementation to Vantiq. Once this is complete, you can select CSV (or whatever you named your Source Implementation) as the Source Type. You will then need to fill out the 
Source ConfigurationDocument.

The Configuration document may look similar to the following example:

```json
    {
        "csvConfig": {
            "fileFolderPath": "d:/tmp/csv",
            "filePrefix": "eje",
            "fileExtension": "csv",
            "maxLinesInEvent": 200,
            "skipFirstLine": true,
            "delimiter":",",
            "processNullValues":false,
                "schema": {
                    "field0": "value",
                    "field2": "flag",
                    "field1": "YScale"
                }
            },
        "options": {
            "maxActiveTasks": 2,
            "maxQueuedTasks": 4,
            "processExistingFiles": true,
            "extensionAfterProcessing": "csv.done",
            "deleteAfterProcessing": false
        }
    }
```

### Configuration
*   **fileFolderPath**: Required. The folder in which this source will look for CSV files. 
*   **filePrefix**: Optional. The prefix of the file pattern to look for, if not set any file name will be accepted. 
*   **fileExtension**: Required. The file extension of the files to be processed 
*   **maxLinesInEvent**: Required. Determine how many lines from the CSV file will be sent in a single message to the server. Depending on the number of the lines of the CSV file, a high value might result in messages too large to process efficiently or a memory exception. 
*   **skipFirstLine**: Optional , skipping first line avoiding processing it when used as header of the csv file. 
*   **delimiter**: the delimiter to be used when parse the CSV file, default is ",", the system will step over null values which might be in the result of the split operation. 
*   **processNullValues**: in case of null value ( means two consecutive delimiters in file) determine if 
the schema filed index should be incremented or not. For example, for the following line _1,,,f_,
determine if *field1* is "f" or *field3* is "f". 
*   **fixedRecordSize**: Optional , fixed length record size , default value is the calculated value based on the schema fields.unsetting the value means that a field at the end of the record must be specified. If it is not, the record length will be incorrect , the last fiels should include the length of the Eol characters as well, therefor it is most recommanded to set that value which,  must include the Eol charachters as well. 


### Schema Configuration
Schema can be used to control the field names on the uploaded event. If no name is assigned, 'fieldX' will be used where 'X' is the index of the field in the line.  For example field0, field1, etc. 

Those values can be used to override that default behavior for example, the following 

will cause the attribute of the 3rd value in each line to be called "address". 
So instead of loading event: 

`{ field0:1, field1:true, field2:"there"}` it will upload `{ field0:1, field1:true, address:"there"}`
this can save conversion processing on the server.

### Fixed Length Records
CSV Reader supports extracting fields based on fixed length positions in each record.
To specify extraction based on fixed positions, set the configuration 
key `fileType` to the value: `FixedLength`
If this key is missing, it will default to `DelimitedFields`.

The schema for a `FixedLength` file type is as follows:

For each attribute, specify the name of the attribute and the positional information.
For example using the `code`, `name`, and `price` from the above example,
each attribute is defined as follows:
* **offset** : the offset of the field from the beginning of the record
* **length** : the size of the field
* **type** : the type of the field : string, int, etc 
* **charset** : optional -- the character set charset to use when reading the field. 
* **reversed** : Optional, reverse the attribute value , mainly for support RTL names.
If `true`, this causes the String attribute read from the file to be reversed.
So _value_ would become _eulav_ if `reversed` is set to `true`.



the configuration should be similar to
```
   "csvConfig": {
      "fileFolderPath": "d:/tmp/csv",
      "filePrefix": "plu",
      "fileExtension": "txt",
      "maxLinesInEvent": 50,
      "waitBetweenTx": 10,
      "FileType": "FixedLength",
      "schema": {
         "code": {
            "offset": 0,
            "length": 13,
            "type": "string"
         },
         "name": {
            "offset": 14,
            "length": 20,
            "type": "string",
            "charset": "Cp862",
            "reversed": true
         },
         "weighted": {
            "offset": 35,
            "length": 1,
            "type": "string"
         },
         "price": {
            "offset": 37,
            "length": 6,
            "type": "string"
         },
         "cost": {
            "offset": 44,
            "length": 6,
            "type": "string"
         },
         "department": {
            "offset": 51,
            "length": 3,
            "type": "string"
         }
      }
   },
```


### Execution Options

* **maxActiveTasks**: Optional. The maximum number of threads running at any given point. This is the number of CSV files being processed simultaneously. This value must be a positive integer. Default value is 5.
* **maxQueuedTasks**: Optional. The maximum number of queued tasks at any given point for CSV files, overflowing that number might cause some new files to be missed. This value must be a positive integer. Default value is 10.
* **processExistingFiles**: Optional. If set to `true`, the service will process all files already existing in the folder `fileFolderPath` (filtered using `filePrefix` and the `fileExtension`). Otherwise the service will process only new files.  Default is`false`.
* **extensionAfterProcessing**: Optional. Rename the file after it has been processed to avoid reprocessing (_e.g._ for cases where `processExistingFiles` is set to `true`).  The default value is combination of the 'fileExtension' and `done`.  For example `.csv.done` when `fileExtension` set to `.csv`.
* **deleteAfterProcessing**: Optional. Delete the processed file only if processed successfully to avoid reprocessing in cases where `processExistingFiles` is set to `true`. Default value is `false`.

**Note**: the sum of **maxActiveTask** and **maxQueuedTasks** is the maximum number of files that can be processed simultaneously.
If more than this number is attempted,
they will be ignored.

## Messages from the Source

Messages that are sent to the source as Notifications are JSON objects in the following format:

```json
{
   "file": "d:/tmp/csv/ejesmall.csv",
   "segment": 39,
   "lines": [
      {
         "flag": "1",
         "value": "109411211",
         "YScale": "13.8000"
      },
      {
         "flag": "1",
         "value": "109415211",
         "YScale": "112.3507"
      },
      {
         "flag": "1",
         "value": "109416211",
         "YScale": "-111.5000"
      },
      {
         "flag": "1",
         "value": "109432211",
         "YScale": "1.0440"
      },
      {
         "flag": "1",
         "value": "109433211",
         "YScale": "-0.3000"
      }
   ]
}
```

where the size of the `lines` property will not exceed the value from the `maxLinesInEvent` configuratipon parameter.

## Event structure
Each event consists on the following structure:

* **file**: the source file from which the data was extracted
* **segment**: the index of the segment, in case the number of lines in the file exceed the `maxLinesInEvent`, the file will be divided to multiple segments. 
* **lines**: the json buffer itself, where the key values are used from the schema definition. 

## Running the example

As noted above, the user must [define the CSV Source implementation](../README.md#-defining-a-typeimplementation) in the Vantiq system.
For an example of the definition, 
please see the [*csvImpl.json*](src/test/resources/csvImpl.json) file located in the *src/test/resources* directory.

Additionally, an example project named *CSVExample.zip* can be found in the *src/test/resources* directory, together with *ejesmall.csv* which is a csv file with structure relates to the CSVExample project types.

* It should be noted that this example looks for csv files in folder d:\tmp\csv with names filtered as eje*.csv.
You will probably need to change this for your environment.

The file the `/src/test/resource/ejesmall.csv` is aligned with the sample application, its content will be inserted to the `csvInputLines` type exists in the Vantiq application. 

Once you copy it to the input folder (default is `d:/tmp/csv`),
the extension source will send the file in multiple segments,
with the app will saving those in a type. 

## Writing CSV Files

It is also possible to have the connector write files. No addtional configuration is needed, the text will be UTF8 and
the assumption is that the line to write is produced by the server.
The VAIL SELECT statement is used to perform this work
The value returned by the VAIL SELECT statement describes the status of the operation.

Creating, appending to or deleting a csv file is done via a **select** statement.

Given a VAIL object defined as follows,
```
m = {
   path:"c:\tmp",
   file:"output.txt",
   content:[
      {"text","this,is,first,line"},
      {"text","this,is,second,line"}
   ]
}
```

The following select statement will create the `output.txt` file at folder `c:\tmp` containing 2 lines.
```
select * from source CSV1 with body= m, op="create"
```
To add additional lines to the file one can use the following command 
```
select * from source CSV1 with body= m , op="append"
```
To delete a file, the following select statement can be used. 
```
select * from source CSV1 with body= m , op="delete"
```
For all select statements, a response will be returned with the following structure:
```
   {
      "message": "File Appended Succeesfully",
      "value": "c:\\tmp\\output.txt",
      "code": "io.vantiq.extsrc.csvsource.success"
   }
```
code table is
io.vantiq.extsrc.csvsource.success - success .
io.vantiq.extsrc.csvsource.fileexists - File already exists
io.vantiq.extsrc.csvsource.nofolder - Folder does not exist 
io.vantiq.extsrc.csvsource.nofile - File does not exist 

## Error Messages

Parsing CSV errors originating from the source will always have the code be the fully-qualified class name with a small descriptor 
attached,
and the message will include the exception causing the problem and the originating request.

The exception thrown by the CSV Class will always be a VantiqCSVException.
This is a wrapper around the traditional exception, and contains the Error Message,
and Error Code from the original Exception.

## Testing <a name="testing" id="testing"></a>

In order to properly run the tests, you must add properties to your _gradle.properties_ file in the _~/.gradle_ directory. These properties include the Vantiq server against which the tests will be run.

One can control the configuration parameter for the testing by customizing gradle.properties file. The following shows what the gradle.properties file should look like:

```
    TestVantiqServer=<yourVantiqServer>
    TestAuthToken=<yourAuthToken>
    EntFileFolderPath=<yourDesiredFolderPath>
    EntFileExtension=<yourDesiredFileExtension>
    EntFilePrefix=<yourDesiredFilePrefix>
    EntMaxLinesInEvent=<yourDesiredLinesInEvent>
    EntDelimiter=<yourDesiredDelimiter>
    EntProcessNullValue=<True|False>
```

possible set of values might be:

```
    EntFileFolderPath="c:/tmp/csvtest"
    EntFileExtension="csv"
    EntFilePrefix="Export"
    EntMaxLinesInEvent=20
    EntDelimiter=","
    EntProcessNullValue="false"
```

will process only files from the following name pattern: 

```
    c:/tmp/csvtest/Export*.csv
```

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

HikariCP, okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
