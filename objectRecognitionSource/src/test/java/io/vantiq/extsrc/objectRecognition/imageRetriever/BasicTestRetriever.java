package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class BasicTestRetriever implements ImageRetrieverInterface {

    public Map<String,?>            config;
    public ObjectRecognitionCore    source;
    public static final String      THROW_EXCEPTION         = "throwException";
    public static final String      THROW_EXCEPTION_ON_REQ  = "throwReqException";
    public static final String      THROW_FATAL_ON_REQ      = "throwReqFatal";
    public final static String      THROW_RUNTIME_ON_REQ    = "throwRuntimeException";
    public static final String      RETURN_NULL             = "retNull";
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        config = dataSourceConfig;
        this.source = source;
        if (config.containsKey(THROW_EXCEPTION)) {
            throw new Exception("Exception requested");
        }
    }

    @Override
    public byte[] getImage() throws ImageAcquisitionException {
        if (config.containsKey(THROW_EXCEPTION_ON_REQ)) {
            throw new ImageAcquisitionException("Exception on request");
        } else if (config.containsKey(RETURN_NULL)) {
            return null;
        } else if (config.containsKey(THROW_FATAL_ON_REQ)) {
            throw new FatalImageException("Exception on request");
        } else if (config.containsKey(THROW_RUNTIME_ON_REQ)) {
            throw new RuntimeException("Exception on request");
        } else {
            try {
                return Files.readAllBytes(new File(ObjRecTestBase.IMAGE_LOCATION).toPath());
            } catch (IOException e) {
                throw new FatalImageException("Couldn't find the image location");
            }
        }
    }

    @Override
    public byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException {
        if (request.containsKey(THROW_EXCEPTION_ON_REQ)) {
            throw new ImageAcquisitionException("Exception on request");
        } else if (request.containsKey(RETURN_NULL)) {
            return null;
        } else if (request.containsKey(THROW_FATAL_ON_REQ)) {
            throw new FatalImageException("Exception on request");
        } else {
            try {
                return Files.readAllBytes(new File(ObjRecTestBase.IMAGE_LOCATION).toPath());
            } catch (IOException e) {
                throw new FatalImageException("Couldn't find the image location");
            }
        }
    }

    @Override
    public void close() {}

}
