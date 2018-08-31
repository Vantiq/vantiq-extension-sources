package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.assertTrue;
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
    
    // final static int IMAGE_FILE_SIZE = 19872;
    
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
            assertTrue("Failure should be caused by a missing username. Error actually was: " + e.getMessage()
                    , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".noUsername"));
        }
        
        config = workingFtpConfig();
        config.remove("password");
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail when missing password");
        } catch (Exception e) {
            assertTrue("Failure should be caused by a missing password. Error actually was: " + e.getMessage()
                    , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".noPassword"));
        }
        
        config = workingFtpConfig();
        config.remove("server");
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail when missing server");
        } catch (Exception e) {
            assertTrue("Failure should be caused by a missing server URL. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".noServerSpecified"));
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
        request.put("DSfile", "pub/example/winceclient.png");
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message was: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by file not retrieving. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".fileRetrievalError"));
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
        request.put("DSfile", "pub/example/winceclient.png");
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by file not retrieving. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".fileRetrievalError"));
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
        request.put("DSfile", "pub/example/winceclient.png");
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by file not retrieving. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".fileRetrievalError"));
        }
    }
    
    @Test
    public void testSftpDownload() {
        Map<String, String> config = workingSftpConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. Exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "pub/example/winceclient.png");
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        request.put("DSfile", "notAFile");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for invalid sample file");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by file not retrieving. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".sftpRetrieval"));
        }
    }
    
    @Test
    public void testNonImageFile() {
        Map<String, String> config = workingFtpConfig();
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSfile", "pub/example/readme.txt");
        try {
            retriever.getImage(request);
            fail("Should throw exception trying for a non-image file");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by file being unreadable. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".unreadableFileType"));
        }
    }
    
    @Test
    public void testQueryServer() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("noDefault", true);
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = fullFtpRequest();
        request.put("DSfile", "pub/example/winceclient.png");
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for a valid new server. Message was: " + e.getMessage());
        }
    }
    
    @Test
    public void testIncompleteQuery() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("noDefault", true);
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        Map<String, String> request = fullFtpRequest();
        request.put("DSfile", "pub/example/winceclient.png");
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should no throw exception for valid file. Message was: " + e.getMessage());
        }
        
        request = fullFtpRequest();
        request.put("DSfile", "pub/example/winceclient.png");
        request.remove("DSserver");
        try {
            retriever.getImage(request);
            fail("Should throw exception when missing server and has no defaults");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by missing the server URL. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".queryNoDefaultServer"));
        }
        
        request = fullFtpRequest();
        request.put("DSfile", "pub/example/winceclient.png");
        request.remove("DSusername");
        try {
            retriever.getImage(request);
            fail("Should throw exception when missing username and has no defaults");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by missing username. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".queryNoDefaultUsername"));
        }
        
        request = fullFtpRequest();
        request.put("DSfile", "pub/example/winceclient.png");
        request.remove("DSpassword");
        try {
            retriever.getImage(request);
            fail("Should throw exception when missing password and has no defaults");
        } catch (ImageAcquisitionException e) {
            assertTrue("Failure should be caused by missing password. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".queryNoDefaultPassword"));
        }
        
        request = fullFtpRequest();
        request.put("DSfile", "pub/example/winceclient.png");
        request.remove("DSconType");
        try {
            retriever.getImage(request);
        } catch (ImageAcquisitionException e) {
            fail("Should succeed when missing conType and has no defaults");
        }
    }
    
    @Test
    public void testBadConnections() {
        Map<String, String> config = workingFtpConfig();
        config.put("username", "VERY MUCH INVALID");
        
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail with invalid username");
        } catch (Exception e) {
            assertTrue("Failure should be caused by bad login. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".failedLogin"));
        }
        
        retriever.close();
        retriever = new FtpRetriever();
        
        config = workingSftpConfig();
        config.put("password", "VERY MUCH INVALID");
        
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail with invalid password");
        } catch (Exception e) {
            assertTrue("Failure should be caused by bad login. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".failedSftpConnection"));
        }
        
        retriever.close();
        retriever = new FtpRetriever();
        
        config = workingSftpConfig();
        config.put("server", "VERY MUCH INVALID");
        
        try {
            retriever.setupDataRetrieval(config, source);
            fail("Should fail with invalid server");
        } catch (Exception e) {
            assertTrue("Failure should be caused by failed connection. Error actually was: " + e.getMessage()
                , e.getMessage().startsWith(FtpRetriever.class.getCanonicalName() + ".failedSftpConnection"));
        }
    }
    
    @Test
    public void testReuseOldConnectionOnSameSettings() {
        Map<String, Object> config = workingImplicitFtpsConfig();
        config.put("protocol", "SSL"); 
        try {
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Should not fail with full config. exception message was : " + e.getMessage());
        }
        
        // Copy all the values in config into the request Map
        Map<String, Object> request = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            request.put("DS" + entry.getKey(), entry.getValue());
        }
        request.put("DSfile", "pub/example/winceclient.png");
        
        String lastReply = retriever.ftpClient.getReplyString();
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        
        // Make sure a new message has been received since the last attempt
        assert lastReply != retriever.ftpClient.getReplyString();
        
        lastReply = retriever.ftpClient.getReplyString();
        request.put("DSimplicit", !((Boolean)request.get("DSimplicit")));
        try {
            ImageRetrieverResults imgResults = retriever.getImage(request);
            assert imgResults != null;
            byte[] results = imgResults.getImage();
            assert results != null;
            assert results.length == 19871;
        } catch (ImageAcquisitionException e) {
            fail("Should not throw exception trying for the sample file. Message: " + e.getMessage());
        }
        // Make sure the reply has not changed
        assert lastReply == retriever.ftpClient.getReplyString();
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
    
    public Map<String, String> fullFtpRequest() {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("DSserver", ftpUrl);
        request.put("DSusername", "demo");
        request.put("DSpassword", "password");
        request.put("DSconType", "ftp");
        
        return request;
    }
}
