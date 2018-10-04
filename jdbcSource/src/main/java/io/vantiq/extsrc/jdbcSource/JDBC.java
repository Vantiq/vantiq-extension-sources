package io.vantiq.extsrc.jdbcSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBC {
    Logger                  log  = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    private Connection      conn = null;
    private Statement       stmt = null;
    private ResultSet       rs   = null;    
    
    /**
     * The method used to setup the connection to the SQL Database, using the values retrieved from the source config.
     * @param jdbcDriver    The JDBC Driver Class to be used to connect to the SQL Database.
     * @param dbURL         The Database URL to be used to connect to the SQL Database.    
     * @param username      The username to be used to connect to the SQL Database.
     * @param password      The password to be used to connect to the SQL Database.
     * @throws SQLException
     * @throws LinkageError
     * @throws ClassNotFoundException
     */
    public void setupJDBC(String jdbcDriver, String dbURL, String username, String password) 
            throws SQLException, LinkageError, ClassNotFoundException {        
        try {
            // Register JDBC driver
            Class.forName(jdbcDriver);
            
            // Open a connection
            conn = DriverManager.getConnection(dbURL,username,password);
            
        } catch (SQLException e) {
            // Handle errors for JDBC
            log.error("A database error occured: ", e);
        } catch(LinkageError e){
            // Handle errors for Class.forName and also handles ExceptionInInitializerError 
            log.error("A JDBC Driver error occured: ", e);
        } catch (ClassNotFoundException e) {
            // Handle errors for Class.forName
            log.error("JDBC Driver class was not found: ", e);
        }
    }
    
    /**
     * The method used to execute the provided query, triggered by a SELECT on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the WITH clause from VANTIQ.
     * @return                  The ResultSet that is returned by the executeQuery() method, or null if an exception
     *                          was caught.
     * @throws SQLException
     */
    public ResultSet processQuery(String sqlQuery) throws SQLException{
        try {
            if (stmt!=null) {
                stmt.close();
            }
            // Create statement used to execute query
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sqlQuery);
            return rs;
            
        } catch(SQLException e){
            // Handle errors for JDBC
            log.error("A database error occured: ", e);
        }
        if (rs!=null) {
            rs.close();
        }
        if (stmt!=null) {
            stmt.close();
        }
        return null;
    }
    
    /**
     * The method used to execute the provided query, triggered by a PUBLISH on the respective source from VANTIQ.
     * @param sqlQuery          A String representation of the query, retrieved from the PUBLISH message.
     * @return                  The integer value that is returned by the executeUpdate() method representing the row count,
     *                          or 0 if an exception was caught.
     * @throws SQLException
     */
    public int processPublish(String sqlQuery) throws SQLException{
        try {
            // Create statement used to execute query
            stmt = conn.createStatement();
            int publishSuccess = stmt.executeUpdate(sqlQuery);
            stmt.close();
            return publishSuccess;
            
        } catch(SQLException e){
            // Handle errors for JDBC
            log.error("A database error occured: ", e);
        }
        if (stmt!=null) {
            stmt.close();
        }
        return 0;
    }
    
    /**
     * Closes the SQL ResultSet, Statement and Connection.
     */
    public void close() {
        try {
            if (rs!=null) {
                rs.close();
            }
            if (stmt!=null) {
                stmt.close();
            }
            if (conn!=null) {
                conn.close();
            }
        } catch(SQLException e) {
            log.error("A database error occured: ", e);
        }
    }
}

