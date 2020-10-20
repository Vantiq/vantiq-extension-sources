# Overview

The following documentation outlines how to incorporate a EasyModbus Source as part of your project. This allows a user to construct applications that interact with a PLC using the EasyModbus layer, thus being able to get coils , descreets , registery or holdingregistry information , and able to update coils and holdingregistry. 

In order to incorporate this Extension Source, you will need to set up your local machine with a EasyModbus client jre that can connect implement to your EasyModbus server . Once you have done this, you will need to create the Source in the VANTIQ Modelo IDE. The documentation 
has been split into two parts, [Setting Up Your Machine](#machine) and [Setting Up Your VANTIQ](#vantiq).

# Prerequisites <a name="pre" id="pre"></a>

In order to compile correctly , one must download the EasyModbus Java Version and locate the EasyModbusJava.jar in one of the path folder. you can access the download it [*here*](http://easymodbustcp.net/en), you can download from the site simulated server as well , I used the one which is .net based.


**IMPORTANT:** Read the [Testing](#testing) section before building this project.
for validating the solution tests included in the extension source one must activate the easyModbus server simultion. The *src/test/resources* contains [*EasyModbus Server Simulator (.NET Version).zip*](src/test/resources) which containts server side simulation of EasyModbus protocol


# Setting Up Your Machine <a name="machine" id="machine"></a>

## Repository Contents

*   **EasyModbusMain** -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   **EasyModbusCore** -- Coordinates the connections to the EasyModbus server, and sends the resulting data back to VANTIQ Modelo if necessary.
*   **EasyModbusHandleConfiguration** -- Sets up the EasyModbus connection based on the source's configuration document, and
    initializes the queryHandler and publishHandler.
*   **EasyModbus** -- The class that directly interacts with the EasyModbus Driver, executing the query and publish requests as sent
    by the VANTIQ Modelo IDE and appropriately formatting the results.



## Prerequisite 
download from [*here*](http://easymodbustcp.net/en/) the java version , you can download from the site simulated server as well , i used the one which is .net based. 

## How to Run the Program
1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew easyModbusSource:assemble`.
3.  Navigate to `<repo location>/vantiq-extension-sources/easyModbusSource/build/distributions`. The zip and tar files both contain 
    the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
5.  Run `<install location>/easyModbusSource/bin/easyModbusSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit the logging config file `<install location>/easyModbusSource/src/main/resources/log4j2.xml`,
which is an [Apache Log4j configuration file.](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger 
name for each class is the class's fully qualified class name, *e.g.* "io.vantiq.extjsdk.ExtensionWebSocketClient".  

## Server Config File

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:
```
authToken=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
sources=EasyModbus1
targetServer=https://dev.vantiq.com/
```

### Vantiq Options
*   **authToken**: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   **sources**: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   **targetServer**: Required. The Vantiq server hosting the sources.

# Setting Up Your VANTIQ <a name="vantiq" id="vantiq"></a>

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the [Extension Source README.md](../README.md) for more information.

In order to incorporate this Extension Source, you will need to create the Source Implementation in the Vantiq system.


## Source Implementation

When creating a EasyModbusSource Extension source, you must first create the source implementation. This is done by using the [*easyModbusImpl.json*](src/test/resources/easyModbusImpl.json)  file found in src/test/resources/csvImpl.json. To make the source type known to Vantiq, use the vantiq cli command

```
vantiq -s <profileName> load sourceimpls <fileName>
```
where <profileName> is replaced by the Vantiq profile name, and <fileName> is the file to be loaded.

(You can, of course, change the source implementation name from that provided in this definition file.)

That completed, you will need to create the Source in the Vantiq system.

## Source Configuration

To set up the Source in the VANTIQ system, you will need to add a Source to your project.  Make sure you have properly added a Source Implementation to Vantiq. Once this is complete, you can select EasyModbus (or whatever you named your Source Definition) as the Source Type. You will then need to fill out the Source Configuration Document.

The Configuration document may look similar to the following example:
```
{
	"easyModbusConfig":{
    	"general":{
  	 	"TCPAddress": "127.0.0.1",
	   	"TCPPort": 502,
 	  	"Size": 20,
	   	"pollTime": 1000,
 	  	"pollQuery": "select * from coils"
        }
	}
}
```    
### Configuration

*   **TCPAddress**: Required. The TCP address of the EasyModbus server.
*   **TCPPort**: Required. The Tcp Port  of the EasyModbus server.
*   **Size**: Optional. Size of the EasyModbus data vectors. 
*   **pollTime**: Optional. If specified, you must specify the pollQuery as well. This option allows you to specify a polling 
    rate indicating the frequency (in milliseconds) at which the pollQuery will be executed. The value must be a positive
    number greater than 0, (*i.e.* 3000 --> executing every 3 seconds).
*   **pollQuery**: Optional. If specified, you must specify the pollTime as well. This option indicates the SQL Query that will be executed by the EasyModbus Source, (frequency assigned by the pollTime). The SQL Query must be a **SELECT** statement, and the returned data will be sent as a Notification to the source. The data can be captured by creating a Rule in the VANTIQ Modelo IDE 



## Select Statements

In order to interact with the EasyModbus Source, one option is to use VAIL to select from the source. To do this, you will need to specify the SQL liked Query you wish to execute against your EasyModbus source. The data will be returned to VANTIQ as a json buffer , which contains all fields of the different storages.

used here would be **SELECT STATEMENTS**. The data will be returned to VANTIQ as a set of messages, where each message 
contains some number of rows.

There are 4 types of information you can select from the EasyMudServer , registers and holdingRegisters which return list of integers , and discrete and coils which return list of booleans. 

you can use 1 of the 4 available select statments :
```
    select * from source EasyModbus1 as r with query : "select * from registers"
 	select * from source EasyModbus1 as r with query : "select * from holdingregisters"
 	select * from source EasyModbus1 as r with query : "select * from discrete"
 	select * from source EasyModbus1 as r with query : "select * from coils"
```
those select statments will return the entire list of regiaters , holdingRegisters , discrete or coils 
```
    select * from source EasyModbus1 as r with query : "select item3 from holdingregisters"
```
The above select statment will return the 4th holidingregister value indicating that fact using the index property 

the following select statment will return a specific value from the requested list : 

```
[
   {
      "registers": [
         {
            "index": 3,
            "value": 1
         }
      ]
   }
]
```
the following select statment will return a specific value from the requested list , actualy the first value in the colis list , noticed that the returned value is boolean and not integer as the previos example

```
select * from source EasyModbus1 as r with query : "select item0 from coils" 
```

```
[
   {
      "values": [
         {
            "index": 0,
            "value": true
         }
      ]
   }
]
```

## Publish Statements

Using Publish statmet one can updates values in the PLC, EasyModbus server accept update request only for
the holdingregisters and the coils list . 

publish {"type":"holdingregisters"} to SOURCE EasyModbus1 using {body=r}

where r is a json object similar to the result of the select statment , that above publisg statmet will update the entire list , one can use the proipery `address` in order to be bale to updarte specific value in the list , for example:
```
publish {"type":"holdingregisters",address:3} to SOURCE EasyModbus1 using {body=r}
```
will update only the 4th element using the value in the r json object , expecting the correct element with the right index value to exists - in this case 3. 

the follwoing statement will select the 4th value and update it with a different value , this can be shown using the server side simulator 
```
    select * from source EasyModbus1 as r with query : "select item3 from holdingregisters"
	r[0].registers[0].value =r[0].registers[0].value+1 // 700  //r.registers[0]+1 
	publish {"type":"holdingregisters",address:3} to SOURCE EasyModbus1 using {body=r}
```

a similar example on coils will look 

```
    select * from source EasyModbus1 as r with query : "select item0 from coils" 
    r[0].values[0].value = !r[0].values[0].value 
    publish {"type":"coils",address:3} to SOURCE EasyModbus1 using {body=r}

```

## Messages from the Source

Messages that are sent to the source as Notifications from the pollQuery are JSON objects with a similar format as the example above.The example below is the result of query values from registers ,
the event in case of query from coils is a bit different and apears after the above example .

result of select * from registers 
```
{
    "registers": [
        {
        "index": 0,
        "value": 0
        },
        {
        "index": 1,
        "value": 0
        },
        .
        .
        .
        {
        "index": 19,
        "value": 0
        }
    ]
}
```
result of user select * from coils
    
```
    {
    "values": [
        {
            "index": 0,
            "value": false
        },
        {
            "index": 1,
            "value": false
        },
        .
        .
        .
        {
            "index": 19,
            "value": false
        }
    ]
    }

```
## Running the example
As noted above, the user must define the EasyModbus Source implementation in the Vantiq system. For an example of the definition, please see the `easyModbusImpl.json` file located in the `src/test/resources directory`.

Additionally, an example project named `EasyModbusExample.zip` can be found in the `src/test/resources directory`. 

You must activate the EasyMudBus simulation , run the application in one of the zip file, i used that one [*EasyModbus Server Simulator (.NET Version).zip*](src/test/resources)

It should be noted that this example looks for EasyModbus simulation on the local host 127.0.0.1 , listen on port 502


## Error Messages

Query errors originating from the source will always have the code be the fully-qualified class name with a small descriptor attached, and the message will include the exception causing it and the request that spawned it.

The exception thrown by the EasyModbus Class will always be a VantiqEasyMosbusException. This is a wrapper around the traditional Exception, and contains the Error Message, and Error Code from the original Exception.

Error Code      Description 
1000            EasyDombus is not connected
1001            General Exception 
1002            Unsupported Query target
1003            Unsupported operation command ( only select and publish are supported )
1004            Unsupported Query Field, must contains offeset (ex. Coil0)
1005            Unsupported Query Syntax , no select statment. 
1006            Unexpected QUery entoty, check the from statment 
1007            Unsupported Query Field, must start with item (ex. item0)

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

HikariCP, okhttp3, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
