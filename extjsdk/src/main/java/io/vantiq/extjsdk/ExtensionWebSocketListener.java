package io.vantiq.extjsdk;

// Author: Alex Blumer
// Email: alex.j.blumer@gmail.com

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ResponseBody;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketListener;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;

/**
 * A listener that deals with messages received from a Vantiq deployment for Extension sources. It uses {@link Handler}
 * to allow users to specify how different types of messages are dealt with.
 */
public class ExtensionWebSocketListener implements WebSocketListener{
    // Each Handler effectively says what to do when receiving a message of its message type, or a response to its
    // message type in the case of authenticationHandler
    /**
     * {@link Handler} that handles Http responses received by this listener after a successful authorization. This
     * should consist purely of confirmations (messages with status 200 and an empty body) and error messages. Set by
     * {@link #setHttpHandler}
     */
    private Handler<Response> httpHandler = null;
    /**
     * {@link Handler} that handles Publish requests received by this listener. Set by {@link #setPublishHandler}
     */
    private Handler<ExtensionServiceMessage> publishHandler = null;
    /**
     * {@link Handler} that handles Query requests received by this listener. Set by {@link #setQueryHandler}
     */
    private Handler<ExtensionServiceMessage> queryHandler = null;
    /**
     * {@link Handler} that handles Configuration messages received by this listener. Configuration messages are sent
     * in response to connection messages, so this should be sent before sending the connection message to a source. Set
     * by {@link #setConfigHandler}
     */
    private Handler<ExtensionServiceMessage> configHandler = null;
    /**
     * {@link Handler} that handles responses to auth messages, both successful and not. Strictly speaking, it handles
     * all Http responses received by this listener before and upon successful authentication, as no other
     * Http responses are expected until after authorization. Set by {@link #setAuthHandler}
     */
    private Handler<Response> authHandler = null;
    /**
     * {@link Handler} that handles reconnect messages. Set by {@link #setReconnectHandler}
     */
    private Handler<ExtensionServiceMessage> reconnectHandler = null;
    /**
     * An Slf4j logger
     */
    final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * The {@link ExtensionWebSocketClient} that this listener is used by.
     */
    private ExtensionWebSocketClient client;

    /**
     * {@link ObjectMapper} used to translate the received message into a {@link Map}
     */
    ObjectMapper mapper = new ObjectMapper();
    /**
     * Whether this listener has been closed, and should not make any more changes to its client.
     */
    boolean isClosed = false;

    /**
     * Creates a new {@link ExtensionWebSocketListener} connected to {@code client}
     *
     * @param client    The {@link ExtensionWebSocketClient} that will use this listener for its WebSocket connection
     */
    public ExtensionWebSocketListener(ExtensionWebSocketClient client) {
        this.client = client;
        initializeDefaultHandlers();
    }


    /**
     * Set the default {@link Handler} for each response type. What each does is specified in its respective setter.
     */
    private void initializeDefaultHandlers() {

        // Respond to all queries with an empty body
        Handler<ExtensionServiceMessage> defaultQueryHandler = new Handler<ExtensionServiceMessage>() {
            @Override
            public void handleMessage(ExtensionServiceMessage msg) {
                log.warn("Query received with no user-set handler");
                log.debug("Full message: " + msg);
                // Prepare a response with an empty body, so that the query doesn't wait for a timeout
                Object[] body = {msg.getSourceName()};
                client.sendQueryError(ExtensionServiceMessage.extractReplyAddress(msg),
                        "io.vantiq.extjsdk.unsetQueryHandler",
                        "No handler has been set for source {0}",
                        body);
            }
        };
        this.setQueryHandler(defaultQueryHandler);

        // Logs both failed and successful authentications
        this.setAuthHandler(
            new Handler<Response>() {
                @Override
                public void handleMessage(Response msg) {
                    // Status code 200 signals the authentication was a success
                    if ((int) msg.getStatus() == 200) {
                        log.info("Auth Successful");
                        log.debug("Response: " + msg);
                    }
                    else {
                        log.warn("Auth Failed. Response: " + msg);
                    }
                }
            }
        );
    }

    /**
     * Set the {@link Handler} for any standard Http response that are received after authentication has completed
     * <br>
     * Upon initialization, a default Handler is created that will log the Http response received, and whether it is
     * likely a confirmation or an error.
     * <br>
     * The handler will receive a {@link Map} that represents the message. Note that the Vantiq system will
     * send acknowledgements of Notification messages and Configuration messages that will have no body or headers
     * and msg.status.code() will equal 200
     *
     * @param httpHandler   {@link Handler} that deals with Http responses
     */
    public void setHttpHandler(Handler<Response> httpHandler) {
        this.httpHandler = httpHandler;
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
        this.publishHandler = publishHandler;
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
        this.queryHandler = queryHandler;
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
        this.configHandler = configHandler;
    }
    /**
     * Set the {@link Handler} for the result of any message received before a successful authentication attempt,
     * and the result of the authentication attempt.
     * <p>
     * The handler will receive a {@link Map} of the message received. If the authentication was successful,
     * then message.status should equal 200. On success, the most significant part is msg.body['userInfo'] which
     * is a Map of various data about the user you logged in as. On failure, msg.body will be an Object containing
     * error messages.
     *
     * @param authHandler   {@link Handler} that deals with the results of authentication messages
     */
    public void setAuthHandler(Handler<Response> authHandler) {
        this.authHandler = authHandler;
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
        this.reconnectHandler = reconnectHandler;
    }
    
