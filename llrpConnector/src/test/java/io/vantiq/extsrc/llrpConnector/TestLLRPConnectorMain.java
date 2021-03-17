/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.llrpConnector;

import static org.junit.Assert.fail;

import java.security.Permission;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestLLRPConnectorMain {

    @Before
    public void setup() {
        System.setSecurityManager(new NoExit());
    }

    @After
    public void tearDown() {
        List<LLRPConnectorCore> sources = LLRPConnectorMain.sources;
        System.setSecurityManager(null);
        if (sources != null) {
            for (LLRPConnectorCore s : sources) {
                s.stop();
            }
            LLRPConnectorMain.sources = null;
        }
    }

    @Test
    public void llrpConfigs() {
        Properties props = new Properties();

        try {
            // Fail when no props are given
            LLRPConnectorMain.createSources(props);
            fail("Didn't exit when missing authToken and sources");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: auth token was not specified.");
            // Expected this Exception
        }

        try {
            // Fail when only authToken is set
            props.setProperty("authToken", "a token");
            LLRPConnectorMain.createSources(props);
            fail("Didn't exit when missing sources");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: source(s) not specified.");
            // Expected this Exception
        }

        try {
            // Fail when only authToken and sources are set
            props.setProperty("sources", "a source");
            LLRPConnectorMain.createSources(props);
            fail("Didn't exit when missing server");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: target server not specified.");
            // Expected this Exception
        }

        // Succeed when authToken, targetVantiqServer, and sources are set
        props.setProperty("sources", "s");
        props.setProperty("targetServer", "url");
        LLRPConnectorMain.createSources(props);
        assert LLRPConnectorMain.targetVantiqServer.equals("url");
        LLRPConnectorCore s = LLRPConnectorMain.sources.get(0);
        assert s.getSourceName().equals("s");
        s.stop();

        try {
            // Fail when only sources is set
            props.remove("authToken");
            LLRPConnectorMain.createSources(props);
            fail("Didn't exit when missing authToken");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: auth token was not specified.");
            // Expected this Exception
        }

        String targetServer = "internal.vantiq.com";
        props.setProperty("authToken", "a token");
        props.setProperty("sources", "s2, s1  ");
        props.setProperty("targetServer", targetServer);
        LLRPConnectorMain.createSources(props);
        assert LLRPConnectorMain.targetVantiqServer.equals(targetServer);
        LLRPConnectorCore s1 = LLRPConnectorMain.sources.get(0);
        LLRPConnectorCore s2 = LLRPConnectorMain.sources.get(1);
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