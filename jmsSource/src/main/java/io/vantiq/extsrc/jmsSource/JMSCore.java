/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.jmsSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extjsdk.Handler;
import io.vantiq.extsrc.jmsSource.exceptions.DestinationNotConfiguredException;
import io.vantiq.extsrc.jmsSource.exceptions.UnsupportedJMSMessageTypeException;

/**
 * Controls the connection and interaction with the Vantiq server. Initialize it and call start() and it will run
 * itself. start() will return a boolean describing whether or not it succeeded, and will wait up to 10 seconds if
 * necessary.
 */
public class JMSCore {

    // Map used to make sure only one InitialContextFactory is being used
    Map<String, String> initialContextMap = new ConcurrentHashMap<String, String>();
    
    // Variables for server configuration
    String sourceName;
    String authToken;
    String targetVantiqServer;

    // Set as the client's config handler
    JMSHandleConfiguration jmsConfigHandler;

    // Used to connect to/communicate with VANTIQ
    ExtensionWebSocketClient client = null;
    
    // Used to coordinate communication with the JMS Server
    JMS jms = null;

    final Logger log;
    final static int RECONNECT_INTERVAL = 5000;
    final static int CONNECTION_TIMEOUT = 10;
    
    private static final String SYNCH_LOCK = "synchLock";

    /**
     * Stops sending messages to the source and tries to reconnect, closing on a failure
     */
    public final Handler<ExtensionServiceMessage> reconnectHandler = new Handler<ExtensionServiceMessage>() {
        @Override
        public void handleMessage(ExtensionServiceMessage message) {
            log.trace("Reconnect message received. Reinitializing configuration");

            jmsConfigHandler.configComplete = false;

            CompletableFuture<Boolean> success = client.connectToSource();

            try {
                if ( !success.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS) ) {
                    if (!client.isOpen()) {
                        log.error("Failed to connect to server url '" + targetVantiqServer + "'.");
                    } else if (!client.isAuthed()) {
                        log.error("Failed to authenticate within 10 seconds using the given authentication data.");
                    } else {
                        log.error("Failed to connect within 10 seconds");
                    }
                    close();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("Could not reconnect to source within 10 seconds: ", e);
                close();
            }
        }
    };

    /**
     * Stops sending messages to the source and tries to reconnect indefinitely
     */
    public final Handler<ExtensionWebSocketClient> closeHandler = new Handler<ExtensionWebSocketClient>() {
        @Override
        public void handleMessage(ExtensionWebSocketClient message) {
            log.trace("WebSocket closed unexpectedly. Attempting to reconnect");

            jmsConfigHandler.configComplete = false;

            boolean sourcesSucceeded = false;
            while (!sourcesSucceeded) {
                client.initiateFullConnection(targetVantiqServer, authToken);
                sourcesSucceeded = exitIfConnectionFails(client, CONNECTION_TIMEOUT);
                if (!sourcesSucceeded) {
                    try {
                        Thread.sleep(RECONNECT_INTERVAL);
                    } catch (InterruptedException e) {
                        log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                    }
                }
            }
        }
    };

