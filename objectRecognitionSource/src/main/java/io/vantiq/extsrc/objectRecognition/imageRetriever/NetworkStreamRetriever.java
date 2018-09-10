
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

/**
 * Captures images from an IP camera.
 * Unique settings are as follows. Remember to prepend "DS" when using an option in a Query. 
 * <ul>
 *      <li>{@code camera}: Required for Config, optional for Query. The URL of the video stream to read images from. For
 *                      queries, defaults to the camera specified in the Config.
 * </ul>
 * 
 * The timestamp is captured immediately before the image is grabbed from the camera. The additional data is:
 * <ul>
 *      <li>{@code camera}: The URL of the camera that the image was read from.
 * </ul>
 */
public class NetworkStreamRetriever implements ImageRetrieverInterface {
    VideoCapture   capture;
    String         camera;
    Logger         log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        } catch (Throwable t) {
            throw new Exception(this.getClass().getCanonicalName() + ".opencvDependency" 
                    + ": Could not load OpenCv for CameraRetriever."
                    + "This is most likely due to a missing .dll/.so/.dylib. Please ensure that the environment "
                    + "variable 'OPENCV_LOC' is set to the directory containing '" + Core.NATIVE_LIBRARY_NAME
                    + "' and any other library requested by the attached error", t);
        }
        if (dataSourceConfig.get("camera") instanceof String){
            camera = (String) dataSourceConfig.get("camera");
            
            capture = new VideoCapture(camera);
        } else {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".configMissingOptions: "  + 
                    "No camera specified in dataSourceConfig");
        }
        if (!capture.isOpened()) {
            diagnoseConnection();
//            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".notVideoStream: " 
//                    + "URL does not represent a video stream");
        }
    }
    
    /**
     * Obtain the most recent image from the camera
     * @throws FatalImageException  If the IP camera is no longer open
     */
    @Override
    public ImageRetrieverResults getImage() throws ImageAcquisitionException {
        // Reading the next video frame from the camera
        Mat matrix = new Mat();
        ImageRetrieverResults results = new ImageRetrieverResults();
        Date captureTime = new Date();

        capture.read(matrix);
        
        if (matrix.empty()) {
            matrix.release();
            // Check connection to URL first
            diagnoseConnection();
            
            // Try to recreate video capture once connection is reestablished 
            capture = new VideoCapture(camera);
            
            // Otherwise, check to see if camera has closed
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
        
        if (request.get("DScamera") instanceof String) {
            String cam = (String) request.get("DScamera");
            camId = cam;
            cap = new VideoCapture(cam);
        } else if (capture == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noMainCamera: " 
                    + "No camera was requested and no main camera was specified at initialization.");
        } else if (capture.isOpened()){
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
        
        if (!cap.isOpened()) {
            cap.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraUnreadable: " 
                    + "Could not open camera '" + camId + "'");
        }
        Mat mat = new Mat();
        
        captureTime = new Date();
        cap.read(mat);
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
        byte [] imageByte = matOfByte.toArray();
        matOfByte.release();
        mat.release();
        cap.release();
        
        results.setImage(imageByte);
        results.setTimestamp(captureTime);
        return results;
    }
    
    public void diagnoseConnection() throws ImageAcquisitionException{
        try {
            URL urlProtocolTest = new URL((String) camera);
            InputStream urlReadTest = urlProtocolTest.openStream();
        } catch (MalformedURLException e) {
//            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".unknownProtocol: "
//                    + "URL specifies unknown protocol");
            log.error(".unknownProtocol: Error parsing URL: ", e);
            
        } catch (java.io.IOException e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".badRead: "
                    + "URL was unable to be read");
        }
    }
    
    public void close() {
        if (capture != null) {
            capture.release();
        }
    }
}
