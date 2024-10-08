/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import static io.vantiq.extsrc.camel.VantiqEndpoint.ACCESS_TOKEN_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.CONSUMER_OUTPUT_JSON_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.CONSUMER_OUTPUT_JSON_STREAM_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.HEADER_DUPLICATION_BEAN_NAME;
import static io.vantiq.extsrc.camel.VantiqEndpoint.SOURCE_NAME_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADERS_PROPERTY;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADER_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_MESSAGE_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extjsdk.FalseWebSocket;
import io.vantiq.extjsdk.Response;
import io.vantiq.extjsdk.TestListener;
import lombok.extern.slf4j.Slf4j;
import okhttp3.WebSocket;
import okio.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class VantiqComponentTest extends CamelTestSupport {
    
    private static final String testSourceName = "camelSource";
    private static final String accessToken = "someAccessToken";
    private static final String TEST_MSG_PREAMBLE = "published message from FauxVantiq #";
    private static final String TEST_MSG_KEY = "someRandomKey";
    private static final String TEST_HEADER_BEAN_NAME = "TestHEBean";
    
    private final String routeStartUri = "direct:start";
    
    private final String routeStartStructuredUri = "direct:structuredstart";
    
    private final String routeStartStructuredHeaderMapUri = "direct:structuredMappedstart";
    
    private final String routeEndUri = "mock:direct:result";
    
    private final String exceptionEndpoint = "mock:direct:error";
    private final String vantiqEndpointUri = "vantiq://doesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken;
    
    private final String vantiqEndpointStructuredUri = "vantiq://doesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true";
    
    private final String vantiqEndpointStructuredHeaderMapUri = "vantiq://doesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true" +
            "&" + HEADER_DUPLICATION_BEAN_NAME + "=" + TEST_HEADER_BEAN_NAME;
    
    private final String vantiqSenderUri = "vantiq://senderdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken;
    
    private final String vantiqQuerySenderUri = "vantiq://querierdoesntmatter/" +
            "?sourceName=" + testSourceName +
            "&accessToken=" + accessToken;
    
    private final String vantiqJsonSenderUri = "vantiq://jsonsenderdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_PARAM + "=true";
    
    private final String vantiqJsonSenderStreamUri = "vantiq://jsonsenderstreamdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_STREAM_PARAM + "=true";
    
    public final String vantiqStructuredJsonSenderUri = "vantiq://structuredsenderdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_PARAM + "=true" +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true";
    
    public final String vantiqStructuredJsonSenderStreamUri = "vantiq://structuredsenderstreamdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_STREAM_PARAM + "=true" +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true";
    
    public final String vantiqStructuredJsonSenderMappedUri = "vantiq://structuredsenderdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_PARAM + "=true" +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true" +
            "&" + HEADER_DUPLICATION_BEAN_NAME + "=" + TEST_HEADER_BEAN_NAME;
    
    public final String vantiqStructuredJsonSenderMappedStreamUri = "vantiq://structuredsenderstreamdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_STREAM_PARAM + "=true" +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true" +
            "&" + HEADER_DUPLICATION_BEAN_NAME + "=" + TEST_HEADER_BEAN_NAME;
    
    private final String vantiqJsonQuerierUri = "vantiq://jsonquerierdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_PARAM + "=true";
    
    private final String vantiqJsonQuerierStreamUri = "vantiq://jsonquerierstreamdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_STREAM_PARAM + "=true";
    
    @Test
    public void testVantiqSetup() {
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
    public void testVantiqProducerJson() {
    
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointUri);
        FalseClient fc = ep.myClient;
        
        ArrayList<String> msgs =new ArrayList<>();
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add("{\"hi\": \"mom " + i + "\"}");
        }

        for (Object item: msgs) {
            log.info("Sending msg: " + item);
            
            assert item != null;
            sendBody(routeStartUri, item);
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, false, null, new String[] {"hi"}, "mom");
        }
    
        // Now, try a similar test sending maps
        ArrayList<Map<String, String>> mapList = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Map<String, String> mp = Map.of("hi", "dad " + i);
            mapList.add(mp);
        }

        //noinspection rawtypes
        for (Map item: mapList) {
            sendBody(routeStartUri, item);
    
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, false, null, new String[] {"hi"}, "dad");
        }
    
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger counter = new AtomicInteger(0);
        msgs.forEach( msg -> {
            try {
                ObjectNode node = mapper.readValue(msg, ObjectNode.class);
                node.put("hi", "aki " + counter.getAndIncrement());
                sendBody(routeStartUri, node);
    
                //noinspection rawtypes
                Map lastMsg = fc.getLastMessageAsMap();
                validateExtensionMsg(lastMsg, false, null, new String[] {"hi"}, "aki");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        
        sendBody(routeStartUri, "{\"bye\": \"mom\"}");
        //noinspection rawtypes
        Map lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, false, null, new String[] {"bye"}, "mom");
        
        String testMsg = "I am a test message";
        byte[] testBytes = testMsg.getBytes(StandardCharsets.UTF_8);
        sendBody(routeStartUri, testBytes);
        lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, false, null, new String[] { "stringVal"}, testMsg);
    
        Instant rightNow = Instant.now();
        //noinspection rawtypes
        Map timeMsg = Map.of("time", rightNow);
        sendBody(routeStartUri, timeMsg);
    
        lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, false, null, new String[] {"time"}, rightNow.toString());
    
        testBytes = new byte[] { (byte) 0xf8, (byte) 0xfa, (byte) 0xfb, (byte) 0xff };
        sendBody(routeStartUri, testBytes);
        lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, false, null, new String[] { "byteVal"}, testBytes);
    }
    
    @Test
    public void testVantiqProducerFromString() {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointUri);
        FalseClient fc = ep.myClient;
        
        ArrayList<String> msgs =new ArrayList<>();
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add("hi mom " + i);
        }
        
        // Verify that when then producer gets a pure string message, that it turns it a Vail object (aka Map) with
        // a single key "stringVal" and the test string as the value.
        for (Object item: msgs) {
            log.info("Sending msg: " + item);
            
            assert item != null;
            sendBody(routeStartUri, item);
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, false, null, new String[] {"stringVal"}, "hi mom");
        }
    }
    
    @Test
    public void testVantiqProducerStructuredJson() {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointStructuredUri);
        FalseClient fc = ep.myClient;
        
        ArrayList<String> msgs =new ArrayList<>();
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add("{\"hi\": \"mom " + i + "\"}");
        }
        
        for (Object item: msgs) {
            log.info("Sending msg: " + item);
            
            assert item != null;
            sendBody(routeStartStructuredUri, item);
            
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, true, null, new String[] {"hi"}, "mom");
        }
        
        // Now, try a similar test sending maps
        ArrayList<Map<String, String>> mapList = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Map<String, String> mp = Map.of("hi", "dad " + i);
            mapList.add(mp);
        }
    
        AtomicInteger hdrCounter = new AtomicInteger(0);
        
        mapList.forEach( msg -> {
            Map<String, Object> hdrs = new HashMap<>();
            hdrs.put("theMessage", msg);
            hdrs.put("counter", hdrCounter.getAndIncrement());
            hdrs.put("shouldBeNull", null);
            sendBody(routeStartStructuredUri, msg, hdrs);
    
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, true, hdrs, new String[] {"hi"}, "dad");
        });
        
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger counter = new AtomicInteger(0);
        msgs.forEach( msg -> {
            try {
                ObjectNode node = mapper.readValue(msg, ObjectNode.class);
                node.put("hi", "aki " + counter.getAndIncrement());
                Map<String, Object> hdrs = new HashMap<>();
                hdrs.put("theMessage", msg);
                hdrs.put("counter", hdrCounter.getAndIncrement());
    
                sendBody(routeStartStructuredUri, node, hdrs);
    
                //noinspection rawtypes
                Map lastMsg = fc.getLastMessageAsMap();
                validateExtensionMsg(lastMsg, true, hdrs, new String[] {"hi"}, "aki");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        
        sendBody(routeStartStructuredUri, "{\"bye\": \"mom\"}");
        
        //noinspection rawtypes
        Map lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, true, null, new String[] {"bye"}, "mom");
    }
    
    @Test
    public void testVantiqProducerHeaderMapping() {
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqEndpointStructuredHeaderMapUri);
        FalseClient fc = ep.myClient;
        
        ArrayList<String> msgs =new ArrayList<>();
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            msgs.add("{\"hi\": \"mom " + i + "\"}");
        }
        
        for (Object item: msgs) {
            log.info("Sending msg: " + item);
            
            assert item != null;
            sendBody(routeStartStructuredHeaderMapUri, item);
            
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, true, null, new String[] {"hi"}, "mom");
        }
        
        // Now, try a similar test sending maps
        ArrayList<Map<String, String>> mapList = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Map<String, String> mp = Map.of("hi", "dad " + i);
            mapList.add(mp);
        }
        
        AtomicInteger hdrCounter = new AtomicInteger(0);
        
        mapList.forEach( msg -> {
            Map<String, Object> hdrs = new HashMap<>();
            hdrs.put("theMessage", msg);
            hdrs.put("counter", hdrCounter.getAndIncrement());
            hdrs.put("shouldBeNull", null);
            hdrs.put("header1", "value1");
            hdrs.put("header2", "value2");
    
            Map<String, Object> expHdrs = new HashMap<>(hdrs);
            expHdrs.put("dupHeader1", hdrs.get("header1"));
            expHdrs.put("dupHeader2", hdrs.get("header2"));
            sendBody(routeStartStructuredHeaderMapUri, msg, hdrs);
            
            //noinspection rawtypes
            Map lastMsg = fc.getLastMessageAsMap();
            validateExtensionMsg(lastMsg, true, expHdrs, new String[] {"hi"}, "dad");
        });
        
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger counter = new AtomicInteger(0);
        msgs.forEach( msg -> {
            try {
                ObjectNode node = mapper.readValue(msg, ObjectNode.class);
                node.put("hi", "aki " + counter.getAndIncrement());
                Map<String, Object> hdrs = new HashMap<>();
                hdrs.put("theMessage", msg);
                hdrs.put("counter", hdrCounter.getAndIncrement());
                
                sendBody(routeStartStructuredHeaderMapUri, node, hdrs);
                
                //noinspection rawtypes
                Map lastMsg = fc.getLastMessageAsMap();
                validateExtensionMsg(lastMsg, true, hdrs, new String[] {"hi"}, "aki");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        
        sendBody(routeStartStructuredHeaderMapUri, "{\"bye\": \"mom\"}");
        
        //noinspection rawtypes
        Map lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, true, null, new String[] {"bye"}, "mom");
    }
    
    
    void validateExtensionMsg(Map<?,?> lastMsg, Boolean isStructured, Map<String, Object> expHdrs,
                              String[] msgKeys, Object msgPreamble) {
        assert lastMsg.containsKey("op");
        assert "notification".equals(lastMsg.get("op"));
        assert lastMsg.containsKey("sourceName");
        assert testSourceName.equals(lastMsg.get("sourceName"));
        assert lastMsg.containsKey("object");
        assert lastMsg.get("object") instanceof Map;
        //noinspection unchecked
        Map<String, Object> msg = (Map<String, Object>) lastMsg.get("object");
        if (isStructured) {
            if (expHdrs != null) {
                assert msg.containsKey(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                //noinspection unchecked
                Map<String, Object> hdrs =
                        (Map<String, Object>) msg.get(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                assert hdrs != null;
                expHdrs.forEach( (key, value) -> {
                    assertTrue(hdrs.containsKey(key), "Missing header " + key);
                    log.debug("For key {}, comparing value {} ({}) with expected {} ({}).",
                              key, hdrs.get(key),
                              hdrs.get(key) != null ? hdrs.get(key).getClass().getName() : hdrs.get(key),
                              expHdrs.get(key),
                              expHdrs.get(key) != null ? expHdrs.get(key).getClass().getName() : expHdrs.get(key));
                    if (hdrs.get(key) != null) {
                        assert hdrs.get(key).equals(expHdrs.get(key));
                    } else {
                        assert expHdrs.get(key) == null;
                        // Also, verify that the header is present -- get(key) will return null if key isn't present.
                        assert hdrs.containsKey(key);
                    }
                });
            }
            assert msg.containsKey(STRUCTURED_MESSAGE_MESSAGE_PROPERTY);
            //noinspection unchecked
            msg = (Map<String, Object>) msg.get(STRUCTURED_MESSAGE_MESSAGE_PROPERTY);
        }
        for (String key: msgKeys) {
            assert msg.containsKey(key);
            if (msgPreamble != null) {
                if (msgPreamble instanceof String) {
                    assert ((String) msg.get(key)).contains((String) msgPreamble);
                } else if (msgPreamble instanceof byte[]) {
                    Object o = msg.get(key);
                    assert o != null;
                    log.debug("{} value is a {}", key, o.getClass().getName());
                    // Message goes as JSON, so any byte array is Base64 encoded.  Also, it may be quoted (as per
                    // JSON), so we may have to deal with that.
                    if (o instanceof String) {
                        log.debug("{} value is {}", key, o);
                        String s = (String) o;
                        if (((String) o).startsWith("\"")) {
                            s = ((String) o).substring(1, ((String) o).length() - 1);
                        }
                        o = Base64.getDecoder().decode(s);
                        log.debug("{} decoded value is {}", key, s);
                    }
                    assert o instanceof byte[];
                    assert Arrays.equals((byte[]) o, (byte[]) msgPreamble);
                } else {
                    fail("msgPreamble was of unexpected type: " + msgPreamble.getClass().getName());
                }
            }
        }
    }
    
    @Test
    public void testVantiqProducerInvalid() throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        
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
    
        assertEquals(itemCount, mocked.getReceivedCounter(), "Mocked service expected vs. actual");
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
        
        assertEquals(expectedMsgCount, mocked.getReceivedCounter(), "Mocked service expected vs. actual");
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        exchanges.forEach(exchange -> {
                assert exchange.getIn().getBody() instanceof Map;
                //noinspection rawtypes
                Map msg = exchange.getIn().getBody(Map.class);
                assert msg.containsKey(TEST_MSG_KEY);
                assert msg.get(TEST_MSG_KEY) instanceof String;
                assert ((String) msg.get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
                uniqueMsgs.add(((String) msg.get(TEST_MSG_KEY)));
        });
        assert uniqueMsgs.size() == expectedMsgCount;
    }
    
    @Test
    public void testVantiqConsumerOutputJson() throws Exception {
        performConsumerOutputJsonTest(vantiqJsonSenderUri,false);
    }
    
    @Test
    public void testVantiqConsumerOutputJsonStreamed() throws Exception {
        performConsumerOutputJsonTest(vantiqJsonSenderStreamUri,true);
    }
    
    void performConsumerOutputJsonTest(String consumerEndpoint, boolean streamRequired) throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(consumerEndpoint);
        
        assert endp.myClient.getListener() instanceof TestListener;
        TestListener tl = (TestListener) endp.myClient.getListener();
        
        int mapMsgCount = 10;
        List<Object> extraTestMsgs = List.of("I am a test string", List.of(Map.of("test", "message", "another", "test" +
                " message")));
    
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(mapMsgCount + extraTestMsgs.size());
        VantiqEndpoint ve = getMandatoryEndpoint(vantiqEndpointUri, VantiqEndpoint.class);
        assert ve != null;
        ObjectMapper mapper = new ObjectMapper();
        WebSocket ws = new FalseWebSocket();
        for (int i = 0; i < mapMsgCount + extraTestMsgs.size(); i++) {
            ExtensionServiceMessage ep = new ExtensionServiceMessage(vantiqEndpointUri);
            ep.op = ExtensionServiceMessage.OP_PUBLISH;
            ep.resourceName = "SOURCES";
            ep.resourceId = testSourceName;
            if (i < mapMsgCount) {
                HashMap<String, Object> msg = new HashMap<>();
                msg.put(TEST_MSG_KEY, TEST_MSG_PREAMBLE + i);
                ep.object = msg;
            } else {
                ep.object = extraTestMsgs.get(i - mapMsgCount);
            }
            
            byte[] msgBytes = mapper.writeValueAsBytes(ep);
            
            // Given the message bytes, simulate delivery of our WebSocket message
            tl.onMessage(ws, new ByteString(msgBytes));
        }
        
        // Consumers run in BG threads, so wait a bit for those to finish.
        mocked.await(5L, TimeUnit.SECONDS);
        
        assertEquals(mapMsgCount + extraTestMsgs.size(), mocked.getReceivedCounter(),
                     "Mocked service expected vs. actual");
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        AtomicInteger msgsReceived = new AtomicInteger();
        exchanges.forEach(exchange -> {
            Object exchangeBody = exchange.getIn().getBody();
            Object msg = deserializeJsonBody(exchangeBody, mapper, streamRequired);

            msgsReceived.addAndGet(1);
             assertNotNull(msg, "Deserialized msg is null");
            if (msg instanceof Map) {
                assert ((Map<?,?>) msg).containsKey(TEST_MSG_KEY);
                assert ((Map<?,?>) msg).get(TEST_MSG_KEY) instanceof String;
                assert ((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
                uniqueMsgs.add(((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)));
            } else if (msg instanceof String) {
                assertEquals(extraTestMsgs.get(0), msg);
            } else {
                assertEquals(extraTestMsgs.get(1), msg);
            }
        });
        assert uniqueMsgs.size() == mapMsgCount;
        assert msgsReceived.get() == mapMsgCount + extraTestMsgs.size();
    }
    
    @Test
    public void testVantiqConsumerOutputJsonStructured() throws Exception {
        performConsumerOutputJsonStructuredTest(vantiqStructuredJsonSenderUri, false);
    }
    
    @Test
    public void testVantiqConsumerOutputJsonStructuredStreamed() throws Exception {
        performConsumerOutputJsonStructuredTest(vantiqStructuredJsonSenderStreamUri, true);
    }
    
    void performConsumerOutputJsonStructuredTest(String consumerEndpoint, boolean streamRequired) throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(consumerEndpoint);
        
        assert endp.myClient.getListener() instanceof TestListener;
        TestListener tl = (TestListener) endp.myClient.getListener();
        
        int mapMsgCount = 10;
        List<Object> extraTestMsgs = List.of("I am a test string", List.of(Map.of("test", "message", "another", "test" +
                " message")));
        
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(mapMsgCount + extraTestMsgs.size());
        VantiqEndpoint ve = getMandatoryEndpoint(vantiqEndpointUri, VantiqEndpoint.class);
        assert ve != null;
        ObjectMapper mapper = new ObjectMapper();
        WebSocket ws = new FalseWebSocket();
        for (int i = 0; i < mapMsgCount + extraTestMsgs.size(); i++) {
            ExtensionServiceMessage ep = new ExtensionServiceMessage(vantiqEndpointUri);
            ep.op = ExtensionServiceMessage.OP_PUBLISH;
            ep.resourceName = "SOURCES";
            ep.resourceId = testSourceName;
            if (i < mapMsgCount) {
                HashMap<String, Object> msg = new HashMap<>();
                msg.put(TEST_MSG_KEY, TEST_MSG_PREAMBLE + i);
                HashMap<String, Object> smsg = new HashMap<>();
                smsg.put(STRUCTURED_MESSAGE_MESSAGE_PROPERTY, msg);
                HashMap<String, Object> hdrs = new HashMap<>();
                hdrs.put("theMessage", msg);
                hdrs.put("counter", i);
                smsg.put(STRUCTURED_MESSAGE_HEADERS_PROPERTY, hdrs);
                ep.object = smsg;
            } else {
                HashMap<String, Object> smsg = new HashMap<>();
                HashMap<String, Object> hdrs = new HashMap<>();
                hdrs.put("theMessage", extraTestMsgs.get(i - mapMsgCount));
                hdrs.put("counter", i);
                hdrs.put("isExtra", true);
                smsg.put(STRUCTURED_MESSAGE_HEADERS_PROPERTY, hdrs);
                smsg.put(STRUCTURED_MESSAGE_MESSAGE_PROPERTY, extraTestMsgs.get(i - mapMsgCount));
                ep.object = smsg;
            }
            
            byte[] msgBytes = mapper.writeValueAsBytes(ep);
            
            // Given the message bytes, simulate delivery of our WebSocket message
            tl.onMessage(ws, new ByteString(msgBytes));
        }
        
        // Consumers run in BG threads, so wait a bit for those to finish.
        mocked.await(5L, TimeUnit.SECONDS);
        
        assertEquals(mapMsgCount + extraTestMsgs.size(), mocked.getReceivedCounter(),
                     "Mocked service expected vs. actual");
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        AtomicInteger msgsReceived = new AtomicInteger();
        exchanges.forEach(exchange -> {
            Object exchangeBody = exchange.getIn().getBody();
            Map<String, Object> exchangeHdrs = exchange.getIn().getHeaders();
            Object msg = deserializeJsonBody(exchangeBody, mapper, streamRequired);
            msgsReceived.addAndGet(1);
            
            if (msg instanceof Map) {
                assert ((Map<?,?>) msg).containsKey(TEST_MSG_KEY);
                assert ((Map<?,?>) msg).get(TEST_MSG_KEY) instanceof String;
                assert ((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
                assert exchangeHdrs.get("theMessage") instanceof Map;
                assert (((Map<?,?>) msg).get(TEST_MSG_KEY)).equals(
                        ((Map<?,?>) exchangeHdrs.get("theMessage")).get(TEST_MSG_KEY));
                assert exchangeHdrs.get("counter") instanceof Number;
                uniqueMsgs.add(((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)));
            } else if (msg instanceof String) {
                assertEquals(msg, extraTestMsgs.get(0));
                assert exchangeHdrs.get("theMessage") instanceof String;
                assert msg.equals(exchangeHdrs.get("theMessage"));
                assert exchangeHdrs.get("counter") instanceof Number;
                assert exchangeHdrs.get("isExtra") instanceof Boolean;
                assert (Boolean) exchangeHdrs.get("isExtra");
            } else {
                assert msg.equals(exchangeHdrs.get("theMessage"));
                assert exchangeHdrs.get("theMessage").getClass().equals(msg.getClass());
                assert exchangeHdrs.get("counter") instanceof Number;
                assert exchangeHdrs.get("isExtra") instanceof Boolean;
                assert (Boolean) exchangeHdrs.get("isExtra");
            }
        });
        assert uniqueMsgs.size() == mapMsgCount;
        assert msgsReceived.get() == mapMsgCount + extraTestMsgs.size();
    }
    
    @Test
    public void testVantiqConsumerHeaderMapping() throws Exception {
        performConsumerOutputJsonStructuredHeaderTest(vantiqStructuredJsonSenderMappedUri,false);
    }
    
    @Test
    public void testVantiqConsumerHeaderMappingStreamed() throws Exception {
        performConsumerOutputJsonStructuredHeaderTest(vantiqStructuredJsonSenderMappedStreamUri,true);
    }
    void performConsumerOutputJsonStructuredHeaderTest(String consumerEndpoint, boolean streamRequired) throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(consumerEndpoint);
        
        assert endp.myClient.getListener() instanceof TestListener;
        TestListener tl = (TestListener) endp.myClient.getListener();
        
        int mapMsgCount = 10;
        List<Object> extraTestMsgs = List.of("I am a test string", List.of(Map.of("test", "message", "another", "test" +
                " message")));
        
        MockEndpoint mocked = getMockEndpoint(routeEndUri);
        mocked.expectedMinimumMessageCount(mapMsgCount + extraTestMsgs.size());
        VantiqEndpoint ve = getMandatoryEndpoint(vantiqEndpointUri, VantiqEndpoint.class);
        assert ve != null;
        ObjectMapper mapper = new ObjectMapper();
        WebSocket ws = new FalseWebSocket();
        for (int i = 0; i < mapMsgCount + extraTestMsgs.size(); i++) {
            ExtensionServiceMessage ep = new ExtensionServiceMessage(vantiqEndpointUri);
            ep.op = ExtensionServiceMessage.OP_PUBLISH;
            ep.resourceName = "SOURCES";
            ep.resourceId = testSourceName;
            if (i < mapMsgCount) {
                HashMap<String, Object> msg = new HashMap<>();
                msg.put(TEST_MSG_KEY, TEST_MSG_PREAMBLE + i);
                HashMap<String, Object> smsg = new HashMap<>();
                smsg.put(STRUCTURED_MESSAGE_MESSAGE_PROPERTY, msg);
                HashMap<String, Object> hdrs = new HashMap<>();
                hdrs.put("theMessage", msg);
                hdrs.put("counter", i);
                smsg.put(STRUCTURED_MESSAGE_HEADERS_PROPERTY, hdrs);
                ep.object = smsg;
            } else {
                HashMap<String, Object> smsg = new HashMap<>();
                HashMap<String, Object> hdrs = new HashMap<>();
                hdrs.put("theMessage", extraTestMsgs.get(i - mapMsgCount));
                hdrs.put("counter", i);
                hdrs.put("isExtra", true);
                hdrs.put("header1", "header1Value");
                hdrs.put("header2", "header2Value");
                smsg.put(STRUCTURED_MESSAGE_HEADERS_PROPERTY, hdrs);
                smsg.put(STRUCTURED_MESSAGE_MESSAGE_PROPERTY, extraTestMsgs.get(i - mapMsgCount));
                ep.object = smsg;
            }
            
            byte[] msgBytes = mapper.writeValueAsBytes(ep);
            
            // Given the message bytes, simulate delivery of our WebSocket message
            tl.onMessage(ws, new ByteString(msgBytes));
        }
        
        // Consumers run in BG threads, so wait a bit for those to finish.
        mocked.await(5L, TimeUnit.SECONDS);
        
        assertEquals(mapMsgCount + extraTestMsgs.size(), mocked.getReceivedCounter(),
                     "Mocked service expected vs. actual");
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        AtomicInteger msgsReceived = new AtomicInteger();
        exchanges.forEach(exchange -> {
            Object exchangeBody = exchange.getIn().getBody();
            Object msg = deserializeJsonBody(exchangeBody, mapper, streamRequired);
            Map<String, Object> exchangeHdrs = exchange.getIn().getHeaders();
            msgsReceived.addAndGet(1);
            assertNotNull(msg, "Deserialized msg is null");
            if (msg instanceof Map) {
                assert ((Map<?,?>) msg).containsKey(TEST_MSG_KEY);
                assert ((Map<?,?>) msg).get(TEST_MSG_KEY) instanceof String;
                assert ((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
                assert exchangeHdrs.get("theMessage") instanceof Map;
                assert (((Map<?,?>) msg).get(TEST_MSG_KEY)).equals(
                        ((Map<?,?>) exchangeHdrs.get("theMessage")).get(TEST_MSG_KEY));
                assert exchangeHdrs.get("counter") instanceof Number;
                uniqueMsgs.add(((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)));
            } else if (msg instanceof String) {
                assertEquals(msg, extraTestMsgs.get(0));
                assert exchangeHdrs.get("theMessage") instanceof String;
                assert msg.equals(exchangeHdrs.get("theMessage"));
                assert exchangeHdrs.get("counter") instanceof Number;
                assert exchangeHdrs.get("isExtra") instanceof Boolean;
                assert (Boolean) exchangeHdrs.get("isExtra");
                assert exchangeHdrs.get("header1") instanceof String;
                assert exchangeHdrs.get("dupHeader1") instanceof String;
                assert exchangeHdrs.get("header1").equals(exchangeHdrs.get("dupHeader1"));
                assert exchangeHdrs.get("header2") instanceof String;
                assert exchangeHdrs.get("dupHeader2") instanceof String;
                assert exchangeHdrs.get("header2").equals(exchangeHdrs.get("dupHeader2"));
            } else {
                assert msg.equals(exchangeHdrs.get("theMessage"));
                assert exchangeHdrs.get("theMessage").getClass().equals(msg.getClass());
                assert exchangeHdrs.get("counter") instanceof Number;
                assert exchangeHdrs.get("isExtra") instanceof Boolean;
                assert (Boolean) exchangeHdrs.get("isExtra");
                assert exchangeHdrs.get("header1") instanceof String;
                assert exchangeHdrs.get("dupHeader1") instanceof String;
                assert exchangeHdrs.get("header1").equals(exchangeHdrs.get("dupHeader1"));
                assert exchangeHdrs.get("header2") instanceof String;
                assert exchangeHdrs.get("dupHeader2") instanceof String;
                assert exchangeHdrs.get("header2").equals(exchangeHdrs.get("dupHeader2"));
    
            }
        });
        assert uniqueMsgs.size() == mapMsgCount;
        assert msgsReceived.get() == mapMsgCount + extraTestMsgs.size();
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
        
        assertEquals(expectedMsgCount, mocked.getReceivedCounter(), "Mocked service expected vs. actual");
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        exchanges.forEach(exchange -> {
            assert exchange.getIn().getBody() instanceof Map;
            Map<?,?> msg = exchange.getIn().getBody(Map.class);
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
        //noinspection unchecked
        Map<String, Object> response = (Map<String, Object>) o;
        log.debug("Found final response message: {}", response);
        assert response.containsKey("Response");
        String ra = rsp.getHeader(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        assert responseAddresses.contains(ra);
    }
    
    @Test
    public void testVantiqConsumerOutputJsonQuery() throws Exception {
        performConsumerOutputJsonQueryTest(vantiqJsonQuerierUri,false);
    }
    
    @Test
    public void testVantiqConsumerOutputJsonQueryStreamed() throws Exception {
        performConsumerOutputJsonQueryTest(vantiqJsonQuerierStreamUri,true);
    }
    void performConsumerOutputJsonQueryTest(String consumerEndpoint, boolean streamRequired) throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(consumerEndpoint);
    
        assertNotNull(endp.myClient.getListener(), "Endpoint's client is null");
        assertInstanceOf(TestListener.class, endp.myClient.getListener(), "Endpoint's client is not a TestListener: " +
                endp.myClient.getListener().getClass().getName());
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
        
        assertEquals(expectedMsgCount, mocked.getReceivedCounter(), "Mocked service expected vs. actual");
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        exchanges.forEach(exchange -> {
            Object exchBody = exchange.getIn().getBody();
            Object mo = deserializeJsonBody(exchBody, mapper, streamRequired);
            assert mo instanceof Map;
            Map<?,?> msg = (Map<?, ?>) mo;
            assertNotNull(msg, "Deserialized JSON message is null");
            assert msg.containsKey(TEST_MSG_KEY);
            assert msg.get(TEST_MSG_KEY) instanceof String;
            assert ((String) msg.get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
            uniqueMsgs.add(((String) msg.get(TEST_MSG_KEY)));
            
            Map<String, Object> props = exchange.getProperties();
            assert props.containsKey(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        });
        assert uniqueMsgs.size() == expectedMsgCount;
        
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint ep = (FauxVantiqEndpoint) context.getEndpoint(vantiqJsonQuerierUri);
        FalseClient fc = ep.myClient;
        Response rsp = fc.getLastMessageAsResponse();
        assert rsp != null;
        assert rsp.getBody() != null;
        assert rsp.getStatus() == 200;
        Object o = rsp.getBody();
        assert o instanceof Map;
        //noinspection unchecked
        Map<String, Object> response = (Map<String, Object>) o;
        log.debug("Found final response message: {}", response);
        assert response.containsKey("Response");
        String ra = rsp.getHeader(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER);
        assert responseAddresses.contains(ra);
    }
    
    protected Object deserializeJsonBody(Object exchangeBody, ObjectMapper mapper, boolean streamExpected) {
        assertNotNull(exchangeBody, "Null exchange body");
        // Verify that we got JSON -- which will manifest here as a String, but we'll verify that we can convert it.
        assertTrue(exchangeBody instanceof BaseJsonNode || exchangeBody instanceof StreamCache,
                   "Vantiq Consumer Output wrong type: " + exchangeBody.getClass().getName());
        if (exchangeBody instanceof StreamCache) {
            assert streamExpected;
            ((StreamCache) exchangeBody).reset();
            byte[] ba = null;
            if (exchangeBody instanceof InputStreamCache) {
                ba = ((InputStreamCache) exchangeBody).readAllBytes();
            } else if (exchangeBody instanceof ByteArrayInputStreamCache) {
                try {
                    ba = ((ByteArrayInputStreamCache) exchangeBody).readAllBytes();
                } catch (IOException e) {
                    fail("Trapped exception reading StreamCache: " + e.getMessage());
                }
            } else {
                fail("Unexpected StreanCache type: " + exchangeBody.getClass().getName());
            }
            exchangeBody = ba;
            try {
                ba = Base64.getDecoder().decode(ba);
                exchangeBody = new String(ba, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException nope) {
                // Guess it's not encoded
            }
        } else {
            assert !streamExpected;
        }
        Object msg = null;
        if (exchangeBody instanceof byte[]) {
            try {
                msg = mapper.readTree((byte[]) exchangeBody);
                if (msg instanceof TextNode) {
                    msg = ((TextNode) msg).asText();
                } else if (msg instanceof ContainerNode) {
                    msg = mapper.convertValue(msg, Object.class);
                    log.debug("Decoded ObjectNode into {}: {}", msg.getClass().getName(), msg);
                }
            } catch (IOException e) {
                // Ignore -- we'll sort it out downstream
            }
        }
        if (msg == null) {
            try {
                msg = mapper.convertValue(exchangeBody, Map.class);
            } catch (Exception trySomethingNew) {
                try {
                    msg = mapper.convertValue(exchangeBody, String.class);
                } catch (Exception tryAgain) {
                    try {
                        msg = mapper.convertValue(exchangeBody, List.class);
                    } catch (Exception e) {
                        log.error("Trapped exception: ", e);
                        msg = e;
                        assertNull(e, "Trapped exception: " + e.getMessage());
                    }
                }
            }
        }
        return msg;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // since everything starts here, we need to instantiate this bean before startup so here as things
                // are defined.
                HeaderDuplicationBean hdBean = new HeaderDuplicationBean();
                Map<String, String> heMap = Map.of("header1", "dupHeader1",
                                                   "header2", "dupHeader2");
                hdBean.setHeaderDuplicationMap(heMap);
                context.getRegistry().bind(TEST_HEADER_BEAN_NAME, hdBean);
                HeaderDuplicationBean testHdBean =
                        context.getRegistry().lookupByNameAndType(TEST_HEADER_BEAN_NAME, HeaderDuplicationBean.class);
                assertNotNull(testHdBean);
                Map<String, String> testHeMap = testHdBean.getHeaderDuplicationMap();
                assertNotNull(testHeMap);
                assertEquals(heMap.size(), testHeMap.size());
    
                heMap.forEach( (k, v) -> {
                    assertEquals(heMap.get(k), testHeMap.get(k));
                });
                // Override the component type to be used...
                context.addComponent("vantiq", new FauxVantiqComponent());
                for (String name: context.getComponentNames()) {
                    log.info("Component name: {}", name);
                }
    
                onException(InvalidPayloadException.class)
                        .log(LoggingLevel.ERROR, "Got InvalidPayloadException")
                                .to(exceptionEndpoint);
                
                from(routeStartUri)
                  .to(vantiqEndpointUri);

                from(routeStartStructuredUri)
                        .to(vantiqEndpointStructuredUri);

                from(vantiqSenderUri)
                        .to(routeEndUri);

                from(vantiqJsonSenderUri)
                        .to(routeEndUri);
    
                from(vantiqJsonSenderStreamUri)
                        .to(routeEndUri);
    
    
                from(vantiqStructuredJsonSenderUri)
                        .to(routeEndUri);
    
                from(vantiqStructuredJsonSenderStreamUri)
                        .to(routeEndUri);
    
                from(vantiqStructuredJsonSenderMappedUri)
                        .to(routeEndUri);
    
                from(vantiqStructuredJsonSenderMappedStreamUri)
                        .to(routeEndUri);
    
    
                from(routeStartStructuredHeaderMapUri)
                        .to(vantiqEndpointStructuredHeaderMapUri);
                
                from(vantiqQuerySenderUri)
                        .setExchangePattern(ExchangePattern.InOut)
                        .to(routeEndUri)
                            .setBody(constant("{ \"Response\": \"Message\"}"))
                        .to(vantiqQuerySenderUri);

                from(vantiqJsonQuerierUri)
                        .setExchangePattern(ExchangePattern.InOut)
                        .to(routeEndUri)
                        .setBody(constant("{ \"Response\": \"Message\"}"))
                        .to(vantiqJsonQuerierUri);
    
                from(vantiqJsonQuerierStreamUri)
                        .setExchangePattern(ExchangePattern.InOut)
                        .to(routeEndUri)
                        .setBody(constant("{ \"Response\": \"Message\"}"))
                        .to(vantiqJsonQuerierUri);
            }
        };
    }
}
