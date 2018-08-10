package io.vantiq.extsrc.opcua.opcUaSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.opcua.uaOperations.OpcExtConfigException;
import io.vantiq.extsrc.opcua.uaOperations.OpcExtRuntimeException;
import io.vantiq.extsrc.opcua.uaOperations.OpcUaESClient;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class OpcUaSource {

    public static final String VANTIQ_URL = "vantiqUrl";
    public static final String VANTIQ_USERNAME = "username";
    public static final String VANTIQ_PASSWORD = "password";
    public static final String VANTIQ_TOKEN = "token";
    public static final String VANTIQ_SOURCENAME = "sourceName";
    public static final String OPC_VALUE_IN_VANTIQ = "dataValue";

    private static Map<String, Map> configurations = new ConcurrentHashMap<String, Map>();

    ExtensionWebSocketClient vantiqClient = null;
    OpcUaESClient opcClient = null;
    String sourceName = null;
    Map configurationDoc = null;
    ObjectMapper oMapper = new ObjectMapper();

    public void connectToOpc(Map config) {
        try {
            opcClient = new OpcUaESClient(config);
        } catch (Exception e) {
            log.error("Unable to connect to OPC server", e);
        }
    }

    public void close() {
        if (vantiqClient != null && vantiqClient.isOpen()) {
            vantiqClient.close();
            vantiqClient = null;
        }
    }

    public boolean connectToVantiq(String sourceName, Map<String, String> connectionInfo) {
        if (connectionInfo == null) {
            log.error("No VANTIQ connection information provided.");
            return false;
        }

        if (sourceName == null) {
            log.error("No source name provided.");
            return false;
        }

        String url = connectionInfo.get(VANTIQ_URL);
        String username = connectionInfo.get(VANTIQ_USERNAME);
        String password = connectionInfo.get(VANTIQ_PASSWORD);
        String token = connectionInfo.get(VANTIQ_TOKEN);
        boolean useToken = false;

        if (url == null) {
            log.error("No VANTIQ URL provided for source {}", sourceName);
        }

        if (token != null) {
            useToken = true;
        } else if (username == null || password == null) {
            log.error("No VANTIQ credentials provided source {}.", sourceName);
        }

        // If we get here, then we have sufficient information.  Let's see if we can get it to work...

        ExtensionWebSocketClient localClient = new ExtensionWebSocketClient(sourceName);

        // Set the handlers for the client
        localClient.setPublishHandler(publishHandler);
        localClient.setQueryHandler(queryHandler);
        localClient.setConfigHandler(configHandler);

        CompletableFuture<Boolean> connecter = localClient.initiateWebsocketConnection(url);
        try {
            connecter.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (!localClient.isOpen()) {
            log.error("Unable to connect to VANTIQ: {}  source {}", url, sourceName);
            return false;
        }

        CompletableFuture<Boolean> auther;
        if (useToken) {
            auther = localClient.authenticate(token);
        } else {
            auther = localClient.authenticate(username, password);
        }
        try {
            auther.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if (!localClient.isAuthed()) {
            log.error("Failed to authenticate our connection to VANTIQ: {} for source {}", url, sourceName);
            return false;
        }

        CompletableFuture<Boolean> sourcer = localClient.connectToSource();
        try {
            sourcer.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (!localClient.isConnected()) {
            log.error("Unable to connect to our VANTIQ source: Source: {} at VANTIQ: {}", sourceName, url);
            localClient.close();
            return false;
        }

        CompletableFuture<Boolean> connectionManager = CompletableFuture.completedFuture(true);

        // Initiate the WebSocket connection, authentication, and source connection for the source
        CompletableFuture<Boolean> future;
        future = localClient.connectToSource();

        // Add the result of the source connection to the chain
        connectionManager = connectionManager.thenCombine(future, (prevSucceeded, succeeded) -> prevSucceeded && succeeded);

        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = connectionManager.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout: not all VANTIQ sources succeeded within 10 seconds.");
        } catch (Exception e) {
            log.error("Exception occurred while waiting for VANTIQ source connection", e);
        }

        if (sourcesSucceeded) {
            // If we're here, we've succeeded.  Save the client and return
            log.info("Connection succeeded:  We're up for source: {}", sourceName);
            vantiqClient = localClient;
            this.sourceName = sourceName;
        } else {
            log.error("Failure to make connection to source {} at {}", sourceName, url);
        }

        if (sourcesSucceeded && localClient.isOpen()) {
            try {
                Thread.sleep(1000 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (localClient.isOpen()) {
            localClient.close();
        }

        return sourcesSucceeded;
    }

    public static Map getConfig(String qualifiedSourceName) {
        return configurations.get(qualifiedSourceName);
    }

    /**
     * A handler for dealing with publishes for sources without a configured handler setup. Will log a warning noting
     * which source sent the Publish.
     */
    // Passes data to the UDP server or tells the program to stop
    private Handler<ExtensionServiceMessage> publishHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {

            if (opcClient.isConnected()) {
                log.debug("Sending publish request to OPC");
                performPublish(message);
            } else {
                log.warn("OPC client not yet connected.  Publish dropped.");
                // FIXME
            }

        }
    };

    /**
     * Shuts down the server when a query is received. This is largely a debug decision, as a) queries are not expected
     * for UDP sources, and b) problems occur when the WebSocket connection is violently shut down
     */
    private Handler<ExtensionServiceMessage> queryHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage msg) {
            log.debug("Query handler:  Got query: {}", msg);
            String replyAddress = ExtensionServiceMessage.extractReplyAddress(msg);
            if (opcClient.isConnected()) {
                log.debug("Sending query request to OPC");
                performQuery(msg);

            } else {
                log.warn("OPC client not yet connected.  Query dropped.");
                vantiqClient.sendQueryError(replyAddress, this.getClass().getName() + ".opcNoConnection",
                        "OPC client not yet connected.  Query services are not available.", new Object[] {});
            }
        }
    };

    /**
     * Creates publish and notification handlers for any messages relating to a source based on the configuration
     * document
     */
    private Handler<ExtensionServiceMessage> configHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            String sourceName = message.getSourceName();
            configurationDoc = (Map) ((Map) message.getObject()).get("config");

            // FIXME -- Need to qualify source name (i.e. resourceId) with namespace name.
            // Save the config away so that we can refer to it in the future...
            configurations.put(sourceName, configurationDoc);
            log.info("Received configuration document for source {}: {}", sourceName, message.getObject());

            connectToOpc();
        }
    };

    /**
     * Create OPC-UA Session with server.
     *
     * We use the configuration document to determine the OPC-UA server to which to connect.
     * From this, we create our OpcUaESClient object, and use that from within this
     * source to handle other requests.
     */

    CompletableFuture<Void> connectToOpc() {
        Map cf = configurationDoc;

        if (opcClient != null && opcClient.isConnected()) {
            try {
                opcClient.disconnect();
            }
            catch (Exception e) {
                log.error("Error disconnecting from OPC", e);
            }
            finally {
                opcClient = null;
            }
        }

        try {
            opcClient = new OpcUaESClient(cf);
            opcClient.connectAsync().thenRunAsync(() ->
                    new Thread(() -> {
                        performMonitoring(configurationDoc);
                    }).start());
        }
        catch (OpcExtConfigException e) {
            log.error("Could not connect to opc error due to configuration error: {}", e.getMessage());
        }
        catch (Exception e) {
            log.error("Error connecting to OPC Server: {}", e, null);
        }
        return CompletableFuture.completedFuture(null); // Null is the Void...
    }

    /**
     * Extract monitoring requirements from the configuration document and set up
     * the monitoring
     *
     */

    void performMonitoring(Map config) {
        try {
            opcClient.updateMonitoredItems(config, this::handleMonitorUpdates);
        }
        catch (Exception e) {
            log.error("Error updating monitored items", e);
        }
    }

    private void handleMonitorUpdates(String nodeInfo, Object newValue) {
        try {
            log.debug(">>>> Update: Node: {}, newValue: {} ", nodeInfo, newValue.toString());
            Map<String, Object> updateMsg = new HashMap<>();

            updateMsg.put("nodeIdentification", nodeInfo);
            updateMsg.put("entity", newValue);
            vantiqClient.sendNotification(updateMsg);
        }
        catch (Throwable e) {
            log.error("Trapped unexpected error during event processing: ", e);
        }
    }

    /**
     * Decode the publish message and have our client perform the work.
     * We set this up to run asynchronously, so that we don't have to worry about
     * hogging the handlers...
     *
     * @param msg publish message initiating the workflow
     */
    void performPublish(ExtensionServiceMessage msg) {
        log.debug("performPublish -- given message: {}", msg);
        CompletableFuture.runAsync(() -> {
            Map pubMsg;
            Object maybeMap = msg.getObject();
            if (!(maybeMap instanceof Map)) {
                log.error("Publish Failed: Message format error -- 'object' was a {}, should be Map.  Overall message: {}",
                        maybeMap.getClass().getName(), msg);
            } else {
                pubMsg = (Map) msg.getObject();
                log.debug("Published msg[object] == {}", pubMsg);
                String intent = (String) pubMsg.get("intent");
                if ("subscribe".equalsIgnoreCase(intent) || "unsubscribe".equalsIgnoreCase(intent)) {
                    log.error("Publish.intent == {} is not yet implemented.", intent);
                } else if ("upsert".equalsIgnoreCase(intent)) {
                    String nsu = (String) pubMsg.get("nsu");
                    String nsIndex = (String) pubMsg.get("nsIndex");
                    String identifier = (String) pubMsg.get("identifier");
                    Object entity = pubMsg.get(OPC_VALUE_IN_VANTIQ);

                    checkNodeId(ExtensionServiceMessage.OP_PUBLISH, nsu, nsIndex, identifier);

                    try {
                        // Vantiq will send us a map here.  We'll need to convert our Map (representind JSON)
                        // into our underlying object format.
                        if (nsu == null) {
                            // Then we have a numeric index.  These are not permanent, but we'll
                            // hope our client knows what they are doing
                            opcClient.writeValue(UShort.valueOf(nsIndex), identifier, null, entity);
                        } else {
                            opcClient.writeValue(nsu, identifier, null, entity);
                        }
                        log.debug("Publish of {}:{} :: {} appears to have succeeded", (nsu != null ? nsu : nsIndex), identifier, entity);
                    } catch (OpcExtRuntimeException e) {
                        log.error("Unable to perform publish: {}", e.getMessage());
                    }
                } else {
                    log.error("Publish failed:  Unknown intent: {}", intent);
                }
            }
        });
    }

    /**
     * Decode the query request & have the OpcClient perform the work.
     *
     * @param msg query message containing the request
     */
    void performQuery(ExtensionServiceMessage msg) {
        log.debug("performQuery -- given message: {}", msg);
        CompletableFuture.runAsync(() -> {
            String replyAddress = ExtensionServiceMessage.extractReplyAddress(msg);
            Map qryMsg;
            Object maybeMap = msg.getObject();
            if (!(maybeMap instanceof Map)) {
                log.error("Query Failed: Message format error -- 'object' was a {}, should be Map.  Overall message: {}",
                        maybeMap.getClass().getName(), msg);
            } else {
                qryMsg = (Map) maybeMap;
                String style = (String) qryMsg.get("queryStyle");
                if ("browse".equalsIgnoreCase(style) || "query".equalsIgnoreCase(style)) {
                    log.error("Query.style == {} is not yet implemented.", style);
                }
                else if ("NodeId".equalsIgnoreCase(style)) {
                    try {
                        // For a NodeId query, we simply extract the node id & call the appropriate
                        // opcClient.readValue() method.  Then, create the response message and return
                        // it to the server.  Since readValue() will return a single node (by definition),
                        // we don't have to worry about chunking issues here.

                        String nsu = (String) qryMsg.get("nsu");
                        String nsIndex = (String) qryMsg.get("nsIndex");
                        String identifier = (String) qryMsg.get("identifier");
                        String identifierType = (String) qryMsg.get("identifierType");

                        if (identifierType == null) {
                            identifierType = "s";   // String is our default
                        }

                        checkNodeId(ExtensionServiceMessage.OP_QUERY, nsu, nsIndex, identifier);

                        Object result;
                        if (nsIndex == null) {
                            result = opcClient.readValue(nsu, identifier, identifierType);
                        } else {
                            result = opcClient.readValue(nsIndex, identifier, identifierType);
                        }
                        log.debug("Query Result: " + result);

                        if (result == null) {
                            vantiqClient.sendQueryResponse(HttpURLConnection.HTTP_NO_CONTENT, replyAddress, (Map) null);
                        } else {
                            Map<String, Object> objectMap = null;
                            try {
                                objectMap = oMapper.convertValue(result, Map.class);
                            }
                            catch (IllegalArgumentException e) {
                                // In this case, our entity is a native type.  We'll just include the value directly.
                            }
                            Map<String, Object> mapToSend = new HashMap<>();
                            if (objectMap == null) {
                                mapToSend.put(OPC_VALUE_IN_VANTIQ, result);
                            } else {
                                mapToSend.put(OPC_VALUE_IN_VANTIQ, objectMap);
                            }
                            log.debug("Sending result object: " + mapToSend);
                            vantiqClient.sendQueryResponse(HttpURLConnection.HTTP_OK, replyAddress, mapToSend);
                        }
                    }
                    catch (OpcExtRuntimeException e) {
                        log.error("Failed to perform query by nodeId: " + e.getMessage());
                        Object[] parms = new Object[] {e.getMessage()};
                        vantiqClient.sendQueryError(replyAddress, this.getClass().getName() + ".opcQueryFailure",
                                "OPC read by node id failed: {0}", parms);
                    }
                    catch (IllegalArgumentException e) {
                        log.error("Failed to perform query by nodeId: " + e.getMessage());
                        Object[] parms = new Object[] {e.getMessage()};
                        vantiqClient.sendQueryError(replyAddress, this.getClass().getName() + ".opcQueryFailure",
                                "OPC read by node id failed: {0}", parms);
                    }
                } else {
                    log.error("Query failed: Unknown queryStyle: ", style);
                }
            }
        });
    }

    private static void checkNodeId(String operation, String nsu, String nsIndex, String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException(operation + " operation failed:  no node identifier specified.");

        } else if (nsu == null && nsIndex == null) {
            throw new IllegalArgumentException(operation + " operation failed:  no namespace provided.");
        }

    }
}
