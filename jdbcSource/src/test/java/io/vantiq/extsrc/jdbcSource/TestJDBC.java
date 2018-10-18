package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestJDBC {
    
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    
    // Queries to be tested
    static final String CREATE_TABLE = "create table Test(id int not null, age int not null, "
            + "first varchar (255), last varchar (255));";
    static final String PUBLISH_QUERY = "INSERT INTO Test VALUES (1, 25, 'Santa', 'Claus');";
    static final String SELECT_QUERY = "SELECT id, first, last, age FROM Test;";
    static final String DELETE_ROW = "DELETE FROM Test WHERE first='Santa';";
    static final String DELETE_TABLE = "DROP TABLE Test;";
    
    static JDBC jdbc;
        
    @BeforeClass
    public static void getProps() {
        testDBUsername = System.getProperty("EntConJDBCUsername", null);
        testDBPassword = System.getProperty("EntConJDBCPassword", null);
        testDBURL = System.getProperty("EntConJDBCURL", null);
    }
    
    @Before
    public void setup() { 
        jdbc = new JDBC();
    }
    
    @AfterClass
    public static void tearDown() {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null) {
            try {
                jdbc.processPublish(DELETE_TABLE);
            } catch (SQLException e) {
                //Shoudn't throw Exception
            }
        }
        jdbc.close();
    }
    
    @Test
    public void testProcessPublish() throws SQLException, LinkageError, ClassNotFoundException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        int queryResult;
        
        // Try processPublish with a nonsense query
        try {
            queryResult = jdbc.processPublish("jibberish");
            fail("Should have thrown an exception");
        } catch (SQLException e) {
            // Expected behavior
        }
        
        // Create table that will be used for testing
        try {
            queryResult = jdbc.processPublish(CREATE_TABLE);
            assert queryResult == 0;
        } catch (SQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Insert a row of data into the table
        try {
            queryResult = jdbc.processPublish(PUBLISH_QUERY);
            assert queryResult > 0;
        } catch (SQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
    }
    
    @Test
    public void testProcessQuery() throws SQLException, LinkageError, ClassNotFoundException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null);
        jdbc.setupJDBC(testDBURL, testDBUsername, testDBPassword);
        
        Map<String, ArrayList<HashMap>> queryResult;
        int deleteResult;
        
        // Try processQuery with a nonsense query
        try {
            queryResult = jdbc.processQuery("jibberish");
            fail("Should have thrown excpetion.");
        } catch (SQLException e) {
            // Expected behavior
        }
        
        // Select the row that we previously inserted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            assert (Integer) queryResult.get("queryResult").get(0).get("id") == 1;
            assert (Integer) queryResult.get("queryResult").get(0).get("age") == 25;
            assert queryResult.get("queryResult").get(0).get("first").equals("Santa");
            assert queryResult.get("queryResult").get(0).get("last").equals("Claus");
        } catch (SQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Delete row from the table
        try {
            deleteResult = jdbc.processPublish(DELETE_ROW);
            assert deleteResult > 0;
        } catch (SQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
        
        // Try selecting again, should return null since row was deleted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            assert queryResult == null;
        } catch (SQLException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }
    }
}
