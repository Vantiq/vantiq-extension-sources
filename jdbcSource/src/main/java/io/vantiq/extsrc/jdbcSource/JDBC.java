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
    
    
    public void setupJDBC(String jdbcDriver, String dbURL, String username, String password) 
            throws SQLException, LinkageError, ClassNotFoundException {        
        try {
            // Register JDBC driver
            Class.forName(jdbcDriver);
            
            // Open a connection
            conn = DriverManager.getConnection(dbURL,username,password);
        
            // Create statement used to execute query
            stmt = conn.createStatement();
            
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
    
    public ResultSet processQuery(String sqlQuery) throws SQLException{
        try {
            rs = stmt.executeQuery(sqlQuery);
            return rs;
            
        } catch(SQLException e){
            // Handle errors for JDBC
            log.error("A database error occured: ", e);
        }
        rs.close();
        return null;
    }
    
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

