package io.vantiq.extsrc.opcua.uaOperations;

public class OpcExtKeyStoreException extends Exception {

    OpcExtKeyStoreException() {
        super();
    }

    OpcExtKeyStoreException(String msg) {
        super(msg);
    }

    OpcExtKeyStoreException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
