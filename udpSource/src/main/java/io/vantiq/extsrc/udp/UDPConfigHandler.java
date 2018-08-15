
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.udp;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;

/**
 * Creates publish and notification handlers for any messages relating to a source based on the configuration
 * document
 */
public class UDPConfigHandler  extends Handler<ExtensionServiceMessage> {
    private final Logger log = LoggerFactory.getLogger(ConfigurableUDPSource.class);
    
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        String sourceName = message.getSourceName();
        Map srcConfig = (Map) ((Map)message.getObject()).get("config");
        if (!(srcConfig.get("extSrcConfig") instanceof Map)) {
            log.error("Unable to obtain source configuration for '" + sourceName + "'.");
            return;
        }
        Map config = (Map) srcConfig.get("extSrcConfig");
        
        if ( !(config.get("type") instanceof String) || !config.get("type").equals("udp") ) {
            log.error("Source is not a UDP source.");
            return;
        }
        log.trace("Creating handlers for '" + sourceName + "'");

        // Acquire the general settings Map and the listening port and address
        Map general = null;
        if (config.get("general") instanceof Map) {
            general = (Map) config.get("general");
        }
        int port = getListeningPort(general, sourceName);
        InetAddress address = getListeningAddress(general, sourceName);
        
        Map incoming = null;
        Map outgoing = null;
        if (config.get("incoming") instanceof Map) {
            incoming = (Map) config.get("incoming");
        }
        if (config.get("outgoing") instanceof Map) {
            outgoing = (Map) config.get("outgoing");
        }
        
        if (!isConfiguredToSend(outgoing) && !isConfiguredToReceive(incoming)) {
            log.error("Source '" + sourceName + "' is not configured to send or receive.");
            return;
        }

        // Synchronization necessary because createUDPSocket creates a list which listenOnUDPSocket will add to.
        // If threads change between the creation of the UDP socket and the assignation of the list or while
        // a source is being added to the list, an error is likely to occur
        DatagramSocket socket;
        synchronized (ConfigurableUDPSource.socketLock) {
            // Create the socket that the source will use for UDP messages
            socket = ConfigurableUDPSource.createUDPSocket(port, address, sourceName);
            if (socket == null) { // No socket could be created for the given port and address
                socket = ConfigurableUDPSource.listenOnUDPSocket(port, address, sourceName);
            }
            if (socket == null) {
                log.error("Failed to obtain UDP socket at address '" + address + "' and port '" + port +
                        "' for source '" + sourceName + "'");
                return;
            }
        }

        // Setup Publish handler as the configuration document requests
        if (isConfiguredToSend(outgoing)) {
            UDPPublishHandler handler = new UDPPublishHandler(outgoing, socket);
            ConfigurableUDPSource.clients.get(sourceName).setPublishHandler(handler);
            log.debug("Publish handler created for source '" + sourceName + "'");
        }

