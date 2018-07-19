package io.vantiq.extsrc.opcua.uaOperations;

import com.google.common.collect.ImmutableList;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;


public class OpcUaESClient {

    // Constants for use in perusing the configuration Map.

    public static final String CONFIG_OPC_UA_INFORMATION = "opcUAInformation";
    public static final String CONFIG_OPC_MONITORED_ITEMS = "monitoredItems";
    public static final String CONFIG_MI_NODEID = "nodeId";
    public static final String CONFIG_MI_IDENTIFIER = "nodeIdentifier";
    public static final String CONFIG_MI_IDENTIFIER_TYPE = "nodeIdentifierType"; // One of OPC UA types: {i, s, g, b}
    // If not present, the assumption is that it's a string
    public static final String CONFIG_MI_NAMESPACE_INDEX = "ns";        // Short, but the OPCUA Conventiion
    public static final String CONFIG_MI_NAMESPACE_URN = "nsu";
    public static final String CONFIG_SECURITY_POLICY = "securityPolicy";
    public static final String CONFIG_DISCOVERY_ENDPOINT = "discoveryEndpoing";
    public static final String CONFIG_STORAGE_DIRECTORY = "storageDirectory";


    public static final String ERROR_PREFIX = "io.vantiq.extsrc.opcua.uaOperations" + "OpcuaClient";
    private static final String SECURITY_DIRECTORY = "security";
    protected Map<String, Object> config;
    protected Logger logger;
    protected OpcUaClient client;
    protected UaSubscription subscription = null;
    protected BiConsumer<String, Object> subscriptionHandler;
    protected List<UInteger> currentMonitoredItemList = null;
    private final AtomicLong clientToMILink = new AtomicLong(1);
    private boolean connected = false;

    // For testing

    public Map<String, Object> getConfig() {
        return config;
    }

    /**
     * Create a client from information in the config document.  This config document is expected
     * to contain the culmination of any combinations or other manipulations that need to be done
     * in the calling system.
     * <p>
     * The config document contains the information necessary to create the client, as well as information
     * that may be used in other phases of this source's operation.  With respect to the connection, the
     * following information is used
     * <p>
     * (overall configuration document)
     * opcUAInformation
     * securityPolicy -- URI specifying the OPC UA Security Policy to use
     * discoveryEndpoint -- the discovery URL
     * storageDirectory -- the persistent store (probably disk) location for this extension source.  This
     * should be secure as security information will be stored here.
     *
     * @param theConfig The config document to use
     */
    public OpcUaESClient(Map theConfig) throws OpcExtConfigException, Exception   // FIXME
    {
        // FIXME -- Hook up SLF4J to log4J via config, etc.
        // Want to put logs into ${theConfig.opcUAInformation.storageDirectory}
        logger = LoggerFactory.getLogger(getClass());

        if (theConfig == null) {
            String errMsg = ERROR_PREFIX + ".nullConfig: Configuration was null";
            logger.error(errMsg);
            throw new OpcExtConfigException(errMsg);
        }

        validateConfiguration(theConfig);
        config = theConfig; // Store for later use

        // Extract OPC information and create the client.
        Map<String, Object> opcConfig = (Map<String, Object>) theConfig.get(CONFIG_OPC_UA_INFORMATION);

        client = createClient(opcConfig);
    }

    public void connect() throws Exception {
        try {
            client.connect().get();
        } catch (ExecutionException e) {
            if (e.getMessage().equals("java.nio.channels.UnresolvedAddressException")) {
                // This indicates that the server is not available.  Throw a more useful exception.
                throw new OpcExtConfigException(ERROR_PREFIX + ".serverUnavailable: OPC Server unavailable", e);
            } else {
                throw e;
            }
        }
    }

    public CompletableFuture<Void> connectAsync() throws Exception {
        return CompletableFuture.runAsync(() -> {
                    client.connect().join();
                    connected = true;
                }
        );
    }

