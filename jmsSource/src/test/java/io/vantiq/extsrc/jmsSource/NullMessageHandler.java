package io.vantiq.extsrc.jmsSource;

import java.util.Map;

import javax.jms.Message;
import javax.jms.Session;

import io.vantiq.extsrc.jmsSource.communication.messageHandler.MessageHandlerInterface;

public class NullMessageHandler implements MessageHandlerInterface {

    @Override
    public Message formatOutgoingMessage(Object message, String messageFormat, Session session) throws Exception {
        return null;
    }

    @Override
    public Map<String, Object> parseIncomingMessage(Message message, String destName) throws Exception {
        return null;
    }

}
