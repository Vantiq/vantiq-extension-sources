/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.testConnector;

import static org.junit.Assert.fail;

import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestTestConnectorMain {

    @Before
    public void setup() {
        TestConnectorMain.setExitProcessor(new NoExit());
    }

    @After
    public void tearDown() {
        List<TestConnectorCore> sources = TestConnectorMain.sources;
        TestConnectorMain.setExitProcessor(null);
        if (sources != null) {
            for (TestConnectorCore s : sources) {
                s.stop();
            }
            TestConnectorMain.sources = null;
        }
    }

    @Test
    public void testConfigs() {
        Properties props = new Properties();

        try {
            // Fail when no props are given
            TestConnectorMain.createSources(props);
            fail("Didn't exit when missing authToken and sources");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: auth token was not specified.");
            // Expected this Exception
        }

        try {
            // Fail when only authToken is set
            props.setProperty("authToken", "a token");
            TestConnectorMain.createSources(props);
            fail("Didn't exit when missing sources");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: source(s) not specified.");
            // Expected this Exception
        }

        try {
            // Fail when only authToken and sources are set
            props.setProperty("sources", "a source");
            TestConnectorMain.createSources(props);
            fail("Didn't exit when missing server");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: target server not specified.");
            // Expected this Exception
        }

        // Succeed when authToken, targetVantiqServer, and sources are set
        props.setProperty("sources", "s");
        props.setProperty("targetServer", "url");
        TestConnectorMain.createSources(props);
        assert TestConnectorMain.targetVantiqServer.equals("url");
        TestConnectorCore s = TestConnectorMain.sources.get(0);
        assert s.getSourceName().equals("s");
        s.stop();

        try {
            // Fail when only sources is set
            props.remove("authToken");
            TestConnectorMain.createSources(props);
            fail("Didn't exit when missing authToken");
        } catch (ExitException e) {
            assert e.getMessage().equals("Exit Requested: auth token was not specified.");
            // Expected this Exception
        }

        String targetServer = "dev.vantiq.com";
        props.setProperty("authToken", "a token");
        props.setProperty("sources", "s2, s1  ");
        props.setProperty("targetServer", targetServer);
        TestConnectorMain.createSources(props);
        assert TestConnectorMain.targetVantiqServer.equals(targetServer);
        TestConnectorCore s1 = TestConnectorMain.sources.get(0);
        TestConnectorCore s2 = TestConnectorMain.sources.get(1);
        assert s1.getSourceName().equals("s1") || s2.getSourceName().equals("s1");
        assert s1.getSourceName().equals("s2") || s2.getSourceName().equals("s2");
        s1.stop();
        s2.stop();
    }

// ================================================= Helper functions =================================================

    private static class NoExit implements TestConnectorMain.ExitProcessor {
        @Override
        public void processExit(int status)
        {
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