    public void disconnect() {
        if (connected) {
            try {
                client.disconnect().get();  // Disconnect client & await completion thereof
            } catch (Throwable e) {
                logger.error(ERROR_PREFIX + ".disconnectFailure: " + e.getMessage());
            }
        }
        connected = false;
    }

    private void validateConfiguration(Map config) throws OpcExtConfigException {
        if (config == null) {
            String errMsg = ERROR_PREFIX + ".nullConfig: No configuration was provided.";
            logger.error(errMsg);
            throw new OpcExtConfigException(errMsg);
        } else if (config.get(CONFIG_OPC_UA_INFORMATION) == null) {
            String errMsg = ERROR_PREFIX + ".noOPCInformation: Configuration contained no OPC Information.";
            throwError(errMsg);
        }
        Map<String, String> opcConfig = (Map<String, String>) config.get(CONFIG_OPC_UA_INFORMATION);

        String errMsg = null;
        if (!opcConfig.containsKey(CONFIG_STORAGE_DIRECTORY)) {
            errMsg = ERROR_PREFIX + ".noStorageSpecified: No storageDirectory provided in configuration.";
        } else if (!opcConfig.containsKey(CONFIG_DISCOVERY_ENDPOINT)) {
            errMsg = ".noDiscoveryEndpoint: No discovery endpoint was provided in the configuration.";
        } else if (!opcConfig.containsKey(CONFIG_SECURITY_POLICY)) {
            errMsg = ".noSecurityPolicy: No OPC UA Security policy was specified in the configuration.";
        }

        if (errMsg != null) {
            errMsg = ERROR_PREFIX + errMsg;
            throwError(errMsg);
        }
    }

    private void throwError(String msg) throws OpcExtConfigException {
        logger.error(msg);
        throw new OpcExtConfigException(msg);
    }

    private SecurityPolicy getSecurityPolicy(Map<String, Object> config) throws OpcExtConfigException {
        // config.securityPolicy should be the URI for the appropriate security policy.

        String secPolURI = (String) config.get(CONFIG_SECURITY_POLICY);
        if (secPolURI == null) {
            // No security policy will default to #NONE.  We will, however, log a warning
            logger.warn(ERROR_PREFIX + ".defaultingSecurityPolicy: No OPC UA Security policy was specified in the configuration.  Defaulting to #NONE");
            secPolURI = SecurityPolicy.None.getSecurityPolicyUri();
        }
        try {
            URI.create(secPolURI);  // To verify wellformedness
            return SecurityPolicy.fromUri(secPolURI);
        } catch (IllegalArgumentException e) {
            String errMsg = ERROR_PREFIX + ".invalidSecurityPolicySyntax: " + secPolURI + " is not a syntactically correct URI";
            logger.error(errMsg);
            throw new OpcExtConfigException(errMsg, e);
        } catch (UaException e) {
            String errMsg = ERROR_PREFIX + ".invalidSecurityPolicy: " + secPolURI + " is not a valid security URI";
            logger.error(errMsg);
            throw new OpcExtConfigException(errMsg, e);
        }
    }

