/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extjsdk.Utils;
import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings({"PMD.ExcessiveClassLength", "rawtypes", "PMD.IllegalTypeCheck"})
@Slf4j
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
    static final String VANTIQ_FORMATTED_TIMESTAMP = "2018-08-15T16:24:18.000Z";
    
    // Queries to test oddball types
    static final String CREATE_TABLE_EXTENDED_TYPES = "create table TestTypes(id int, ts TIMESTAMP, testDate DATE, "
            + "testTime TIME, testDec decimal(5,2));";

    static final String PUBLISH_QUERY_EXTENDED_TYPES = "INSERT INTO TestTypes VALUES (1, '" + TIMESTAMP + "', '" + DATE + "',"
            + " '" + TIME + "', 145.86);";
    static final String SELECT_QUERY_EXTENDED_TYPES = "SELECT * FROM TestTypes;";
    static final String DELETE_ROW_EXTENDED_TYPES = "DELETE FROM TestTypes;";
    static final String DELETE_TABLE_EXTENDED_TYPES = "DROP TABLE TestTypes;";

    static final Integer TS_COUNT = 25;
    static final Integer PARALLEL_DATE_INSTANCE_COUNT = 15;
    static final Integer PARALLEL_DATE_THREAD_COUNT = 150;
    static final String CREATE_TABLE_MANY_DATES = "create table TestManyDates(id int," +
            "ts1  TIMESTAMP," +
            "ts2  TIMESTAMP," +
            "ts3  TIMESTAMP," +
            "ts4  TIMESTAMP," +
            "ts5  TIMESTAMP," +
            "ts6  TIMESTAMP," +
            "ts7  TIMESTAMP," +
            "ts8  TIMESTAMP," +
            "ts9  TIMESTAMP," +
            "ts10 TIMESTAMP," +
            "ts11 TIMESTAMP," +
            "ts12 TIMESTAMP," +
            "ts13 TIMESTAMP," +
            "ts14 TIMESTAMP," +
            "ts15 TIMESTAMP," +
            "ts16 TIMESTAMP," +
            "ts17 TIMESTAMP," +
            "ts18 TIMESTAMP," +
            "ts19 TIMESTAMP," +
            "ts20 TIMESTAMP," +
            "ts21 TIMESTAMP," +
            "ts22 TIMESTAMP," +
            "ts23 TIMESTAMP," +
            "ts24 TIMESTAMP," +
            "ts25 TIMESTAMP," +
            "testDate DATE, " +
            "testTime TIME);";

    static final String INSERT_MANY_DATES_FRAG = "INSERT INTO TestManyDates VALUES (";
    static final String SELECT_QUERY_MANY_DATES = "SELECT * FROM TestManyDates;";
    static final String DELETE_ROW_MANY_DATES = "DELETE FROM TestManyDates;";
    static final String DROP_TABLE_MANY_DATES = "DROP TABLE TestManyDates;";

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
    
    static JDBCCore core;
    static JDBC jdbc;
    static Vantiq vantiq;
    
    @BeforeClass
    public static void setup() {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null) {
            jdbc = new JDBC();
            vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
            vantiq.setAccessToken(testAuthToken);
            try {
                createSourceImpl(vantiq);
            } catch (Exception e) {
                fail("Could not create sourceImpl: " + e.getMessage());
            }
            try {
                setupSource(createSourceDef(false, false));
            } catch (Exception e) {
                fail("Could not create source: " + e.getMessage());
            }
    
        }
    }

    @BeforeClass
    public static void setupEnv() throws IOException {
        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        File serverConfigFile = new File("server.config");
        serverConfigFile.createNewFile();
        serverConfigFile.deleteOnExit();
        Utils.obtainServerConfig();
    }
    
    @After
    public void postTestCleanup() {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null) {
            // Delete the Source/Type/Topic/Procedure/Rule from VANTIQ
            deleteType();
            deleteTopic();
            deleteProcedure();
            deleteRule();
    
            if (jdbc != null) {
                jdbc.close();
                jdbc = null;
            }
        }
    }

    @SuppressWarnings({"PMD.JUnit4TestShouldUseAfterAnnotation", "PMD.CognitiveComplexity", "PMD.EmptyCatchBlock"})
    @AfterClass
    public static void tearDown() throws VantiqSQLException {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null) {
            // Create new instance of JDBC to drop tables, in case the global JDBC instance was closed
            JDBC dropTablesJDBC = new JDBC();
            dropTablesJDBC.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            
            // Delete first table
            try {
                dropTablesJDBC.processPublish(DELETE_TABLE);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }
            
            // Delete second table
            try {
                dropTablesJDBC.processPublish(DELETE_TABLE_EXTENDED_TYPES);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
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
            
            // Delete fifth table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_AFTER_LOST_CONNECTION);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }
            
            // Delete sixth table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_MAX_MESSAGE_SIZE);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }

            // Delete seventh table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_ASYNCH);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }

            // Delete eighth table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_INVALID_BATCH);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }

            // Delete ninth table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_BATCH);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }

            // Delete tenth table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_QUERY);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }

            // Delete eleventh table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_BATCH_QUERY);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }

            // Delete parallel Dates table
            try {
                dropTablesJDBC.processPublish(DROP_TABLE_MANY_DATES);
            } catch (VantiqSQLException e) {
                // Shouldn't throw Exception
            }
            
            // Close the new JDBC Instance
            dropTablesJDBC.close();

            // Delete all VANTIQ Resources in case they are still there
            deleteSources();
            deleteType();
            deleteTopic();
            deleteProcedure();
            deleteRule();

            deleteSourceImpl(vantiq);
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
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            int queryResult;
    
            // Try processPublish with a nonsense query
            try {
                jdbc.processPublish("jibberish");
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
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }
    
    @Test
    public void testProcessQuery() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            
            Map[] queryResult;
            int deleteResult;
            
            // Try processQuery with a nonsense query
            try {
                jdbc.processQuery("jibberish");
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
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }
    
    @Test
    public void testExtendedTypes() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            Map[] queryResult;
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
                assertEquals("Expected " + FORMATTED_TIMESTAMP + ", but got " + timestampTest,
                        FORMATTED_TIMESTAMP, timestampTest);
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
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.SimpleDateFormatNeedsLocale"})
    @Test
    public void testParallelDates() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            int publishResult;
    
    
            // Create table with odd types including timestamps, dates, times, and decimals
            try {
                publishResult = jdbc.processPublish(CREATE_TABLE_MANY_DATES);
                assert publishResult == 0;
            } catch (VantiqSQLException e) {
                e.printStackTrace();
                fail("Should not have thrown exception." + e);
            }
    
            // Insert values into the newly created table
            // Here, we will set up a bunch of sets of 25 rows, where each row has a bunch of dates in it.
    
            ArrayList<Map<String, Object>> dataMap = new ArrayList<>();
            try {
                for (int i = 0; i < 25; i++) {
                    Map<String, Object> toPub = createManyDatesRows(i, PARALLEL_DATE_INSTANCE_COUNT);
                    dataMap.add(toPub);
                    for (String row : (String[]) toPub.get("batch")) {
                        publishResult = jdbc.processPublish(row);
                        assert publishResult > 0;
                    }
                }
            } catch (VantiqSQLException e) {
                e.printStackTrace();
                fail("Should not have thrown exception on insert: " + e);
            }
    
            // Now, to verify our parallel operations, we'll fire off a bunch of threads all doing the same
            // query across this table full of dates.  We should get everything with correct results,
            // but we had had the date results scrambled due to use of non-threadsafe methods.
    
            AtomicReference<Exception> threadFailed = new AtomicReference<>();
            Runnable querier = () -> {
                // Select the values from the table and make sure the data is retrieved correctly
                try {
                    Map[] qres = jdbc.processQuery(SELECT_QUERY_MANY_DATES);
                    for (Map row : qres) {
                        int id = (int) row.get("id");
                        for (int i = 1; i <= TS_COUNT; i++ ) {
                            String timestampTest = (String) row.get("ts" + i);
                            assertNotNull(timestampTest);
                            assert timestampTest.matches(timestampPattern);
                            Date found = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(timestampTest);
                            assert dataMap.size() > id;
                            Date expected = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss")
                                    .parse(dataMap.get(id).get("expected").toString());
                            assertNotNull(found);
                            assertNotNull(expected);
                            assertEquals("Expected " + expected + ", but got " + found, expected, found);
                            String dateTest = (String) row.get("testDate");
                            assert dateTest.matches(datePattern);
                            assert dateTest.equals(DATE);
                            String timeTest = (String) row.get("testTime");
                            assert timeTest.matches(timePattern);
                            assert timeTest.equals(FORMATTED_TIME);
                        }
                    }
                } catch (Exception e) {
                    threadFailed.set(e);
                }
            };
    
            ArrayList<Thread> threads = new ArrayList<>();
            for (int i = 0; i < PARALLEL_DATE_THREAD_COUNT; i++) {
                Thread t = new Thread(querier);
                threads.add(t);
                t.start();
            }
    
            // Now, wait for all the threads to complete, checking that they all ran exception free.
            Exception trapped = null;
            for (Thread t: threads) {
                try {
                    t.join();
                } catch (Exception e) {
                    trapped = e;
                }
            }
    
            assertNull(trapped);
            assertNull("Trapped exception during thread processing: " + threadFailed.get(), threadFailed.get());
    
            // Delete the row of data
            try {
                publishResult = jdbc.processPublish(DELETE_ROW_MANY_DATES);
                assert publishResult > 0;
            } catch (VantiqSQLException e) {
                fail("Should not have thrown exception on drop.");
            }
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }
    
    @Test
    public void testVantiqDateFormatting() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        
        // Check that Type does not already exist in namespace, and skip test if they do
        assumeFalse(checkTypeExists());
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            
            // Publish to the source in order to create a table
            Map<String, Object> createParams = new LinkedHashMap<>();
            createParams.put("query", CREATE_TABLE_DATETIME);
            vantiq.publish("sources", testSourceName, createParams);
            
            // Publish to the source in order to insert data into the table
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("query", INSERT_VALUE_DATETIME);
            vantiq.publish("sources", testSourceName, insertParams);
            
            // Query the Source, and get the returned date
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("query", QUERY_TABLE_DATETIME);
            VantiqResponse response = vantiq.query(testSourceName, params);
            JsonArray responseBody = (JsonArray) response.getBody();
            JsonObject responseMap = (JsonObject) responseBody.get(0);
            String timestamp = responseMap.get("ts").getAsString();
            assertEquals("Expected " + FORMATTED_TIMESTAMP + ", but got " + timestamp,
                    FORMATTED_TIMESTAMP, timestamp);
    
            // Create a Type with DateTime property
            setupType();
            
            // Insert timestamp into Type
            Map<String, String> insertObj = new LinkedHashMap<>();
            insertObj.put("timestamp", timestamp);
            vantiq.insert(testTypeName, insertObj);
            
            // Check that value is as expected
            response = vantiq.select(testTypeName, null, null, null);
            ArrayList typeResponseBody = (ArrayList) response.getBody();
            responseMap = (JsonObject) typeResponseBody.get(0);
            String vantiqTimestamp = responseMap.get("timestamp").getAsString();
            
            // Check that the date was offset by adding 7 hours (since original date ends in 0700)
            assertEquals("Formatted time stamp", VANTIQ_FORMATTED_TIMESTAMP, vantiqTimestamp);
            
            // Delete the Type from VANTIQ
            deleteType();
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
     }

    @SuppressWarnings({"PMD.CognitiveComplexity"})
    @Test
    public void testNullValues() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            
            int publishResult;
            Map[] queryResult;
            
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
                ArrayList<Object> nullValuesList = new ArrayList<>();
                nullValuesList.add("'" + TIMESTAMP + "'");
                nullValuesList.add("'" + DATE + "'");
                nullValuesList.add("'" + TIME + "'");
                nullValuesList.add(TEST_INT);
                nullValuesList.add("'" + TEST_STRING + "'");
                nullValuesList.add(TEST_DEC);
                Object[] nullValues = nullValuesList.toArray(new Object[0]);
                
                // Make one value null
                nullValues[i] = null;
                
                String queryString = "INSERT INTO TestNullValues VALUE (" + nullValues[0] + ", " + nullValues[1] + ", "
                        + nullValues[2] + ", " + nullValues[3] + ", " + nullValues[4] + ", " + nullValues[5] + ")";
                
                // Insert data into database
                try {
                    publishResult = jdbc.processPublish(queryString);
                    assertTrue("publish failed: " + publishResult, publishResult > 0);
                } catch (Exception e) {
                    fail("No exception should be thrown when inserting null values: " + e.getMessage());
                }
                
                // Make sure collected data is identical to input data, and fail if any type of exception is caught
                try {
                    queryResult = jdbc.processQuery(QUERY_NULL_VALUES);
                    assert queryResult.length == 1;
                    assert queryResult[0].size() == 5;
                    assert i == 0 || queryResult[0].get("ts").equals(FORMATTED_TIMESTAMP);
                    if (i != 1) {
                        assertEquals("Expected " + DATE + ", but got " + queryResult[0].get("testDate"),
                                DATE, queryResult[0].get("testDate"));
                    }
                    if (i != 2) {
                        assertEquals("Expected " + FORMATTED_TIME + ", but got " + queryResult[0].get("testTime"),
                             FORMATTED_TIME, queryResult[0].get("testTime"));
                    }
                    assert i == 3 || queryResult[0].get("testInt").toString().equals(TEST_INT);
                    assert i == 4 || queryResult[0].get("testString").equals(TEST_STRING);
                    assert i == 5 || queryResult[0].get("testDec").toString().equals(TEST_DEC);
                } catch (Exception e) {
                    fail("No exception should be thrown when querying: " + e.getMessage());
                }
            }
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @SuppressWarnings({"PMD.CognitiveComplexity"})
    @Test
    public void testCorrectErrors() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        assumeTrue(testDBURL.contains("mysql"));
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            // Check error code for selecting from non-existent table
            try {
                jdbc.processQuery(NO_TABLE);
                fail("Should have thrown an exception.");
            } catch (VantiqSQLException e) {
                String message = e.getMessage();
                assert message.contains("1146");
            }
            
            // Check error code for selecting non-existent field
            try {
                jdbc.processQuery(NO_FIELD);
                fail("Should have thrown an exception.");
            } catch (VantiqSQLException e) {
                String message = e.getMessage();
                assert message.contains("1054");
            }
            
            // Check error code for syntax error
            try {
                jdbc.processQuery(SYNTAX_ERROR);
                fail("Should have thrown an exception.");
            } catch (VantiqSQLException e) {
                String message = e.getMessage();
                assert message.contains("1064");
            }
            
            // Check error code for using INSERT with executeQuery() method
            try {
                jdbc.processQuery(PUBLISH_QUERY);
                fail("Should have thrown an exception.");
            } catch (VantiqSQLException e) {
                String message = e.getMessage();
                assert message.contains("0");
            }
            
            // Check error code for INSERT not matching columns
            try {
                jdbc.processPublish(INSERT_NO_FIELD);
                fail("Should have thrown an exception.");
            } catch (VantiqSQLException e) {
                String message = e.getMessage();
                assert message.contains("1136");
            }
            
            // Check error code for inserting wrong types
            try {
                jdbc.processPublish(INSERT_WRONG_TYPE);
                fail("Should have thrown an exception.");
            } catch (VantiqSQLException e) {
                String message = e.getMessage();
                assert message.contains("1366");
            }
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }
    
    @Test
    public void testDBReconnect() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
            
            // Close the connection, and then try to query
            jdbc.close();
            
            // Query should still work, because reconnect will be triggered
            int publishResult;
            try {
                publishResult = jdbc.processPublish(CREATE_TABLE_AFTER_LOST_CONNECTION);
                assert publishResult == 0;
            } catch (VantiqSQLException e) {
                fail("Should not throw an exception");
            }
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }
    
    @Test
