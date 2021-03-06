package edu.ml.tensorflow.util;

/**
 * ServiceException to throw in case if something wrong happen in the service layer.
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceException() {
        super();
    }

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
