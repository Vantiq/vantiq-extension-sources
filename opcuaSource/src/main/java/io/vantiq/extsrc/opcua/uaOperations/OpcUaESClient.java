/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.opcua.uaOperations;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;

import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Client for working with OPCUA Server.
 *
 * Performs specific tasks that the Vantiq OPC UA Source needs.
 */

@Slf4j
public class OpcUaESClient {

    public static final String ERROR_PREFIX = "io.vantiq.extsrc.opcua.uaOperations" + "OpcuaESClient";
    private static final String SECURITY_DIRECTORY = "security";
    protected Map<String, Object> config;
    protected OpcUaClient client;
    protected UaSubscription subscription = null;
    protected String discoveryEndpoint = null;
    protected String serverEndpoint = null;
    protected BiConsumer<NodeId, Object> subscriptionHandler;
    protected List<UInteger> currentMonitoredItemList = null;
    protected KeyStoreManager keyStoreManager = null;
    protected CompletableFuture<Void> connectFuture = null;
    private final AtomicLong clientToMILink = new AtomicLong(1);
    private boolean connected = false;
    private static String defaultStorageDirectory = null;
    private final String storageDirectory;



    // For testing

    /**
     * Fetch the configuration object (map).  Used in testing.
     * @return Map represeting the configuration.
     */
    public Map<String, Object> getConfig() {
        return config;
    }

    /**
     * Set storage directory used by this source
     * @param dir directory in question
     */
    public static void setDefaultStorageDirectory(String dir) {
        defaultStorageDirectory = dir;
    }

    /**
     * Return storage directory in use
     * @return Directory in use.
     */
    public static String getDefaultStorageDirectory() { return defaultStorageDirectory;}


    /**
     * Create a client from information in the config document.  This config document is expected
     * to contain the culmination of any combinations or other manipulations that need to be done
     * in the calling system.
     * <p>
     * The config document contains the information necessary to create the client, as well as information
     * that may be used in other phases of this source's operation.  With respect to the connection, the
     * following information is used
     * <p>
     * (overall configuration document)
     * opcUAInformation
     * securityPolicy -- URI specifying the OPC UA Security Policy to use
     * discoveryEndpoint -- the discovery URL
     * storageDirectory -- the persistent store (probably disk) location for this extension source.  This
     * should be secure as security information will be stored here.
     *
     * @param theConfig The config document to use
     * @throws OpcExtConfigException when a null configuration is passed in
     * @throws Exception due to other errors during processing the creation of the client
     */
    public OpcUaESClient(Map<String, Object> theConfig) throws OpcExtConfigException, Exception
    {
        if (theConfig == null) {
            String errMsg = ERROR_PREFIX + ".nullConfig: Configuration was null";
            log.error(errMsg);
            throw new OpcExtConfigException(errMsg);
        }

        validateConfiguration(theConfig);
        config = theConfig; // Store for later use

        // Extract OPC information and create the client.
        //noinspection unchecked
        Map<String, Object> opcConfig = (Map<String, Object>) theConfig.get(OpcConstants.CONFIG_OPC_UA_INFORMATION);
        storageDirectory = opcConfig.get(OpcConstants.CONFIG_STORAGE_DIRECTORY) != null
                ? (String) opcConfig.get(OpcConstants.CONFIG_STORAGE_DIRECTORY) : defaultStorageDirectory;

        client = createClient(opcConfig);
    }

    public X509Certificate getCertificate() {
        Optional<X509Certificate> maybeCert = client.getConfig().getCertificate();
        return maybeCert.orElse(null);
    }

    /**
     * Is client currently connected to an OPC UA Server
     * @return boolean indicating whether the client is currently connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Connect the client to the OPC UA server
     *
     * Performs a synchronous connectino to the server, throwing exceptions when failures occur.
     *
     * @throws ExecutionException Thrown by underlying connection to server when the connection cannot be made
     * @throws OpcExtConfigException Thrown when the server in question is not available
     * @throws OpcExtRuntimeException Thrown when the server connection call is interrupted
     */
    public void connect() throws ExecutionException, OpcExtConfigException, OpcExtRuntimeException {
        try {
            client.connect().get();
            connected = true;
        } catch (ExecutionException e) {
            if (e.getMessage().equals("java.nio.channels.UnresolvedAddressException")) {
                // This indicates that the server is not available.  Throw a more useful exception.
                throw new OpcExtConfigException(ERROR_PREFIX + ".serverUnavailable: OPC Server unavailable", e);
            } else {
                throw e;
            }
        }
        catch (InterruptedException e) {
            throw new OpcExtRuntimeException("Connect call as interrupted", e);
        }
    }

