package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class FileRetriever implements ImageRetrieverInterface {

    File defaultImageFile;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSourceConfig.get("fileLocation");
            defaultImageFile = new File(imageLocation);
            if ( !(defaultImageFile.exists() && !defaultImageFile.isDirectory() && defaultImageFile.canRead())) {
                throw new IllegalArgumentException ("Could not read file at '" + defaultImageFile.getAbsolutePath() + "'");
            }
        } else if (dataSourceConfig.get("pollRate") instanceof Integer && 
                        (Integer) dataSourceConfig.get("pollRate") >= 0) { // Won't be using messages to get the file location
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
        } else if (defaultImageFile != null) {
            return getImage();
        } else {
            throw new ImageAcquisitionException("No file specified for acquisition");
        }
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        
    }

}
