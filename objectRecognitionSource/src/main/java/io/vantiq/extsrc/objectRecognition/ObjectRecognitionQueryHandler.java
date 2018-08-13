package io.vantiq.extsrc.objectRecognition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.objectRecognition.exception.*;

public class ObjectRecognitionQueryHandler extends Handler<ExtensionServiceMessage> {

    ObjectRecognitionCore source;
    ExtensionWebSocketClient client;
    Logger log;
    
    public ObjectRecognitionQueryHandler(ObjectRecognitionCore source, ExtensionWebSocketClient client) {
        this.source = source;
        this.client = client;
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + source.getSourceName());
    }
    
    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
        if ( !(message.getObject() instanceof Map) ) {
            client.sendQueryError(replyAddress, "io.vantiq.extsrc.objectRecognition.InvalidImageRequest", 
                    "Request must be a map", null);
        }
        
        Map<String,?> request = (Map) message.getObject();
        byte[] image;
        List<Map> data;
        
        try {
            image = source.imageRetriever.getImage(request);
        } catch (ImageAcquisitionException e) {
            log.error("Could not obtain image.", e);
            log.debug("Request was: " + request);
            client.sendQueryError(replyAddress, ImageAcquisitionException.class.getCanonicalName(), 
                    "Failed to obtain an image with request {0}", new Object[] {request});
            return;
        } catch (FatalImageException e) {
            log.error("Fatal error occurred trying to obtain image.", e);
            log.debug("Request was: " + request);
            client.sendQueryError(replyAddress, FatalImageException.class.getCanonicalName() + ".acquisition", 
                    "Fatally failed to obtain an image with request {0}", new Object[] {request});
            source.stop();
            return;
        }
        
        try {
            data = source.neuralNet.processImage(image);
        } catch (ImageProcessingException e) {
            log.error("Could not obtain image.", e);
            log.debug("Request was: " + request);
            client.sendQueryError(replyAddress, ImageProcessingException.class.getCanonicalName(), 
                    "Failed to process the image obtained with request {0}", new Object[] {request});
            return;
        } catch (FatalImageException e) {
            log.error("Fatal error occurred trying to process image.", e);
            log.debug("Request was: " + request);
            client.sendQueryError(replyAddress, FatalImageException.class.getCanonicalName() + ".processing", 
                    "Fatally failed to process the image obtained with request {0}", new Object[] {request});
            source.stop();
            return;
        }
        
        if (data.isEmpty()) {
            client.sendQueryResponse(204, replyAddress, new LinkedHashMap<>());
        } else {
            client.sendQueryResponse(200, replyAddress, data.toArray(new Map[0]));
        }
    }
    
}
