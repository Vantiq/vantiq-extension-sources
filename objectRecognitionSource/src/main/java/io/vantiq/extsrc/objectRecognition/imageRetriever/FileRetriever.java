package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 * This reads files from disk. If it is setup for image files then the file need not be there at initialization, and
 * messages will be sent only if the file is found. For Queries when setup for image files, a new file can be
 * specified in the message with option fileLocation, otherwise the initial file is used. If it is setup for video
 * files, then the file must be there at initialization, and any failed attempts to read will result in the source
 * closing. The options are:
 * 
 * <li>{@code fileLocation}: Required. The location of the file to be read. The file does not need to exist, but
 *              attempts to access a non-existent file will send an empty message in response to Queries and no message
 *              for periodic requests.
 * <li>{@code fileExtension}: Optional. The type of file it is, "mov" for video files, "img" for image files. Defaults
 *              to image files. 
 */
public class FileRetriever implements ImageRetrieverInterface {

    File defaultImageFile;
    VideoCapture capture;
    Boolean isMov = false;
    int time_interval;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("fileExtension") instanceof String) {
            String ext = (String) dataSourceConfig.get("fileExtension");
            if (ext.equals("mov")) {
                isMov = true;
            }
        }
        if (dataSourceConfig.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSourceConfig.get("fileLocation");
            if (isMov) {
                nu.pattern.OpenCV.loadShared();
                capture = new VideoCapture(imageLocation);
                if (!capture.isOpened()) {
                    capture.release();
                    throw new IllegalArgumentException("Intended video could not be opened");
                }
                double fps = capture.get(Videoio.CAP_PROP_FPS);
                time_interval = (int) (3 * Math.round(fps));
            }
            else {
                defaultImageFile = new File(imageLocation);
            }
        } else {
            throw new IllegalArgumentException ("File required but not given");
        }
    }

    @Override
    public byte[] getImage() throws ImageAcquisitionException {
        if (isMov) {
            
            Mat matrix = new Mat();
    
            capture.read(matrix);
            if (matrix.empty()) { // Exit if nothing could be read
                throw new FatalImageException("Video could not be read or video file has finished");
            }
            
            for (int i = 0; i < time_interval; i++) {
                capture.grab();
            }
            
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", matrix, matOfByte);
            byte [] imageByte = matOfByte.toArray();
            matOfByte.release();
            matrix.release();
                    
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
        if (request.get("fileLocation") instanceof String && !isMov) {
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
