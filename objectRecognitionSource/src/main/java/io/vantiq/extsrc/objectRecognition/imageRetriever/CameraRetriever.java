
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Date;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

/**
 * Captures images and returns them as jpeg encoded bytes.
 * Unique settings are as follows. Remember to prepend "DS" when using an option in a Query.
 * <ul>
 *      <li>{@code camera}: Required for Config, optional for Query. The index of the camera to read images from. For
 *      Queries, defaults to the camera specified in the Config.
 * </ul>
 * 
 * The timestamp is captured immediately before the image is grabbed from the camera. No other data is included.
 */
public class CameraRetriever implements ImageRetrieverInterface {
    VideoCapture   capture;
    int         camera;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        // Try to load OpenCV
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        } catch (Throwable t) {
            throw new Exception(this.getClass().getCanonicalName() + ".opencvDependency" 
                    + ": Could not load OpenCv for CameraRetriever."
                    + "This is most likely due to a missing .dll/.so/.dylib. Please ensure that the environment "
                    + "variable 'OPENCV_LOC' is set to the directory containing 'opencv_java342' and any other library"
                    + "requested by the attached error", t);
        }
        
        // Specify which camera to read
        if (dataSourceConfig.get("camera") instanceof Integer) {
            camera = (Integer) dataSourceConfig.get("camera");

            capture = new VideoCapture(camera);
        } else {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".configMissingOptions: "  + 
                    "No camera specified in dataSourceConfig");
        }
        
        // Error out if the camera could not be opened
        if (!capture.isOpened()) {
            throw new Exception(this.getClass().getCanonicalName() + ".cameraUnreadable: " 
                    + "Could not open requested camera '" + camera + "'. Common reasons are: "
                    + "The camera is not connected properly; "
                    + "OpenCV is not compiled with the correct codecs to deal with the camera type or is missing a "
                    + "specific .dll/.so/.dylib file (typically FFmpeg); "
                    + "The program is not allowed access to the camera");
        }
    }
    
    /**
     * Obtain the most recent image from the camera
     * @throws FatalImageException     If the camera is no longer open.
     */
    @Override
    public ImageRetrieverResults getImage() throws ImageAcquisitionException {
        // Read the next video frame from the camera
        Mat matrix = new Mat();
        ImageRetrieverResults results = new ImageRetrieverResults();
        Date captureTime = new Date();

        capture.read(matrix);
        
        // Exit if nothing was read
        if (matrix.empty()) {
            matrix.release();
            // If the camera is no longer open, then nothing can be read in the future
            if (!capture.isOpened() ) {
                capture.release();
                throw new FatalImageException(this.getClass().getCanonicalName() + ".mainCameraClosed: " 
                        + "Camera '" + camera + "' has closed");
            } else {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraReadError: " 
                        + "Could not obtain frame from camera '" + camera + "'");
            }
        }
        
        
        MatOfByte matOfByte = new MatOfByte();
        // Translate the image into jpeg, error out if it cannot
        if (!Imgcodecs.imencode(".jpg", matrix, matOfByte)) {
            matOfByte.release();
            matrix.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraConversionError: " 
                    + "Could not convert the frame from camera '" + camera + "' into a jpeg image");
        }
        
        // Save the jpeg image into as a byte array
        byte [] imageByte = matOfByte.toArray();
        matOfByte.release();
        matrix.release();
        
        results.setImage(imageByte);
        results.setTimestamp(captureTime);
        
        return results;
    }
    
    /**
     * Obtain the most recent image from the specified camera, or the configured camera if no camera is specified
     */
    @Override
    public ImageRetrieverResults getImage(Map<String, ?> request) throws ImageAcquisitionException {
        VideoCapture cap;
        Object camId;
        ImageRetrieverResults results = new ImageRetrieverResults();
        Date captureTime;
        
        // Specify which camera to read
        if (request.get("DScamera") instanceof Integer) {
            int cam = (Integer) request.get("DScamera");
            camId = cam;
            cap = new VideoCapture(cam);
        } else if (capture == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noMainCamera: " 
                    + "No camera was requested and no main camera was specified at initialization.");
        } else if (capture.isOpened()){
            // Try to use the main camera if none was specified
            try {
                return getImage();
            } catch (FatalImageException e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraFatalError: " 
                        + "Main camera failed fatally. Other cameras are still Queryable.");
            }
        } else {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraClosed: " 
                    + "No camera was requested and the main camera is no longer open. Most likely this is due to a "
                    + "previous fatal error for the main camera.");
        }
        
        // Error out if the camera could not be opened
        if (!cap.isOpened()) {
            cap.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraUnreadable: " 
                    + "Could not open camera '" + camId + "'");
        }
        Mat mat = new Mat();
        
        captureTime = new Date();
        cap.read(mat);
        
        // Exit if nothing was read
        if (mat.empty()) {
            cap.release();
            mat.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraReadError: " 
                    + "Could not obtain frame from camera '" + camId + "'");
        }
        MatOfByte matOfByte = new MatOfByte();
        
        // Translate the image into jpeg, error out if it cannot
        if (!Imgcodecs.imencode(".jpg", mat, matOfByte)) {
            matOfByte.release();
            mat.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraConversionError: " 
                    + "Could not convert the frame from camera '" + camera + "' into a jpeg image");
        }
        
        // Save the jpeg image into as a byte array
        byte [] imageByte = matOfByte.toArray();
        matOfByte.release();
        mat.release();
        cap.release();
                
        results.setImage(imageByte);
        results.setTimestamp(captureTime);
        
        return results;
    }
    
    public void close() {
        if (capture != null) {
            capture.release();
        }
    }
}
