package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;


public class FileRetriever implements ImageRetrieverInterface {

    File defaultImageFile;
    VideoCapture capture;
    Boolean isMov = false;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSourceConfig.get("fileLocation");
            String fileExtension = (String) dataSourceConfig.get("fileExtension");
            if (fileExtension.equals("mov")) {
                isMov = true;
                nu.pattern.OpenCV.loadShared();
                capture = new VideoCapture(imageLocation);
            }
            else {
                defaultImageFile = new File(imageLocation);
                if ( !(defaultImageFile.exists() && !defaultImageFile.isDirectory() && defaultImageFile.canRead())) {
                    throw new IllegalArgumentException ("Could not read file at '" + defaultImageFile.getAbsolutePath() + "'");
                }
            }
        } else if (dataSourceConfig.get("pollRate") instanceof Integer && 
                        (Integer) dataSourceConfig.get("pollRate") >= 0) { // Won't be using messages to get the file location
        } else {
            throw new IllegalArgumentException ("File required but not given");
        }
    }

    @Override
    public byte[] getImage() throws ImageAcquisitionException {
        if (isMov) {
            Mat matrix = new Mat();
    
            capture.read(matrix);
          
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", matrix, matOfByte);
            byte [] imageByte = matOfByte.toArray();
            matOfByte.release();
                    
            return imageByte;
        }
        
        else {
            try {
                return Files.readAllBytes(defaultImageFile.toPath());
            } catch (IOException e) {
                throw new ImageAcquisitionException("Could not read the given file");
            }
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
