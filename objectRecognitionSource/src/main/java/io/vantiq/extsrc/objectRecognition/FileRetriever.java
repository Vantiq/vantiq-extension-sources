package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class FileRetriever implements DataRetrieverInterface {

    File defaultImageFile;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSourceConfig.get("fileLocation");
            defaultImageFile = new File(imageLocation);
            if (defaultImageFile.exists() && !defaultImageFile.isDirectory() && defaultImageFile.canRead()) {
                source.imageFile = defaultImageFile;
            } else {
                throw new IllegalArgumentException ("Could not read file at '" + defaultImageFile.getAbsolutePath() + "'");
            }
        } else if ((int) dataSourceConfig.get("pollRate") >= 0) { // Won't be using messages to get the file location
            throw new IllegalArgumentException ("File required but not given");
        }
    }

    @Override
    public byte[] getImage() {
        try {
            return Files.readAllBytes(defaultImageFile.toPath());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
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
        }
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        
    }

}
