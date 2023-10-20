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
import static org.apache.camel.ExchangePattern.InOnly;
import static org.apache.camel.ExchangePattern.InOut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

@Slf4j
public class VantiqConsumer extends DefaultConsumer {
    private final VantiqEndpoint endpoint;

    private ExecutorService executorService;
    
    ObjectMapper mapper = new ObjectMapper();
    
    Map<String, String> hdrDupMap = null;
    
    public VantiqConsumer(VantiqEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.startup();
        hdrDupMap = endpoint.getHeaderDuplicationMap();
        ExtensionWebSocketClient vantiqClient = endpoint.getVantiqClient();
        vantiqClient.setPublishHandler(publishHandler);
        vantiqClient.setQueryHandler(queryHandler);

        // start a single threaded pool to process incoming messages
        executorService = endpoint.createExecutor();
    }

    @Override
    protected void doStop() throws Exception {
        if (null != endpoint) {
            endpoint.shutdown();
        }
        super.doStop();

        // shutdown the thread pool gracefully
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
    }
    
    /**
     * A handler for dealing with publishes to Camel.
     */
    private final Handler<ExtensionServiceMessage> publishHandler = new Handler<>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            // When we get a message, process it in the background...
            executorService.submit( () -> processMessage(message, InOnly));
        }
    };
    
    /**
     * A handler for dealing with queries to Camel.
     */
    private final Handler<ExtensionServiceMessage> queryHandler = new Handler<>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            // When we get a message, process it in the background...
            executorService.submit( () -> processMessage(message, InOut));
        }
    };
    
    
    private void processMessage(Object msg, ExchangePattern pattern) {
        if (!(msg instanceof ExtensionServiceMessage)) {
            log.error("Message processor for {} ignoring an unknown message type: {}.",
                    endpoint.getEndpointName(), msg.getClass().getName());
        } else {
            ExtensionServiceMessage message = (ExtensionServiceMessage) msg;
            Object msgBody = message.getObject();
            Map<String, Object> camelHdrs = null;
            Object camelBody = null;
            if (endpoint.isStructuredMessageHeader() && msgBody instanceof Map) {
                camelHdrs = null;
                Map<?,?> msgAsMap = (Map<?,?>) msgBody;
                if (msgAsMap.get(STRUCTURED_MESSAGE_HEADERS_PROPERTY) instanceof Map) {
                    Map<?,?> hdrMap = (Map<?,?>) msgAsMap.get(VantiqEndpoint.STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                    camelHdrs = hdrMap.entrySet()
                                .stream()
                                .filter(e -> e.getKey() instanceof String)
                                .collect(Collectors.toMap(e -> (String) e.getKey(), Map.Entry::getValue));
                    // If endpoint says to copy header values, do that here now that we have the values.
                    if (camelHdrs.size() > 0) {
                        camelHdrs.putAll(endpoint.duplicateHeaders(camelHdrs));
                    }
                }
                if (msgAsMap.get(STRUCTURED_MESSAGE_MESSAGE_PROPERTY) != null) {
                    camelBody = msgAsMap.get(STRUCTURED_MESSAGE_MESSAGE_PROPERTY);
                }
                msgBody = camelBody;
                log.debug("Structured message -- hdrs: {}, message: {}", camelHdrs, camelBody);
            }
            Object output = msgBody;
            if (endpoint.isConsumerOutputJson()) {
                // Convert to JSON output
                JsonNode jnode =  mapper.convertValue(msgBody, JsonNode.class);
                output = jnode;
                if (log.isDebugEnabled()) {
                    Object resValueDbg = jnode.isTextual() ? jnode.asText() : jnode;
                    log.debug("ConsumerOutputJson: msgBody out: {}", resValueDbg);
                }
            }
    
            // Create an exchange to move our message along.  In the publish case,
            // we have no interest in the result, so we'll allow it to be released when
            // camel operations are complete.  If/when we support queries, we will care about the
            // result, so we'll deal with those separately.
            final Exchange exchange = createExchange(false);
            exchange.setPattern(pattern);
            exchange.getIn().setBody(output);
            if (camelHdrs != null) {
                exchange.getIn().setHeaders(camelHdrs);
            }
            if (pattern == InOut) {
                // In this case, we need to save the reply address in the exchange so that we can reply appropriately
                // This is the case when we are processing a Vantiq query.
                Map<String, Object> hdrs = message.getMessageHeaders();
                Object ra = hdrs.get(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER);
                if (ra instanceof String) {
                    exchange.setProperty(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, ra);
                } else {
                    // Internal error -- should have a reply address here if we've been told to be in/out
                    log.error("Expected reply address header ({}) in {} message",
                              ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, message.getOp());
                }
            }
    
            try {
                // send message to next processor in the route
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
                releaseExchange(exchange, false);
            }
        }
    }
}
