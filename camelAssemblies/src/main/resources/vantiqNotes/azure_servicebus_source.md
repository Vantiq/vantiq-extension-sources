## Message Paradigms

The Azure Service Bus operates using messages consisting of headers (standard Apache Camel operations) and String
content. This String content can take the form of a single String or a list of Strings.  In the latter case,
each String in the list is sent as a separate service bus message.

Moreover, if the service bus sees a messages that is not a String, it will try and interpret it as a list of Strings,
each of which is sent as a separate service bus message.

The Vantiq system, and specifically these assemblies, exchanges messages with the underlying source using Vail Objects.

## Receiving Messages

The combination of these two operational paradigms results in the following interpretation.

When a message is delivered to the Vantiq Camel Component, that message, in the form of a Camel Exchange, contains 
headers and the message body. The message body will contain only a String.

The Vantiq system will send a Vail Object (specifically, an object with`headers` and `message` property. The 
`headers` property will contain the headers specified by the Camel Exchange headers, and the `message` property will 
contain the message body (a String).  Since the Vail Object is a set of properties and their values, the property 
name used here will be `stringVal` (no property name is otherwise delivered), and that property value will be the 
value delivered in the Camel Exchange message body.


The result of this is that each property in the `message` is sent as a separate String, and the property name is
ignored.  For example, if a Camel Exchange containing the header `myHeader: "my header value"` and the body `I am a 
property value` is received, the resulting Vantiq message will be the following:

```js
{
    headers: {
        myHeader: "my header value"
    },
    message: {
        stringVal: "I am a property value"
    }
}
```

# Legal

Apache Camel, Camel, and Apache are trademarks of The Apache Software Foundation.

Azure Service Bus is a trademark of Microsoft Corporation.

Vantiq is a trademark of Vantiq, Inc.

All other trademarks mentioned are trademarks or registered trademarks of their respective owners.