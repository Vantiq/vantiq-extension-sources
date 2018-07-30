package io.vantiq.extjsdk;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

// For decoding of the messages received
import com.fasterxml.jackson.databind.ObjectMapper;

// WebSocket imports
import okhttp3.*;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client that handles the WebSocket connection with a Vantiq deployment, for the purposes of Extension sources.
 */
public class ExtensionWebSocketClient {
    /**
     * An {@link ObjectMapper} used to transform objects into JSON before sending
     */
    private ObjectMapper mapper = new ObjectMapper();
    /**
     * The WebSocket used to talk to the Vantiq deployment. null when no connection is established
     */
    WebSocket webSocket = null;
    /**
     * The name of ths source this client is connected to.
     */
    private String sourceName;
    /**
     * An Slf4j logger
     */
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    /**
     * The listener that receives and interprets responses from the Vantiq deployment for this client's connection.
     */
    private ExtensionWebSocketListener listener;
    /**
     * A {@link CompletableFuture} that will return true when connected over a websocket, and false when the connection
     * is closed or has failed
     */
    CompletableFuture<Boolean> webSocketFuture;
    /**
     * A {@link CompletableFuture} that will return true when authenticated with Vantiq, and false when the WebSocket
     * connection is closed or has failed
     */
    CompletableFuture<Boolean> authFuture;
    /**
     * A {@link CompletableFuture} that will return true when connected to source {@code sourceName}, and false when the
     * WebSocket connection is closed or has failed
     */
    CompletableFuture<Boolean> sourceFuture;
    /**
     * Used to signal that an authentication message has been requested
     */
    CompletableFuture<Void> authRequested;
    /**
     * Used to signal that a source connection has been requested
     */
    CompletableFuture<Void> sourceRequested;
    /**
     * A {@link CompletableFuture CompletableFuture<Void>} that is completed when authorization succeeds.
     */
    CompletableFuture<Void> authSuccess;
    /**
     * True after {@link #close} has been called.
     */
    CompletableFuture<Boolean> closedFuture;
    /**
     * Whether it should automatically send a connection message after receiving a reconnect message
     */
    boolean autoReconnect = false;
    /**
     * The data to be used for authentication. This will be either a {@link String} containing an authentication token or
     * a {@link Map} containing the username and password.
     */
    Object authData;

    /**
     * Obtain the {@link ExtensionWebSocketListener} listening to this client's source on Vantiq. Necessary to set
     * the {@link Handler} for various events.
     *
     * @return The {@link ExtensionWebSocketListener} that is listening for messages from the Vantiq source.
     */
    public ExtensionWebSocketListener getListener() {
        return listener;
    }
    
    /**
     * Obtain the name of the source this client is assigned to.
     * 
     * @return  The name of the source this client is assigned to.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Creates an {@link ExtensionWebSocketClient} that will connect to the source {@code sourceName}.
     * @param sourceName    The name of the source to which this client will be connected
     */
    public ExtensionWebSocketClient (String sourceName) {
        this.sourceName = sourceName;
        listener = new ExtensionWebSocketListener(this);
        initializeFutures();
    }

    private void initializeFutures() {
        webSocketFuture = new CompletableFuture<>();
        authRequested = new CompletableFuture<>();
        sourceRequested = new CompletableFuture<>();
        authSuccess = new CompletableFuture<>();
        closedFuture = new CompletableFuture<>();


        // When authRequested is completed by calling authenticate(), it will check if the WebSocket connection succeeded,
        // waiting if necessary.
        // If the connection succeeded, then it will create a new Future that will be completed upon receiving an
        // authentication request. If the connection failed, then it will return a Future with the value false.
        authFuture = authRequested
                .thenCombineAsync(webSocketFuture, 
                        (unused, success) -> {
                            return success;
                        }
                ).thenComposeAsync(
                        (success) -> {
                            if (success != null && success) { // In case a user assigns null
                                doAuthentication();
                                return new CompletableFuture<>();
                            }
                            return CompletableFuture.completedFuture(false);
                        }
                );

        // If authentication succeeded, become a Future that will be set upon succeed or failure of a source connection
        // If authentication failed, complete as false in order to propagate the failure
        sourceFuture = authFuture.thenComposeAsync(
                        (success) -> {
                            if (success != null && success) { // In case a user assigns null
                                return new CompletableFuture<Boolean>();
                            }
                            return CompletableFuture.completedFuture(false);
                        }
                );
        
        // Try to connect to source once both it has been requested and authentication has succeeded
        sourceRequested.runAfterBoth(authSuccess, () -> doConnectionToSource());
    }

