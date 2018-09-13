/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.imageRetriever.ImageRetrieverResults;

public class JDBCConfigHandler extends Handler<ExtensionServiceMessage> {
    Logger                  log;
    String                  sourceName;
    JDBCCore                source;
    boolean                 configComplete = false;
    
    Map<String, ?> lastDataSource = null;
    
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
                    client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.invalidImageRequest", 
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
}
