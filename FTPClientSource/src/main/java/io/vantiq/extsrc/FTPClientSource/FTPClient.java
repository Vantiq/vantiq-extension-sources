/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.FTPClientSource;

import io.vantiq.client.ResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.FTPClientSource.exception.VantiqFTPClientException;

/**
 * Implementing WatchService based on configuration given in FTPClientConfig in
 * extension configuration. Sets up the source using the configuration document,
 * which looks as below.
 * 
 * <pre>
 * "FTPClientConfig": {
 *     "fileFolderPath": "d:/tmp/FTPClient",
 *     "filePrefix": "eje",
 *     "fileExtension": "FTPClient",
 *     "maxLinesInEvent": 200,
 *     "schema": {
 *	      "field0": "value",
 *   	  "field2": "flag",
 *     	"field1": "YScale"
 *  	}
 *  }
 * </pre>
 * 
 * The class enable multiple activites using thread pool based on the attached
 * configuration
 * 
 * <pre>
 *  "options": {
 *     "maxActiveTasks": 2,
 *     "maxQueuedTasks": 4,
 *     "processExistingFiles": true,
 *     "extensionAfterProcessing": "FTPClient.done",
 *     "deleteAfterProcessing": false
 *  }
 * </pre>
 *
 * 
 * Later version added support for fixed length records where schema
 * configuration is similar to above
 * 
 * <pre>
 * "FTPClientConfig": 
 * { "fileFolderPath":"d:/tmp/FTPClient", 
 *  "filePrefix": "plu", 
 *  "fileExtension": "txt", 
 *  "maxLinesInEvent": 50, 
*   "fixedRecordSize" : 53,
 *  "FileType": "FixedLength", 
 * "schema": 
 *  { "code": {
 *      "offset": 0, 
 *      "length": 13, 
 *      "type": "string" 
 *      }, 
 *  "name": { 
 *      "offset": 14,
 *      "length": 20, 
 *      "type": "string", 
 *      "charset": "Cp862", 
 *      "reversed": true },
 *  "weighted": { 
 *      "offset": 35, 
 *      "length": 1, "type": "string" 
 *      }, 
 *  "price": {
 *      "offset": 37, 
 *      "length": 6, 
 *      "type": "string" 
 *      }, 
 *  "cost": { 
 *      "offset": 44,
 *      "length": 6, 
 *      "type": "string" 
 *      }, 
 *  "department": { 
 *      "offset": 51, 
 *      "length": 3,
 *      "type": "string" 
 *      } 
 *  } 
 * }
 * </pre>
 * 
 * and ability to write, append and delete text files to disk .
 */
public class FTPClient {
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    // Used to receive configuration informatio .
    Map<String, Object> config;
    Map<String, Object> options;

    // Components used
    ExecutorService executionPool = null;
    ExtensionWebSocketClient oClient;

    int pollTime;

    Timer timerTask;

    String remoteFolderPath;
    String server;
    Integer port;
    String username;
    String password;

    FTPUtil ftpUtil = new FTPUtil(log);
    boolean isRunningInLinux = isRunningInsideLinux();

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;
    private static final int DEFAULT_POLL_TIME = 30000;

    private static final String MAX_ACTIVE_TASKS_LABEL = "maxActiveTasks";
    private static final String MAX_QUEUED_TASKS_LABEL = "maxQueuedTasks";

    private static final String DELETE_AFTER_UPLOAD_KEYWORD = "deleteAfterUpload";
    private static final String LOCAL_PATH_KEYWORD = "local";
    private static final String REMOTE_PATH_KEYWORD = "remote";
    private static final String AGE_IN_DAYS_KEYWORD = "ageInDays";
    private static final String BODY_KEYWORD = "body";
    private static final String PATH_KEYWORD = "path";
    private static final String FILE_KEYWORD = "file";
    private static final String FILENAME_KEYWORD = "filename";
    private static final String CONTENT_KEYWORD = "content";
    private static final String TEXT_KEYWORD = "text";
    private static final String WORKFOLDER_KEYWORD = "WorkFolderPath";

    FTPServerEntry defaultServer = new FTPServerEntry();
    List<FTPServerEntry> serverList;

