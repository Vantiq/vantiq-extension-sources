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
 * An interface used to handle incoming and outgoing JMS Messages for all Message Producers/Consumers/Listeners.
 */
public interface MessageHandlerInterface {
    
    /**
     * A helper function used to format the provided message according to whatever JMS Message Type was specified.
     * This method MUST return a valid Message that can be sent to the appropriate destination.
     * @param messageMap        The map of the message to be sent, containing message headers, properties, and body
     * @param session           The JMS Session used to create the JMS Message
     * @return                  The JMS Message that will be sent to the appropriate destination (queue/topic)
     * @throws JMSException
     * @throws UnsupportedJMSMessageTypeException
     */
    Message formatOutgoingMessage(Map<String, Object> messageMap, Session session) throws Exception;
    
    /**
     * A helper function used to parse the incoming message and format it according to its message type.
     * This method MUST return a map with a "destination" and "headers" field. If possible, the map should also have
     * a "properties" and "message" field, (unless these values are null).
     * @param message           The retrieved message from the queue
     * @param destName          The destination, (queue or topic), from which the message arrived
     * @param isQueue           A boolean flag specifying if the message arrived from a queue or a topic
     * @return                  A map containing the message's origin (queue or topic), body, headers, and properties 
     *                          (if they exist)
     * @throws JMSException
     * @throws UnsupportedJMSMessageTypeException
     */
    Map<String, Object> parseIncomingMessage(Message message, String destName, boolean isQueue) throws Exception;
}
