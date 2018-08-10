package io.vantiq.extsrc.objectRecognition;

import java.util.List;
import java.util.Map;

/**
 * An interface for the neural net that will process the image and return a List of data representing the objects found.
 */
public interface NeuralNetInterface {
    
    /**
     * Setup the neural net for image processing.
     * @param neuralNetConfig   A map containing the configuration necessary to setup the neural net. This will be the
     *                          'neuralNet' object in the source configuration document.
     */
    public void setupImageProcessing(Map<String,?> neuralNetConfig, String modelDirectory) throws Exception;
    
    /**
     * Process the image and return a List of Maps describing the objects identified
     *
     * @param image The bytes of a jpg file.
     * @return      A List returning Maps describing the objects identified. The ordering and contents of the Maps is
     *              implementation dependent. 
     */
    public List<Map> processImage(byte[] image);
    
    /**
     * Safely close any resources obtained by the net
     */
    public void close();
}
