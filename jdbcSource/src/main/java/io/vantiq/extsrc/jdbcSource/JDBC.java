package io.vantiq.extsrc.jdbcSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBC {
    Logger              log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    private Connection  conn = null;
    private Statement   stmt = null;
    private ResultSet   rs   = null;    
    
    /**
     * The method used to setup the connection to the SQL Database, using the values retrieved from the source config.
     * @param dbURL         The Database URL to be used to connect to the SQL Database.    
     * @param username      The username to be used to connect to the SQL Database.
     * @param password      The password to be used to connect to the SQL Database.
     * @throws SQLException
     */
    public void setupJDBC(String dbURL, String username, String password) throws SQLException {        
        try {
            // Open a connection
            conn = DriverManager.getConnection(dbURL,username,password);
            
        } catch (SQLException e) {
            // Handle errors for JDBC
            throw new SQLException(this.getClass().getCanonicalName() + ": A database error occurred: " + e.getMessage(), e);
        } 
    }
    
    /**
     * The method used to execute the provided query, triggered by a SELECT on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the WITH clause from VANTIQ.
     * @return                  A Map containing all of the data retrieved by the query, (null if nothing was returned)
     * @throws SQLException
     */
    public Map<String, ArrayList<HashMap>> processQuery(String sqlQuery) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sqlQuery)) {
            this.stmt = stmt;
            this.rs = rs;
            Map<String, ArrayList<HashMap>> rsMap = createMapFromResults(rs);
            return rsMap;
            
        } catch(SQLException e) {
            // Handle errors for JDBC
            throw new SQLException(this.getClass().getCanonicalName() + ": A database error occurred: " + e.getMessage(), e);
        } 
    }
    
    /**
     * The method used to execute the provided query, triggered by a PUBLISH on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the PUBLISH message.
     * @return                  The integer value that is returned by the executeUpdate() method representing the row count.
     * @throws SQLException
     */
    public int processPublish(String sqlQuery) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            this.stmt = stmt;
            int publishSuccess = stmt.executeUpdate(sqlQuery);
            return publishSuccess;
            
        } catch(SQLException e) {
            // Handle errors for JDBC
            throw new SQLException(this.getClass().getCanonicalName() + ": A database error occurred: " + e.getMessage(), e);
        } 
    }
    
    /**
     * 
     * @param queryResults   A ResultSet containing return value from executeQuery()
     * @return               The map containing a key/value pair where key = "queryResult" and
     *                       value = an ArrayList of maps each representing one row of the ResultSet,
     *                       (null if the ResultSet was empty).
     * @throws SQLException
     */
    Map<String, ArrayList<HashMap>> createMapFromResults(ResultSet queryResults) throws SQLException {
        if (!queryResults.next()) { 
            return null;
        } else {
            queryResults.beforeFirst();
            Map<String, ArrayList<HashMap>> map = new LinkedHashMap<>();
            ArrayList<HashMap> rows = new ArrayList<HashMap>();
            ResultSetMetaData md = queryResults.getMetaData(); 
            int columns = md.getColumnCount();
            
            // Iterate over rows of Result Set and create a map for each row
            while(queryResults.next()) {
                HashMap row = new HashMap(columns);
                for (int i=1; i<=columns; ++i) {
                    row.put(md.getColumnName(i), queryResults.getObject(i));
                }
                // Add each row map to the list of rows
                rows.add(row);
            }
            
            // Put list of maps as value to the key "queryResult"
            map.put("queryResult", rows);
            return map;
        }
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