        // Setup Notification handler as the configuration document requests
        if (isConfiguredToReceive(incoming)) {
            UDPNotificationHandler handler = new UDPNotificationHandler(incoming, ConfigurableUDPSource.clients.get(sourceName));
            ConfigurableUDPSource.setNotificationHandler(handler, sourceName, incoming);
            log.debug("Notification handler created for source '" + sourceName + "'");
        }
        log.info("Source '" + sourceName + "' setup and ready to go.");
    }

    int getListeningPort(Map general,String sourceName) {
        Object port = null;
        if (general != null) {
            port = general.get("listenPort");
        }
        if (port instanceof Integer && (int) port >= 0 && (int) port <= 65535) {
            return (int) port;
        }
        else {
            log.debug("No valid listening port specified for source '" + sourceName + "'. Using default port '"
                    + ConfigurableUDPSource.LISTENING_PORT + "'.");
            return ConfigurableUDPSource.LISTENING_PORT;
        }
    }

    InetAddress getListeningAddress(Map general, String sourceName) {
        Object address = null;
        if (general != null) {
            address = general.get("listenAddress");
        }

        if (address instanceof String) {
            try {
                return InetAddress.getByName((String) address);
            }
            catch (UnknownHostException e) {
                log.warn("Requested listening address '" + address + "' specified for source '" + sourceName + "'" +
                        "could not be found. Using default address '" + ConfigurableUDPSource.LISTENING_ADDRESS + "'.");
                return ConfigurableUDPSource.LISTENING_ADDRESS;
            }
        }
        else {
            log.debug("No listening address specified for source '" + sourceName + "'. Using default address '" +
                    ConfigurableUDPSource.LISTENING_ADDRESS + "'.");
            return ConfigurableUDPSource.LISTENING_ADDRESS;
        }
    }

    boolean isConfiguredToSend(Map outgoing) {
        if (outgoing == null || !(outgoing.get("targetAddress") instanceof String) ||
                !(outgoing.get("targetPort") instanceof Integer)) {
            return false;
        }
        int port = (Integer) outgoing.get("targetPort");
        String addr = (String) outgoing.get("targetAddress");
        if (port < 0 || port > 65535) {
            return false;
        }
        try {
            InetAddress.getByName(addr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean isConfiguredToReceive(Map incoming) {
        return incoming != null &&
                (ConfigurableUDPSource.valueIsTrue(incoming, "receiveAllPorts") 
                || ConfigurableUDPSource.valueIsTrue(incoming, "receiveAllAddresses") ||
                ConfigurableUDPSource.valueIsTrue(incoming, "passPureMapIn") 
                || ConfigurableUDPSource.valueIsTrue(incoming, "passUnspecifiedIn") ||
                ConfigurableUDPSource.valueIsTrue(incoming,"expectCsvIn") || ConfigurableUDPSource.valueIsTrue(incoming,"expectXmlIn") ||
                incoming.get("passRecAddress") instanceof String || incoming.get("passRecPort") instanceof String ||
                incoming.get("passBytesInAs") instanceof String ||
                (incoming.get("receivePorts") instanceof List  
                        && hasValidPort((List) incoming.get("receivePorts"))) ||
                (incoming.get("receiveAddresses") instanceof List 
                        && hasValidAddress((List) incoming.get("receiveAddresses"))) ||
                (incoming.get("receiveServers") instanceof List 
                        && hasValidServer((List) incoming.get("receiveServers"))) ||
                (incoming.get("transformations") instanceof List 
                        && hasValidTransform((List) incoming.get("transformations"))) || 
                (incoming.get("regexParser") instanceof Map && 
                        isValidRegexParser((Map)incoming.get("regexParser")))
                );
    }
    
    boolean hasValidAddress(List potentialAddresses) {
        for (Object name : potentialAddresses) {
            if (name instanceof String) {
                try {
                    InetAddress.getByName((String) name);
                    return true;
                }
                catch (UnknownHostException e) {
                    
                }
            }
        }
        return false;
    }
    
    boolean hasValidPort(List potentialPorts) {
        for (Object port : potentialPorts) {
            if (port instanceof Integer && (int) port >= 0 && (int) port <= 65535) {
                return true;
            }
        }
        return false;
    }
    
    boolean hasValidServer(List potentialServers) {
        for (Object server : potentialServers) {
            if (ConfigurableUDPSource.isValidServer(server)) {
                List<Object> s = (List) server;
                String addressName = (String) s.get(0);
                try {
                    InetAddress.getByName(addressName);
                    return true;
                }
                catch (UnknownHostException e) {
                    
                }
            }
        }
        return false;
    }
    
    boolean hasValidTransform(List potentialTransformations) {
        for (Object obj : potentialTransformations) {
            if (obj instanceof List) {
                List list = (List) obj;
                if (list.size() == 2 && list.get(0) instanceof String && list.get(1) instanceof String) {
                    return true;
                }
            }
        }
        return false;
    }
    
    boolean isValidRegexParser(Map potentialParser) {
        Pattern p;
        try {
            p = Pattern.compile((String) potentialParser.get("pattern"));
        }
        catch (Throwable t) {
            return false;
        }
        if (potentialParser.get("locations") instanceof List) {
            List locs = (List)potentialParser.get("locations");
            int strings = 0;
            for (Object obj : locs) {
                if (obj instanceof String) {
                    strings++;
                }
            }
            if (strings == p.matcher("").groupCount()) {
                return true;
            }
        }
        return false;
    }
}