    private OpcUaClient createClient(Map<String, Object> config) throws OpcExtConfigException, Exception {

        SecurityPolicy securityPolicy = getSecurityPolicy(config);

        File securityDir = new File((String) config.get(CONFIG_STORAGE_DIRECTORY), SECURITY_DIRECTORY);
        if (!securityDir.exists() && !securityDir.mkdirs()) {
            throw new OpcExtConfigException(ERROR_PREFIX + ".invalidStorageDirectory: unable to create security dir: " + securityDir);
        }
        logger.info("security temp dir: {}", securityDir.getAbsolutePath());

        KeyStoreManager loader = new KeyStoreManager().load(securityDir);

        EndpointDescription[] endpoints;

        String discoveryEndpoint = (String) config.get(CONFIG_DISCOVERY_ENDPOINT);
        if (discoveryEndpoint == null) {
            String errorMsg = ERROR_PREFIX + ".noDiscoveryEndpoint: No discovery endpoint was provided in the configuration.";
            logger.error(errorMsg);
            throw new OpcExtConfigException(errorMsg);
        }
        try {
            endpoints = UaTcpStackClient
                    .getEndpoints(discoveryEndpoint)
                    .get();
        } catch (Throwable ex) {
            try {
                // try the explicit discovery endpoint as well
                String discoveryUrl = discoveryEndpoint + "/discovery";
                logger.info("Trying explicit discovery URL: {}", discoveryUrl);
                endpoints = UaTcpStackClient
                        .getEndpoints(discoveryUrl)
                        .get();
            } catch (ExecutionException e) {
                String errMsg = ERROR_PREFIX + ".discoveryError: Could not discovery OPC Endpoints.";
                logger.error(ERROR_PREFIX + ".discoveryError: Could not discover OPC Endpoints: {}", e.getClass().getName() + "::" + e.getMessage());
                throw new OpcExtConfigException(errMsg, e);
            }
        }

        EndpointDescription endpoint = Arrays.stream(endpoints)
                .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getSecurityPolicyUri()))
                .findFirst().orElseThrow(() -> new Exception("no desired endpoints returned"));

        logger.info("Using endpoint: {} [{}]", endpoint.getEndpointUrl(), securityPolicy);

        OpcUaClientConfig opcConfig = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
                .setApplicationUri("urn:eclipse:milo:examples:client")
                .setCertificate(loader.getClientCertificate())
                .setKeyPair(loader.getClientKeyPair())
                .setEndpoint(endpoint)
                .setIdentityProvider(new AnonymousProvider())   // FIXME
                .setRequestTimeout(uint(5000))
                .build();

        return new OpcUaClient(opcConfig);
    }

    /**
     * Write a value into OPCUA.
     * <p>
     * This method writes the value attribute of a node via the connected OPC US server. Only writes of non-indexed data
     * are supported (i.e.) one cannot write to some particular index in an array).
     * The information necessary to write this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param namespaceURN The identity of the OPC UA namespace into which the write is to occur
     * @param identifier   The node to be written
     * @param datatype     The datatype of the entity in question.
     * @param value        The value to be written.
     * @throws OpcExtRuntimeException wraps any errors returned by the server.  May also be thrown if the namespace is not found.
     */
    public void writeValue(String namespaceURN, String identifier, String datatype, Object value) throws OpcExtRuntimeException {

        // To perform the insert, we need to translate the namespace URN into an index.  We'll do that here.
        UShort nsIndex = client.getNamespaceTable().getIndex(namespaceURN);
        if (nsIndex == null) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".badNamespaceURN:  Namespace URN " + namespaceURN + " does not exist in the OPC server.");
        } else {
            writeValue(nsIndex, identifier, datatype, value);
        }
    }

    /**
     * Write a value into OPCUA.
     * <p>
     * This method writes the value attribute of a node via the connected OPC US server. Only writes of non-indexed data
     * are supported (i.e.) one cannot write to some particular index in an array).
     * The information necessary to write this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param nsIndex    The namespace index of the OPC UA namespace into which the write is to occur
     * @param identifier The node to be written
     * @param datatype   The datatype of the entity in question.
     * @param value      The value to be written.
     * @throws OpcExtRuntimeException wraps any errors returned by the server.
     */
    public void writeValue(UShort nsIndex, String identifier, String datatype, Object value) throws OpcExtRuntimeException {

        Variant v = new Variant(value);

        List<NodeId> nodesToWrite = ImmutableList.of(new NodeId(nsIndex, identifier));

        // don't write status or timestamps
        DataValue dv = new DataValue(v, null, null);

        StatusCode status = null;
        try {
            // write asynchronously....
            CompletableFuture<List<StatusCode>> syncFuture = client.writeValues(nodesToWrite, ImmutableList.of(dv));

            // ...but block for the results so we write in order
            List<StatusCode> statusCodes = syncFuture.get();
            status = statusCodes.get(0);
        } catch (Exception e) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".unexpectedException: OPC UA Error", e);
        }
        if (status != null && status.isGood()) {
            logger.debug("Wrote value '{}' to nodeId={}", v, nodesToWrite.get(0));
        } else {
            String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".writeError: OPC UA Error '{}' performing writeValues() for nodeId={}, value={}",
                    new Object[]{status, nodesToWrite.get(0), value}).getMessage();
            logger.error(errMsg);
            throw new OpcExtRuntimeException(errMsg);
        }
    }

    /**
     * Read a value from an OPC UA server.
     * <p>
     * This method reads the value attribute of a node via the connected OPC US server. Only reads of non-indexed data
     * are supported (i.e.) one cannot read  some particular index in an array).
     * The information necessary to read this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param namespaceURN The identity of the OPC UA namespace from which the read is to occur
     * @param identifier   The node to be read
     * @return the read value (as a Java Object).
     * @throws OpcExtRuntimeException wraps any errors returned by the server.  May also be thrown if the namespace is not found.
     */

    public Object readValue(String namespaceURN, String identifier) throws OpcExtRuntimeException {
        UShort nsIndex = client.getNamespaceTable().getIndex(namespaceURN);
        if (nsIndex == null) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".badNamespaceURN:  Namespace URN " + namespaceURN + " does not exist in the OPC server.");
        } else {
            return readValue(nsIndex, identifier);
        }
    }

    /**
     * Read a value from an OPC UA server.
     * <p>
     * This method reads the value attribute of a node via the connected OPC US server. Only reads of non-indexed data
     * are supported (i.e.) one cannot read  some particular index in an array).
     * The information necessary to read this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param nsIndex    The namespace index of the OPC UA namespace from which the read is to occur
     * @param identifier The node to be read
     * @return the read value (as a Java Object).
     * @throws OpcExtRuntimeException wraps any errors returned by the server.
     */
    public Object readValue(UShort nsIndex, String identifier) throws OpcExtRuntimeException {
        try {
            VariableNode theNode = client.getAddressSpace().getVariableNode(new NodeId(nsIndex, identifier)).get();
            return theNode.readValue().get().getValue().getValue();
        } catch (Exception e) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".unexpectedException: OPC UA Error", e);
        }
    }

    /**
     * Subscribe (if necessary) and monitor items specified in config.
     * <p>
     * The configuration will contain a set of items about which our user
     * is interested in hearing about data changes.  Each is specified by including a
     * OPC UA namespace URN or index, and an associated identifier.
     * <p>
     * When this method is called, the set of specified monitored items is reconciled with the
     * actual monitored items.  The result is that data value changes to those monitored items will
     * cause the calling of the provided handler.
     *
     * @param config  The configuration specifying the items to monitor.
     * @param handler The handler to call with data change updates
     * @throws OpcExtRuntimeException In case of errors, etc.
     */

    public void updateMonitoredItems(Map<String, Object> config, BiConsumer<String, Object> handler) throws OpcExtRuntimeException {
        try {
            boolean isNewSubscription = false;
            if (subscription == null) {
                // FIXME -- update interval should be gleaned from the config file
                subscription = client.getSubscriptionManager().createSubscription(1000.0).get();
                isNewSubscription = true;
            }

            Map<String, Map<String, String>> newMonitoredItems =
                    (Map<String, Map<String, String>>) ((Map<String, Object>)
                            config.get(CONFIG_OPC_UA_INFORMATION)).get(CONFIG_OPC_MONITORED_ITEMS);
            logger.debug("Config requesting {} monitored items", newMonitoredItems.size());

            if (currentMonitoredItemList != null && !currentMonitoredItemList.isEmpty()) {
                // First, if we're currently monitoring anything, remove them.
                // This can be improved to compare new vs old, but this overkill version is sufficient for now.
                client.deleteMonitoredItems(subscription.getSubscriptionId(), currentMonitoredItemList).get();
            }
            currentMonitoredItemList = new ArrayList<>();

            // First, create a list of MI requests from our config file.
            ArrayList<MonitoredItemCreateRequest> reqList = new ArrayList<MonitoredItemCreateRequest>();
            for (Map.Entry<String, Map<String, String>> ent : newMonitoredItems.entrySet()) {
                UShort nsIndex;
                NodeId nodeIdentifier = null;

                // Determine node we wish to monitor...

                // Nodes can be defined in a couple of major ways.  The first includes a nodeId field, which
                // combines (standard nomenclature in OPC UA) the namespace index & identifer: "ns=3;s=someNode/identifier";
                // If this nodeId field is present, we ignore other fields as it is a complete specification.
                // Otherwise, we'll look for the namespace specification (either ns for a numeric namespace index or
                // nsu for the namespace URN) and a node identifier.  The node identifier can be augmented with a type
                // (s, i, g, or b for String, Numeric (integer), GUID, or ByteString, respectively) to describe how
                // the identifier is encoded.  If this is missing, it is assumed to be a string.

                if (ent.getValue().containsKey(CONFIG_MI_NODEID)) {
                    String nodeIdSpec = ent.getValue().get(CONFIG_MI_NODEID);
                    nodeIdentifier = NodeId.parse(nodeIdSpec);
                } else {
                    if (ent.getValue().containsKey(CONFIG_MI_NAMESPACE_URN)) {
                        nsIndex = client.getNamespaceTable().getIndex(ent.getValue().get(CONFIG_MI_NAMESPACE_URN));
                        if (nsIndex == null) {
                            throw new OpcExtRuntimeException(ERROR_PREFIX + ".badNamespaceURN:  Namespace URN "
                                    + ent.getValue().get(CONFIG_MI_NAMESPACE_URN) + " does not exist in the OPC server.");
                        }
                    } else if (ent.getValue().containsKey(CONFIG_MI_NAMESPACE_INDEX)) { // FIXME -- should we disallow this form since it's not stable over reboots?  I think yes, but it is convenient
                        nsIndex = UShort.valueOf(ent.getValue().get(CONFIG_MI_NAMESPACE_INDEX));
                    } else {
                        String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".invalidMonitoredItem: Monitored Item {} has no namespace index: {}",
                                new Object[]{ent.getKey(), ent.getValue()}).getMessage();
                        throw new OpcExtRuntimeException(errMsg);
                    }
                    String nodeIdType = "s";
                    if (ent.getValue().containsKey(CONFIG_MI_IDENTIFIER_TYPE)) {
                        nodeIdType = ent.getValue().get(CONFIG_MI_IDENTIFIER_TYPE);
                    }
                    switch (nodeIdType) {
                        case "s":
                            nodeIdentifier = new NodeId(nsIndex, ent.getValue().get(CONFIG_MI_IDENTIFIER));
                            break;
                        case "i":
                            nodeIdentifier = new NodeId(nsIndex, Integer.valueOf(ent.getValue().get(CONFIG_MI_IDENTIFIER)));
                            break;
                        case "g":
                            nodeIdentifier = new NodeId(nsIndex, UUID.fromString(ent.getValue().get(CONFIG_MI_IDENTIFIER)));
                            break;
                        case "b":
                            nodeIdentifier = new NodeId(nsIndex, new ByteString(ent.getValue().get(CONFIG_MI_IDENTIFIER).getBytes()));
                            break;
                        default:
                            String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".invalidMonitoredItemIdType: Monitored Item {} has provided an invalid {} property: {}.  It must be either s, i, g, or b",
                                    new Object[]{ent.getKey(), CONFIG_MI_IDENTIFIER_TYPE, ent.getValue().get(CONFIG_MI_IDENTIFIER_TYPE)}).getMessage();
                            throw new OpcExtRuntimeException(errMsg);
                    }
                }
                ReadValueId readValueId = new ReadValueId(
                        nodeIdentifier,
                        AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

                // important: client handle must be unique per item
                UInteger localHandle = uint(clientToMILink.getAndIncrement());

                MonitoringParameters parms = new MonitoringParameters(
                        localHandle,
                        1000.0,     // sampling interval // FIXME -- We should allow this to be adjustable
                        null,       // filter, null means use default
                        uint(10),   // queue size
                        true        // discard oldest
                );

                MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                        readValueId, MonitoringMode.Reporting, parms);
                logger.debug("MonitoredItemCreateRequest for {} added to list", readValueId.toString());
                reqList.add(request);
            }

            BiConsumer<UaMonitoredItem, Integer> monitoringCreated =
                    (item, id) -> item.setValueConsumer(this::onDataChange);

            this.subscriptionHandler = handler;
            // Having created the list, add it to the subscription
            List<UaMonitoredItem> items = subscription.createMonitoredItems(
                    TimestampsToReturn.Both,
                    reqList,
                    monitoringCreated
            ).get();

            for (UaMonitoredItem item : items) {
                if (item.getStatusCode().isGood()) {
                    logger.debug("item created for nodeId={}", item.getReadValueId().getNodeId());
                    currentMonitoredItemList.add(item.getMonitoredItemId());
                } else {
                    logger.warn(
                            "failed to create item for nodeId={} (status={})",
                            item.getReadValueId().getNodeId(), item.getStatusCode());
                }
            }
        } catch (Exception e) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".unexpectedException", e);
        }
    }

    private void onDataChange(UaMonitoredItem item, DataValue value) {
        logger.debug(
                "Update event on subscription {} value received: item={}, value={}",
                subscription.getSubscriptionId(), item.getReadValueId().getNodeId().toParseableString(), value.getValue());
        subscriptionHandler.accept(item.getReadValueId().getNodeId().toParseableString(), value.getValue().getValue());
    }

    /**
     *
     * FIXME -- Need to determine if just replicating this is good idea or whether
     * We should be turning it into a query specified via browse.  That is, we'd return
     * values instead of this node-browse junk.
     *
     * return list of nodes obtained via browse() operations
     *
     * @param nodeList List of specifications for nodes. Each can be either a nodeId or a (namespace, identifier) pair.
     * @param depthLimit How far down the tree to go.  Bounded by overall depth limit setting in configuration.
     * @param direction Forward or backward (defaults to forward)
     * @param typeToFollow NodeId for ReferenceType to follow.  Defaults to Reference types.
     * @param includeSubtypes Boolean for whether to include subtypes of typeToFollow
     * @param resultTypes list of result types to return.  Defaults to Variable and Object nodes
     * @param resultFields list of fields to return.  Defaults to all.
     */

    /**
     * Return values specified by browsing.
     *
     * FIXME -- this has issues that you might get a variety of things back with no way to know what to expect.
     * FIXME -- to work within a SELECT-style framework, doing the pure browse version then asking for
     * FIXME -- data about specific nodes is probably preferable.  To some extent, we really need to see
     * FIXME -- if/how people use this.  Then, we can evaluate ER's to determine the correct path.
     *
     * FIXME -- Alternatively, provide the ability to specify in more detail what (single, possibly subtyped)
     * FIXME -- Variable nodes to return, as these will all have the same type.  This may be possible, but if we
     * FIXME -- want to enforce it, it may be tricky.  Alternatively, we just ask for a name, look it up.
     * FIXME -- If it exists, then we use it; otherwise, either throw an error OR return an empty list.
     *
     * Returns the value attributes from Variable nodes found by following references via the OPC UA Browse operations.
     * This method will
     *
     * @param nodeList List of specifications for nodes. Each can be either a nodeId or a (namespace, identifier) pair.
     * @param depthLimit How far down the tree to go.  Bounded by overall depth limit setting in configuration.
     *
     */

}
