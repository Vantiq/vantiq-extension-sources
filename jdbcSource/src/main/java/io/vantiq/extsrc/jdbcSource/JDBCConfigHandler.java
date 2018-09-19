/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.jdbcSource.JDBCCore;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverInterface;
import io.vantiq.extsrc.objectRecognition.neuralNet.NeuralNetInterface;

/**
 * Sets up the source using the configuration document, which looks as below.
 *<pre> {
 *      objRecConfig: {
 *          general: {
 *              &lt;general options&gt;
 *          }
 *      }
 * }</pre>
 * 
 * The options for general are as follows. At least one must be valid for the source to function:
 * <ul>
 *      <li>{@code pollRate}: This indicates how often an image should be captured. A positive number
 *                      represents the number of milliseconds between captures. If the specified time is less than
 *                      the amount of time it takes to process the image then images will be taken as soon as the
 *                      previous finishes. If this is set to ero, the next image will be captured as soon as the
 *                      previous is sent.
 *      <li>{@code allowQueries}: This option allows Queries to be received when set to {@code true}
 *                      
 * </ul>
 * 
 * Most options for dataSource and neuralNet are dependent on the implementation of {@link ImageRetrieverInterface} and
 * {@link NeuralNetInterface} specified through the {@code type} option. {@code type} is the fully qualified class name
 * of the implementation. It can also be unset, in which case it will attempt to find {@code DefaultRetriever} and 
 * {@code DefaultProcessor}, which will be written by you, for your specific needs. {@code type} can also be set to the
 * implementations included in the standard package: {@code file} for FileRetriever, {@code camera} for 
 * CameraRetriever, {@code ftp} for FtpRetriever, and {@code network} for NetworkStreamRetriever for the dataSource
 * config; and {@code yolo} for YoloProcessor for the neuralNet config.
 */

public class JDBCConfigHandler extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    JDBCCore                source;
    boolean                 configComplete = false;
    
    Map<String, ?> lastGeneral = null;
    
    Handler<ExtensionServiceMessage> queryHandler;
    
    public JDBCConfigHandler(JDBCCore source) {
        this.source = source;
        this.sourceName = source.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
        queryHandler = new Handler<ExtensionServiceMessage>() {
            ExtensionWebSocketClient client = source.client;
            
            @Override
            public void handleMessage(ExtensionServiceMessage message) {
                // Should never happen, but just in case something changes in the backend
                if ( !(message.getObject() instanceof Map) ) {
                    String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.JDBC.invalidQueryRequest", 
                            "Request must be a map", null);
                }
                
                // Read, process, and send the image
                ImageRetrieverResults data = source.retrieveImage(message);
                if (data != null) {
                    source.sendDataFromImage(data, message);
                }
            }
        };
    }
    
    /**
     * Interprets the configuration message sent by the Vantiq server and sets up the neural network and data retriever.
     */
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> config = (Map) message.getObject();
        Map<String, Object> general;
        
        // Obtain the Maps for each object
        if ( !(config.get("config") instanceof Map && ((Map)config.get("config")).get("jdbcConfig") instanceof Map) ) {
            log.error("No configuration suitable for an objectRecognition. Waiting for valid config...");
            failConfig();
            return;
        }
        config = (Map) ((Map) config.get("config")).get("jdbcConfig");
        
        if ( !(config.get("general") instanceof Map)) {
            log.error("No general options specified. Waiting for valid config...");
            failConfig();
            return;
        }
        general = (Map) config.get("general");
        
        // Only create a new data source if the config changed, to save time and state
        if (lastGeneral != null && general.equals(lastGeneral)) {
            log.info("config unchanged, keeping previous");
        } else {
            boolean success = createDBConnection(general);
            if (!success) {
                return; // Exit if the image retriever could not be created. Closing taken care of by createImageRetriever()
            }
        }
        
        // Start listening for queries as requested by the config
        source.client.setQueryHandler(queryHandler);
        
        log.info("Setup complete");
        
        configComplete = true;
    }
    
    /**
     * Attempts to create an image retriever based on the configuration document.
     * @param dataSourceConfig  The configuration for the image retriever
     * @return                  true if the requested image retriever could be created, false otherwise
     */
    boolean createDBConnection(Map<String, ?> generalConfig) {
        // Null the last config so if it fails it will know the last failed
        lastGeneral = null;
        
        // Figure out where to receive the data from
        // Initialize to default in case no type was given
        String dbDriver; 
        if (generalConfig.get("driver") instanceof String) {
            dbDriver = (String) generalConfig.get("driver");
        } else {
            log.debug("No db driver was specified");
            failConfig();
            return false;
        }
        
//        // Create the image retriever
//        ImageRetrieverInterface ir = getImageRetriever(retrieverType);
//        if (ir == null) {
//            return false; // Error message and exiting taken care of by getImageRetriever()
//        }
//        
//        // Setup the image retriever
//        try {
//            ir.setupDataRetrieval(dataSourceConfig, source);
//            source.imageRetriever = ir;
//        } catch (Exception e) {
//            log.error("Exception occurred while setting up image retriever.", e);
//            failConfig();
//            return false;
//        }
//        
//        // Only save the last config if the creation succeeded.
//        lastGeneral = generalConfig;
//        
        log.info("DB Connection created");
//        log.debug("Image retriever class is {}", retrieverType);
        return true;
    }
    
    /**
     * Closes the source {@link ObjectRecognitionCore} and marks the configuration as completed. The source will
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
}
