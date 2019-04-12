/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource.communication.messageHandler;

import java.util.Map;
import javax.jms.Message;
import javax.jms.Session;

import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

/**
 * An interface used to handle incoming and outgoing JMS Messages for all Message Producers/Consumers/Listeners
 */
public interface MessageHandlerInterface {
    
    /**
     * A helper function used to format the provided message according to whatever JMS Message Type was specified
     * @param message           The message to be sent, either a string or a map
     * @param messageFormat     The format (JMS Message Type) of the message to be sent
     * @param session           The JMS Session used to create the JMS Message
     * @return                  The JMS Message that will be sent to the appropriate destination (queue/topic)
     * @throws JMSException
     * @throws UnsupportedJMSMessageTypeException
     */
    Message formatOutgoingMessage(Object message, String messageFormat, Session session) throws Exception;
    
    /**
     * A helper function used to parse the incoming message and format it according to its message type
     * @param message           The retrieved message from the queue
     * @param destName          The destiation, (queue or topic), from which the message arrived
     * @return                  A map containing the message, as well as the queue name and the JMS Message Type
     * @throws JMSException
     * @throws UnsupportedJMSMessageTypeException
     */
    Map<String, Object> parseIncomingMessage(Message message, String destName) throws Exception;
}
