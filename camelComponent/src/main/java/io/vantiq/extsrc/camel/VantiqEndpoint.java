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
import io.vantiq.extsrc.camel.utils.ClientRegistry;
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
import java.util.HashMap;
import java.util.Map;
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
             category = {Category.CLOUD, Category.SAAS})
@UriParams
@Slf4j
public class VantiqEndpoint extends DefaultEndpoint {
    
    public static final String STRUCTURED_MESSAGE_MESSAGE_PROPERTY = "message";
    public static final String STRUCTURED_MESSAGE_HEADERS_PROPERTY = "headers";
    
    public static final String SOURCE_NAME_PARAM = "sourceName";
    @UriParam @Metadata(required = true)
    @Setter
    private String sourceName;
    
    public static final String ACCESS_TOKEN_PARAM = "accessToken";
    @UriParam(label="security", secret = true) @Metadata(required = true)
    @Setter
    private String accessToken;
    
    public static final String SEND_PINGS_PARAM = "sendPings";
    @UriParam(defaultValue = "false")
    @Getter
    @Setter
    private boolean sendPings;
    
    public static final String NO_SSL_PARAM = "noSsl";
    @UriParam(defaultValue = "false")
    @Getter
    @Setter
    private boolean noSsl;
    
    public static final String CONSUMER_OUTPUT_JSON_PARAM = "consumerOutputJson";
    @Deprecated
    @UriParam(defaultValue = "false") @Metadata(deprecationNote = "Use (replaced by) consumerOutputJsonStream which " +
            "emulates a marshal step more closely.")
    @Getter
    @Setter
    private boolean consumerOutputJson;
    
    public static final String CONSUMER_OUTPUT_JSON_STREAM_PARAM = "consumerOutputJsonStream";
    @UriParam(defaultValue = "false")
    @Getter
    @Setter
    private boolean consumerOutputJsonStream;
    
    public static final String FAILED_MESSAGE_QUEUE_SIZE_PARAM = "failedMessageQueueSize";
    @UriParam(defaultValue = "25")
    @Setter
    private int failedMessageQueueSize;
    
    public static final String STRUCTURED_MESSAGE_HEADER_PARAM = "structuredMessageHeader";
    @UriParam(defaultValue = "false")
    @Getter
    @Setter
    private boolean structuredMessageHeader;
    
    // Note doing this as a constructed bean rather than a multivalue parameter because the number of headers might
    // be large, and a large set thereof may violate URI length requirements. Also, it's primarily intended for use
    // in the Camel Connector, so constructing such a things there based on source parameters is not hard.  Should a
    // need arise, we can offer another option(s).
    public static final String HEADER_DUPLICATION_BEAN_NAME = "headerDuplicationBeanName";
    
    @UriParam(name = HEADER_DUPLICATION_BEAN_NAME, description = "A bean name where the bean contains a " +
            "headerDuplicationMap value mapping header names to a duplicate header into which the value should be " +
            "copied.")
    @Metadata(required = false)
    @Getter
    @Setter
    private String headerDuplicationBeanName;
    
    @Setter
    @Getter
    private ExtensionWebSocketClient vantiqClient;
    
    private String correctedVantiqUrl = null;
    private boolean started = false;
    private final String startStopLock = "startStopLock";
    private boolean runningInConnector = false;
    
    private boolean consumerCreated = false;
    
    @Getter
    private String endpointName;
    
    @Getter
    private Map<String, String> headerDuplicationMap;
    
