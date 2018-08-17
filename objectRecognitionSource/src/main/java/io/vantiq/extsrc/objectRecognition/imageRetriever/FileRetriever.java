package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    int frameInterval;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("fileExtension") instanceof String) {
            String ext = (String) dataSourceConfig.get("fileExtension");
            if (ext.equals("mov") || ext.equals("mp4")) {
                isMov = true;
            }
        }
        nu.pattern.OpenCV.loadShared();
        if (dataSourceConfig.get("fileLocation") instanceof String) {
            String imageLocation = (String) dataSourceConfig.get("fileLocation");
            if (isMov) {
                capture = new VideoCapture(imageLocation);
                if (!capture.isOpened()) {
                    capture.release();
                    throw new IllegalArgumentException("Intended video could not be opened");
                }
                
                // Obtain the frame rate of the video, defaulting to 24
                double videoFps = capture.get(Videoio.CAP_PROP_FPS);
                if (videoFps == 0) {
                    videoFps = 24;
                }
                
                // Calculate the number of frames to move each capture
                double fps = 0;
                if (dataSourceConfig.get("fps") instanceof Number) {
                    fps = ((Number) dataSourceConfig.get("fps")).doubleValue();
                }
                if (fps <= 0) {
                    frameInterval = 1;
                }
                else {
                    frameInterval = (int) Math.ceil(videoFps / fps);
                }
                
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
            double val = capture.get(Videoio.CAP_PROP_POS_FRAMES);
            
            capture.read(matrix);
            if (matrix.empty()) { // Exit if nothing could be read
                capture.release();
                matrix.release();
                throw new FatalImageException("Video could not be read or video file has finished");
            }
            
            val += frameInterval;
            capture.set(Videoio.CAP_PROP_POS_FRAMES, val);
            
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
        boolean isMov = false; // Make it local so we don't overwrite the class variable
        if (request.get("fileExtension") instanceof String) {
            String ext = (String) request.get("fileExtension");
            if (ext.equals("mov") || ext.equals("mp4")) {
                isMov = true;
            }
        }
        if (request.get("fileLocation") instanceof String) {
            if (isMov) {
                String imageFile = (String) request.get("fileLocation");
                VideoCapture newcapture = new VideoCapture(imageFile);
                Mat matrix = new Mat();
                
                if (!newcapture.isOpened()) {
                    newcapture.release();
                    matrix.release();
                    throw new ImageAcquisitionException("Intended video could not be opened");
                }
                
                int targetFrame = 0;
                if (request.get("targetFrame") instanceof Number) {
                    targetFrame = ((Number) request.get("targetFrame")).intValue();
                }
                
                // Ensure that targetFrame is inside the bounds of the video
                double frameCount = newcapture.get(Videoio.CAP_PROP_FRAME_COUNT);
                if (frameCount == 0) {
                    newcapture.release();
                    matrix.release();
                    throw new ImageAcquisitionException("Video registers as 0 frames");
                }
                if (targetFrame >= frameCount || targetFrame < 0) {
                    newcapture.release();
                    matrix.release();
                    throw new ImageAcquisitionException("Requested frame outside valid bounds");
                }
                newcapture.set(Videoio.CAP_PROP_POS_FRAMES, targetFrame);
                
                newcapture.read(matrix);
                if (matrix.empty()) { // Exit if nothing could be read
                    newcapture.release();
                    matrix.release();
                    throw new ImageAcquisitionException("Video could not be read");
                }
                
                // Translate the image to jpeg
                MatOfByte matOfByte = new MatOfByte();
                Imgcodecs.imencode(".jpg", matrix, matOfByte);
                byte [] imageByte = matOfByte.toArray();
                matOfByte.release();
                matrix.release();
                newcapture.release();
                        
                return imageByte;
            }
            else {
                File imageFile = new File((String) request.get("fileLocation"));
                try {
                    return Files.readAllBytes(imageFile.toPath());
                } catch (IOException e) {
                    throw new ImageAcquisitionException("Could not read file '" + imageFile.getAbsolutePath() + "'", e);
                }
            }
        } else {
            // Only try use default image if it is an image or a still-open video
            if (capture == null || !capture.isOpened()) {
                try {
                    return getImage();
                } catch (FatalImageException e) {
                    // Fatal Image only thrown when a video is complete
                    // Since the source can read other videos as well, we don't want to fatally end
                    throw new ImageAcquisitionException("Default video no longer readable", e);
                }
            } else {
                throw new ImageAcquisitionException("Default video no longer readable");
            }
        }
    }

    @Override
    public void close() {
    }

}
