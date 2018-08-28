package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Map;

import javax.imageio.ImageIO;

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

/**
 * This implementation can read files from FTP, FTPS, and SFTP servers. Not all options available for each protocol are
 * implemented. Only Queries are allowed, for which {@code file} is necessary. The server created at initialization will
 * be used if no new options are set for the server and {@code noDefault} was not set to {@code true} for the
 * configuration. If some but notall options are set for a Query, then the values from the initial configuration are
 * used.
 * <br>
 *
 * Errors are thrown whenever an image or video frame cannot be read. Fatal errors are thrown only when the initial
 * server cannot be created.
 * <br>
 * The options are as follows. Remember to prepend "DS" when using an option in a Query.
 * <ul>
 *   <li>{@code noDefault}: Optional. Config only. When true, no default server is created and no default settings are
 *                  saved. This means that when true all options without defaults are required for Queries. When false
 *                  or unset, <i>all other Configuration settings without default values are required</i>.
 *   <li>{@code server}: Optional. Config and Query. The URL of the server to connect to. It is preferred if the URL
 *                  is only the domain name, e.g. "site.name.com", though the source will try to obtain the domain name
 *                  from a URL if necessary.
 *   <li>{@code username}: Optional. Config and Query. The username for the given server.
 *   <li>{@code password}: Optional. Config and Query. The password for the given server.
 *   <li>{@code conType}: Optional. Config and Query. The type of connection you would like, one of "FTP", "FTPS", or
 *                  "SFTP". Case sensitivity does not matter. Defaults to "FTP".
 *   <li>{@code implicit}: Optional. Config and Query. For FTPS only. Whether to connect using implicit security.
 *                  Defaults to false (i.e. explicit mode).
 *   <li>{@code protocol}: Optional. Config and Query. For FTPS only. Which security mechanism to use. Typically either
 *                  "SSL" or "TLS". Default to "TLS". 
 * </ul>
 */
public class FtpRetriever implements ImageRetrieverInterface {

    final static String FTPS = "ftps";
    final static String SFTP = "sftp";
    final static String FTP  = "ftp";
    
    final static String DEFAULT_PROTOCOL = "TLS";
    
    Logger log;

    boolean noDefault = false;
    
    String  conType     = FTP;
    String  password    = null;
    String  username    = null;
    String  server      = null;
    String  protocol    = DEFAULT_PROTOCOL;
    boolean isImplicit  = false;
    
    FTPClient ftpClient = null;
    Session   session = null;
    
