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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;
import io.vantiq.extsrc.CSVSource.exception.VantiqCSVException;
/**
 * Implemeting WatchService based on configuration given in csvConfig in extension configuration.   
 * Sets up the source using the configuration document, which looks as below.
 *<pre>    "csvConfig": {
 *     "fileFolderPath": "d:/tmp/csv",
 *     "filePrefix": "eje",
 *     "fileExtension": "csv",
 *     "maxLinesInEvent": 200,
 *     "schema": {
 *	      "field0": "value",
 *   	  "field2": "flag",
 *     	"field1": "YScale"
 *  	}
 *  }</pre>
 * 
 * The class enable multiple activites using thread pool based on the attached configuration
 * <pre> "options": {
 *     "maxActiveTasks": 2,
 *     "maxQueuedTasks": 4,
 *     "processExistingFiles": true,
 *     "extensionAfterProcessing": "csv.done",
 *     "deleteAfterProcessing": false
 *  }</pre>
 *
 */

public class CSV {
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());


    
    // Used to receive configuration informatio . 
    Map<String, Object>  config;
    Map<String, Object>  options ;

    // Control of thread should continue to work , set to false in Stop. 
    boolean bContinue = true;

    // Components used 
    WatchService serviceWatcher;
    ExecutorService executionPool = null;
    ExtensionWebSocketClient oClient ; 

    String fullFilePath;
    String extension = ".csv"; 
    String filePrefix = ""; 
    FilenameFilter fileFilter ; 
    String extensionAfterProcessing = ".done";
    boolean deleteAfterProcessing = false; 

    private static final int MAX_ACTIVE_TASKS = 5;
    private static final int MAX_QUEUED_TASKS = 10;

    private static final String MAX_ACTIVE_TASKS_LABEL = "maxActiveTasks";
    private static final String MAX_QUEUED_TASKS_LABEL = "maxQueuedTasks";

    void prepareConfigurationData()    {

        extension  = "csv"; 
        
        if (config.get("fileExtension") != null)        {
            extension  = (String) config.get("fileExtension"); 
        }

        if (!extension .startsWith("."))        {
            extension  = "." + extension ; 
        }

        
        if (config.get("filePrefix")!=null)        {
            filePrefix = (String) config.get("filePrefix"); 
        }
        
        if (options.get("extensionAfterProcessing") != null)        {
            extensionAfterProcessing = (String) options.get("extensionAfterProcessing"); 
        }
        else
            extensionAfterProcessing = extension  + extensionAfterProcessing;
        
        if (!extensionAfterProcessing.startsWith("."))        {
            extensionAfterProcessing = "." + extensionAfterProcessing; 
        }

        if (options.get("deleteAfterProcessing") != null)        {
            deleteAfterProcessing = (boolean) options.get("deleteAfterProcessing"); 
        }
        
        int maxActiveTasks = MAX_ACTIVE_TASKS; 
        int maxQueuedTasks = MAX_QUEUED_TASKS; 

        if (options.get(MAX_ACTIVE_TASKS_LABEL) != null)        {
            maxActiveTasks = (int) options.get(MAX_ACTIVE_TASKS_LABEL);
        }
        if (options.get(MAX_QUEUED_TASKS_LABEL) != null)        {
            maxQueuedTasks = (int) options.get(MAX_QUEUED_TASKS_LABEL);
        }

        executionPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

        fileFilter = (dir, name) -> {
            String lowercaseName = name.toLowerCase();
    
            return lowercaseName.endsWith(extension) && lowercaseName.startsWith(filePrefix);
            };
    }

    public void setupCSV(ExtensionWebSocketClient oClient ,String fileFolderPath ,String fullFilePath ,Map<String, Object>  config ,Map<String, Object>  options  ) throws VantiqCSVException
    {
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

            // working on files already exists in folder. 
            if (options.get("processExistingFiles") != null)  {

                Object processExistingFiles = options.get("processExistingFiles");
                if (processExistingFiles instanceof Boolean )
                    if ((boolean)processExistingFiles) {

                        log.info("Start working on existing file in folder {}", fileFolderPath);
                        hanldeExistingFiles(fileFolderPath);

                    }
            }


        }   
        catch (Exception e)     {

            log.error("CSV failed to read  from {}", fullFilePath, e);
            reportCSVError(e);
        }
    }

    void executeInPool(String fileFolderPath, String filename)
    {
        String fullFileName = String.format("%s/%s", fileFolderPath, filename);

        File path = new File (fileFolderPath);
        if (fileFilter.accept(path, filename)) {

            executionPool.execute(new Runnable() {
                @Override
                public void run() {
                    log.info("start executing {}", fullFileName);
                    try {
                        CSVReader.execute(fullFileName, config, oClient);

                        File file = new File (fullFileName);
                        if (deleteAfterProcessing) {
                            log.info("File {} deleted", fullFileName);
                            file.delete();
                        }
                        else if (extensionAfterProcessing != "") {
                            File newfullFileName = new File( fullFileName.replace(extension, extensionAfterProcessing));
                            log.info("File {} renamed to {}", fullFileName,newfullFileName);
                            file.renameTo(newfullFileName);
                        }

                    } catch (RejectedExecutionException e) {
                        log.error("The queue of tasks has filled, and as a result the request was unable to be processed.", e);
                    } catch (Exception ex) {
                        log.error("Failure in executing Tass", ex);
                    }
                }

            });
        }

    }

    void hanldeExistingFiles(String fileFolderPath)
    {

        File folder = new File(fileFolderPath);

        String[] listOfFiles = folder.list(fileFilter);
        for (String fileName : listOfFiles) {

            executeInPool(fileFolderPath, fileName);
        }

    }

    /**
     * currently , only new entries are neing handled , however , it recorded rename as well 
     * buit still not processes , just to understand if it will be requiered in the future 
     */
    void processThread(String fileFolderPath) 
    {
        WatchKey key = null;
        bContinue = true;


        while (bContinue)
        {
            try {

                key = serviceWatcher.take();
                // Dequeueing events
                Kind<?> kind = null;
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    // Get the type of the event
                    kind = watchEvent.kind();

                    if (OVERFLOW == kind) {
                        continue; // loop
                    } else if (ENTRY_CREATE == kind) {
                        // A new Path was created
                        @SuppressWarnings("unchecked")
                        Path newPath = ((WatchEvent<Path>) watchEvent)
                                .context();
                        // Output
                        log.info("New path created: " + newPath);

                       executeInPool(fileFolderPath, newPath.toString());
                     

                    } else if (ENTRY_MODIFY == kind) {
                        // modified
                        @SuppressWarnings("unchecked")
                        Path newPath = ((WatchEvent<Path>) watchEvent)
                                .context();
                        // Output
                        log.info("New path modified: " + newPath);
                    }
    
                }
                key.reset();
    
            }
            catch (InterruptedException ex1)
            {
                log.error("processThread inloop failure", ex1);
            }
        }

        log.info("Process thread exited");

    }
    
    public void reportCSVError(Exception e) throws VantiqCSVException {
        String message = this.getClass().getCanonicalName() + ": A CSV error occurred: " + e.getMessage() +
                ", Error Code: " + e.getCause();
        throw new VantiqCSVException(message,e);
    }


    
    
    public void close() {
        // Close single connection if open
        
        bContinue = false; 

        executionPool.shutdownNow();

        try{
            serviceWatcher.close();
        }
        catch (IOException ioex) {
        }

 
    }
}
