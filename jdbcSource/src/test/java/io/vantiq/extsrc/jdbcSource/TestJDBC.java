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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

public class TestJDBC extends TestJDBCBase {
    
    // Queries to be tested
    static final String CREATE_TABLE = "create table Test(id int not null, age int not null, "
            + "first varchar (255), last varchar (255));";
    static final String PUBLISH_QUERY = "INSERT INTO Test VALUES (1, 25, 'Santa', 'Claus');";
    static final String SELECT_QUERY = "SELECT id, first, last, age FROM Test;";
    static final String DELETE_ROW = "DELETE FROM Test WHERE first='Santa';";
    static final String DELETE_TABLE = "DROP TABLE Test;";
    
    // Date values used to test oddball types
    static final String TIMESTAMP = "2018-08-15 9:24:18";
    static final String DATE = "2018-08-15";
    static final String TIME = "9:24:18";
    static final String FORMATTED_TIMESTAMP = "2018-08-15T09:24:18.000-0700";
    static final String FORMATTED_TIME = "09:24:18.000-0800";
    static final String VANTIQ_FORMATTED_TIMESTAMP = "2018-08-15T16:24:18Z";
    
    // Queries to test oddball types
    static final String CREATE_TABLE_EXTENDED_TYPES = "create table TestTypes(id int, ts TIMESTAMP, testDate DATE, "
            + "testTime TIME, testDec decimal(5,2));";
    static final String PUBLISH_QUERY_EXTENDED_TYPES = "INSERT INTO TestTypes VALUES (1, '" + TIMESTAMP + "', '" + DATE + "',"
            + " '" + TIME + "', 145.86);";
    static final String SELECT_QUERY_EXTENDED_TYPES = "SELECT * FROM TestTypes;";
    static final String DELETE_ROW_EXTENDED_TYPES = "DELETE FROM TestTypes;";
    static final String DELETE_TABLE_EXTENDED_TYPES = "DROP TABLE TestTypes;";
    
    // Queries to test errors
    static final String NO_TABLE = "SELECT * FROM jibberish";
    static final String NO_FIELD = "SELECT jibberish FROM Test";
    static final String SYNTAX_ERROR = "ELECT * FROM Test";
    static final String INSERT_NO_FIELD = "INSERT INTO Test VALUES (1, 25, 'Santa', 'Claus', 'jibberish')";
    static final String INSERT_WRONG_TYPE = "INSERT INTO Test VALUES ('string', 'string', 3, 4)";
    
    // Queries to test DateTime format in VANTIQ
    static final String CREATE_TABLE_DATETIME = "CREATE TABLE Test(ts TIMESTAMP);";
    static final String INSERT_VALUE_DATETIME = "INSERT INTO Test VALUES ('" + TIMESTAMP + "');";
    static final String QUERY_TABLE_DATETIME = "SELECT * FROM Test";
    
    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    static final String datePattern = "\\d{4}-\\d{2}-\\d{2}";
    static final String timePattern = "\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    
    static final String SOURCE_NAME = "testSourceName";
    static final String TYPE_NAME = "testTypeName";
    static final int CORE_START_TIMEOUT = 10;
    
    static JDBCCore core;
    static JDBC jdbc;
    static Vantiq vantiq;
    
    @Before
    public void setup() { 
        jdbc = new JDBC();
        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }
    
    /* Uncomment this BeforeClass when debugging to ensure tables have been properly deleted.
    
    @BeforeClass
    public void debugSetup() {
        tearDown();
    }
    
    */
    
    @AfterClass
    public static void tearDown() {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null) {
            // Delete first table
            try {
                jdbc.processPublish(DELETE_TABLE);
            } catch (VantiqSQLException e) {
                // Shoudn't throw Exception
            }
            
            // Delete second table
            try {
                jdbc.processPublish(DELETE_TABLE_EXTENDED_TYPES);
            } catch (VantiqSQLException e) {
                // Shoudn't throw Exception
            }
        }
        if (core != null) {
            core.close();
            core = null;
        }
        if (jdbc != null) {
            jdbc.close();
            jdbc = null;
        }
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
        
        HashMap[] queryResult;
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
            assert (Integer) queryResult[0].get("id") == 1;
            assert (Integer) queryResult[0].get("age") == 25;
            assert queryResult[0].get("first").equals("Santa");
            assert queryResult[0].get("last").equals("Claus");
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
    public void testExtendedTypes() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        HashMap[] queryResult;
        int publishResult;
        
