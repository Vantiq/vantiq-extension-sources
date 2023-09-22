/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.connector;

import static io.vantiq.client.Vantiq.SystemResources.DOCUMENTS;

import com.google.gson.JsonObject;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.camelconn.discover.CamelRunner;
import lombok.extern.slf4j.Slf4j;
import okio.BufferedSource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;


/**
 * Sets up the source using the configuration document, which looks as below.
 *<pre> {
 *      camelConfig: {
 *          camelRuntime: {
 *               &lt;camel app options&gt;
 *          }
 *          general: {
 *              &lt;general options&gt;
 *          }
 *      }
 * }</pre>
 *
 */

@Slf4j
public class CamelHandleConfiguration extends Handler<ExtensionServiceMessage> {
    String                  sourceName;
    CamelCore               source;
    boolean                 configComplete = false; // Not currently used

    // Constants for getting config options
    public static final String CAMEL_CONFIG = "config";
    public static final String CAMEL_APP = "camelRuntime";
    public static final String APP_NAME = "appName";
    public static final String APP_NAME_DEFAULT = "camelConnectorApp";
    public static final String ROUTES_DOCUMENT = "routesDocument";
    public static final String ROUTES_LIST = "routesList";
    public static final String ROUTES_FORMAT = "routesFormat";
    public static final String COMPONENT_PROPERTIES = "componentProperties";
    public static final String PROPERTY_VALUES = "propertyValues";
    public static final String RAW_REQUIRED = "rawValuesRequired";
    public static final String DISCOVERED_RAW = "discovered";
    public static final String CONFIGURED_RAW = "configured";
    
    public static final String VANTIQ = "vantiq";
    public static final String GENERAL = "general";
    public static final String COMPONENT_CACHE = "componentCacheDirectory";
    public static final String COMPONENT_CACHE_DEFAULT = "componentCache";
    public static final String COMPONENT_LIB = "componentLibraryDirectory";
    public static final String COMPONENT_LIB_DEFAULT = "componentLib";
    public static final String REPOSITORY_LIST = "repoList";
    public static final String ADDITIONAL_LIBRARIES = "additionalLibraries";
  
    private static String componentCache;
    private static String componentLib;
    
    private CamelRunner currentCamelRunner = null;

    public CamelHandleConfiguration(CamelCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the Camel Source.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map<String, Object>) message.getObject();
        Map<String, Object> config;
        Map<String, Object> vantiq;
        Map<String, Object> camelConfig;
        Map<String, Object> general;
        