    public static String FTPClient_CHECK_COMM_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.communicationFailure";
    public static String FTPClient_CHECK_COMM_FAILED_MESSAGE = "connect failure";
    public static String FTPClient_CHECK_COMM_NONAME_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.noServerName";
    public static String FTPClient_CHECK_COMM_NONAME_FAILED_MESSAGE = "connect failure";
    public static String FTPClient_UPLOAD_FOLDER_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.uploadFolderFailure";
    public static String FTPClient_UPLOAD_FOLDER_FAILED_MESSAGE = "Upload folder failure";
    public static String FTPClient_CLEAN_FOLDER_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.cleanFolderFailure";
    public static String FTPClient_CLEAN_FOLDER_FAILED_MESSAGE = "clean folder failure";
    public static String FTPClient_DOWNLOAD_FOLDER_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.downloadFolderFailure";
    public static String FTPClient_DOWNLOAD_FOLDER_FAILED_MESSAGE = "Download destination folder failure (does not exists)";
    public static String FTPClient_DOWNLOAD_DOCUMENT_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.downloadDocumentFailure";
    public static String FTPClient_DOWNLOAD_DOCUMENT_FAILED_MESSAGE = "Download document failure";
    public static String FTPClient_DOWNLOAD_DOCUMENT_MISSING_MESSAGE = "Must supply source and destination";
    public static String FTPClient_UPLOAD_DOCUMENT_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.uploadDocumentFailure";
    public static String FTPClient_UPLOAD_DOCUMENT_FAILED_MESSAGE = "Upload document failure";
    public static String FTPClient_UPLOAD_DOCUMENT_MISSING_MESSAGE = "Must supply source and destination";
    public static String FTPClient_IMPORT_DOCUMENT_FAILED_CODE = "io.vantiq.extsrc.FTPClientsource.importDocumentFailure";
    public static String FTPClient_IMPORT_DOWNLOAD_DOCUMENT_FAILED_MESSAGE = "Import download document failure";
    public static String FTPClient_IMPORT_UPLOAD_DOCUMENT_FAILED_MESSAGE = "Import upload document failure";
    public static String FTPClient_IMPORT_DOCUMENT_SOURCE_MISSING_MESSAGE = "Must supply source server URL , source server token and SourceServerPath";
    public static String FTPClient_IMPORT_DOCUMENT_FILE_MISSING_MESSAGE = "Must supply File name and WorkFolderPath to import";

    public static String FTPClient_NOFILE_CODE = "io.vantiq.extsrc.FTPClientsource.nofile";
    public static String FTPClient_NOFILE_MESSAGE = "File does not exist.";
    public static String FTPClient_NOFOLDER_CODE = "io.vantiq.extsrc.FTPClientsource.nofolder";
    public static String FTPClient_NOFOLDER_MESSAGE = "Folder does not exist.";
    public static String FTPClient_FILEEXIST_CODE = "io.vantiq.extsrc.FTPClientsource.fileexists";
    public static String FTPClient_FILEEXIST_MESSAGE = "File already exists.";

    public static String FTPClient_SUCCESS_CODE = "io.vantiq.extsrc.FTPClientsource.success";
    public static String FTPClient_SUCCESS_FOLDER_UPLOADED_MESSAGE = "Folder uploaded Successfully";
    public static String FTPClient_SUCCESS_FOLDER_DOWNLOADED_MESSAGE = "Folder downloaded Successfully";
    public static String FTPClient_SUCCESS_FOLDER_CLEANED_MESSAGE = "Folder cleaned Successfully";
    public static String FTPClient_SUCCESS_CHECK_COMM_MESSAGE = "communication extablished Successfully";
    public static String FTPClient_SUCCESS_IMAGE_DOWNLOADED_MESSAGE = "Image downloaded Successfully";
    public static String FTPClient_SUCCESS_IMAGE_UPLOADED_MESSAGE = "Image uploaded Successfully";
    public static String FTPClient_SUCCESS_IMPORT_DOCUMENT_MESSAGE = "Image imported Successfully";

    public static String FTPClient_SUCCESS_FILE_CREATED_MESSAGE = "File Created Successfully";
    public static String FTPClient_SUCCESS_FILE_APPENDED_MESSAGE = "File Appended Successfully";
    public static String FTPClient_SUCCESS_FILE_DELETED_MESSAGE = "File Deleted Successfully.";

