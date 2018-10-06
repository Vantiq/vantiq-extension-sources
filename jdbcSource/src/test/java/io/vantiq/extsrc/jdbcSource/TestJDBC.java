package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestJDBC {
    
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    static String testDBDriver;
    
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
        testDBUsername = System.getProperty("TestDBUsername", null);
        testDBPassword = System.getProperty("TestDBPassword", null);
        testDBURL = System.getProperty("TestDBURL", null);
        testDBDriver = System.getProperty("TestDBDriver", null);
    }
    
    @Before
    public void setup() { 
        jdbc = new JDBC();
    }
    
    @AfterClass
    public static void tearDown() {
        if (testDBUsername != null && testDBPassword != null && testDBURL != null && testDBDriver != null) {
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
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && testDBDriver != null);
        jdbc.setupJDBC(testDBDriver, testDBURL, testDBUsername, testDBPassword);
        
        int queryResult;
        
        // Try processPublish with a nonsense query
        try {
            queryResult = jdbc.processPublish("jibberish");
            assert queryResult == 0;
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
        
        // Create table that will be used for testing
        try {
            queryResult = jdbc.processPublish(CREATE_TABLE);
            assert queryResult == 0;
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
        
        // Insert a row of data into the table
        try {
            queryResult = jdbc.processPublish(PUBLISH_QUERY);
            assert queryResult > 0;
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
    }
    
    @Test
    public void testProcessQuery() throws SQLException, LinkageError, ClassNotFoundException {
        assumeTrue(testDBUsername != null && testDBPassword != null && testDBURL != null && testDBDriver != null);
        jdbc.setupJDBC(testDBDriver, testDBURL, testDBUsername, testDBPassword);
        
        ResultSet queryResult;
        int deleteResult;
        
        // Try processQuery with a nonsense query
        try {
            queryResult = jdbc.processQuery("jibberish");
            assert queryResult == null;
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
        
        // Select the row that we previously inserted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            //assert queryResult != null;
            while(queryResult.next()) {
                assert queryResult.getInt("id") == 1;
                assert queryResult.getInt("age") == 25;
                assert queryResult.getString("first").equals("Santa");
                assert queryResult.getString("last").equals("Claus");
            }
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
        
        // Delete row from the table
        try {
            deleteResult = jdbc.processPublish(DELETE_ROW);
            assert deleteResult > 0;
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
        
        // Try selecting again, should return null since row was deleted
        try {
            queryResult = jdbc.processQuery(SELECT_QUERY);
            assert queryResult.next() == false;
        } catch (SQLException e) {
            fail("Should not throw an exception");
        }
    }
}