    // TODO more options for FTPS secure protocols, 
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + source.getSourceName());
        
        if (dataSourceConfig.get("noDefault") instanceof Boolean && (Boolean) dataSourceConfig.get("noDefault")) {
            noDefault = true;
            return;
        }
        
        if (dataSourceConfig.get("server") instanceof String) {
            server = stripDomainName((String) dataSourceConfig.get("server"));
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
            server = stripDomainName((String) request.get("DSserver"));
            if (!server.equals(this.server)) {
                newServer = true;
            }
        } else if (!noDefault) {
            server = this.server;
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryNoDefaultServer: "
                    + "Server option required when configured with no defaults");
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
                } else if (!noDefault) {
                    protocol = this.protocol;
                } else {
                    protocol = DEFAULT_PROTOCOL;
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
        } else if (!noDefault) {
            username = this.username;
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryNoDefaultUsername: "
                    + "Username option required when configured with no defaults");
        }
        if (request.get("DSpassword") instanceof String) {
            password = (String) request.get("DSpassword");
            if (!password.equals(this.password)) {
                newServer = true;
            }
        } else if (!noDefault) {
            password = this.password;
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryNoDefaultPassword: "
                    + "Password option required when configured with no defaults");
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

        ByteArrayOutputStream file;
        
        try {
            if (conType == FTP || conType == FTPS) {
                file = readFromFtpServer(ftpClient, fileName); 
            } else {
                file = readFromSftpServer(session, fileName);
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
        
        ByteArrayInputStream fileInput = new ByteArrayInputStream(file.toByteArray());
        BufferedImage image;
        
        try {
            image = ImageIO.read(fileInput);
        } catch (IOException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileParseError: "
                    + "Error occurred while reading the file"
                    , e);
        }
        
        if (image == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".unreadableFileType: "
                    + "Could not understand the requested file. Only " + Arrays.asList(ImageIO.getReaderFormatNames()));
        }
        
        ByteArrayOutputStream results = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpeg", results);
        } catch (IOException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".untranslatableFileType: "
                    + "Could not translate the requested image to jpeg.", e);
        }
        return results.toByteArray();
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
     * @param protocol      The security protocol to use, typically "SSL" or "TLS". FTPS only.
     * @param isImplicit    Whether the target server uses implicit security. FTPS only.
     * @return              A client connected to {@code server}
     * @throws IOException  When an attempt to connect with FTPClient throws an IOException
     */
    public FTPClient connectToFtpServer(String server, String username,
                String password, String conType, String protocol, boolean isImplicit) throws IOException {
        FTPClient client;
        if (conType == FTPS) {
            client = new FTPSClient(protocol, isImplicit);
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
        
        client.enterLocalPassiveMode(); // So it can get around firewalls
        
        // Only needs to be done for FTPS implicit. Unclear why only implicit requires it
        if (conType == FTPS && isImplicit) {
            // Necessary, not entirely clear on why it is necessary only for implicit
            // PBSZ sets the size of the "protection buffer" and PROT sets the security level
            ((FTPSClient) client).execPBSZ(0); 
            ((FTPSClient) client).execPROT("P"); 
            
            reply = client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                client.disconnect();
                throw new IOException(this.getClass().getCanonicalName() + ".failedSecurityNegotiation: "
                        + "Could not setup proper security for server '" + server + "'");
            }
        }
        
        // Login to the server
        boolean success = client.login(username, password);
        if (!success) {
            client.logout();
            client.disconnect();
            throw new ConnectException(this.getClass().getCanonicalName() + ".failedLogin: "
                    + "Could not log into server '" + server + "' using the given credentials");
        }
        
        return client;
    }
    
    /**
     * Creates a JSch Session connected to the target server using the given credentials. Currently does not check
     * the host's credentials
     * @param server        The domain name of the server to connect to, e.g. "site.name.com" but not
     *                      "sftp://site.name.com" or "site.name.com/location"
     * @param username      The username to connect with
     * @param password      The password to connect with
     * @return              A Session connected to the target server
     * @throws Exception    Thrown when the server cannot be connected to for any reason
     */
    public Session connectToSftpServer(String server, String username, String password) throws Exception {
        JSch jsch = new JSch();
        Session sess;
        try {
            sess = jsch.getSession(username, server);
        } catch (JSchException e) {
            throw new Exception(this.getClass().getCanonicalName() + ".failedSftpSessionSetup: "
                    + "Could not create session at host '" + server + "' with given username");
        }
        
        sess.setConfig("StrictHostKeyChecking", "no"); // Accept any connection made
        sess.setPassword(password);
        
        try {
            sess.connect();
        } catch (JSchException e) {
            throw new Exception(this.getClass().getCanonicalName() + ".failedSftpConnection: "
                    + "Could not connect to session at host '" + server + "' with given username and password.", e);
        }
        
        return sess;
    }
    
    /**
     * Read the specified file from {@code client}
     * @param client                        The client to use for reading.
     * @param filePath                      The path to the file to read.
     * @return                              The bytes of the file.
     * @throws ImageAcquisitionException    Thrown when the image cannot be retrieved for any reason
     */
    public ByteArrayOutputStream readFromFtpServer(FTPClient client, String filePath) throws ImageAcquisitionException {
        ByteArrayOutputStream image = new ByteArrayOutputStream(64 * 1024); // Start at 64 kB
        try {
            
            boolean success = client.retrieveFile(filePath, image);
            
            if (!success) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileRetrievalError: "
                        + "File could not be retreived. "
                        + "Reply was : " + client.getReplyString());
            }
        } catch(IOException e) {
            e.printStackTrace();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileConnectionError: "
                    + "Could not read file '" + filePath + "' from server '" 
                    + client.getRemoteAddress().getCanonicalHostName()
                    + "' with given username and password. Error message was: " + e.getMessage(), e); 
        }
        return image;
    }
    
    /**
     * Read the specified file from {@code session}
     * @param session                       The session to use for reading.
     * @param filePath                      The path to the file to read.
     * @return                              The bytes of the file.
     * @throws ImageAcquisitionException    Thrown when the image cannot be retrieved for any reason
     */
    public ByteArrayOutputStream readFromSftpServer(Session session, String fileName) throws ImageAcquisitionException {
        
        ChannelSftp sftpChannel;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpChannelFailure: "
                    + "Could not connect to server with SFTP protocol. The target server may not be configured for SFTP"
                    , e);
        }
        
        try {
            sftpChannel.connect();
        } catch (JSchException e) {
            e.printStackTrace();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpRetrieval: "
                    + "Error when trying to retrieve file '" + fileName + "' from server", e);
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
        
        return image;
    }
    
    public String stripDomainName(String url) {
        String domain = url;
        // Remove any prefixes, such as "ftp://"
        if (domain.matches(".+://")) {
            domain = domain.replaceFirst(".+://", "");
        }
        
        if (domain.indexOf('/') != -1) {
            domain = domain.substring(0, domain.indexOf('/'));
        }
        
        return domain;
    }
}
