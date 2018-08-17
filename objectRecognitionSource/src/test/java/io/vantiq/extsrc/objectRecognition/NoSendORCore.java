package io.vantiq.extsrc.objectRecognition;

import io.vantiq.extjsdk.FalseClient;

public class NoSendORCore extends ObjectRecognitionCore{

    FalseClient fClient;
    boolean closed = false;
    
    public NoSendORCore(String sourceName, String authToken, String targetVantiqServer, String modelDirectory) {
        super(sourceName, authToken, targetVantiqServer, modelDirectory);
    }
    
    /**
     * Tries to connect to a source and waits up to 10 seconds for it to succeed or fail.
     * @return  true if the source connection succeeds, false if it fails.
     */
    @Override
    public boolean start() {
        closed = false;
        fClient = new FalseClient(sourceName);
        client = fClient;
        objRecConfigHandler = new ObjectRecognitionConfigHandler(this);
        
        client.setConfigHandler(objRecConfigHandler);
        client.setReconnectHandler(reconnectHandler);
        client.setCloseHandler(closeHandler);
        client.setQueryHandler(defaultQueryHandler);
        client.initiateFullConnection(targetVantiqServer, authToken);
        
        return exitIfConnectionFails(client);
    }
    
    @Override
    public void close() {
        super.close();
        closed = true;
    }
    
    @Override
    public void stop() {
        super.stop();
        closed = true;
    }
    
    public boolean isClose() {
        return closed;
    }
}
