package io.vantiq.extsrc.jmsSource;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.Message;
import javax.jms.Session;

import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;

public class InvalidMessageHandler implements MessageHandlerInterface {

    @Override
    public Message formatOutgoingMessage(Map<String, Object> messageMap, Session session) throws Exception {
        Message message = session.createMessage();
        return message;
    }

    @Override
    public Map<String, Object> parseIncomingMessage(Message message, String destName, boolean isQueue)
            throws Exception {
        return new LinkedHashMap<String, Object>();
    }

}