    /**
     * Called when its {@link ExtensionWebSocketClient} is closed. Stops this listener from dealing with any future 
     * auth, config, Query, or reconnect messages. It keeps its handlers and will finish any Publish messages in 
     * progress.
     */
    public void close() {
        isClosed = true;
    }
    
    /**
     * Checks to see if {@link #close} had been called. 
     * 
     * @return
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Log that the connection is open, save the WebSocket for {@link #client} and signal the successful opening
     *
     * @param webSocket The {@link WebSocket} that opened this listener.
     * @param response  The {@link okhttp3.Response} associated with the opening of the connection. Currently not used.
     */
    @Override
    public void onOpen(WebSocket webSocket, okhttp3.Response response) {
        this.client.webSocket = webSocket;
        this.client.webSocketFuture.complete(true);
        log.info("WebSocket open");
    }

    
    /**
     * Translate the received message and pass it on to the related handler. Additionally, updates the client about
     * successful authentications and source connections.
     *
     * @param body  The {@link ResponseBody} containing the message received.
     */
    @Override
    public void onMessage(ResponseBody body) {
        // Extract the original message from the body
        byte[] data;
        try {
            if (body.contentType() == WebSocket.TEXT) {
                data = body.string().getBytes();
            } else {
                data = body.bytes();
            }
        }
        catch (IOException e) {
            log.error("Error trying to interpret WebSocket message", e);
            return;
        }
        body.close();


        // Convert the data from a Json string/byte array to a map
        Map msg;
        try {
            msg = mapper.readValue(data, Map.class);
        }
        catch (Exception e) {
            log.warn("Failed to interpret WebSocket message as Map.", e);
            return;
        }
        log.debug("Map of the received message: " + msg);
        
        
        // Now we figure out which handler should receive the message
        
        // The message received has no op, and thus is not an ExtensionServiceMethod
        // Since we're acting through the WebSocket interface, this means it should be a Http response
        if (msg.get("op") == null) {
            Response message = Response.fromMap(msg);
            log.debug("Http response received");
            if (client.isAuthed()) {
                // Is an error message before successful connection to the target source
                // This is most likely a failure related to a source connection request
                if (!client.isConnected() && (Integer) message.getStatus() >= 300) {
                    log.warn("Error occurred attempting to connect to source " + client.getSourceName());
                    log.debug("Error message was: "+ message);
                    client.sourceFuture.complete(false);
                }
                if (this.httpHandler != null) {
                    try {
                        this.httpHandler.handleMessage(message);
                    }
                    catch (Exception e) {
                        log.error("Error occurred when running the HTTP handler for source '" + 
                                client.getSourceName() + "'");
                    }
                }
                else {
                    log.debug("Http response received with no handler set");
                }
            }
            else {
                // Resetting authFuture is also sync'd on client, so this way they won't interfere with each other
                synchronized (client) {
                    // Checking isOpen() while sync'd in case of concurrency problems
                    if (!client.isOpen() || this.isClosed) {
                        return;
                    }

                    if ((int) message.getStatus() == 200 && !client.isAuthed()) {
                        // Forcibly setting in case an error occurred before succeeding
                        client.authFuture.obtrudeValue(true);
                    }
                    else {
                        client.authFuture.complete(false);
                        log.warn("Error occurred attempting to authenticate");
                    }
                    if (authHandler != null) {
                        try {
                            this.authHandler.handleMessage(message);
                        }
                        catch (Exception e) {
                            log.error("Error occurred when running the authentication handler for source '" + 
                                    client.getSourceName() + "'");
                        }
                    }
                    else {
                        log.debug("Authentication received with no handler set");
                    }
                }

            }
        }
        else {
            ExtensionServiceMessage message = new ExtensionServiceMessage("").fromMap(msg);
            if (client.isConnected()) {
                log.debug("Message with op '" + message.getOp() + "' received");
                log.debug("Map of ExtensionServiceMessage: " + message);
                if (message.getOp().equals(ExtensionServiceMessage.OP_PUBLISH))
                {
                    if (this.publishHandler != null) {
                        try {
                            this.publishHandler.handleMessage(message);
                        }
                        catch (Exception e) {
                            log.error("Error occurred when running the Publish handler for source '" + 
                                    client.getSourceName() + "'");
                        }
                    }
                    else {
                        log.debug("Publish received with no handler set");
                    }
                }
                else if (message.getOp().equals(ExtensionServiceMessage.OP_QUERY)) {
                    if (this.queryHandler != null && !isClosed) {
                        try {
                            this.queryHandler.handleMessage(message);
                        }
                        catch (Exception e) {
                            log.error("Error occurred when running the Query handler for source '" + 
                                    client.getSourceName() + "'");
                        }
                    }
                }
                else if (message.getOp().equals(ExtensionServiceMessage.OP_RECONNECT_REQUIRED)) {
                    synchronized (client) {
                        // Do not alert client of reconnect message if this client ahs been closed  
                        if (this.isClosed) {
                            return; 
                        }
                        client.sourceHasDisconnected(); // Resets to pre source connection state
                        if (this.reconnectHandler != null) {
                            try {
                                this.reconnectHandler.handleMessage(message);
                            }
                            catch (Exception e) {
                                log.error("Error occurred when running the Reconnect handler for source '" + 
                                        client.getSourceName() + "'");
                            }
                        }
                        if (client.autoReconnect) {
                            log.info("Automatically attempting to reconnect to source '" + client.getSourceName() + "'");
                            client.connectToSource();
                        }
                        // Warn when cannot reconnect or know that the connection has failed 
                        if (!client.autoReconnect && this.reconnectHandler == null) {
                            log.warn("Reconnect received with no handler set and no autoconnect. Can no longer "
                                    + "communicate with source '" + client.getSourceName() + "'");
                        }
                    }
                }
                else {
                    log.warn("ExtensionServiceMessage with unknown/unexpected op '" + msg.get("op") + "'");
                }
            }
            else if (msg.get("op").equals(ExtensionServiceMessage.OP_CONFIGURE_EXTENSION) && client.isAuthed()) {
                // Resetting sourceFuture is also sync'd on client, so this way they won't interfere with each other
                synchronized (client) {
                    // Rechecking isAuthed() while sync'd in case of concurrency problems
                    if (!client.isAuthed()) {
                        return;
                    }

                    // Forcibly setting in case an error occurred before succeeding
                    client.sourceFuture.obtrudeValue(true);
                    log.info("Successful connection to " + msg.get("resourceId").toString());
                    if (this.configHandler != null) {
                        try {
                            this.configHandler.handleMessage(message);
                        }
                        catch (Exception e) {
                            log.error("Error occurred when running the Configuration handler for source '" + 
                                    client.getSourceName() + "'");
                        }
                    }
                    else {
                        log.warn("Configuration received with no handler set");
                    }
                }
            }
            else {
                log.warn("ExtensionServiceMessage received when not connected");
            }
        }
    }

