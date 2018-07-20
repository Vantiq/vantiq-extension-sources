package io.vantiq.extsrc.opcua.opcUaSource;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.opcua.uaOperations.OpcExtConfigException;
import io.vantiq.extsrc.opcua.uaOperations.OpcExtRuntimeException;
import io.vantiq.extsrc.opcua.uaOperations.OpcUaESClient;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

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

    private static Map<String, Map> configurations = new ConcurrentHashMap<String, Map>();

    ExtensionWebSocketClient vantiqClient = null;
    OpcUaESClient opcClient = null;
    String sourceName = null;
    Map configurationDoc = null;

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
                Thread.sleep(100 * 1000);
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
    private Handler<Map> publishHandler = new Handler<Map>() {
        @Override
        public void handleMessage(Map message) {

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
    private Handler<Map> queryHandler = new Handler<Map>() {
        @Override
        public void handleMessage(Map msg) {
            String RETURN_HEADER = "REPLY_ADDR_HEADER";  // header containing the return address for a query
            String srcName = (String) msg.get("resourceId");
//                // Prepare a response with an empty body, so that the query doesn't wait for a timeout
//                clients.get(srcName).sendQueryResponse(200,
//                        (String) ((Map) msg.get("messageHeaders")).get(RETURN_HEADER),
//                        new LinkedHashMap<>());
//
//                // Allow the system to stop
//                stopLatch.countDown();
        }
    };

    /**
     * Creates publish and notification handlers for any messages relating to a source based on the configuration
     * document
     */
    private Handler<Map> configHandler = new Handler<Map>() {
        @Override
        public void handleMessage(Map message) {
            String sourceName = (String) message.get("resourceId");
            configurationDoc = (Map) ((Map) message.get("object")).get("config");

            // FIXME -- Need to qualify source name (i.e. resourceId) with namespace name.
            // Save the config away so that we can refer to it in the future...
            configurations.put(sourceName, configurationDoc);
            log.info("Received configuration document for source {}: {}", sourceName, message.get("object"));

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

        try {
            opcClient = new OpcUaESClient(cf);
            return opcClient.connectAsync();
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
     * Decode the publish message and have our client perform the work.
     * We set this up to run asynchronously, so that we don't have to worry about
     * hogging the handlers...
     *
     * @param msg publish message initiating the workflow
     */
    void performPublish(Map msg) {
        log.debug("performPublish -- given message: {}", msg);
        CompletableFuture.runAsync(() -> {
                    Map pubMsg = (Map) msg.get("object");
                    log.debug("Published msg[object] == {}", pubMsg);
                    String intent = (String) pubMsg.get("intent");
                    if ("subscribe".equalsIgnoreCase(intent)) {
                        log.error("Publish.intent == subscribe is not yet implemented.");
                    } else if ("unsubscribe".equalsIgnoreCase(intent)) {
                        log.error("Publish.intent == unsubscribe is not yet implemented.");
                    } else if ("upsert".equalsIgnoreCase(intent)) {
                        String nsu = (String) pubMsg.get("nsu");
                        String nsIndex = (String) pubMsg.get("nsIndex");
                        String identifier = (String) pubMsg.get("identifier");
                        Object entity = pubMsg.get("dataValue");

                        if (identifier == null) {
                            log.error("Publish failed:  no node identifier specified.");
                        } else if (nsu == null && nsIndex == null) {
                            log.error("Publish failed:  no namespace provided.");
                        }
                        try {
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
        );
    }
}
