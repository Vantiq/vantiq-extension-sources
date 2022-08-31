package io.vantiq.extsrc.camel;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.InstanceConfigUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelException;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * vantiq component to interact with Vantiq systems.
 *
 * The vantiq component supports sending and receiving events to/from a Vantiq installation via a Vantiq source. As
 * messages are sent or received, the producer or consumer, respectively, process these messages.  All such messages
 * are one-way.
 *
 */
@UriEndpoint(firstVersion = "1.0-SNAPSHOT", scheme = "vantiq", title = "vantiq", syntax="vantiq:name",
             category = {Category.JAVA})
@UriParams
@Slf4j
public class VantiqEndpoint extends DefaultEndpoint {
    @UriParam @Metadata(required = true)
    @Setter
    private String sourceName;
    
    @UriParam(label="security", secret = true) @Metadata(required = true)
    @Setter
    private String accessToken;
    
    @UriParam(defaultValue = "false")
    @Getter
    @Setter
    private boolean sendPings;
    
    @UriParam(defaultValue = "false")
    @Getter
    @Setter
    private boolean noSsl;
    
    @UriParam(defaultValue = "25")
    @Setter
    private int failedMessageQueueSize;
    
    @Setter
    @Getter
    private ExtensionWebSocketClient vantiqClient;
    
    private String correctedVantiqUrl = null;
    private boolean started = false;
    
    @Getter
    private String endpointName;
    
    InstanceConfigUtils utils = null;

    public VantiqEndpoint() {
        utils = new InstanceConfigUtils();
    }

    public VantiqEndpoint(String uri, VantiqComponent component) {
        super(uri, component);
        log.info("Creating VantiqEndpoint for uri: {} with sourceName: {}, accessToken: {}",
                uri, sourceName, accessTokenForLog());
         utils = new InstanceConfigUtils();
    }
    
    private void buildEndpointName() {
        String baseUri = getEndpointBaseUri();
        String vantiqInstName = baseUri.replace("vantiq://", "");
        vantiqInstName = vantiqInstName.replace("/", "");
        endpointName = "vantiq--" + vantiqInstName + "::" + sourceName;
        log.debug("Endpoint name {} assigned for for {}", endpointName, getEndpointBaseUri());
    }

    public Producer createProducer() throws Exception {
        log.info("VantiqEndpoint creating producer for uri: {} with sourceName: {}, accessToken: {}",
                getEndpointBaseUri(), sourceName, accessTokenForLog());
    
        return new VantiqProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        log.info("VantiqEndpoint creating consumer for uri: {} with sourceName: {}, accessToken: {}",
                getEndpointBaseUri(), sourceName, accessTokenForLog());
        Consumer consumer = new VantiqConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }
    
    public void sendMessage(Object vMsg) {
        vantiqClient.sendNotification(vMsg);
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
    protected ExtensionWebSocketClient buildVantiqClient(String source, int msgQueueSize) {
       return  new ExtensionWebSocketClient(sourceName, failedMessageQueueSize, utils);
    }
    
    protected void completeFuturesForFauxInstances() {
        // Does nothing in the real case.  Overridden in FauxVantiqEndpoint.
    }
    
    public void startup() throws CamelException {
    
        if (started) {
            return;
        }
        CamelException failure = null;
        try {
            log.debug("Attempting to connect to URL: {}", getEndpointBaseUri());
            String vtq = getEndpointBaseUri();
            String protocol = "https";
            if (noSsl) {
                // This is primarily for use in development.  But supported...
               protocol = "http";
            }
            correctedVantiqUrl = vtq.replace("vantiq", protocol);
            log.trace("Fixed-up Vantiq URL: {}", correctedVantiqUrl);
            // TODO:  Add fetching of TCP port from somewhere. This doesn't seem like it should be on the URL.
            utils.provideServerConfig(correctedVantiqUrl, accessToken, sourceName, sendPings, null);
            vantiqClient = buildVantiqClient(sourceName, failedMessageQueueSize);
            buildEndpointName();
            CompletableFuture<Boolean> fut = vantiqClient.initiateFullConnection(correctedVantiqUrl, accessToken, sendPings);
    
            completeFuturesForFauxInstances();
            if (fut.get()) {
                started = true; // Mark that we've successfully started up.
                vantiqClient.setAutoReconnect(true);
                return;
            } else {
                String errMsg = "Failed to initiate connection to Vantiq server: " + vtq +
                        " (" + correctedVantiqUrl + "), source: " + sourceName;
                // Something didn't work.  Let's try & get a better diagnosis of what went awry.
                if (!vantiqClient.isOpen()) {
                    // Then we failed to connected.
                    errMsg = "Failed to connect to Vantiq server: " + vtq +
                            " (" + correctedVantiqUrl + ").";
                } else if (!vantiqClient.isAuthed()) {
                    errMsg = "Authentication failure connecting to " + vtq +
                            " (" + correctedVantiqUrl + ").";
                } else if (!vantiqClient.isConnected()) {
                    errMsg = "Failed to connect to Vantiq source " + sourceName + " at server: " + vtq +
                            " (" + correctedVantiqUrl + ").";
                }
                log.error(errMsg);
                failure = new CamelException(errMsg);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to initiate connection to Vantiq server: {}, source: {}",
                    correctedVantiqUrl, sourceName, e);
            throw new CamelException(e);
        }
        throw failure;
    }
    
    public boolean isConnected() {
        return started;
    }
    
    @Override
    public void doStop() {
        if (vantiqClient != null) {
            vantiqClient.close();
            vantiqClient = null;
        }
    }

    public ExecutorService createExecutor() {
        // TODO: Pool min/max sizes should be configurable in URI or properties
        return getCamelContext().getExecutorServiceManager().newThreadPool(this, endpointName,
                1, 5);
    }
    
    /**
     * Sanitize access token for logging
     * @return String access token representation suitable for logging.
     */
    private String accessTokenForLog() {
        return StringUtils.isEmpty(accessToken) ? "null/empty" : "present";
    }
}
