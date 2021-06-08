
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

//Authors: Alex Blumer, Namir Fawaz, Fred Carter
//Email: support@vantiq.com


import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okio.ByteString;

/**
 * A listener that contains methods to create sample responses and send those messages to itself, and getters for
 * its handlers. 
 */
public class TestListener extends ExtensionWebSocketListener {
    public TestListener(ExtensionWebSocketClient client) {
        super(client);
    }

    FalseWebSocket falseWebSocket = new FalseWebSocket();
    public Handler<Response> getAuthHandler() {
        return authHandler;
    }
    public Handler<Response> getHttpHandler() {
        return httpHandler;
    }
    public Handler<ExtensionServiceMessage> getConfigHandler() {
        return configHandler;
    }
    public Handler<ExtensionServiceMessage> getPublishHandler() {
        return publishHandler;
    }
    public Handler<ExtensionServiceMessage> getQueryHandler() {
        return queryHandler;
    }
    public Handler<ExtensionServiceMessage> getReconnectHandler() {
        return reconnectHandler;
    }
    
    /**
     * Makes the listener receive a response specifying either a successful or failed authentication
     * @param success   Whether the authentication response should respond as a success 
     */
    public void receiveAuthenticationResponse(boolean success) {
        this.onMessage(falseWebSocket, createAuthenticationResponse(success));
    }
    /**
     * Makes the listener receive a configuration message, signifying a successful source connection. A failed
     * connection is sent using {@link #errorMessage()}.
     * @param config        The configuration document that will be received
     * @param sourceName    The name of the source for which the connection succeeded.
     */
    public void receiveConfigResponse(Map<String,Object> config, String sourceName) {
        this.onMessage(falseWebSocket, createConfigResponse(config, sourceName));
    }
    /**
     * Makes the listener receive a Publish message
     * @param message       The object sent with the Publish
     * @param sourceName    The name of the source that sent the message
     */
    public void receivePublishMessage(Map<String,Object> message, String sourceName) {
        this.onMessage(falseWebSocket, createPublishMessage(message, sourceName));
    }
    /**
     * Makes the listener receive a Query message
     * @param message       The data to be received along with the Query message
     * @param sourceName    The name of the source that sent the message
     */
    public void receiveQueryMessage(Map<String,Object> message, String sourceName) {
        this.onMessage(falseWebSocket, createQueryMessage(message, sourceName));
    }
    /**
     * Makes the listener receive a reconnect message
     * @param sourceName    The name of the source that sent the message
     */
    public void receiveReconnectMessage(String sourceName) {
        this.onMessage(falseWebSocket, createReconnectMessage(sourceName));
    }
    /**
     * Makes the listener receive an HTTP message.
     * @param resp The {@link Response} that the listener will receive
     */
    public void receiveHttpMessage(Response resp) {
        this.onMessage(falseWebSocket, createHttpMessage(resp));
    }
    /**
     * Makes the listener receive a simple HTTP error message. This is a {@link Response} with status code 400.
     */
    public void receiveErrorMessage() {
        this.onMessage(falseWebSocket, errorMessage());
    }

