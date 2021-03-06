/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import de.re.easymodbus.modbusclient.*;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.sql.Connection;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extsrc.EasyModbusSource.exception.VantiqEasyModbusException;

/**
 * Responsible for the connection with the EasyModbus server and triggering it
 * with queries and updates accepted from Vantiq or polling as defind in the
 * extension source configuration
 * 
 * there are 4 different object which can be read ( using select ) :
 * registers,Holdingregisters , coils,discrete but only two of those are valid
 * for update : Holdingregisters and coils
 */
public class EasyModbus {
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    ModbusClient oClient = new ModbusClient();
    private Connection conn = null;

    int vectorSize = 20;

    // Used if asynchronous publish/query handling has been specified
    private HikariDataSource ds = null;

    DateFormat dfTimestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    DateFormat dfDate = new SimpleDateFormat("yyyy-MM-dd");
    DateFormat dfTime = new SimpleDateFormat("HH:mm:ss.SSSZ");

    public void setupEasyModbus(String tcpAddress, int tcpPort, boolean asyncProcessing, int maxPoolSize)
            throws VantiqEasyModbusException {
        try {
            oClient.Connect(tcpAddress, tcpPort);
            log.info("EasyModsub trying to connect on {}:{}", tcpAddress, tcpPort);
        } catch (Exception e) {
            log.error("EasyModsub failed to connect on {}:{} - {}", tcpAddress, tcpPort, e);
            reportEasyModbusError(e,1000);
        }
    }

    /**
     * Handling select statement received from Vantiq or from polling configuration
     * 
     * @param s statment .
     * @return HashMap contains the result .
     * @throws VantiqEasyModbusException
     */
    HashMap[] handleSelectCommand(String[] s) throws VantiqEasyModbusException {
        if (!oClient.isConnected()) {
            throw new VantiqEasyModbusException(String.format("EasyModbus is not connected Code %d", 1000));
        }
        String w = s[3].toLowerCase();

        int index = 0;
        int size = vectorSize;
        if (!s[1].equals("*")) {
            if (!s[1].substring(0, 4).toLowerCase().equals("item")) {
                throw new VantiqEasyModbusException(String
                        .format("Unsupported Query Field %s  must start with item (ex. item0)  Code %d", s[1], 1007));
            }

            try {
                index = Integer.parseInt(s[1].substring(4));
            } catch (Exception ex) {
                throw new VantiqEasyModbusException(String
                        .format("Unsupported Query Field %s  must contain offeset (ex. Coil0)  Code %d", s[1], 1004));
            }
            size = 1;
        }

        try {
            switch (w) {
                case "registers": {
                    int[] rr = oClient.ReadInputRegisters(index, size);
                    InputRegisters r = new InputRegisters();
                    r.set(rr, index);
                    return r.get();
                }
                case "holdingregisters": {
                    int[] rr = oClient.ReadHoldingRegisters(index, size);
                    InputRegisters r = new InputRegisters();
                    r.set(rr, index);
                    return r.get();
                }
                case "discrete": {
                    boolean[] rr = oClient.ReadDiscreteInputs(index, size);
                    InputDiscrete r = new InputDiscrete();
                    r.set(rr, index);
                    return r.get();
                }
                case "coils": {
                    boolean[] rr = oClient.ReadCoils(index, size);
                    InputDiscrete r = new InputDiscrete();
                    r.set(rr, index);
                    return r.get();
                }
                default: {
                    throw new VantiqEasyModbusException(
                            String.format("Unsupported Query Field target %s Code %d", s[3], 1006));
                }
            }
        } catch (Exception e) {
            throw new VantiqEasyModbusException(e.getMessage());
        }
    }

    /**
     * Handle Vantiq Publish command which converted to update holdingregisters or
     * coils
     * 
     * @param request request received from Vantiq
     * @return HashMap contains the result .
     * @throws VantiqEasyModbusException
     */
    int handleUpdateCommand(Map<String, ?> request) throws VantiqEasyModbusException {
        int addressInt = 0;

        String type = (String) request.get("type");

        if (!(request.get("address") == null)) {
            addressInt = (Integer) request.get("address");
        }

        List<Map<String, Object>> l = (List<Map<String, Object>>) request.get("body");

        try {
            switch (type) {
                case "holdingregisters": {
                    List<Map<String, Object>> m1 = (List<Map<String, Object>>) l.get(0).get("registers");

                    int[] buffer = new int[m1.size()];
                    for (int i = 0; i < m1.size(); i++) {
                        Map<String, Object> m = m1.get(i);
                        buffer[i] = (int) m.get("value");
                    }

                    oClient.WriteMultipleRegisters(addressInt, buffer);
                    return 0;
                }
                case "coils": {
                    List<Map<String, Object>> m1 = (List<Map<String, Object>>) l.get(0).get("values");

                    boolean[] buffer = new boolean[m1.size()];
                    for (int i = 0; i < m1.size(); i++) {
                        Map<String, Object> m = m1.get(i);
                        buffer[i] = (boolean) m.get("value");
                    }

                    oClient.WriteMultipleCoils(addressInt, buffer);
                    return 0;
                }
                default: {
                    throw new VantiqEasyModbusException(
                            String.format("Unsupported Query target %s Code %d", type, 1002));
                }
            }
        } catch (Exception ex) {
            throw new VantiqEasyModbusException(String.format("Exception Raised %s", ex.getMessage(), 1001));
        }
    }

