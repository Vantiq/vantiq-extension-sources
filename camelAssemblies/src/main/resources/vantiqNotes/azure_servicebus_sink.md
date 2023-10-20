## Message Paradigms

The Azure Service Bus operates using messages consisting of headers (standard Apache Camel operations) and String
content. This String content can take the form of a single String or a list of Strings.  In the latter case,
each String in the list is sent as a separate Service Bus message.

Moreover, of the Service Bus sees a messages that is not a String, it will try and interpret it as a list of Strings,
each of which is sent as a separate Service Bus message.

The Vantiq system, and specifically these assemblies, exchanges messages with the underlying source using Vail Objects.

## Sending Messages

The combination of these two operational paradigms results in the following interpretation.

The Vantiq system will send a Vail Object (specifically, an object with`headers` and `message` property). The 
`headers` property is placed into the Camel Exchange headers, and the `message` property is placed into the Camel 
Exchange body.

Once the Azure Service Bus receives the message, it will look at the message body.  Generally, it will see a Vail 
Object (which appears as a JSON Object), and, since that's not a String, it will interpret it as a list of Strings.

The result of this is that each property in the `message` is sent as a separate String, and the property name is
ignored.  For example, if you send a message such as the following:

```js
{
    headers: {
        myHeader: "my header value"
    },
    message: {
        someProp: "I am a property value"
    }
}
```

the message send across the Azure Service Bus will contain the `headers` as specified, but the message body will be 
the string _I am a property value_. The property name `someProp` will not be present.

If you were to send the same message, but it had two properties:

```js
{
    headers: {
        myHeader: "my header value"
    },
    message: {
        someProp: "I am a property value",
        someOtherProp: "A different property value"
    }
}
```

two (2) messages would be placed sent across the Azure Service Bus:  both would have the `headers` as specified, one 
with the message value _I am a property value_, and the second with the message value _A different property value_.

# Legal

Apache Camel, Camel, and Apache are trademarks of The Apache Software Foundation.

Azure Service Bus is a trademark of Microsoft Corporation.

Vantiq is a trademark of Vantiq, Inc.

All other trademarks mentioned are trademarks or registered trademarks of their respective owners.