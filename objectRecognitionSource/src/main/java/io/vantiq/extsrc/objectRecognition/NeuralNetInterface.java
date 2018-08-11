package io.vantiq.extsrc.objectRecognition;

import java.util.List;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

/**
 * An interface for the neural net that will process the image and return a List of data representing the objects found.
 */
public abstract class NeuralNetInterface {
    
    protected boolean threadSafe = true;
    public boolean isThreadSafe() {
        return threadSafe;
    }
    
    /**
     * Setup the neural net for image processing.
     * @param neuralNetConfig   A map containing the configuration necessary to setup the neural net. This will be the
     *                          'neuralNet' object in the source configuration document.
     * @param modelDirectory    The directory in which it should look for the models
     * @throws Exception        Thrown when an error occurs during setup.
     */
    public abstract void setupImageProcessing(Map<String,?> neuralNetConfig, String modelDirectory) throws Exception;
    
    /**
     * Process the image and return a List of Maps describing the objects identified
     *
     * @param image                     The bytes of a jpg file.
     * @return                          A List returning Maps describing the objects identified. The ordering and 
     *                                  contents of the Maps is implementation dependent.
     * @throws ImageProcessingException Thrown when the image could not be processed for any reason
     */
    public abstract List<Map> processImage(byte[] image) throws ImageProcessingException;
    
    /**
     * Safely close any resources obtained by the net
     */
    public abstract void close();
}