//    @Ignore
    public void testMaxMessageSize() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            // Number of rows to insert in table;
            int numRows = 2000;
    
            // Publish to the source in order to create a table
            Map<String, Object> createParams = new LinkedHashMap<>();
            createParams.put("query", CREATE_TABLE_MAX_MESSAGE_SIZE);
            vantiq.publish("sources", testSourceName, createParams);
    
            // Insert 2000 rows into the table
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("query", INSERT_ROW_MAX_MESSAGE_SIZE);
            for (int i = 0; i < numRows; i ++) {
                vantiq.publish("sources", testSourceName, insertParams);
            }
    
            // Query the Source without bundleFactor and make sure there were no errors
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("query", QUERY_TABLE_MAX_MESSAGE_SIZE);
            VantiqResponse response = vantiq.query(testSourceName, params);
            JsonArray responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == numRows;
            assert core != null;
            assert core.lastRowBundle != null;
            assert core.lastRowBundle.length == JDBCCore.DEFAULT_BUNDLE_SIZE;
    
            // Query with an invalid bundleFactor
            params.put("bundleFactor", "jibberish");
            response = vantiq.query(testSourceName, params);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == numRows;
            assert core.lastRowBundle.length == JDBCCore.DEFAULT_BUNDLE_SIZE;
    
            // Query with an invalid bundleFactor
            params.put("bundleFactor", -1);
            response = vantiq.query(testSourceName, params);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == numRows;
            assert core.lastRowBundle.length == JDBCCore.DEFAULT_BUNDLE_SIZE;
    
            // Query with bundleFactor that divides evenly into 2000 rows
            int bundleFactor = 200;
            params.put("bundleFactor", bundleFactor);
            response = vantiq.query(testSourceName, params);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == numRows;
            assert core.lastRowBundle.length == bundleFactor;
    
            // Query with bundleFactor that doesn't divide evenly into 2000 rows
            bundleFactor = 600;
            params.put("bundleFactor", bundleFactor);
            response = vantiq.query(testSourceName, params);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == numRows;
            assert core.lastRowBundle.length == numRows % bundleFactor;
    
            // Drop table and then create it again
            Map<String, Object> dropParams = new LinkedHashMap<>();
            dropParams.put("query", DROP_TABLE_MAX_MESSAGE_SIZE);
            vantiq.publish("sources", testSourceName, dropParams);
            vantiq.publish("sources", testSourceName, createParams);
    
            // Check that lastRowBundle is null when the query returns no data
            params.remove("bundleFactor");
            response = vantiq.query(testSourceName, params);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == 0;
            assert core.lastRowBundle == null;
    
            // Insert fewer rows, and make sure that using bundleFactor of 0 works
            numRows = 100;
            for (int i = 0; i < numRows; i ++) {
                vantiq.publish("sources", testSourceName, insertParams);
            }
            params.put("bundleFactor", 0);
            response = vantiq.query(testSourceName, params);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == numRows;
            assert core.lastRowBundle.length == numRows;
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @Test
    public void testAsynchronousProcessing() throws InterruptedException {
        doAsynchronousProcessing(true, 500);
    }

    @Test
    public void testAsynchronousProcessingWithDefaults() throws InterruptedException {
        doAsynchronousProcessing(false, 300);
    }

    public void doAsynchronousProcessing(boolean useCustomTaskConfig, int limitValue) throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source, Type, Topic, Procedure and Rule do not already exist in namespace, and skip test if they do
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", testSourceNameAsynch);
        VantiqResponse response = vantiq.select("system.sources", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (!responseBody.isEmpty()) {
            response = vantiq.delete("system.sources", where);
            assert response.isSuccess();
        }
    
        assumeFalse(checkTypeExists());
        assumeFalse(checkTopicExists());
        assumeFalse(checkProcedureExists());
        assumeFalse(checkRuleExists());

        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(true, useCustomTaskConfig));
        // Create Type to store query results
        setupAsynchType();

        // Create Topic used to trigger Rule
        setupTopic();

        // Create Procedure to publish to VANTIQ Source
        setupProcedure(limitValue);

        // Create Rule to query the VANTIQ Source
        setupRule();
    
        // Select from the type and make sure all of our results are there as expected
        response = vantiq.select(testTypeName, null, null, null);
        responseBody = (ArrayList) response.getBody();
        assertEquals (0, responseBody.size());
    
        // Publish to the source in order to create a table
        Map<String, Object> createParams = new LinkedHashMap<>();
        createParams.put("query", CREATE_TABLE_ASYNCH);
        vantiq.publish("sources", testSourceName, createParams);
        
        // Execute Procedure to trigger asynchronous publish/queries (assign to variable to ensure that procedure has finished before selecting from type)
        response = vantiq.execute(testProcedureName, new LinkedHashMap<>());
        if (!response.isSuccess()) {
            log.debug("Errors on execute {}: {}", testProcedureName, response.getErrors());
        }
        assert response.isSuccess();
        // Sleep for a bit and see if we're done yet
        long seconds = 1;
        
        while (seconds < 30) {
            Thread.sleep(1000);
            seconds += 1;
            // Select from the type and make sure all of our results are there as expected
            response = vantiq.select(testTypeName, null, null, null);
            responseBody = (ArrayList) response.getBody();
            if (limitValue == responseBody.size()) {
                break;
            }
        }
        assertEquals(limitValue, responseBody.size());

        // Delete the table for next test
        Map<String, Object> deleteParams = new LinkedHashMap<>();
        deleteParams.put("query", DROP_TABLE_ASYNCH);
        vantiq.publish("sources", testSourceName, deleteParams);

        // Delete the Source/Type/Topic/Procedure/Rule from VANTIQ
        response = vantiq.delete("system.sources", where);
        assert response.isSuccess();
    
        deleteType();
        deleteTopic();
        deleteProcedure();
        deleteRule();
    }

    @Test
    public void testInvalidBatchProcessing() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            // Create table
            Map<String, Object> createParams = new LinkedHashMap<>();
            createParams.put("query", CREATE_TABLE_INVALID_BATCH);
            vantiq.publish("sources", testSourceName, createParams);
    
            // Creating a list of integers to insert as a batch
            ArrayList<Object> invalidBatch = new ArrayList<>();
            for (int i = 0; i<50; i++) {
                invalidBatch.add(10);
            }
    
            // Attempt to insert data into the table, which should fail
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("query", invalidBatch);
            vantiq.publish("sources", testSourceName, insertParams);
    
            // Query the table and make sure it is empty
            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("query", SELECT_TABLE_INVALID_BATCH);
            VantiqResponse response = vantiq.query(testSourceName, queryParams);
            JsonArray responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == 0;
    
            // Now try a list of jibberish queries
            for (int i = 0; i<50; i++) {
                invalidBatch.add("jibberish");
            }
    
            // Attempt to insert data into the table, which should fail
            insertParams.put("query", invalidBatch);
            vantiq.publish("sources", testSourceName, insertParams);
    
            // Query the table and make sure it is empty
            response = vantiq.query(testSourceName, queryParams);
            responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == 0;
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @Test
    public void testBatchProcessing() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            // Create table
            Map<String, Object> createParams = new LinkedHashMap<>();
            createParams.put("query", CREATE_TABLE_BATCH);
            vantiq.publish("sources", testSourceName, createParams);
    
            // Creating a list of strings to insert as a batch
            ArrayList<String> batch = new ArrayList<>();
            for (int i = 0; i<50; i++) {
                batch.add("INSERT INTO TestBatch VALUES (" + i + ", 'First', 'Second');");
            }
    
            // Inserting data into the table as a batch
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("query", batch);
            VantiqResponse response = vantiq.publish("sources", testSourceName, insertParams);
            assert !response.hasErrors();
    
            // Select the data from table and make sure the response is valid
            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("query", SELECT_TABLE_BATCH);
            response = vantiq.query(testSourceName, queryParams);
            if (response.hasErrors()) {
                log.debug("Response errors: {}", response.getErrors());
            }
            assert response.isSuccess();
            assert response.getBody() instanceof JsonArray;
            JsonArray responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == 50;
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @Test
    public void testQueryUpdate() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
  
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            // Create table using query
            Map<String, Object> createParams = new LinkedHashMap<>();
            createParams.put("query", CREATE_TABLE_QUERY);

            VantiqResponse response = vantiq.query(testSourceName, createParams);
            if (response.hasErrors()) {
                log.debug("Errors on response to {}: {}", CREATE_TABLE_QUERY, response.getErrors());
            }
            assert !response.hasErrors();
    
            // Inserting data into the table using query
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("query", INSERT_TABLE_QUERY);
            response = vantiq.query(testSourceName, insertParams);
            assert !response.hasErrors();
    
            // Select the data from table and make sure the previous queries worked
            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("query", SELECT_TABLE_QUERY);
            response = vantiq.query(testSourceName, queryParams);
            Object b = response.getBody();
            JsonObject bodyObject;
            JsonArray responseBody;
            if (b instanceof JsonArray) {
                log.debug("Got an array");
                responseBody = (JsonArray) response.getBody();
                bodyObject = responseBody.get(0).getAsJsonObject();
            } else {
                log.debug("Got object of class: {}", b.getClass().getName());
                bodyObject = (JsonObject) b;
            }
            assert bodyObject.get("id").getAsInt() == 1;
            assert bodyObject.get("name").getAsString().equals("Name");
    
            // Delete data from table using query
            Map<String, Object> deleteParams = new LinkedHashMap<>();
            deleteParams.put("query", DELETE_ROWS_QUERY);
            response = vantiq.query(testSourceName, deleteParams);
            assert !response.hasErrors();
    
            // Double check that the delete worked
            response = vantiq.query(testSourceName, queryParams);
            responseBody = (JsonArray) response.getBody();
            assert responseBody == null || responseBody.size() == 0;
        } catch (Exception e) {
            log.error("Test failing with exception: ", e);
            fail("Trapped exception: " + e.getMessage());
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    @Test
    public void testQueryUpdateBatch() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        try {
            jdbc = new JDBC();
            jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
    
            // Create table
            Map<String, Object> createParams = new LinkedHashMap<>();
            createParams.put("query", CREATE_TABLE_BATCH_QUERY);
            vantiq.query(testSourceName, createParams);
    
            // Creating a list of strings to insert as a batch
            ArrayList<String> batch = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                batch.add(INSERT_TABLE_BATCH_QUERY);
            }
    
            // Inserting data into the table as a batch
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("query", batch);
            VantiqResponse response = vantiq.query(testSourceName, insertParams);
            assert !response.hasErrors();
    
            // Select the data from table and make sure the response is valid
            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("query", SELECT_TABLE_BATCH_QUERY);
            response = vantiq.query(testSourceName, queryParams);
            JsonArray responseBody = (JsonArray) response.getBody();
            assert responseBody.size() == 50;
    
            // Adding a select statement to batch, to make sure it does not execute
            batch.add(SELECT_TABLE_BATCH_QUERY);
            insertParams.put("query", batch);
            response = vantiq.query(testSourceName, insertParams);
            assert response.hasErrors();
        } finally {
            if (jdbc != null) {
                jdbc.close();
            }
        }
    }

    // ================================================= Helper functions =================================================

    @SuppressWarnings({"PMD.SimpleDateFormatNeedsLocale", "PMD.IllegalTypeCheck"})
    public static Map<String, Object> createManyDatesRows(int id, int rowCount) {
        Instant inst = Instant.now().plus(id, ChronoUnit.DAYS);
        String instString = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss").format(Date.from(inst));
        LinkedHashMap<String, Object> resSet = new LinkedHashMap<>();
        String[] insertList = new String[rowCount];
        for (int i = 1; i <= rowCount; i++) {
            StringBuilder result = new StringBuilder(INSERT_MANY_DATES_FRAG + id + ", '");
            for (int j = 0; j < TS_COUNT; j++) {
                result.append(instString).append("', '");
            }
            result.append(" " + DATE + "', '" + TIME + "');");
            insertList[i - 1] = result.toString();
        }
        resSet.put("id", id);
        resSet.put("expected", instString);
        resSet.put("batch", insertList);
        return resSet;
    }

    public static boolean checkSourceExists() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.select("system.sources", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        return !responseBody.isEmpty();
    }

    public static void setupSource(Map<String, Object> sourceDef) {

        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", "JDBC");
        VantiqResponse implResp = vantiq.select(VANTIQ_SOURCE_IMPL, null, where, null);
        assertFalse("Errors from fetching source impl", implResp.hasErrors());
        ArrayList responseBody = (ArrayList) implResp.getBody();
        assertEquals("Missing sourceimpl -- expected a count of 1", 1, responseBody.size());

        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new JDBCCore((String) sourceDef.get("name"), testAuthToken, testVantiqServer);
            core.start(CORE_START_TIMEOUT);
        }
        if (insertResponse.hasErrors()) {
            log.debug("Errors creating source: {}", insertResponse.getErrors());
        }
        assert insertResponse.isSuccess();
    
        // Now, wait for system to get started...
        try {
            int counter = 0;
            while (!core.client.isAuthed() && counter++ < 50) {
                Thread.sleep(500L);
            }
            assert core.client.isAuthed();
            Map sourceConfig = (Map) sourceDef.get("config");
            Map jdbcConfig = (Map) sourceConfig.get("jdbcConfig");
            Map general = (Map) jdbcConfig.get("general");
            Boolean isAsync = (Boolean) general.getOrDefault("asynchronousProcessing", Boolean.FALSE);
            log.debug("Source {} is Async: {}", testSourceName, isAsync);
            if (isAsync) {
                counter = 0;
                while (counter++ < 60) {
                    if (core.publishPool == null || core.queryPool == null) {
                        Thread.sleep(500L);
                    }
                }
                assert core.publishPool != null;
                assert core.queryPool != null;
            }
        } catch (InterruptedException e) {
            fail("Interrupted while starting system: " + e.getMessage());
        }
    }
    
    public static Map<String, Object> createSourceDef(boolean isAsynch, boolean useCustomTaskConfig) {
        Map<String, Object> sourceDef = new LinkedHashMap<>();
        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        Map<String, Object> jdbcConfig = new LinkedHashMap<>();
        Map<String, Object> vantiq = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        
        // Setting up vantiq config options
        vantiq.put("packageRows", "true");
        
        // Setting up general config options
        general.put("username", testDBUsername);
        general.put("password", testDBPassword);
        general.put("dbURL", testDBURL);
        if (isAsynch) {
            general.put("asynchronousProcessing", true);
            if (useCustomTaskConfig) {
                general.put("maxActiveTasks", 10);
                general.put("maxQueuedTasks", 50);
            }
        }
        
        // Placing general config options in "jdbcConfig"
        jdbcConfig.put("general", general);
        
        // Putting objRecConfig and vantiq config in the source configuration
        sourceConfig.put("jdbcConfig", jdbcConfig);
        sourceConfig.put("vantiq", vantiq);
        
        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        String srcNam = (isAsynch ? testSourceNameAsynch : testSourceName);
        sourceDef.put("name", srcNam);
        sourceDef.put("type", "JDBC");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        
        return sourceDef;
    }
    
    public static void deleteSources() {
        Map<String, Object> tsn = new LinkedHashMap<>();
        tsn.put("name", testSourceName);
        Map<String, Object> tsna = new LinkedHashMap<>();
        tsna.put("name", testSourceNameAsynch);
        Map<String, Object> where = new LinkedHashMap<>();
        List<Map<String, Object>> qList = new ArrayList<>();
        qList.add(tsn);
        qList.add(tsna);
        where.put("$or", qList);
    
        VantiqResponse response = vantiq.delete("system.sources", where);
    }

    public static boolean checkTypeExists() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.select("system.types", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        return !responseBody.isEmpty();
    }
    
    public static void setupType() {
        Map<String, Object> typeDef = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> propertyDef = new LinkedHashMap<>();
        propertyDef.put("type", "DateTime");
        propertyDef.put("required", true);
        properties.put("timestamp", propertyDef);
        typeDef.put("properties", properties);
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }

    public static void setupAsynchType() {
        Map<String, Object> typeDef = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> id = new LinkedHashMap<>();
        Map<String, Object> first = new LinkedHashMap<>();
        Map<String, Object> last = new LinkedHashMap<>();
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
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }
    
    public static void deleteType() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.delete("system.types", where);
    }

    public static boolean checkTopicExists() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", testTopicName);
        VantiqResponse response = vantiq.select("system.topics", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        return !responseBody.isEmpty();
    }

    public static void setupTopic() {
        Map<String, String> topicDef = new LinkedHashMap<>();
        topicDef.put("name", testTopicName);
        topicDef.put("description", "A description");
        vantiq.insert("system.topics", topicDef);
    }

    public static void deleteTopic() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testTopicName);
        VantiqResponse response = vantiq.delete("system.topics", where);
    }

    public static boolean checkProcedureExists() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", testProcedureName);
        VantiqResponse response = vantiq.select("system.procedures", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        return !responseBody.isEmpty();
    }

    public static void setupProcedure(int limitValue) {
        String procedure =
                "PROCEDURE " + testProcedureName +  "()\n"
                + "for (i in range(0," + limitValue + ")) {\n"
                    + "var sqlQuery = \"INSERT INTO TestAsynchProcessing VALUES(\" + i + \", 'FirstName', 'LastName')\"\n"
                    + "PUBLISH {query: sqlQuery} to SOURCE " + testSourceName + "\n"
                    + "PUBLISH {\"key\": i} TO TOPIC \"" + testTopicName + "\"\n"
                + "}";

        vantiq.insert("system.procedures", procedure);
    }

    public static void deleteProcedure() {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testProcedureName);
        VantiqResponse response = vantiq.delete("system.procedures", where);
    }

    public static boolean checkRuleExists() {
        Map<String, String> where = new LinkedHashMap<>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.select("system.rules", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        return !responseBody.isEmpty();
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
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.delete("system.rules", where);
    }
}