    /**
     * maintain the connectivity with the EasyModbus server and handle Vantiq
     * Publish command which converted to update holdingregisters or coils
     * 
     * 
     * @param message message received from Vantiq
     * @return HashMap contains the result .
     * @throws VantiqEasyModbusException
     */
    int handleUpdateCommand(ExtensionServiceMessage message) throws VantiqEasyModbusException {
        try {
            if (!oClient.isConnected()) {
                throw new VantiqEasyModbusException(String.format("EasyDombus is not connected Code %d", 1000));
            }
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            return handleUpdateCommand(request);
        } catch (Exception ex) {
            throw new VantiqEasyModbusException(String.format("Exception Raised %s", ex.getMessage(), 1001));
        }
    }

    /**
     * Handle Vantiq select command
     * 
     * @param query statement received from Vantiq
     * @return HashMap contains the result .
     * @throws VantiqEasyModbusException
     */
    public HashMap[] handleQuery(String query) throws VantiqEasyModbusException {
        String[] s = query.split(" ");
        if (s[0].toLowerCase().equals("select")) {
            return handleSelectCommand(s);
        } else
            throw new VantiqEasyModbusException(String.format("Unsupported Query Syntax %s Code %d", s[0], 1005));
    }

    /**
     * Handle Vantiq commands
     * 
     * @param message statement received from Vantiq
     * @return HashMap contains the result .
     * @throws VantiqEasyModbusException
     */
    public HashMap[] handleQuery(ExtensionServiceMessage message) throws VantiqEasyModbusException {
        Map<String, ?> request = (Map<String, ?>) message.getObject();

        String op = message.getOp();
        switch (op) {
            case "query": {
                String[] s = ((String) request.get("query")).split(" ");
                return handleSelectCommand(s);
            }
            case "publish": {
                handleUpdateCommand(message);
                return null;
            }
            default: {
                throw new VantiqEasyModbusException(
                        String.format("Unsupported operation command %s Code %d", op, 1003));
            }
        }
    }

    /**
     * The method used to execute the provided query, triggered by a SELECT on the
     * respective source from VANTIQ.
     * 
     * @param query A String representation of the query, retrieved from the
     *                     WITH clause from VANTIQ.
     * @return A HashMap Array containing all of the data retrieved by the query,
     *         (empty HashMap Array if nothing was returned)
     * @throws VantiqEasyModbusException
     */
    public HashMap[] processQuery(String query) throws VantiqEasyModbusException {
        HashMap[] rsArray = null;
        rsArray = handleQuery(query);
        return rsArray;
    }

    /**
     * The method used to execute the provided query, triggered by a SELECT on the
     * respective source from VANTIQ.
     * 
     * @param message
     * @return A HashMap Array containing all of the data retrieved by the query,
     *         (empty HashMap Array if nothing was returned)
     * @throws VantiqEasyModbusException
     */
    public HashMap[] processQuery(ExtensionServiceMessage message) throws VantiqEasyModbusException {
        HashMap[] rsArray = null;
        Map<String, ?> request = (Map<String, ?>) message.getObject();

        String sqlQuery = (String) request.get("query");
        rsArray = handleQuery(message);
        return rsArray;
    }

    /**
     * The method used to execute the provided query, triggered by a PUBLISH on the
     * respective VANTIQ source.
     * 
     * @param message A String representation of the query, retrieved from the
     *                 PUBLISH message.
     * @return The integer value that is returned by the executeUpdate() method
     *         representing the row count.
     * @throws VantiqEasyModbusException
     */
    public int processPublish(ExtensionServiceMessage message) throws VantiqEasyModbusException {
        int publishSuccess = -1;

        Map<String, ?> request = (Map<String, ?>) message.getObject();

        publishSuccess = handleUpdateCommand(message);
        return publishSuccess;
    }

    /**
     * Method used to try and reconnect if EasyModbus server connection was lost.
     * Used for synchronous processing (connection pool handles this internally).
     * 
     * @throws VantiqEasyModbusException
     */
    public void diagnoseConnection() throws VantiqEasyModbusException {
        try {
            if (!oClient.isConnected()) {
                oClient.Connect();
            }
        } catch (Exception e) {
            // Handle errors for EasyModbus
            reportEasyModbusError(e,1000);
        }
    }

    /**
     * Generate log entry based on EasyModbus exception
     * 
     * @param e
     * @throws VantiqEasyModbusException
     */
    public void reportEasyModbusError(Exception e) throws VantiqEasyModbusException {
        String message = this.getClass().getCanonicalName() + ": A EasyModbus error occurred: " + e.getMessage()
                + ", Error Code: " + e.getCause();
        throw new VantiqEasyModbusException(message);
    }

    /**
     * Generate log entry based on EasyModbus exception and supplied error code. 
     * 
     * @param e
     * @param errorCode
     * @throws VantiqEasyModbusException
     */
    public void reportEasyModbusError(Exception e,int errorCode) throws VantiqEasyModbusException {
        String message = this.getClass().getCanonicalName() + ": A EasyModbus error occurred: " + e.getMessage()
                + ", Error Code: " + errorCode;
        throw new VantiqEasyModbusException(message);
    }

    /**
     * Closes the Client Connection.
     */
    public void close() {
        // Close single connection if open
        try {
            if (oClient.isConnected()) {
                oClient.Disconnect();
            }
        } catch (IOException e) {
            log.error("A error occurred when closing the Connection: ", e);
        }
    }
}
