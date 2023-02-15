/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel.utils;

import io.vantiq.extjsdk.ExtensionWebSocketClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

/**
 * A registry of known clients.
 *
 * This is used by camel connector to avoid having multiple clients for the same source/server combination.  Since
 * the camel components can construct their own clients (when used as a pure Camel component rather than in our
 * connector), we need to avoid this situation.
 *
 * Although this is (at least, originally) used by Camel connector, it is co-located with the Camel components so
 * that they can call it.  This allows Apache Camel applications that make use of the Vantiq component to be
 * constructed without the inclusion of the Camel connector.
 */
public class ClientRegistry {
    
    private static final String SOURCE_NAME_PARAM = "?sourceName=";
    public static final String SOURCE_NAME_KEY = "sourceName";
    public static final String SERVER_URL_KEY = "serverUrl";
    private static final ConcurrentMap<String, ExtensionWebSocketClient> knownClients = new ConcurrentHashMap<>();
    
    /**
     * Get the known client for the source name and server specified.
     *
     * @param sourceName String name of the Vantiq source this client represents
     * @param serverUrl String URL of the Vantiq installation within which the source resides
     * @return ExtensionWebSocketClient corresponding to the sourceName & serverUrl, or null if none is present
     */
    public static ExtensionWebSocketClient fetchClient(String sourceName, String serverUrl) {
        String key = constructClientKey(sourceName, serverUrl);
        return knownClients.get(key);
    }
    
    /**
     * Register a client in the registry
     * @param sourceName String name of the Vantiq source this client represents
     * @param serverUrl String URL of the Vantiq installation within which the source resides
     * @param clientBuilder BiFunction<String, String, ExtensionWebSocketClient> function to build the client if one
     *                      does not already exist.
     * @return ExtensionWebSocketClient if created, null if not created (i.e., already exists).
     */
    public static ExtensionWebSocketClient registerClient(String sourceName, String serverUrl,
                                                          BiFunction<String, String, ExtensionWebSocketClient> clientBuilder) {
        String key = constructClientKey(sourceName, serverUrl);
        return knownClients.computeIfAbsent(key, (clientKey) -> {
            return clientBuilder.apply(sourceName, serverUrl);
        });
    }
    
    /**
     * Register a client in the registry
     * @param sourceName String name of the Vantiq source this client represents
     * @param serverUrl String URL of the Vantiq installation within which the source resides
     * @param client ExtensionWebSocketClient client to register
     * @return ExtensionWebSocketClient associated with the sourceName & serverUrl.  This will be the given client if
     *                                  did not exist, or the existing client if one as previously registered.
     */
    public static ExtensionWebSocketClient registerClient(String sourceName, String serverUrl,
                                                    ExtensionWebSocketClient client) {
        String key = constructClientKey(sourceName, serverUrl);
        return knownClients.putIfAbsent(key, client);
    }
    
    /**
     * Remove the registered mapping for the given sourceName & serverUrl.
     *
     * If the client parameter is non-null, the registered client is removed only if the client is the one
     * specified.  If the client parameter is null, any registered client matching the sourceName and serverUrl will
     * be removed from the registry.
     *
     * @param sourceName String name of the Vantiq source this client represents
     * @param serverUrl String URL of the Vantiq installation within which the source resides
     * @param client ExtensionWebSocketClient client to remove.  Can be null.
     * @return ExtensionWebSocketClient removed.  Null if no client was removed.
     */
    public static ExtensionWebSocketClient removeClient(String sourceName, String serverUrl, ExtensionWebSocketClient client) {
        String key = constructClientKey(sourceName, serverUrl);
        ExtensionWebSocketClient removed;
        if (client != null) {
            if (knownClients.remove(key, client)) {
                removed = client;
            } else {
                removed = null;
            }
        } else {
            removed = knownClients.remove(key);
        }
        return removed;
    }
    
    /**
     * Return the number of registered clients
     *
     * @return int representing the number of currently registered clients.
     **/
    //
    public static int getKnownClientCount() {
        return knownClients.size();
    }
    
    /**
     * Construct the key used in our registry.
     *
     * @param sourceName String name of the Vantiq source this client represents
     * @param serverUrl String URL of the Vantiq installation within which the source resides
     * @return String representing the key to use in our registry.
     */
    public static String constructClientKey(String sourceName, String serverUrl) {
        // Normalize URL just in case...
        if (!serverUrl.endsWith("/")) {
            serverUrl = serverUrl + "/";
        }
        return serverUrl + SOURCE_NAME_PARAM + sourceName;
    }
    
    /**
     * Parse the key to its component parts.
     *
     * @param key String as constructed by {@link ClientRegistry#constructClientKey(String, String)
     * constructClientKey()}
     * @return Map with two members, sourceName ({@link ClientRegistry#SOURCE_NAME_KEY}) and serverUrl
     *                  ({@link ClientRegistry#SERVER_URL_KEY}).
     * @throws URISyntaxException when the URI represented by the key is malformed.
     */
    public static Map<String, String> parseKey(String key) throws URISyntaxException {
        URI uri = new URI(key);
        Map<String, String> res = new HashMap<>();
        String qry = uri.getQuery();
        String src = qry.split("=")[1];
        res.put(SOURCE_NAME_KEY, src);
        res.put(SERVER_URL_KEY, key.substring(0, key.lastIndexOf(SOURCE_NAME_PARAM)));
        return res;
    }
}