        // Create table with odd types including timestamps, dates, times, and decimals
        try {
            publishResult = jdbc.processPublish(CREATE_TABLE_EXTENDED_TYPES);
            assert publishResult == 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
        
        // Insert values into the newly created table
        try {
            publishResult = jdbc.processPublish(PUBLISH_QUERY_EXTENDED_TYPES);
            assert publishResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
        
        // Select the values from the table and make sure the data is retrieved correctly
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY_EXTENDED_TYPES);
            String timestampTest = (String) queryResult[0].get("ts");
            assert timestampTest.matches(timestampPattern);
            assert timestampTest.equals(FORMATTED_TIMESTAMP);
            String dateTest = (String) queryResult[0].get("testDate");
            assert dateTest.matches(datePattern);
            assert dateTest.equals(DATE);
            String timeTest = (String) queryResult[0].get("testTime");
            assert timeTest.matches(timePattern);
            assert timeTest.equals(FORMATTED_TIME);
            assert ((BigDecimal) queryResult[0].get("testDec")).compareTo(new BigDecimal("145.86")) == 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
        
        // Delete the row of data
        try {
            publishResult = jdbc.processPublish(DELETE_ROW_EXTENDED_TYPES);
            assert publishResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not have thrown exception.");
        }
    }
    
    @Test
    public void testVantiqDateFormatting() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef());
        
        // Publish to the source in order to create a table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_DATETIME);
        vantiq.publish("sources", SOURCE_NAME, create_params);
        
        // Publish to the source in order to insert data into the table
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", INSERT_VALUE_DATETIME);
        vantiq.publish("sources", SOURCE_NAME, insert_params);
        
        // Query the Source, and get the returned date
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("query", QUERY_TABLE_DATETIME);
        VantiqResponse response = vantiq.query(SOURCE_NAME, params);
        JsonArray responseBody = (JsonArray) response.getBody();
        JsonObject responseMap = (JsonObject) responseBody.get(0);
        String timestamp = (String) responseMap.get("ts").getAsString();
        assert timestamp.equals(FORMATTED_TIMESTAMP);

        // Create a Type with DateTime property
        setupType();
        
        // Insert timestamp into Type
        Map<String,String> insertObj = new LinkedHashMap<String,String>();
        insertObj.put("timestamp", timestamp);
        vantiq.insert(TYPE_NAME, insertObj);
        
        // Check that value is as expected
        response = vantiq.select(TYPE_NAME, null, null, null);
        ArrayList typeResponseBody = (ArrayList) response.getBody();
        responseMap = (JsonObject) typeResponseBody.get(0);
        String vantiqTimestamp = (String) responseMap.get("timestamp").getAsString();
        
        // Check that the date was offset by adding 7 hours (since original date ends in 0700)
        assert vantiqTimestamp.equals(VANTIQ_FORMATTED_TIMESTAMP);
        
        // Delete the Type from VANTIQ
        deleteType();
        
        // Delete the Source from VANTIQ
        deleteSource();
    }
    
    @Test
    public void testCorrectErrors() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        assumeTrue(testDBURL.contains("mysql"));
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        HashMap[] queryResult;
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
    
    // ================================================= Helper functions =================================================

    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new JDBCCore(SOURCE_NAME, testAuthToken, testVantiqServer);
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static Map<String,Object> createSourceDef() {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> jdbcConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> vantiq = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        
        // Setting up vantiq config options
        vantiq.put("packageRows", "true");
        
        // Setting up general config options
        general.put("username", testDBUsername);
        general.put("password", testDBPassword);
        general.put("dbURL", testDBURL);
        
        // Placing general config options in "jdbcConfig"
        jdbcConfig.put("general", general);
        
        // Putting objRecConfig and vantiq config in the source configuration
        sourceConfig.put("jdbcConfig", jdbcConfig);
        sourceConfig.put("vantiq", vantiq);
        
        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", SOURCE_NAME);
        sourceDef.put("type", "JDBC");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
    }
    
    public static void deleteSource() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", SOURCE_NAME);
        vantiq.delete("system.sources", where);
    }
    
    public static void setupType() {
        Map<String,Object> typeDef = new LinkedHashMap<String,Object>();
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        Map<String,Object> propertyDef = new LinkedHashMap<String,Object>();
        propertyDef.put("type", "DateTime");
        propertyDef.put("required", true);
        properties.put("timestamp", propertyDef);
        typeDef.put("properties", properties);
        typeDef.put("name", TYPE_NAME);
        vantiq.insert("system.types", typeDef);
    }
    
    public static void deleteType() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", TYPE_NAME);
        vantiq.delete("system.types", where);
    }
    
}
