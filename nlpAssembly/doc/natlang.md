
[CLUStudio]: https://language.cognitive.azure.com/home
[AzureCognitiveServicesDef]: https://azure.microsoft.com/en-us/services/cognitive-services/

[CLU_Documentation]: https://learn.microsoft.com/en-us/azure/cognitive-services/language-service/
[CONNECT_DIRECT_LINE]:  https://docs.microsoft.com/en-us/bot-framework/channel-connect-directline

[VANTIQ_NLS]: ../downloads/VantiqBase_CLU.json

[CLU_SOURCE_IMAGE]: assets/img/CLUSourceDefinition.png "Vantiq Source for CLU Application"
[NL_ARCHITECTURE]: assets/img/NLArch.png "Vantiq Macro Architecture for Natural Language Processing"
[NL_COLLAB_GRAPH]: assets/img/NLCollabGraph.png


# Natural Language Processing Reference Guide

## Introduction
In the (near) past, users often struggle(d) with learning how to work with applications.
New systems are focusing on more *people-literate* systems.
That is, the applications are responsible for providing an easy to understand interaction for their users.  

Vantiq now supports providing *natural language* interfaces.
This style of interface can be made available via a chatbot, thus manifesting in a chatroom in a collaboration, or via any other connections provided via the chatbot.  

Vantiq's natural language processing is provided in concert with an external source that provides the parsing and interpretation of the natural language utterances.
Incorporating natural language processing into a Vantiq application will require setting up and integrating this source into the Vantiq system.
Customers will need to define a [Conversational Language Understanding][CLUStudio] application for use by the Vantiq system.

Vantiq supports the Azure Conversational Language Understanding system.
LUIS V3 and V3 support in Vantiq is now deprecated and will be removed in a future release.
Customers are strongly encouraged to migrate any existing LUIS projects to Conversational Language Understanding. LUIS support at Microsoft is now somewhat limited.

**Note:** The processing of natural language is separate from the conversion of speech to text (or vice versa).  This guide focuses on the interpretation of natural language text;
obtaining the natural language text from speech is a separate exercise.

## Overview
<a name="SectionConcepts"></a>
### Concepts
To talk about building a natural language interface, there are a few concepts and terms to define.

* *Utterance* -- The natural language *uttered* by the application's user.
* *Intent* -- The thing the user is trying to accomplish.
* *Entity* or *Entities* -- Modifier(s) to the *intent*.
* *Intent Set* -- A set of functions intended for expression in some set of natural languages.
* *Natural Language Subset* -- A subset of some language that expresses an intent set. 
* *Interpretation* -- The converting of an *utterance* into an *intent* and some (possibly zero) *entities*.
* *Execution* -- The act of using the interpretation to accomplish the user's *intent*.
* *Interpreter Source* -- A source that provided the *interpretation* of an *utterance*.
* *Model* -- A trained runtime capable of interpreting a utteraance.
* *Deployment* -- An accessible instance of a Model running in as part of an Azure Language Resource.

#### Examples
The following are some example utterances and their interpretations

* *hi*
	* intent: `system.smalltalk.greetings`
* *list people*
	* intent: `system.list`
	* entities
		* `system.typename`, value 'people'
* *please count people whose age is less than 34*
	* intent: `system.count`
	* entities
		* `system.typename`, value 'people'
		* `system.propValue`, value '34'
		* `system.propName`, value 'age'
		* `system.comparator_lt`, value 'is less than'

Note that the interpretations above return information about the language specified.  They do NOT ensure, for example, that the resource 'people' exists, nor that it has a property 'age'.  That would happen during the *execution* phase.

### Architecture and Flow

The act of *interpretation* is, as noted, performed by an external (remote) source, `Interpreter Source`.
The interpreter source must be a [Conversational Language Understanding][CLUStudio] model and deployment.
These are part of the general group of [Cognitive Services][AzureCognitiveServicesDef].

Once your Conversational Language Understanding (CLU) application is defined and the model and deployment defined, it can be added to Vantiq as a Remote Source.

Often, a chatbot will be required as well.
Information about defining a chatbot can be found in Vantiq's CHATBOT Guide.

The overall system view is shown below.

![Vantiq Architecture Diagram][NL_ARCHITECTURE]

