package io.vantiq.extsrc.opcua.opcUaSource;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.opcua.uaOperations.OpcUaESClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    ExtensionWebSocketClient vantiqClient = null;
    OpcUaESClient opcClient = null;
    String sourceName = null;

    public void connectToOpc(Map config) {
        try {
            opcClient = new OpcUaESClient(config);
        }
        catch (Exception e) {
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
        localClient.setPublishHandler(UDPDefaultPublish);
        localClient.setQueryHandler(UDPDefaultQuery);
        localClient.setConfigHandler(UDPConfig);

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
        }
        catch (TimeoutException e) {
            log.error("Timeout: not all VANTIQ sources succeeded within 10 seconds.");
        }
        catch (Exception e) {
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
        /**
         * A handler for dealing with publishes for sources without a configured handler setup. Will log a warning noting
         * which source sent the Publish.
         */
        // Passes data to the UDP server or tells the program to stop
        static Handler<Map> UDPDefaultPublish = new Handler<Map>() {
            @Override
            public void handleMessage(Map message) {
                // Translate the data from the Publish message to what we want it to be
                log.warn("Vantiq requesting message sent from" +  message.get("resourceId").toString()
                        + ", but no handler is set up for it");
            }
        };

        /**
         *  Shuts down the server when a query is received. This is largely a debug decision, as a) queries are not expected
         *  for UDP sources, and b) problems occur when the WebSocket connection is violently shut down
         */
        static Handler<Map> UDPDefaultQuery = new Handler<Map>() {
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
        static Handler<Map> UDPConfig = new Handler<Map>() {
            @Override
            public void handleMessage(Map message) {
                String sourceName = (String) message.get("resourceId");
                Map srcConfig = (Map) ((Map) message.get("object")).get("config");
                log.info("Received configuration document for source {}: {}", sourceName, message.get("object"));
                if (!(srcConfig.get("extSrcConfig") instanceof Map)) {
                    log.error("Unable to obtain server configuration for '" + sourceName + "'. Source '" +
                            sourceName + "' is terminating.");
//                    clients.get(sourceName).close();
                    return;
                }
                Map config = (Map) srcConfig.get("extSrcConfig");
                log.trace("Creating handlers for '" + sourceName + "'");


                log.trace("Finished creating handlers for source '" + sourceName + "'");
            }
        };
}
