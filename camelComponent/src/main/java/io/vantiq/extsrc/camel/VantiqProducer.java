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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
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
                                  .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);;
    }
    
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Type converter count: {}", exchange.getContext().getTypeConverterRegistry().size());
        }
        
        Object msg = exchange.getIn().getBody();
        Map<String, Object> vMsg;
        if (!(msg instanceof StreamCache)) {
            vMsg = exchange.getIn().getBody(HashMap.class);
            // Jackson converts to json nodes but not to map.  Have to hunt for that or convert json node.
            if (log.isDebugEnabled()) {
                log.debug("Camel converted body is {}, msg type: {}, msg: {}", vMsg, msg.getClass().getName(), msg);
            }
            if (vMsg == null) {
                // Then Camel couldn't convert on its own.  Let's see if we can help things along...
                if (msg instanceof String) {
                    // Then we must be fetching a JSON string.
                    String strMsg = (String) msg;
                    vMsg = mapper.readValue(strMsg, new TypeReference<>() {});
                } else  if (msg instanceof byte[]) {
                    byte[] ba = (byte[]) msg;
                    // Then we must be fetching a JSON string.
                    if (checkUTF8(ba)) {
                        String strMsg = (String) new String((byte[]) msg, StandardCharsets.UTF_8);
                        try {
                            vMsg = mapper.readValue(strMsg, new TypeReference<>() {});
                        } catch (Exception e) {
                            String strVal = mapper.writeValueAsString(strMsg);
                            vMsg = Map.of("stringVal", strVal);
                        }
                    } else {
                        String strVal = mapper.writeValueAsString(ba);
                        vMsg = Map.of("byteVal", strVal);
                    }
                } else if (msg instanceof Map) {
                    vMsg = (Map<String, Object>) msg;
                } else if (msg instanceof JsonNode) {
                    vMsg = mapper.convertValue(msg, new TypeReference<>() {});
                } else {
                    log.error("Unexpected type: {}.  Unable to convert to Map to send to Vantiq.",
                              msg.getClass().getName());
                    throw new InvalidPayloadException(exchange, Map.class);
                }
            }
        } else {
            if (msg instanceof InputStreamCache) {
                // Here, we have an input stream cache to attempt to process
                InputStreamCache isc = (InputStreamCache) msg;
                isc.reset();
                String str = new String(isc.readAllBytes());
                if (str.charAt(0) == '"') {
                    // Then strip leading & trailing quotes
                    str = str.substring(1, str.length() - 1);
                }
                if (log.isTraceEnabled()) {
                    log.trace("JSON String as input is: {}", str);
                }
        
                vMsg = mapper.readValue(isc.readAllBytes(), new TypeReference<>() {});
            } else {
                log.error("Unexpected type: {}.  Unable to convert to Map to send to Vantiq.",
                          msg.getClass().getName());
                throw new InvalidPayloadException(exchange, Map.class);
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
            // Note that we cannot use the Jackson objectMapper here because headers sometimes use Java types it
            // doesn't understand (e.g., things from google come with Protobuf timestamps or soemthing like that.
            // Simple .toString() handles it, so we'll copy the map ourselves.
            Map<String, Object> hdrs = new HashMap<>();
            exchange.getMessage().getHeaders().forEach( (k, v) -> {
                hdrs.put(k, v.toString());
            });
            fmtMsg.put(STRUCTURED_MESSAGE_HEADERS_PROPERTY, hdrs);
            Map<String, Object> m = mapper.convertValue(vMsg, new TypeReference<>() {});
            fmtMsg.put(STRUCTURED_MESSAGE_MESSAGE_PROPERTY, m);
            vMsg = fmtMsg;
        } else {
            // run the map thru the converted to serialize any embedded dates.
            Map<String, Object> m = mapper.convertValue(vMsg, new TypeReference<>() {});
            vMsg = m;
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
            endpoint.sendMessage(vMsg);
        }
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