    public static MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Create a ResponseBody with a simple error. This is a {@link Response} with status code 400.
     * @return  A ResponseBody representing the message
     */
    public static ByteString errorMessage() {
        String errorString = "{\"status\":400}";
        return ByteString.of(errorString.getBytes());
    }
    /**
     * Creates a response specifying either a successful or failed authentication
     * @param success   Whether the authentication response should respond as a success
     * @return              A ResponseBody representing the message
     */
    public static ByteString createAuthenticationResponse(boolean success) {
        if (success) {
            return ByteString.of(sampleAuthResponseBody.getBytes());
        }
        else {
            return errorMessage();
        }
    }
    /**
     * Creates a configuration message, signifying a successful source connection. A failed
     * connection is sent using {@link #errorMessage()}.
     * @param config        The configuration document that will be received
     * @param sourceName    The name of the source for which the connection succeeded.
     * @return              A ByteString representing the message
     */
    public static ByteString createConfigResponse(Map<String, Object> config, String sourceName) {
        try {
            Map<String,Object> body = mapper.readValue(sampleConfigBody, Map.class);
            Map<String,Object> c = new LinkedHashMap<>();
            c.put("config", config);
            body.put("resourceId", sourceName);
            body.put("object", c);
            return ByteString.of(mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            return ByteString.EMPTY;
        }
    }
    /**
     * Creates a Publish message
     * @param message       The object sent with the Publish
     * @param sourceName    The name of the source that sent the message
     * @return              A ByteString representing the message
     */
    public static ByteString createPublishMessage(Map<String, Object> message, String sourceName) {
        try {
            Map<String,Object> body = mapper.readValue(samplePublishBody, Map.class);
            body.put("resourceId", sourceName);
            body.put("object", message);
            return ByteString.of(mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            return ByteString.EMPTY;
        }
    }
    /**
     * Creates a Query message
     * @param message       The data to be received along with the Query message
     * @param sourceName    The name of the source that sent the message
     * @return              A ByteString representing the message
     */
    public static ByteString createQueryMessage(Map<String, Object> message, String sourceName) {
        try {
            Map<String,Object> body = mapper.readValue(sampleQueryBody, Map.class);
            body.put("resourceId", sourceName);
            body.put("object", message);
            return ByteString.of(mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            return ByteString.EMPTY;
        }
    }
    /**
     * Creates an HTTP message.
     * @param resp The {@link Response} that the listener will receive
     * @return              A ByteString representing the message
     */
    public static ByteString createHttpMessage(Response resp) {
        try {
            return ByteString.of(mapper.writeValueAsBytes(resp));
        }
        catch (Exception e) {
            return ByteString.EMPTY;
        }
    }
    /**
     * Creates a reconnect message
     * @param sourceName    Name of the source that sent the message
     * @return              A ByteString representing the message
     */
    public static ByteString createReconnectMessage(String sourceName) {
        try {
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("resourceId", sourceName);
            body.put("op", ExtensionServiceMessage.OP_RECONNECT_REQUIRED);
            return ByteString.of(mapper.writeValueAsBytes(body));
        }
        catch (Exception e) {
            return ByteString.EMPTY;
        }
    }
    
    public static final String sampleAuthResponseBody = "{\"status\":200, \"contentType\":\"application/json\", \"body\":{\"anonymous\":false, " +
            "\"userInfo\":{\"accessToken\":\"1c55ag9pyVZFa8k9BTGgI7XiI7nHTLTtLMRKBZ0=\", " +
            "\"idToken\":\"yJ0eXAiOiJKV1QLCJhbGcOiJIUzI1NiJ9.eyJzdWIiOiJ1ZHBzb3VyY2V0b2tlbl9fc3lzdGVtIiwicHJlZmVycmVkVX" +
            "lcm5hbWUiOiJ1ZHBzb3VyY2V0b2tlbl9fc3lzdGVtIiwiaXNzIjoiaHR0cHM6Ly92YW50aXEuY29tL2F1dGhlbnRpY2F0ZSIsInBy" +
            "ZpbGVzIjpbInN5c3RlbS51c2VyIl0sInByZWZlcnJlZF91c2VybmFtZSI6InVkcHNvdXJZXRva2Vu19zeXN0ZW0iLCJob21lTmFtZNw" +
            "YWNlIjoic3lzdGVtIiwiWRlbnRpdHlQcm92aWRlciI6IlZhbnRpcSIsImF1ZCI6InZhnRpcS1jbllbnQiLCJjcmVhdGVkQnkiOiJze" +
            "XN0ZW0iLCJuYW1lc3BhY2UiOiJzeXN0ZW0iLCJ1c2VyVHlwZSI6Im5vZGUiLCJleHAiOj1OTM2NDYwNTAsImlhdCI6MTUzMTg2ODI0Cw" +
            "ianRpIjoiYzRkMU0NDAtOGExNC0xMWU4LWI4ODAtNDgxNTJkNDRhNTg5IiwidXNlcm5hbWUiOiJ1ZHBzb3VyY20b2tlbl9fc3lzdGVtI" +
            "n0.h9EVfLQcxVVfpuPKkr8bwcXuCrF2k8wgdkavfs-M\", " +
            "\"username\":\"udpsourcetoken__system\", \"preferredUsername\":\"udpsourcetoken__system\", \"namespace\":\"system\", " +
            "\"homeNamespace\":\"system\", \"createdBy\":\"system\", \"userType\":\"node\", \"profiles\":[\"system.user\"]}}}";
    public static final String sampleConfigBody = "{\"op\":\"configureExtension\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
            "\"parameters\":{}, \"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
            "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", \"messageHeaders\":{}}";
    public static final String samplePublishBody = "{\"op\":\"publish\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
            "\"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
            "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", \"messageHeaders\":{}}";
    public static final String sampleQueryBody = "{\"op\":\"query\", \"resourceName\":\"sources\", \"isSystemResource\":true, " +
            "\"contentType\":\"application/json\", \"skipMonitoring\":false, \"isExternal\":false, " +
            "\"address\":\"c672e138-2915-433d-99fe-78a6661ea047\", " +
            "\"messageHeaders\":{\"REPLY_ADDR_HEADER\":\"d15cf6b0-8a1f-11e8-b880-48152d44a589\"}}";
}
