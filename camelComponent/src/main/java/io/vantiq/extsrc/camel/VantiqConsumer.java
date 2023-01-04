/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import static org.apache.camel.ExchangePattern.InOnly;
import static org.apache.camel.ExchangePattern.InOut;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

@Slf4j
public class VantiqConsumer extends DefaultConsumer {
    private final VantiqEndpoint endpoint;
    private ExtensionWebSocketClient vantiqClient;

    private ExecutorService executorService;
    
    public VantiqConsumer(VantiqEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.startup();
        vantiqClient = endpoint.getVantiqClient();
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
    private final Handler<ExtensionServiceMessage> publishHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            // When we get a message, process it in the background...
            executorService.submit( () -> {
                processMessage(message, InOnly);
            });
        }
    };
    
    /**
     * A handler for dealing with queries to Camel.
     */
    private final Handler<ExtensionServiceMessage> queryHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            // When we get a message, process it in the background...
            executorService.submit( () -> {
                processMessage(message, InOut);
            });
        }
    };
    
    
    private void processMessage(Object msg, ExchangePattern pattern) {
        if (!(msg instanceof ExtensionServiceMessage)) {
            log.error("Message processor for {} ignoring an unknown message type: {}.",
                    endpoint.getEndpointName(), msg.getClass().getName());
        } else {
            ExtensionServiceMessage message = (ExtensionServiceMessage) msg;
            Object msgBody = message.getObject();
    
            // Create an exchange to move our message along.  In the publish case,
            // we have no interest in the result, so we'll allow it to be released when
            // camel operations are complete.  If/when we support queries, we will care about the
            // result so we'll deal with those separately.
            final Exchange exchange = createExchange(false);
            exchange.setPattern(pattern);
            exchange.getIn().setBody(msgBody);
            if (pattern == InOut) {
                // In this case, we need to save the reply address in the exchange so that we can reply appropriately
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
