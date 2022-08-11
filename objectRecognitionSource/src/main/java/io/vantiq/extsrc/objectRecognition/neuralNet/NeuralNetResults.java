package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

/**
 * A class used to transfer the results and miscellaneous data from neural nets.
 */
public class NeuralNetResults {
    /**
     * The name of the last saved image (used in tests)
     */
    protected String                  lastFilename   = null;

    /**
     * The results of processing the image received
     */
    protected List<Map<String, ?>>    results     = null;
    /**
     * A Map containing any data that should be passed to the source
     */
    protected Map<String, ?>          otherData   = null;

    /* String containing the base64-encoded image that was just evaluated, assuming that's desired */
    protected String                  encodedImage = null;
    
    public NeuralNetResults() {
        // Do nothing
    }
    /**
     * @param results   The results of processing the image received
     */
    public NeuralNetResults(List<Map<String, ?>> results) {
        this.results = results;
    }
    
    /**
     * @param results   The results of processing the image received
     * @param otherData A Map containing any data that should be passed to the source
     */
    public NeuralNetResults(List<Map<String, ?>> results, Map<String, ?> otherData) {
        this.results = results;
        this.otherData = otherData;
    }

    /**
     * @return The results of processing the image received
     */
    public List<Map<String, ?>> getResults() {
        return results;
    }

    /**
     * @param results   The results of processing the image received
     */
    public void setResults(List<Map<String, ?>> results) {
        this.results = results;
    }

    /**
     * @return A Map containing any data that should be passed to the source
     */
    public Map<String, ?> getOtherData() {
        return otherData;
    }

    /**
     * @param otherData A Map containing any data that should be passed to the source
     */
    public void setOtherData(Map<String, ?> otherData) {
        this.otherData = otherData;
    }
    
    /**
     * @return A Map containing any data that should be passed to the source
     */
    public String getLastFilename() {
        return lastFilename;
    }

    /**
     * @param lastFilename A String containing the name of the last saved image file.  Used in testing
     */
    public void setLastFilename(String lastFilename) {
        this.lastFilename = lastFilename;
    }

    /**
     * @param ei String the base64-encoded image to return
     */
    public void setEncodedImage(String ei) {
        this.encodedImage = ei;
    }

    /**
     * @return String the base64-encoded image to return
     */
    public String getEncodedImage() {
        return encodedImage;
    }
}
