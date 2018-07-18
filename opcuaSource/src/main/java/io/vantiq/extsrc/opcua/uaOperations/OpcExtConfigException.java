package io.vantiq.extsrc.opcua.uaOperations;

import java.lang.Exception;

/**
 * Indicates an error in the configuration for the extension source
 */

public class OpcExtConfigException extends Exception {
    OpcExtConfigException() {
        super();
    }

    OpcExtConfigException(String msg) {
        super(msg);
    }

    OpcExtConfigException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
