/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

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
import java.util.List;
import org.json.JSONObject;
import org.json.XML;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

public class JDBC {
    Logger              log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    private Connection  conn = null;

    // Boolean flag specifying if publish/query requests are handled synchronously, or asynchronously
    boolean isAsync;
    
    // Used to reconnect if necessary
    private String dbURL;
    private String username;
    private String password;
    
    // Timeout (in seconds) used to check if connection is still valid
    private static final int CHECK_CONNECTION_TIMEOUT = 5;

    // Timeout (in milliseconds) specifying how long ds.getConnection() will wait for a connection before timing out
    private static final int CONNECTION_POOL_TIMEOUT = 5000;

    // Used if asynchronous publish/query handling has been specified
    private HikariDataSource ds = null;

    DateFormat dfTimestamp  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    DateFormat dfDate       = new SimpleDateFormat("yyyy-MM-dd");
    DateFormat dfTime       = new SimpleDateFormat("HH:mm:ss.SSSZ");
    
    /**
     * The method used to setup the connection to the SQL Database, using the values retrieved from the source config.
     * @param dbURL             The Database URL to be used to connect to the SQL Database.
     * @param username          The username to be used to connect to the SQL Database.
     * @param password          The password to be used to connect to the SQL Database.
     * @param asyncProcessing   A boolean flag specifying if publish/query requests are handled synchronously, or asynchronously.
     * @param maxPoolSize       An integer representing the maxPoolSize for the Connection Pool.
     * @throws VantiqSQLException 
     */
    public void setupJDBC(String dbURL, String username, String password, boolean asyncProcessing, int maxPoolSize) throws VantiqSQLException {
        try {
            if (asyncProcessing) {
                // Create a connection pool
                HikariConfig connectionPoolConfig = new HikariConfig();
                connectionPoolConfig.setJdbcUrl(dbURL);
                if (username != null) {
                    connectionPoolConfig.setUsername(username);
                }
                if (password != null) {
                    connectionPoolConfig.setPassword(password);
                }
                ds = new HikariDataSource(connectionPoolConfig);
                ds.setConnectionTimeout(CONNECTION_POOL_TIMEOUT);

                // Setting max pool size (should always match number of active threads for publish and query)
                ds.setMaximumPoolSize(maxPoolSize);
            } else {
                // Open a single connection
                conn = DriverManager.getConnection(dbURL,username,password);
            }
            
            // Save login credentials for reconnection if necessary
            this.dbURL = dbURL;
            this.username = username;
            this.password = password;
            this.isAsync = asyncProcessing;
        } catch (SQLException e) {
            // Handle errors for JDBC
            reportSQLError(e);
        } 
    }
    
