
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import static org.junit.Assert.fail;

import java.security.Permission;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestObjRecMain {
    
    @Before
    public void setup() {
        ObjectRecognitionMain.authToken             = "gcy1hHR39ge2PNCZeiUbYKAev-G7u-KyPh2Ns4gI0Y8=";
        ObjectRecognitionMain.targetVantiqServer    = "ws://localhost:8080";
        ObjectRecognitionMain.modelDirectory        = "models/";
        System.setSecurityManager(new NoExit());
    }
    
    @After
    public void tearDown() {
        List<ObjectRecognitionCore> sources = ObjectRecognitionMain.sources;
        System.setSecurityManager(null);
        if (sources != null) {
            for (ObjectRecognitionCore s : sources) {
                s.stop();
            }
            ObjectRecognitionMain.sources = null;
        }
    }
    
    @Test
    public void testConfigs() {
        Properties props = new Properties();
        
        try {
            // Fail when no props are given
            ObjectRecognitionMain.createSources(props);
            fail("Didn't exit when missing authToken and sources");
        } catch (ExitException e) {
            // Expected this Exception
        }
        
        try {
            // Fail when only auth is set
            props.setProperty("authToken", "a token");
            ObjectRecognitionMain.createSources(props);
            fail("Didn't exit when missing sources");
        } catch (ExitException e) {
            // Expected this Exception
        }
        
        // Succeed when auth and sources are set
        props.setProperty("sources", "s");
        ObjectRecognitionMain.createSources(props);
        assert ObjectRecognitionMain.modelDirectory.equals(ObjectRecognitionMain.DEFAULT_MODEL_DIRECTORY);
        assert ObjectRecognitionMain.targetVantiqServer.equals(ObjectRecognitionMain.DEFAULT_VANTIQ_SERVER);
        ObjectRecognitionCore s = ObjectRecognitionMain.sources.get(0);
        assert s.getSourceName().equals("s");
        s.stop();
        
        try {
            // Fail when only sources is set
            props.remove("authToken");
            ObjectRecognitionMain.createSources(props);
            fail("Didn't exit when missing authToken");
        } catch (ExitException e) {
            // Expected this Exception
        }
        
        String modelDirectory = "models";
        String targetServer = "dev.vantiq.com";
        props.setProperty("authToken", "a token");
        props.setProperty("sources", "s2, s1  ");
        props.setProperty("modelDirectory", modelDirectory);
        props.setProperty("targetServer", targetServer);
        ObjectRecognitionMain.createSources(props);
        assert ObjectRecognitionMain.modelDirectory.equals(modelDirectory);
        assert ObjectRecognitionMain.targetVantiqServer.equals(targetServer);
        ObjectRecognitionCore s1 = ObjectRecognitionMain.sources.get(0);
        ObjectRecognitionCore s2 = ObjectRecognitionMain.sources.get(1);
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
