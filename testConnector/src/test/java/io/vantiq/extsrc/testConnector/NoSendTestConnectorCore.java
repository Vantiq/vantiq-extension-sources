/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.testConnector;

import io.vantiq.extjsdk.FalseClient;

public class NoSendTestConnectorCore extends TestConnectorCore {

    FalseClient fClient;
    boolean closed = false;

    public NoSendTestConnectorCore(String sourceName, String authToken, String targetVantiqServer) {
        super(sourceName, authToken, targetVantiqServer);
    }

    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds for it to succeed or fail.
     * @return  true if the source connection succeeds, false if it fails.
     */
    @Override
    public void start(int timeout) {
        closed = false;
        fClient = new FalseClient(sourceName);
        client = fClient;
        testConnectorHandleConfiguration = new TestConnectorHandleConfiguration(this);

        client.setConfigHandler(testConnectorHandleConfiguration);
        client.setReconnectHandler(reconnectHandler);
        client.setCloseHandler(closeHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);

        fClient.completeAuthentication(true);
        fClient.completeWebSocketConnection(true);
        fClient.completeSourceConnection(true);

        exitIfConnectionFails(timeout);
        client.declareHealthy();
    }

    @Override
    public void close() {
        if (client != null) {
            client.declareUnhealthy();
        }
        super.close();
        closed = true;
    }

    @Override
    public void stop() {
        if (client != null) {
            client.declareUnhealthy();
        }
        super.stop();
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
