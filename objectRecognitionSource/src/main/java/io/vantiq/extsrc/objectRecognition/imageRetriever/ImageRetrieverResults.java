package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Date;
import java.util.Map;

/**
 * A class used to transfer the image, timestamp, and miscellaneous data from image retrievers. All constructors and
 * setters are protected so that only neural nets can create and change these objects.
 */
public class ImageRetrieverResults {

    /**
     * The image captured. Should be in jpeg format.
     */
    protected byte[]            image       = null;
    /**
     * The time at which the image was captured. This is not required, but strongly advised for any image retrievers
     * that are live capture.
     */
    protected Date              timestamp   = null;
    /**
     * A Map containing any data that should be passed to the source
     */
    protected Map<String, ?>    otherData   = null;
    
    protected ImageRetrieverResults() {
        // Do nothing
    }
    
    /**
     * @param image     The image captured
     */
    protected ImageRetrieverResults(byte[] image) {
        this.image = image;
    }
    
    /**
     * @param image     The image captured
     * @param timestamp The time at which the image was captured.
     * @param otherData A Map containing any data that should be passed to the source
     */
    protected ImageRetrieverResults(byte[] image, Date timestamp, Map<String, ?> otherData) {
        this.image = image;
        this.timestamp = timestamp;
        this.otherData = otherData;
    }

    /**
     * @return The image captured
     */
    public byte[] getImage() {
        return image;
    }

    /**
     * @param image   The image captured
     */
    protected void setImage(byte[] image) {
        this.image = image;
    }
    
    /**
     * @return The time at which the image was captured.
     */
    public Date getTimestamp() {
        return timestamp;
    }
    
    /**
     * @param timestamp The time at which the image was captured.
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
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
