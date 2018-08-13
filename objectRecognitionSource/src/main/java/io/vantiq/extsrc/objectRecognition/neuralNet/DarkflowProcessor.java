package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import jep.Jep;
import jep.NDArray;

/**
 * Slightly faster than YoloProcessor, but cannot have multiple instances and difficult to install the dependencies
 * correctly.
 * <br>
 * Unique settings are: 
 * <ul>
 *  <li>{@code cfgFile}: Required. The .cfg file for the model.
 *  <li>{@code weightsFile}: Required. The weights for the model.
 *  <li>{@code threshold}: Optional. The confidence threshold at which an identification will be considered accurate 
 *                  enough to include in the output, on a scale of 0-1 (exclusive). Default is 0.5. 
 * </ul>
 */
public class DarkflowProcessor extends NeuralNetInterface{
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    Jep jep = null;
    JepThread jepThread = null;
    
    String modelFile;
    String weightsFile;
    double threshold = 0.5;
    
    /**
     * Sets up Java Embedded Python to run darkflow.
     * @param neuralNetConfig   A map containing the configuration necessary to setup the neural net. This will be the
     *                          'neuralNet' object in the source configuration document.
     * @param modelDirectory    The directory in which it should look for the models
     * @throws Exception        Thrown when an error occurs during setup.
     */
    @Override
    public void setupImageProcessing(Map<String, ?> neuralNet, String modelDirectory) throws Exception {
        jepThread = new JepThread();
        jepThread.setupImageProcessing(neuralNet, modelDirectory);
        jepThread.start();
        synchronized(jepThread) {
            if (jepThread.neuralNet != null) {
                try {
                    jepThread.wait();
                } catch (InterruptedException e) {}
            }
        }
        if (jepThread.exception != null) {
            throw jepThread.exception;
        }
    }
    
    /**
     * Save the necessary data from the given map.
     * @param neuralNet         The configuration from 'neuralNet' in the config document
     * @param modelDirectory    The directory in which the .cfg and weights files are placed
     * @throws Exception        Thrown when an invalid configuration is requested
     */
    private void setup(Map<String, ?> neuralNet, String modelDirectory) throws Exception{
        // Obtain the files for the net
       if (neuralNet.get("cfgFile") instanceof String && neuralNet.get("weightsFile") instanceof String) {
           modelFile = modelDirectory + "cfg/" + (String) neuralNet.get("cfgFile");
           weightsFile = modelDirectory + (String) neuralNet.get("weightsFile");
       } else {
           throw new Exception("No valid combination of cfgFile and weightsFile");
       }
       if (neuralNet.get("threshold") instanceof Double || neuralNet.get("threshold") instanceof Float) {
           double thresh = (Double) neuralNet.get("threshold");
           if (thresh > 0 && thresh <= 1) {
               threshold = thresh;
           }
       }
    }
    
    /**
     * Imports everything into JEP so that the first attempt to read the image will go quickly
     * @throws Exception
     */
    private void setupJep() throws Exception {
        try {
            jep = new Jep();
        } catch (Exception e) {
            throw new Exception("Could not create JEP instance", e);
        }
        
        try {
            jep.eval("import sys");
            jep.eval("from darkflow.net.build import TFNet");
        } catch (Exception e) {
            throw new Exception("Could not import darkflow.", e);
        }
        
        try {
            jep.eval("options = {\"model\":\"" + modelFile + "\", "
                    + "\"load\":\"" + weightsFile + "\", "
                    + "\"threshold\":" + threshold + "}");
            jep.eval("tfnet = TFNet(options)");
        } catch (Exception e) {
            throw new Exception("Could not create a net with the given options: model='" + modelFile 
                    + "', weights='" + weightsFile + "', threshold=" + threshold, e);
        }
        
        try {
            jep.eval("def procImage(img):\n" +
                    "\treturn tfnet.return_predict(img)\n");
        } catch (Exception e) {
            throw new Exception("Could not create python function.", e);
        }
    }
    
    /**
     * Passes the image to JEP and processes it.
     */
    public List<Map> processImage(byte[] image) {
        jepThread.processImage(image);
        synchronized (jepThread) {
            try {
                jepThread.wait();
            } catch (InterruptedException e) {
            }
        }
        return jepThread.retrieveProcessedImage();
    }
    
    public List<Map> doImageProcessing(byte[] image) {
        if (image == null || image.length == 0) {
            log.warn("Null or empty image sent to be processed. Returning empty ArrayList");
            return new ArrayList<>();
        }
        
        try {
            // Translate the image into a Python accessible array
            Mat mat = Imgcodecs.imdecode(new MatOfByte(image), Imgcodecs.IMREAD_UNCHANGED);
            byte[] b = new byte[mat.channels()*mat.rows()*mat.cols()];
            mat.get(0, 0, b);
            NDArray<byte[]> ndBytes= new NDArray(b, true, mat.rows(),mat.cols() , mat.channels());
            mat.release();
            // Perform the TensorFlow ops on the image
            Object bytes = jep.invoke("procImage", ndBytes);
            if (bytes instanceof List) {
                return (List<Map>)bytes;
            } else {
                log.error("The prediction returned a non-List. Returning empty ArrayList instead");
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Could not interpret image.", e);
            log.error("Returning empty ArrayList");
            throw new FatalImageException("JEP error on failing message", e);
        }
    }
    
    public void close() {
        if (jep != null) {
            try {
                jep.eval("exit()");
            } catch (Exception e) {}
            try {
                jep.close();
            } catch (Exception e) {}
        }
        if (jepThread != null && jepThread.isAlive()) {
            jepThread.threadStop = true;
        }
    }
    
    private class JepThread extends Thread {
        Logger log = LoggerFactory.getLogger(this.getClass());
        
        boolean threadStop = false;
        byte[] image = null;
        Map<String, ?> neuralNet = null;
        String modelDirectory = null;
        
        Exception exception = null;
        
        List<Map> imageResults = new ArrayList<>();
        
        
        public void setupImageProcessing(Map<String, ?> neuralNet, String modelDirectory) {
            this.neuralNet = neuralNet;
            this.modelDirectory = modelDirectory;
            
        }
        
        public void processImage(byte[] image) {
            this.image = image;
        }
        public List<Map> retrieveProcessedImage() {
            List<Map> results = imageResults;
            imageResults = new ArrayList<>();
            return results;
        }
        
        @Override
        public void run() {
            if (neuralNet == null) {
                log.error("JepThread started without input data");
            } else {
                try {
                    setup(neuralNet, modelDirectory);
                } catch (Exception e) {
                    exception = new Exception("Could not use settings", e);
                    threadStop = true;
                }
                try {
                    setupJep();
                } catch (Exception e) {
                    exception = new Exception("Could not run Jep", e);
                    threadStop = true;
                }
                neuralNet = null;
                modelDirectory = null;
                synchronized (this) {
                    this.notify();
                }
            }
            while (!threadStop) {
                if (image != null) {
                    long before = System.currentTimeMillis();
                    imageResults = doImageProcessing(image);
                    long after = System.currentTimeMillis();
                    log.info("Image processing time: " + (after - before) / 1000 + "." + (after - before) % 1000 + " seconds");
                    synchronized (this) {
                        this.notify();
                    }
                }
                Thread.yield();
            }
        }
    }
}
