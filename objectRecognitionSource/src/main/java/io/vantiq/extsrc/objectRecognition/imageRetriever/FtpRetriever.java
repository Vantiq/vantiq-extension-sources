package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
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
 * configuration. If some but not all options are set for a Query, then the values from the initial configuration are
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
 *   <li>{@code file}: Required. Query only. The path of the target file.
 * </ul>
 * 
 * The timestamp is captured immediately before the copy request is sent. The additional data is:
 * <ul>
 *      <li>{@code file}: The path of the file read.
 *      <li>{@code server}: The domain name of the server from which the file was read.
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
        
        // Return and don't save values if the config doesn't want a default
        if (dataSourceConfig.get("noDefault") instanceof Boolean && (Boolean) dataSourceConfig.get("noDefault")) {
            noDefault = true;
            return;
        }
        
        // Get the domain name to connect to
        if (dataSourceConfig.get("server") instanceof String) {
            server = obtainDomainName((String) dataSourceConfig.get("server"));
        } else {
            throw new Exception(this.getClass().getCanonicalName() + ".noServerSpecified: "
                    + "No server was specified in the configuration setup." );
        }
        
        // Setup for FTP/FTPS/SFTP based on the config. Default to FTP
        if (dataSourceConfig.get("conType") instanceof String) {
            String type = (String) dataSourceConfig.get("conType");
            if (type.equalsIgnoreCase("ftps")) {
                conType = FTPS;
                // FTPS has a few more options available
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
        
        // Obtain the username and password
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
        
        // Connect to the file server using the requested connection type
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
    
    /**
     * Automatically fails, as no polling is allowed for the FTP retriever.
     * @throws FatalImageException  Always. No polling is allowed for this source
     */
    @Override
    public ImageRetrieverResults getImage() throws ImageAcquisitionException {
        throw new FatalImageException(this.getClass().getCanonicalName() + ".pollingNotAllowed: "
                + "Polling is not allowed for the FTPRetriever.");
    }

    /**
     * Retrieve an image from an FTP/FTPS/SFTP server.
     */
    @Override
    public ImageRetrieverResults getImage(Map<String, ?> request) throws ImageAcquisitionException {
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
        
        ImageRetrieverResults results = new ImageRetrieverResults();
        Map<String, Object> otherData = new LinkedHashMap<>();
        Date readTime;
        
        results.setOtherData(otherData);
        
        // Obtain the file path that is requested
        if (request.get("DSfile") instanceof String) {
            fileName = (String) request.get("DSfile");
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryMissingFile: "
                    + "Requires a file placed in the 'DSfile' option in order to obtain an image.");
        }
        otherData.put("file", fileName);
        
        // Use the specified server, or the original server if none specified. 
        if (request.get("DSserver") instanceof String) {
            server = obtainDomainName((String) request.get("DSserver"));
            if (!server.equals(this.server)) {
                newServer = true;
            }
        } else if (!noDefault) {
            server = this.server;
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryNoDefaultServer: "
                    + "Server option required when configured with no defaults");
        }
        otherData.put("server", server);
        
        // Use the specified connection type, the original connection type if none specified, or FTP if noDefault was set
        if (request.get("DSconType") instanceof String) {
            String type = (String) request.get("DSconType");
            if (type.equalsIgnoreCase("ftps")) {
                conType = FTPS;
                if (request.get("DSimplicit") instanceof Boolean) {
                    if ((Boolean) request.get("DSimplicit")) {
                        isImplicit = true;
                    } else {
                        isImplicit = false;
                    }
                    
                    if (isImplicit != this.isImplicit) {
                        newServer = true;
                    }
                } else {
                    isImplicit = this.isImplicit;
                }
                if (request.get("DSprotocol") instanceof String) {
                    protocol = (String) request.get("DSprotocol");
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
            // This will be FTP if noDefault was setup
            conType = this.conType;
        }
        
        // Use the specified username, or the original username if none specified
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
        
        // Use the specified password, or the original password if none specified
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
        // Create a new server only if a setting was changed from the initial settings
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
        // Read the file from the file server
        try {
            if (conType == FTP || conType == FTPS) {
                readTime = new Date(); // Log when the file request is made 
                file = readFromFtpServer(ftpClient, fileName); 
            } else {
                ChannelSftp channel = createChannelFromSession(session);
                
                readTime = new Date(); // Log when the file request is made 
                file = readFromSftpServer(channel, fileName);
            }
            results.setTimestamp(readTime);
        } finally {
            // Only close the FTPClient/Session if it was not the default one 
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
        
        byte[] fileBytes = file.toByteArray();
        quietClose(file);
        if (fileBytes == null || fileBytes.length == 0 ) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".emptyFile: "
                    + "The file '" + fileName + "' from server '" + server + "' was null or empty.");
        }
        
        ByteArrayInputStream fileInput = new ByteArrayInputStream(fileBytes);
        BufferedImage image;
        
        // Create an image from the file. This is to translate it into jpeg format
        try {
            image = ImageIO.read(fileInput);
        } catch (IOException e) {
            quietClose(fileInput);
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileParseError: "
                    + "Error occurred while reading the file"
                    , e);
        }
        
        // Make sure the image was actually created. It should only be null with no exception if the file was not
        // an image type readable by ImageIO
        quietClose(fileInput);
        if (image == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".unreadableFileType: "
                    + "Could not understand the requested file. Only " + Arrays.asList(ImageIO.getReaderFormatNames()));
        }
        
        ByteArrayOutputStream jpegFile = new ByteArrayOutputStream();
        boolean success = false;
        // Write save the image in jpeg format
        try {
            success = ImageIO.write(image, "jpeg", jpegFile);
            results.setImage(jpegFile.toByteArray());
            quietClose(jpegFile);
        } catch (IOException e) {
            quietClose(jpegFile);
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".untranslatableFileType: "
                    + "Could not translate the requested image to jpeg.", e);
        }
        
        if (!success) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".unwritableImage: "
                    + "Could not find an ImageIO Writer capable of writing the image as a jpeg.");
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
            session = null;
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
        
        // Create the client as FTP or FTPS based on the settings.
        if (conType == FTPS) {
            client = new FTPSClient(protocol, isImplicit);
            ((FTPSClient) client).setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
        } else {
            client = new FTPClient();
        }
        
        // Connect to target domain
        client.connect(server);
        
        // Make sure the connection succeeded.
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new ConnectException(this.getClass().getCanonicalName() + ".failedConnection: "
                    + "Could not connect to server '" + server + "'");
        }
        
        client.setFileType(FTPClient.BINARY_FILE_TYPE);
        // Helps when dealing with firewalls
        client.enterLocalPassiveMode();
        
        // Only needs to be done for FTPS implicit. Unclear why only implicit requires it
        if (conType == FTPS && isImplicit) {
            // Necessary, not entirely clear on why it is necessary only for implicit
            // Possibly it expects normal FTP until told otherwise?
            
            // PBSZ sets the size of the "protection buffer" and PROT sets the security level
            ((FTPSClient) client).execPBSZ(0); 
            ((FTPSClient) client).execPROT("P"); 
            
            // Make sure the commands made it through
            reply = client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                client.disconnect();
                throw new IOException(this.getClass().getCanonicalName() + ".failedSecurityNegotiation: "
                        + "Could not setup proper security for server '" + server + "'");
            }
        }
        
        // Login to the server
        boolean success = client.login(username, password);
        
        // Exit if the login failed
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
        
        // Create the Session for the target server
        try {
            sess = jsch.getSession(username, server);
        } catch (JSchException e) {
            throw new Exception(this.getClass().getCanonicalName() + ".failedSftpSessionSetup: "
                    + "Could not create session at host '" + server + "' with given username");
        }
        
        sess.setConfig("StrictHostKeyChecking", "no"); // Accept any connection made
        sess.setPassword(password);

        // Connect to the server
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
        
        // Retrieve the file, exit if it cannot
        try {
            boolean success = client.retrieveFile(filePath, image);
            
            if (!success) {
                quietClose(image);
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileRetrievalError: "
                        + "File could not be retreived. "
                        + "Reply was : " + client.getReplyString());
            }
        } catch(IOException e) {
            quietClose(image);
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".fileConnectionError: "
                    + "Could not read file '" + filePath + "' from server '" 
                    + client.getRemoteAddress().getCanonicalHostName()
                    + "' with given username and password. Error message was: " + e.getMessage(), e); 
        }
        return image;
    }
    
    /**
     * Creates and starts an SFTP channel from the given session
     * @param session                       The Session to create a channel from 
     * @return                              An SFTP channel
     * @throws ImageAcquisitionException    If the SFTP channel could not be properly opened.
     */
    public ChannelSftp createChannelFromSession(Session session) throws ImageAcquisitionException {
        ChannelSftp sftpChannel;
        
        // Make a new SFTP channel
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpChannelFailure: "
                    + "Could not connect to server with SFTP protocol. The target server may not be configured for SFTP"
                    , e);
        }
        
        // Connect using the SFTP channel
        try {
            sftpChannel.connect();
        } catch (JSchException e) {
            e.printStackTrace();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpConnection: "
                    + "Error when trying to connect to server using SFTP", e);
        }
        
        return sftpChannel;
    }
    
    /**
     * Read the specified file from {@code sftpChannel}
     * @param sftpChannel                   The channel to use for reading.
     * @param fileName                      The path to the file to read.
     * @return                              The bytes of the file.
     * @throws ImageAcquisitionException    Thrown when the image cannot be retrieved for any reason
     */
    public ByteArrayOutputStream readFromSftpServer(ChannelSftp sftpChannel, String fileName) throws ImageAcquisitionException {
        
        ByteArrayOutputStream image = new ByteArrayOutputStream(64 * 1024); // Start at 64 kB
        
        // Read the image from the SFTP channel
        try {
            sftpChannel.get(fileName, image);
        } catch (SftpException e) {
            sftpChannel.exit();
            quietClose(image);
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".sftpRetrieval: "
                    + "Error when trying to retrieve file '" + fileName + "' from server", e);
        }
        
        sftpChannel.exit();
        return image;
    }
    
    /**
     * Obtain the domain name from a URL by removing the protocol type and path name. For example, if {@code url} is 
     * "http://site.name.com/home/index.html" this function will return "site.name.com"
     * @param url   The url to obtain a domain name from
     * @return      The original string with "*://" removed from the beginning and everything after and including the
     *              first "/" removed
     */
    public String obtainDomainName(String url) {
        String domain = url;
        // Remove any prefixes, such as "ftp://"
        if (domain.matches(".+://")) {
            domain = domain.replaceFirst(".+://", "");
        }
        
        // Remove paths from the url, e.g. remove "/home/index.html" from "site.name.com/home/index.html"
        if (domain.indexOf('/') != -1) {
            domain = domain.substring(0, domain.indexOf('/'));
        }
        
        return domain;
    }
    
    /**
     * Calls close() on the object passed, catching and ignoring any errors
     * @param closeable The object to close
     */
    public void quietClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // Do nothing
        }
    }
}
