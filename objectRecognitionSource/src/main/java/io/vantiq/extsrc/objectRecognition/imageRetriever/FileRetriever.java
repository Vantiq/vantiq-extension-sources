
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;

/**
 * This implementation reads files from the disk, using OpenCV for the videos. {@code fileLocation} must be a valid file
 * at initialization if specified for a video. The initial image file can be replaced while the source is running, but the video cannot. For
 * Queries, new files can be specified using the {@code fileLocation} and {@code fileExtension} options, and defaults
 * to the initial file if {@code fileLocation} is not set. Queried videos can specify which frame of the video to access
 * using the {@code targetFrame} option.
 * <br>
 * Errors are thrown whenever an image or video frame cannot be read. Fatal errors are thrown only when a video finishes
 * being read when the source is not setup for to receive Queries.
 * <br>
 * The options are as follows. Remember to prepend "DS" when using an option in a Query.
 * <ul>
 *     <li>{@code fileLocation}: Optional. Config and  Query. The location of the file to be read.
 *                      For Config where {@code fileExtension} is "mov", the file must exist at initialization. If this
 *                      option is not set at Config and the source is configured to poll, then the source will open but
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
 * 
 * No timestamp is captured. The additional data is:
 * <ul>
 *      <li>{@code file}: The path of the file read.
 *      <li>{@code frame}: Which frame of the file this represents. Only included when `fileExtension` is set to "mov".
 * </ul>
 */
public class FileRetriever extends RetrieverBase implements ImageRetrieverInterface {

    Boolean isMov = false;
    int frameInterval;
    int currentFrameNumber = 0;

    // Constants for source configuration
    private static final String FILE_EXTENSION = "fileExtension";
    private static final String FILE_LOCATION = "fileLocation";
    private static final String FPS = "fps";

    @Override
    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.AvoidDeeplyNestedIfStmts"})
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        // Check if the file is a video
        if (dataSourceConfig.get(FILE_EXTENSION) instanceof String) {
            String ext = (String) dataSourceConfig.get(FILE_EXTENSION);
            if (ext.equals("mov") || ext.equals("mp4")) {
                isMov = true;
            }
        }

        // Save the initial file location
        if (dataSourceConfig.get(FILE_LOCATION) instanceof String) {
            cameraOrFile = (String) dataSourceConfig.get(FILE_LOCATION);
            // Setup OpenCV to read the video if the file is a video
            if (isMov) {
                // Open the requested file
                capture = new FFmpegFrameGrabber(cameraOrFile);
                try {
                    capture.start();
                } catch (Exception e) {
                    capture.release();
                    if (!new File(cameraOrFile).exists()) {
                        throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainVideoDoesNotExist: "
                                + "The requested video '" + cameraOrFile + "' does not exist");
                    }
                    throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".invalidMainVideo: "
                            + "Intended video '" + cameraOrFile + "' could not be opened. Most likely OpenCV is not "
                            + "compiled with the codecs required to read this video type");
                }

                // Obtain the frame rate of the video, defaulting to 24
                double videoFps = capture.getFrameRate();
                if (videoFps == 0) {
                    videoFps = 24;
                }
                