        log.debug("Connector got configuration: {}", configObject);
        // Obtain entire config from the message object
        if ( !(configObject.get(CAMEL_CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for Camel Source.");
            failConfig();
            return;
        }
        config = (Map<String, Object>) configObject.get(CAMEL_CONFIG);
        
        // Retrieve the Camel application
        if ( !(config.get(CAMEL_APP) instanceof Map)) {
            log.error("Configuration failed. Configuration must contain '{}' property.", CAMEL_APP);
            failConfig();
            return;
        }
        
        camelConfig = (Map<String, Object>) config.get(CAMEL_APP);
        // Not currently used. vantiq = (Map<String, Object>) config.get(VANTIQ);
        general = (Map<String, Object>) config.get(GENERAL);
        if (general == null) {
            general = new HashMap<>();
        }
        boolean gotList = false;
        Object target = camelConfig.get(ROUTES_DOCUMENT);
        if (!(target instanceof String)) {
            target = camelConfig.get(ROUTES_LIST);
            gotList = true;
        }
        if (!(target instanceof String)) {
            log.error("Camel connector operation requires that the {} section contain either the {} or {} properties.",
                      CAMEL_CONFIG, ROUTES_DOCUMENT, ROUTES_LIST);
            failConfig();
            return;
        }
        
        target = camelConfig.get(ROUTES_FORMAT);
        if (gotList && !(target instanceof String)) {
            log.error("Camel connector with the {} property requires the {} property as well.",
                      ROUTES_LIST, ROUTES_FORMAT);
            failConfig();
            return;
        }
        
        target = camelConfig.get(PROPERTY_VALUES);
        if (target != null) {
            if (!(target instanceof Map)) {
                log.error("Camel connector property {} should be a simple JSON object (found {}).",
                          PROPERTY_VALUES, target.getClass().getName());
                failConfig();
                return;
            }
        }
        
        target = camelConfig.get(RAW_REQUIRED);
        if (target != null) {
            if (!(target instanceof Map)) {
                log.error("Camel connector property {} should be a single JSON object (found {}).",
                          RAW_REQUIRED, target);
                failConfig();
            }
        }
        
        target = camelConfig.get(COMPONENT_PROPERTIES);
        if (target != null) {
            if (!(target instanceof List)) {
                log.error("Camel connector property {} should be a list (found {}).",
                          COMPONENT_PROPERTIES, target.getClass().getName());
                failConfig();
                return;
            } else {
                boolean failed = false;
                for (Object obj : (List<?>) target) {
                    if (!(obj instanceof Map) ||
                            (((Map<?, ?>) obj).get(CamelRunner.COMPONENT_NAME) == null) ||
                            (((Map<?, ?>) obj).get(CamelRunner.COMPONENT_PROPERTIES) == null)) {
                        log.error("Camel connector property {} should be a list of component names and properties.",
                                  COMPONENT_PROPERTIES);
                        failed = true;
                    }
                }
                if (failed) {
                    failConfig();
                    return;
                }
            }
        }
        boolean success;
        try {
            success = runCamelApp(camelConfig, general);
        } catch (VantiqCamelException vce) {
            success = false;
        }
        if (!success) {
            failConfig();
            return;
        }
    
        log.trace("Setup complete");
        configComplete = true;
    }
    
    public boolean runCamelApp(Map<String, Object> camelConfig, Map<String, Object> general)
            throws VantiqCamelException {
        String appName = null;
        try {
            componentCache = (String) general.getOrDefault(COMPONENT_CACHE, COMPONENT_CACHE_DEFAULT);
            componentLib = (String) general.getOrDefault(COMPONENT_LIB, COMPONENT_LIB_DEFAULT);
            //noinspection unchecked
            List<String> repoListRaw = (List<String>) general.get(REPOSITORY_LIST);
            List<URI> repoList = new ArrayList<>();
            if (repoListRaw != null) {
                for (String item : repoListRaw) {
                    // .forEach here has issues with the URISyntaxException
                    repoList.add(new URI(item));
                }
            }
            //noinspection unchecked
            List<String> additionalLibraries = (List<String>) general.get(ADDITIONAL_LIBRARIES);
            appName = (String) camelConfig.getOrDefault(APP_NAME, APP_NAME_DEFAULT);
            String routeDocName = (String) camelConfig.get(ROUTES_DOCUMENT);
            Map<String, String> routeSpec = null;
            if (routeDocName != null) {
                routeSpec = fetchDocument(routeDocName);
                if (routeSpec == null) {
                    log.error("Camel connector operation requires a valid route specification. " +
                                      "No document '{}' was found.",
                              routeDocName);
                    failConfig();
                    return false;
                }
            } else {
                String format = (String) camelConfig.get(ROUTES_FORMAT);
                String routes = null;
                routes = (String) camelConfig.get(ROUTES_LIST);
    
                if (routes != null) {
                    routeSpec = new HashMap<>();
                    routeSpec.put(ROUTES_FORMAT, format);
                    routeSpec.put(ROUTES_LIST, routes);
                } else {
                    log.error("Camel connector requires a valid route specification.");
                    failConfig();
                    return false;
                }
            }
            
            // This is checked in the caller
            //noinspection unchecked
            List<Map<String, Object>> componentProperties =
                    (List<Map<String, Object>>) camelConfig.get(COMPONENT_PROPERTIES);
    
            // This is checked in the caller
            //noinspection unchecked
            Map<String, Object> input = (Map<String, Object>) camelConfig.get(PROPERTY_VALUES);
            //noinspection unchecked
            Map<String, List<String>> raw = (Map<String, List<String>>) camelConfig.get(RAW_REQUIRED);
            List<String> rawRequired = new ArrayList<>();
            // Create a list from the various categories that may be in this set...
            if (raw != null) {
                raw.forEach((k, v) -> {
                    rawRequired.addAll(v);
                });
            }
            // Camel wants these as a Java Properties object, so perform the conversion as required.
            Properties propVals = null;
            if (input != null) {
                propVals = new Properties(input.size());
                for (Map.Entry<String, Object> p : input.entrySet()) {
                    if (p.getValue() != null) {
                        // Assembly mechanism may send nulls for empty values.  Ignore them here.
                        if (rawRequired.contains(p.getKey()) && p.getValue() instanceof String) {
                            String val = (String) p.getValue();
                            // If the caller has already wrapped our values with RAW()/RAW{}, we needn't bother.
                            boolean suppress = (val.startsWith("RAW(") && val.endsWith(")")) ||
                                    (val.startsWith("RAW{") && val.endsWith("}"));
                            if (!suppress || !(val.contains(")") && val.contains("}"))) {
                                String startRaw = "RAW(";
                                String endRaw = ")";
                                if (val.contains(")")) {
                                    if (!val.contains("}")) {
                                        startRaw = "RAW{";
                                        endRaw = "}";
                                    }
                                }
                                propVals.setProperty(p.getKey(), startRaw + val + endRaw);
                            } else {
                                // If we're suppressing or we have a value with both ) & }, leave things alone
                                propVals.setProperty(p.getKey(), p.getValue().toString());
                            }
                        } else {
                            propVals.setProperty(p.getKey(), p.getValue().toString());
                        }
                    }
                }
            }
            log.debug("Properties provided: {}", propVals);
    
            // If we get this far, then we're ready to run start from the new configuration.  If we have an old
            // configuration running, we'll need to shut it down first.
    
            if (currentCamelRunner != null) {
                
                // TODO: Determine if this config change is really a change.  If not, ignore this change and just
                //  continue to run.  The config change could have been just due to some change on Vantiq side about
                //  which a running Camel app doesn't really care. Compute route hash or something to determine if it
                //  changed.
    
                Thread previousThread = currentCamelRunner.getCamelThread();
                currentCamelRunner.close();
                if (previousThread != null) {
                    // Wait for CamelRunner thread to complete
                    previousThread.join();
                }
                currentCamelRunner = null;
            }
    
            // Note:  Cannot use try-with-resource block here.  Since we don't await the run thread, we need to leave
            // the runner open (no auto-close), so make certain that we don't shut things down before we let things run.
            // The connector (or this method, when things are reconfigured -- see a few lines up) will handle closing
            // things as appropriate.
            CamelRunner runner =
                         new CamelRunner(appName, Objects.requireNonNull(routeSpec).get(ROUTES_LIST),
                                         routeSpec.get(ROUTES_FORMAT), repoList,
                                         componentCache, componentLib, componentProperties, propVals);
            if (additionalLibraries != null) {
                runner.setAdditionalLibraries(additionalLibraries);
            }
            runner.runRoutes(false);
            currentCamelRunner = runner;
        } catch (URISyntaxException urie) {
            throw new VantiqCamelException("Invalid repository in " +
                                                   CAMEL_CONFIG + "." + GENERAL + "." + REPOSITORY_LIST, urie);
        } catch (Exception e) {
            throw new VantiqCamelException("Error running Camel App " + appName, e);
        }
        return true;
    }
    
    private Map<String, String> fetchDocument(String docName) {
        String token = source.authToken;
        String url = source.targetVantiqServer;
        Vantiq vantiq = new Vantiq(url, 1);
        vantiq.setAccessToken(token);
        Map<String, String> routeSpec = null;
        
        try {
            VantiqResponse resp = vantiq.selectOne("system." + DOCUMENTS.value(), docName);
            if (resp.isSuccess()) {
                if (resp.getBody() instanceof JsonObject) {
                    JsonObject doc = (JsonObject) resp.getBody();
                    String docContents = doc.get("content").getAsString();
                    log.debug("Fetching contents of document {} from path {}.", docName, docContents);
                    resp = vantiq.download(docContents);
                    if (resp.isSuccess()) {
                        String routeDoc = ((BufferedSource) resp.getBody()).readUtf8();
                        String routeFormat;
                        if (docName.endsWith("xml")) {
                            routeFormat = "xml";
                        } else if (docName.endsWith("yml") || docName.endsWith("yaml")) {
                            routeFormat = "yml";
                        } else {
                            log.error("Unknown format for document {}.", docName);
                            return null;
                        }
                        routeSpec = new HashMap<>();
                        routeSpec.put(ROUTES_FORMAT, routeFormat);
                        routeSpec.put(ROUTES_LIST, routeDoc);
                        return routeSpec;
                    } else {
                        log.error("Could not download contents of document '{}: {}.", docName, resp.getErrors());
                        return null;
                    }
                }
            } else {
                log.error("Cannot fetch document: {}: {}", docName, resp.getErrors());
                return null;
            }
        } catch (IOException e) {
            log.error("Cannot read document '{}' contents.", docName, e);
        } finally {
            // code hygiene -- not used below now, but be safe for the future.
            //noinspection ReassignedVariable
            vantiq = null;
        }
        return null;
    }

    /**
     * Closes the source {@link CamelCore} and marks the configuration as completed. The source will
     * be reactivated when the source reconnects, due either to a Reconnect message (likely created by an update to the
     * configuration document) or to the WebSocket connection crashing momentarily.
     */
    private void failConfig() {
        source.close();
        configComplete = true;
    }
    
    /**
     * Returns whether the configuration handler has completed. Necessary since the sourceConnectionFuture is completed
     * before the configuration can complete, so a program may need to wait before using configured resources.
     * @return  true when the configuration has completed (successfully or not), false otherwise
     */
    public boolean isComplete() {
        return configComplete;
    }
    
    /**
     * Get the current runner.  Useful when poking around in tests...
     * @return
     */
    public CamelRunner getCurrentCamelRunner() {
        return currentCamelRunner;
    }
}
