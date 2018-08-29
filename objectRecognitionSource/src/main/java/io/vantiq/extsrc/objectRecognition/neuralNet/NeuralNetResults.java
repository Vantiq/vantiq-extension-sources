package io.vantiq.extsrc.objectRecognition.neuralNet;

import java.util.List;
import java.util.Map;

/**
 * A class used to transfer the results and miscellaneous data from neural nets. All constructors and setters are
 * protected so that only neural nets can create and change these objects.
 */
public class NeuralNetResults {

    /**
     * The results of processing the image received
     */
    protected List<Map<String, ?>>    results     = null;
    /**
     * A Map containing any data that should be passed to the source
     */
    protected Map<String, ?>          otherData   = null;
    
    protected NeuralNetResults() {
        // Do nothing
    }
    /**
     * @param results   The results of processing the image received
     */
    protected NeuralNetResults(List<Map<String, ?>> results) {
        this.results = results;
    }
    
    /**
     * @param results   The results of processing the image received
     * @param otherData A Map containing any data that should be passed to the source
     */
    protected NeuralNetResults(List<Map<String, ?>> results, Map<String, ?> otherData) {
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
    protected void setResults(List<Map<String, ?>> results) {
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
    protected void setOtherData(Map<String, ?> otherData) {
        this.otherData = otherData;
    }
    
}
