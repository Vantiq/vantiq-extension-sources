# FHIR&reg; Sink Operation

## Sending and Receiving Messages

The Vantiq component, which is the entry to the FHIR&reg; Component, accepts messages with `header` and `message` 
values. These are translated into JSON for the FHIR Component.

For example, for the `SEARCH` API, using the `searchByUrl` method, 
the `header` should contain properties specifying the search criteria in the `url` parameter.  The way the FHIR 
component works, it expects the header-resident parameters to be specified as `CamelFhir.<paramName>`.  This means 
that the `url` parameter will be included in the header as `CamelFhir.url=`.  For example, to look for information 
about a Patient named _smith_, we'd use `CamelFhir.url=Patient?name=smith`. 

The FHIR Component's `search` operation also wants the resource type required to be specified as the body of the 
message, in a property named `resourceType`.
Please see the FHIR Component in the Apache Camel documentation for details.

Putting this all together, we can send messages using standard Vail code using the FHIR-sink assembly.  The 
following is a simple procedure that sends a search query to the FHIR Server.

```js
package io.test.fhir
PROCEDURE doFhirSearch(urlParam String, resourceType String): Object

var msg = { resourceType: resourceType }
var headers = {CamelFhir.url = urlParam }

publish {headers: headers, message: msg } to SERVICE EVENT  "com.vantiq.extsrc.camel.kamelets.v3_21_0.fhir_sink.fhir_sink_service/fhir_sink_serviceEvent"

return null
```

And this can be invoked supplying "Patient" for `resourceType`, and "Patient?name=smith" for `urlParam`.

However, this paradigm is not really very useful. We would like to see the result of the operation (especially for a 
search).  To do this, we'll need to interact with the source that underlies the service & associated event seen above.

To do this, we'll use something like the following Vail procedure:

```js
package io.test.fhir
PROCEDURE returnFhirSearch(urlParam String, resourceType String): Object array

var msg = { resourceType: resourceType }
var headers = {CamelFhir.url = urlParam }

var res = select * from source com.vantiq.extsrc.camel.kamelets.v3_21_0.fhir_sink.fhir_sink_source 
	with message = msg, headers = headers
return res
```

using the same parameters. Operated in this fashion, we get a result that looks something like the following (which 
has been abbreviated):

```
[
   {
      "headers": {
         "CamelFhir.url": "Patient?name=smith",
         "Content-Type": "application/fhir+json"
      },
      "message": {
         "resourceType": "Bundle",
         "meta": {
            "versionId": "c1a8968d-8be5-451b-af72-2e9e2addffa5",
            "lastUpdated": "2024-03-06T21:44:21.168+00:00"
         },
         "type": "searchset",
         "timestamp": "2024-03-06T21:44:21.168+00:00",
         "total": 100,
         "link": [
            {
               "relation": "self",
               "url": "https://server.fire.ly/Patient?name=smith&_format=json&_total=accurate&_count=10&_skip=0"
            },
            {
               "relation": "next",
               "url": "https://server.fire.ly/95e51b3d-b13e-4f5e-9f4d-a24d57290517"
            },
            {
               "relation": "last",
               "url": "https://server.fire.ly/a16cd3c9-8e75-4319-99e6-b64052a1fe89"
            }
         ],
         "entry": [
            {
               "fullUrl": "https://server.fire.ly/Patient/def57a80-2d3e-4fe5-bcfb-661d98ce4579",
               "resource": {
                  "resourceType": "Patient",
                  "id": "def57a80-2d3e-4fe5-bcfb-661d98ce4579",
                  "meta": {
                     "versionId": "e5738b3c-2e2f-451b-a096-fc796b3475fc",
                     "lastUpdated": "2024-03-04T17:10:06.121+00:00",
                     "tag": [
                        {
                           "system": "http://www.alpha.alp/use-case",
                           "code": "EX20"
                        }
                     ]
                  },
                  "extension": [
                     {
                        "url": "http://hl7.org/fhir/StructureDefinition/patient-birthPlace",
                        "valueAddress": {
                           "city": "Bath",
                           "country": "UK"
                        }
                     },
                     {
                        "url": "http://hl7.org/fhir/StructureDefinition/patient-disability",
                        "valueCodeableConcept": {
                           "coding": [
                              {
                                 "system": "http://snomed.info/sct",
                                 "code": "232424232424",
                                 "display": "Amputation of foot"
                              }
                           ]
                        }
                     }
                  ],
                  "identifier": [
                     {
                        "system": "http://www.miniaf.alp/citreg",
                        "value": "232424232424"
                     },
                     {
                        "system": "http://www.alpha-hospital.alp/patient-id",
                        "value": "232424232424"
                     }
                  ],
                  "name": [
                     {
                        "family": "Tucker-Shimanuki",
                        "given": [
                           "Alfonso"
                        ],
                        "prefix": [
                           "Mr."
                        ]
                     },
                     {
                        "use": "old",
                        "family": "Smith-Jones"
                     }
                  ],
                  "telecom": [
                     {
                        "system": "phone",
                        "value": "020 824 322",
                        "use": "home"
                     }
                  ],
                  "gender": "male",
                  "birthDate": "1970-01-01",
                  "address": [
                     {
                        "line": [
                           "Southlands, Forest View"
                        ],
                        "city": "North-East Forest",
                        "postalCode": "BA3 1AT"
                     }
                  ],
                  "communication": [
                     {
                        "language": {
                           "coding": [
                              {
                                 "system": "urn:ietf:bcp:47",
                                 "code": "en",
                                 "display": "English"
                              }
                           ]
                        }
                     },
                     {
                        "language": {
                           "coding": [
                              {
                                 "system": "urn:ietf:bcp:47",
                                 "code": "JP",
                                 "display": "Japanese"
                              }
                           ]
                        },
                        "preferred": true
                     }
                  ]
               },
               "search": {
                  "mode": "match"
               }
            },
            {
               "fullUrl": "https://server.fire.ly/Patient/7b1bee01-7c8b-44ae-b410-02b5cd7854e0",
               "resource": {
                  "resourceType": "Patient",
                  "id": "7b1bee01-7c8b-44ae-b410-02b5cd7854e0",
                  "meta": {
                     "versionId": "ce803181-15c4-4793-8887-dedbd8796185",
                     "lastUpdated": "2024-03-02T13:19:10.130+00:00"
                  },
                  "identifier": [
                     {
                        "system": "http://testpatient.id/mrn",
                        "value": "99999999"
                     }
                  ],
                  "name": [
                     {
                        "family": "Smith",
                        "given": [
                           "Alan"
                        ]
                     }
                  ],
                  "gender": "male",
                  "birthDate": "1965-05-06"
               },
               "search": {
                  "mode": "match"
               }
            },
            ...
```

(This is using sample data from one of the many FHIR test servers.)

# Legal

Apache Camel, Camel, and Apache are trademarks of The Apache Software Foundation.

FHIR&reg; is the registered trademark of HL7&reg; and is used with the permission of Health Level Seven 
International (HL7&).

Vantiq is a trademark of Vantiq, Inc.

All other trademarks mentioned are trademarks or registered trademarks of their respective owners.