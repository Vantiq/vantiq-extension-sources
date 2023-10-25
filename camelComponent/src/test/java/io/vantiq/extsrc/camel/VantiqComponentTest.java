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
import static io.vantiq.extsrc.camel.VantiqEndpoint.SOURCE_NAME_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADER_PARAM;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_HEADERS_PROPERTY;
import static io.vantiq.extsrc.camel.VantiqEndpoint.STRUCTURED_MESSAGE_MESSAGE_PROPERTY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.FalseClient;
import io.vantiq.extjsdk.FalseWebSocket;
import io.vantiq.extjsdk.Response;
import io.vantiq.extjsdk.TestListener;

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
import okhttp3.WebSocket;
import okio.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class VantiqComponentTest extends CamelTestSupport {
    
    private static final String testSourceName = "camelSource";
    private static final String accessToken = "someAccessToken";
    private static final String TEST_MSG_PREAMBLE = "published message from FauxVantiq #";
    private static final String TEST_MSG_KEY = "someRandomKey";
    
    private final String routeStartUri = "direct:start";
    
    private final String routeStartStructuredUri = "direct:structuredstart";
    private final String routeEndUri = "mock:direct:result";
    
    private final String exceptionEndpoint = "mock:direct:error";
    private final String vantiqEndpointUri = "vantiq://doesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken;
    
    private final String vantiqEndpointStructuredUri = "vantiq://doesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true";
    
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
    
    public final String vantiqStructuredJsonSenderUri = "vantiq://structuredsenderdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_PARAM + "=true" +
            "&" + STRUCTURED_MESSAGE_HEADER_PARAM + "=true";
    
    private final String vantiqJsonQuerierUri = "vantiq://jsonquerierdoesntmatter/" +
            "?" + SOURCE_NAME_PARAM + "=" + testSourceName +
            "&" + ACCESS_TOKEN_PARAM + "=" + accessToken +
            "&" + CONSUMER_OUTPUT_JSON_PARAM + "=true";
    
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
        Map timeMsg = Map.of("time", rightNow);
        sendBody(routeStartUri, timeMsg);
    
        //noinspection rawtypes
        lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, false, null, new String[] {"time"}, rightNow.toString());
    
        testBytes = new byte[] { (byte) 0xf8, (byte) 0xfa, (byte) 0xfb, (byte) 0xff };
        sendBody(routeStartUri, testBytes);
        lastMsg = fc.getLastMessageAsMap();
        validateExtensionMsg(lastMsg, false, null, new String[] { "byteVal"}, testBytes);
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
                //noinspection unchecked
                Map<String, Object> hdrs =
                        (Map<String, Object>) msg.get(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
                assert hdrs != null;
                assert expHdrs.size() == hdrs.size();
                expHdrs.forEach( (key, value) -> {
                    assert hdrs.containsKey(key);
                    assert hdrs.get(key) != null;
                    assert expHdrs.get(key) != null;
                    log.debug("For key {}, comparing value {} ){} with expected {} ({}).",
                              key, hdrs.get(key), hdrs.get(key).getClass().getName(),
                              expHdrs.get(key), expHdrs.get(key).getClass().getName());
                    assert hdrs.get(key).equals(expHdrs.get(key));
                });
            }
            assert msg.containsKey(STRUCTURED_MESSAGE_MESSAGE_PROPERTY);
            assert msg.containsKey(STRUCTURED_MESSAGE_HEADERS_PROPERTY);
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
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(vantiqJsonSenderUri);
        
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
        
        assertEquals("Mocked service expected vs. actual", mapMsgCount + extraTestMsgs.size(),
                     mocked.getReceivedCounter());
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        AtomicInteger msgsReceived = new AtomicInteger();
        exchanges.forEach(exchange -> {
            Object exchangeBody = exchange.getIn().getBody();
            assertNotNull("Null exchange body",  exchangeBody);
            // Verify that we got JSON -- which will manifest here as a String, but we'll verify that we can convert it.
            assertTrue("Vantiq Consumer Output wrong type: " + exchangeBody.getClass().getName(),
                exchangeBody instanceof BaseJsonNode);
            msgsReceived.addAndGet(1);
            Object msg;
            try {
                msg = mapper.convertValue(exchangeBody, Map.class); // readValue((ObjectNode) exchangeBody, Map.class);
            } catch (Exception trySomethingNew) {
                try {
                    msg = mapper.convertValue(exchangeBody, String.class);
                } catch (Exception tryAgain) {
                    try {
                        msg = mapper.convertValue(exchangeBody, List.class);
                    } catch (Exception e) {
                        log.error("Trapped exception: ", e);
                        msg = e;
                        assertNull("Trapped exception: " + e.getMessage(), e);
                        // about it
                    }
                }
            }
            assertNotNull("Deserialized msg is null", msg);
            if (msg instanceof Map) {
                assert ((Map<?,?>) msg).containsKey(TEST_MSG_KEY);
                assert ((Map<?,?>) msg).get(TEST_MSG_KEY) instanceof String;
                assert ((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)).startsWith(TEST_MSG_PREAMBLE);
                uniqueMsgs.add(((String) ((Map<?,?>) msg).get(TEST_MSG_KEY)));
            } else if (msg instanceof String) {
                assertEquals(msg, extraTestMsgs.get(0));
            } else {
                assertEquals(msg, extraTestMsgs.get(1));
            }
        });
        assert uniqueMsgs.size() == mapMsgCount;
        assert msgsReceived.get() == mapMsgCount + extraTestMsgs.size();
    }
    
    @Test
    public void testVantiqConsumerOutputJsonStructured() throws Exception {
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(vantiqStructuredJsonSenderUri);
        
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
        
        assertEquals("Mocked service expected vs. actual", mapMsgCount + extraTestMsgs.size(),
                     mocked.getReceivedCounter());
        List<Exchange> exchanges = mocked.getReceivedExchanges();
        Set<String> uniqueMsgs = new HashSet<>();
        AtomicInteger msgsReceived = new AtomicInteger();
        exchanges.forEach(exchange -> {
            Object exchangeBody = exchange.getIn().getBody();
            Map<String, Object> exchangeHdrs = exchange.getIn().getHeaders();
            assertNotNull("Null exchange body",  exchangeBody);
            // Verify that we got JSON -- which will manifest here as a String, but we'll verify that we can convert it.
            assertTrue("Vantiq Consumer Output wrong type: " + exchangeBody.getClass().getName(),
                       exchangeBody instanceof BaseJsonNode);
            msgsReceived.addAndGet(1);
            Object msg;
            try {
                msg = mapper.convertValue(exchangeBody, Map.class); //readValue((String) exchangeBody, Map.class);
            } catch (Exception e) {
                // Maybe we asked for the wrong type
                try {
                    msg = mapper.convertValue(exchangeBody, String.class);
                } catch (Exception ex) {
                    try {
                        msg = mapper.convertValue(exchangeBody, List.class);
                        // readValue((String) exchangeBody,List.class);
                    } catch (Exception exc) {
                        throw new RuntimeException(exc);
                    }
                }
            }
            assertNotNull("Deserialized msg is null", msg);
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
        
        // First, grab our test environment.
        FauxVantiqComponent vc = (FauxVantiqComponent) context.getComponent("vantiq");
        assert vc != null;
        // Note that we need to fetch the endpoints by URI since there are more than one of them.
        FauxVantiqEndpoint endp = (FauxVantiqEndpoint) context.getEndpoint(vantiqJsonQuerierUri);
    
        assertNotNull("Endpoint's client is null", endp.myClient.getListener());
        assertTrue("Endpoint's client is not a TestListener: " +
                              endp.myClient.getListener().getClass().getName(),
                      endp.myClient.getListener() instanceof TestListener);
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
        Set<String> uniqueMsgs = new HashSet<>();
        exchanges.forEach(exchange -> {
            Object exchBody = exchange.getIn().getBody();
            assertNotNull("Exchange boddy is null", exchBody);
            assertTrue("Exchange body wrong type: " + exchBody.getClass().getName(),
                       exchange.getIn().getBody() instanceof ObjectNode);
            Map<?,?> msg;
            try {
                msg = mapper.convertValue(exchBody, Map.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assertNotNull("Deserialized JSON message is null", msg);
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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                
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
    
                from(vantiqStructuredJsonSenderUri)
                        .to(routeEndUri);
    
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
            }
        };
    }
}
