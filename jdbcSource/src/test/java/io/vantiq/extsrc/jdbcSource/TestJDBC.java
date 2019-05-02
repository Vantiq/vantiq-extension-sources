/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
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
    static final String CREATE_TABLE_DATETIME = "CREATE TABLE TestDates(ts TIMESTAMP);";
    static final String INSERT_VALUE_DATETIME = "INSERT INTO TestDates VALUES ('" + TIMESTAMP + "');";
    static final String QUERY_TABLE_DATETIME = "SELECT * FROM TestDates";
    static final String DROP_TABLE_DATETIME = "DROP TABLE TestDates";
    
    // Queries to test null values
    static final String CREATE_TABLE_NULL_VALUES = "CREATE TABLE TestNullValues(ts TIMESTAMP, testDate DATE, testTime TIME, "
            + "testInt int, testString varchar (255), testDec decimal(5,2));";
    static final String INSERT_ALL_NULL_VALUES = "INSERT INTO TestNullValues VALUES (null, null, null, null, null, null)";
    static final String QUERY_NULL_VALUES = "SELECT * FROM TestNullValues";
    static final String DELETE_ROW_NULL_VALUES = "DELETE FROM TestNullValues";
    static final String DROP_TABLE_NULL_VALUES = "DROP TABLE TestNullValues";
    
    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    static final String datePattern = "\\d{4}-\\d{2}-\\d{2}";
    static final String timePattern = "\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    
    static final String TEST_INT = "10";
    static final String TEST_STRING = "test";
    static final String TEST_DEC = "123.45";
            
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
    public static void debugSetup() throws VantiqSQLException {
        tearDown();
    }
    
    */
    
    @AfterClass
    public static void tearDown() throws VantiqSQLException {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null) {
            // Create new instance of JDBC to drop tables, in case the global JDBC instance was closed
            JDBC dropTablesJDBC = new JDBC();
            dropTablesJDBC.setupJDBC(testDBURL, testDBUsername, testDBPassword);
            
            // Delete first table
            try {
                dropTablesJDBC.processPublish(DELETE_TABLE);
            } catch (VantiqSQLException e) {
                // Shoudn't throw Exception
            }
            
            // Delete second table
            try {
                dropTablesJDBC.processPublish(DELETE_TABLE_EXTENDED_TYPES);
            } catch (VantiqSQLException e) {
                // Shoudn't throw Exception
            }
            
            // Delete third table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_DATETIME);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }
            
            // Delete fourth table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_NULL_VALUES);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }
            
            // Close the new JDBC Instance
            dropTablesJDBC.close();
        }
        // Close JDBCCore if still open
        if (core != null) {
            core.close();
            core = null;
        }
        // Close JDBC if still open
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
        
        // Try selecting again, should return empty HashMap Array since row was deleted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            assert queryResult.length == 0;
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
        
        // Check that Source and Type do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
        assumeFalse(checkTypeExists());
                
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef());
        
        // Publish to the source in order to create a table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_DATETIME);
        vantiq.publish("sources", testSourceName, create_params);
        
        // Publish to the source in order to insert data into the table
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", INSERT_VALUE_DATETIME);
        vantiq.publish("sources", testSourceName, insert_params);
        
        // Query the Source, and get the returned date
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("query", QUERY_TABLE_DATETIME);
        VantiqResponse response = vantiq.query(testSourceName, params);
        JsonArray responseBody = (JsonArray) response.getBody();
        JsonObject responseMap = (JsonObject) responseBody.get(0);
        String timestamp = (String) responseMap.get("ts").getAsString();
        assert timestamp.equals(FORMATTED_TIMESTAMP);

        // Create a Type with DateTime property
        setupType();
        
        // Insert timestamp into Type
        Map<String,String> insertObj = new LinkedHashMap<String,String>();
        insertObj.put("timestamp", timestamp);
        vantiq.insert(testTypeName, insertObj);
        
        // Check that value is as expected
        response = vantiq.select(testTypeName, null, null, null);
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
    public void testNullValues() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        int publishResult;
        HashMap[] queryResult;
        
        // Create table with all supported data type values
        try {
            publishResult = jdbc.processPublish(CREATE_TABLE_NULL_VALUES);
            assert publishResult == 0;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Insert a row of data into the table containing all null values
        try {
            publishResult = jdbc.processPublish(INSERT_ALL_NULL_VALUES);
            assert publishResult > 0;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Select all the values and make sure they are null, and that no error was thrown
        try {
            queryResult = jdbc.processQuery(QUERY_NULL_VALUES);
            assert queryResult[0].get("ts") == null;
            assert queryResult[0].get("testDate") == null;
            assert queryResult[0].get("testTime") == null;
            assert queryResult[0].get("testInt") == null;
            assert queryResult[0].get("testString") == null;
            assert queryResult[0].get("testDec") == null;
        } catch (VantiqSQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Alternate making one value null
        for (int i = 0; i < 6; i++) {
            // First delete the existing row in the table
            try {
                publishResult = jdbc.processPublish(DELETE_ROW_NULL_VALUES);
                assert publishResult > 0;
            } catch (VantiqSQLException  e) {
                fail("Should not fail when deleting the row: " + e.getMessage());
            }
            
            // Create array of the values to be input
            ArrayList<Object> nullValuesList = new ArrayList<Object>();
            nullValuesList.add("'" + TIMESTAMP + "'");
            nullValuesList.add("'" + DATE + "'");
            nullValuesList.add("'" + TIME + "'");
            nullValuesList.add(TEST_INT);
            nullValuesList.add("'" + TEST_STRING + "'");
            nullValuesList.add(TEST_DEC);
            Object[] nullValues = nullValuesList.toArray(new Object[nullValuesList.size()]);
            
            // Make one value null
            nullValues[i] = null;
            
            String queryString = "INSERT INTO TestNullValues VALUE (" + nullValues[0] + ", " + nullValues[1] + ", "
                    + nullValues[2] + ", " + nullValues[3] + ", " + nullValues[4] + ", " + nullValues[5] + ")";
            
            // Insert data into database
            try {
                publishResult = jdbc.processPublish(queryString);
            } catch (Exception e) {
                fail("No exception should be thrown when inserting null values: " + e.getMessage());
            }
            
            // Make sure collected data is identical to input data, and fail if any type of exception is caught
            try {
                queryResult = jdbc.processQuery(QUERY_NULL_VALUES);
                assert queryResult[0].size() == 5;
                if (i != 0) {
                    assert queryResult[0].get("ts").equals(FORMATTED_TIMESTAMP);
                }
                if (i != 1) {
                    assert queryResult[0].get("testDate").equals(DATE);
                }
                if (i != 2) {
                    assert queryResult[0].get("testTime").equals(FORMATTED_TIME);
                }
                if (i != 3) {
                    assert queryResult[0].get("testInt").toString().equals(TEST_INT);
                }
                if (i != 4) {
                    assert queryResult[0].get("testString").equals(TEST_STRING);
                }
                if (i != 5) {
                    assert queryResult[0].get("testDec").toString().equals(TEST_DEC);
                }
            } catch (Exception e) {
                fail("No exception should be thrown when querying: " + e.getMessage());
            }
        }
        
        jdbc.close();
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
            core = new JDBCCore(testSourceName, testAuthToken, testVantiqServer);
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
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", "JDBC");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
    }
    
    public static void deleteSource() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testSourceName);
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
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }
    
    public static void deleteType() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testTypeName);
        vantiq.delete("system.types", where);
    }
    
    public static boolean checkSourceExists() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.select("system.sources", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }
    
    public static boolean checkTypeExists() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.select("system.types", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }
    
}
