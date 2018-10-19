
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

// For decoding of the messages received
import com.fasterxml.jackson.databind.ObjectMapper;

// WebSocket imports
import okhttp3.*;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;

import java.util.HashMap;
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
     * The code for a query response where more data will be sent.
     */
    public static final int QUERY_CHUNK_CODE    = 100;
    /**
     * The code for a query response where data is sent and no more is coming.
     */
    public static final int QUERY_DATA_CODE     = 200;
    /**
     * The code for a query response where no data is sent and no more is coming.
     */
    public static final int QUERY_NODATA_CODE   = 204;
    
    /**
     * An {@link ObjectMapper} used to transform objects into JSON before sending
     */
    private ObjectMapper mapper = new ObjectMapper();
    /**
     * The WebSocket used to talk to the Vantiq deployment. null when no connection is established
     */
    WebSocket webSocket = null;
    /**
     * The name of the source this client is connected to.
     */
    private String sourceName;
    /**
     * An Slf4j logger
     */
    private final Logger log;
    /**
     * The listener that receives and interprets responses from the Vantiq deployment for this client's connection.
     */
    ExtensionWebSocketListener listener;
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
     * Whether it should automatically send a connection message after receiving a reconnect message
     */
    boolean autoReconnect = false;
    /**
     * The data to be used for authentication. This will be either a {@link String} containing an authentication token or
     * a {@link Map} containing the username and password.
     */
    Object authData;
    /**
     * An {@link Handler} that is called when the websocket connection is closed
     */
    Handler<ExtensionWebSocketClient> closeHandler;

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
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
        listener = new ExtensionWebSocketListener(this);
    }

    /**
     * Attempts to connect to the source using the given target url and authentication token. If the connection fails, 
     * {@link #isOpen}, {@link #isAuthed}, and {@link #isConnected} can be used to identify if the connection failed
     * or succeeded at the WebSocket, authentication attempt, and source, respectively. This function may be called
     * again regardless of where the failure occurred.
     *
     * @param url   The url of the target Vantiq server.
     * @param token The authentication token for the target namespace.
     * @return      An {@link CompletableFuture} that completes as {@code true} when the connection to the source is
     *              fully completed, or {@code false} when the connection fails at any point along the way.
     */
    public CompletableFuture<Boolean> initiateFullConnection(String url, String token) {
        initiateWebsocketConnection(url);
        authenticate(token);
        return connectToSource();
    }

    /**
     * Creates a WebSocket connection to the given URL. Does nothing if a connection has already been established
     *
     * @param url   The url of the Vantiq system to which you wish to connect.
     *              Typically "wss://dev.vantiq.com/api/v1/wsock/websocket"
     * @return      A {@link CompletableFuture} that will return true when the connection succeeds, or false
     *              WebSocket fails to connect.
     */
    synchronized public CompletableFuture<Boolean> initiateWebsocketConnection(String url) {
        // Only create the webSocketFuture if the websocket connection has completed or it has failed
        if (webSocket == null || !webSocketFuture.getNow(true)) {
            webSocketFuture = new CompletableFuture<>();

            // Start the connection attempt
            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS)
                    .build();
            WebSocketCall.create(client, new Request.Builder()
                    .url(validifyUrl(url))
                    .build()).enqueue(listener);
        }
        return webSocketFuture;
    }
    
    /**
     * Returns a {@link CompletableFuture} that will return true when the websocket connection succeeds, or false
     * when the connection fails. Returns {@code null} if {@link #initiateWebsocketConnection} has not been called yet
     * 
     * @return      A {@link CompletableFuture} that will return true when the websocket connection succeeds, or false
     *              when the connection fails. {@code null} if {@link #initiateWebsocketConnection} has not been called yet
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

        boolean usesSSL = true;
        // Ensure prepended by wss:// and not http:// or https://
        if (url.startsWith("http://")) {
            url = url.substring("http://".length());
            usesSSL = false;
        }
        else if (url.startsWith("https://")) {
            url = url.substring("https://".length());
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            String prefix = "wss://";
            if (!usesSSL) {
                prefix = "ws://";
            }
            url = prefix + url;
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
     * Send the response to a specific query message stating that the query returned no data.
     *
     * @param replyAddress  The address where the reply will go. This is a UUID that must be obtained from the original
     *                      query message through {@link ExtensionServiceMessage#extractReplyAddress(Object)}
     */
    public void sendQueryResponseEmpty(String replyAddress) {
        Response response = new Response()
                .status(QUERY_NODATA_CODE)
                .addHeader(ExtensionServiceMessage.RESPONSE_ADDRESS_HEADER, replyAddress);
        send(response);
    }
    
    /**
     * Send a single Map as the response to a specific query message.
     *
     * @param httpCode      The HTTP code to accompany the response. This should be one of: {@link #QUERY_CHUNK_CODE}
     *                      , there is data in this message and more will be coming; {@link #QUERY_DATA_CODE}, there is
     *                      data and this is the last or only message
     * @param replyAddress  The address where the reply will go. This is a UUID that must be obtained from the original
     *                      query message through {@link ExtensionServiceMessage#extractReplyAddress(Object)}
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
     * Send multiple Maps as the response to a specific query message.
     *
     * @param httpCode      The HTTP code to accompany the response. This should be one of: {@link #QUERY_CHUNK_CODE}
     *                      , there is data in this message and more will be coming; {@link #QUERY_DATA_CODE}, there is
     *                      data and this is the last or only message
     * @param replyAddress  The address where the reply will go. This is a UUID that must be obtained from the original
     *                      query message through {@link ExtensionServiceMessage#extractReplyAddress(Object)}
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
     * @param replyAddress  The address where the reply will go. This is a UUID that must be obtained from the original
     *                      query message through {@link ExtensionServiceMessage#extractReplyAddress(Object)}
     * @param messageCode A error code that might be used for message lookup or categorization. This is a string,
     *                          and it is generally specific to the server or class therein.
     * @param messageTemplate A string that is the (in this case) the error. Places where parameters should be
     *                          substituted are represented by {#}, where the # is replaced by the number of the
     *                          parameters array (see below), beginning at 0.
     * @param parameters An array of parameters for the messageTemplate.
     */
    public void sendQueryError(String replyAddress, String messageCode, String messageTemplate, Object[] parameters) {
        // Create the body
        Map<String, Object> err = new HashMap<>();
        err.put("messageCode", messageCode);
        err.put("messageTemplate", messageTemplate);
        err.put("parameters", parameters);

        sendQueryResponse(400, replyAddress, err);
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
        if (!isOpen()) {
            return;
        }
        log.trace("Sending message");
        try {
            byte[] bytes = mapper.writeValueAsBytes(obj);
            synchronized (this) {
                if (webSocket != null) {
                    this.webSocket.sendMessage(RequestBody.create(WebSocket.BINARY, bytes));
                }
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
     * connection has not finished yet, the message will not be sent until the connection is finished. Be aware that 
     * this can connect you to <b>any</b> of the namespaces the credentials have access to, typically the one the user
     * last logged into.
     *
     * @param user  The username of a user capable of logging into your namespace
     * @param pass  The password of the supplies user
     * @return      A {@link CompletableFuture} that will return true when the authentication succeeds, or false
     *              when the WebSocket connection fails before authentication can occur.
     */
    synchronized public CompletableFuture<Boolean> authenticate(String user, String pass) {
        Map<String, String> authData = new LinkedHashMap<>();
        authData.put("username", user);
        authData.put("password", pass);
        this.authData = authData;

        // Only create the authFuture if there has been no request or a failed request, and a websocket request has 
        // been made
        if (webSocketFuture != null && (authFuture == null || !authFuture.getNow(true))) {
            // Builds a Future that sends an authentication message and waits for the result if the websocket
            // connection succeeded, or is immediately false if the websocket connection failed
            authFuture = webSocketFuture.thenComposeAsync(
                    (wsSuccess) ->
                        {
                            if (wsSuccess) {
                                doAuthentication();
                                return new CompletableFuture<Boolean>();
                            } else {
                                return CompletableFuture.completedFuture(false);
                            }
                        }
                    );
        }
        return authFuture;
    }

    /**
     * Requests an authentication message to Vantiq be sent using the authentication token. If the WebSocket
     * connection has not finished yet, the message will not be sent until the connection is finished.
     *
     * @param token An authentication token capable of accessing the target namespace
     * @return      A {@link CompletableFuture} that will return true when the authentication succeeds, or false
     *              when the WebSocket connection fails before authentication can occur.
     */
    synchronized public CompletableFuture<Boolean> authenticate(String token) {
        authData = token;
        // Only create the authFuture if there has been no request or a failed request, and a websocket request has 
        // been made
        if (webSocketFuture != null && (authFuture == null || !authFuture.getNow(true))) {
            // Builds a Future that sends an authentication message and waits for the result if the websocket
            // connection succeeded, or is immediately false if the websocket connection failed
            authFuture = webSocketFuture.thenComposeAsync(
                    (wsSuccess) ->
                        {
                            if (wsSuccess) {
                                doAuthentication();
                                return new CompletableFuture<Boolean>();
                            } else {
                                return CompletableFuture.completedFuture(false);
                            }
                        }
                    );
        }
        return authFuture;
    }
    
    /**
     * Returns a {@link CompletableFuture} that will return true when the authentication succeeds, or false
     * when authentication fails. Returns {@code null} if {@link #authenticate} has not been called yet
     * 
     * @return      A {@link CompletableFuture} that will return true when the authentication succeeds, or false
     *              when the WebSocket connection fails before authentication can occur. Returns {@code null} if
     *              {@link #authenticate} has not been called yet
     */
    public CompletableFuture<Boolean> getAuthenticationFuture() {
        return authFuture;
    }

    /**
     * Send the connection message
     */
    protected void doConnectionToSource() {
        ExtensionServiceMessage connectMessage = new ExtensionServiceMessage("");
        connectMessage.connectExtension(ExtensionServiceMessage.RESOURCE_NAME_SOURCES, sourceName, null);
        send(connectMessage);
        log.trace("Connect message sent.");
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
    synchronized public CompletableFuture<Boolean> connectToSource() {
        // Only create the authFuture if there has been no request or a failed request, and a websocket request has 
        // been made
        if (authFuture != null && (sourceFuture == null || !sourceFuture.getNow(true))) {
            // Builds a Future that sends a connection message and waits for the result if authentication succeeded,
            // or is immediately false if authentication (or by extension the websocket connection) failed
            sourceFuture = authFuture.thenComposeAsync(
                    (authSuccess) ->
                        {
                            if (authSuccess) {
                                doConnectionToSource();
                                return new CompletableFuture<Boolean>();
                            } else {
                                return CompletableFuture.completedFuture(false);
                            }
                        }
                    );
        }
        return sourceFuture;
    }
    
    /**
     * Returns a {@link CompletableFuture} that will return true when a connection succeeds, or false when
     * it fails. Returns {@code null} if {@link #connectToSource} has not been called yet.
     * 
     * @return  A {@link CompletableFuture} that will return true when a connection succeeds, or false when
     *          either the WebSocket connection or authentication fails before the source can connect.
     *          Returns {@code null} if {@link #connectToSource} has not been called yet.
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
        if (webSocketFuture != null) {
            return webSocketFuture.getNow(false);
        } else {
            return false;
        }
    }

    /**
     * Check if authentication has succeeded
     *
     * @return  true if authentication has succeeded, false otherwise
     */
    public boolean isAuthed() {
        if (authFuture != null) {
            return authFuture.getNow(false);
        } else {
            return false;
        }
    }

    /**
     * Check if the client is connected to its source
     *
     * @return              true if a Configuration message has been received from the source, false otherwise
     */
    public boolean isConnected() {
        if (sourceFuture != null) {
            return sourceFuture.getNow(false);
        } else {
            return false;
        }
    }

    /**
     * Orders the close of the websocket connection, resets to pre-WebSocket connection state and calls the close 
     * handler. Additionally, completes all {@link CompletableFuture} obtained from the connection and authentication 
     * functions as false.
     */
    public void close() {
        this.stop();

        ExtensionWebSocketListener oldListener = listener;
        listener = new ExtensionWebSocketListener(this);
        listener.useHandlersFromListener(oldListener);

        if (this.closeHandler != null) {
            this.closeHandler.handleMessage(this);
        }
    }
    
    /**
     * Orders the close of the websocket connection with the expectation that it will not reopen. Additionally,
     * completes all {@link CompletableFuture} obtained from the connection and authentication functions as false.
     */
    public void stop() {
        // Saving and nulling before closing so EWSListener can know when it is closed by the client 
        WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            try {
                socket.close(1000, "Closed by client");
            } catch (Exception e) {
                if (!e.getMessage().equals("Socket closed")) {
                    log.warn("Websocket has already been closed");
                } else {
                    log.error("Error trying to close WebSocket", e);
                }
            }
        }
        synchronized (this) {
            // Make sure anything still using these futures know that they are no longer valid
            if (webSocketFuture != null) {
                webSocketFuture.obtrudeValue(false);
                webSocketFuture = null;
            }
            if (authFuture != null) {
                authFuture.obtrudeValue(false);
                authFuture = null;
            }
            if (sourceFuture != null) {
                sourceFuture.obtrudeValue(false);
                sourceFuture = null;
            }
            
            listener.close();
        }
        log.info("Websocket closed.");
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
     * Set the {@link Handler} for when the websocket connection closes. This handler will receive the client whose
     * websocket closed.
     * 
     * @param closeHandler
     */
    public void setCloseHandler(Handler<ExtensionWebSocketClient> closeHandler) {
        this.closeHandler = closeHandler;
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
     * If no Handler is set then Vantiq will receive an errore for any Queries, which will say
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