    /**
     * Sets this Listener's handlers to the same as {@code listener}. This function is intended to allow handlers to
     * maintain state even if the parent {@link ExtensionWebSocketClient} is closed due to websocket issues.
     * 
     * @param listener  The {@link ExtensionWebSocketListener} to copy the handlers from.
     */
    public void useHandlersFromListener(ExtensionWebSocketListener listener) {
        this.authHandler = listener.authHandler;
        this.configHandler = listener.configHandler;
        this.publishHandler = listener.publishHandler;
        this.httpHandler = listener.httpHandler;
        this.queryHandler = listener.queryHandler;
        this.reconnectHandler = listener.reconnectHandler;
    }
    
    /**
     * Sets this Listener's handlers to the same as the listener of {@code client}. This function is intended to allow 
     * handlers to maintain state even if the parent {@link ExtensionWebSocketClient} is closed due to websocket issues.
     * 
     * @param client    The {@link ExtensionWebSocketClient} to copy the handlers from.
     */
    public void useHandlersFromListener(ExtensionWebSocketClient client) {
        ExtensionWebSocketListener listener = client.getListener();
        this.useHandlersFromListener(listener);
    }
    
    /**
     * Logs the code and reason for this listener closing.
     *
     * @param code      The WebSocket code for why this listener is closing
     * @param reason    The {@link String} describing why it closed
     */
    @Override
    public void onClose(int code, String reason) {
        log.info("Closing websocket code: " + code);
        log.debug(reason);
        if (client.isOpen()) {
            client.close();
        }
    }

    /**
     * Logs the pong received.
     * @param payload   The payload received with the Pong message
     */
    @Override
    public void onPong(Buffer payload) {
        log.debug("Pong received");
        log.debug("Pong payload: " + payload.toString());
    }

    /**
     * Logs the error and closes the client. Only closes the client on an {@link EOFException} with no message, as that appears to
     * be the result of closing the connection with the Vantiq deployment.
     * @param e         The {@link IOException} that initiated the failure.
     * @param response  The {@link okhttp3.Response} that caused the failure, if any.
     */
    @Override
    public void onFailure(IOException e, okhttp3.Response response) {
        if (e instanceof EOFException) { // An EOF exception appears on closing the websocket connection
            if (e.getMessage() != null) {
                log.error("EOFException: " + e.getMessage());
            }
        }
        else if (e instanceof ConnectException) {
            log.error(e.getClass().toString() + ": " + e.getMessage());
        }
        else {
            log.error("Failure occurred in listener", e);
        }
        
        // The error occurred during an unknown point during execution. We don't have enough information to determine
        // what caused it, so we will close
        if (client.isOpen()) { 
            client.close();
        } else { // The websocket never opened, so it must be a problem connecting. Mark the failure and let the user handle it
            client.webSocketFuture.complete(false);
        }
    }
}
