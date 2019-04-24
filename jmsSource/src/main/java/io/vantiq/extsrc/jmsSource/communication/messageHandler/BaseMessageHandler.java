package io.vantiq.extsrc.jmsSource.communication.messageHandler;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
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
public class BaseMessageHandler implements MessageHandlerInterface {
    
    public static final String MESSAGE = "Message";
    public static final String TEXT = "TextMessage";
    public static final String MAP = "MapMessage";
    
    @Override
    public Message formatOutgoingMessage(Map<String, Object> messageMap, Session session) throws Exception {
        Map<String, Object> headers = (Map) messageMap.get("headers");
        Map<String, Object> properties = (Map) messageMap.get("properties");
        String messageFormat;
        
        // If JMSType was not specified, try to guess the message type based on the message itself
        if (headers != null && headers.get("JMSType") instanceof String) {
            messageFormat = (String) headers.get("JMSType");
        } else {
            if (messageMap.get("message") instanceof String) {
                messageFormat = TEXT;
            } else if (messageMap.get("message") instanceof Map) {
                messageFormat = MAP;
            } else {
                messageFormat = MESSAGE;
            }
        }
        
        switch(messageFormat) {
            case MESSAGE:
                Message baseMessage = session.createMessage();
                return setHeadersAndProperties(session, baseMessage, headers, properties);
            case TEXT:
                TextMessage textMessage = session.createTextMessage();
                String msg = (String) messageMap.get("message");
                textMessage.setText(msg);
                return setHeadersAndProperties(session, textMessage, headers, properties);
            case MAP:
                MapMessage mapMessage = session.createMapMessage();
                Map msgMap = (Map) messageMap.get("message");;
                Iterator it = msgMap.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry)it.next();
                    mapMessage.setObject((String) pair.getKey(), pair.getValue());
                    it.remove();
                }
                return setHeadersAndProperties(session, mapMessage, headers, properties);
            default:
                throw new UnsupportedJMSMessageTypeException(messageFormat);
        }
    }
    
    /**
     * A helper method to formatOutgoingMessage, which sets the JMS Message Headers and Properties, if they were specified
     * @param session           The JMS Session used to create a queue or topic
     * @param message           The Message that will be sent to the appropriate destination
     * @param headers           The headers as specified in the publish parameters
     * @param properties        The properties as specified in the publish parameters
     * @return                  The message with all headers and properties set accordingly
     * @throws JMSException 
     */
    protected Message setHeadersAndProperties(Session session, Message message, Map<String, Object> headers, Map<String, Object> properties) throws JMSException {
        // Set the JMS Message Headers if they were specified
        if (headers != null) {
            if (headers.get("JMSCorrelationID") instanceof String) {
                message.setJMSCorrelationID((String) headers.get("JMSCorrelationID"));
            }
            if (headers.get("JMSReplyTo") instanceof Map) {
                Map jmsReply = (Map) headers.get("JMSReplyTo");
                if (jmsReply.get("queue") instanceof String) {
                    Destination destination = session.createQueue((String) jmsReply.get("queue"));
                    message.setJMSReplyTo(destination);
                } else if (jmsReply.get("topic") instanceof String) {
                    Destination destination = session.createTopic((String) jmsReply.get("queue"));
                    message.setJMSReplyTo(destination);
                }
            }
            if (headers.get("JMSType") instanceof String) {
                message.setJMSType((String) headers.get("JMSType"));
            }
        }
        
        // Set the JMS Message Properties if they were specified
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                message.setObjectProperty(entry.getKey(), entry.getValue());
            }
        }
        
        return message;
    }
    
    @Override
    public Map<String, Object> parseIncomingMessage(Message message, String destName, boolean isQueue) throws Exception {
        Map<String, Object> msgMap = new LinkedHashMap<String, Object>();
        Map<String, Object> headers = new LinkedHashMap<String, Object>();
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        
        // Getting the JMS Message Body
        if (message instanceof TextMessage) {
            // Extract the string message
            String msgText = ((TextMessage) message).getText();
            msgMap.put("message", msgText);
        } else if (message instanceof MapMessage) {
            // Get the map names, and then extract the data accordingly
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            Enumeration<?> en = ((MapMessage) message).getMapNames();
            while (en.hasMoreElements()) {
              String key = (String) en.nextElement();
              map.put(key, ((MapMessage) message).getObject(key));
            }
            msgMap.put("message", map);
        } else if (message instanceof BytesMessage || message instanceof StreamMessage || message instanceof ObjectMessage) {
            // Throw exception for unsupported message types
            throw new UnsupportedJMSMessageTypeException(message.getJMSType());
        } else {
            // Send null as message since the message has no payload
            msgMap.put("message", null);
        }
        
        // Iterating through JMS Message Properties
        if (message != null) {
            Enumeration msgProperties = message.getPropertyNames();
            while (msgProperties.hasMoreElements()) {
                String propertyName = (String)msgProperties.nextElement();
                properties.put(propertyName, message.getObjectProperty(propertyName));
            }
            
            // Getting the JMS Message Headers
            if (message.getJMSDestination() != null) {
                headers.put("JMSDestination", message.getJMSDestination().toString());
            }
            if (message.getJMSReplyTo() != null) {
                headers.put("JMSReplyTo", message.getJMSReplyTo().toString());
            }
            headers.put("JMSDeliveryMode", message.getJMSDeliveryMode());
            headers.put("JMSExpiration", message.getJMSExpiration());
            headers.put("JMSPriority", message.getJMSPriority());
            headers.put("JMSMessageID", message.getJMSMessageID());
            headers.put("JMSTimestamp", message.getJMSTimestamp());
            headers.put("JMSCorrelationID", message.getJMSCorrelationID());
            headers.put("JMSType", message.getJMSType());
            headers.put("JMSRedelivered", message.getJMSRedelivered());
        }
            
        if (isQueue) {
            msgMap.put("queue", destName);
        } else {
            msgMap.put("topic", destName);
        }
        
        msgMap.put("headers", headers);
        msgMap.put("properties", properties);
        
        return msgMap;
    }

}
