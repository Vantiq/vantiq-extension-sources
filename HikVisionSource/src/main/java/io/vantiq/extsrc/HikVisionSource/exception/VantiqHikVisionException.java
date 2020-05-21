package io.vantiq.extsrc.HikVisionSource.exception;


/**
 * A custom exception used to extract the useful information from a SQLException
 */
public class VantiqHikVisionException extends Exception {
    
    public VantiqHikVisionException() {
        super();
    }

    public VantiqHikVisionException(String message) {
        super(message);
    }
    
    public VantiqHikVisionException(String message, Throwable cause) {
        super(message, cause);
    }
}