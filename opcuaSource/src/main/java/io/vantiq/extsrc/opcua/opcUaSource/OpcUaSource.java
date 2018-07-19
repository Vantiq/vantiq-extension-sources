package io.vantiq.extsrc.opcua.opcUaSource;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.opcua.uaOperations.OpcUaESClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class OpcUaSource {

    public static final String VANTIQ_URL = "vantiqUrl";
    public static final String VANTIQ_USERNAME = "username";
    public static final String VANTIQ_PASSWORD = "password";
    public static final String VANTIQ_TOKEN = "token";

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
            log.error("No VANTIQ URL provided");
        }

        if (token != null) {
            useToken = true;
        } else if (username == null || password == null) {
            log.error("No VANTIQ credentials provided.");
        }

        // If we get here, then we have sufficient information.  Let's see if we can get it to work...

        ExtensionWebSocketClient localClient = new ExtensionWebSocketClient(sourceName);
        CompletableFuture<Boolean> connecter = localClient.initiateWebsocketConnection(url);
        try {
            connecter.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (!localClient.isOpen()) {
            log.error("Unable to connect to VANTIQ: {}", url);
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
            log.error("Failed to authenticate our connection to VANTIQ: {}", url);
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

        // If we're here, we've succeeded.  Save the client and return
        //vantiqClient = localClient;
        log.info("Connection succeeded:  We're up");
        localClient.close();
        return true;
    }
}
