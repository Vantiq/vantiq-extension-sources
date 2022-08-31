
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

//Author: Alex Blumer
//Email: support@vantiq.com

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;

/**
 * Provides a Client that will fake its WebSocket connection and can retrieve the data it sent via the fake connection.
 * Only Notification and source connection messages will be {@link ExtensionServiceMessage}, all others will be 
 * {@link Response}.
 *
 */
public class FalseClient extends ExtensionWebSocketClient {
    
    @Setter
    private boolean defeatAutoAck = false;
    public FalseClient(String sourceName) {
        super(sourceName);
        listener = new TestListener(this);
    }
    
    ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Acknowledge the send.
     *
     * In the "real" client, this happens when the response message is received.  Here, since we don't have a response
     * message (since we have no real message), we'll do the ack automatically when someone reads the message.  Since
     * these messages are meant to be visited immediately, this is sufficient.
     *
     * We allow this behavior to be defeated if desired (using this.setDefeatAutoAck(true);).  Yes, it's backwards,
     * but it is unusual to do this.
     */
    @Override
    void acknowledgeNotification() {
        if (!defeatAutoAck) {
            super.acknowledgeNotification();
        }
    }
    @Override
    public CompletableFuture<Boolean> initiateWebsocketConnection(String url, boolean sendPings) {
        webSocketFuture = new CompletableFuture<Boolean>();
        webSocket = new FalseWebSocket();
        return webSocketFuture;
    }
    
    @Override
    public CompletableFuture<Boolean> initiateWebsocketConnection(String url) {
        return initiateWebsocketConnection(url, false);
    }
    
    /**
     * Tries to signal that the webSocket connection completed with status {@success}. Returns true if this action
     * could be completed successfully. 
     * 
     * @param success   Whether to mark the operation as a success.
     * @return          True if it was completed successfully. False if one of: intiateWebSocketConnection() or
     *                  initiateFullConnection() hasn't 
     *                  been called since either initialization or the last close() call; the webSocket connection
     *                  has already been completed.
     */
    public boolean completeWebSocketConnection(boolean success) {
        if (webSocketFuture != null && !webSocketFuture.isDone()) {
            webSocketFuture.complete(success);
            return true;
        } else {
            return false;
        }
    }
    /**
     * Tries to signal that the authentication completed with status {@success}. Returns true if this action
     * could be completed successfully. 
     * 
     * @param success   Whether to mark the operation as a success.
     * @return          True if it was completed successfully. False if one of: initiateWebSocket()->authenticate()
     *                  or initiateFullConnection() hasn't 
     *                  been called since either initialization or the last close() call; theauthentication
     *                  has already been completed.
     */
    public boolean completeAuthentication(boolean success) {
        if (authFuture != null && !authFuture.isDone()) {
            authFuture.complete(success);
            return true;
        } else {
            return false;
        }
    }
    /**
     * Tries to signal that the source connection completed with status {@success}. Returns true if this action
     * could be completed successfully. 
     * 
     * @param success   Whether to mark the operation as a success.
     * @return          True if it was completed successfully. False if one of: initiateWebSocket()->authenticate()
     *                  ->sourceConnection() or initiateFullConnection() hasn't 
     *                  been called since either initialization or the last close() call; the source connection
     *                  has already been completed.
     */
    public boolean completeSourceConnection(boolean success) {
        if (sourceFuture != null && !sourceFuture.isDone()) {
            sourceFuture.complete(success);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Returns the last message sent by the client as a JSON object represented by bytes.
     *
     * @return  The last message sent by the client as a JSON object represented by a byte array or null if no 
     *          message has been sent.
     */
    public byte[] getLastMessageAsBytes() {
        acknowledgeNotification();
        return ((FalseWebSocket) webSocket).getMessage();
    }
    
    /**
     * Returns the last message sent by the client as a Map. Note that all arrays will have been turned into
     * ArrayLists and internal byte arrays are Base-64 strings.
     * 
     * @return  The last message sent by the client as a Map, or null if no message has been sent yet.
     */
    public Map getLastMessageAsMap() {
        acknowledgeNotification();
        try {
            return mapper.readValue(((FalseWebSocket) webSocket).getMessage(), Map.class);
        } catch(IOException e) {
            return null;
        }
    }
    
    /**
     * Returns the last message sent by the client as a {@link Response}. Note that all arrays will have been turned
     * into ArrayLists and byte arrays are Base-64 strings.
     * 
     * @return  The last message sent by the client as a Response, or null if no message has been sent yet.
     * @throws  IOException if the message could not be interpreted as a Response. This is most likely to occur if
     *          the last message was not a Response.
     */
    public Response getLastMessageAsResponse() throws IOException {
        acknowledgeNotification();
    
        byte[] bytes = ((FalseWebSocket) webSocket).getMessage();
        if (bytes == null) {
            return null;
        }
        return mapper.readValue(bytes, Response.class);
    }
    
    /**
     * Returns the last message sent by the client as a {@link ExtensionServiceMessage}. Note that all arrays will
     * have been turned into ArrayLists and byte arrays are Base-64 strings.
     * 
     * @return  The last message sent by the client as a ExtensionServiceMessage, or null if no message has been 
     *          sent yet.
     * @throws  IOException if the message could not be interpreted as a ExtensionServiceMessage. This is most
     *          likely to occur if the last message was not a ExtensionServiceMessage.
     */
    public ExtensionServiceMessage getLastMessageAsExtSvcMsg() throws IOException {
        acknowledgeNotification();
        byte[] bytes = ((FalseWebSocket) webSocket).getMessage();
        if (bytes == null) {
            return null;
        }
        return mapper.readValue(bytes, ExtensionServiceMessage.class);
    }
}
