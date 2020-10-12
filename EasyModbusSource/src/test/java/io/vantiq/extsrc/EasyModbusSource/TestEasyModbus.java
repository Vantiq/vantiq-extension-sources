/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import groovyjarjarantlr.collections.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;

import io.vantiq.extsrc.EasyModbusSource.exception.VantiqEasymodbusException;

public class TestEasyModbus extends TestEasyModbusBase {
    
    // Queries to be tested
    static final String SELECT_QUERY = "SELECT * from coils";
    
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
    static final String NO_FIELD_ITEM_INDEX = "SELECT itemrish FROM Test";
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
    static final String QUERY_TABLE_MAX_MESSAGE_SIZE = "SELECT * FROM TestMessageSize;";
    static final String DROP_TABLE_MAX_MESSAGE_SIZE = "DROP TABLE TestMessageSize";

    // Queries for asynchronous processing test
    static final String CREATE_TABLE_ASYNCH = "CREATE TABLE TestAsynchProcessing(id int, first varchar (255), last varchar (255));";
    static final String DROP_TABLE_ASYNCH = "DROP TABLE TestAsynchProcessing";

    // Queries for invalid batch processing tests
    static final String CREATE_TABLE_INVALID_BATCH = "CREATE TABLE TestInvalidBatch(id int, first varchar (255), last varchar (255));";
    static final String SELECT_TABLE_INVALID_BATCH = "SELECT * FROM TestInvalidBatch";
    static final String DROP_TABLE_INVALID_BATCH = "DROP TABLE TestInvalidBatch";

    // Queries for valid batch processing tests
    static final String CREATE_TABLE_BATCH = "CREATE TABLE TestBatch(id int, first varchar (255), last varchar (255));";
    static final String INSERT_TABLE_BATCH = "INSERT INTO TestBatch VALUES (1, 'First', 'Second');";
    static final String SELECT_TABLE_BATCH = "SELECT * FROM TestBatch;";
    static final String DROP_TABLE_BATCH = "DROP TABLE TestBatch";

    // Queries for updating DB using VANTIQ Query
    static final String CREATE_TABLE_QUERY = "CREATE TABLE TestQueryUpdate(id int, name varchar (255));";
    static final String INSERT_TABLE_QUERY = "INSERT INTO TestQueryUpdate VALUES (1, 'Name');";
    static final String SELECT_TABLE_QUERY = "SELECT * FROM TestQueryUpdate;";
    static final String DELETE_ROWS_QUERY = "DELETE FROM TestQueryUpdate;";
    static final String DROP_TABLE_QUERY = "DROP TABLE TestQueryUpdate;";

    // Queries for updating DB using VANTIQ Query, as batch
    static final String CREATE_TABLE_BATCH_QUERY = "CREATE TABLE TestQueryBatchUpdate(id int);";
    static final String INSERT_TABLE_BATCH_QUERY = "INSERT INTO TestQueryBatchUpdate VALUES (1);";
    static final String SELECT_TABLE_BATCH_QUERY = "SELECT * FROM TestQueryBatchUpdate;";
    static final String DROP_TABLE_BATCH_QUERY = "DROP TABLE TestQueryBatchUpdate;";
    
    static final String timestampPattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    static final String datePattern = "\\d{4}-\\d{2}-\\d{2}";
    static final String timePattern = "\\d{2}:\\d{2}:\\d{2}.\\d{3}-\\d{4}";
    
    static final String TEST_INT = "10";
    static final String TEST_STRING = "test";
    static final String TEST_DEC = "123.45";
            
    static final int CORE_START_TIMEOUT = 10;
    
    static EasyModbusCore core;
    static EasyModbus easyModbus;
    static Vantiq vantiq;
    
    @Before
    public void setup() {
        easyModbus = new EasyModbus();
        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }
    
