
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class CameraRetriever implements ImageRetrieverInterface {
	VideoCapture capture;
	
	static {
        String libPath = System.getProperty("LD_LIBRARY_PATH", "");
        String opencvLoc = System.getenv("OPENCV_LOC");
        if (opencvLoc == null) {
            Logger log = LoggerFactory.getLogger(CameraRetriever.class);
            log.error("Could not find environment variable 'OPENCV_LOC'. Unless the location of opencv_java342.dll/.so "
                    + "is already added to java.library.path, CameraRetriever will be unable to work.");
            log.debug("java.library.path is currently '{}'", libPath);
            opencvLoc = "";
        }
        System.setProperty("LD_LIBRARY_PATH", libPath + opencvLoc);
    }
	
	@Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
	    try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        } catch (Throwable t) {
            throw new Exception(this.getClass().getCanonicalName() + ".opencvDependency" 
                    + ": Could not load OpenCv for CameraRetriever."
                    + "This is most likely due to a missing .dll/.so", t);
        }
        if (dataSourceConfig.get("camera") instanceof Integer) {
            int camera = (Integer) dataSourceConfig.get("camera");

            capture = new VideoCapture(camera);
            if (!capture.isOpened()) {
                throw new Exception("Could not open requested camera #");
            }
        } else if (dataSourceConfig.get("camera") instanceof String){
            String camera = (String) dataSourceConfig.get("camera");
            capture = new VideoCapture(camera);
            if (!capture.isOpened()) {
                throw new Exception("Could not open requested camera");
            }
        } else {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".opencvDependency"  + 
                    "No camera specified in dataSourceConfig");
        }
        if (!capture.isOpened()) {
            throw new Exception("Could not open requested camera");
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
		        throw new FatalImageException("Camera has closed");
		    } else {
		        throw new ImageAcquisitionException("Could not obtain frame from camera");
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
	    if (request.get("DScamera") instanceof Integer) {
	        int cam = (Integer) request.get("DScamera");
	        VideoCapture cap = new VideoCapture(cam);
	        if (!cap.isOpened()) {
	            cap.release();
	            throw new ImageAcquisitionException("Could not open requested camera");
	        }
	        Mat mat = new Mat();
	        
	        cap.read(mat);
	        if (mat.empty()) {
	            cap.release();
	            mat.release();
	            throw new ImageAcquisitionException("Could not obtain frame from camera");
	        }
	        MatOfByte matOfByte = new MatOfByte();
	        Imgcodecs.imencode(".jpg", mat, matOfByte);
	        byte [] imageByte = matOfByte.toArray();
	        matOfByte.release();
	        mat.release();
	        cap.release();
	                
	        return imageByte;
	    } else {
	        try {
	            return getImage();
	        } catch (FatalImageException e) {
	            throw new ImageAcquisitionException("Default camera failed fatally. Non-defaults still available.");
	        }
	    }
    }
	
	public void close() {
	    capture.release();
	}
}
