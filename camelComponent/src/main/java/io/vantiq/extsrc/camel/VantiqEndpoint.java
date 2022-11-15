/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import static io.vantiq.extjsdk.Utils.AUTH_TOKEN_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.SEND_PING_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.SERVER_CONFIG_FILENAME;
import static io.vantiq.extjsdk.Utils.SOURCES_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.TARGET_SERVER_PROPERTY_NAME;

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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
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
    
    /**
     * Create the Vantiq endpoint based on the URI provided.
     *
     * We have some special handling for the URI.  If it's null (generally harder to do except from bean-style
     * configurations), we'll let that through.  In such cases, our superclass will call our overridden method
     * createEndpointUri().  This will construct the URI from the information in the containing connector's
     * server.config file (if any). As noted, this is sometimes hard to do given programmatic route construction. So,
     * the alternative is to allow for a URI of "vantiq://server.config".  If see a "host" name of "server.config",
     * we will call the superclass constructor with a uri of null, and the processing described above will take over.
     * This capability is primarily of use in the case of the Camel connector, allowing the user to avoid repeatedly
     * haveing to specify the connection information.
     *
     * @param uri String the URI to which to connect
     * @param component VantiqComponent the Camel component from which to get the endpoint.
     * @throws Exception when the URI is invalid or there are other issues constructing the endpoint
     */
    public VantiqEndpoint(String uri, VantiqComponent component) throws Exception {
        // A bit of a strange construct, but necessary given the required interface and Java's requirement that the
        // super() call be the first functional line of a constructor. A "factory method" here might be a better
        // choice, but the constructor interface is fixed by Camel.
        super( (uri != null && new URI(uri).getHost().equalsIgnoreCase(SERVER_CONFIG_FILENAME)) ?
                      null : uri, component);
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
    
    /**
     * Create and endpoint URI for our VantiqEndpoint.
     *
     * This is used by the DefaultEndpoint code when no endpoint URI is specified.  In the Vantiq case, we will
     * interpret the lack of URI specification to mean that this VantiqEndpoint is to connect to the Vantiq
     * described in the standard connector server.config file.  We will provide the parameters as is appropriate from
     * data gleaned from the server.config file.
     *
     * @return String representing the URI provided by the source.config file.
     * @throws IllegalArgumentException when no source.config file is present or the data present is incorrect.
     */
    @Override
    public String createEndpointUri() {
        
        Properties scProps = utils.obtainServerConfig();
        String baseUri = scProps.getProperty(TARGET_SERVER_PROPERTY_NAME);
        String accessToken = scProps.getProperty(AUTH_TOKEN_PROPERTY_NAME);
        String sourceName = scProps.getProperty(SOURCES_PROPERTY_NAME);
        String sendPings = scProps.getProperty(SEND_PING_PROPERTY_NAME);
        if (StringUtils.isEmpty(baseUri) || StringUtils.isEmpty(accessToken)) {
            throw new IllegalArgumentException("source.config file is missing or does not contain sufficent " +
                                                       "information from which to construct an endpoint URI.");
        }
        if (StringUtils.isEmpty(sourceName) || sourceName.contains(",")) {
            throw new IllegalArgumentException("Default vantiq: endpoints require a source.config file with a single" +
                                                       " source name. Found: '" + sourceName + "'.");
        }
        
        try {
            URI vantiqURI = new URI(baseUri);
            this.setEndpointUri(baseUri);
            String origScheme = vantiqURI.getScheme();
            
            StringBuffer epString = new StringBuffer(vantiqURI.toString());
            epString.append("?sourceName=").append(sourceName);
            this.sourceName = sourceName;
            epString.append("&accessToken=").append(accessToken);
            this.accessToken = accessToken;
            if (sendPings != null) {
                epString.append("&sendPings=").append(sendPings);
                this.sendPings = Boolean.parseBoolean(sendPings);
            }
            if (origScheme.equals("http") || origScheme.equals("ws")) {
                epString.append("&noSsl=").append("true");
            }
            epString.replace(0, origScheme.length(), "vantiq");
            URI endpointUri = URI.create(String.valueOf(epString));
            return endpointUri.toString();
        } catch (URISyntaxException  mue) {
            throw new IllegalArgumentException(TARGET_SERVER_PROPERTY_NAME + " from server config file is invalid",
                                               mue);
        }
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
