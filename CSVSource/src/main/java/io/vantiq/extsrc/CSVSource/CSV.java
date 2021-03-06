/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.CSVSource;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.CSVSource.exception.VantiqCSVException;

/**
 * Implementing WatchService based on configuration given in csvConfig in
 * extension configuration. Sets up the source using the configuration document,
 * which looks as below.
 * 
 * <pre>
 * "csvConfig": {
 *     "fileFolderPath": "d:/tmp/csv",
 *     "filePrefix": "eje",
 *     "fileExtension": "csv",
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
 *     "extensionAfterProcessing": "csv.done",
 *     "deleteAfterProcessing": false
 *  }
 * </pre>
 *
 * 
 * Later version added support for fixed length records where schema
 * configuration is similar to above
 * 
 * <pre>
 * "csvConfig": 
 * { "fileFolderPath":"d:/tmp/csv", 
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
public class CSV {
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    // Used to receive configuration informatio .
    Map<String, Object> config;
    Map<String, Object> options;

    // Control of thread should continue to work, set to false in Stop.
    boolean bContinue = true;

    // Components used
    WatchService serviceWatcher;
    ExecutorService executionPool = null;
    ExtensionWebSocketClient oClient;

    String fullFilePath;
    String extension = ".csv";
    String filePrefix = "";
    FilenameFilter fileFilter;
    String extensionAfterProcessing = ".done";
    boolean deleteAfterProcessing = false;

    Thread currThread;

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    private static final String MAX_ACTIVE_TASKS_LABEL = "maxActiveTasks";
    private static final String MAX_QUEUED_TASKS_LABEL = "maxQueuedTasks";

    private static final String BODY_KEYWORD = "body";
    private static final String PATH_KEYWORD = "path";
    private static final String FILE_KEYWORD = "file";
    private static final String CONTENT_KEYWORD = "content";
    private static final String TEXT_KEYWORD = "text";

    public static String CSV_NOFILE_CODE = "io.vantiq.extsrc.csvsource.nofile";
    public static String CSV_NOFILE_MESSAGE = "File does not exist.";
    public static String CSV_NOFOLDER_CODE = "io.vantiq.extsrc.csvsource.nofolder";
    public static String CSV_NOFOLDER_MESSAGE = "Folder does not exist.";
    public static String CSV_FILEEXIST_CODE = "io.vantiq.extsrc.csvsource.fileexists";
    public static String CSV_FILEEXIST_MESSAGE = "File already exists.";

    public static String CSV_SUCCESS_CODE = "io.vantiq.extsrc.csvsource.success";
    public static String CSV_SUCCESS_FILE_CREATED_MESSAGE = "File Created Successfully";
    public static String CSV_SUCCESS_FILE_APPENDED_MESSAGE = "File Appended Successfully";
    public static String CSV_SUCCESS_FILE_DELETED_MESSAGE = "File Deleted Successfully.";

    /**
     * Create resource which will required by the extension activity
     */
    void prepareConfigurationData() {
        extension = "csv";

        if (config.get("fileExtension") != null) {
            extension = (String) config.get("fileExtension");
        }
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        if (config.get("filePrefix") != null) {
            filePrefix = (String) config.get("filePrefix");
        }
        if (options.get("extensionAfterProcessing") != null) {
            extensionAfterProcessing = (String) options.get("extensionAfterProcessing");
        } else {
            extensionAfterProcessing = extension + extensionAfterProcessing;
        }

        if (!extensionAfterProcessing.startsWith(".")) {
            extensionAfterProcessing = "." + extensionAfterProcessing;
        }
        if (options.get("deleteAfterProcessing") != null) {
            deleteAfterProcessing = (boolean) options.get("deleteAfterProcessing");
        }

        int maxActiveTasks = MAX_ACTIVE_TASKS;
        int maxQueuedTasks = MAX_QUEUED_TASKS;

        if (options.get(MAX_ACTIVE_TASKS_LABEL) != null) {
            maxActiveTasks = (int) options.get(MAX_ACTIVE_TASKS_LABEL);
        }
        if (options.get(MAX_QUEUED_TASKS_LABEL) != null) {
            maxQueuedTasks = (int) options.get(MAX_QUEUED_TASKS_LABEL);
        }

        fileFilter = (dir, name) -> {
            String lowercaseName = name.toLowerCase();

            return lowercaseName.endsWith(extension.toLowerCase())
                    && lowercaseName.startsWith(filePrefix.toLowerCase());
        };

        executionPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

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
     * @throws VantiqCSVException
     */
    public void setupCSV(ExtensionWebSocketClient oClient, String fileFolderPath, String fullFilePath,
            Map<String, Object> config, Map<String, Object> options) throws VantiqCSVException {
        try {
            this.fullFilePath = fullFilePath;
            this.config = config;
            this.options = options;
            this.oClient = oClient;

            serviceWatcher = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(fileFolderPath);
            path.register(serviceWatcher, ENTRY_CREATE);
            log.info("CSV trying to subscribe to {}", fileFolderPath);

            prepareConfigurationData();

            new Thread(() -> processThread(fileFolderPath)).start();

            // working on files already that exist in folder.
            if (options.get("processExistingFiles") != null) {
                Object processExistingFiles = options.get("processExistingFiles");
                if (processExistingFiles instanceof Boolean)
                    if ((boolean) processExistingFiles) {
                        log.info("Start working on existing file in folder {}", fileFolderPath);
                        handleExistingFiles(fileFolderPath);
                    }
            }
        } catch (Exception e) {
            log.error("CSV failed to read  from {}", fullFilePath, e);
            reportCSVError(e);
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
        String fullFileName = String.format("%s/%s", fileFolderPath, filename);
        File path = new File(fileFolderPath);

        if (fileFilter.accept(path, filename)) {
            executionPool.execute(new Runnable() {
                @Override
                public void run() {
                    log.info("start executing {}", fullFileName);
                    try {
                        String configType = (String) config.get("fileType");

                        if (configType != null && configType.toLowerCase().equals("fixedlength")) {
                            CSVReader.executeFixedRecord(fullFileName, config, oClient);
                        } else {
                            CSVReader.execute(fullFileName, config, oClient);
                        }

                        File file = new File(fullFileName);
                        if (deleteAfterProcessing) {
                            log.info("File {} deleted", fullFileName);
                            file.delete();
                        } else if (extensionAfterProcessing != "") {
                            File newfullFileName = new File(fullFileName.replace(extension, extensionAfterProcessing));
                            log.info("File {} renamed to {}", fullFileName, newfullFileName);
                            file.renameTo(newfullFileName);
                        }
                    } catch (RejectedExecutionException e) {
                        log.error(
                                "The queue of tasks has filled, and as a result the request was unable to be processed.",
                                e);
                    } catch (Exception ex) {
                        log.error("Failure in executing Task", ex);
                    }
                }
            });
        }
    }

    /**
     * Responsible for handling files which already exist in folder and will not be
     * notified by the WatchService
     * 
     * @param fileFolderPath - the path of the files waiting to be processed .
     */
    void handleExistingFiles(String fileFolderPath) {
        File folder = new File(fileFolderPath);

        String[] listOfFiles = folder.list(fileFilter);
        for (String fileName : listOfFiles) {
            executeInPool(fileFolderPath, fileName);
        }
    }

    /**
     * Create standard vantiq respone for the different suceess or failures as part
     * of the select operations, those are being repliyed to vail.
     * 
     * @param code    : code of the error ( io.vantiq.extsrc.csvsource.success is
     *                success otherwise failure)
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

        if (code.equals("io.vantiq.extsrc.csvsource.success")) {
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
     * @throws VantiqCSVException
     */
    public HashMap[] processDelete(ExtensionServiceMessage message) throws VantiqCSVException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String pathStr = "";
        String fileStr = "";
        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(BODY_KEYWORD);
            checkedAttribute = PATH_KEYWORD;
            pathStr = (String) body.get(PATH_KEYWORD);
            Path path = Paths.get(pathStr);
            if (!path.toFile().exists()) {
                rsArray = CreateResponse(CSV_NOFOLDER_CODE, CSV_NOFOLDER_MESSAGE, pathStr);
            }

            checkedAttribute = FILE_KEYWORD;
            fileStr = (String) body.get(FILE_KEYWORD);
            checkedAttribute = "";
            String fullFilePath = path.toString() + "\\" + fileStr;
            File file = new File(fullFilePath);
            if (file.exists()) {
                file.delete();
                rsArray = CreateResponse(CSV_SUCCESS_CODE, CSV_SUCCESS_FILE_DELETED_MESSAGE, file.toString());
                // file already created.
            } else {
                rsArray = CreateResponse(CSV_NOFILE_CODE, CSV_NOFILE_MESSAGE, file.toString());

            }
            return rsArray;
        } catch (InvalidPathException exp) {
            throw new VantiqCSVException(String.format("Path %s not exist", pathStr), exp);
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqCSVException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqCSVException("General Error", ex);
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
     * @throws VantiqCSVException
     */
    public HashMap[] processCreate(ExtensionServiceMessage message) throws VantiqCSVException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String pathStr = "";
        String fileStr = "";
        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(BODY_KEYWORD);
            checkedAttribute = PATH_KEYWORD;
            pathStr = (String) body.get(PATH_KEYWORD);
            Path path = Paths.get(pathStr);
            if (!path.toFile().exists()) {
                java.nio.file.Files.createDirectories(path);
                log.info("Folder created: {}", path.toString());
            }

            checkedAttribute = FILE_KEYWORD;
            fileStr = (String) body.get(FILE_KEYWORD);
            checkedAttribute = "";
            String fullFilePath = path.toString() + "\\" + fileStr;
            File file = new File(fullFilePath);
            if (file.exists()) {
                // file already created.
                rsArray = CreateResponse(CSV_FILEEXIST_CODE, CSV_FILEEXIST_MESSAGE, file.toString());
            } else {

                file.createNewFile();

                try (FileOutputStream fos = new FileOutputStream(file);
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))) {

                    checkedAttribute = CONTENT_KEYWORD;
                    List<Map<String, Object>> content = (List<Map<String, Object>>) body.get(CONTENT_KEYWORD);

                    for (int i = 0; i < content.size(); i++) {

                        String line = (String) content.get(i).get(TEXT_KEYWORD);
                        bw.write(line);
                        bw.newLine();
                    }

                    bw.close();
                    rsArray = CreateResponse(CSV_SUCCESS_CODE, CSV_SUCCESS_FILE_CREATED_MESSAGE, file.toString());
                }

            }
            return rsArray;
        } catch (InvalidPathException exp) {
            throw new VantiqCSVException(String.format("Path %s not exist", pathStr), exp);
        } catch (FileNotFoundException exp) {
            throw new VantiqCSVException(String.format("File %s not exist", fullFilePath), exp);
        } catch (IOException exp) {
            throw new VantiqCSVException("IO Exception", exp);
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqCSVException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqCSVException("General Error", ex);
            }
        }
    }

    /**
     * The method used to execute an append command to an existing file , triggered
     * by a SELECT on the respective source from VANTIQ.
     * 
     * @param message
     * @return
     * @throws VantiqCSVException
     */
    public HashMap[] processAppend(ExtensionServiceMessage message) throws VantiqCSVException {
        HashMap[] rsArray = null;
        String checkedAttribute = BODY_KEYWORD;
        String pathStr = "";
        String fileStr = "";

        try {
            Map<String, ?> request = (Map<String, ?>) message.getObject();
            Map<String, Object> body = (Map<String, Object>) request.get(checkedAttribute);
            checkedAttribute = PATH_KEYWORD;
            pathStr = (String) body.get(PATH_KEYWORD);
            Path path = Paths.get(pathStr);
            if (!path.toFile().exists()) {
                rsArray = CreateResponse(CSV_NOFOLDER_CODE, CSV_NOFOLDER_MESSAGE, pathStr);
            }

            checkedAttribute = FILE_KEYWORD;
            fileStr = (String) body.get(FILE_KEYWORD);
            String fullFilePath = path.toString() + "\\" + fileStr;
            File file = new File(fullFilePath);
            if (file.exists()) {
                try (FileWriter fw = new FileWriter(file, true); BufferedWriter bw = new BufferedWriter(fw)) {

                    checkedAttribute = CONTENT_KEYWORD;
                    List<Map<String, Object>> content = (List<Map<String, Object>>) body.get(CONTENT_KEYWORD);
                    checkedAttribute = "";
                    for (int i = 0; i < content.size(); i++) {

                        String line = (String) content.get(i).get(TEXT_KEYWORD);
                        bw.append(line);
                        bw.newLine();
                    }

                    rsArray = CreateResponse(CSV_SUCCESS_CODE, CSV_SUCCESS_FILE_APPENDED_MESSAGE, file.toString());
                } finally {

                }
                // file already created.
            } else {
                rsArray = CreateResponse(CSV_NOFILE_CODE, CSV_NOFILE_MESSAGE, file.toString());

            }

            return rsArray;
        } catch (InvalidPathException exp) {
            throw new VantiqCSVException(String.format("Path %s not exist", pathStr), exp);
        } catch (Exception ex) {
            if (checkedAttribute != "") {
                throw new VantiqCSVException(
                        String.format("Illegal request structure , attribute %s doesn't exist", checkedAttribute), ex);
            } else {
                throw new VantiqCSVException("General Error", ex);
            }
        } finally {

        }
    }

    /**
     * Function handles the watcher service notifications. Currently, only new
     * entries are being handled , however , it logs rename files as well without
     * any processing , just to understand if it will be requiered in the future
     *
     * @param fileFolderPath - path to the watched folder
     */
    void processThread(String fileFolderPath) {
        WatchKey key = null;
        bContinue = true;

        while (bContinue) {
            try {
                key = serviceWatcher.poll(100, TimeUnit.MILLISECONDS);
                if (key != null) {
                    // Dequeue events
                    Kind<?> kind = null;
                    for (WatchEvent<?> watchEvent : key.pollEvents()) {
                        // Get the type of the event
                        kind = watchEvent.kind();

                        if (OVERFLOW == kind) {
                            continue; // loop
                        } else if (ENTRY_CREATE == kind) {
                            // A new Path was created
                            @SuppressWarnings("unchecked")
                            Path newPath = ((WatchEvent<Path>) watchEvent).context();
                            log.info("New path created: {}", newPath.toString());
                            executeInPool(fileFolderPath, newPath.toString());
                        } else if (ENTRY_MODIFY == kind) {
                            // modified
                            @SuppressWarnings("unchecked")
                            Path newPath = ((WatchEvent<Path>) watchEvent).context();
                            log.debug("Ignored path modified: {}", newPath.toString());
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException ex1) {
                log.error("processThread inloop failure", ex1);
            }
        }
        log.info("Process thread exited");
    }

    public void reportCSVError(Exception e) throws VantiqCSVException {
        String message = this.getClass().getCanonicalName() + ": A CSV error occurred: " + e.getMessage()
                + ", Error Code: " + e.getCause().getClass().getSimpleName();
        throw new VantiqCSVException(message, e);
    }

    public void close() {
        // Close single connection if open
        try {
            bContinue = false;
            wait(1000);
        } catch (Exception ex) {
        }
        log.info("*******************Process thread exited");
        executionPool.shutdownNow();
        try {
            serviceWatcher.close();
        } catch (IOException ioex) {
        }
    }
}
