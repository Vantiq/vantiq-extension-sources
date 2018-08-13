package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

/**
 * Reads files from the disk.
 * Unique settings are: 
 * <ul>
 *  <li>{@code fileLocation}: Required. The name of the file to be read. If the source is setup to perform messages on
 *                  Query, then this serves as the default file if no file is specified in the query.
 * </ul>
 */
public class FileRetriever implements ImageRetrieverInterface {

    File defaultImageFile;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSourceConfig.get("fileLocation");
            defaultImageFile = new File(imageLocation);
        } else {
            throw new IllegalArgumentException ("File required but not given");
        }
    }

    @Override
    public byte[] getImage() throws ImageAcquisitionException {
        try {
            return Files.readAllBytes(defaultImageFile.toPath());
        } catch (IOException e) {
            throw new ImageAcquisitionException("Could not read the given file");
        }
    }

    @Override
    public byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException {
        if (request.get("fileLocation") instanceof String) {
            File imageFile = new File((String) request.get("fileLocation"));
            try {
                return Files.readAllBytes(imageFile.toPath());
            } catch (IOException e) {
                throw new ImageAcquisitionException("Could not read file '" + imageFile.getAbsolutePath() + "'", e);
            }
        } else {
            return getImage();
        }
    }

    @Override
    public void close() {
    }

}
