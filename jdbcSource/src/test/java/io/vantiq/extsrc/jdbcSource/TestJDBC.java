/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

public class TestJDBC extends TestJDBCBase {
    
    // Queries to be tested
    static final String CREATE_TABLE = "create table Test(id int not null, age int not null, "
            + "first varchar (255), last varchar (255));";
    static final String PUBLISH_QUERY = "INSERT INTO Test VALUES (1, 25, 'Santa', 'Claus');";
    static final String SELECT_QUERY = "SELECT id, first, last, age FROM Test;";
    static final String DELETE_ROW = "DELETE FROM Test WHERE first='Santa';";
    static final String DELETE_TABLE = "DROP TABLE Test;";
    
    // Queries to test oddball types
    static final String CREATE_TABLE_ODD_TYPES = "create table TestTypes(id int, ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
            + " testDate DATE, testTime TIME, testDec decimal(5,2));";
    static final String PUBLISH_QUERY_ODD_TYPES = "INSERT INTO TestTypes VALUES (1, CURRENT_TIMESTAMP, '2018-08-15',"
            + " '9:24:18', 145.86);";
    static final String SELECT_QUERY_ODD_TYPES = "SELECT * FROM TestTypes;";
    static final String DELETE_ROW_ODD_TYPES = "DELETE FROM TestTypes;";
    static final String DELETE_TABLE_ODD_TYPES = "DROP TABLE TestTypes;";
    
    // Queries to test errors
    static final String NO_TABLE = "SELECT * FROM jibberish";
    static final String NO_FIELD = "SELECT jibberish FROM Test";
    static final String SYNTAX_ERROR = "ELECT * FROM Test";
    static final String INSERT_NO_FIELD = "INSERT INTO Test VALUES (1, 25, 'Santa', 'Claus', 'jibberish')";
    static final String INSERT_WRONG_TYPE = "INSERT INTO Test VALUES ('string', 'string', 3, 4)";
    
    DateFormat dfTimestamp  = new SimpleDateFormat("yyyy-dd-mm'T'HH:mm:ss.SSSZ");
    DateFormat dfDate       = new SimpleDateFormat("yyyy-dd-mm");
    DateFormat dfTime       = new SimpleDateFormat("HH:mm:ss.SSSZ");
    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    static final String datePattern = "\\d{4}-\\d{2}-\\d{2}";
    static final String timePattern = "\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    
    static JDBC jdbc;
    
    @Before
    public void setup() { 
        jdbc = new JDBC();
    }
    
    @AfterClass
    public static void tearDown() {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null) {
            try {
                jdbc.processPublish(DELETE_TABLE);
                jdbc.processPublish(DELETE_TABLE_ODD_TYPES);
            } catch (VantiqSQLException e) {
                //Shoudn't throw Exception
            }
        }
        jdbc.close();
    }
    
    @Test
    public void testProcessPublish() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        int queryResult;
        
        // Try processPublish with a nonsense query
        try {
            queryResult = jdbc.processPublish("jibberish");
            fail("Should have thrown an exception");
        } catch (VantiqSQLException e) {
            // Expected behavior
        }
        
        // Create table that will be used for testing
        try {
            queryResult = jdbc.processPublish(CREATE_TABLE);
            assert queryResult == 0;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Insert a row of data into the table
        try {
            queryResult = jdbc.processPublish(PUBLISH_QUERY);
            assert queryResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        jdbc.close();
    }
    
    @Test
    public void testProcessQuery() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        Map<String, ArrayList<HashMap>> queryResult;
        int deleteResult;
        
        // Try processQuery with a nonsense query
        try {
            queryResult = jdbc.processQuery("jibberish");
            fail("Should have thrown exception.");
        } catch (VantiqSQLException e) {
            // Expected behavior
        }
        
        // Select the row that we previously inserted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            assert (Integer) queryResult.get("queryResult").get(0).get("id") == 1;
            assert (Integer) queryResult.get("queryResult").get(0).get("age") == 25;
            assert queryResult.get("queryResult").get(0).get("first").equals("Santa");
            assert queryResult.get("queryResult").get(0).get("last").equals("Claus");
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Delete row from the table
        try {
            deleteResult = jdbc.processPublish(DELETE_ROW);
            assert deleteResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Try selecting again, should return null since row was deleted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            assert queryResult == null;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        jdbc.close();
    }
    
    @Test
    public void testOddTypes() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        Map<String, ArrayList<HashMap>> queryResult;
        int publishResult;
        
        // Create table with odd types including timestamps, dates, times, and decimals
        try {
            publishResult = jdbc.processPublish(CREATE_TABLE_ODD_TYPES);
            assert publishResult == 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
        
        // Insert values into the newly created table
        try {
            publishResult = jdbc.processPublish(PUBLISH_QUERY_ODD_TYPES);
            assert publishResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
        
        // Select the values from the table and make sure the data is retrieved correctly
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY_ODD_TYPES);
            String timestampTest = (String) queryResult.get("queryResult").get(0).get("ts");
            assert timestampTest.matches(timestampPattern);
            String dateTest = (String) queryResult.get("queryResult").get(0).get("testDate");
            assert dateTest.matches(datePattern);
            String timeTest = (String) queryResult.get("queryResult").get(0).get("testTime");
            assert timeTest.matches(timePattern);
            assert ((BigDecimal) queryResult.get("queryResult").get(0).get("testDec")).compareTo(new BigDecimal("145.86")) == 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
        
        // Delete the row of data
        try {
            publishResult = jdbc.processPublish(DELETE_ROW_ODD_TYPES);
            assert publishResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
    }
    
    @Test
    public void testCorrectErrors() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        assumeTrue(testDBURL.contains("mysql"));
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        Map<String, ArrayList<HashMap>> queryResult;
        int publishResult;
        
        // Check error code for selecting from non-existent table
        try {
            queryResult = jdbc.processQuery(NO_TABLE);
            fail("Should have thrown an exception.");
        } catch (VantiqSQLException e) {
            String message = e.getMessage();
            assert message.contains("1146");
        }
        
        // Check error code for selecting non-existent field
        try {
            queryResult = jdbc.processQuery(NO_FIELD);
            fail("Should have thrown an exception.");
        } catch (VantiqSQLException e) {
            String message = e.getMessage();
            assert message.contains("1054");
        }
        
        // Check error code for syntax error
        try {
            queryResult = jdbc.processQuery(SYNTAX_ERROR);
            fail("Should have thrown an exception.");
        } catch (VantiqSQLException e) {
            String message = e.getMessage();
            assert message.contains("1064");
        }
        
        // Check error code for using INSERT with executeQuery() method
        try {
            queryResult = jdbc.processQuery(PUBLISH_QUERY);
            fail("Should have thrown an exception.");
        } catch (VantiqSQLException e) {
            String message = e.getMessage();
            assert message.contains("0");
        }
        
        // Check error code for INSERT not matching columns
        try {
            publishResult = jdbc.processPublish(INSERT_NO_FIELD);
            fail("Should have thrown an exception.");
        } catch (VantiqSQLException e) {
            String message = e.getMessage();
            assert message.contains("1136");
        }
        
        // Check error code for inserting wrong types
        try {
            publishResult = jdbc.processPublish(INSERT_WRONG_TYPE);
            fail("Should have thrown an exception.");
        } catch (VantiqSQLException e) {
            String message = e.getMessage();
            assert message.contains("1366");
        }
        
    }
}
