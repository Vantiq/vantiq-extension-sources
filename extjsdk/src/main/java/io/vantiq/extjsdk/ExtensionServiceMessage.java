
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

import okhttp3.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * ServiceMessage for External Sources
 *
 * These messages are used for Extension sources (at least).  They are identical to ServiceMessages
 * EXCEPT that 1) they have an address (since they may be destined to things outside VANTIQ, and
 * 2) they must serialize to a buffer rather than as a Map.  Since they're going over the wire,
 * this works better.
 *
 * Created by fcarter 7 Jun 2018
 */

public class ExtensionServiceMessage {
    public static final String RESOURCE_NAME_SOURCES = "sources";
    public static final String OP_CONFIGURE_EXTENSION = "configureExtension";
    public static final String OP_CONNECT_EXTENSION = "connectExtension";
    public static final String OP_PUBLISH = "publish";
    public static final String OP_NOTIFICATION = "notification";
    public static final String OP_QUERY = "query";
    public static final String OP_RECONNECT_REQUIRED = "reconnectRequired";
    public static final MediaType CONTENT_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    public static final String ORIGIN_ADDRESS_HEADER = "REPLY_ADDR_HEADER";
    public static final String RESPONSE_ADDRESS_HEADER = "X-Reply-Address";
    public static final String PROPERTY_MESSAGE_HEADERS = "messageHeaders";

    public String address;
    public Map    messageHeaders;

    /**
     * The name of the namespace against which the request is issued
     */
    public String namespaceName;

    /**
     * The locale in which the request should be processed.
     */
    public String locale;

    /**
     * The requested operation.
     */
    public String op;

    /**
     * The name of the target resource
     */
    public String resourceName;

    /**
     * Indicates whether this should be treated as a system resource.
     */
    boolean isSystemResource;

    /**
     * The identifier of the target resource (if any)
     */
    public String resourceId;

    /**
     * The parameters supplied for the operation.
     */
    public Map parameters;

    /**
     * The body content of the operation.
     */
    public Object object;

    /**
     * The execution context that should be established for this message.
     */
    Map executionContext;

    /**
     * If this request is part of a session then this will contain the session id.  See SessionListener for more details.
     */
    String sessionId;

    /**
     * The content type of the message object
     */
    public String contentType = CONTENT_TYPE_JSON.toString();

    /**
     * The content type produced by the execution of the message.  Default is "null" which means use what server
     * returns.
     */
    public String responseType = null;

    /**
     * Set to <code>true</code> when we want to avoid recording any monitoring data for the message.
     */
    boolean skipMonitoring;

    /**
     * Indicates that this request came from an "external" source and should be validated as such.
     */
    boolean isExternal;

    public ExtensionServiceMessage(String address) {
        super();
        this.address = address;
        this.messageHeaders = new HashMap();
    }


    public ExtensionServiceMessage connectExtension(String resourceName, String resourceId, Object announcement) {
        this.op = OP_CONNECT_EXTENSION;
        this.resourceName = resourceName;
        this.resourceId = resourceId;
        this.object = announcement;
        return this;
    }


    @Override
    public String toString() {
        return asMap().toString();
    }

    public Object getObject() {
        return this.object;
    }
    
    public String getSourceName() {
        return this.resourceId;
    }
    
    public Map<String,Object> getMessageHeaders() {
        return this.messageHeaders;
    }
    
    public String getOp() {
        return this.op;
    }
    
    /**
     * Extract reply address from a message.
     *
     * This is a utility method that examines the various forms a message may take and
     * extracts the reply address if present.
     *
     * @param msg Message from which to extract the reply address
     * @return String The reply address (or null if absent from msg).
     *
     * @throws IllegalArgumentException if the msg parameter with neither a Map nor an ExtensionServiceMessage
     *
     */
    public static String extractReplyAddress(Object msg) {
        String repAddr = null;
        Object maybeRepAddr = null;
        if (msg instanceof Map) {
            Map msgMap = (Map) msg;
            Object maybeMap = msgMap.get(ExtensionServiceMessage.PROPERTY_MESSAGE_HEADERS);
            if (maybeMap instanceof Map) {
                maybeRepAddr = ((Map) msgMap.get(ExtensionServiceMessage.PROPERTY_MESSAGE_HEADERS)).get(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER);
                if (maybeRepAddr != null && maybeRepAddr instanceof String) {
                    repAddr = (String) maybeRepAddr;
                }
            }
        } else if (msg instanceof ExtensionServiceMessage) {
            ExtensionServiceMessage esm = (ExtensionServiceMessage) msg;
            maybeRepAddr = esm.messageHeaders.get(ExtensionServiceMessage.ORIGIN_ADDRESS_HEADER);
        } else {
            throw new IllegalArgumentException("extractReplyAddress requires either a Map or ExtensionServiceMessage; received " + msg.getClass().getName());
        }
        if (maybeRepAddr instanceof String) {
            repAddr = (String) maybeRepAddr;
        }
        return repAddr;
    }
    
    /**
     * Convert this DataMessage into a Java Map.
     * Primarily used to transform the message into a form that can be
     * stored in the document store.
     *
     * @return - the DataMessage as a map
     */
    public Map asMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("address", address);
        if (namespaceName != null) m.put("namespaceName", namespaceName);
        if (locale != null) m.put("locale", locale);
        if (op != null) m.put("op", op);
        if (resourceName != null) m.put("resourceName", resourceName);
        m.put("isSystemResource", isSystemResource);
        if (resourceId != null) m.put("resourceId", resourceId);
        if (parameters != null) m.put("parameters", parameters);
        if (object != null) m.put("object", object);
        if (sessionId != null) m.put("sessionId", sessionId);
        if (contentType != null) m.put("contentType", contentType);
        if (responseType != null) m.put("responseType", responseType);
        if (executionContext != null) m.put("executionContext", executionContext);
        if (skipMonitoring) m.put("skipMonitoring", skipMonitoring);
        if (isExternal) m.put("isExternal", isExternal);
        m.put("messageHeaders", messageHeaders);

        return m;
    }

    /**
     * Convert the contents of the Map into the contents of this DataMessage.
     *
     * @param mapOfMessage - the map representation of the message.
     * @return - the DataMessage populated with the mapOfMessage contents.
     */
    public ExtensionServiceMessage fromMap(Map mapOfMessage) {
        Map m = mapOfMessage;
        if (m.containsKey("namespaceName")) namespaceName = (String) m.get("namespaceName");
        if (m.containsKey("locale")) locale = (String) m.get("locale");
        if (m.containsKey("op")) op = (String) m.get("op");
        if (m.containsKey("isSystemResource")) isSystemResource = (boolean) m.get("isSystemResource");
        if (m.containsKey("resourceName")) resourceName = (String) m.get("resourceName");
        if (m.containsKey("resourceId")) resourceId = (String) m.get("resourceId");
        if (m.containsKey("object")) object = m.get("object");
        if (m.containsKey("sessionId")) sessionId = (String) m.get("sessionId");
        if (m.containsKey("contentType")) contentType = (String) m.get("contentType");
        if (m.containsKey("responseType")) responseType = (String) m.get("responseType");
        if (m.containsKey("skipMonitoring")) skipMonitoring = (boolean) m.get("skipMonitoring");
        if (m.containsKey("isExternal")) isExternal = (boolean) m.get("isExternal");
        if (m.containsKey("messageHeaders")) messageHeaders = (Map) m.get("messageHeaders");
        return this;
    }

}
