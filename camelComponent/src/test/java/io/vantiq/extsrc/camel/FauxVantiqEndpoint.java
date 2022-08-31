package io.vantiq.extsrc.camel;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.FalseClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Category;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParams;

/**
 * vantiq component which does bla bla.
 *
 * TODO: Update one line description above what the component does.
 */
@UriEndpoint(firstVersion = "1.0-SNAPSHOT", scheme = "vantiq", title = "vantiq", syntax="vantiq:name",
             category = {Category.JAVA})
@UriParams
@Slf4j
public class FauxVantiqEndpoint extends VantiqEndpoint {
    public FalseClient myClient = null;
    FauxVantiqEndpoint(String uri, VantiqComponent component) {
        super(uri, component);
    }
    /**
     * Constructs & returns an ExtensionWebSocketClient.
     *
     * This is here primarily to override for unit testing. In unit test cases, we override to return a client
     * that doesn't send to the (nonexistent Vantiq installation);
     *
     * @param source        String name of the source to which to connect
     * @param msgQueueSize  int number of messages to queue when the connection is broken.
     * @return ExtensionWebSocketClient
     */
    @Override
    protected ExtensionWebSocketClient buildVantiqClient(String source, int msgQueueSize) {
        // Save the client so unit tests can pick it up.
        myClient = new FalseClient(source);
        return myClient;
    }
    
    @Override
    protected void completeFuturesForFauxInstances() {
        if (myClient != null) {
            myClient.completeWebSocketConnection(true);
            myClient.completeAuthentication(true);
            myClient.completeSourceConnection(true);
        }
    }
 }
