/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.opcua.uaOperations;

public class OpcConstants {
    // Constants for use in perusing and perparing (primarily) the configuration documents.

    public static final String CONFIG_OPC_UA_INFORMATION = "opcUAInformation";
    public static final String CONFIG_OPC_MONITORED_ITEMS = "monitoredItems";
    public static final String CONFIG_MI_NODEID = "nodeId";
    public static final String CONFIG_MI_IDENTIFIER = "nodeIdentifier";
    public static final String CONFIG_MI_IDENTIFIER_TYPE = "nodeIdentifierType"; // One of OPC UA types: {i, s, g, b}
    // If not present, the assumption is that it's a string
    public static final String CONFIG_MI_NAMESPACE_INDEX = "ns";        // Short, but the OPCUA Conventiion
    public static final String CONFIG_MI_NAMESPACE_URN = "nsu";
    public static final String CONFIG_SECURITY_POLICY = "securityPolicy";
    public static final String CONFIG_MESSAGE_SECURITY_MODE = "messageSecurityMode";
    public static final String CONFIG_DISCOVERY_ENDPOINT = "discoveryEndpoint";
    public static final String CONFIG_STORAGE_DIRECTORY = "storageDirectory";
    public static final String CONFIG_IDENTITY_ANONYMOUS = "identityAnonymous";
    public static final String CONFIG_IDENTITY_CERTIFICATE = "identityCertificate";
    public static final String CONFIG_IDENTITY_USERNAME_PASSWORD = "identityUsernamePassword";

    // Constants related to the interaction with Vantiq operations.
    // Some config constants are used there for consistency.

    public static final String VANTIQ_URL = "vantiqUrl";
    public static final String VANTIQ_USERNAME = "username";
    public static final String VANTIQ_PASSWORD = "password";
    public static final String VANTIQ_TOKEN = "token";
    public static final String VANTIQ_SOURCENAME = "sourceName";
    public static final String OPC_VALUE_IN_VANTIQ = "dataValue";

    public static final String PUBLISH_INTENT = "intent";
    public static final String PUBLISH_INTENT_UPSERT = "upsert";
    public static final String PUBLISH_INTENT_SUBSCRIBE = "subscribe";
    public static final String PUBLISH_INTENT_UNSUBSCRIBE = "unsubscribe";
    public static final String QUERY_STYLE = "queryStyle";
    public static final String QUERY_STYLE_BROWSE = "browse";
    public static final String QUERY_STYLE_QUERY = "query";
    public static final String QUERY_STYLE_NODEID = "nodeId";

    public static final String NODE_ID = "nodeId";
}
