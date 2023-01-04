/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extjsdk.FalseWebSocket;
import io.vantiq.extjsdk.Response;
import io.vantiq.extjsdk.TestListener;
import okhttp3.WebSocket;
import okio.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VantiqComponentTest extends CamelTestSupport {
    
    private static final String testSourceName = "camelSource";
    private static final String accessToken = "someAccessToken";
    private static final String TEST_MSG_PREAMBLE = "published message from FauxVantiq #";
    private static final String TEST_MSG_KEY = "someRandomKey";
    
    private final String routeStartUri = "direct:start";
    private final String routeEndUri = "mock:direct:result";
    
    private final String exceptionEndpoint = "mock:direct:error";
    private final String vantiqEndpointUri = "vantiq://doesntmatter/" +
            "?sourceName=" + testSourceName +
            "&accessToken=" + accessToken;
    
    private final String vantiqSenderUri = "vantiq://senderdoesntmatter/" +
            "?sourceName=" + testSourceName +
            "&accessToken=" + accessToken;
    
    private final String vantiqQuerySenderUri = "vantiq://querierdoesntmatter/" +
            "?sourceName=" + testSourceName +
            "&accessToken=" + accessToken;
    
    @Test
    public void testVantiqSetup() throws Exception {
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        
        FauxVantiqEndpoint epConsumer = (FauxVantiqEndpoint) context.getEndpoint(vantiqSenderUri);
        assert epConsumer.isConnected();
        assert epConsumer.getEndpointUri().contains(accessToken);
        assert epConsumer.getEndpointUri().contains(testSourceName);
        assert vantiqSenderUri.startsWith(epConsumer.getEndpointBaseUri());
        
        FauxVantiqEndpoint epProducer = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointUri);
        assert epProducer.isConnected();
        assert epProducer.getEndpointUri().contains(accessToken);
        assert epProducer.getEndpointUri().contains(testSourceName);
        assert vantiqEndpointUri.startsWith(epProducer.getEndpointBaseUri());
    }
    
    @Test
    public void testVantiqProducerJson() throws Exception {
    
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointUri);
        FalseClient fc = ep.myClient;
        
        ArrayList<String> msgs =new ArrayList<String>();
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add("{\"hi\": \"mom " + i + "\"}");
        }

        for (Object item: msgs) {
            log.info("Sending msg: " + item);
            
            assert item != null;
            sendBody(routeStartUri, item);
            Map lastMsg = fc.getLastMessageAsMap();
            assert lastMsg != null;
            assert lastMsg.containsKey("op");
            assert "notification".equals(lastMsg.get("op"));
            assert lastMsg.containsKey("sourceName");
            assert testSourceName.equals(lastMsg.get("sourceName"));
            assert lastMsg.containsKey("object");
            assert lastMsg.get("object") instanceof Map;
            Map<String, Object> msg = (Map<String, Object>) lastMsg.get("object");
            assert msg.containsKey("hi");
            assert ((String) msg.get("hi")).contains("mom");
        }
    
        // Now, try a similar test sending maps
        ArrayList<Map<String, String>> mapList = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Map<String, String> mp = Map.of("hi", "dad " + i);
            mapList.add(mp);
        }

        for (Map item: mapList) {
            sendBody(routeStartUri, item);
    
            Map lastMsg = fc.getLastMessageAsMap();
            assert lastMsg != null;
            assert lastMsg.containsKey("op");
            assert "notification".equals(lastMsg.get("op"));
            assert lastMsg.containsKey("sourceName");
            assert testSourceName.equals(lastMsg.get("sourceName"));
            assert lastMsg.containsKey("object");
            assert lastMsg.get("object") instanceof Map;
            Map<String, Object> msg = (Map<String, Object>) lastMsg.get("object");
            assert msg.containsKey("hi");
            assert ((String) msg.get("hi")).contains("dad");
        }
    
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger counter = new AtomicInteger(0);
        msgs.forEach( msg -> {
            try {
                ObjectNode node = mapper.readValue(msg, ObjectNode.class);
                node.put("hi", "aki " + counter.getAndIncrement());
                sendBody(routeStartUri, node);
    
                Map lastMsg = fc.getLastMessageAsMap();
                assert lastMsg != null;
                assert lastMsg.containsKey("op");;
                assert "notification".equals(lastMsg.get("op"));
                assert lastMsg.containsKey("sourceName");
                assert testSourceName.equals(lastMsg.get("sourceName"));
                assert lastMsg.containsKey("object");
                assert lastMsg.get("object") instanceof Map;
                Map<String, Object> testMsg = (Map<String, Object>) lastMsg.get("object");
                assert testMsg.containsKey("hi");
                assert ((String) testMsg.get("hi")).contains("aki");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        
        sendBody(routeStartUri, "{\"bye\": \"mom\"}");
    
        Map lastMsg = fc.getLastMessageAsMap();
        assert lastMsg != null;
        assert lastMsg.containsKey("op");
        assert "notification".equals(lastMsg.get("op"));
        assert lastMsg.containsKey("sourceName");
        assert testSourceName.equals(lastMsg.get("sourceName"));
        assert lastMsg.containsKey("object");
        assert lastMsg.get("object") instanceof Map;
        Map<String, Object> msg = (Map<String, Object>) lastMsg.get("object");
        assert msg.containsKey("bye");
        assert "mom".equals(msg.get("bye"));
    }
    
    @Test
    public void testVantiqProducerInvalid() throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointUri);
        FalseClient fc = ep.myClient;
        
        ArrayList<Integer> msgs = new ArrayList<>();
        
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add(i);
        }
    
        MockEndpoint mocked = getMockEndpoint(exceptionEndpoint);
        mocked.expectedMinimumMessageCount(itemCount);
        
        for (Object item: msgs) {
            log.info("Sending msg: " + item);
            
            assert item != null;
            sendBody(routeStartUri, item);
        }
    
        mocked.await(5L, TimeUnit.SECONDS);
    
        assertEquals("Mocked service expected vs. actual", itemCount, mocked.getReceivedCounter());
    }
    
    @Test
    public void testVantiqConsumer() throws Exception {
    
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(vantiqSenderUri);
    
        assert endp.myClient.getListener() instanceof TestListener;
        TestListener tl = (TestListener) endp.myClient.getListener();
    
        int expectedMsgCount = 10;
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(expectedMsgCount);
        VantiqEndpoint ve = getMandatoryEndpoint(vantiqEndpointUri, VantiqEndpoint.class);
        assert ve != null;
        ObjectMapper mapper = new ObjectMapper();
        WebSocket ws = new FalseWebSocket();
        for (int i = 0; i < expectedMsgCount; i++) {
            ExtensionServiceMessage ep = new ExtensionServiceMessage(vantiqEndpointUri);
            ep.op = ExtensionServiceMessage.OP_PUBLISH;
            ep.resourceName = "SOURCES";
            ep.resourceId = testSourceName;
            HashMap<String, Object> msg = new HashMap<>();
            msg.put(TEST_MSG_KEY, TEST_MSG_PREAMBLE + i);
            ep.object = msg;
            
            byte[] msgBytes = mapper.writeValueAsBytes(ep);
            
            // Given the message bytes, simulate delivery of our WebSocket message
            tl.onMessage(ws, new ByteString(msgBytes));
        }

        // Consumers run in BG threads, so wait a bit for those to finish.
        mocked.await(5L, TimeUnit.SECONDS);
        
        assertEquals("Mocked service expected vs. actual", expectedMsgCount, mocked.getReceivedCounter());
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<String>();
        exchanges.forEach(exchange -> {
                assert exchange.getIn().getBody() instanceof Map;
                Map msg = exchange.getIn().getBody(Map.class);
                assert msg.containsKey(TEST_MSG_KEY);
                assert msg.get(TEST_MSG_KEY) instanceof String;
                assert ((String) msg.get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
                uniqueMsgs.add(((String) msg.get(TEST_MSG_KEY)));
        });
        assert uniqueMsgs.size() == expectedMsgCount;
    }
    
    @Test
    public void testVantiqConsumerQuery() throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(vantiqQuerySenderUri);
        
        assert endp.myClient.getListener() instanceof TestListener;
        TestListener tl = (TestListener) endp.myClient.getListener();
        
        int expectedMsgCount = 10;
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(expectedMsgCount);
        VantiqEndpoint ve = getMandatoryEndpoint(vantiqEndpointUri, VantiqEndpoint.class);
        assert ve != null;
        ObjectMapper mapper = new ObjectMapper();
        WebSocket ws = new FalseWebSocket();
        Set<String> responseAddresses = new HashSet<>();
        for (int i = 0; i < expectedMsgCount; i++) {
            ExtensionServiceMessage ep = new ExtensionServiceMessage(vantiqEndpointUri);
            ep.op = ExtensionServiceMessage.OP_QUERY;
            ep.resourceName = "SOURCES";
            ep.resourceId = testSourceName;
            Map<String, Object> hdrs = new HashMap<>();
            String respAddr = UUID.randomUUID().toString();
            hdrs.put(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER, respAddr);
            responseAddresses.add(respAddr);
            ep.messageHeaders = hdrs;
            HashMap<String, Object> msg = new HashMap<>();
            msg.put(TEST_MSG_KEY, TEST_MSG_PREAMBLE + i);
            ep.object = msg;
            
            byte[] msgBytes = mapper.writeValueAsBytes(ep);
            
            // Given the message bytes, simulate delivery of our WebSocket message
            tl.onMessage(ws, new ByteString(msgBytes));
        }
        
        // Consumers run in BG threads, so wait a bit for those to finish.
        mocked.await(5L, TimeUnit.SECONDS);
        
        assertEquals("Mocked service expected vs. actual", expectedMsgCount, mocked.getReceivedCounter());
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<String>();
        exchanges.forEach(exchange -> {
            assert exchange.getIn().getBody() instanceof Map;
            Map msg = exchange.getIn().getBody(Map.class);
            assert msg.containsKey(TEST_MSG_KEY);
            assert msg.get(TEST_MSG_KEY) instanceof String;
            assert ((String) msg.get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
            uniqueMsgs.add(((String) msg.get(TEST_MSG_KEY)));
            
            Map<String, Object> props = exchange.getProperties();
            assert props.containsKey(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        });
        assert uniqueMsgs.size() == expectedMsgCount;
      
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqQuerySenderUri);
        FalseClient fc = ep.myClient;
        Response rsp = fc.getLastMessageAsResponse();
        assert rsp != null;
        assert rsp.getBody() != null;
        assert rsp.getStatus() == 200;
        Object o = rsp.getBody();
        assert o instanceof Map;
        Map<String, Object> response = (Map) o;
        log.debug("Found final response message: {}", response);
        assert response.containsKey("Response");
        String ra = rsp.getHeader(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        assert responseAddresses.contains(ra);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
    
                
                // Override the component type to be used...
                context.addComponent("vantiq", new FauxVantiqComponent());
                for (String name: context.getComponentNames()) {
                    log.info("Component name: {}", name);
                }
//                // interceptors
//                interceptSendToEndpoint("vantiq:*")
//                        .marshal()
//                        .json();
//
//                interceptFrom("vantiq:*")
//                        .unmarshal()
//                        .json();
    
                onException(InvalidPayloadException.class)
                        .log(LoggingLevel.ERROR, "Got InvalidPayloadException")
                                .to(exceptionEndpoint);
                
                from(routeStartUri)
                  .to(vantiqEndpointUri);
                
                from(vantiqSenderUri)
                        .to(routeEndUri);
    
                from(vantiqQuerySenderUri)
                        .setExchangePattern(ExchangePattern.InOut)
                        .to(routeEndUri)
                            .setBody(constant("{ \"Response\": \"Message\"}"))
                        .to(vantiqQuerySenderUri);
            }
        };
    }
}
