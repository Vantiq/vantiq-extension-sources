/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADERS_PROPERTY;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_MESSAGE_PROPERTY;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
public class VantiqProducer extends DefaultProducer {
    private final VantiqEndpoint endpoint;
    ObjectMapper mapper;
    
    public VantiqProducer(VantiqEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        // The mapper in the java SDK is not configured to serialize dates to strings. We will configure ourselves to
        // do so and perform the conversion before sending.
        JavaTimeModule mod = new JavaTimeModule();
        mapper =
                new ObjectMapper().registerModule(mod)
                                  .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
    
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Type converter count: {}", exchange.getContext().getTypeConverterRegistry().size());
        }
        
        Map<String, Object> vMsg = exchange.getIn().getBody(HashMap.class);
    
        if (vMsg == null) {
            Object msg = exchange.getIn().getBody();
            if (log.isDebugEnabled()) {
                log.debug("Camel converted body is {}, msg type: {}, msg: {}", vMsg, msg.getClass().getName(), msg);
            }
    
            // If we have a stream, extract the bytes before attempting to convert them
            if (msg instanceof StreamCache) {
                // First, let's extract the data from the stream
                byte[] ba = null;
                if (msg instanceof InputStreamCache) {
                    // Here, we have an input stream cache to attempt to process
                    InputStreamCache isc = (InputStreamCache) msg;
                    isc.reset();
                    ba = isc.readAllBytes();
                } else if (msg instanceof ByteArrayInputStreamCache) {
                    // Here, we have an input stream cache to attempt to process
                    ByteArrayInputStreamCache basc = (ByteArrayInputStreamCache) msg;
                    basc.reset();
                    ba = basc.readAllBytes();
                } else {
                    log.error("Unexpected type: {}.  Unable to convert to Map to send to Vantiq.",
                              msg.getClass().getName());
                    throw new InvalidPayloadException(exchange, Map.class);
                }
                msg = ba;
            }
    
            // Jackson converts to json nodes but not to map.  Have to hunt for that or convert json node.
            // Then Camel couldn't convert on its own.  Let's see if we can help things along...
            if (msg instanceof String) {
                // Then we must be fetching a JSON string.
                String strMsg = (String) msg;
                vMsg = mapper.readValue(strMsg, new TypeReference<>() {});
            } else if (msg instanceof byte[]) {
                // See if we can get Jackson to do the deserialization for us.
                byte[] ba = (byte[]) msg;
    
                try {
                    msg = mapper.readTree( ba);
                    if (msg instanceof TextNode) {
                        msg = ((TextNode) msg).asText();
                    } else if (msg instanceof ContainerNode) {
                        msg = mapper.convertValue(msg, Object.class);
                        log.debug("Decoded ObjectNode into {}: {}", msg.getClass().getName(), msg);
                    }
                } catch (IOException e) {
                    // Ignore -- we'll sort it out downstream
                }
                if (msg instanceof byte[]) {
                    try {
                        msg = mapper.readValue(ba, new TypeReference<>() {});
                        log.trace("Tried readValue() -- got a {}", msg.getClass().getName());
                    } catch (JsonParseException | DatabindException dbe) {
                        // This couldn't do the conversion.
                        log.trace("Exception converting/readValue() input message:", dbe);
                        // Let's see if this is a serialization of something else. Google pubsub sometimes does this
                        try {
                            ByteArrayInputStream baStream = new ByteArrayInputStream(
                                    (byte[]) exchange.getIn().getBody());
                            ObjectInputStream objFromByte = new ObjectInputStream(baStream);
                            msg = objFromByte.readObject();
                        } catch (Exception e) {
                            // Guess not...
                            log.trace("Trapped after Java deserialization", e);
                        }
                    }
                }
                // If these both fail, msg will still be a byte array. Guess that's what there is...
                if (msg instanceof byte[]) {
                    // Then we might be fetching a JSON string.
                    if (checkUTF8(ba)) {
                        String strMsg = new String((byte[]) msg, StandardCharsets.UTF_8);
                        try {
                            vMsg = mapper.readValue(strMsg, new TypeReference<>() {
                            });
                        } catch (Exception e) {
                            String strVal = mapper.writeValueAsString(strMsg);
                            vMsg = Map.of("stringVal", strVal);
                        }
                    } else {
                        // But, maybe it's just a byte array...
                        String strVal = mapper.writeValueAsString(ba);
                        vMsg = Map.of("byteVal", strVal);
                    }
                }
            }
            // If we still haven't found anything good, try the already converted
            if (vMsg == null) {
                if (msg instanceof Map) {
                    vMsg = (Map<String, Object>) msg;
                } else if (msg instanceof JsonNode) {
                    // Let's check for more specific value nodes -- possible if someone, say, sent a String
                    vMsg = fromJsonNode((JsonNode) msg);
                } else {
                    log.error("Unexpected type: {}.  Unable to convert to Map to send to Vantiq.",
                              msg.getClass().getName());
                    throw new InvalidPayloadException(exchange, Map.class);
                }
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Sending message: {}", vMsg);
        }
        
        // The RESPONSE_ADDRESS_HEADER is sent by Vantiq when the message represents a Vantiq query.  It is used on
        // the Vantiq side to link up the query & response, so it needs to be preserved and returned to Vantiq.
        Object ra = exchange.getProperty(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        String responseAddr = null;
        if (ra instanceof String) {
            responseAddr = (String) ra;
        }
        
        if (endpoint.isStructuredMessageHeader()) {
            // Then, our caller is asking for message data formatted as header & message
            Map<String, Object> fmtMsg = new HashMap<>();
            // the headers are usually implemented as a CaseInsensitiveMap, so we'll have Java walk the list.
            // We walk the list so that we get the original case to send on...
            // Note that we can't always use the Jackson objectMapper here because headers sometimes use Java types it
            // doesn't understand (e.g., things from google come with Protobuf timestamps or soemthing like that.
            // Simple .toString() handles it, so we'll copy the map ourselves.
            Map<String, Object> hdrs = null;
            if (exchange.getMessage().hasHeaders()) {
                try {
                    hdrs = mapper.convertValue(exchange.getMessage().getHeaders(),
                                               new TypeReference<>() {
                                               });
                } catch (Exception e) {
                    Map<String, Object> h = new HashMap<>();
                    exchange.getMessage().getHeaders().forEach((k, v) -> {
                        if (v instanceof Integer || v instanceof Map) {
                            h.put(k, v);
                        } else if (v != null) {
                            h.put(k, v.toString());
                        } else {
                            // If they just pass a null value, we'll send it along and let the message consumer deal
                            // with it.  This seems like an improper thing to do, but it's really up to the message
                            // consumers here to deal with the vagaries of how various camel connectors operate.
                            h.put(k, v);
                        }
                    });
                    hdrs = h;
                }
            }
            if (hdrs != null) {
                hdrs.putAll(endpoint.duplicateHeaders(hdrs));
                fmtMsg.put(STRUCTURED_MESSAGE_HEADERS_PROPERTY, hdrs);
            }
            Map<String, Object> m = mapper.convertValue(vMsg, new TypeReference<>() {});
            fmtMsg.put(STRUCTURED_MESSAGE_MESSAGE_PROPERTY, m);
            vMsg = fmtMsg;
        } else {
            // run the map thru the converted to serialize any embedded dates.
            vMsg = mapper.convertValue(vMsg, new TypeReference<>() {});
        }
        if (exchange.getPattern() == ExchangePattern.InOut) {
            if (exchange.getException() != null) {
                Exception e = exchange.getException();
                String emsg = "Exception while processing message: {0}::{1}";
                endpoint.sendQueryError(responseAddr, "io.vantiq.extsrc.camel.appexception",
                                        emsg, new Object[] {e.getClass().getName(), e.getMessage()});
            } else {
                log.debug("Sending response message: code; {}, addr: {}, msg: {}",
                          HttpURLConnection.HTTP_OK, responseAddr, vMsg);
                endpoint.sendResponse(HttpURLConnection.HTTP_OK, responseAddr, vMsg);
            }
        } else {
            log.debug("Producing message: {}", vMsg);
            endpoint.sendMessage(vMsg);
        }
    }
    
    private Map<String, Object> fromJsonNode(JsonNode msg) {
        Map<String, Object> retVal = null;
        if (msg instanceof TextNode) {
            retVal = Map.of("stringVal", msg.asText());
        } else if (msg instanceof BinaryNode) {
            retVal = Map.of("byteVal", msg.asText());
        } else if (msg instanceof BooleanNode) {
            retVal = Map.of("booleanVal", msg.asText());
        } else if (msg instanceof ArrayNode) {
            List<Map<String, Object>> list = new ArrayList<>(msg.size());
            for (Iterator<JsonNode> it = msg.elements(); it.hasNext(); ) {
                JsonNode n = it.next();
                list.add(fromJsonNode(n));
            }
            retVal = Map.of("listVal", list);
        } else {
            try {
                retVal = mapper.convertValue(msg, new TypeReference<>() {});
            } catch (IllegalArgumentException iae) {
                // If we get this, there's not much we can do except return the text
                retVal = Map.of("unknownDatatype", msg.asText());
            }
        }
        return retVal;
    }
    
    protected boolean checkUTF8(byte[] bytes) {
        
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        try {
            decoder.decode(buf);
        } catch(CharacterCodingException e){
            return false;
        }
        
        return true;
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.startup();
    }
    
    @Override
    protected void doStop() throws Exception {
        if (null != endpoint) {
            endpoint.shutdown();
        }
        super.doStop();
    }
}
