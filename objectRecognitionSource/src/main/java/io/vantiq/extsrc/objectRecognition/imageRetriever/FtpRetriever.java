package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class FtpRetriever implements ImageRetrieverInterface {

    final static String FTPS = "ftps";
    final static String SFTP = "sftp";
    final static String FTP  = "ftp";
    
    Logger log;
    
    String  conType     = FTP;
    String  password    = null;
    String  username    = null;
    String  server      = null;
    boolean isImplicit  = false;
    
    FTPClient ftpClient;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + source.getSourceName());
        
        if (dataSourceConfig.get("server") instanceof String) {
            server = (String) dataSourceConfig.get("server");
        } else {
            throw new Exception(this.getClass().getCanonicalName() + ".noServerSpecified: "
                    + "No server was specified in the configuration setup." );
        }
        
        if (dataSourceConfig.get("conType") instanceof String) {
            String type = (String) dataSourceConfig.get("conType");
            if (type.equalsIgnoreCase("ftps")) {
                conType = FTPS;
                if (dataSourceConfig.get("implicit") instanceof Boolean && (Boolean) dataSourceConfig.get("implicit")) {
                    isImplicit = true;
                }
            } else if (type.equalsIgnoreCase("sftp")) {
                conType = SFTP;
            } else if (!type.equalsIgnoreCase("ftp")) {
                log.warn("Unexpected value of 'conType'. Should be 'ftps', 'sftp', or 'ftp'. Defaulting to 'ftp'");
            }
        }
        
        if (dataSourceConfig.get("username") instanceof String) {
            username = (String) dataSourceConfig.get("username");
        } else {
            throw new Exception(this.getClass().getCanonicalName() + ".noPassword: "
                    + "No password was given in the configuration setup");
        }
        if (dataSourceConfig.get("password") instanceof String) {
            password = (String) dataSourceConfig.get("password");
        } else {
            throw new Exception(this.getClass().getCanonicalName() + ".noUsername: "
                    + "No username was given in the configuration setup");
        }
        
        if (conType == FTP || conType == FTPS) { 
            try {
                ftpClient = connectToFtpServer(server, username, password, conType, isImplicit);
            } catch (IOException e) {
                throw new Exception(this.getClass().getCanonicalName() + ".ftpClientSetup: "
                        + "Attempt to connect to the server threw an error with message: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Attempts to connect to server using the arguments passed and returns the result. If {@code conType} is {@link #FTP} or {@link #FTPS}
     * then an {@link #ftpClient} is set to the correct type. 
     * @param server        The server url to connect to
     * @param username      The username to connect with
     * @param password      The password to connect with
     * @param conType       The type of connection to use, one of {@link #FTP}, {@link #FTPS}
     * @return              The server 
     * @throws IOException  When an attempt to connect with FTPClient throws an IOException
     */
    public FTPClient connectToFtpServer(String server, String username, 
                String password, String conType, boolean isImplicit) throws IOException {
        FTPClient client;
        if (conType == FTPS) {
            client = new FTPSClient(isImplicit);
            ((FTPSClient) client).setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
        } else {
            client = new FTPClient();
        }
        
        client.connect(server);
        
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new ConnectException(this.getClass().getCanonicalName() + ".failedConnection: "
                    + "Could not connect to server '" + server + "'");
        }
        
        boolean success = client.login(username, password);
        if (!success) {
            client.logout();
            client.disconnect();
            throw new ConnectException(this.getClass().getCanonicalName() + ".failedLogin: "
                    + "Could not log into server '" + server + "' using the given credentials");
        }
        
        return client;
    }

    @Override
    public byte[] getImage() throws ImageAcquisitionException {
        throw new FatalImageException(this.getClass().getCanonicalName() + ".pollingNotAllowed: "
                + "Polling is not allowed for the FTPRetriever.");
    }

    @Override
    public byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException {
        boolean newServer = false; // Do we need to create a new server for this request
        
        String      fileName;
        String      server;
        String      conType;
        String      username;
        String      password;
        boolean     isImplicit = false;
        FTPClient   ftpClient;
        
        if (request.get("DSfile") instanceof String) {
            fileName = (String) request.get("DSfile");
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryMissingFile: "
                    + "Requires a file placed in the 'DSfile' option in order to obtain an image.");
        }
        
        if (request.get("DSserver") instanceof String) {
            server = (String) request.get("DSserver");
            if (!server.equals(this.server)) {
                newServer = true;
            }
        } else {
            server = this.server;
        }
        
        if (request.get("DSconType") instanceof String) {
            String type = (String) request.get("DSconType");
            if (type.equalsIgnoreCase("ftps")) {
                conType = FTPS;
                if (request.get("DSimplicit") instanceof Boolean && (Boolean) request.get("DSimplicit")) {
                    isImplicit = true;
                }
            } else if (type.equalsIgnoreCase("sftp")) {
                conType = SFTP;
            } else if (type.equalsIgnoreCase("ftp")) {
                conType = FTP;
            } else {
                conType = FTP;
                log.warn("Unexpected value of 'DSconType'. Should be 'ftps', 'sftp', or 'ftp'. Defaulting to 'ftp'");
            }
            if (!conType.equals(this.conType)) {
                newServer = true;
            }
        } else {
            conType = this.conType;
        }
        
        if (request.get("DSusername") instanceof String) {
            username = (String) request.get("DSusername");
            if (!username.equals(this.username)) {
                newServer = true;
            }
        } else {
            username = this.username;
        }
        if (request.get("DSpassword") instanceof String) {
            password = (String) request.get("DSpassword");
            if (!password.equals(this.password)) {
                newServer = true;
            }
        } else {
            password = this.password;
        }
        
        if (newServer) {
            try {
                ftpClient = connectToFtpServer(server, username, password, conType, isImplicit);
            } catch (IOException e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryFailedConnection: "
                        + "Could not connect to server '" + server + "' with given username and password", e); 
            }
        } else {
            ftpClient = this.ftpClient;
        }
        
        ByteArrayOutputStream image = new ByteArrayOutputStream(64 * 1024); // Start at 64 kB
        try {
            
            boolean success = ftpClient.retrieveFile(fileName, image);
            if (newServer) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
            if (!success) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileRetrievalError: "
                        + "File could not be retreived. Most likely the file could not be found.");
            }
        } catch (IOException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileRetrievalError: "
                    + "Could not read file '" + fileName + "' from server '" + server 
                    + "' with given username and password. Error message was: " + e.getMessage(), e); 
        }
        return image.toByteArray();
    }

    @Override
    public void close() {
        if (ftpClient != null) {
            try {
                ftpClient.logout();
            } catch (IOException e) {
                log.warn("Problem logging out of server '" + server + "' in close()", e);
            }
            try {
                ftpClient.disconnect();
            } catch (IOException e) {
                log.warn("Problem disconnecting from server '" + server + "' in close()", e);
            }
            ftpClient = null;
        }
    }
}
