package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.Map;

import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageProcessingException;

/**
 * An interface for the neural net that will process the image and return a List of data representing the objects found.
 */
public interface NeuralNetInterface2 extends NeuralNetInterface {
    /**
     * Process the image and return a List of Maps describing the objects identified, and any other data the 
     * implementation deems relevant.
     *
     * @param processingParams          Additional parameters required for processing.
     * @param image                     The bytes of a jpg file.
     * @return                          A {@link NeuralNetInterface} containing a List of Maps describing the objects
     *                                  identified and a Map containing other data that the source may need to know.
     *                                  The ordering and contents of the List of Maps is implementation dependent, as 
     *                                  is the contents of the Map containing other useful data.
     * @throws ImageProcessingException Thrown when the image could not be processed for any reason
     * @throws FatalImageException      Thrown when the image processing fails in such a way that the processor cannot
     *                                  recover
     */
    NeuralNetResults processImage(Map<String, ?> processingParams, byte[] image) throws ImageProcessingException;
    
    /**
     * Process the image using the options in {@code request} and return a List of Maps describing the objects
     * identified, and any other data the implementation deems relevant
     *
     * @param processingParams          Additional parameters required for processing.
     * @param image                     The bytes of a jpg file.
     * @param request                   The options accompanying a Query message.
     * @return                          A {@link NeuralNetInterface} containing a List of Maps describing the objects
     *                                  identified and a Map containing other data that the source may need to know.
     *                                  The ordering and contents of the List of Maps is implementation dependent, as 
     *                                  is the contents of the Map containing other useful data.
     * @throws ImageProcessingException Thrown when the image could not be processed for any reason
     * @throws FatalImageException      Thrown when the image processing fails in such a way that the processor cannot
     *                                  recover
     */
    NeuralNetResults processImage(Map<String, ?> processingParams, byte[] image, Map<String, ?> request) throws ImageProcessingException;
}