    InstanceConfigUtils utils;

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
     * the alternative is to allow for a URI of "vantiq://server.config".  If we see a "host" name of "server.config",
     * we will call the superclass constructor with a uri of null, and the processing described above will take over.
     * This capability is primarily of use in the case of the Camel connector, allowing the user to avoid repeatedly
     * having to specify the connection information.
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
        log.debug("Creating VantiqEndpoint for uri: {} with sourceName: {}, accessToken: {}",
                 uri, sourceName, accessTokenForLog());
        utils = new InstanceConfigUtils();
    }
    
    private void buildEndpointName() {
        String baseUri = getEndpointBaseUri();
        String vantiqInstName = baseUri.replace("vantiq://", "");
        vantiqInstName = vantiqInstName.replace("/", "");
        endpointName = "vantiq--" + vantiqInstName + "::" + sourceName;
        log.debug("Endpoint name {} assigned for {}", endpointName, getEndpointBaseUri());
    }

    public Producer createProducer() throws Exception {
        log.info("VantiqEndpoint creating producer for uri: {} with sourceName: {}, accessToken: {}",
                getEndpointBaseUri(), sourceName, accessTokenForLog());
    
        return new VantiqProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        log.info("VantiqEndpoint creating consumer for uri: {} with sourceName: {}, accessToken: {}",
                getEndpointBaseUri(), sourceName, accessTokenForLog());
        if (consumerCreated) {
            log.error("More than one consumer created for this endpoint. Check that this endpoint's consumer is" +
                              " not used in multiple places. That said, sometimes this error result from defining " +
                              "routes without ids.  Please add ids to any routes in use and retry.");
        }
        Consumer consumer = new VantiqConsumer(this, processor);
        consumerCreated = true;
        configureConsumer(consumer);
        return consumer;
    }
    
    public void sendMessage(Object vMsg) {
        vantiqClient.sendNotification(vMsg);
    }
    
    public void sendResponse(int httpStatus, String respAddress, Map vMsg) {
        vantiqClient.sendQueryResponse(httpStatus, respAddress, vMsg);
    }
    
    public void sendQueryError(String respAddress, String msgCode, String msgText, Object[] params) {
        vantiqClient.sendQueryError(respAddress, msgCode, msgText, params);
    }
    
    /**
     * Constructs & returns an ExtensionWebSocketClient.
     *
     * This is here primarily to override for unit testing. In unit test cases, we override to return a client
     * that doesn't send to the (nonexistent) Vantiq installation;
     *
     * @param source        String name of the source to which to connect
     * @param msgQueueSize  int number of messages to queue when the connection is broken.
     * @return ExtensionWebSocketClient
     */
    protected ExtensionWebSocketClient buildVantiqClient(String source, int msgQueueSize) {
        return new ExtensionWebSocketClient(sourceName, failedMessageQueueSize, utils);
    }
    
    protected ExtensionWebSocketClient buildVantiqClientFromTarget(String source, String targetServer) {
        return new ExtensionWebSocketClient(sourceName, failedMessageQueueSize, utils);
    }
    
    protected void completeFuturesForFauxInstances() {
        // Does nothing in the real case.  Overridden in FauxVantiqEndpoint.
    }
    
    public void startup() throws CamelException {
    
        synchronized (startStopLock) {
            if (started) {
                return;
            }
            CamelException failure = null;
            try {
                if (headerDuplicationBeanName != null &&
                        !headerDuplicationBeanName.isEmpty() && !headerDuplicationBeanName.isBlank()) {
                    // If we have header equivalents specified, fetch them and populate our local store for our consumers &
                    // producers
                    HeaderDuplicationBean hdBean = getCamelContext().getRegistry().lookupByNameAndType(
                            headerDuplicationBeanName,
                            HeaderDuplicationBean.class);
                    if (hdBean == null) {
                        throw new IllegalArgumentException("No HeaderDuplicationBean named " + headerDuplicationBeanName +
                                                                   " was found in the Camel Registry.");
                    } else {
                        headerDuplicationMap = hdBean.getHeaderDuplicationMap();
                    }
                }
                log.debug("Attempting to connect to URL: {} from {}", getEndpointBaseUri(), getEndpointUri());
                String vtq = getEndpointBaseUri();
               
                correctedVantiqUrl = adjustVantiqTarget(vtq, noSsl);
                utils.provideServerConfig(correctedVantiqUrl, accessToken, sourceName, sendPings, null);
                buildEndpointName();
        
                // If we are running inside a Vantiq Camel connector, the connector runtime may have already created a
                // client. If that's the case, use, that client,  Otherwise, we'll create our own, knowing that the
                // connector runtime is not managing our client.
        
                vantiqClient = ClientRegistry.fetchClient(sourceName, correctedVantiqUrl);
                if (vantiqClient != null) {
                    runningInConnector = true;
                    started = true;
                    return;
                } else {
                    // Reconfig's are handled here by auto-reconnect.
            
                    vantiqClient = buildVantiqClient(sourceName, failedMessageQueueSize);
                    CompletableFuture<Boolean> fut =
                            vantiqClient.initiateFullConnection(correctedVantiqUrl, accessToken, sendPings);
            
                    completeFuturesForFauxInstances();
                    if (fut.get()) {
                        runningInConnector = false;
                        started = true; // Mark that we've successfully started up.
                        vantiqClient.setAutoReconnect(true);
                        return;
                    } else {
                        String errMsg = "Failed to initiate connection to Vantiq server: " + vtq +
                                " (" + correctedVantiqUrl + "), source: " + sourceName;
                        // Something didn't work.  Let's try & get a better diagnosis of what went awry.
                        if (!vantiqClient.isOpen()) {
                            // Then we failed to connect.
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
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to initiate connection to Vantiq server: {}, source: {}",
                          correctedVantiqUrl, sourceName, e);
                throw new CamelException(e);
            }
            throw failure;
        }
    }
    
    public boolean isConnected() {
        synchronized (startStopLock) {
            return started;
        }
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
            throw new IllegalArgumentException("source.config file is missing or does not contain sufficient " +
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
            
            StringBuilder epString = new StringBuilder(vantiqURI.toString());
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
                noSsl = true;
            }
            epString.replace(0, origScheme.length(), "vantiq");
            URI endpointUri = URI.create(String.valueOf(epString));
            return endpointUri.toString();
        } catch (URISyntaxException  mue) {
            throw new IllegalArgumentException(TARGET_SERVER_PROPERTY_NAME + " from server config file is invalid",
                                               mue);
        }
    }
    
    /**
     * Adjust Vantiq URL.
     *
     * If using a standard vantiq:// connection, set the adjusted URL to a more reasonable protocol.  If the URL does
     * not have a scheme of "vantiq", then leave it alone. No adjustment is necessary
     *
     * @param baseUrl String the baseURL to be adjusted
     * @param noSsl boolean indicating whether we've been told to skip the SSL handling
     *                          (i.e.,  use http rather than https).  Only applies if we're adjusting the URL
     * @return String adjusted (if appropriate) URL
     */
    public static String adjustVantiqTarget(String baseUrl, boolean noSsl) throws CamelException {
        String correctedUrl = null;
        String protocol = "https";
        if (noSsl) {
            // This is used in development or edge servers.
            protocol = "http";
        }
        try {
            URI vtqUri = new URI(baseUrl);
            if (vtqUri.getScheme().equalsIgnoreCase("vantiq")) {
                vtqUri = new URI(protocol, null, vtqUri.getHost(), vtqUri.getPort(), vtqUri.getPath(), null,
                                 null);
                correctedUrl = vtqUri.toASCIIString();
            } else {
                correctedUrl = baseUrl;
            }
            log.debug("Adjusted Vantiq URL: {} (from {})", correctedUrl, baseUrl);
        } catch (URISyntaxException uriSE) {
            throw new CamelException("Unable to connect to provided URI: " + baseUrl +
                                             " (adjusted: " + correctedUrl + ")", uriSE);
        }
        return correctedUrl;
    }
    
    /**
     * Return map of headers duplicated as specified by the HEADER_EQUIVALENCE_BEAN_NAME parameter.
     * <p>
     * If no duplication is specified or nothing found to duplicate, return an empty map.
     * <p>
     * Note that if multiple headers are specified to duplicate to a single duplicate (e.g., both header foo & bar
     * are both supposed to be duplicated to the header baz), the results are unpredictable.  Something will happen,
     * but the results depend upon the order on which the headers are processed, and may vary from instance to
     * instance.  Callers should take care to avoid these situations.  There may be cases where components have
     * alternatives that are used, both of which are desired to duplicate to the same header, so we allow such a
     * definition.
     *
     * @param headers Map<String, Object> specifying the headers supplied by Camel
     * @return Map<String, Object> specifying the duplicated headers.  Returns an empty map of nothing to duplicated
     */
    public Map<String, Object> duplicateHeaders(Map<String, Object> headers) {
        Map<String, Object> dupedHdrs = new HashMap<>();
        if (headerDuplicationMap != null && headerDuplicationMap.size() > 0) {
            headerDuplicationMap.forEach((k, v) -> {
                if (headers.get(k) != null) {
                    // If we have a value for a header we're expecting to duplicate, then we duplicate that
                    // value into the duplicated header's name.
                    dupedHdrs.put(v, headers.get(k));
                }
            });
        }
        return dupedHdrs;
    }
    @Override
    public void doStop() {
        synchronized (startStopLock) {
            if (vantiqClient != null) {
                if (!runningInConnector) {
                    // Only close if we are the one that opened things
                    vantiqClient.close();
                }
                vantiqClient = null;
            }
            started = false;
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