Utterances typically arrive via a chabot.
These utterances are then passed to the intepreter source (defined in Vantiq making use of the CLU deployment) for interpretation.
The interpretation contains the intents and entities that describe, in a *machine readable* manner, what actions are required.
This interpretation is returned to the Vantiq system, and the interpretation is acted upon or executed, and the results, if any, can be returned to the user.

Within the Vantiq application, the flow of information is dependent upon the application.  Typically, the interpretation is determined, and the result examined.
Assuming that a reasonable interpretation is returned, the execution of that interpretation is then invoked.
In subsequent sections, this process will be shown for a rule-based system and for a collaboration conversation.


## Application Design

### Language Design
A Conversational Language Understanding application contains a set of intents, utterances, and entities that it can process.
The CLU application, including an application specific [Natural Language Subset](#SectionConcepts) (NLS) for your [Intent Set](#SectionConcepts), is developed using the [Language Studio][CLUStudio].
If you wish to use the Vantiq language entities or intents, you should start by importing the Vantiq NLS.
More information is available in the [Vantiq NLS section](#SectionVantiqNLS).

The Vantiq NLS contains an English culture (a specific natural language *e.g.* English, French, *etc.*) for the [system intent set](#SectionSystemIntents) as well as the [smalltalk intent set](#SectionSmalltalkIntents).  These are described in more detail below.

<a name="SectionConventions"></a>
#### Conventions
In order to more easily distinguish custom elements from Vantiq elements, the following conventions are used.

* Things starting with **system.** are part of the Vantiq Intent Set, and are defined in the Vantiq NLS
* As per the CLU design, builtin entities have no periods (`.`) in their names (_e.g._, `datetimeV2`, `number`, `age`).
* The intent **None** is reserved by CLU, and is reported when it can find no suitable intent at all.
	* Note that many times, it will guess at something and return something with a low score. Vantiq will ignore returned intents whose score is lower than 0.65.  See [CLU Documentation][CLU_Documentation] for more details on how to interact with CLU.
* Other intent names are available to custom applications.

#### System Entities
There are a number of entities that are defined as part of the Vantiq NLS.  These may, of course, be used as part of the custom application.
These entities are described below.
It is important to note the following.
Natural language processing is imprecise.
Consequently, the entities returned can vary from exactly what would be expected.
Thus, it is important to have some flexibility in the interpretation to handle these inconsistencies.

* `system.typeName` -- generally corresponds to an Vantiq type name (*e.g.* collaborations, situations, projects).
* `system.plainWord` -- a single word. Within the Vantiq NLS, this is rarely used. In practice, this and `system.typeName` are used somewhat interchangeably.
* `system.propertyName` -- a property name used in a simple query
* `system.propertyValue` -- a value to be compared to the property name in a simple query
* comparators -- these are the comparison operations used within a simple query. There are a number of them.
	* `system.comparator_eq` -- equality
	* `system.comparator_ne` -- inequality
	* `system.comparator_lt` -- less than
	* `system.comparator_lte` -- less than or equal to
	* `system.comparator_gt	` -- greater than
	* `system.comparator_gte` -- greater than or equal to
* conditions -- these have to do with the special conditions (active, inactive, open, closed) used for collaborations and situations
	* `system.condition_active` -- corresponds to open, active, current, *etc.*
	* `system.condition_inactive` -- corresponds to closed, inactive, resolved, *etc.*

The Vantiq Intent Set also makes use of the `age`, `dateTimeV2`, and `number` from the CLU provided builtins. 

<a name="SectionSystemIntents"></a>
#### System Intents
The Vantiq Intent Set includes the following intents.  These are given with some examples from each intent.

* `system.describeType` -- this provides some basic information (properties and their types) about datatypes in the Vantiq system.
	* *describe person*
	* *outline collaboration*
* `system.count` -- this counts the instances of a type
	* *how many collaborations are there?*
	* *count person whose age < 34*
	* *count person whose age is greater than or equal to 80*
	* *please count Patients whose age exceeds 29*
* `system.list` -- this lists the instances of a type
	* *list person*
	* *list person whose name is fred*
	* *list person whose age exceeds 29*
* `system.showActive` -- this is specific to collaborations and situations, and shows the active/open/inactive/closed collaborations or situations
	* *show active collaborations since yesterday*
	* *show closed situations since the first of june*
* `system.endDiscussion` -- this closes a collaboration of which it is a part
	* *exit*
	* *end*
	* *bye*

[See Example](#SectionProcessingSummary)

<a name="SectionSmalltalkIntents"></a> 
##### *Smalltalk Intents*
The Vantiq Intent Set also includes a set of *smalltalk* intents.
These are basically chatter that users often try out.
They perform no real function other than providing some comfort and/or entertainment.

 * `system.smalltalk.greetings` -- a simple hello
 	* *hi*
 	* *g'day*
 * `system.smalltalk.thankYou` -- responds to being thanked
 	* *thanks*
 	* *thank you*
 * `system.smalltalk.mindframe` -- how does the system "feel"
 	* *are you happy?*
 	* *do you like your job?*
 * `system.smalltalk.chatbot` -- question about what life is like
	* *are you a chatbot?*
 * `system.smalltalk.bio` -- questions about how things started
 	* *tell me about yourself*
 	* *how did you get started*
 * `system.smalltalk.birthday` -- questions about one's birthday
 	* *when is your birthday*
 
#### Custom Intents
Custom intents are the intents that are specific to the Vantiq application.
The [system intents](#SectionSystemIntents) and [smalltalk intents](#SectionSmalltalkIntents) are provided by Vantiq, providing answers about the Vantiq system or general chatter, respectively.
For example, suppose there is an application for health care.
In such an application, there might be a type *patients*.
To find out who the patients are, one could use the Vantiq intent `system.list` and say *list patients.*.
However, in some environments, it may be more natural to ask about *who is sick*.
Providing a natural language interface allows the application to become more *people-literate*, to understand things from the perspective of its users rather than having to train the users to ask the correct questions.

<a name="SectionInterpreterSource"></a>
### Interpreter Source
Finally, the interpreter must be defined as an [Interpreter Source](#SectionConcepts).
This is a Vantiq Source that is set up to call a Microsoft Azure CLU application.

To define such an application, use the [Language Studio][CLUStudio] as outlined in the associated [documentation][CLU_Documentation].
If you are including the Vantiq NLS, create the application in the Language Studio.
See the [Vantiq Natural Language Subset section](#SectionVantiqNLS) for more details.

The Vantiq system will interact with this source using the POST method.

To create the source, find the URL from the [Language Studio][CLUStudio].  Specifically, go to the _Deploying a model_ item, and press the _Get prediction URL_ button, looking for the _Prediction URL'.  Provide that as the `Server URI` property.
The `Response Type` property should be `application/json`.
The `Headers` property should have one header `Ocp-Apim-Subscription-Key`, with a value corresponding to the subscription key of your CLU application.
Generally, this value is sensitive and should be stored in a Vantiq Secret.
The value can be provided in the header using `@secret(secretName)`.
Please set the polling properties `Polling Interval` property to 0.
All other properties can be left unset.

A source for a CLU application is shown below.
Here, we've stored our Azure subscription key in the Vantiq Secret `AzureOCPKey`.

![CLU Source Example][CLU_SOURCE_IMAGE]

### Working with Natural Language in Vantiq
Generally speaking, natural language processing in Vantiq involves two phases:

* Interpret the utterance (figure out what was said)
* Execute the Interpretation (respond appropriately)

This section describes how those are accomplished in various scenarios.

#### Importing the Natural Language Assembly

To use these Vantiq services, you will need to install the Natural Language Assembly.  In the Vantiq public catalog, please find and install the assembly named `com.vantiq.nlp.NaturalLanguageProcessing`.  There are no configuration parameters necessary.

#### Processing
When a natural language text is obtained, it must be interpreted to determine what function is requested.
This section outlines the basic processing and how to accomplish it.
<a name="SectionPrepTextInterp"></a>
#### Prepare Text for Interpretation
Different interactions can produce text that is formatted as something other than plain text.
For example, Slack uses an XML-esque encoding of various characters, and these must be removed for the CLU service to interpret things.

To do this, use the `NLUtils.prepareText()` procedure.  This procedure takes three parameters:

* `channel` -- this is the channel or style of input involved. Choices include, but are not limted to, the following:
	* slack
	* directline
	* msteams
	* skype
* `incoming` -- a boolean indicating if this is an incoming or outgoing message.
This same procedure is used to prepare outgoing text for its appropriate channel.
* msg -- the text of the message to be prepared.

The procedure returns the prepared text.

```js
var preparedText = NLUtils.prepareText(message.channelId, true, message.text)
``` 

<a name="SectionInterpretText"></a> 
#### Interpret Text
To interpret the text, make use of the [interpreter source](#SectionInterpreterSource), and use the `NLCore.interpretConversationalQuery()` procedure.
This procedures takes 4 parameters.

* `natLangQuery` -- the prepared text to be interpreted
* `naturalLanguageSource` -- the name of the interpreter source
* `cluModel` -- the name of the CLU model to use
* `cluDeployment` -- the name of the CLU deployment to use.

The model is created using the _Training jobs_ function of the [Language Studio][CLUStudio], and the deployment is created using the _Deploying a model_ function of the Language Studio.

This procedure will return an `Object` that contains the following elements.

* `response` -- a high-level view of the intent. This contains an *intent specification*, formally defined [here](#SectionIntentSpecification). The interesting bits are reproduced here for convenience.
	* `intent` -- the text of the intent (*e.g.* `system.list`)
	* `entities` -- an array of the entities involved.  Each element contains
		* `name` -- the entity name (*e.g.* `system.typeName`)
		* `value` -- the entity value (*.e.g* "situations")
		* `rawEnt` -- the entity object returned by CLU
	* `query` -- the text (prepared) for which interpretation has been attempted
	* `rawIntent` -- the complete intent object returned by CLU
* `errorMsg` -- any error message returned.

Generally, the caller should check `errorMsg` for content.
If content is present there, the `response` field may not be complete, may be missing, or may contain an invalid interpretation.

```js
var sourceName = "cluSource"	// Using the name used in the example above.  
var modelName = <your model name>
var deploymentName = <your deployment name>
var interpretation = NLCore.interpretConversationalQuery(preparedText,
                sourceName, modelName, deploymentNamme)
var resultsToReturn
if (interpretation.errorMsg != null) {
	// return the error to the caller
	resultsToReturn = interpretation.errorMsg
} else {
	// Process the returned interpretation
}
```

<a name="SectionExecuteInterpretation"></a> 
#### Execute Interpretation
To execute the interpretation, the application may wish to make some decisions.
For example, some applications may not wish to show the Vantiq `system.*` intents through.
However, they may want to allow the `system.smalltalk` intents.
So, the execution phase may first wish to filter based on intent.

To execute only `system.smalltalk` intents, use the `NLCore.executeSmalltalkIntent()` procedure.
To execute `system.smalltalk` and other `system.*` intents, use the `NLCore.executeSystemIntent()` procedure (*i.e.* this will also execute smalltalk intents).
Both of these procedures take a single parameter:

* intent -- the contents of the `response` field returned by the `NLCore.interpretConversationalQuery()` procedure outlined as part of [Intepret Text](#SectionInterpretText).

To execute custom intents, the application should provide suitable execution code.
It will execute the intent based upon the `intent` and `entities` found in the `response` object.

```
if (interpretation.response.intent.startsWith("system.smalltalk")) {
	// Handle smalltalk
	resultsToReturn = executeSmalltalkIntent(interpretation.response)
} else if (interpretation.response.intent.startsWith("system.")) {
	// Assuming system intents should be processed...
	resultstoReturn = executeSystemIntent(interpretation.response)
} else {
	// Handle any custom intents
	resultsToReturn = ...
}
```

#### Prepare Response
This is done the same way (using the same procedure) as was shown for [preparation for interpretation](#SectionPrepTextInterp).
The difference is that `incoming` is set to `false`.
(Note that if results are being returned using `NLCore.publishResponse()`, the response will be *prepared* by that procedure.
In that case, this `prepareText()` need not be called.)

```js
var outputText = NLUtils.prepareText(message.channelId, false, resultsToReturn)
```

<a name="SectionProcessingSummary"></a> 
#### Processing Summary

Putting these code snippets together, a (mostly) complete processing of an natural language message is as follows.
Here, it is assumed that this procedure is called with a `channel` and `text` to be interpreted.

```js

package vantiq.example

import service com.vantiq.nlp.NLCore
import service com.vantiq.nlp.NLUtils
// NLExample.processMessage()
// 
// @param 	channel 	The channel over which this communication is happening
// @param 	text 		The message text to be acted upon
// @returns 				The text to return to the user

 
NLExample.processMessage(channel String, text String, prepareResponse Boolean)

// Strip formatting, encoding, etc.
var preparedText = NLUtils.prepareText(channel, true, text)

// Determine interpretation using the CLU Source
var sourceName = "cluSource"	// Using the name used in the example above.
var modelName = "someModelName"
var deploymentName = "someDeploymentName"  
var interpretation = NLCore.interpretConversationalQuery(preparedText, sourceName, modelName, deploymentName)
var resultsToReturn
if (interpretation.errorMsg != null) {
	// return the error to the caller
	resultsToReturn = interpretation.errorMsg
} else {
	// Process the returned interpretation
	if (interpretation.response.intent.startsWith("system.smalltalk")) {
		// Handle smalltalk
		resultsToReturn = executeSmalltalkIntent(interpretation.response)
	} else if (interpretation.response.intent.startsWith("system.")) {
		// Assuming system intents should be processed...
		resultstoReturn = executeSystemIntent(interpretation.response)
	} else {
		// Handle any custom intents
		resultsToReturn = ...
	}
}

var outputText
if (prepareResponse) {
	// Prepare the returned text for the channel
	outputText = NLUtils.prepareText(message.channelId, false, resultsToReturn)
} else {
	outputText = resultsToReturn
}
return outputText
```

### Natural Language via Rules
Vantiq Rules can be used to process natural language requests.
A simple rule that does so is shown below.

This rule makes use of another Vantiq-supplied procedure, `NLCore.publishResponse()`.
This procedure handles the preparing of the output text as well as the publication back to the caller.
For more details on this (and other) available services, please see the [Vail Resources](#SectionVantiqResources) section, more specifically [VAIL Procedures](#SectionVailProcedures).

```js
RULE ConverseViaRules
WHEN EVENT OCCURS ON "/sources/someChatbot" AS message

// Let's use the procedure just developed to perform the interpretation & execution

var ruleResponse = NLExample.processMessage(message.channelId, message.text, false)

// Here, let's reuse the same message to respond to the caller.
// However, it must replace the text with the response.

message.text = ruleResponse

// Here, use Vantiq procedure to handle the publication.
// publishResponse() will perform the output prepare for us, so we 
// suppressed that call above.

NLCore.publishResponse(message, message.text, "someChatbot")
```

### Apps


It is worth noting here that Vantiq's direct interaction with a chatroom (via the Vantiq Mobile App) requires the *Chatbot* to have a Direct Line Secret Key.
When you set up the chatbot, make sure that you add that channel to your chatbot.
Information about adding the Direct Line channel to the bot can be found in the [Azure Bot Services documentation][CONNECT_DIRECT_LINE].

Natural language processing in Apps will generally take place in the context of a `Chat` activity.
The Chat activity pattern creates a chatroom.
To add natural language processing, processing by the appropriate app component are added to the collaboration.

The `com.vantiq.nlp.InterpretCLU` app component interprets the Natural Language Interpretation of an *Utterance* from an external source using Azure Conversational Language Understanding. 
Interpret task expects to receive an event in the format produced by the Chat task's *message* event. 
Interpret produces the following outbound events:

* **processIntent** (default) - This event is generated whenever text is interpreted.
* **processCustomIntent** - This event is generated whenever text resulting in a custom intent is interpreted,.
* **processSystemIntent** - This event is generated whenever text resulting in a system intent is interpreted.

The resulting events will contain the intent and associated entities. These can be used by a procedure to act on the intent.


![Collaboration Visualization][NL_COLLAB_GRAPH]


<a name="SectionVantiqResources"></a> 
## Vantiq Resources
<a name="SectionVailProcedures"></a> 
### VAIL Services and Procedures Reference
<a name="SectionIntentSpecification"></a> 
#### IntentSpecification
The *intentSpecification* object structure is used throughout the natural language processing system to pass information about intents.
The properties of the object are as follows.

* `intent` -- the text of the intent (*e.g.* `system.list`)
* `entities` -- an array of the entities involved.  Each element contains
	* `name` -- the entity name (*e.g.* `system.typeName`)
	* `value` -- the entity value (*.e.g* "situations")
	* `rawEnt` -- the entity object returned by underlying CLU source
* `query` -- the text (prepared) for which interpretation has been attempted
* `rawIntent` -- the complete intent object returned by CLU
* `response` -- the response to be returned to the user

#### Service: NLCore

This service is in the `com.vantiq.nlp` package.
Please import the service using `import service com.vantiq.nlp.NLCore` after installing the assembly.

##### *interpretConversationalQuery()*

* Procedure Name: `NLCore.interpretConversationalQuery`
* Parameters 
	* `natLangQuery` -- the prepared text to be interpreted
	* `naturalLanguageSource` -- the name of the interpreter source
	* `cluModel` -- the name of the model trained from your language set
	* `cluDeployment` -- the name of the deployment hosting the `cluModel`.
* Returns
	* This procedure will return an `Object` that contains the following elements. 
		* `response` -- an [IntentSpecification](#SectionIntentSpecification) object
		* `errorMsg` -- any error message returned.

Generally, the caller should check `errorMsg` for content.
If content is present there, the `response` field may not be complete, may be missing, or may contain an invalid interpretation.

##### *executeSmalltalkIntent()*

* Procedure Name: `NLCore.executeSmalltalkIntent`
* Parameters
	* `intent` -- the [IntentSpecification](#SectionIntentSpecification) to be executed
* Returns
	* Text resulting from the execution of the intent.

##### *executeSystemIntent()*

* Procedure Name: `NLCore.executeSystemIntent`
* Parameters
	* `intent` -- the [IntentSpecification](#SectionIntentSpecification) to be executed
* Returns
	* Text resulting from the execution of the intent.

<a name="SectionPublishResponse"></a>
##### *publishResponse()*

* Procedure Name: `NLCore.publishResponse`
* Parameters
	* `event` -- the object representing the event via which the query arrived
	* `response` -- the String to send back to the caller
	* `chatbotName` -- String containing the name of the chatbot to use for publication
* Returns
	* (no return value)

#### Service: NLUtils

This service is in the `com.vantiq.nlp` package.
Please import the service using `import service com.vantiq.nlp.NLCore` after installing the assembly.

##### *prepareText()*

* Procedure Name: `NLUtils.prepareText`
* Parameters
	* `channel` -- String representing the channel.  The *channel* is the name of the type of channel used.  Examples include *directline*, *slack*, *msteam*, *skype*.  Please see the Vantiq CHATBOT Guide for more information.
	* `incoming` -- Boolean indicating whether this is an incoming message.
	    * `true` means incoming (toward the system)
	    * `false` means outgoing (toward the user)
	* `msg` -- String representing the message in question
* Returns
	* String representing the prepared text

<a name="SectionVantiqNLS"></a> 
### Vantiq System Natural Language Subset

The Vantiq NLS can be found [here][VANTIQ_NLS].  It contains the following json file(s), representing the language model definitions.

* `VantiqBase_CLU.json` -- Vantiq System Natural Language Subset


#### Importing the Vantiq NLS
The general instructions for importing into CLU can be found in the [CLU Documentation][CLU_Documentation].

Of specific interest here is that the CLU API does not support partial or additive imports.
That is, there is, at present, no option to import the Vantiq NLS and, subsequently, import an additional set of custom intents.
Instead, the CLU user must start with the Vantiq NLS, then add the custom intents using the [Language Studio][CLUStudio].
When that work is complete, tested, trained, *etc.*, the CLU application can be trained and deployed, and then used as an [interpreter source](#SectionInterpreterSource).

However, if the Vantiq NLS were to change (*e.g.* add new utterances, intents, entities),
it is not currently possible to import only those changes.
At present, the CLU application developer should keep the various forms of things, and manually merge any changes required.

# Trademarks

* Microsoft and Azure are trademarks of the Microsoft group of companies.
