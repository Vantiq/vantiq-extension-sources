package io.vantiq.extsrc.jmsSource.communication.messageHandler;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

/**
 * The default implementation of the MessageHandlerInterface. Setup to handle 3 JMS Message Types: Message, TextMessage, 
 * and MapMessage. All other message types will throw exceptions which are handled accordingly.
 */
public class DefaultMessageHandler implements MessageHandlerInterface {
    
    public static final String MESSAGE = "Message";
    public static final String TEXT = "TextMessage";
    public static final String MAP = "MapMessage";
    
    @Override
    public Message formatOutgoingMessage(Object message, String messageFormat, Session session) throws Exception {
        switch(messageFormat) {
            case MESSAGE:
                Message baseMessage = session.createMessage();
                return baseMessage;
            case TEXT:
                TextMessage textMessage = session.createTextMessage();
                String msg = (String) message;
                textMessage.setText(msg);
                return textMessage;
            case MAP:
                MapMessage mapMessage = session.createMapMessage();
                Map msgMap = (Map) message;
                Iterator it = msgMap.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry)it.next();
                    mapMessage.setObject((String) pair.getKey(), pair.getValue());
                    it.remove();
                }
                return mapMessage;
            default:
                throw new UnsupportedJMSMessageTypeException(messageFormat);
        }
    }

    @Override
    public Map<String, Object> parseIncomingMessage(Message message, String destName) throws Exception {
        Map<String, Object> msgMap = new LinkedHashMap<String, Object>();
        
        if (message instanceof TextMessage) {
            // Extract the string message
            String msgText = ((TextMessage) message).getText();
            msgMap.put("message", msgText);
            msgMap.put("JMSFormat", TEXT);
        } else if (message instanceof MapMessage) {
            // Get the map names, and then extract the data accordingly
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            Enumeration<?> en = ((MapMessage) message).getMapNames();
            while (en.hasMoreElements()) {
              String key = (String) en.nextElement();
              map.put(key, ((MapMessage) message).getObject(key));
            }
            msgMap.put("message", map);
            msgMap.put("JMSFormat", MAP);
        } else if (message instanceof BytesMessage || message instanceof StreamMessage || message instanceof ObjectMessage) {
            // Throw exception for unsupported message types
            throw new UnsupportedJMSMessageTypeException(message.getJMSType());
        } else {
            // Send null as message since the message has no payload
            msgMap.put("message", null);
            msgMap.put("JMSFormat", MESSAGE);
        }
                
        msgMap.put("destination", destName);
        return msgMap;
    }

}