    /**
     * Connect the client to the OPC UA server
     *
     * Performs an asynchronous connectino to the server, throwing exceptions when failures occur.  Connection attempt
     * is run using the simple CompletableFuture.runAsync() method, so from the default pool.
     *
     * @throws Exception Thrown by underlying connection to server when the connection cannot be made
     * @return CompletableFuture&lt;Void&gt; from which the connection status can be determined.
     */
    public CompletableFuture<Void> connectAsync() throws Exception {
        connectFuture = CompletableFuture.runAsync(() -> {
                    try {
                        client.connect().join();
                        connected = true;
                    } catch (Throwable t) {
                        log.error("Failed to connect to OPC: ", t);
                        throw t;    // We'll rethrow this so that the future will complete (exceptionally)
                    }
                }
        );
        return connectFuture;
    }

    /**
     * Fetch the CompletableFuture used to make the connection, if any.  If a synchronous connection was made,
     * this will return null.
     *
     * @return CompletableFuture&lt;Void&gt; used to make the OPC UA Client connection, if any.  Null if none was created.
     */
    public CompletableFuture<Void> getConnectFuture() {
        return connectFuture;
    }

    /**
     * Disconnection client from OPC UA Server
     */
    public void disconnect() {
        if (connected) {
            try {
                client.disconnect().get();  // Disconnect client & await completion thereof
            } catch (Throwable e) {
                log.error(ERROR_PREFIX + ".disconnectFailure: " + e.getMessage());
            }
        }
        connected = false;
    }

    private void validateConfiguration(Map config) throws OpcExtConfigException {
        if (config == null) {
            String errMsg = ERROR_PREFIX + ".nullConfig: No configuration was provided.";
            log.error(errMsg);
            throw new OpcExtConfigException(errMsg);
        } else if (config.get(OpcConstants.CONFIG_OPC_UA_INFORMATION) == null) {
            String errMsg = ERROR_PREFIX + ".noOPCInformation: Configuration contained no OPC Information.";
            throwError(errMsg);
        }
        //noinspection unchecked
        Map<String, String> opcConfig = (Map<String, String>) config.get(OpcConstants.CONFIG_OPC_UA_INFORMATION);

        String errMsg = null;
        if (!opcConfig.containsKey(OpcConstants.CONFIG_STORAGE_DIRECTORY) && defaultStorageDirectory == null) {
            errMsg = ERROR_PREFIX + ".noStorageSpecified: No storageDirectory provided in configuration. " +
                    "The configuration should contain a property: " + OpcConstants.CONFIG_STORAGE_DIRECTORY;
        } else if (!opcConfig.containsKey(OpcConstants.CONFIG_DISCOVERY_ENDPOINT)) {
            errMsg = ".noDiscoveryEndpoint: No discovery endpoint was provided in the configuration. " +
                    "The configuration should contain a property: " +
                    OpcConstants.CONFIG_DISCOVERY_ENDPOINT;
        } else if (!opcConfig.containsKey(OpcConstants.CONFIG_SECURITY_POLICY)) {
            errMsg = ".noSecurityPolicy: No OPC UA Security policy was specified in the configuration. "
                    + "The configuration should contain a property: " + OpcConstants.CONFIG_SECURITY_POLICY;
        }

        if (errMsg != null) {
            errMsg = ERROR_PREFIX + errMsg;
            throwError(errMsg);
        }
    }

    private void throwError(String msg) throws OpcExtConfigException {
        log.error(msg);
        throw new OpcExtConfigException(msg);
    }

    private SecurityPolicy determineSecurityPolicy(Map<String, Object> config) throws OpcExtConfigException {
        // config.securityPolicy should be the URI for the appropriate security policy.

        String secPolURI = (String) config.get(OpcConstants.CONFIG_SECURITY_POLICY);
        if (secPolURI == null || secPolURI.isEmpty()) {
            // No security policy will default to #NONE.  We will, however, log a warning
            log.warn(ERROR_PREFIX + ".defaultingSecurityPolicy: No OPC UA Security policy was specified in the configuration.  Defaulting to #NONE");
            secPolURI = SecurityPolicy.None.getUri();
        }
        try {
            //noinspection UnusedReturnValue
            URI.create(secPolURI);  // To verify wellformedness
            return SecurityPolicy.fromUri(secPolURI);
        } catch (IllegalArgumentException e) {
            String errMsg = ERROR_PREFIX + ".invalidSecurityPolicySyntax: " + secPolURI + " is not a syntactically correct URI";
            log.error(errMsg);
            throw new OpcExtConfigException(errMsg, e);
        } catch (UaException e) {
            String errMsg = ERROR_PREFIX + ".invalidSecurityPolicy: " + secPolURI + " is not a valid security URI";
            log.error(errMsg);
            throw new OpcExtConfigException(errMsg, e);
        }
    }

