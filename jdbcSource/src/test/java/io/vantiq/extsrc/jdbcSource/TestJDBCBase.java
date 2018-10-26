package io.vantiq.extsrc.jdbcSource;

import org.junit.BeforeClass;

public class TestJDBCBase {
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    static String jdbcDriverLoc;
    
    @BeforeClass
    public static void getProps() {
        testDBUsername = System.getProperty("EntConJDBCUsername", null);
        testDBPassword = System.getProperty("EntConJDBCPassword", null);
        testDBURL = System.getProperty("EntConJDBCURL", null);
        jdbcDriverLoc = System.getenv("JDBC_DRIVER_LOC");
    }
}
