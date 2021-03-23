/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

import static org.junit.Assert.fail;

import java.security.Permission;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestFTPClientMain {
    
    @Before
    public void setup() {
        System.setSecurityManager(new NoExit());
    }
    
    @After
    public void tearDown() {
        List<FTPClientCore> sources = FTPClientMain.sources;
        System.setSecurityManager(null);
        if (sources != null) {
            for (FTPClientCore s : sources) {
                s.stop();
            }
            FTPClientMain.sources = null;
        }
    }
    
    @Test
    public void testConfigs() {
        Properties props = new Properties();
        
        try {
            // Fail when no props are given
            FTPClientMain.createSources(props);
            fail("Didn't exit when missing authToken and sources");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: auth token was not specified.");
            // Expected this Exception
        }
        
        try {
            // Fail when only authToken is set
            props.setProperty("authToken", "a token");
            FTPClientMain.createSources(props);
            fail("Didn't exit when missing sources");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: source(s) not specified.");
            // Expected this Exception
        }
        
        try {
            // Fail when only authToken and sources are set
            props.setProperty("sources", "a source");
            FTPClientMain.createSources(props);
            fail("Didn't exit when missing server");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: target server not specified.");
            // Expected this Exception
        }
        
        // Succeed when authToken, targetVantiqServer, and sources are set
        props.setProperty("sources", "s");
        props.setProperty("targetServer", "url");
        FTPClientMain.createSources(props);
        assert FTPClientMain.targetVantiqServer.equals("url");
        FTPClientCore s = FTPClientMain.sources.get(0);
        assert s.getSourceName().equals("s");
        s.stop();
        
        try {
            // Fail when only sources is set
            props.remove("authToken");
            FTPClientMain.createSources(props);
            fail("Didn't exit when missing authToken");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: auth token was not specified.");
            // Expected this Exception
        }
        
        String targetServer = "dev.vantiq.com";
        props.setProperty("authToken", "a token");
        props.setProperty("sources", "s2, s1  ");
        props.setProperty("targetServer", targetServer);
        FTPClientMain.createSources(props);
        assert FTPClientMain.targetVantiqServer.equals(targetServer);
        FTPClientCore s1 = FTPClientMain.sources.get(0);
        FTPClientCore s2 = FTPClientMain.sources.get(1);
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
            if (status == 1) {
                throw new ExitException("Exit Requested: auth token was not specified.");
            } else if (status == 2) {
                throw new ExitException("Exit Requested: source(s) not specified.");
            } else if (status == 3) {
                throw new ExitException("Exit Requested: target server not specified.");
            } else {
                throw new ExitException("Exit Requested");
            }
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