    private MessageSecurityMode determineMessageSecurityMode(Map<String, Object> config) throws OpcExtConfigException {
        // config.messageSecurityMode should be the URI for the appropriate security policy.

        String msgSecModeSpec = (String) config.get(OpcConstants.CONFIG_MESSAGE_SECURITY_MODE);
        MessageSecurityMode msgSecMode;
        if (msgSecModeSpec == null || msgSecModeSpec.isEmpty()) {
            // No message security mode will default to either #NONE or #SignAndEncrypt, depending on the security policy.  We will, however, log a warning\
            SecurityPolicy secPol = determineSecurityPolicy(config);
            if (secPol.equals(SecurityPolicy.None)) {
                msgSecModeSpec = MessageSecurityMode.None.toString();
            } else {
                msgSecModeSpec = MessageSecurityMode.SignAndEncrypt.toString();
            }
            log.warn(ERROR_PREFIX + ".defaultMessageSecurityMode: No OPC UA message security mode was specified in the configuration. " +
                    "Using default value of '{}' based on the securityPolicy value of '{}'",
                    msgSecModeSpec,
                    secPol.getUri());
        }
        try {
            msgSecMode = MessageSecurityMode.valueOf(msgSecModeSpec);
            return msgSecMode;
        } catch (IllegalArgumentException e) {
            String errMsg = ERROR_PREFIX + ".invalidMessageSecurityMode: " + msgSecModeSpec + " is not a valid message security mode.";
            log.error(errMsg);
            throw new OpcExtConfigException(errMsg, e);
        }
    }

    private static Boolean foundValue(String val) {
        return val != null && !val.isEmpty();
    }