    /**
     * Create resource which will required by the extension activity
     * 
     * @throws VantiqFTPClientException
     */
    void prepareConfigurationData() throws VantiqFTPClientException {

        defaultServer.server = (String) config.get(FTPClientHandleConfiguration.SERVER_IP);
        defaultServer.port = (Integer) config.get(FTPClientHandleConfiguration.SERVER_PORT);
        defaultServer.username = (String) config.get(FTPClientHandleConfiguration.USERNAME);
        defaultServer.password = (String) config.get(FTPClientHandleConfiguration.PASSWORD);
        defaultServer.remoteFolderPath = (String) config.get(FTPClientHandleConfiguration.REMOTE_FOLDER_PATH);
        defaultServer.localFolderPath = (String) config.get(FTPClientHandleConfiguration.LOCAL_FOLDER_PATH);
        defaultServer.ageInDays = (Integer) config.get(FTPClientHandleConfiguration.AGE_IN_DAYS_KEYWORD);
        defaultServer.connectTimeout = (Integer) config.get(FTPClientHandleConfiguration.CONNECT_TIMEOUT);
        defaultServer.addPrefixToDownload = (Boolean) config.get(FTPClientHandleConfiguration.ADD_PRRFIX_TO_DOWNLOAD);

        defaultServer.baseDocumentPath = "";
        if (config.get(FTPClientHandleConfiguration.BASE_DOCUMENT_PATH) != null) {
            defaultServer.baseDocumentPath = (String) config.get(FTPClientHandleConfiguration.BASE_DOCUMENT_PATH);
        }

        defaultServer.documentServer = "dev.vantiq.com";
        if (config.get(FTPClientHandleConfiguration.DOCUMENT_SERVER) != null) {
            defaultServer.documentServer = (String) config.get(FTPClientHandleConfiguration.DOCUMENT_SERVER);
        }

        defaultServer.documentServerToken = "";
        if (config.get(FTPClientHandleConfiguration.DOCUMENT_SERVER_TOKEN) != null) {
            defaultServer.documentServerToken = (String) config.get(FTPClientHandleConfiguration.DOCUMENT_SERVER_TOKEN);
        }

        defaultServer.documentServerPath = "";
        if (config.get(FTPClientHandleConfiguration.DOCUMENT_SERVER_PATH) != null) {
            defaultServer.documentServerPath = (String) config.get(FTPClientHandleConfiguration.DOCUMENT_SERVER_PATH);
        }

        defaultServer.sourceDocumentServer = "dev.vantiq.com";
        if (config.get(FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER) != null) {
            defaultServer.sourceDocumentServer = (String) config
                    .get(FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER);
        }

        defaultServer.sourceDocumentServerToken = "";
        if (config.get(FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_TOKEN) != null) {
            defaultServer.sourceDocumentServerToken = (String) config
                    .get(FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_TOKEN);
        }

        defaultServer.sourceDocumentServerPath = "";
        if (config.get(FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_PATH) != null) {
            defaultServer.sourceDocumentServerPath = (String) config
                    .get(FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_PATH);
        }

        defaultServer.autoUploadToDocumentPostfix = "";
        if (config.get(FTPClientHandleConfiguration.AUTO_UPLOAD_TO_DOCUMENT_POSTFIX) != null) {
            defaultServer.autoUploadToDocumentPostfix = (String) config
                    .get(FTPClientHandleConfiguration.AUTO_UPLOAD_TO_DOCUMENT_POSTFIX);
        }

        defaultServer.deleteAfterSuccessfullUpload = false;
        if (config.get(FTPClientHandleConfiguration.DELETE_AFTER_SUCCESSFULL_UPLOAD) != null) {
            defaultServer.deleteAfterSuccessfullUpload = (Boolean) config
                    .get(FTPClientHandleConfiguration.DELETE_AFTER_SUCCESSFULL_UPLOAD);
        }

        try {
            if (config.get(FTPClientHandleConfiguration.SERVER_LIST) != null) {
                List<Map<String, Object>> sl = (List<Map<String, Object>>) config
                        .get(FTPClientHandleConfiguration.SERVER_LIST);

                serverList = new ArrayList<>();
                for (int i = 0; i < sl.size(); i++) {
                    Map<String, Object> o = (Map<String, Object>) sl.get(i);

                    serverList.add(new FTPServerEntry(o, defaultServer));
                }

            }

            remoteFolderPath = "/";
            if (config.get(FTPClientHandleConfiguration.REMOTE_FOLDER_PATH) instanceof String) {
                remoteFolderPath = (String) config.get(FTPClientHandleConfiguration.REMOTE_FOLDER_PATH);
            }

            pollTime = DEFAULT_POLL_TIME;
            if (options.get("pollTime") != null) {
                pollTime = (Integer) options.get("pollTime");
            }

            int maxActiveTasks = MAX_ACTIVE_TASKS;
            int maxQueuedTasks = MAX_QUEUED_TASKS;

            if (options.get(MAX_ACTIVE_TASKS_LABEL) != null) {
                maxActiveTasks = (int) options.get(MAX_ACTIVE_TASKS_LABEL);
            }
            if (options.get(MAX_QUEUED_TASKS_LABEL) != null) {
                maxQueuedTasks = (int) options.get(MAX_QUEUED_TASKS_LABEL);
            }

            executionPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(maxQueuedTasks));
        } catch (VantiqFTPClientException exv) {
            throw exv;
        } catch (Exception ex) {
            throw ex;
        }

    }

    /**
     * function set the watcher service on the folder where files created. It also
     * cleans existing files which haven't been processed yet .
     * 
     * @param oClient
     * @param fileFolderPath
     * @param fullFilePath
     * @param config
     * @param options
     * @throws VantiqFTPClientException
     */
    public void setupFTPClient(ExtensionWebSocketClient oClient, Map<String, Object> config,
            Map<String, Object> options) throws VantiqFTPClientException {
        try {

            this.config = config;
            this.options = options;

            prepareConfigurationData();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    log.info("TimerTask Start working on existing file in folder {}", remoteFolderPath);
                    LookForRemoteFiles(remoteFolderPath);

                }
            };
            // Create new Timer, and schedule the task according to the pollTime
            timerTask = new Timer("executePolling");
            timerTask.schedule(task, 0, pollTime);

        } catch (Exception e) {
            log.error("FTPClient failed  ", e);
            reportFTPClientError(e);
        }
    }

    /**
     * Handling accepted file, work only in case the file name is match the file
     * name pattern . the input file can be renamed and stay in the folder (usually
     * for debug purposes) or can be deleted .
     * 
     * @param fileFolderPath - the path where the file is located
     * @param filename       - the file name to be procesed.
     */
    void executeInPool(String fileFolderPath, String filename) {

    }

    /**
     * Responsible for handling files which already exist in folder and will not be
     * notified by the WatchService
     * 
     * @param fileFolderPath - the path of the files waiting to be processed .
     */
    void LookForRemoteFiles(String fileFolderPath) {
        try {

            if (serverList != null && serverList.size() > 0) {
                for (int i = 0; i < serverList.size(); i++) {

                    FTPServerEntry currEntry = serverList.get(i);

                    if (currEntry.enable) {
                        log.info("LookForRemoteFiles trying to connect to " + currEntry.name);

                        String localFolderPath = currEntry.localFolderPath;
                        if (isRunningInLinux) {
                            localFolderPath = fixFileFolderPathForUnix(localFolderPath);
                        }
                        new File(localFolderPath).mkdirs();

                        try {
                            if (!ftpUtil.downloadFolder(currEntry.server, currEntry.port, currEntry.username,
                                    currEntry.password, currEntry.remoteFolderPath, localFolderPath, true,
                                    currEntry.connectTimeout, currEntry.addPrefixToDownload, currEntry.name,
                                    currEntry)) {
                                log.error("LookForRemoteFiles failure when downloading files from " + currEntry.name);
                            }
                        } catch (VantiqFTPClientException exv) {
                            log.error("LookForRemoteFiles ftpUtil.downloadFolder failed", exv);
                        }
                    }
                }

            } else {
                // support when there is no server list and shgould download from a single
                // server only.
                FTPServerEntry currEntry = defaultServer;
                if (ftpUtil.openFtpConection(currEntry.server, currEntry.port, currEntry.username, currEntry.password,
                        currEntry.connectTimeout)) {
                    FTPFile[] files = ftpUtil.getListFiles(remoteFolderPath);

                    for (FTPFile ftpFile : files) {
                        String file = ftpFile.getName();
                        System.out.println(file);
                    }

                    ftpUtil.closeFtpConection();
                }
            }
        } catch (VantiqFTPClientException ex) {

        }
    }

    /**
     * Create standard vantiq respone for the different suceess or failures as part
     * of the select operations, those are being repliyed to vail.
     * 
     * @param code    : code of the error ( io.vantiq.extsrc.FTPClientsource.success
     *                is success otherwise failure)
     * @param message : description of the operation results
     * @param value   : the actual artifact the operation was activate on.
     * @return map which represent the values fo the error , to be sent as Json to
     *         the server
     */
    HashMap[] CreateResponse(String code, String message, String value) {
        ArrayList<HashMap> rows = new ArrayList<HashMap>();

        HashMap row = new HashMap(3);
        row.put("code", code);
        row.put("message", message);
        row.put("value", value);
        rows.add(row);

        HashMap[] rowsArray = rows.toArray(new HashMap[rows.size()]);

        if (code.equals("io.vantiq.extsrc.FTPClientsource.success")) {
            log.info(String.format("Response : %s (%s) code %s", message, value, code));
        } else {
            log.error(String.format("Response : %s (%s) code %s", message, value, code));
        }

        return rowsArray;
    }

    /**
     * The method used to execute a delete file command, triggered by a SELECT on
     * the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processCheckComm(ExtensionServiceMessage message) throws VantiqFTPClientException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String sourcePathStr = "";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);

            FTPUtil ftpUtil = new FTPUtil(log);
            FTPServerEntry currServer = null;

            if (body.get(FTPClientHandleConfiguration.SERVER_NAME) instanceof String) {
                String name = (String) body.get(FTPClientHandleConfiguration.SERVER_NAME);
                currServer = findServer(name);
                sourcePathStr = currServer.server + ":" + currServer.port;

                try {
                    if (!ftpUtil.checkCommunication(currServer.server, currServer.port, currServer.username,
                            currServer.password, currServer.connectTimeout)) {
                        rsArray = CreateResponse(FTPClient_CHECK_COMM_FAILED_CODE, FTPClient_CHECK_COMM_FAILED_MESSAGE,
                                "[" + name + "] " + sourcePathStr);
                    } else {
                        rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_CHECK_COMM_MESSAGE,
                                "[" + name + "] " + sourcePathStr);
                    }
                } catch (VantiqFTPClientException exv) {
                    rsArray = CreateResponse(FTPClient_CHECK_COMM_FAILED_CODE, FTPClient_CHECK_COMM_FAILED_MESSAGE,
                            "[" + name + "] " + sourcePathStr);
                }
            } else {
                rsArray = CreateResponse(FTPClient_CHECK_COMM_NONAME_FAILED_CODE,
                        FTPClient_CHECK_COMM_NONAME_FAILED_MESSAGE,
                        "name must be supplied, or must exists for checkcomm command");
            }

            return rsArray;
        } catch (VantiqFTPClientException exv) {
            throw exv;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        } finally {

        }
    }

    public class destinationStruct {
        public String server;
        public Integer port;
        public String username;
        public String password;
    }

    private destinationStruct setDestination(Map<String, Object> body) {
        destinationStruct dest = new destinationStruct();
        dest.server = server;
        dest.port = port;
        dest.username = username;
        dest.password = password;

        if (body.get(FTPClientHandleConfiguration.SERVER_IP) instanceof String) {
            dest.server = (String) body.get(FTPClientHandleConfiguration.SERVER_IP);
        }
        if (body.get(FTPClientHandleConfiguration.SERVER_PORT) instanceof Integer) {
            dest.port = (Integer) body.get(FTPClientHandleConfiguration.SERVER_PORT);
        }
        if (body.get(FTPClientHandleConfiguration.USERNAME) instanceof String) {
            dest.username = (String) body.get(FTPClientHandleConfiguration.USERNAME);
        }
        if (body.get(FTPClientHandleConfiguration.PASSWORD) instanceof String) {
            dest.password = (String) body.get(FTPClientHandleConfiguration.PASSWORD);
        }

        return dest;
    }

    public FTPServerEntry findServer(String name) throws VantiqFTPClientException {
        Iterator<FTPServerEntry> iterator = serverList.iterator();
        while (iterator.hasNext()) {
            FTPServerEntry server = iterator.next();
            if (server.name.equals(name)) {
                return server;
            }
        }
        throw new VantiqFTPClientException("Server " + name + " not found");
    }

    /**
     * The method used to execute a delete file command, triggered by a SELECT on
     * the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processDownload(ExtensionServiceMessage message) throws VantiqFTPClientException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String sourcePathStr = "";
        String destinationPathStr = "";
        String name = "default";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);

            FTPServerEntry currEntry = null;

            checkedAttribute = FTPClientHandleConfiguration.SERVER_NAME;
            if (body.get(FTPClientHandleConfiguration.SERVER_NAME) instanceof String) {
                name = (String) body.get(FTPClientHandleConfiguration.SERVER_NAME);
                currEntry = findServer(name);
            } else {
                currEntry = defaultServer;
            }

            checkedAttribute = REMOTE_PATH_KEYWORD;
            sourcePathStr = SetFieldStringValue(checkedAttribute, body, currEntry.remoteFolderPath, true);

            checkedAttribute = LOCAL_PATH_KEYWORD;
            destinationPathStr = SetFieldStringValue(checkedAttribute, body, currEntry.localFolderPath, true);
            new File(destinationPathStr).mkdirs();

            Boolean deleteAfterDownload = (Boolean) options
                    .get(FTPClientHandleConfiguration.DELETE_AFTER_PROCCESING_KEYWORD);
            checkedAttribute = FTPClientHandleConfiguration.DELETE_AFTER_DOWNLOAD;
            deleteAfterDownload = SetFieldBooleanValue(checkedAttribute, body, deleteAfterDownload);

            Boolean addPrefixAfterDownload = (Boolean) options
                    .get(FTPClientHandleConfiguration.AUTO_UPLOAD_TO_DOCUMENT_POSTFIX);
            checkedAttribute = FTPClientHandleConfiguration.AUTO_UPLOAD_TO_DOCUMENT_POSTFIX;
            deleteAfterDownload = SetFieldBooleanValue(checkedAttribute, body, addPrefixAfterDownload);

            Path path = Paths.get(destinationPathStr);
            if (!path.toFile().exists()) {
                rsArray = CreateResponse(FTPClient_DOWNLOAD_FOLDER_FAILED_CODE,
                        FTPClient_DOWNLOAD_FOLDER_FAILED_MESSAGE, destinationPathStr);
            } else {

                FTPUtil ftpUtil = new FTPUtil(log);

                // destinationStruct dest = setDestination(body);

                if (!ftpUtil.downloadFolder(currEntry.server, currEntry.port, currEntry.username, currEntry.password,
                        sourcePathStr, destinationPathStr, deleteAfterDownload, currEntry.connectTimeout,
                        addPrefixAfterDownload, currEntry.name, currEntry)) {
                    rsArray = CreateResponse(FTPClient_DOWNLOAD_FOLDER_FAILED_CODE,
                            FTPClient_DOWNLOAD_FOLDER_FAILED_MESSAGE, "[" + name + "] " + sourcePathStr);
                } else {
                    rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_FOLDER_DOWNLOADED_MESSAGE,
                            "[" + name + "] " + sourcePathStr);

                }
            }

            return rsArray;
        } catch (VantiqFTPClientException exv) {
            throw exv;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        } finally {

        }
    }

    /**
     * The method used to execute a delete file command, triggered by a SELECT on
     * the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processDownloadImage(ExtensionServiceMessage message) throws VantiqFTPClientException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String sourcePathStr = "";
        String destinationPathStr = "";
        String name = "default";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);

            checkedAttribute = FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_PATH;
            String sourceDocumentServerPath = (String) SetFieldStringValue(checkedAttribute, body,
                    defaultServer.sourceDocumentServerPath, true);

            String basePathStr = sourceDocumentServerPath;

            checkedAttribute = REMOTE_PATH_KEYWORD;
            sourcePathStr = SetFieldStringValue(checkedAttribute, body, "", true);

            checkedAttribute = LOCAL_PATH_KEYWORD;
            destinationPathStr = SetFieldStringValue(checkedAttribute, body, "", true);

            File p = new File(destinationPathStr);
            String p1 = p.getParent();
            new File(p1).mkdirs();

            if (sourcePathStr == "" || destinationPathStr == "") {
                rsArray = CreateResponse(FTPClient_DOWNLOAD_DOCUMENT_FAILED_CODE,
                        FTPClient_DOWNLOAD_DOCUMENT_MISSING_MESSAGE, destinationPathStr);
            } else {

                VantiqUtil vantiqUtil = new VantiqUtil(log);

                String fullSourcePath = basePathStr + "/" + sourcePathStr.replace("public/", "");

                if (!vantiqUtil.downloadFromVantiq(fullSourcePath, destinationPathStr)) {
                    rsArray = CreateResponse(FTPClient_DOWNLOAD_DOCUMENT_FAILED_CODE,
                            FTPClient_DOWNLOAD_FOLDER_FAILED_MESSAGE, fullSourcePath);
                } else {
                    rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_IMAGE_DOWNLOADED_MESSAGE,
                            fullSourcePath);

                }
            }

            return rsArray;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        } finally {

        }
    }

    /**
     * The method used to execute an upload imgae command, triggered by a SELECT on
     * the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processUploadImage(ExtensionServiceMessage message) throws VantiqFTPClientException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String sourcePathStr = "";
        String destinationPathStr = "";
        String name = "default";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);

            checkedAttribute = FTPClientHandleConfiguration.DOCUMENT_SERVER;
            String documentServer = SetFieldStringValue(checkedAttribute, body, defaultServer.documentServer, false); // body.get(checkedAttribute).toString();

            checkedAttribute = FTPClientHandleConfiguration.DOCUMENT_SERVER_TOKEN;
            String token = SetFieldStringValue(checkedAttribute, body, defaultServer.documentServerToken, false);

            checkedAttribute = FTPClientHandleConfiguration.BASE_DOCUMENT_PATH;
            String defBaseFolder = (String) config.get(FTPClientHandleConfiguration.BASE_DOCUMENT_PATH);
            String basePathStr = defBaseFolder;

            checkedAttribute = LOCAL_PATH_KEYWORD;
            sourcePathStr = SetFieldStringValue(checkedAttribute, body, "", true);

            checkedAttribute = REMOTE_PATH_KEYWORD;
            destinationPathStr = SetFieldStringValue(checkedAttribute, body, "", true);
            // new File(destinationPathStr).mkdirs();

            if (sourcePathStr == "" || destinationPathStr == "") {
                rsArray = CreateResponse(FTPClient_UPLOAD_DOCUMENT_FAILED_CODE,
                        FTPClient_UPLOAD_DOCUMENT_MISSING_MESSAGE, destinationPathStr);
            } else {

                VantiqUtil vantiqUtil = new VantiqUtil(log, documentServer, token);

                File f = new File(sourcePathStr);
                String fullDestinationPath = basePathStr + "/" + destinationPathStr;

                vantiqUtil.uploadAsImage = true;
                if (!vantiqUtil.uploadToVantiq(f, fullDestinationPath)) {
                    rsArray = CreateResponse(FTPClient_UPLOAD_DOCUMENT_FAILED_CODE,
                            FTPClient_UPLOAD_DOCUMENT_FAILED_MESSAGE, fullDestinationPath);
                } else {
                    rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_IMAGE_UPLOADED_MESSAGE,
                            fullDestinationPath);

                }

            }

            return rsArray;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        } finally {

        }
    }

    /**
     * The method used to execute an upload imgae command, triggered by a SELECT on
     * the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processImportDocument(ExtensionServiceMessage message) throws VantiqFTPClientException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String sourcePathStr = "";
        String destinationPathStr = "";
        String name = "default";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);

            checkedAttribute = FTPClientHandleConfiguration.DOCUMENT_SERVER;
            String documentServer = SetFieldStringValue(checkedAttribute, body, defaultServer.documentServer, false); // body.get(checkedAttribute).toString();

            checkedAttribute = FTPClientHandleConfiguration.DOCUMENT_SERVER_TOKEN;
            String token = SetFieldStringValue(checkedAttribute, body, defaultServer.documentServerToken, false);

            checkedAttribute = FTPClientHandleConfiguration.DOCUMENT_SERVER_PATH;
            destinationPathStr = SetFieldStringValue(checkedAttribute, body, defaultServer.documentServerPath, true);

            checkedAttribute = FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER;
            String sourceDocumentServer = SetFieldStringValue(checkedAttribute, body,
                    defaultServer.sourceDocumentServer, false); // body.get(checkedAttribute).toString();

            checkedAttribute = FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_TOKEN;
            String sourceToken = SetFieldStringValue(checkedAttribute, body, defaultServer.sourceDocumentServerToken,
                    false);

            checkedAttribute = FTPClientHandleConfiguration.SOURCE_DOCUMENT_SERVER_PATH;
            String sourceDocumentServerPath = (String) SetFieldStringValue(checkedAttribute, body,
                    defaultServer.sourceDocumentServerPath, true);

            checkedAttribute = FILENAME_KEYWORD;
            String fileName = SetFieldStringValue(checkedAttribute, body, "", true);

            checkedAttribute = WORKFOLDER_KEYWORD;
            String workFolderPath = SetFieldStringValue(checkedAttribute, body, "", true);

            // checkedAttribute = LOCAL_PATH_KEYWORD;
            // sourcePathStr = SetFieldStringValue(checkedAttribute, body, "", true);

            new File(workFolderPath).mkdirs();

            if (sourceDocumentServer == null || sourceDocumentServer == "" || sourceToken == null || sourceToken == ""
                    || sourceDocumentServerPath == null || sourceDocumentServerPath == "") {
                rsArray = CreateResponse(FTPClient_IMPORT_DOCUMENT_FAILED_CODE,
                        FTPClient_IMPORT_DOCUMENT_SOURCE_MISSING_MESSAGE, sourceDocumentServerPath);
            } else {
                if (fileName == null || fileName == "" || workFolderPath == null | workFolderPath == "") {
                    rsArray = CreateResponse(FTPClient_IMPORT_DOCUMENT_FAILED_CODE,
                            FTPClient_IMPORT_DOCUMENT_FILE_MISSING_MESSAGE, destinationPathStr);
                } else {

                    VantiqUtil downloadVantiqUtil = new VantiqUtil(log, sourceDocumentServer, sourceToken);

                    String fileURL = sourceDocumentServer + "/" + sourceDocumentServerPath + "/" + fileName;
                    String localFilename = workFolderPath + "/" + fileName;

                    // fileURL = "https://dev.vantiq.com/ui/docs/NS/rdmDev/image/i.jpg";

                    if (downloadVantiqUtil.downloadFromVantiq(fileURL, localFilename)) {

                        VantiqUtil vantiqUtil = new VantiqUtil(log, documentServer, token);

                        File f = new File(localFilename);
                        String fullDestinationPath = destinationPathStr + "/" + fileName;

                        vantiqUtil.uploadAsImage = true;
                        if (!vantiqUtil.uploadToVantiq(f, fullDestinationPath)) {
                            rsArray = CreateResponse(FTPClient_IMPORT_DOCUMENT_FAILED_CODE,
                                    FTPClient_IMPORT_UPLOAD_DOCUMENT_FAILED_MESSAGE, fullDestinationPath);
                        } else {
                            rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_IMPORT_DOCUMENT_MESSAGE,
                                    fullDestinationPath);

                        }

                    } else {
                        rsArray = CreateResponse(FTPClient_IMPORT_DOCUMENT_FAILED_CODE,
                                FTPClient_IMPORT_DOWNLOAD_DOCUMENT_FAILED_MESSAGE, fileURL);

                    }

                }
            }

            return rsArray;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        } finally {

        }
    }

    /**
     * The method used to execute a create file command, triggered by a SELECT on
     * the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processClean(ExtensionServiceMessage message) throws VantiqFTPClientException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String destinationPathStr = "";
        int ageInDays = 0;
        String name;

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(BODY_KEYWORD);

            FTPServerEntry currEntry = null;

            checkedAttribute = FTPClientHandleConfiguration.SERVER_NAME;
            if (body.get(FTPClientHandleConfiguration.SERVER_NAME) instanceof String) {
                name = (String) body.get(FTPClientHandleConfiguration.SERVER_NAME);
                currEntry = findServer(name);
            } else {
                currEntry = defaultServer;
            }

            checkedAttribute = REMOTE_PATH_KEYWORD;
            destinationPathStr = SetFieldStringValue(checkedAttribute, body, defaultServer.remoteFolderPath, false);

            checkedAttribute = AGE_IN_DAYS_KEYWORD;
            ageInDays = SetFieldIntegerValue(checkedAttribute, body, currEntry.ageInDays);
            ageInDays = (Integer) body.get(AGE_IN_DAYS_KEYWORD);

            checkedAttribute = "";
            FTPUtil ftpUtil = new FTPUtil(log);

            if (!ftpUtil.cleanRemoteFolder(currEntry.server, currEntry.port, currEntry.username, currEntry.password,
                    destinationPathStr, ageInDays, currEntry.connectTimeout)) {
                rsArray = CreateResponse(FTPClient_CLEAN_FOLDER_FAILED_CODE, FTPClient_CLEAN_FOLDER_FAILED_MESSAGE,
                        destinationPathStr);
            } else {
                rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_FOLDER_CLEANED_MESSAGE,
                        destinationPathStr);

            }

            return rsArray;
        } catch (InvalidPathException exp) {
            throw new VantiqFTPClientException(String.format("Path %s not exist", destinationPathStr), exp);
        } catch (VantiqFTPClientException exv) {
            throw exv;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        }
    }

    /**
     * The method used to execute an append command to an existing file , triggered
     * by a SELECT on the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqFTPClientException
     */
    public HashMap[] processUpload(ExtensionServiceMessage message) throws VantiqFTPClientException {

        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String sourcePathStr = "";
        String destinationPathStr = "";
        String name = "default";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);

            FTPServerEntry currEntry = null;

            checkedAttribute = FTPClientHandleConfiguration.SERVER_NAME;
            if (body.get(FTPClientHandleConfiguration.SERVER_NAME) instanceof String) {
                name = (String) body.get(FTPClientHandleConfiguration.SERVER_NAME);
                currEntry = findServer(name);
            } else {
                currEntry = defaultServer;
            }

            Boolean deleteAfterUpload = (Boolean) options
                    .get(FTPClientHandleConfiguration.DELETE_AFTER_PROCCESING_KEYWORD);
            checkedAttribute = DELETE_AFTER_UPLOAD_KEYWORD;
            deleteAfterUpload = SetFieldBooleanValue(checkedAttribute, body, deleteAfterUpload);

            checkedAttribute = LOCAL_PATH_KEYWORD;
            sourcePathStr = SetFieldStringValue(checkedAttribute, body, currEntry.localFolderPath, true);
            new File(sourcePathStr).mkdirs();

            Path path = Paths.get(sourcePathStr);
            if (!path.toFile().exists()) {
                rsArray = CreateResponse(FTPClient_UPLOAD_FOLDER_FAILED_CODE, FTPClient_UPLOAD_FOLDER_FAILED_MESSAGE,
                        sourcePathStr);
            } else {
                checkedAttribute = REMOTE_PATH_KEYWORD;
                destinationPathStr = SetFieldStringValue(checkedAttribute, body, currEntry.remoteFolderPath, false);

                FTPUtil ftpUtil = new FTPUtil(log);

                if (!ftpUtil.uploadFolder(currEntry.server, currEntry.port, currEntry.username, currEntry.password,
                        destinationPathStr, sourcePathStr, deleteAfterUpload, currEntry.connectTimeout)) {
                    rsArray = CreateResponse(FTPClient_UPLOAD_FOLDER_FAILED_CODE,
                            FTPClient_UPLOAD_FOLDER_FAILED_MESSAGE, "[" + name + "] " + sourcePathStr);
                } else {
                    rsArray = CreateResponse(FTPClient_SUCCESS_CODE, FTPClient_SUCCESS_FOLDER_UPLOADED_MESSAGE,
                            "[" + name + "] " + sourcePathStr);
                }
            }

            return rsArray;
        } catch (VantiqFTPClientException exv) {
            throw exv;
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqFTPClientException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqFTPClientException("General Error", ex);
            }
        } finally {

        }
    }

    Integer SetFieldIntegerValue(String checkedValue, Map<String, Object> body, Integer defaultValue) {
        int value = defaultValue;
        if (body.get(checkedValue) != null) {
            value = (Integer) body.get(checkedValue);
        }

        return value;
    }

    String SetFieldStringValue(String checkedValue, Map<String, Object> body, String defaultValue, Boolean varifyPath) {
        String value = defaultValue;
        if (body.get(checkedValue) != null) {
            value = (String) body.get(checkedValue);
        }
        /*
         * System.out.println("*********************checkedValue:"+checkedValue);
         * System.out.println("varifyPath:"+varifyPath);
         * System.out.println("InLinux:"+isRunningInLinux);
         * System.out.println("defaultValue:"+defaultValue);
         * System.out.println("value:"+value);
         */
        if (varifyPath && isRunningInLinux) {
            value = fixFileFolderPathForUnix(value);
        }
        // System.out.println("after fix value:"+value);

        return value;
    }

    Boolean SetFieldBooleanValue(String checkedValue, Map<String, Object> body, Boolean defaultValue) {
        Boolean value = defaultValue;
        if (body.get(checkedValue) != null) {
            value = (Boolean) body.get(checkedValue);
        }

        return value;
    }

    /**
     * this function detects if the code execute in a conteiner envrionment or not.
     * 
     * @return
     */
    public static Boolean isRunningInsideLinux() {
        return File.separator.equals("/");
    }

    /**
     * Function handles the watcher service notifications. Currently, only new
     * entries are being handled , however , it logs rename files as well without
     * any processing , just to understand if it will be requiered in the future
     *
     * @param fileFolderPath - path to the watched folder
     */
    String fixFileFolderPathForUnix(String filePath) {
        if (filePath.indexOf(":") > -1) {
            filePath = File.separator + filePath.replace(":", "").toLowerCase();
        } else {
            filePath = filePath.toLowerCase();
        }

        return filePath;
    }

    public void reportFTPClientError(Exception e) throws VantiqFTPClientException {
        String message = this.getClass().getCanonicalName() + ": A FTPClient error occurred: " + e.getMessage()
                + ", Error Code: " + e.getCause().getClass().getSimpleName();
        throw new VantiqFTPClientException(message, e);
    }

    public void close() {
        // Close single connection if open
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        executionPool.shutdownNow();
    }
}
