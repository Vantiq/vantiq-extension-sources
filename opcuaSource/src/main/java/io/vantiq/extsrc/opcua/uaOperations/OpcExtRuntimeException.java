package io.vantiq.extsrc.opcua.uaOperations;

/**
 * Indicates an error in the configuration for the extension source
 */

public class OpcExtRuntimeException extends Exception {
    OpcExtRuntimeException() {
        super();
    }

    OpcExtRuntimeException(String msg) {
        super(msg);
    }

    OpcExtRuntimeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
