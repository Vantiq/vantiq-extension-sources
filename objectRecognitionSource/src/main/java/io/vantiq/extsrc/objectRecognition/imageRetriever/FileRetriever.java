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
 * This implementation reads files from the disk using OpenCV for the videos. {@code fileLocation} must be a valid file
 * at initialization. The initial image file can be replaced while the source is running, but the video cannot. For
 * Queries, new files can be specified using the {@code fileLocation} and {@code fileExtension} options, and defaults
 * to the initial file if {@code fileLocation} is not set. Queried videos can specify which frame of the video to access
 * using the {@code targetFrame} option.
 * <br>
 * Errors are thrown whenever an image or video frame cannot be read. Fatal errors are thrown only when a video finishes
 * being read when the source is not setup for to receive Queries.
 * <br>
 * The options are:
 * <ul>
 *     <li>{@code fileLocation}: Required for Config, Optional for Query. The location of the file to be read.
 *                      For Config where {@code fileExtension} is "mov", the file must exist at initialization. If this
 *                      is not set at Config and the source is not configured for Queries, then the source will open but
 *                      the first attempt to retrieve will kill the source. For Queries, defaults to the configured file
 *                      or returns an error if there was none.
 *     <li>{@code fileExtension}: Optional. Config and Query. The type of file it is, "mov" for video files, "img" for
 *                      image files. Defaults to image files.
 *     <li>{@code fps}: Optional. Config only. Requires {@code fileExtension} be "mov". How many frames to retrieve for
 *                      every second in the video. Rounds up the result when calculating the number of frames to move
 *                      each capture. Non-positive numbers revert to default. Default is every frame.
 *     <li>{@code targetFrame}: Optional. Query only. Requires {@code fileExtension} be "mov". The frame in the video
 *                      that you would like to access, with the first being 0. Exceptions will be thrown if this targets
 *                      an invalid frame, i.e. negative or beyond the video's frame count. Mutually exclusive with
 *                      {@code targetTime}. Defaults to 0.
 *     <li>{@code targetTime}: Optional. Query only. Requires {@code fileExtension} be "mov". The second in the video
 *                      that you would like to access, with the first frame being 0. Exceptions will be thrown if this
 *                      targets an invalid frame, i.e. negative or beyond the video's frame count. Non-integer values 
 *                      are allowed. Mutually exclusive with {@code targetFrame}. Defaults to 0.
 * </ul>
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
        else if (defaultImageFile != null){
            try {
                return Files.readAllBytes(defaultImageFile.toPath());
            } catch (IOException e) {
                throw new ImageAcquisitionException("Could not read the given file");
            }
        } else {
            throw new FatalImageException("No file found");
        }
    }

    @Override
    public byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException {
        boolean isMov = false; // Make it local so we don't overwrite the class variable
        if (request.get("DSfileExtension") instanceof String) {
            String ext = (String) request.get("DSfileExtension");
            if (ext.equals("mov") || ext.equals("mp4")) {
                isMov = true;
            }
        }
        if (request.get("DSfileLocation") instanceof String) {
            if (isMov) {
                String imageFile = (String) request.get("DSfileLocation");
                VideoCapture newcapture = new VideoCapture(imageFile);
                Mat matrix = new Mat();
                
                if (!newcapture.isOpened()) {
                    newcapture.release();
                    matrix.release();
                    throw new ImageAcquisitionException("Intended video could not be opened");
                }
                
                int targetFrame = 0;
                if (request.get("DStargetFrame") instanceof Number) {
                    targetFrame = ((Number) request.get("DStargetFrame")).intValue();
                } else if (request.get("DStargetTime") instanceof Number) {
                    double fps = newcapture.get(Videoio.CAP_PROP_FPS);
                    if (fps != 0) {
                        targetFrame = (int) (fps * ((Number)request.get("DStargetTime")).doubleValue());
                    }
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
                File imageFile = new File((String) request.get("DSfileLocation"));
                try {
                    return Files.readAllBytes(imageFile.toPath());
                } catch (IOException e) {
                    throw new ImageAcquisitionException("Could not read file '" + imageFile.getAbsolutePath() + "'", e);
                }
            }
        } else {
            // Only try to use default if it is set
            if ((isMov && capture.isOpened()) || defaultImageFile != null) {
                try {
                    return getImage();
                } catch (FatalImageException e) {
                    // Fatal Image only thrown when a video is complete
                    // Since the source can read other videos as well, we don't want to fatally end
                    throw new ImageAcquisitionException("Default video no longer readable", e);
                }
            } else {
                throw new ImageAcquisitionException("No default available");
            }
        }
    }

    @Override
    public void close() {
    }

}