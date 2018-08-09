package io.vantiq.extsrc.objectRecognition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jep.Jep;
import jep.NDArray;

public class DarkflowProcessor implements NeuralNetInterface{ // TODO rename to something not containing interface
    
    Logger log = LoggerFactory.getLogger(this.getClass());
    Jep jep = null;
    
    String modelFile;
    String weightsFile;
    double threshold;
    
    public void setupImageProcessing(Map<String, ?> neuralNet, String modelDirectory) {
        setup(neuralNet, modelDirectory);
        
        setupJep();
    }
    
    private void setup(Map<String, ?> neuralNet, String modelDirectory) {
        // Obtain the files for the net
       if (neuralNet.get("cfgFile") instanceof String && neuralNet.get("weightsFile") instanceof String) {
           modelFile = modelDirectory + (String) neuralNet.get("cfgFile");
           weightsFile = modelDirectory + (String) neuralNet.get("weightsFile");
       } else {
           log.error("No valid combination of cfgFile and weightsFile");
           log.error("Exiting...");
           ObjectRecognitionCore.exit();
       }
   }
    
    private void setupJep() {
        try {
            jep = new Jep();
        } catch (Exception e) {
            log.error("Could not create JEP instance", e);
            ObjectRecognitionCore.exit();
        }
        
        try {
            jep.eval("import sys");
            jep.eval("from darkflow.net.build import TFNet");
        } catch (Exception e) {
            log.error("Could not import darkflow.", e);
            ObjectRecognitionCore.exit();
        }
        
        try {
            jep.eval("options = {\"model\":\"" + modelFile + "\", "
                    + "\"load\":\"" + weightsFile + "\", "
                    + "\"threshold\":" + threshold + "}");
            jep.eval("tfnet = TFNet(options)");
        } catch (Exception e) {
            log.error("Could not create a net with the given options: model='" + modelFile 
                    + "', weights='" + weightsFile + "', threshold=" + threshold);
            ObjectRecognitionCore.exit();
        }
    }
    
    public List<Map> processImage(Mat image) {
        if (image == null || image.empty()) {
            log.warn("Null or empty image sent to be processed. Returning empty ArrayList");
            return new ArrayList<>();
        }
        if (image.channels() != 3) {
            log.warn("Image has (" + image.channels() + ") channels instead of expected 3. Returning empty ArrayList");
            return new ArrayList<>();
        }
        
        try {
            // Translate the image into a Python accessible array
            byte[] b = new byte[image.channels()*image.rows()*image.cols()];
            image.get(0, 0, b);
            NDArray<byte[]> ndBytes= new NDArray(b, image.rows(),image.cols() , image.channels());
            image.release();
            jep.set("img", ndBytes);
            jep.eval("img = img.astype('uint8')");
            
            // Perform the TensorFlow ops on the image
            jep.eval("result = tfnet.return_predict(img)");
            Object bytes = jep.getValue("result"); // gets a List
            if (bytes instanceof List) {
                return (List<Map>)bytes;
            } else {
                log.error("The prediction returned a non-List. Returning empty ArrayList instead");
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Could not interpret message.", e);
            log.error("Returning empty ArayList");
            return new ArrayList<>();
        }
    }
    
    public void close() {
        if (jep != null) {
            try {
                jep.close();
            } catch (Exception e) {
                // Do nothing with the exception
            }
        }
    }
}
