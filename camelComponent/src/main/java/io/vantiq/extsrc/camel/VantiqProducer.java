/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
@Slf4j
public class VantiqProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(VantiqProducer.class);
    private VantiqEndpoint endpoint;
    ObjectMapper mapper = new ObjectMapper();
    
    public VantiqProducer(VantiqEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }
    
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        log.debug("VantiqProducer.process() sending message: {}", exchange.getIn().getBody());
        log.trace("Type converter count: {}", exchange.getContext().getTypeConverterRegistry().size());
        
        Object msg = exchange.getIn().getBody();
        Map<String, Object> vMsg;
        vMsg = exchange.getIn().getBody(HashMap.class);
        // Jackson converts to json nodes but not to map.  Have to hunt for that or convert json node.
        log.debug("Camel converted body is {}, msg type: {}, msg: {}", vMsg, msg.getClass().getName(), msg);
        if (vMsg == null) {
            // Then Camel couldn't convert on it's own.  Let's see if we can help things along...
            if (msg instanceof String) {
                // Then we must be fetching a JSON string.
                String strMsg = (String) msg;
                vMsg = mapper.readValue(strMsg, new TypeReference<>() {});
            } else if (msg instanceof Map) {
                vMsg = (Map<String, Object>) msg;
            } else if (msg instanceof InputStreamCache) {
                InputStreamCache isc = (InputStreamCache) msg;
                String str = new String(isc.readAllBytes());
                if (str.charAt(0) == '"') {
                    // Then strip leading & trailing quotes
                    str = str.substring(1, str.length() - 1);
                }
                log.trace("JSON String as input is: {}", str);

                vMsg = mapper.readValue(isc.readAllBytes(), new TypeReference<Map<String,Object>>(){});
            } else if (msg instanceof JsonNode) {
                vMsg = mapper.convertValue(msg, new TypeReference<>(){});
            } else {
                log.error("Unexpected type: {}.  Unable to convert to Map to send to Vantiq.",
                        msg.getClass().getName());
                throw new InvalidPayloadException(exchange, Map.class);
            }
        }
        log.trace("Sending message: {}", vMsg);
        Object ra = exchange.getProperty(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        String responseAddr = null;
        if (ra instanceof String) {
            responseAddr = (String) ra;
        }
        if (exchange.getPattern() == ExchangePattern.InOut) {
            log.debug("Sending response message: code; {}, addr: {}, msg: {}", 200, responseAddr, vMsg);
            endpoint.sendResponse(200, responseAddr, vMsg);
        } else {
            endpoint.sendMessage(vMsg);
        }
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
