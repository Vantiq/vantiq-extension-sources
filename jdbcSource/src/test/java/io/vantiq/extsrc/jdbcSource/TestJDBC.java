/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;

import java.lang.Integer;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.jdbcSource.exception.VantiqSQLException;

@SuppressWarnings("PMD.ExcessiveClassLength")
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

    static final Integer TS_COUNT = 25;
    static final Integer PARALLEL_DATE_COUNT = 15;
    static final String CREATE_TABLE_MANY_DATES = "create table TestDates(id int," +
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

    static final String SELECT_QUERY_MANY_DATES= "SELECT * FROM TestDates;";
    static final String DELETE_ROW_MANY_DATES = "DELETE FROM TestDates;";
    static final String DROP_TABLE_MANY_DATES = "DROP TABLE TestDates;";

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

    @SuppressWarnings("PMD.JUnit4TestShouldUseAfterAnnotation")
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
            deleteSource();
            deleteType();
            deleteTopic();
            deleteProcedure();
            deleteRule();
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
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
        
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
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
        
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
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
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
            assertTrue("Expected " + FORMATTED_TIMESTAMP + ", but got " + timestampTest,timestampTest.equals(FORMATTED_TIMESTAMP));
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
    public void testParallelDates() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
        HashMap[] queryResult;
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
                Map<String, Object> toPub = createManyDatesRows(i, PARALLEL_DATE_COUNT);
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
                System.out.println("Rowcount: " + qres.length);
                for (Map row : qres) {
                    int id = (int) row.get("id");
                    System.out.println("Doing id: " + id);
                    for (int i = 1; i <= TS_COUNT; i++ ) {
                        String timestampTest = (String) row.get("ts" + i);
                        assertNotNull(timestampTest);
                        assert timestampTest.matches(timestampPattern);
                        Date found = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(timestampTest);
                        System.out.println("Datamap size: " + dataMap.size());
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
                e.printStackTrace();
                threadFailed.set(e);
                fail("Should not have thrown exception during select." + e);
            }
        };

        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < PARALLEL_DATE_COUNT; i++) {
            Thread t = new Thread(querier);
            threads.add(t);
            t.start();
        }

        System.out.println("Thread count: " + threads.size());
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
    }
    
    @Test
    public void testVantiqDateFormatting() throws VantiqSQLException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
        
        // Check that Source and Type do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
        assumeFalse(checkTypeExists());
                
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
        
        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(false, false));
        
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
        assertTrue("Expected " + FORMATTED_TIMESTAMP + ", but got " + timestamp,
                timestamp.equals(FORMATTED_TIMESTAMP));

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
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
        
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
                assertTrue("publish failed: " + publishResult, publishResult > 0);
            } catch (Exception e) {
                fail("No exception should be thrown when inserting null values: " + e.getMessage());
            }
            
            // Make sure collected data is identical to input data, and fail if any type of exception is caught
            try {
                queryResult = jdbc.processQuery(QUERY_NULL_VALUES);
                assert queryResult.length == 1;
                assert queryResult[0].size() == 5;
                if (i != 0) {
                    assert queryResult[0].get("ts").equals(FORMATTED_TIMESTAMP);
                }
                if (i != 1) {
                    assertTrue("Expected " + DATE + ", but got " + queryResult[0].get("testDate"),
                            queryResult[0].get("testDate").equals(DATE));
                }
                if (i != 2) {
                    assertTrue("Expected " + FORMATTED_TIME + ", but got " + queryResult[0].get("testTime"),
                         queryResult[0].get("testTime").equals(FORMATTED_TIME));
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
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword, false, 0);
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
    
    @Test
    public void testDBReconnect() throws VantiqSQLException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);
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
        
        jdbc.close();
    }
    
    @Test
    public void testMaxMessageSize() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source does not already exist in namespace, and skip test if it does
        assumeFalse(checkSourceExists());

        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(false, false));

        // Number of rows to insert in table;
        int numRows = 2000;

        // Publish to the source in order to create a table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_MAX_MESSAGE_SIZE);
        vantiq.publish("sources", testSourceName, create_params);

        // Insert 2000 rows into the the table
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", INSERT_ROW_MAX_MESSAGE_SIZE);
        for (int i = 0; i < numRows; i ++) {
            vantiq.publish("sources", testSourceName, insert_params);
        }

        // Query the Source without bundleFactor and make sure there were no errors
        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("query", QUERY_TABLE_MAX_MESSAGE_SIZE);
        VantiqResponse response = vantiq.query(testSourceName, params);
        JsonArray responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == numRows;
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
        Map<String,Object> drop_params = new LinkedHashMap<String,Object>();
        drop_params.put("query", DROP_TABLE_MAX_MESSAGE_SIZE);
        vantiq.publish("sources", testSourceName, drop_params);
        vantiq.publish("sources", testSourceName, create_params);

        // Check that lastRowBundle is null when the query returns no data
        params.remove("bundleFactor");
        response = vantiq.query(testSourceName, params);
        responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == 0;
        assert core.lastRowBundle == null;

        // Insert fewer rows, and make sure that using bundleFactor of 0 works
        numRows = 100;
        for (int i = 0; i < numRows; i ++) {
            vantiq.publish("sources", testSourceName, insert_params);
        }
        params.put("bundleFactor", 0);
        response = vantiq.query(testSourceName, params);
        responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == numRows;
        assert core.lastRowBundle.length == numRows;

        // Delete the Source from VANTIQ
        deleteSource();
    }

    @Test
    public void testAsynchronousProcessing() throws InterruptedException {
        doAsynchronousProcessing(true);
    }

    @Test
    public void testAsynchronousProcessingWithDefaults() throws InterruptedException {
        doAsynchronousProcessing(false);
    }

    public void doAsynchronousProcessing(boolean useCustomTaskConfig) throws InterruptedException {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source, Type, Topic, Procedure and Rule do not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());
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
        setupProcedure();

        // Create Rule to query the VANTIQ Source
        setupRule();

        // Publish to the source in order to create a table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_ASYNCH);
        vantiq.publish("sources", testSourceName, create_params);


        // Execute Procedure to trigger asynchronous publish/queries (assign to variable to ensure that procedure has finished before selecting from type)
        VantiqResponse response = vantiq.execute(testProcedureName, new LinkedHashMap<>());

        // Sleep for 5 seconds to make sure all queries have finished
        Thread.sleep(5000);

        // Select from the type and make sure all of our results are there as expected
        response = vantiq.select(testTypeName, null, null, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        assertEquals (500, responseBody.size());

        // Delete the table for next test
        Map<String,Object> delete_params = new LinkedHashMap<String,Object>();
        delete_params.put("query", DROP_TABLE_ASYNCH);
        vantiq.publish("sources", testSourceName, delete_params);

        // Delete the Source/Type/Topic/Procedure/Rule from VANTIQ
        deleteSource();
        deleteType();
        deleteTopic();
        deleteProcedure();
        deleteRule();
    }

    @Test
    public void testInvalidBatchProcessing() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source does not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());

        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(false, false));

        // Create table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_INVALID_BATCH);
        vantiq.publish("sources", testSourceName, create_params);

        // Creating a list of integers to insert as a batch
        ArrayList<Object> invalidBatch = new ArrayList<>();
        for (int i = 0; i<50; i++) {
            invalidBatch.add(10);
        }

        // Attempt to insert data into the table, which should fail
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", invalidBatch);
        vantiq.publish("sources", testSourceName, insert_params);

        // Query the table and make sure it is empty
        Map<String,Object> query_params = new LinkedHashMap<String,Object>();
        query_params.put("query", SELECT_TABLE_INVALID_BATCH);
        VantiqResponse response = vantiq.query(testSourceName, query_params);
        JsonArray responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == 0;

        // Now try a list of jibberish queries
        for (int i = 0; i<50; i++) {
            invalidBatch.add("jibberish");
        }

        // Attempt to insert data into the table, which should fail
        insert_params.put("query", invalidBatch);
        vantiq.publish("sources", testSourceName, insert_params);

        // Query the table and make sure it is empty
        response = vantiq.query(testSourceName, query_params);
        responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == 0;

        // Delete the Source
        deleteSource();
    }

    @Test
    public void testBatchProcessing() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source does not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());

        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(false, false));

        // Create table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_BATCH);
        vantiq.publish("sources", testSourceName, create_params);

        // Creating a list of strings to insert as a batch
        ArrayList<String> batch = new ArrayList<String>();
        for (int i = 0; i<50; i++) {
            batch.add(INSERT_TABLE_BATCH);
        }

        // Inserting data into the table as a batch
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", batch);
        VantiqResponse response = vantiq.publish("sources", testSourceName, insert_params);
        assert !response.hasErrors();

        // Select the data from table and make sure the response is valid
        Map<String,Object> query_params = new LinkedHashMap<String,Object>();
        query_params.put("query", SELECT_TABLE_BATCH);
        response = vantiq.query(testSourceName, query_params);
        JsonArray responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == 50;

        // Delete the Source
        deleteSource();
    }

    @Test
    public void testQueryUpdate() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source does not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());

        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(false, false));

        // Create table using query
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_QUERY);
        VantiqResponse response = vantiq.query(testSourceName, create_params);
        assert !response.hasErrors();

        // Inserting data into the table using query
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", INSERT_TABLE_QUERY);
        response = vantiq.query(testSourceName, insert_params);
        assert !response.hasErrors();

        // Select the data from table and make sure the previous queries worked
        Map<String,Object> query_params = new LinkedHashMap<String,Object>();
        query_params.put("query", SELECT_TABLE_QUERY);
        response = vantiq.query(testSourceName, query_params);
        JsonArray responseBody = (JsonArray) response.getBody();
        JsonObject bodyObject = responseBody.get(0).getAsJsonObject();
        assert bodyObject.get("id").getAsInt() == 1;
        assert bodyObject.get("name").getAsString().equals("Name");

        // Delete data from table using query
        Map<String,Object> delete_params = new LinkedHashMap<String,Object>();
        delete_params.put("query", DELETE_ROWS_QUERY);
        response = vantiq.query(testSourceName, delete_params);
        assert !response.hasErrors();

        // Double check that the delete worked
        response = vantiq.query(testSourceName, query_params);
        responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == 0;

        // Delete the Source
        deleteSource();
    }

    @Test
    public void testQueryUpdateBatch() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && jdbcDriverLoc != null);

        // Check that Source does not already exist in namespace, and skip test if they do
        assumeFalse(checkSourceExists());

        // Setup a VANTIQ JDBC Source, and start running the core
        setupSource(createSourceDef(false, false));

        // Create table
        Map<String,Object> create_params = new LinkedHashMap<String,Object>();
        create_params.put("query", CREATE_TABLE_BATCH_QUERY);
        vantiq.query(testSourceName, create_params);

        // Creating a list of strings to insert as a batch
        ArrayList<String> batch = new ArrayList<String>();
        for (int i = 0; i<50; i++) {
            batch.add(INSERT_TABLE_BATCH_QUERY);
        }

        // Inserting data into the table as a batch
        Map<String,Object> insert_params = new LinkedHashMap<String,Object>();
        insert_params.put("query", batch);
        VantiqResponse response = vantiq.query(testSourceName, insert_params);
        assert !response.hasErrors();

        // Select the data from table and make sure the response is valid
        Map<String,Object> query_params = new LinkedHashMap<String,Object>();
        query_params.put("query", SELECT_TABLE_BATCH_QUERY);
        response = vantiq.query(testSourceName, query_params);
        JsonArray responseBody = (JsonArray) response.getBody();
        assert responseBody.size() == 50;

        // Adding a select statement to batch, to make sure it does not execute
        batch.add(SELECT_TABLE_BATCH_QUERY);
        insert_params.put("query", batch);
        response = vantiq.query(testSourceName, insert_params);
        assert response.hasErrors();

        // Delete the Source
        deleteSource();
    }

    // ================================================= Helper functions =================================================

    public static Map createManyDatesRows(int id, int rowCount) {
//        TIMESTAMP = "2018-08-15 9:24:18";
        Instant inst = Instant.now().plus(id, ChronoUnit.DAYS);
        String instString = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss").format(Date.from(inst));
        LinkedHashMap<String, Object> resSet = new LinkedHashMap<>();
        String[] insertList = new String[rowCount];
        for (int i = 1; i <= rowCount; i++) {
            StringBuilder result = new StringBuilder("INSERT INTO TestDates VALUES (" + id + ", '");
            for (int j = 0; j < TS_COUNT; j++) {
                result.append(instString).append("', '");
            }
            result.append(" " + DATE + "', '" + TIME + "');");
            insertList[i -1] = result.toString();
            System.out.println(result);
        }
        resSet.put("id", id);
        resSet.put("expected", instString);
        resSet.put("batch", insertList);
        return resSet;
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
        where.put("name", "JDBC");
        VantiqResponse implResp = vantiq.select("system.sourceimpls", null, where, null);

        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new JDBCCore(testSourceName, testAuthToken, testVantiqServer);
            core.start(CORE_START_TIMEOUT);
        }
    }
    
    public static Map<String,Object> createSourceDef(boolean isAsynch, boolean useCustomTaskConfig) {
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
        if (isAsynch) {
            general.put("asynchronousProcessing", true);
            if (useCustomTaskConfig) {
                general.put("maxActiveTasks", 10);
                general.put("maxQueuedTasks", 20);
            }
        }
        
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
        VantiqResponse response = vantiq.delete("system.sources", where);
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
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }
    
    public static void deleteType() {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.delete("system.types", where);
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