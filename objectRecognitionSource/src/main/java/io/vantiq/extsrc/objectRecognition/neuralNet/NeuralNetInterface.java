package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

/**
 * An interface for the neural net that will process the image and return a List of data representing the objects found.
 */
public interface NeuralNetInterface {
    
    /**
     * Setup the neural net for image processing.
     * @param neuralNetConfig   A map containing the configuration necessary to setup the neural net. This will be the
     *                          'neuralNet' object in the source configuration document.
     * @param modelDirectory    The directory in which it should look for the models
     * @throws Exception        Thrown when an error occurs during setup.
     */
    public abstract void setupImageProcessing(Map<String,?> neuralNetConfig, String modelDirectory) throws Exception;
    
    /**
     * Process the image and return a List of Maps describing the objects identified. For each instance of the
     * retriever, only one of {@link #processImage(byte[])} and {@link #processImage(byte[],Map)} will be called
     * depending on whether the source is setup for Queries.
     *
     * @param image                     The bytes of a jpg file.
     * @return                          A List returning Maps describing the objects identified. The ordering and 
     *                                  contents of the Maps is implementation dependent.
     * @throws ImageProcessingException Thrown when the image could not be processed for any reason
     * @throws FatalImageException      Thrown when the image processing fails in such a way that the processor cannot
     *                                  recover
     */
    public abstract List<Map> processImage(byte[] image) throws ImageProcessingException;
    
    /**
     * Process the image using the options in {@code request} and return a List of Maps describing the objects
     * identified. For each instance of the retriever, only one of {@link #processImage(byte[])} and
     * {@link #processImage(byte[],Map)} will be called depending on whether the source is setup for Queries.
     *
     * @param image                     The bytes of a jpg file.
     * @param request                   The options accompanying a Query message.
     * @return                          A List returning Maps describing the objects identified. The ordering and 
     *                                  contents of the Maps is implementation dependent.
     * @throws ImageProcessingException Thrown when the image could not be processed for any reason
     * @throws FatalImageException      Thrown when the image processing fails in such a way that the processor cannot
     *                                  recover
     */
    public abstract List<Map> processImage(byte[] image, Map<String,?> request) throws ImageProcessingException;
    
    /**
     * Safely close any resources obtained by the net
     */
    public abstract void close();
}
