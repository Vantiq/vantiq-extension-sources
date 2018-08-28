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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

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
    String  protocol    = "SSL";
    boolean isImplicit  = false;
    
    FTPClient ftpClient;
    Session   session;
    
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
                if (dataSourceConfig.get("protocol") instanceof String) {
                    protocol = (String) dataSourceConfig.get("protocol");
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
                ftpClient = connectToFtpServer(server, username, password, conType, protocol, isImplicit);
            } catch (IOException e) {
                throw new Exception(this.getClass().getCanonicalName() + ".ftpClientSetup: "
                        + "Attempt to connect to the server threw an error with message: " + e.getMessage(), e);
            }
        } else {
            session = connectToSftpServer(server, username, password);
        }
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
        String      protocol   = null;
        boolean     isImplicit = false;
        FTPClient   ftpClient;
        Session     session;
        
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
                    if (isImplicit != this.isImplicit) {
                        newServer = true;
                    }
                } else {
                    isImplicit = this.isImplicit;
                }
                if (request.get("protocol") instanceof String) {
                    protocol = (String) request.get("protocol");
                    if (!protocol.equals(this.protocol)) {
                        newServer = true;
                    }
                } else {
                    protocol = this.protocol;
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
        
        ftpClient = this.ftpClient;
        session = this.session;
        if (newServer) {
            if (conType == FTP || conType == FTPS) {
                try {
                    ftpClient = connectToFtpServer(server, username, password, conType, protocol, isImplicit);
                } catch (IOException e) {
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryFailedConnection: "
                            + "Could not connect to server '" + server + "' with given username and password", e); 
                }
            } else {
                try {
                    session = connectToSftpServer(server, username, password);
                } catch (Exception e) {
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryFailedConnection: "
                            + "", e);
                }
            }
        }

        byte[] results;
        
        try {
            if (conType == FTP || conType == FTPS) {
                results = readFromFtpServer(ftpClient, fileName); 
            } else {
                results = readFromSftpServer(session, fileName);
            }
        } finally {
            if (newServer) {
                if (conType == FTP || conType == FTPS) {
                    try {
                        ftpClient.logout();
                    } catch (IOException e) {
                        // Do nothing
                    }
                    try {
                        ftpClient.disconnect();
                    } catch (IOException e) {
                        // Do nothing
                    }
                } else {
                    session.disconnect();
                }
            }
        }
        return results;
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
        if (session != null) {
            session.disconnect();
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
                String password, String conType, String protocol, boolean isImplicit) throws IOException {
        FTPClient client;
        if (conType == FTPS) {
            client = new FTPSClient(protocol, isImplicit);
            ((FTPSClient) client).setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
            client.enterLocalPassiveMode();
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
    
    public Session connectToSftpServer(String server, String username, String password) throws Exception {
        JSch jsch = new JSch();
        Session sess;
        try {
            sess = jsch.getSession(username, server);
        } catch (JSchException e) {
            throw new Exception(this.getClass().getCanonicalName() + ".failedSftpSessionSetup: "
                    + "Could not create session at host '" + server + "' with given username");
        }
        
        sess.setConfig("StrictHostKeyChecking", "no");
        sess.setPassword(password);
        
        try {
            sess.connect();
        } catch (JSchException e) {
            throw new Exception(this.getClass().getCanonicalName() + ".failedSftpConnection: "
                    + "Could not connect to session at host '" + server + "' with given username and password.", e);
        }
        
        return sess;
    }
    
    public byte[] readFromFtpServer(FTPClient client, String fileName) throws ImageAcquisitionException {
        ByteArrayOutputStream image = new ByteArrayOutputStream(64 * 1024); // Start at 64 kB
        try {
            
            boolean success = client.retrieveFile(fileName, image);
            
            if (!success) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileRetrievalError: "
                        + "File could not be retreived. Most likely the file could not be found. "
                        + "Reply was : " + client.getReplyString());
            }
        } catch(IOException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileConnectionError: "
                    + "Could not read file '" + fileName + "' from server '" 
                    + client.getRemoteAddress().getCanonicalHostName()
                    + "' with given username and password. Error message was: " + e.getMessage(), e); 
        }
        return image.toByteArray();
    }
    
    public byte[] readFromSftpServer(Session session, String fileName) throws ImageAcquisitionException {
        
        ChannelSftp sftpChannel;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpChannelFailure: "
                    + "Could not connect to server with SFTP protocol. The target server may not be configured for SFTP"
                    , e);
        }
        
        ByteArrayOutputStream image = new ByteArrayOutputStream(64 * 1024); // Start at 64 kB
        try {
            sftpChannel.get(fileName, image);
        } catch (SftpException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpRetrieval: "
                    + "Error when trying to retrieve file '" + fileName + "' from server", e);
        } finally {
            sftpChannel.exit();
        }
        
        return image.toByteArray();
    }
}