    private IdentityProvider constructIdentityProvider(Map<String, Object> config) throws OpcExtConfigException, OpcExtKeyStoreException {
        IdentityProvider retVal = null;

        String anonymous = (String) config.get(OpcConstants.CONFIG_IDENTITY_ANONYMOUS);
        boolean anonIsPresent = anonymous != null; // This can be empty -- presence is sufficient
        String certAlias = (String) config.get(OpcConstants.CONFIG_IDENTITY_CERTIFICATE);
        boolean certIsPresent = foundValue(certAlias);
        String userPass = (String) config.get(OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD);
        boolean upwIsPresent = foundValue(userPass);
        boolean exactlyOnePresent = (anonIsPresent ^ certIsPresent ^ upwIsPresent) ^ (anonIsPresent && certIsPresent && upwIsPresent);
        // (^ is Java XOR -- I didn't remember it!)

        if (!anonIsPresent && !certIsPresent && !upwIsPresent) {
            log.warn(ERROR_PREFIX + ".noIdentitySpecification: No identity specification was provided.  Using Anonymous as default.");
            retVal = new AnonymousProvider();
        } else if (exactlyOnePresent) {
            // Now we know there is exactly one of them set.
            if (anonIsPresent) {
                retVal = new AnonymousProvider();
            } else if (certIsPresent) {
                X509Certificate namedCert = keyStoreManager.fetchCertByAlias(certAlias);
                PrivateKey pKey = keyStoreManager.fetchPrivateKeyByAlias(certAlias);
                retVal = new X509IdentityProvider(namedCert, pKey);
            } else if (upwIsPresent) {
                String[] upw = userPass.split(",[ ]*");
                if (upw.length != 2) {
                    String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".invalidUserPasswordSpecification: the {} ({}) must contain only a username AND password separated by a comma.",
                            new Object[]{OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD, userPass}).getMessage();
                    log.error(errMsg);
                    throw new OpcExtConfigException(errMsg);
                } else {
                    retVal = new UsernameProvider(upw[0], upw[1]);
                }
            }

        } else {
                String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".invalidIdentitySpecification: exactly one identity specification ({}, {}, {}) is required.",
                        new Object[]{OpcConstants.CONFIG_IDENTITY_ANONYMOUS, OpcConstants.CONFIG_IDENTITY_CERTIFICATE, OpcConstants.CONFIG_IDENTITY_USERNAME_PASSWORD}).getMessage();
                log.error(errMsg);
                throw new OpcExtConfigException(errMsg);
        }

        return retVal;
    }

    private OpcUaClient createClient(Map<String, Object> config) throws Exception {

        if (storageDirectory == null) {
            throw new OpcExtConfigException(ERROR_PREFIX + ".missingStorageDirectory: No storage directory specified.");
        }
        SecurityPolicy securityPolicy = determineSecurityPolicy(config);
        MessageSecurityMode msgSecMode = determineMessageSecurityMode(config);

        File securityDir = new File(storageDirectory, SECURITY_DIRECTORY);
        if (!securityDir.exists() && !securityDir.mkdirs()) {
            throw new OpcExtConfigException(ERROR_PREFIX + ".invalidStorageDirectory: unable to create security dir: " + securityDir);
        }
        log.info("security temp dir: {}", securityDir.getAbsolutePath());

        keyStoreManager = new KeyStoreManager().load(securityDir);

        IdentityProvider idProvider = constructIdentityProvider(config);

        List<EndpointDescription> endpoints;

        discoveryEndpoint = (String) config.get(OpcConstants.CONFIG_DISCOVERY_ENDPOINT);
        serverEndpoint = (String) config.get(OpcConstants.CONFIG_SERVER_ENDPOINT);
        if (discoveryEndpoint == null && serverEndpoint == null) {
            String errorMsg = ERROR_PREFIX + ".noDiscoveryEndpoint: No discovery or server endpoint was provided in the configuration.";
            log.error(errorMsg);
            throw new OpcExtConfigException(errorMsg);
        }

        OpcUaClientConfig opcConfig;
        try {
            endpoints = DiscoveryClient
                    .getEndpoints(discoveryEndpoint)
                    .get();
        } catch (Throwable ex) {
            try {
                // try the explicit discovery endpoint as well
                String discoveryUrl = discoveryEndpoint + "/discovery";
                log.info("Trying explicit discovery URL: {}", discoveryUrl);
                endpoints = DiscoveryClient
                        .getEndpoints(discoveryUrl)
                        .get();
            } catch (ExecutionException e) {
                String errMsg = ERROR_PREFIX + ".discoveryError: Could not discover OPC Endpoints:"
                        + e.getClass().getName() + "::" + e.getMessage();
                log.error(ERROR_PREFIX + ".discoveryError: Could not discover OPC Endpoints: {} :: {}", e.getClass().getName(), e.getMessage());
                throw new OpcExtConfigException(errMsg, e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Endpoints discovered via discoveryEndpoint: " + discoveryEndpoint);
            for (EndpointDescription e : endpoints) {
                URI secPolUri = new URI(e.getSecurityPolicyUri());
                String fragSpec = secPolUri.getFragment();
                if (fragSpec == null) {
                    fragSpec = e.getSecurityPolicyUri();
                }
                log.debug("    Discovered endpoint: {} [{}, {}])", e.getEndpointUrl(), fragSpec, e.getSecurityMode());
            }
        }

        List<EndpointDescription> validEndpoints = endpoints.stream()
                .filter(e -> (e.getSecurityPolicyUri().equals(securityPolicy.getUri())
                        && e.getSecurityMode().equals(msgSecMode)))
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("Discovered endpoints that accept the security configuration: [security policy: {}, message security mode: {}]",
                    securityPolicy.getUri(),
                    msgSecMode);
            for (EndpointDescription e : validEndpoints) {
                URI secPolUri = new URI(e.getSecurityPolicyUri());
                String fragSpec = secPolUri.getFragment();
                if (fragSpec == null) {
                    fragSpec = e.getSecurityPolicyUri();
                }
                log.debug("    Acceptable endpoint: {} [{}, {}])", e.getEndpointUrl(), fragSpec, e.getSecurityMode());
            }

            // The following code is here for testing only.  It allows us to fake a poorly configured
            // server that reports invalid or unreachable endpoints as part of discovery.  This is, for
            // reasons I'm sure i don't agree with, part of the protocol, so we must tolerate it.  The
            // purportedly proper response is to substitute the address used for discovery for any
            // unreachable addresses.  This, of course, makes little sense since the whole point of discovery
            // is to allow these to be spread across different nodes.  But I didn't write the spec.

            Boolean fakeBadAddress = (Boolean) config.get(OpcConstants.CONFIG_TEST_DISCOVERY_UNREACHABLE);
            if (fakeBadAddress != null && fakeBadAddress) {
                List<EndpointDescription> newValidEndpoints = new ArrayList<>();
                for (EndpointDescription e : validEndpoints) {
                    URI url = new URI(e.getEndpointUrl());
                    URI borkedUri = new URI(url.getScheme(),
                            null,
                            "utterlyWorthlessHostThatShouldNeverResolve",
                            url.getPort(),
                            url.getPath(),
                            null,
                            null);
                    EndpointDescription borkedEd = new EndpointDescription(borkedUri.toString(),
                            e.getServer(),
                            e.getServerCertificate(),
                            e.getSecurityMode(),
                            e.getSecurityPolicyUri(),
                            e.getUserIdentityTokens(),
                            e.getTransportProfileUri(),
                            e.getSecurityLevel());
                    newValidEndpoints.add(borkedEd);
                }
                validEndpoints = newValidEndpoints;
            }
        }
        // First, we'll look for an endpoint that doesn't contain localhost.  This is, generally,
        // a not too useful configuration since localhost is always a relative address.

        EndpointDescription endpoint = validEndpoints.stream()
                .filter(e -> {
                    try {
                        // Note:  Must use URI here.  If you use URL, it will fail with
                        // a MailformedURLException because the generic system doesn't
                        // understand opc.tcp: as a scheme/protocol.
                        URI url = new URI(e.getEndpointUrl());
                        InetAddress ina = InetAddress.getByName(url.getHost());
                        if (!ina.isLoopbackAddress() || ina.isReachable(3000)) {
                            return true;
                        }
                    } catch (UnknownHostException | URISyntaxException ex) {
                        log.warn("Recoverable error during discovered server URL validation:" + ex.getClass().getName() + "::" + ex.getMessage() + "-->" + e.getEndpointUrl());
                    } catch (Exception ex) {
                        // This means that we have some non-optimal addresses returned by discovery.
                        // In these cases, we'll leave it up to the SDK & network stack to figure out how to get there.
                        log.debug("Recoverable error during discovered server URL validation. Left to network stack to resolve:"
                                + ex.getClass().getName() + "::" + ex.getMessage() + "-->" + e.getEndpointUrl());
                    }
                    return false;
                }).findFirst().orElse(null);

        // If endpoint is set here, then we have a valid address that's not localhost-y.
        // If not, we'll go find one that is localhost & hope for the best

        if (endpoint == null) {
            // Discovery server returned either no reasonable endpoints or none that weren't a loopback.
            log.warn("No servers at reachable, non-loopback addresses found via discovery. " +
                    "Fixing up addresses to match discovery server.");

            endpoint = validEndpoints.stream()
                    .findFirst().orElse(null);
            // Here, if we have no endpoint, then we can't go anywhere so we give up.
            // Otherwise, we'll check if we're supposed to fix up a poorly configured
            // discovery server by setting the localhost-y address it reported
            // to be the same host as the discovery server

            // As noted above, the spec says that the discovery server can return unreachable addresses
            // Consequently, we'll apply the spec'd response which is to substitute the discovery address.

            if (endpoint != null) {
                // Fixup loopback or unreachable address...

                URI url = new URI(endpoint.getEndpointUrl());
                try {
                    InetAddress ina = null;
                    try {
                        ina = InetAddress.getByName(url.getHost());
                    } catch (UnknownHostException uhe) {
                        // We'll treat this the same as unreachable.  Leave ina null to be checked below
                    }

                    if (ina == null || ina.isLoopbackAddress() || !ina.isReachable(3000)) {
                        // We'll only do this replacement for loopback or unreachable addresses.
                        // We can end up here if the addresses are less than optimal, but the SDK can connect.

                        URI discUrl = new URI(discoveryEndpoint);

                        log.info("Host {} is either unreachable or is a loopback address.  Substituting discovery address: {}",
                                url.getHost(), discUrl.getHost());

                        URI fixedEndpoint = new URI(url.getScheme(),
                                null,
                                discUrl.getHost(),
                                url.getPort(),
                                url.getPath(),
                                null,
                                null);

                        EndpointDescription newEndpoint = new EndpointDescription(fixedEndpoint.toString(),
                                endpoint.getServer(),
                                endpoint.getServerCertificate(),
                                endpoint.getSecurityMode(),
                                endpoint.getSecurityPolicyUri(),
                                endpoint.getUserIdentityTokens(),
                                endpoint.getTransportProfileUri(),
                                endpoint.getSecurityLevel());
                        log.debug("Replacing loopback/unreachable address for endpoint: {} --> {}",
                                endpoint.getEndpointUrl(), newEndpoint.getEndpointUrl());

                        endpoint = newEndpoint;
                    }
                } catch (Exception ex) {
                    // This means that we have some non-optimal addresses returned by discovery.
                    // In these cases, we'll leave it up to the SDK & network stack to figure out how to get there.
                    log.debug("Recoverable error during discovered server URL validation. Left to network stack to resolve:" + ex.getClass().getName() + "::" + ex.getMessage() + "-->" + endpoint.getEndpointUrl());
                }
            }
        }

        if (endpoint == null) {
            throw new Exception("No acceptable endpoints returned for security policy: " +
                    securityPolicy.getUri() + " and security mode " + msgSecMode);
        }

        if (serverEndpoint != null) {
            // Then we'll override the endpoint provided but otherwise use the endpoint descriptor returned.
            // The SDK seems to have an issue when no EndpointDescriptor is provided.

            EndpointDescription newEndpoint = new EndpointDescription(serverEndpoint,
                    endpoint.getServer(),
                    endpoint.getServerCertificate(),
                    endpoint.getSecurityMode(),
                    endpoint.getSecurityPolicyUri(),
                    endpoint.getUserIdentityTokens(),
                    endpoint.getTransportProfileUri(),
                    endpoint.getSecurityLevel());
            log.debug("Replacing endpoint address with provided serverEndpoint: {} --> {}", endpoint.getEndpointUrl(), newEndpoint.getEndpointUrl());

            endpoint = newEndpoint;
        }

        log.info("Using discovered endpoint: {} [{}, {}]", endpoint.getEndpointUrl(), securityPolicy, msgSecMode.toString());

        opcConfig = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("VANTIQ OPC-UA Source"))
                .setApplicationUri("urn:io:vantiq:extsrc:opcua:client")
                .setCertificate(keyStoreManager.getClientCertificate())
                .setKeyPair(keyStoreManager.getClientKeyPair())
                .setEndpoint(endpoint)
                .setIdentityProvider(idProvider)
                .setRequestTimeout(uint(5000))
                .build();

        return OpcUaClient.create(opcConfig);
    }

    /**
     * Write a value into OPCUA.
     * <p>
     * This method writes the value attribute of a node via the connected OPC US server. Only writes of non-indexed data
     * are supported (i.e.) one cannot write to some particular index in an array).
     * The information necessary to write this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param namespaceURN The identity of the OPC UA namespace into which the write is to occur
     * @param identifier The node to be written
     * @param datatype The datatype of the entity in question.
     * @param value The value to be written.
     * @throws OpcExtRuntimeException wraps any errors returned by the server.  May also be thrown if the namespace is not found.
     */
    public void writeValue(String namespaceURN, String identifier, String datatype, Object value) throws OpcExtRuntimeException {

        // To perform the insert, we need to translate the namespace URN into an index.  We'll do that here.
        UShort nsIndex = client.getNamespaceTable().getIndex(namespaceURN);
        if (nsIndex == null) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".badNamespaceURN:  Namespace URN " + namespaceURN + " does not exist in the OPC server.");
        } else {
            writeValue(nsIndex, identifier, datatype, value);
        }
    }

    /**
     * Write a value into OPCUA.
     * <p>
     * This method writes the value attribute of a node via the connected OPC US server. Only writes of non-indexed data
     * are supported (i.e.) one cannot write to some particular index in an array).
     * The information necessary to write this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param nsIndex The namespace index of the OPC UA namespace into which the write is to occur
     * @param identifier The node to be written
     * @param datatype The datatype of the entity in question.
     * @param value The value to be written.
     * @throws OpcExtRuntimeException wraps any errors returned by the server.
     */
    public void writeValue(UShort nsIndex, String identifier, String datatype, Object value) throws OpcExtRuntimeException {

        Variant v = new Variant(value);

        List<NodeId> nodesToWrite = ImmutableList.of(new NodeId(nsIndex, identifier));

        // don't write status or timestamps
        DataValue dv = new DataValue(v, null, null);

        StatusCode status = null;
        try {
            // write asynchronously....
            CompletableFuture<List<StatusCode>> syncFuture = client.writeValues(nodesToWrite, ImmutableList.of(dv));

            // ...but block for the results so we write in order
            List<StatusCode> statusCodes = syncFuture.get();
            status = statusCodes.get(0);
        } catch (Exception e) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".unexpectedException: OPC UA Error", e);
        }
        if (status != null && status.isGood()) {
            log.debug("Wrote value '{}' to nodeId={}", v, nodesToWrite.get(0));
        } else {
            String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".writeError: OPC UA Error '{}' performing writeValues() for nodeId={}, value={}",
                    new Object[]{status, nodesToWrite.get(0), value}).getMessage();
            log.error(errMsg);
            throw new OpcExtRuntimeException(errMsg);
        }
    }

    /**
     * Read a value from an OPC UA server.
     * <p>
     * This method reads the value attribute of a node via the connected OPC US server. Only reads of non-indexed data
     * are supported (i.e.) one cannot read  some particular index in an array).
     * The information necessary to read this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param namespaceURN The identity of the OPC UA namespace from which the read is to occur
     * @param identifier The node to be read.  The identifier is assumed to be of type 's' (String).
     * @return the read value (as a Java Object).
     * @throws OpcExtRuntimeException wraps any errors returned by the server.  May also be thrown if the namespace is not found.
     */

    public Object readValue(String namespaceURN, String identifier) throws OpcExtRuntimeException {
        return readValue(namespaceURN, identifier, "s");
    }

    /**
     * Read a value from an OPC UA server.
     * <p>
     * This method reads the value attribute of a node via the connected OPC US server. Only reads of non-indexed data
     * are supported (i.e.) one cannot read  some particular index in an array).
     * The information necessary to read this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param namespaceURN The identity of the OPC UA namespace from which the read is to occur
     * @param identifier The node to be read
     * @param identifierType The (OPC) type of the identifier (i, s, g, b) (s/String is the default)
     * @return the read value (as a Java Object).
     * @throws OpcExtRuntimeException wraps any errors returned by the server.  May also be thrown if the namespace is not found.
     */
    public Object readValue(String namespaceURN, String identifier, String identifierType) throws OpcExtRuntimeException {
        UShort nsIndex = client.getNamespaceTable().getIndex(namespaceURN);
        if (nsIndex == null) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".badNamespaceURN:  Namespace URN " + namespaceURN + " does not exist in the OPC server.");
        } else {
            return readValue(nsIndex, identifier, identifierType);
        }
    }

    /**
     * Read a value from an OPC UA server.
     * <p>
     * This method reads the value attribute of a node via the connected OPC US server. Only reads of non-indexed data
     * are supported (i.e.) one cannot read  some particular index in an array).
     * The information necessary to read this value is specified by the parameters listed below.
     * Errors result in OpcExtRuntimeExceptions.
     *
     * @param nsIndex The namespace index of the OPC UA namespace from which the read is to occur
     * @param identifier The node to be read
     * @return the read value (as a Java Object).
     * @throws OpcExtRuntimeException wraps any errors returned by the server.
     */
    public Object readValue(UShort nsIndex, String identifier) throws OpcExtRuntimeException {
        return readValue(nsIndex, identifier, "s");
    }

    public Object readValue(UShort nsIndex, String identifier, String identifierType) throws OpcExtRuntimeException {
        try {
            UaVariableNode theNode = client.getAddressSpace().getVariableNode(constructNodeId(nsIndex, identifier, identifierType));
                //.   .get();
            return theNode.readValue().getValue().getValue();
        } catch (Exception e) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".unexpectedException: OPC UA Error", e);
        }
    }

    public String getNamespaceURN(UShort index) {
        return client.getNamespaceTable().getUri(index);
    }

    /**
     * Subscribe (if necessary) and monitor items specified in config.
     * <p>
     * The configuration will contain a set of items about which our user
     * is interested in hearing about data changes.  Each is specified by including a
     * OPC UA namespace URN or index, and an associated identifier.
     * <p>
     * When this method is called, the set of specified monitored items is reconciled with the
     * actual monitored items.  The result is that data value changes to those monitored items will
     * cause the calling of the provided handler.
     *
     * @param config The configuration specifying the items to monitor.
     * @param handler The handler to call with data change updates
     * @throws OpcExtRuntimeException In case of errors, etc.
     */

    public void updateMonitoredItems(Map<String, Object> config, BiConsumer<NodeId, Object> handler) throws OpcExtRuntimeException {
        try {
            if (subscription == null) {
                // TODO -- update interval should be gleaned from the config file
                subscription = client.getSubscriptionManager().createSubscription(1000.0).get();
            }

            // noinspection unchecked
            Map<String, Object> opcConf = (Map<String, Object>) config.get(OpcConstants.CONFIG_OPC_UA_INFORMATION);
            Object mis = opcConf.get(OpcConstants.CONFIG_OPC_MONITORED_ITEMS);

            if (mis instanceof Map) {
                // noinspection unchecked
                Map<String, Map<String, String>> newMonitoredItems = (Map<String, Map<String, String>>) mis;

                if (newMonitoredItems.isEmpty()) {
                    log.info("No monitoring requested for OPC UA server with discovery endpoint: {}", discoveryEndpoint);
                } else {
                    log.debug("Config requesting {} monitored items", newMonitoredItems.size());

                    if (currentMonitoredItemList != null && !currentMonitoredItemList.isEmpty()) {
                        // First, if we're currently monitoring anything, remove them.
                        // This can be improved to compare new vs old, but this overkill version is sufficient for now.
                        client.deleteMonitoredItems(subscription.getSubscriptionId(), currentMonitoredItemList).get();
                    }
                    currentMonitoredItemList = new ArrayList<>();

                    // First, create a list of MI requests from our config file.
                    ArrayList<MonitoredItemCreateRequest> reqList = new ArrayList<MonitoredItemCreateRequest>();
                    for (Map.Entry<String, Map<String, String>> ent : newMonitoredItems.entrySet()) {
                        UShort nsIndex;
                        NodeId nodeIdentifier = null;

                        // Determine node we wish to monitor...

                        // Nodes can be defined in a couple of major ways.  The first includes a nodeId field, which
                        // combines (standard nomenclature in OPC UA) the namespace index & identifer: "ns=3;s=someNode/identifier";
                        // If this nodeId field is present, we ignore other fields as it is a complete specification.
                        // Otherwise, we'll look for the namespace specification (either ns for a numeric namespace index or
                        // nsu for the namespace URN) and a node identifier.  The node identifier can be augmented with a type
                        // (s, i, g, or b for String, Numeric (integer), GUID, or ByteString, respectively) to describe how
                        // the identifier is encoded.  If this is missing, it is assumed to be a string.

                        if (ent.getValue().containsKey(OpcConstants.CONFIG_MI_NODEID)) {
                            String nodeIdSpec = ent.getValue().get(OpcConstants.CONFIG_MI_NODEID);
                            nodeIdentifier = NodeId.parse(nodeIdSpec);
                        } else {
                            if (ent.getValue().containsKey(OpcConstants.CONFIG_MI_NAMESPACE_URN)) {
                                nsIndex = client.getNamespaceTable().getIndex(ent.getValue().get(OpcConstants.CONFIG_MI_NAMESPACE_URN));
                                if (nsIndex == null) {
                                    throw new OpcExtRuntimeException(ERROR_PREFIX + ".badNamespaceURN:  Namespace URN "
                                            + ent.getValue().get(OpcConstants.CONFIG_MI_NAMESPACE_URN) + " does not exist in the OPC server.");
                                }
                            } else if (ent.getValue().containsKey(OpcConstants.CONFIG_MI_NAMESPACE_INDEX)) { // TODO -- should we disallow this form since it's not stable over reboots?  I think yes, but it is convenient
                                nsIndex = UShort.valueOf(ent.getValue().get(OpcConstants.CONFIG_MI_NAMESPACE_INDEX));
                            } else {
                                String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".invalidMonitoredItem: Monitored Item {} has no namespace index: {}",
                                        new Object[]{ent.getKey(), ent.getValue()}).getMessage();
                                throw new OpcExtRuntimeException(errMsg);
                            }
                            String nodeIdType = "s";
                            if (ent.getValue().containsKey(OpcConstants.CONFIG_MI_IDENTIFIER_TYPE)) {
                                nodeIdType = ent.getValue().get(OpcConstants.CONFIG_MI_IDENTIFIER_TYPE);
                            }
                            nodeIdentifier = constructNodeId(nsIndex, ent.getValue().get(OpcConstants.CONFIG_MI_IDENTIFIER), nodeIdType);
                        }
                        ReadValueId readValueId = new ReadValueId(
                                nodeIdentifier,
                                AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

                        // important: client handle must be unique per item
                        UInteger localHandle = uint(clientToMILink.getAndIncrement());

                        MonitoringParameters parms = new MonitoringParameters(
                                localHandle,
                                1000.0,     // sampling interval // TODO -- We should allow this to be adjustable
                                null,       // filter, null means use default
                                uint(10),   // queue size
                                true        // discard oldest
                        );

                        MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                                readValueId, MonitoringMode.Reporting, parms);
                        log.debug("MonitoredItemCreateRequest for {} added to list", readValueId.toString());
                        reqList.add(request);
                    }

                    UaSubscription.ItemCreationCallback monitoringCreated =
                         (item, index) ->  item.setValueConsumer(this::onDataChange);

                    this.subscriptionHandler = handler;
                    // Having created the list, add it to the subscription
                    List<UaMonitoredItem> items = subscription.createMonitoredItems(
                            TimestampsToReturn.Both,
                            reqList,
                            monitoringCreated
                    ).get();

                    for (UaMonitoredItem item : items) {
                        if (item.getStatusCode().isGood()) {
                            log.debug("item created for nodeId={}", item.getReadValueId().getNodeId());
                            currentMonitoredItemList.add(item.getMonitoredItemId());
                        } else {
                            log.warn(
                                    "failed to create item for nodeId={} (status={})",
                                    item.getReadValueId().getNodeId(), item.getStatusCode());
                        }
                    }
                }
            } else {
                log.error("Illegal format for {}.{} in configuration document.  Expecting a Map of Maps of Strings.",
                        OpcConstants.CONFIG_OPC_UA_INFORMATION, OpcConstants.CONFIG_OPC_MONITORED_ITEMS);
            }
        } catch (Exception e) {
            throw new OpcExtRuntimeException(ERROR_PREFIX + ".unexpectedException", e);
        }
    }

    private void onDataChange(UaMonitoredItem item, DataValue value) {
        log.debug(
                "Update event on subscription {} value received: item={}, value={}",
                subscription.getSubscriptionId(), item.getReadValueId().getNodeId().toParseableString(), value.getValue());
        subscriptionHandler.accept(item.getReadValueId().getNodeId(), value.getValue().getValue());
    }

    private NodeId constructNodeId(UShort nsIndex, String identifier, String identifierType) throws OpcExtRuntimeException {
        NodeId nodeIdentifier;
        switch (identifierType) {
            case "s":
                nodeIdentifier = new NodeId(nsIndex, identifier);
                break;
            case "i":
                nodeIdentifier = new NodeId(nsIndex, UInteger.valueOf(identifier));
                break;
            case "g":
                nodeIdentifier = new NodeId(nsIndex, UUID.fromString(identifier));
                break;
            case "b":
                nodeIdentifier = new NodeId(nsIndex, new ByteString(identifier.getBytes()));
                break;
            default:
                String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".invalidNodeIdType: {}.  It must be either s, i, g, or b",
                        new Object[]{identifier}).getMessage();
                throw new OpcExtRuntimeException(errMsg);
        }
        return nodeIdentifier;
    }
}