    /**
     * Creates a WebSocket connection to the given URL. Does nothing if a connection has already been established
     *
     * @param url   The url of the Vantiq system to which you wish to connect.
     *              Typically "wss://dev.vantiq.com/api/v1/wsock/websocket"
     * @return      A {@link CompletableFuture} that will return true when the connection succeeds, or false
     *              WebSocket fails to connect.
     */
    public CompletableFuture<Boolean> initiateWebsocketConnection(String url) {
        if (webSocket == null && !isClosed()) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS)
                    .build();
            WebSocketCall.create(client, new Request.Builder()
                    .url(validifyUrl(url))
                    .build()).enqueue(listener);
        }
        return getWebsocketConnectionFuture();
    }
    
    /**
     * Returns a {@link CompletableFuture} that will return true when the  succeeds, or false
     * when the connection fails.
     * 
     * @return      A {@link CompletableFuture} that will return true when the websocket connection succeeds, or false
     *              when the connection fails.
     */
    public CompletableFuture<Boolean> getWebsocketConnectionFuture() {
        return webSocketFuture;
    }
    
    /**
     * Ensures that the target address is correctly prepended by "wss://" and appended by 
     * "/api/v{version number}/wsock/websocket"
     * 
     * @param url   The url to be made valid
     * @return      A valid url for websocket connections
     */
    protected String validifyUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Must give a valid URL to connect to the websocket");
        }
        
        // Ensure prepended by wss:// and not http:// or https://
        if (url.startsWith("http://")) {
            url = url.substring("http://".length());
        }
        else if (url.startsWith("https://")) {
            url = url.substring("https://".length());
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            url = "wss://" + url;
        }
        
        // Ensure it ends with /api/v{version number}/wsock/websocket
        if (!url.matches(".*/api/v[0-9]+/wsock/websocket")) {
         // Sometimes generic urls end with a '/' already, so we only want to add one if it does not already exist
            if (!url.endsWith("/")) { 
                url = url + "/";
            }
            url = url + "api/v1/wsock/websocket";
        }
        
        return url;
    }

    /**
     * Sends a notification to the specified source if it is connected.
     *
     * @param data  The data to be sent to the source
     */
    // Fills in a notification message to sourceName with data
    // Requires this client to be connected to the source
    public void sendNotification(Object data) {
        if (isConnected()) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("op", ExtensionServiceMessage.OP_NOTIFICATION);
            m.put("resourceId", sourceName);
            m.put("resourceName", ExtensionServiceMessage.RESOURCE_NAME_SOURCES);
            m.put("object", data);
            ExtensionServiceMessage msg = new ExtensionServiceMessage("");
            msg.fromMap(m);
            this.send(msg);
        }
    }

    /**
     * Send the response to a specific query message.
     *
     * @param httpCode      The HTTP code to accompany the response. This should be one of: 100, there is data in this
     *                      message and more will be coming; 200, there is data and this is the last or only message; or
     *                      204, no (more) data needs to be returned
     * @param replyAddress  The address where the reply will go. This is a UUID that must be obtained from the original
     *                      query message
     * @param body          The data to be sent back as the result of the query
     */
    public void sendQueryResponse(int httpCode, String replyAddress, Map body){
        Response response = new Response()
                .status(httpCode)
                .addHeader(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, replyAddress)
                .body(body);
        send(response);
    }

    /**
     * Send the response to a specific query message.
     *
     * @param httpCode      The HTTP code to accompany the response. This should be one of: 100, there is data in this
     *                      message and more will be coming; 200, there is data and this is the last or only message; or
     *                      204, no (more) data needs to be returned
     * @param replyAddress  The address where the reply will go. This is a UUID that must be obtained from the original
     *                      query message
     * @param body          An array of the data to be sent back as the result of the query
     */
    public void sendQueryResponse(int httpCode, String replyAddress, Map[] body) {
        Response response = new Response()
                .status(httpCode)
                .addHeader(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, replyAddress)
                .body(body);
        send(response);
    }

    /**
     * Sends an error for a specific query message
     *
     * @param replyAddress The address where the reply will go. This is a UUID that must be obtained from the original
     *                          query message
     * @param messageCode A error code that might be used for message lookup or categorization. This is a string,
     *                          and it is generally specific to the server or class therein.
     * @param messageTemplate A string that is the (in this case) the error. Places where parameters should be
     *                          substituted are represented by {#}, where the # is replaced by the number of the
     *                          parameters array (see below), beginning at 0.
     * @param parameters An array of parameters for the messageTemplate.
     */
    public void sendQueryError(String replyAddress, String messageCode, String messageTemplate, Object[] parameters) {
        // Create the body
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("messageCode", messageCode);
        body.put("messageTemplate", messageTemplate);
        body.put("parameters", parameters);

        sendQueryResponse(400, replyAddress, body);
    }

    /**
     * Sends an Object to Vantiq. This is expected to be used only by ExtensionWebSocketClient, but it is open to the
     * public in case a custom message is desired.
     * <p>
     * It is preferred that this method is ignored unless truly necessary, and {@link #sendNotification},
     * {@link #authenticate}, {@link #connectToSource} or {@link #sendQueryResponse}/{@link #sendQueryError}
     * are used instead
     *
     * @param obj   The object you wish to send to the Vantiq server
     */
    public void send(Object obj) {
        if (isClosed()) {
            return;
        }
        log.trace("Sending message");
        try {
            byte[] bytes = mapper.writeValueAsBytes(obj);
            synchronized (this) {
                this.webSocket.sendMessage(RequestBody.create(WebSocket.BINARY, bytes));
            }
        }
        catch (Exception e) {
            log.warn("Error sending to WebSocket", e);
        }
    }

    /** 
     * Send the authentication message based on the auth data passed through {@link #authenticate}
     */
    protected void doAuthentication() {
        if (isClosed()) {
            return;
        }
        Map<String, Object> authMsg = new LinkedHashMap<>();
        // If this is username and password combo, use authenticate op
        if (authData instanceof Map) {
            authMsg.put("op", "authenticate");
        }
        // If this is a token use the validate op
        if (authData instanceof String) {
            authMsg.put("op", "validate");
        }
        authMsg.put("resourceName", "system.credentials");
        authMsg.put("object", authData);

        this.send(authMsg);
        log.trace("Authentication sent");
    }

    /**
     * Requests that an authentication message be sent to Vantiq using the given username and password. If the WebSocket
     * connection has not finished yet, the message will not be sent until the connection is finished.
     *
     * @param user  The username of a user capable of logging into your namespace
     * @param pass  The password of the supplies user
     * @return      A {@link CompletableFuture} that will return true when the authentication succeeds, or false
     *              when the WebSocket connection fails before authentication can occur.
     */
    public CompletableFuture<Boolean> authenticate(String user, String pass) {
        Map<String, String> authData = new LinkedHashMap<>();
        authData.put("username", user);
        authData.put("password", pass);
        this.authData = authData;
        // No need to complete it if it is already completed
        if (!authRequested.isDone()) {
            authRequested.complete(null);
        }
        // If this is a re-request and the client is connected but not authenticated through WebSocket.
        // This most likely means that an authentication has been sent and failed, in which case we should send again.
        else if (!isAuthed() && isOpen()) {
            // We could instead recreate authFuture, but this way anyone holding onto the original will still
            // receive the results
            doAuthentication();
        }
        return getAuthenticationFuture();
    }

    /**
     * Requests an authentication message to Vantiq be sent using the authentication token. If the WebSocket
     * connection has not finished yet, the message will not be sent until the connection is finished.
     *
     * @param token An authentication token capable of accessing the target namespace
     * @return      A {@link CompletableFuture} that will return true when the authentication succeeds, or false
     *              when the WebSocket connection fails before authentication can occur.
     */
    public CompletableFuture<Boolean> authenticate(String token) {
        authData = token;
        // No need to complete it if it is already completed
        if (!authRequested.isDone()) {
            authRequested.complete(null);
        }
        // If this is a re-request and the client is connected but not authenticated through WebSocket.
        // This most likely means that an authentication has been sent and failed, in which case we should send again.
        else if (!isAuthed() && isOpen()) {
            // We could instead recreate authFuture, but this way anyone holding onto the original will still
            // receive the results
            doAuthentication();
        }
        return getAuthenticationFuture();
    }
    
    /**
     * Returns a {@link CompletableFuture} that will return true when the authentication succeeds, or false
     * when authentication fails.
     * 
     * @return      A {@link CompletableFuture} that will return true when the authentication succeeds, or false
     *              when the WebSocket connection fails before authentication can occur.
     */
    public CompletableFuture<Boolean> getAuthenticationFuture() {
        return authFuture;
    }

    /**
     * Send the connection message
     */
    protected void doConnectionToSource() {
        if (isClosed()) {
            return;
        }
        ExtensionServiceMessage connectMessage = new ExtensionServiceMessage("");
        connectMessage.connectExtension(ExtensionServiceMessage.RESOURCE_NAME_SOURCES, sourceName, null);
        send(connectMessage);
        log.trace("Connect message sent to " + sourceName);
    }

    /**
     * Request that a connection message for the source. If the WebSocket connection has not authenticated yet, the
     * message will not be sent until the authentication has succeeded.
     *
     * @return  A {@link CompletableFuture} that will return true when a connection succeeds, or false when
     *          either the WebSocket connection or authentication fails before the source can connect.
     */
    // Send a connection request for the source
    // Note that this client MUST already be authenticated or else the message will be ignored
    public CompletableFuture<Boolean> connectToSource() {
        // No need to complete it if it is already completed
        if (!sourceRequested.isDone()) {
            sourceRequested.complete(null);
        }
        // If this is a re-request and the client is authenticated but not connected to.
        // This most likely means that a connection message has been sent and failed, in which case we should send again.
        else if (!isConnected() && isAuthed()) {
            // We could instead recreate sourceFuture, but this way anyone holding onto the original will still
            // receive the results
            doConnectionToSource();
        }
        return getSourceConnectionFuture();
    }
    
    /**
     * Returns a {@link CompletableFuture} that will return true when a connection succeeds, or false when
     * it fails.
     * 
     * @return  A {@link CompletableFuture} that will return true when a connection succeeds, or false when
     *          either the WebSocket connection or authentication fails before the source can connect.
     */
    public CompletableFuture<Boolean> getSourceConnectionFuture() {
        return sourceFuture;
    }
    
    /**
     * Signals that this is no longer connected to the source, and resets to before a source connection has been 
     * requested.
     */
    public void sourceHasDisconnected() {
        sourceFuture.obtrudeValue(false);
        
        // Reset the asynchronous system 
        sourceRequested = new CompletableFuture<Void>();
        sourceRequested.thenAccept((NULL) -> doConnectionToSource()); 
    }
    
    /**
     * Specify if the client should automatically reconnect to the source upon receipt of a Reconnect message. Initially
     * set to {@code false}.
     * 
     * @param value Should the client automatically reconnect
     */
    public void setAutoReconnect(boolean value) {
        autoReconnect = value;
    }

    /**
     * Check if the WebSocket connection is open
     *
     * @return  true if a WebSocket connection to the target address is open, false otherwise
     */
    public boolean isOpen() {
        return webSocketFuture.getNow(false);
    }

    /**
     * Check if authentication has succeeded
     *
     * @return  true if authentication has succeeded, false otherwise
     */
    public boolean isAuthed() {
        return authFuture.getNow(false);
    }

    /**
     * Check if the client is connected to its source
     *
     * @return              true if a Configuration message has been received from the source, false otherwise
     */
    public boolean isConnected() {
        return sourceFuture.getNow(false);
    }
    
    /**
     * Check if the client has been closed, and thus is no longer functional
     *  
     * @return  true if {@link close} has been called by either the user or the websocket, false otherwise
     */
    public boolean isClosed() {
        return closedFuture.getNow(false);
    }
    
    public CompletableFuture<Boolean> getClosedFuture() {
        return closedFuture;
    }

    /**
     * Orders the close of the websocket connection. Resets to pre-WebSocket connection state. Additionally, completes
     * all {@link CompletableFuture} obtained from the connection and authentication functions as false.
     */
    public void close() {
        closedFuture.complete(true);
        if (this.webSocket != null) {
            try {
                this.webSocket.close(1000, "Closed by client");
            } catch (Exception e) {
                if (!e.getMessage().equals("Socket closed")) {
                    log.warn("Websocket has already been closed");
                } else {
                    log.error("Error trying to close WebSocket", e);
                }
            }
        }
        synchronized (this) {
            webSocket = null;
            // Make sure anything still using these futures know that they are no longer valid
            webSocketFuture.obtrudeValue(false);
            authFuture.obtrudeValue(false);
            sourceFuture.obtrudeValue(false);
        }
        listener = null;
        log.info("Websocket closed for source " + sourceName);
    }

    /**
     * Sets the handlers to the same as {@code listener}. This function is intended to allow handlers to
     * maintain state even if the parent {@link ExtensionWebSocketClient} is closed due to websocket issues.
     * 
     * @param listener  The {@link ExtensionWebSocketListener} to copy the handlers from.
     */
    public void useHandlersFrom(ExtensionWebSocketListener listener) {
        this.listener.useHandlersFromListener(listener);
    }
    
    /**
     * Sets the handlers to the same as the listener of {@code client}. This function is intended to allow 
     * handlers to maintain state if the parent {@link ExtensionWebSocketClient} is closed due to websocket issues.
     * 
     * @param client    The {@link ExtensionWebSocketClient} to copy the handlers from.
     */
    public void useHandlersFrom(ExtensionWebSocketClient client) {
        this.listener.useHandlersFromListener(client);
    }

    /**
     * Set the {@link Handler} for any standard Http response that are received after authentication has completed
     * <br>
     * Upon initialization, a default Handler is created that will log the Http response received, and whether it is
     * likely a confirmation or an error.
     * <br>
     * The handler will receive a {@link Map} that represents the message. Note that the Vantiq system will
     * send acknowledgements of Notifications, Authentications, and Query responses that will have no body or headers
     * and msg.status.code() will equal 200
     *
     * @param httpHandler   {@link Handler} that deals with Http responses
     */
    public void setHttpHandler(Handler<Response> httpHandler) {
        this.listener.setHttpHandler(httpHandler);
    }
    /**
     * Set the {@link Handler} for any Publish messages that are received.
     * <br>
     * Upon initialization a default {@link Handler} is created that will log that a Publish was received, and its
     * contents.
     * <br>
     * The handler will receive a {@link Map} that represents the Publish message. The most
     * significant part will be msg.object which contains the data published to the source
     *
     * @param publishHandler    {@link Handler} that deals with any publishes from a source without its own publish
     *                          {@link Handler}
     */
    public void setPublishHandler(Handler<ExtensionServiceMessage> publishHandler) {
        this.listener.setPublishHandler(publishHandler);
    }
    /**
     * Set the {@link Handler} for any queries that are received.
     * <br>
     * Upon initialization a default Handler is created that will send back an error message saying
     * "Unset Handler: No handler has been set for source &lt;sourceName&gt;".
     * <br>
     * The handler will receive an {@link Map} that represents the Query message. The most
     * significant parts will be msg.getMessageHeaders().get("REPLY_ADDR_HEADER") which contains a String representing the
     * return address and must be sent as part of the response, and msg.object being a {@link Map} with query options
     * specified by "WITH" in the SELECT statement.
     *
     * @param queryHandler   {@link Handler} that deals with any queries from a source without its own query
     *                       {@link Handler}
     */
    public void setQueryHandler(Handler<ExtensionServiceMessage> queryHandler) {
        this.listener.setQueryHandler(queryHandler);
    }
    /**
     * Set the {@link Handler} for any Configuration messages that are returned.
     * <p>
     * Configuration messages are only expected immediately after connection to a source. If you intend to use the
     * config received, make sure to call this before {@link ExtensionWebSocketClient#connectToSource}
     * <p>
     * The handler will receive an {@link Map} that represents the Configuration message. The most
     * significant parts will be msg.resourceId which will contain the source's name, and msg.object.config that
     * will contain the source's config as a {@link Map}
     *
     * @param configHandler {@link Handler} that deals with any configurations from a source without its own
     *                      configuration {@link Handler}
     */
    public void setConfigHandler(Handler<ExtensionServiceMessage> configHandler) {
        this.listener.setConfigHandler(configHandler);
    }
    /**
     * Set the {@link Handler} for the result of any message received before a successful authentication attempt,
     * and the result of the authentication attempt.
     * <p>
     * The handler will receive a {@link Map} of the message received. If the authentication was successful,
     * then message.status.code() should equal 200. On success, the most significant part is msg.body['userInfo'] which
     * is a Map of various data about the user you logged in as. On failure, msg.body will be an Object containing
     * error messages.
     *
     * @param authHandler   {@link Handler} that deals with the results of authentication messages
     */
    public void setAuthHandler(Handler<Response> authHandler) {
        this.listener.setAuthHandler(authHandler);
    }
    /**
     * Set the {@link Handler} that will deal with any reconnect messages received. These will occur when an event 
     * happens on the Vantiq servers that requires the source to shut down. To restart the connection, just call 
     * {@link ExtensionWebSocketClient#connectToSource}, or have the client set to automatically reconnect with 
     * {@link ExtensionWebSocketClient#setAutoReconnect}.
     * <p>
     * The handler will receive a {@link Map} of the message received. 
     * 
     * @param reconnectHandler  {@link Handler} that deals with reconnect messages
     */
    public void setReconnectHandler(Handler<ExtensionServiceMessage> reconnectHandler) {
        this.listener.setReconnectHandler(reconnectHandler);
    }
}