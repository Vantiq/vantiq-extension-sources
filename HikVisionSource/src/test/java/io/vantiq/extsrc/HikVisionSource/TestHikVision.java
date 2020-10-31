/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.HikVisionSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.HikVisionSource.exception.VantiqHikVisionException;

public class TestHikVision extends TestHikVisionBase {
    
    // Queries to be tested
    static final String CREATE_TABLE = "create table Test(id int not null, age int not null, "
            + "first varchar (255), last varchar (255));";
    static final String PUBLISH_QUERY = "INSERT INTO Test VALUES (1, 25, 'Santa', 'Claus');";
    static final String SELECT_QUERY = "SELECT id, first, last, age FROM Test;";
    static final String DELETE_ROW = "DELETE FROM Test WHERE first='Santa';";
    static final String DELETE_TABLE = "DROP TABLE Test;";
    
    // Date values used to test oddball types
    static final String TIMESTAMP = "2020-08-15 9:24:18";
    static final String DATE = "2020-08-15";
    static final String TIME = "9:24:18";
    static final String FORMATTED_TIMESTAMP = "2020-08-15T09:24:18.000-0700";
    static final String FORMATTED_TIME = "09:24:18.000-0800";
    static final String VANTIQ_FORMATTED_TIMESTAMP = "2020-08-15T16:24:18Z";
    
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
    
    // Queries for checking dropped connection
    static final String CREATE_TABLE_AFTER_LOST_CONNECTION = "CREATE TABLE NoConnection(id int);";
    static final String DROP_TABLE_AFTER_LOST_CONNECTION = "DROP TABLE NoConnection;";
    
    // Queries for max message size test
    static final String CREATE_TABLE_MAX_MESSAGE_SIZE = "CREATE TABLE TestMessageSize(id int, first varchar (255), last varchar (255), "
            + "age int, title varchar (255), is_active varchar (255), department varchar (255), salary int);";
    static final String INSERT_ROW_MAX_MESSAGE_SIZE = "INSERT INTO TestMessageSize VALUES(1, 'First', 'Last', 30, 'Title', 'Active',"
            + " 'Department', 1000000);";

 
    
    static final String TEST_INT = "10";
    static final String TEST_STRING = "test";
    static final String TEST_DEC = "123.45";
            
    static final int CORE_START_TIMEOUT = 10;
    
    static HikVisionCore core;
    static HikVision hikVision;
    static Vantiq vantiq;
    
    @Before
    public void setup() {
        hikVision = new HikVision();
        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }
    
    
    
    @Test
    public void testExtendedTypes() throws VantiqHikVisionException {
        /*
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        hikVision.setupHikVision(testIPAddress, testIPPort, false, 0);        HashMap[] queryResult;
        */
        int publishResult;
        
    }
    
    @Test
    public void testCorrectErrors() throws VantiqHikVisionException {
        /*
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        hikVision.setupHikVision(testIPAddress, testIPPort, false, 0);
        */
        HashMap[] queryResult;
        int publishResult;
        
        // Check error code for selecting from non-existent table
        
        
    }
    // ================================================= Helper functions =================================================

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

    public static void setupSource(Map<String,Object> sourceDef) {

        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", "HikVision");
        VantiqResponse implResp = vantiq.select("system.sourceimpls", null, where, null);

        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new HikVisionCore(testSourceName, testAuthToken, testVantiqServer);
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static Map<String,Object> createSourceDef(boolean isAsynch, boolean useCustomTaskConfig) {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> vantiq = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        
        // Setting up vantiq config options
        vantiq.put("packageRows", "true");
        
        // Setting up general config options
        if (isAsynch) {
            general.put("asynchronousProcessing", true);
            if (useCustomTaskConfig) {
                general.put("maxActiveTasks", 10);
                general.put("maxQueuedTasks", 20);
            }
        }
        
        // Placing general config options in "SSVConfig"
        sourceConfig.put("vantiq", vantiq);
        
        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", "HikVision");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
    }
    
    public static void deleteSource() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.delete("system.sources", where);
    }

    
    public static void setupType() {
        Map<String,Object> typeDef = new LinkedHashMap<String,Object>();
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        Map<String,Object> propertyDef = new LinkedHashMap<String,Object>();
        propertyDef.put("type", "DateTime");
        propertyDef.put("required", true);
        properties.put("timestamp", propertyDef);
        typeDef.put("properties", properties);
        vantiq.insert("system.types", typeDef);
    }

    public static void setupAsynchType() {
        Map<String,Object> typeDef = new LinkedHashMap<String,Object>();
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        Map<String,Object> id = new LinkedHashMap<String,Object>();
        Map<String,Object> first = new LinkedHashMap<String,Object>();
        Map<String,Object> last = new LinkedHashMap<String,Object>();
        id.put("type", "Integer");
        id.put("required", false);
        first.put("type", "String");
        first.put("required", false);
        last.put("type", "String");
        last.put("required", false);
        properties.put("id", id);
        properties.put("first", first);
        properties.put("last", last);
        typeDef.put("properties", properties);
        vantiq.insert("system.types", typeDef);
    }
    

    public static boolean checkTopicExists() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testTopicName);
        VantiqResponse response = vantiq.select("system.topics", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void setupTopic() {
        Map<String,String> topicDef = new LinkedHashMap<String,String>();
        topicDef.put("name", testTopicName);
        topicDef.put("description", "A description");
        vantiq.insert("system.topics", topicDef);
    }

    public static void deleteTopic() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testTopicName);
        VantiqResponse response = vantiq.delete("system.topics", where);
    }

    public static boolean checkProcedureExists() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testProcedureName);
        VantiqResponse response = vantiq.select("system.procedures", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void setupProcedure() {
        String procedure =
                "PROCEDURE " + testProcedureName +  "()\n"
                + "for (i in range(0,500)) {\n"
                    + "var sqlQuery = \"INSERT INTO TestAsynchProcessing VALUES(\" + i + \", 'FirstName', 'LastName')\"\n"
                    + "PUBLISH {query: sqlQuery} to SOURCE " + testSourceName + "\n"
                    + "PUBLISH {\"key\": i} TO TOPIC \"" + testTopicName + "\"\n"
                + "}";

        vantiq.insert("system.procedures", procedure);
    }

    public static void deleteProcedure() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testProcedureName);
        VantiqResponse response = vantiq.delete("system.procedures", where);
    }

    public static boolean checkRuleExists() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.select("system.rules", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void setupRule() {
        String rule =
                "RULE " + testRuleName + "\n"
                + "WHEN PUBLISH OCCURS ON \"" + testTopicName +  "\" as event\n"
                + "var sqlQuery = \"SELECT * FROM TestAsynchProcessing WHERE id=\" + event.newValue.key\n"
                + "SELECT * FROM SOURCE " + testSourceName + " AS results WITH query: sqlQuery\n"
                + "{\n"
                    + "if (results == []) {\n"
                        + "var insertData = {}\n"
                        + "insertData.id = event.newValue.key\n"
                        + "insertData.first = \"FirstName\"\n"
                        + "insertData.last = \"LastName\"\n"
                        + "INSERT " + testTypeName + "(insertData)\n"
                    + "} else {\n"
                        + "INSERT " + testTypeName + "(results)\n"
                    + "}\n"
                + "}";

        vantiq.insert("system.rules", rule);
    }

    public static void deleteRule() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.delete("system.rules", where);
    }
}