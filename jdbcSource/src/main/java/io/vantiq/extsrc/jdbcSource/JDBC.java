/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

public class JDBC {
    Logger              log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    private Connection  conn = null;
    private Statement   stmt = null;
    private ResultSet   rs   = null;    
    
    DateFormat dfTimestamp  = new SimpleDateFormat("yyyy-dd-mm'T'HH:mm:ss.SSSZ");
    DateFormat dfDate       = new SimpleDateFormat("yyyy-dd-mm");
    DateFormat dfTime       = new SimpleDateFormat("HH:mm:ss.SSSZ");
    
    /**
     * The method used to setup the connection to the SQL Database, using the values retrieved from the source config.
     * @param dbURL         The Database URL to be used to connect to the SQL Database.    
     * @param username      The username to be used to connect to the SQL Database.
     * @param password      The password to be used to connect to the SQL Database.
     * @throws SQLException
     * @throws VantiqSQLException 
     */
    public void setupJDBC(String dbURL, String username, String password) throws VantiqSQLException {        
        try {
            // Open a connection
            conn = DriverManager.getConnection(dbURL,username,password);
            
        } catch (SQLException e) {
            // Handle errors for JDBC
            reportSQLError(e);
        } 
    }
    
    /**
     * The method used to execute the provided query, triggered by a SELECT on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the WITH clause from VANTIQ.
     * @return                  A Map containing all of the data retrieved by the query, (null if nothing was returned)
     * @throws SQLException
     */
    public Map<String, ArrayList<HashMap>> processQuery(String sqlQuery) throws VantiqSQLException {
        Map<String, ArrayList<HashMap>> rsMap = null;
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sqlQuery)) {
            this.stmt = stmt;
            this.rs = rs;
            rsMap = createMapFromResults(rs);           
        } catch (SQLException e) {
            // Handle errors for JDBC
            reportSQLError(e);
        } 
        return rsMap;
    }
    
    /**
     * The method used to execute the provided query, triggered by a PUBLISH on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the PUBLISH message.
     * @return                  The integer value that is returned by the executeUpdate() method representing the row count.
     * @throws SQLException
     */
    public int processPublish(String sqlQuery) throws VantiqSQLException {
        int publishSuccess = -1;
        try (Statement stmt = conn.createStatement()) {
            this.stmt = stmt;
            publishSuccess = stmt.executeUpdate(sqlQuery);
            
        } catch (SQLException e) {
            // Handle errors for JDBC
            reportSQLError(e);
        } 
        return publishSuccess;
    }
    
    /**
     * Method used to create a map out of the output ResultSet. Map is needed in order to send the data back to VANTIQ
     * @param queryResults   A ResultSet containing return value from executeQuery()
     * @return               The map containing a key/value pair where key = "queryResult" and
     *                       value = an ArrayList of maps each representing one row of the ResultSet,
     *                       (null if the ResultSet was empty).
     * @throws SQLException
     */
    Map<String, ArrayList<HashMap>> createMapFromResults(ResultSet queryResults) throws VantiqSQLException {
        Map<String, ArrayList<HashMap>> map = new LinkedHashMap<>();
        try {
            if (!queryResults.next()) { 
                return null;
            } else {
                queryResults.beforeFirst();
                ArrayList<HashMap> rows = new ArrayList<HashMap>();
                ResultSetMetaData md = queryResults.getMetaData(); 
                int columns = md.getColumnCount();
                
                // Iterate over rows of Result Set and create a map for each row
                while(queryResults.next()) {
                    HashMap row = new HashMap(columns);
                    for (int i=1; i<=columns; ++i) {
                        // Check column type to retrieve data in appropriate manner
                        int columnType = md.getColumnType(i);
                        switch (columnType) {
                            case java.sql.Types.DECIMAL:
                                row.put(md.getColumnName(i), queryResults.getBigDecimal(i));
                                break;
                            case java.sql.Types.DATE:
                                Date rowDate = queryResults.getDate(i);
                                row.put(md.getColumnName(i), dfDate.format(rowDate));
                                break;
                            case java.sql.Types.TIME:
                                Time rowTime = queryResults.getTime(i);
                                row.put(md.getColumnName(i), dfTime.format(rowTime));
                                break;
                            case java.sql.Types.TIMESTAMP:
                                Timestamp rowTimestamp = queryResults.getTimestamp(i);
                                row.put(md.getColumnName(i), dfTimestamp.format(rowTimestamp));
                                break;
                            default:
                                // If none of the initial cases are met, the data will be converted to a String via getObject()
                                row.put(md.getColumnName(i), queryResults.getObject(i));
                                break;
                        }
                    }
                    // Add each row map to the list of rows
                    rows.add(row);
                }
                
                // Put list of maps as value to the key "queryResult"
                map.put("queryResult", rows);
            }
        } catch (SQLException e) {
            reportSQLError(e);
        }
        return map;
    }
    
    /**
     * Method used to throw the VantiqSQLException whenever is necessary
     * @param e The SQLException caught by the calling method
     * @throws VantiqSQLException
     */
    public void reportSQLError(SQLException e) throws VantiqSQLException {
        String message = this.getClass().getCanonicalName() + ": A database error occurred: " + e.getMessage() +
                " SQL State: " + e.getSQLState() + ", Error Code: " + e.getErrorCode();
        throw new VantiqSQLException(message);
    }
    
    /**
     * Calls the close functions for the SQL ResultSet, Statement, and Connection.
     */
    public void close() {
        closeResultSet();
        closeStatement();
        closeConnection();
    }
    
    /**
     * Closes the SQL ResultSet.
     */
    public void closeResultSet() {
        try {
            if (rs!=null) {
                rs.close();
            }
        } catch(SQLException e) {
            log.error("A error occurred when closing the ResultSet: ", e);
        }
    }
    
    /**
     * Closes the SQL Statement.
     */
    public void closeStatement() {
        try {
            if (stmt!=null) {
                stmt.close();
            }
        } catch(SQLException e) {
            log.error("A error occurred when closing the Statement: ", e);
        }
    }
    
    /**
     * Closes the SQL Connection.
     */
    public void closeConnection() {
        try {
            if (conn!=null) {
                conn.close();
            }
        } catch(SQLException e) {
            log.error("A error occurred when closing the Connection: ", e);
        }
    }
}

