/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import static org.junit.Assert.fail;

import java.security.Permission;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestJDBCMain {
    
    @Before
    public void setup() {
        System.setSecurityManager(new NoExit());
    }
    
    @After
    public void tearDown() {
        List<JDBCCore> sources = JDBCMain.sources;
        System.setSecurityManager(null);
        if (sources != null) {
            for (JDBCCore s : sources) {
                s.stop();
            }
            JDBCMain.sources = null;
        }
    }
    
    @Test
    public void testConfigs() {
        Properties props = new Properties();
        
        try {
            // Fail when no props are given
            JDBCMain.createSources(props);
            fail("Didn't exit when missing authToken and sources");
        } catch (ExitException e) {
            // Expected this Exception
        }
        
        try {
            // Fail when only authToken is set
            props.setProperty("authToken", "a token");
            JDBCMain.createSources(props);
            fail("Didn't exit when missing sources");
        } catch (ExitException e) {
            // Expected this Exception
        }
        
        // Succeed when authToken, targetVantiqServer, and sources are set
        props.setProperty("sources", "s");
        props.setProperty("targetServer", "url");
        JDBCMain.createSources(props);
        assert JDBCMain.targetVantiqServer.equals("url");
        JDBCCore s = JDBCMain.sources.get(0);
        assert s.getSourceName().equals("s");
        s.stop();
        
        try {
            // Fail when only sources is set
            props.remove("authToken");
            JDBCMain.createSources(props);
            fail("Didn't exit when missing authToken");
        } catch (ExitException e) {
            // Expected this Exception
        }
        
        String targetServer = "dev.vantiq.com";
        props.setProperty("authToken", "a token");
        props.setProperty("sources", "s2, s1  ");
        props.setProperty("targetServer", targetServer);
        JDBCMain.createSources(props);
        assert JDBCMain.targetVantiqServer.equals(targetServer);
        JDBCCore s1 = JDBCMain.sources.get(0);
        JDBCCore s2 = JDBCMain.sources.get(1);
        assert s1.getSourceName().equals("s1") || s2.getSourceName().equals("s1");
        assert s1.getSourceName().equals("s2") || s2.getSourceName().equals("s2");
        s1.stop();
        s2.stop();
    }
    
// ================================================= Helper functions =================================================
    
    private static class NoExit extends SecurityManager 
    {
        @Override
        public void checkPermission(Permission perm) {}
        @Override
        public void checkPermission(Permission perm, Object context) {}
        @Override
        public void checkExit(int status) 
        {
            super.checkExit(status);
            throw new ExitException("Exit Requested");
        }
    }
    
    protected static class ExitException extends SecurityException 
    {
        private static final long serialVersionUID = 1L;

        public ExitException(String string) {
            super(string);
        }
    }
}
