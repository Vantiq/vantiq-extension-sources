package io.vantiq.extsrc.FTPClientSource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Calendar;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;

import io.vantiq.extsrc.FTPClientSource.exception.VantiqFTPClientException;

/**
 * A program that demonstrates how to upload files from local computer to a
 * remote FTP server using Apache Commons Net API.
 * 
 * @author www.codejava.net
 */
public class FTPUtil {

    final Logger Log;

    public static int FTP_LOGIN_FAILED = 530;
    public static int FTP_REMOTE_FOLDER_NOT_EXISTS = 550;

    FTPClient ftpClient; // = new org.apache.commons.net.ftp.FTPClient();
    String baseFolder = "";

    public FTPUtil(Logger log) {
        this.Log = log;
    }
    public boolean closeFtpConection() throws VantiqFTPClientException{

        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
            return true; 

        } catch (IOException ex) {
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed closeing Connection",ex); 
        }
    }
 
    public boolean openFtpConection(String server, Integer port, String user, String password,int connectTimeout)
            throws VantiqFTPClientException {
        ftpClient = new org.apache.commons.net.ftp.FTPClient();
        try {
            ftpClient.setConnectTimeout(connectTimeout);
            ftpClient.connect(server, port);
            ftpClient.login(user, password);
            int returnCode = ftpClient.getReplyCode();
            if (returnCode == FTP_LOGIN_FAILED) {
                throw new VantiqFTPClientException("Login failed " + server );
            }

            ftpClient.enterLocalPassiveMode();

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            return true;
        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            //throw new VantiqFTPClientException("Failed open ftp connection", ex);
            return false;
        } finally {
        }
    }

    public boolean UploadFile(FTPClient ftpClient, String path, String filename, String destination)
            throws VantiqFTPClientException {

        if (!ftpClient.isConnected()) {
            throw new VantiqFTPClientException("No ftp connection open");
        }

        try {
            String fullFileName = path + "/" + filename;

            baseFolder = ftpClient.printWorkingDirectory();

            ftpClient.changeWorkingDirectory(destination);
            int returnCode = ftpClient.getReplyCode();
            if (returnCode == FTP_REMOTE_FOLDER_NOT_EXISTS) {
                throw new VantiqFTPClientException("Remote folder " + destination + " doesn't exist");
            }

            InputStream inputStream = new FileInputStream(fullFileName);

            Log.info("Start uploading first file " + fullFileName + " to " + destination);
            boolean done = ftpClient.storeFile(filename, inputStream);
            inputStream.close();
            if (done) {
                Log.info("File " + fullFileName + " uploaded successfully to " + destination);
            } else {
                Log.error("File " + fullFileName + " failed to upload to " + destination);
            }

            ftpClient.changeWorkingDirectory(baseFolder);

            return done;

        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed UploadFile", ex);
        } finally {
            /*
             * try { if (ftpClient.isConnected()) { ftpClient.logout();
             * ftpClient.disconnect(); } } catch (IOException ex) { ex.printStackTrace(); }
             */
        }

    }

    public boolean DownFile(FTPClient ftpClient, String remoteFullFilePath, String destinationFullPath)
            throws VantiqFTPClientException {

        if (!ftpClient.isConnected()) {
            throw new VantiqFTPClientException("No ftp connection open");
        }

        try {

            Log.info("Start downloading file " + remoteFullFilePath + " to " + destinationFullPath);
            OutputStream outputStream1 = new BufferedOutputStream(new FileOutputStream(destinationFullPath));
            boolean done = ftpClient.retrieveFile(remoteFullFilePath, outputStream1);
            outputStream1.close();
            if (done) {
                Log.info("File " + remoteFullFilePath + " downloaded successfully to " + destinationFullPath);
            } else {
                Log.error("File " + remoteFullFilePath + " failed to downloaded to " + destinationFullPath);
            }

            return done;

        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed UploadFile", ex);
        } finally {
            /*
             * try { if (ftpClient.isConnected()) { ftpClient.logout();
             * ftpClient.disconnect(); } } catch (IOException ex) { ex.printStackTrace(); }
             */
        }

    }

    public Boolean deleteLocalFile(String filename){
        try
        {
            Files.deleteIfExists(Paths.get(filename));
            return true; 
        }
        catch(NoSuchFileException e)
        {
            Log.error("No such file/directory exists", e);
        }
        catch(DirectoryNotEmptyException e)
        {
            Log.error("Directory is not empty.", e);
        }
        catch(IOException e)
        {
            Log.error("Invalid permissions.", e);
        }
        return false;
    }


    public boolean uploadFolder(String server, Integer port, String user, String password, String remoteFolderPath,
            String localFolderPath,Boolean deleteAfterUpload, Integer connectTimeout) throws VantiqFTPClientException {

        FTPClient currentFtpClient = new org.apache.commons.net.ftp.FTPClient();

        try {
            currentFtpClient.setConnectTimeout(connectTimeout);
            currentFtpClient.connect(server, port);
            currentFtpClient.login(user, password);
            int returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_LOGIN_FAILED) {
                throw new VantiqFTPClientException("Login failed " + remoteFolderPath );
            }

            currentFtpClient.enterLocalPassiveMode();

            currentFtpClient.setFileType(FTP.BINARY_FILE_TYPE);

            String currentbaseFolder = currentFtpClient.printWorkingDirectory();

            currentFtpClient.changeWorkingDirectory(remoteFolderPath);
            returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_REMOTE_FOLDER_NOT_EXISTS) {
                throw new VantiqFTPClientException("Remote folder " + remoteFolderPath + " doesn't exist");
            }

            File folder = new File(localFolderPath);

            String[] listOfFiles = folder.list();
            for (String fileName : listOfFiles) {
                File f = new File(localFolderPath + "/" + fileName);
                if (f.isFile()) {
                    if (!UploadFile(currentFtpClient, localFolderPath, fileName, remoteFolderPath)) {
                        Log.error("File " + fileName + " couldn't uploaded");
                    } else {
                        if (deleteAfterUpload){
                            if (deleteLocalFile(f.getPath())){
                                Log.info("File " + f.getPath() + " uploaded and deleted");
                            } else {
                                Log.error("File " + f.getPath() + " uploaded but couldn't deleted");
                            }
                        }
                    }
                }
            }

            currentFtpClient.changeWorkingDirectory(currentbaseFolder);

            return true;
        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed open ftp connection", ex);
        } finally {
            try {
                if (currentFtpClient.isConnected()) {
                    currentFtpClient.logout();
                    currentFtpClient.disconnect();
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean downloadFolder(String server, Integer port, String user, String password, String remoteFolderPath,
            String localFolderPath,boolean deleteAfterDownload,Integer connectTimeout,Boolean AddFileServerNamePrefix,String serverName) throws VantiqFTPClientException {

        FTPClient currentFtpClient = new org.apache.commons.net.ftp.FTPClient();

        try {
            currentFtpClient.setConnectTimeout(connectTimeout);
            currentFtpClient.connect(server, port);
            currentFtpClient.login(user, password);
            int returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_LOGIN_FAILED) {
                throw new VantiqFTPClientException("Login failed " + server );
            }

            currentFtpClient.enterLocalPassiveMode();

            currentFtpClient.setFileType(FTP.BINARY_FILE_TYPE);

            String currentbaseFolder = currentFtpClient.printWorkingDirectory();

            currentFtpClient.changeWorkingDirectory(remoteFolderPath);
            returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_REMOTE_FOLDER_NOT_EXISTS) {
                throw new VantiqFTPClientException("Remote folder " + remoteFolderPath + " doesn't exist");
            }

            FTPFile[] files = currentFtpClient.listFiles();
//            FTPFile[] files = ftpClient.listFiles();
            for (FTPFile f : files) {
                String fileName = f.getName();
                String s = localFolderPath + "/" + fileName;
                if (AddFileServerNamePrefix){
                    s = localFolderPath + "/" +serverName.toLowerCase()+"_"+ fileName;
                }
                if (!DownFile(currentFtpClient, fileName, s)) {
                    Log.error("File " + fileName + " couldn't downloaded");
                } else {
                    if (deleteAfterDownload){
                        if (deleteRemoteFile(currentFtpClient,fileName)){
                            Log.info("File " + fileName + " downloaded and deleted");
                        } else {
                            Log.error("File " + fileName + " downloaded but couldn't deleted");
                        }

                    }
                }
            }

            currentFtpClient.changeWorkingDirectory(currentbaseFolder);

            return true;
        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed open ftp connection", ex);
        } finally {
            try {
                if (currentFtpClient.isConnected()) {
                    currentFtpClient.logout();
                    currentFtpClient.disconnect();
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean checkCommunication(String server, Integer port, String user, String password,Integer connectTimeout)
            throws VantiqFTPClientException {

        FTPClient currentFtpClient = new org.apache.commons.net.ftp.FTPClient();

        try {

            String remoteFolderPath = server + ":" + port ; 
            currentFtpClient.setConnectTimeout(connectTimeout);
            currentFtpClient.connect(server, port);
            currentFtpClient.login(user, password);
            int returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_LOGIN_FAILED) {
                throw new VantiqFTPClientException("Login failed " + server );
            }

            return true;
        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            return false;
        } finally {
            try {
                if (currentFtpClient.isConnected()) {
                    currentFtpClient.logout();
                    currentFtpClient.disconnect();
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean deleteRemoteFile(FTPClient currentFtpClient, String remoteFilePath) throws VantiqFTPClientException {

        try {

            if (!currentFtpClient.deleteFile(remoteFilePath)) {
                Log.error("File " + remoteFilePath + " couldn't deleted");
                return false;
            } else {
                Log.info("File " + remoteFilePath + " deleted");
                return true;
            }

        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("currentFtpClient.deleteFile failure", ex);
        } finally {
        }
    }
    public boolean cleanRemoteFolder(String server, Integer port, String user, String password, String remoteFolderPath,
            Integer ageInDays,Integer connectTimeout) throws VantiqFTPClientException {

        FTPClient currentFtpClient = new org.apache.commons.net.ftp.FTPClient();

        try {
            currentFtpClient.setConnectTimeout(connectTimeout);
            currentFtpClient.connect(server, port);
            currentFtpClient.login(user, password);
            int returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_LOGIN_FAILED) {
                throw new VantiqFTPClientException("Login failed " + server );
            }

            currentFtpClient.enterLocalPassiveMode();

            currentFtpClient.setFileType(FTP.BINARY_FILE_TYPE);

            String currentbaseFolder = currentFtpClient.printWorkingDirectory();

            currentFtpClient.changeWorkingDirectory(remoteFolderPath);
            returnCode = currentFtpClient.getReplyCode();
            if (returnCode == FTP_REMOTE_FOLDER_NOT_EXISTS) {
                throw new VantiqFTPClientException("Remote folder " + remoteFolderPath + " doesn't exist");
            }

            FTPFile[] files = currentFtpClient.listFiles();
            for (FTPFile f : files) {
                String fileName = f.getName();
                Calendar t = f.getTimestamp();

                if ((t.getTimeInMillis() + ageInDays * 24 * 3600 * 1000) < Calendar.getInstance().getTimeInMillis()) {

                    String s = remoteFolderPath + "/" + fileName;
                    if (!currentFtpClient.deleteFile(s)) {
                        Log.error("File " + fileName + " couldn't deleted");
                    } else {
                        Log.info("File " + fileName + " deleted");
                    }
                }
            }

            currentFtpClient.changeWorkingDirectory(currentbaseFolder);

            return true;
        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed open ftp connection", ex);
        } finally {
            /*
             * try { if (currentFtpClient.isConnected()) { currentFtpClient.logout();
             * currentFtpClient.disconnect(); }
             * 
             * } catch (IOException ex) { ex.printStackTrace(); }
             */
        }
    }

    public FTPFile[] getListFiles(String remoteFolderPath) throws VantiqFTPClientException {

        if (!ftpClient.isConnected()) {
            throw new VantiqFTPClientException("No ftp connection open");
        }
        try {
            baseFolder = ftpClient.printWorkingDirectory();

            ftpClient.changeWorkingDirectory(remoteFolderPath);

            FTPFile[] files = ftpClient.listFiles();

            ftpClient.changeWorkingDirectory(baseFolder);

            return files;

        } catch (IOException ex) {
            Log.error("Error: " + ex.getMessage());
            ex.printStackTrace();
            throw new VantiqFTPClientException("Failed UploadFile", ex);
        } finally {
        }
    }
}
