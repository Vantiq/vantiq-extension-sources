package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

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
    
    String ftpUrl   = "speedtest.tele2.net";
    String ftpsUrl  = "ftps.cs.brown.edu";
    String sftpUrl  = "demo.wftpserver.com";
    
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
        assumeFtpConnect();
        
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
        assumeFtpConnect();
        
        Map<String, String> config = workingFtpConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "1KB.zip");
        try {
            byte[] results = retriever.getImage(request);
            assert results != null;
            assert results.length == 1024;
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
    public void testFtpsDownload() {
        //assumeFtpsConnect();
        
        Map<String, Object> config = workingFtpsConfig();
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
        //assumeFtpsConnect();
        
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
        config.put("username", "anonymous");
        config.put("password", UNUSED);
        config.put("conType", "ftp");
        
        return config;
    }
    public Map<String, Object> workingFtpsConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("server", ftpsUrl);
        config.put("username", "anonymous");
        config.put("password", UNUSED);
        config.put("conType", "ftps");
        config.put("implicit", true);
        
        return config;
    }
    public Map<String, String> workingSftpConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("server", sftpUrl);
        config.put("username", "demo-user");
        config.put("password", "demo-user");
        config.put("conType", "sftp");
        
        return config;
    }
    
    void assumeFtpConnect() {
        assumeTrue("Could not connect to test url", checkUrl("ftp://" + ftpUrl));
    }
    void assumeFtpsConnect() {
        assumeTrue("Could not connect to test url", checkUrl("ftps://" + ftpsUrl) || checkUrl("ftpes://" + ftpsUrl));
    }
    void assumeSftpConnect() {
        assumeTrue("Could not connect to test url", checkUrl("sftp://" + ftpUrl));
    }
}
