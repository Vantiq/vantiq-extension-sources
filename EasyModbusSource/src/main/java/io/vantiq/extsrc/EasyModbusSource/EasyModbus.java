/*
 * Copyright (c) 2018 Vantiq, Inc.
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
import java.sql.SQLException;
import java.sql.Statement;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extsrc.EasyModbusSource.exception.VantiqEasymodbusException;

public class EasyModbus {
    Logger              log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    ModbusClient oClient = new ModbusClient() ;
    private Connection  conn = null;

    // Boolean flag specifying if publish/query requests are handled synchronously, or asynchronously
    boolean isAsync;
    
    int VectorSize = 20 ; 
    
    // Used if asynchronous publish/query handling has been specified
    private HikariDataSource ds = null;

    DateFormat dfTimestamp  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    DateFormat dfDate       = new SimpleDateFormat("yyyy-MM-dd");
    DateFormat dfTime       = new SimpleDateFormat("HH:mm:ss.SSSZ");
    
    public void setupEasyModbus(String tcpAddress, int tcpPort, boolean asyncProcessing, int maxPoolSize) throws VantiqEasymodbusException {
        try {
            oClient.Connect(tcpAddress, tcpPort);
            log.info("EasyModsub trying to connect on {}:{}", tcpAddress, tcpPort);
        }   
        catch (Exception e) {
            log.error("EasyModsub failed to connect on {}:{} - {}", tcpAddress, tcpPort, e);
            reportEasyModbusError(e);
        }
    }

    HashMap[] hanldeSelectCommand(String[] s) throws VantiqEasymodbusException {
        if (!oClient.isConnected()) {
            throw new VantiqEasymodbusException(String.format("EasyDombus is not connected Code %d",3)) ;
        }
        String w = s[3].toLowerCase();

        int index = 0;
        int size = VectorSize;
        if (!s[1].equals("*")) {
            index = Integer.parseInt(s[1].substring(4));
            size = 1;
        }

        try {
            switch (w) {
                case "registers": {
                        int[] rr = oClient.ReadInputRegisters(index, size);
                        InputRegisters r = new InputRegisters();
                        r.Set(rr, index);
                        return r.Get(); 
                    }
                case "holdingregisters":  {
                        int[] rr = oClient.ReadHoldingRegisters(index, size);
                        InputRegisters r = new InputRegisters();
                        r.Set(rr, index);
                        return r.Get(); 
                    }
                case "discrete": {
                        boolean[] rr = oClient.ReadDiscreteInputs(index, size);
                        InputDiscrete r = new InputDiscrete();
                        r.Set(rr, index);
                        return r.Get(); 
                    }
                case "coils": {
                        boolean[] rr = oClient.ReadCoils(index, size);
                        InputDiscrete r = new InputDiscrete();
                        r.Set(rr, index);
                        return r.Get(); 
                    }
                default:
                    throw new VantiqEasymodbusException(String.format("Unsupported Query target %s Code %d",s[4],3)) ;
            }
        }
        catch (Exception e){
            throw new VantiqEasymodbusException(e.getMessage()) ;
        }
    }

    int hanldeUpdateCommand(ExtensionServiceMessage message) throws VantiqEasymodbusException 
    {
        if (!oClient.isConnected()) {
            throw new VantiqEasymodbusException(String.format("EasyDombus is not connected Code %d",10)) ;
        }

        
        int addressInt = 0 ; 
            
        Map<String, ?> request = (Map<String, ?>) message.getObject();
        String type = (String) request.get("type");

        if (!(request.get("address")==null)){
            addressInt = (int) request.get("address");
        }

        List<Map<String,Object>> l = (List<Map<String,Object>>) request.get("body");

        try {
            switch (type) {
                case "holdingregisters": {
                        List<Map<String,Object>> m1  = (List<Map<String,Object>>) l.get(0).get("registers");

                        int[] buffer = new int[m1.size()];
                        for (int i = 0 ; i < m1.size() ; i++)
                        {
                            Map<String,Object> m = m1.get(i);
                            buffer[i] = (int) m.get("value");
                        }

                        oClient.WriteMultipleRegisters(addressInt, buffer);
                        return 0 ; 
                    }
                case "coils": {
                        List<Map<String,Object>> m1  = (List<Map<String,Object>>) l.get(0).get("values");
                        
                        boolean[] buffer = new boolean[m1.size()];
                        for (int i = 0 ; i < m1.size() ; i++) {
                            Map<String,Object> m = m1.get(i);
                            buffer[i] = (boolean) m.get("value");
                        }

                        oClient.WriteMultipleCoils(addressInt, buffer);
                        return 0;
                    }
                default: {
                        throw new VantiqEasymodbusException(String.format("Unsupported Query target %s Code %d",type,14)) ;
                    }
            }
        }
        catch (Exception ex){
            throw new VantiqEasymodbusException(String.format("Exception Raised %s",ex.getMessage(),15)) ;
        }

    }

    public HashMap[]  HandleQuery(String query) throws VantiqEasymodbusException {
        String[] s = query.split(" ");
        return hanldeSelectCommand(s);
    }

    public HashMap[]  HandleQuery(ExtensionServiceMessage message) throws VantiqEasymodbusException {
        Map<String, ?> request = (Map<String, ?>) message.getObject();

        String op = message.getOp();
        switch (op) 
        {
            case "query":
                {
                    String[] s = ((String)request.get("query")).split(" ");
                    return hanldeSelectCommand(s);
                }
            case "publish":
            {
                hanldeUpdateCommand(message);
                return null ; 
            }
            default:
            {
                throw new VantiqEasymodbusException(String.format("Unsupported Query command %s Code %d", op ,3)) ;
            }
        }
    }

    /**
     * The method used to execute the provided query, triggered by a SELECT on the respective source from VANTIQ.
     * @param processQuery          A String representation of the query, retrieved from the WITH clause from VANTIQ.
     * @return                  A HashMap Array containing all of the data retrieved by the query, (empty HashMap 
     *                          Array if nothing was returned)
     * @throws VantiqSQLException
     */

    public HashMap[] processQuery(String query ) throws VantiqEasymodbusException {
        HashMap[] rsArray = null;
        rsArray = HandleQuery(query);
        return rsArray;
    }


     public HashMap[] processQuery(ExtensionServiceMessage message ) throws VantiqEasymodbusException {
            HashMap[] rsArray = null;
        Map<String, ?> request = (Map<String, ?>) message.getObject();

        String sqlQuery = (String) request.get("query");
        rsArray = HandleQuery(message);
        return rsArray;
    }
    
    /**
     * The method used to execute the provided query, triggered by a PUBLISH on the respective VANTIQ source.
     * @param sqlQuery          A String representation of the query, retrieved from the PUBLISH message.
     * @return                  The integer value that is returned by the executeUpdate() method representing the row count.
     * @throws VantiqEasymodbusException
     */
    public int processPublish(ExtensionServiceMessage message) throws VantiqEasymodbusException {
        int publishSuccess = -1;

        Map<String, ?> request = (Map<String, ?>) message.getObject();

        publishSuccess = hanldeUpdateCommand(message);
        return publishSuccess;
    }

    /**
     * The method used to execute the provided list of queries, triggered by a PUBLISH on the respective VANTIQ source. These queries
     * are processed as a batch.
     * @param queryList             The list of queries to be processed as a batch.
     * @return
     * @throws VantiqSQLException
     * @throws ClassCastException
     */
    public int[] processBatchPublish(List queryList) throws VantiqEasymodbusException, ClassCastException {
        int[] publishSuccess = null;

        if (isAsync) {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Adding queries into batch
                for (int i = 0; i < queryList.size(); i++) {
                    stmt.addBatch((String) queryList.get(i));
                }

                // Executing the batch
                publishSuccess = stmt.executeBatch();
            } catch (SQLException e) {
                // Handle errors for EasyModbus
                reportEasyModbusError(e);
            }
        } else {
            // Check that connection hasn't closed
            diagnoseConnection();

            try (Statement stmt = conn.createStatement()) {
                // Adding queries into batch
                for (int i = 0; i < queryList.size(); i++) {
                    stmt.addBatch((String) queryList.get(i));
                }

                // Executing the batch
                publishSuccess = stmt.executeBatch();
            } catch (SQLException e) {
                // Handle errors for EasyModbus
                reportEasyModbusError(e);
            }
        }
        return publishSuccess;
    }
    
    /**
     * Method used to try and reconnect if database connection was lost. Used for synchronous processing (connection pool handles this internally).
     * @throws VantiqSQLException
     */
    public void diagnoseConnection() throws VantiqEasymodbusException {
        try {
            if (!oClient.isConnected()) {
                oClient.Connect();
            }
        } catch (Exception e) {
            // Handle errors for EasyModbus
            reportEasyModbusError(e);
        }
    }
    
    public void reportEasyModbusError(Exception e) throws VantiqEasymodbusException {
        String message = this.getClass().getCanonicalName() + ": A EasyModbus error occurred: " + e.getMessage() +
                ", Error Code: " + e.getCause();
        throw new VantiqEasymodbusException(message);
    }
    
    /**
     * Closes the Client Connection.
     */
    public void close() {
        // Close single connection if open
        try {
            if (oClient.isConnected())
            {
                oClient.Disconnect();
            }
        } catch(IOException e) {
            log.error("A error occurred when closing the Connection: ", e);
        }
    }
}