    /**
     * The method used to execute the provided query, triggered by a SELECT on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the WITH clause from VANTIQ.
     * @return                  A HashMap Array containing all of the data retrieved by the query, (empty HashMap 
     *                          Array if nothing was returned)
     * @throws VantiqSQLException
     */
    public HashMap[] processQuery(String sqlQuery) throws VantiqSQLException {
        HashMap[] rsArray = null;

        if (isAsync) {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlQuery)) {
                rsArray = createMapFromResults(rs);
            } catch (SQLException e) {
                // Handle errors for JDBC
                reportSQLError(e);
            }
        } else {
            // Check that connection hasn't closed
            diagnoseConnection();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlQuery)) {
                rsArray = createMapFromResults(rs);
            } catch (SQLException e) {
                // Handle errors for JDBC
                reportSQLError(e);
            }
        }

        return rsArray;
    }
    
    /**
     * The method used to execute the provided query, triggered by a PUBLISH on the respective VANTIQ source.
     * @param sqlQuery          A String representation of the query, retrieved from the PUBLISH message.
     * @return                  The integer value that is returned by the executeUpdate() method representing the row count.
     * @throws VantiqSQLException
     */
    public int processPublish(String sqlQuery) throws VantiqSQLException {
        int publishSuccess = -1;

        if (isAsync) {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                publishSuccess = stmt.executeUpdate(sqlQuery);
            } catch (SQLException e) {
                // Handle errors for JDBC
                reportSQLError(e);
            }
        } else {
            // Check that connection hasn't closed
            diagnoseConnection();

            try (Statement stmt = conn.createStatement()) {
                publishSuccess = stmt.executeUpdate(sqlQuery);
            } catch (SQLException e) {
                // Handle errors for JDBC
                reportSQLError(e);
            }
        }

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
    public int[] processBatchPublish(List queryList) throws VantiqSQLException, ClassCastException {
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
                // Handle errors for JDBC
                reportSQLError(e);
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
                // Handle errors for JDBC
                reportSQLError(e);
            }
        }

        return publishSuccess;
    }
    
    /**
     * Method used to create a map out of the output ResultSet. Map is needed in order to send the data back to VANTIQ
     * @param queryResults   A ResultSet containing return value from executeQuery()
     * @return               A HashMap Array containing all of the rows from the ResultSet, each converted to a HashMap,
     *                       (or an empty HashMap Array if the ResultSet was empty).
     * @throws VantiqSQLException
     */
    HashMap[] createMapFromResults(ResultSet queryResults) throws VantiqSQLException {
        ArrayList<HashMap> rows = new ArrayList<HashMap>();
        try {
            if (!queryResults.next()) { 
                return rows.toArray(new HashMap[rows.size()]);
            } else {
                ResultSetMetaData md = queryResults.getMetaData(); 
                int columns = md.getColumnCount();
                
                // Iterate over rows of Result Set and create a map for each row
                do {
                    HashMap row = new HashMap(columns);
                    for (int i=1; i<=columns; ++i) {
                        // Check column type to retrieve data in appropriate manner
                        int columnType = md.getColumnType(i);
                        switch (columnType) {
                            case java.sql.Types.DECIMAL:
                                if (queryResults.getBigDecimal(i) != null) {
                                    row.put(md.getColumnName(i), queryResults.getBigDecimal(i));
                                }
                                break;
                            case java.sql.Types.DATE:
                                Date rowDate = queryResults.getDate(i);
                                if (rowDate != null) {
                                    row.put(md.getColumnName(i), dfDate.format(rowDate));
                                }
                                break;
                            case java.sql.Types.TIME:
                                Time rowTime = queryResults.getTime(i);
                                if (rowTime != null) {
                                    row.put(md.getColumnName(i), dfTime.format(rowTime));
                                }
                                break;
                            case java.sql.Types.TIMESTAMP:
                                Timestamp rowTimestamp = queryResults.getTimestamp(i);
                                if (rowTimestamp != null) {
                                    row.put(md.getColumnName(i), dfTimestamp.format(rowTimestamp));
                                }
                                break;
                            default:
                                {
                                    // If none of the initial cases are met, the data will be converted to a String via getObject()
                                    String name = md.getColumnName(i) ; 
                                    if (name.contains("XML")){

                                        if(queryResults.getObject(i) != null) {
                                            JSONObject json = XML.toJSONObject((String)queryResults.getObject(i)); // converts xml to json
                                            String jsonPrettyPrintString = json.toString(); // json pretty print
                                            row.put(md.getColumnName(i), jsonPrettyPrintString);
                                        }
                    
                                    } else {
                                        if(queryResults.getObject(i) != null) {
                                            row.put(md.getColumnName(i), queryResults.getObject(i));
                                    }
                                }
                            }
                                break;
                        }
                    }
                    // Add each row map to the list of rows
                    rows.add(row);
                } while(queryResults.next());
            }
        } catch (SQLException e) {
            reportSQLError(e);
        }
        HashMap[] rowsArray = rows.toArray(new HashMap[rows.size()]);
        return rowsArray;
    }
    
    /**
     * Method used to try and reconnect if database connection was lost. Used for synchronous processing (connection pool handles this internally).
     * @throws VantiqSQLException
     */
    public void diagnoseConnection() throws VantiqSQLException {
        try {
            if (!conn.isValid(CHECK_CONNECTION_TIMEOUT)) {
                conn = DriverManager.getConnection(dbURL,username,password);
            }
        } catch (SQLException e) {
            // Handle errors for JDBC
            reportSQLError(e);
        }
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
     * Closes the SQL Connection.
     */
    public void close() {
        // Close single connection if open
        try {
            if (conn!=null) {
                conn.close();
            }
        } catch(SQLException e) {
            log.error("A error occurred when closing the Connection: ", e);
        }
        // Close connection pool if open
        if (ds != null) {
            ds.close();
        }
    }
}

