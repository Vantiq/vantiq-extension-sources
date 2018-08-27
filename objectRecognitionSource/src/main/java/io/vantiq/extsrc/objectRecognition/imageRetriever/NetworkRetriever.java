
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
 * Unique settings are: 
 * <ul>
 *  <li>{@code camera}: Required for Config, optional for Query. The index of the camera to read images from. For queries, defaults to the camera specified in the Config.
 * </ul>
 */
public class NetworkRetriever implements ImageRetrieverInterface {
    VideoCapture   capture;
    String         camera;
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        } catch (Throwable t) {
            throw new Exception(this.getClass().getCanonicalName() + ".opencvDependency" 
                    + ": Could not load OpenCv for CameraRetriever."
                    + "This is most likely due to a missing .dll/.so/.dylib. Please ensure that the environment "
                    + "variable 'OPENCV_LOC' is set to the directory containing 'opencv_java342' and any other library"
                    + "requested by the attached error", t);
        }
        if (dataSourceConfig.get("camera") instanceof String){
            camera = (String) dataSourceConfig.get("camera");
            
            capture = new VideoCapture(camera);
        } else {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".configMissingOptions: "  + 
                    "No camera specified in dataSourceConfig");
        }
        if (!capture.isOpened()) {
            try {
                URL urlProtocolTest = new URL((String) camera);
                InputStream urlReadTest = urlProtocolTest.openStream();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".unknownProtocol: "
                        + "URL specifies unknown protocol");
            } catch (java.io.IOException e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".badRead: "
                        + "URL was unable to be read");
            }
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".notVideoStream: " 
                    + "URL does not represent a video stream");
        }
    }
    
    /**
     * Obtain the most recent image from the camera
     */
    @Override
    public byte[] getImage() throws ImageAcquisitionException {
        // Reading the next video frame from the camera
        Mat matrix = new Mat();

        capture.read(matrix);
        
        if (matrix.empty()) {
            matrix.release();
            if (!capture.isOpened() ) {
                capture.release();
                throw new FatalImageException(this.getClass().getCanonicalName() + ".mainCameraClosed: " 
                        + "Camera '" + camera + "' has closed");
            } else {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraReadError: " 
                        + "Could not obtain frame from camera + '" + camera + "'");
            }
        }
      
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", matrix, matOfByte);
        byte [] imageByte = matOfByte.toArray();
        matOfByte.release();
        matrix.release();
                
        return imageByte;
    }
    
    /**
     * Obtain the most recent image from the specified camera, or the configured camera if no camera is specified
     */
    @Override
    public byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException {
        VideoCapture cap;
        Object camId;
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
        
        cap.read(mat);
        if (mat.empty()) {
            cap.release();
            mat.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraReadError: " 
                    + "Could not obtain frame from camera '" + camId + "'");
        }
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, matOfByte);
        byte [] imageByte = matOfByte.toArray();
        matOfByte.release();
        mat.release();
        cap.release();
                
        return imageByte;
         
    }
    
    public void close() {
        capture.release();
    }
}