                // Calculate the number of frames to move each capture
                double fps = 0;
                if (dataSourceConfig.get(FPS) instanceof Number) {
                    fps = ((Number) dataSourceConfig.get(FPS)).doubleValue();
                }
                if (fps <= 0) {
                    frameInterval = 1;
                } else {
                    frameInterval = (int) Math.ceil(videoFps / fps);
                }
            }
        }
    }

    /**
     * Read the file specified at configuration
     * @throws FatalImageException  If no file was specified at configuration, or if the video has completed
     */
    @Override
    @SuppressWarnings({"PMD.CognitiveComplexity"})
    public ImageRetrieverResults getImage() throws ImageAcquisitionException {
        ImageRetrieverResults results = new ImageRetrieverResults();
        Map<String, Object> otherData = new LinkedHashMap<>();
        
        results.setOtherData(otherData);
        otherData.put("file", cameraOrFile);
        if (capture != null) {
            otherData.put("videoFrameCount", capture.getLengthInVideoFrames());
        }
        
        if (isMov) {
            Mat matrix = new Mat();
            // Save the current position for later.
            log.debug("Reading frame number {}", currentFrameNumber);

            matrix = grabFrameAsMat();
            // Exit if nothing could be read
            if (matrix.empty()) {
                close();
                throw new FatalImageException(this.getClass().getCanonicalName() + ".defaultVideoReadError: " 
                         + "Default video could not be read. Most likely the video completed reading.");
            }

            // Translate the image to jpeg
            byte[] imageBytes = convertMatToJpeg(matrix);
            if (imageBytes == null) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".videoConversionError: " 
                        + "Could not convert frame #" + currentFrameNumber + " from video '" + cameraOrFile
                        + "' into a jpeg image");
            }

            // Move forward by the number of frames specified in the Configuration
            currentFrameNumber += frameInterval;
            try {
                log.debug("Setting next frame number to {}", currentFrameNumber);
                capture.setVideoFrameNumber(currentFrameNumber);
            } catch (FFmpegFrameGrabber.Exception e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".videoSeekError: "
                        + "Could not advance to frame #" + currentFrameNumber + " from video '" + cameraOrFile
                        + "'.", e);
            }
            otherData.put("frame", currentFrameNumber);
            results.setImage(imageBytes);
                    
            return results;
        } else if (cameraOrFile != null) {
            // Read the expected image
            otherData.put("file", cameraOrFile);
            Mat image = imread(cameraOrFile);

            if (image.empty()) {
                image.release();

                if (!new File(cameraOrFile).exists()) {
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".defaultImageDoesNotExist: "
                            + "The default image does not exist");
                }
                
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".defaultImageUnreadable: " 
                        + "Could not read requested file '" + cameraOrFile + "'. "
                        + "Most likely the image was in an unreadable format");
            }
            
            byte[] jpegImage = convertMatToJpeg(image);
            if (jpegImage == null) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".imageConversionError: " 
                        + "Could not convert file '" + cameraOrFile + "' into a jpeg image");
            }
            
            results.setImage(jpegImage);
            return results;
        } else {
            throw new FatalImageException(this.getClass().getCanonicalName() + ".noDefaultFile: " 
                    + "No default file found. Most likely none was specified in the configuration.");
        }
    }

    /**
     * Read the specified file, or the file specified at configuration
     */
    @Override
    @SuppressWarnings({"PMD.CognitiveComplexity"})
    public ImageRetrieverResults getImage(Map<String, ?> request) throws ImageAcquisitionException {
        ImageRetrieverResults results = new ImageRetrieverResults();
        Map<String, Object> otherData = new LinkedHashMap<>();
        
        results.setOtherData(otherData);
        
        boolean isMov = false; // Make it local so we don't overwrite the class variable
        
        // Check if the file is expected to be a video
        if (request.get("DSfileExtension") instanceof String) {
            String ext = (String) request.get("DSfileExtension");
            if (ext.equals("mov") || ext.equals("mp4")) {
                isMov = true;
            }
        }
        
        // Read in the file specified, or try the default if no file is specified
        if (request.get("DSfileLocation") instanceof String) {
            if (isMov) {
                String imageFile = (String) request.get("DSfileLocation");
                otherData.put("file", imageFile);
                
                FFmpegFrameGrabber newcapture = new FFmpegFrameGrabber(imageFile);
                try {
                    newcapture.start();
                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!new File(imageFile).exists()) {
                        throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryImageDoesNotExist: "
                                + "The requested image '" + imageFile + "' does not exist", e);
                    }
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryImageUnreadable: "
                            + "Could not read requested file '" + imageFile + "'. "
                            + "Most likely the image was in an unreadable format", e);
                }
                Mat matrix = new Mat();
                
                // Calculate the specified frame
                int targetFrame = 1;
                if (request.get("DStargetFrame") instanceof Number) {
                    targetFrame = ((Number) request.get("DStargetFrame")).intValue();
                } else if (request.get("DStargetTime") instanceof Number) {
                    double fps = newcapture.getFrameRate();
                    if (fps == 0) {
                        fps = 24;
                    }
                    targetFrame = (int) (fps * ((Number)request.get("DStargetTime")).doubleValue());
                }
                
                // Ensure that targetFrame is inside the bounds of the video, and that we know how many frames are
                // in the video
                int frameCount = newcapture.getLengthInVideoFrames();
                if (frameCount == 0) {
                    try {
                        newcapture.release();
                    } catch (FFmpegFrameGrabber.Exception e) {
                        otherData.put("releaseException", e);
                        // Otherwise, ignore
                    }
                    matrix.release();
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".videoPropertyError: " 
                            + "Video '" + imageFile + "' registers as 0 frames long");
                } else if (targetFrame >= frameCount || targetFrame < 0) {
                    try {
                        newcapture.release();
                    } catch (FFmpegFrameGrabber.Exception e) {
                        otherData.put("releaseException", e);
                    }
                    matrix.release();
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".invalidTargetFrame: " 
                            + "Requested frame " + targetFrame + " outside valid bounds (0," + frameCount + ") for "
                            + "video '"+ imageFile + "'");
                }
                try {
                    newcapture.setVideoFrameNumber(targetFrame);
                } catch (FFmpegFrameGrabber.Exception e) {
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".videoSeekError2: "
                            + "Could not advance to frame #" + currentFrameNumber + " from video '" + imageFile
                            + "'.", e);
                }

                matrix = grabFrameAsMat(newcapture);
                // Since we're going to release the stream, we need to clone the matrix.
                // Otherwise, things go badly awry.
                matrix = matrix.clone();
                try {
                    newcapture.release();
                } catch (FFmpegFrameGrabber.Exception e) {
                    otherData.put("releaseException", e);
                    // Otherwise, ignore
                }
                // Exit if nothing could be read
                if (matrix.empty()) {
                    matrix.release();
                    
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".videoUnreadable: " 
                            + "Video '" + imageFile + "' could not be read");
                }
                
                // Translate the image to jpeg
                byte[] imageBytes = convertMatToJpeg(matrix);
                if (imageBytes == null) {
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryVideoConversionError: " 
                            + "Could not convert frame #" + targetFrame + " from video '" + imageFile
                            + "' into a jpeg image");
                }
                        
                otherData.put("frame", targetFrame);
                results.setImage(imageBytes);
                        
                return results;
            } else {
                // Read the expected image
                String imageFile = (String) request.get("DSfileLocation");
                otherData.put("file", imageFile);
                Mat image = imread(imageFile);

                if (image == null || image.empty()) {
                    if (image != null) {
                        image.release();
                    }
                    
                    if (!new File(imageFile).exists()) {
                        throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryImageDoesNotExist: "
                                + "The requested image '" + imageFile + "' does not exist");
                    }
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryImageUnreadable: " 
                            + "Could not read requested file '" + imageFile + "'. "
                            + "Most likely the image was in an unreadable format");
                }
                
                byte[] jpegImage = convertMatToJpeg(image);
                if (jpegImage == null) {
                    throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryImageConversionError: " 
                            + "Could not convert file '" + cameraOrFile + "' into a jpeg image");
                }
                
                results.setImage(jpegImage);
                return results;
            }
        } else {
            // Only try to use default if it is set
            if (isMov || cameraOrFile != null) {
                try {
                    return getImage();
                } catch (FatalImageException e) {
                    // Fatal Image only thrown when a video is complete
                    // Since the source can read other videos as well, we don't want to fatally end
                    throw new ImageAcquisitionException(e.getMessage(), e);
                }
            } else {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noDefaultFile: " 
                        + "No default file available. Most likely the default video has been completed");
            }
        }
    }

    protected Mat grabFrameAsMat() throws ImageAcquisitionException {
        return grabFrameAsMat(capture);
    }

    protected Mat grabFrameAsMat(FFmpegFrameGrabber cap) throws ImageAcquisitionException {
        try {
            Frame frame = null;
            int tryCount = 0;
            while (frame == null && tryCount < 100) {
                frame = cap.grabImage();
                tryCount += 1;
                if (frame != null) {
                    if (!frame.getTypes().contains(Frame.Type.VIDEO)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Found non-video frame: {}", frame.getTypes());
                        }
                        continue;
                    }
                }
            }

            OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
            return converterToMat.convertToMat(frame);
        } catch (Exception e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".badImageGrab: "
                    + "Could not obtain frame from file '" + cameraOrFile + "': " + e, e);
        }
    }

    @Override
    public void close() {
        if (capture != null) {
            try {
                capture.release();
                capture.close();
            } catch (FrameGrabber.Exception e) {
                log.warn("Error encountered releasing capture: ", e);
            }
        }
    }
}
