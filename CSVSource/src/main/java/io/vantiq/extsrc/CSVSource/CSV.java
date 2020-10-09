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

public class CSV {
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    String fullFilePath;
    WatchService serviceWatcher;

    ExtensionWebSocketClient oClient ; 
    Map<String, Object>  config;
    Map<String, Object>  options ;
    boolean bContinue = true;

    ExecutorService executionPool = null;

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
        if (config.get("fileExtension")!=null)        {
            extension  = (String) config.get("fileExtension"); 
        }

        if (!extension .startsWith("."))        {
            extension  = "."+extension ; 
        }

        
        if (config.get("filePrefix")!=null)        {
            filePrefix = (String) config.get("filePrefix"); 
        }
        
        if (options.get("extensionAfterProcessing")!=null)        {
            extensionAfterProcessing = (String) options.get("extensionAfterProcessing"); 
        }
        else
            extensionAfterProcessing = extension  + extensionAfterProcessing;
        
        if (!extensionAfterProcessing.startsWith("."))        {
            extensionAfterProcessing = "."+extensionAfterProcessing; 
        }

        if (options.get("deleteAfterProcessing")!=null)        {
            deleteAfterProcessing = (boolean) options.get("deleteAfterProcessing"); 
        }
        
        int maxActiveTasks = MAX_ACTIVE_TASKS; 
        int maxQueuedTasks = MAX_QUEUED_TASKS; 

        if (options.get(MAX_ACTIVE_TASKS_LABEL)!=null)        {
            maxActiveTasks = (int) options.get(MAX_ACTIVE_TASKS_LABEL);
        }
        if (options.get(MAX_QUEUED_TASKS_LABEL)!=null)        {
            maxQueuedTasks = (int) options.get(MAX_QUEUED_TASKS_LABEL);
        }

        executionPool = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

        fileFilter = new FilenameFilter(){
            public boolean accept(File dir, String name) {
               String lowercaseName = name.toLowerCase();

               if (lowercaseName.endsWith(extension ) && lowercaseName.startsWith(filePrefix)) {
                  return true;
               } else {
                  return false;
               }
            }};



    }

    public void setupCSV(ExtensionWebSocketClient oClient,String fileFolderPath , String fullFilePath ,Map<String, Object>  config ,Map<String, Object>  options ,boolean asyncProcessing ) throws VantiqCSVException
    {
        try 
        {
            this.fullFilePath = fullFilePath;
            this.config = config; 
            this.options = options; 
            this.oClient = oClient;

            serviceWatcher = FileSystems.getDefault().newWatchService(); 
            Path path = Paths.get(fileFolderPath);

            path.register(serviceWatcher,ENTRY_CREATE);

            log.info("CSV trying to subscribe to {}",fileFolderPath);

           
            prepareConfigurationData(); 

            new Thread(() -> processThread(fileFolderPath)).start();

            // working on files already exists in folder. 
            if (options.get("processExistingFiles") != null)
            {
                Object processExistingFiles = options.get("processExistingFiles");
                if (processExistingFiles instanceof Boolean )
                if ((boolean)processExistingFiles)
                {
                    log.info("Start working on existing file in folder {}", fileFolderPath);
                    hanldeExistingFiles(fileFolderPath);
                }
            }


        }   
        catch (Exception e)     
        {

            log.error("CSV failed to read  from {}",fullFilePath,e);
            reportCSVError(e);
        }
    }


    void executeInPool(String fileFolderPath , String filename)
    {
        String fullFileName = String.format("%s/%s",fileFolderPath,filename);

        File path = new File (fileFolderPath);
        if (fileFilter.accept(path, filename))
        {
            executionPool.execute(new Runnable() {
                @Override
                public void run() {
                    log.info("start executing {}",fullFileName);
                    try {
                        CSVReader.execute(fullFileName,config,oClient);

                        File file = new File (fullFileName);
                        if (deleteAfterProcessing)
                        {
                            log.info("File {} deleted", fullFileName);
                            file.delete();
                        }
                        else if (extensionAfterProcessing != "")
                        {
                            File newfullFileName = new File( fullFileName.replace(extension , extensionAfterProcessing));
                            log.info("File {} renamed to {}", fullFileName,newfullFileName);
                            file.renameTo(newfullFileName);
                        }

                    } catch (RejectedExecutionException e) {
                        log.error("The queue of tasks has filled, and as a result the request was unable to be processed.", e);
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

            executeInPool(fileFolderPath,fileName);
        }

    }

    void processThread(String fileFolderPath) 
    {
        WatchKey key = null;
        bContinue = true;


        while (bContinue)
        {
            try 
            {
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
                        System.out.println("New path created: " + newPath);

                       executeInPool(fileFolderPath,newPath.toString());
                     

                    } else if (ENTRY_MODIFY == kind) {
                        // modified
                        @SuppressWarnings("unchecked")
                        Path newPath = ((WatchEvent<Path>) watchEvent)
                                .context();
                        // Output
                        System.out.println("New path modified: " + newPath);
                    }
    
                }
                key.reset();
    
            }
            catch (InterruptedException ex1)
            {

            }
        }

        System.out.println("Process thread exited");

    }
    
    public void reportCSVError(Exception e) throws VantiqCSVException {
        String message = this.getClass().getCanonicalName() + ": A CSV error occurred: " + e.getMessage() +
                ", Error Code: " + e.getCause();
        throw new VantiqCSVException(message);
    }


    
    
    public void close() {
        // Close single connection if open
        
        bContinue = false; 

        executionPool.shutdownNow();

        try
        {
            serviceWatcher.close();
        }
        catch (IOException ioex)
        {

        }

 
    }
}