    /**
     * Creates a new JMSCore with the settings given.
     * @param sourceName            The name of the source to connect to.
     * @param authToken             The authentication token to use to connect.
     * @param targetVantiqServer    The url to connect to.
     */
    public JMSCore(String sourceName, String authToken, String targetVantiqServer) {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + '#' + sourceName);
        this.sourceName = sourceName;
        this.authToken = authToken;
        this.targetVantiqServer = targetVantiqServer;
    }

    /**
     * Returns the name of the source that it is connected to.
     * @return  The name of the source that it is connected to.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Tries to connect to a source and waits up to {@code timeout} seconds before failing and trying again.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping.
     * @return          true if the source connection succeeds, (will retry indefinitely and never return false).
     */
    public boolean start(int timeout) {
        boolean sourcesSucceeded = false;
        while (!sourcesSucceeded) {
            client = new ExtensionWebSocketClient(sourceName);
            jmsConfigHandler = new JMSHandleConfiguration(this);

            client.setConfigHandler(jmsConfigHandler);
            client.setReconnectHandler(reconnectHandler);
            client.setCloseHandler(closeHandler);
            client.initiateFullConnection(targetVantiqServer, authToken);

            sourcesSucceeded = exitIfConnectionFails(client, timeout);
            if (!sourcesSucceeded) {
                try {
                    Thread.sleep(RECONNECT_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("An error occurred when trying to sleep the current thread. Error Message: ", e);
                }
            }
        }
        return true;
    }
    
    /**
     * Called by the publishHandler. Used to send a message to the JMS Destination (topic or queue). Message is sent
     * using whatever format was specified in the JMSFormat parameter (as part of the publish message), and defaults 
     * to ObjectMessage if none was specified.
     * @param message   The Publish message
     */
    public void sendJMSMessage(ExtensionServiceMessage message) {
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        
        // Get local copy of JMS
        JMS localJMS;
        synchronized (SYNCH_LOCK) {
            localJMS = jms;
        }
        
        // Make sure JMS is safe to use (has not been closed)
        if (localJMS == null) {
            log.error("JMS connection closed before operation could complete");
        } else {
            Object msg;
            String dest;
            String msgFormat;
            boolean isQueue;
            
            // Getting the contents of the message if specified, or defaulting to an empty string
            if (request.get("message") instanceof String) {
                msg = (String) request.get("message");
            } else if (request.get("message") instanceof Map) {
                msg = (Map) request.get("message");
            } else {
                msg = null;
                log.debug("No message was specified in the publish request, or the message was not a String/Map. "
                        + "The message was set to its default value, null.");
            }
                
            // Getting the destination of the message
            if (request.get("queue") instanceof String) {
                dest = (String) request.get("queue");
                isQueue = true;
            } else if (request.get("topic") instanceof String) {
                dest = (String) request.get("topic");
                isQueue = false;
            } else {
                log.error("No destination was specified, or destination was not a String. Either a topic "
                        + "or a queue must be included as a String in the publish request.");
                return;
            }
            
            // Getting the message format if it was specified, or defaulting to Message
            if (request.get("JMSFormat") instanceof String) {
                msgFormat = (String) request.get("JMSFormat");
            } else {
                log.debug("No JMSFormat was specified, the default Message message type will be used.");
                msgFormat = "Message";
            }
            
            // Sending the message to the appropriate destination
            try {
                localJMS.produceMessage(msg, dest, msgFormat, isQueue);
            } catch (JMSException e) {
                log.error("An error occured when attempting to send the given message.", e);
            } catch (DestinationNotConfiguredException e) {
                log.error("An error occured when attempting to send the given message. The source was not configured "
                        + "to send messages to the following destination: " + dest + ".", e);
            } catch(UnsupportedJMSMessageTypeException e) {
                log.error("An error occured when attempting to send the given message. The provided JMS Message Type: "
                        + e.getMessage() + " is either invalid or not currently supported.");
            } catch (Exception e) {
                log.error("An unexpected error occured when attempting to send the given message.", e);
            }
        }
    }
    
    /**
     * Called by the queryHandler. Used to read the next available message from the specified JMS Queue. The message is
     * converted to JSON, and sent back to VANTIQ as a queryResponse.
     * @param message   The Query message
     */
    public void readQueueMessage(ExtensionServiceMessage message) {
      Map<String, ?> request = (Map<String, ?>) message.getObject();
      String replyAddress = ExtensionServiceMessage.extractReplyAddress(message);
      
      // Get local copy of JMS
      JMS localJMS;
      synchronized (SYNCH_LOCK) {
          localJMS = jms;
      }
      
      // Make sure JMS is safe to use (has not been closed)
      if (localJMS == null) {
          if (client != null) {
              client.sendQueryError(replyAddress, this.getClass().getName() + ".closed",
                      "JMS connection closed before operation could complete.", null);
          } else {
              log.error("JMS connection closed before operation could complete.");
          }
      } else {
          // Retrieve most recent message from specified queue. If no queue name is specified, or if exception is thrown, return query error.
          if (request.get("queue") instanceof String) {
              String queue = (String) request.get("queue");
              try {
                  Map<String, Object> messageMap = localJMS.consumeMessage(queue);
                  client.sendQueryResponse(200, replyAddress, messageMap);
              } catch (JMSException e) {
                  client.sendQueryError(replyAddress, JMSException.class.getCanonicalName(), 
                          "Failed to read message from the queue: " + queue + ". Error message was: " + e.getMessage(), null);
              } catch (DestinationNotConfiguredException e) {
                  client.sendQueryError(replyAddress, DestinationNotConfiguredException.class.getCanonicalName(), 
                          "Failed to read message from the queue: " + queue + ". The source was not configured to read from "
                                  + "this queue.", null);
              } catch(UnsupportedJMSMessageTypeException e) {
                  client.sendQueryError(replyAddress, UnsupportedJMSMessageTypeException.class.getCanonicalName(), 
                          "Failed to read message from the queue: " + queue + ". The incoming JMS Message Type was: " + e.getMessage()
                                  + ", which is not currently supported.", null);
              } catch (Exception e) {
                  client.sendQueryError(replyAddress, Exception.class.getCanonicalName(), 
                          "An unexpected error occured when reading message from queue: " + queue + ". Error message was: " 
                          + e.getMessage(), null);
              }
          } else {
              client.sendQueryError(replyAddress, this.getClass().getName() + ".noQueue", 
                      "No queue was specified as a query parameter. Query cannot be completed.", null);
          }
      }
    }

    /**
     * Closes all resources held by this program except for the {@link ExtensionWebSocketClient}.
     */
    public void close() {
        synchronized (SYNCH_LOCK) {
            if (jms != null) {
                jms.close();
                jms = null;
            }
        }
    }

    /**
     * Closes all resources held by this program and then closes the connection.
     */
    public void stop() {
        close();
        if (client != null && client.isOpen()) {
            client.stop();
            client = null;
        }
    }

    /**
     * Waits for the connection to succeed or fail, logs and exits if the connection does not succeed within
     * {@code timeout} seconds.
     *
     * @param client    The client to watch for success or failure.
     * @param timeout   The maximum number of seconds to wait before assuming failure and stopping
     * @return          true if the connection succeeded, false if it failed to connect within {@code timeout} seconds.
     */
    public boolean exitIfConnectionFails(ExtensionWebSocketClient client, int timeout) {
        boolean sourcesSucceeded = false;
        try {
            sourcesSucceeded = client.getSourceConnectionFuture().get(timeout, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            log.error("Timeout: full connection did not succeed within {} seconds: {}", timeout, e);
        }
        catch (Exception e) {
            log.error("Exception occurred while waiting for webSocket connection", e);
        }
        if (!sourcesSucceeded) {
            log.error("Failed to connect to all sources.");
            if (!client.isOpen()) {
                log.error("Failed to connect to server url '" + targetVantiqServer + "'.");
            } else if (!client.isAuthed()) {
                log.error("Failed to authenticate within " + timeout + " seconds using the given authentication data.");
            } else {
                log.error("Failed to connect within 10 seconds");
            }
            return false;
        }
        return true;
    }
}
