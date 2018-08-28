package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extsrc.objectRecognition.NoSendORCore;
import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class TestFtpRetriever extends ObjRecTestBase {
    
    FtpRetriever retriever;
    NoSendORCore source;
    
    String ftpUrl   = "test.rebex.net";
    String ftpsUrl  = "test.rebex.net";
    String sftpUrl  = "test.rebex.net";
    
    @Before
    public void setup() {
        retriever = new FtpRetriever();
        source = new NoSendORCore(UNUSED, UNUSED, UNUSED, UNUSED);
    }
    
    @After
    public void tearDown() {
        retriever.close();
        source.close();
    }
    
    @Test
    public void testSetup() {
        Map<String, String> config = workingFtpConfig();
        
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        config = workingFtpConfig();
        config.remove("username");
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail when missing username");
        } catch (Exception e) {
            // Expected result
        }
        
        config = workingFtpConfig();
        config.remove("password");
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail when missing password");
        } catch (Exception e) {
            // Expected result
        }
        
        config = workingFtpConfig();
        config.remove("server");
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail when missing server");
        } catch (Exception e) {
            // Expected result
        }
        
        config = workingFtpConfig();
        config.remove("conType");
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should use default FTP when missing conType");
        }
    }
    
    @Test
    public void testFtpDownload() {
        Map<String, String> config = workingFtpConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "readme.txt");
        try {
            byte[] results = retriever.getImage(request);
            assert results != null;
            //assert results.length == 407;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file");
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
    }
    
    @Test
    public void testExplicitFtpsDownload() {
        Map<String, Object> config = workingExplicitFtpsConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "pub/example/readme.txt");
        try {
            byte[] results = retriever.getImage(request);
            assert results != null;
            assert results.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
    }
    
    @Test
    public void testImplicitFtpsDownload() {
        Map<String, Object> config = workingImplicitFtpsConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "pub/example/readme.txt");
        try {
            byte[] results = retriever.getImage(request);
            assert results != null;
            assert results.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
    }
    
    @Test
    public void testSftpDownload() {
        Map<String, String> config = workingSftpConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "readme.txt");
        try {
            byte[] results = retriever.getImage(request);
            assert results != null;
            assert results.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
    }
    
// ================================================= Helper functions =================================================
    public Map<String, String> workingFtpConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("server", ftpUrl);
        config.put("username", "demo");
        config.put("password", "password");
        config.put("conType", "ftp");
        
        return config;
    }
    public Map<String, Object> workingExplicitFtpsConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("server", ftpsUrl);
        config.put("username", "demo");
        config.put("password", "password");
        config.put("conType", "ftps");
        config.put("implicit", false);
        
        return config;
    }
    public Map<String, Object> workingImplicitFtpsConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("server", ftpsUrl);
        config.put("username", "demo");
        config.put("password", "password");
        config.put("conType", "ftps");
        config.put("implicit", true);
        
        return config;
    }
    public Map<String, String> workingSftpConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("server", sftpUrl);
        config.put("username", "demo");
        config.put("password", "password");
        config.put("conType", "sftp");
        
        return config;
    }
}