    @Test
    public void testProcessQuery() throws VantiqEasymodbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);
        
        HashMap[] queryResult;

        Map<String, Object> request = CreateFalseCoilsRequest();
        easyModbus.hanldeUpdateCommand(request);

        
        // Try processQuery with a nonsense query
        try {
            queryResult = easyModbus.processQuery("jibberish");
            fail("Should have thrown exception.");
        } catch (VantiqEasymodbusException e) {
            // Expected behavior
        }
        
        // Select the row that we previously inserted
        try {
            queryResult = easyModbus.processQuery(SELECT_QUERY);
            assert (Integer) queryResult[0].size() == 1;
            ArrayList<Value> list = (ArrayList<Value>) queryResult[0].get("values");
            assert (Integer) list.size() == 20;
            for (int i = 0 ; i <list.size() ; i++){
                Value v = list.get(i); 
                assert v.index == i;
                assertFalse("illegal value on Index : "+v.index ,v.value ) ; // all fields should be false. 
            }

            request = SetValue(request,0,true);
            int rc = easyModbus.hanldeUpdateCommand(request);
            assert rc == 0;


        } catch (VantiqEasymodbusException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        
        // Try selecting again, should return empty HashMap Array since row was deleted
        try {
            queryResult = easyModbus.processQuery(SELECT_QUERY);
            assert (Integer) queryResult[0].size() == 1;
            ArrayList<Value> list = (ArrayList<Value>) queryResult[0].get("values");
            assert (Integer) list.size() == 20;
            Value v1 = list.get(0); 
            assertTrue("illegal value on Index : "+v1.index ,v1.value ) ; // all fields should be false. 

            for (int i = 1 ; i <list.size() ; i++){
                Value v = list.get(i); 
                assert v.index == i;
                assertFalse("illegal value on Index : "+v.index ,v.value ) ; // all fields should be false. 
            }
        } catch (VantiqEasymodbusException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        easyModbus.close();
    }

    @Test
    public void testExtendedTypes() throws VantiqEasymodbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);        HashMap[] queryResult;
        
        int publishResult;
        
    }

    @Test
    public void testCorrectErrors() throws VantiqEasymodbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0 ) ;
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);
        HashMap[] queryResult;
        int publishResult;
        
        // Check error code for selecting from non-existent table
        try {
            queryResult = easyModbus.processQuery(NO_TABLE);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1006");
        }
        
        // Check error code for selecting non-existent field
        try {
            queryResult = easyModbus.processQuery(NO_FIELD);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1007");
        }

        try {
            queryResult = easyModbus.processQuery(NO_FIELD_ITEM_INDEX);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1004");
        }

        // Check error code for syntax error
        try {
            queryResult = easyModbus.processQuery(SYNTAX_ERROR);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1005");
        }
        
    }
    
    // ================================================= Helper functions =================================================
    private Map<String, Object> CreateFalseCoilsRequest() {
        Map<String, Object> request = new HashMap<String, Object>(); 
        Map<String, Object> b = new HashMap<String, Object>(); 
        ArrayList<Map<String,Object>> l = new ArrayList<Map<String,Object>>(); // request.get("body");
        request.put("type","coils");
        request.put("body",l);

        ArrayList<HashMap<String,Object>> n = new ArrayList<HashMap<String,Object>>(); 
        for (int i = 0 ; i < 20 ; i++){
            HashMap<String,Object> m = new HashMap<String,Object>(); 
            m.put("value", false);
            n.add(m);
        }
       
        b.put("values",n);
        l.add( b);
        return request;
    }

    private Map<String, Object> SetValue( Map<String, Object> request , int index , boolean value) {
        request.put("type","coils");
        ArrayList<Map<String,Object>> l = (ArrayList<Map<String,Object>>) request.get("body");
        Map<String, Object> b = l.get(0);

        ArrayList<HashMap<String,Object>> n = (ArrayList<HashMap<String,Object>>) b.get("values");
        HashMap<String,Object> m = n.get(index);
        m.put("value", value);
        return request ; 
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

    public static void setupSource(Map<String,Object> sourceDef) {

        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", "EasyModbus");
        VantiqResponse implResp = vantiq.select("system.sourceimpls", null, where, null);

        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new EasyModbusCore(testSourceName, testAuthToken, testVantiqServer);
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static Map<String,Object> createSourceDef(boolean isAsynch, boolean useCustomTaskConfig) {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> easyModbusConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> vantiq = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        
        // Setting up vantiq config options
        vantiq.put("packageRows", "true");
        
        // Setting up general config options
        general.put("TCPAddress", testIPAddress);
        general.put("TCPPort", testIPPort);
        if (isAsynch) {
            general.put("asynchronousProcessing", true);
            if (useCustomTaskConfig) {
                general.put("maxActiveTasks", 10);
                general.put("maxQueuedTasks", 20);
            }
        }
        
        // Placing general config options in "easyModbusConfig"
        easyModbusConfig.put("general", general);
        
        // Putting objRecConfig and vantiq config in the source configuration
        sourceConfig.put("easyModbusConfig", easyModbusConfig);
        sourceConfig.put("vantiq", vantiq);
        
        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", testSourceName);
        sourceDef.put("type", "EasyModbus");
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