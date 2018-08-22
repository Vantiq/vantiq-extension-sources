package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Map;

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
public class CameraRetriever implements ImageRetrieverInterface {
	VideoCapture capture;
	
	@Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        if (dataSourceConfig.get("camera") instanceof Integer) {
            int camera = (Integer) dataSourceConfig.get("camera");
            nu.pattern.OpenCV.loadShared();
            capture = new VideoCapture(camera);
            if (!capture.isOpened()) {
                throw new Exception("Could not open requested camera");
            }
        } else if (dataSourceConfig.get("camera") instanceof String){
            String camera = (String) dataSourceConfig.get("camera");
            nu.pattern.OpenCV.loadShared();
            capture = new VideoCapture(camera);
            if (!capture.isOpened()) {
                throw new Exception("Could not open requested camera");
            }
        } else {
            throw new IllegalArgumentException("No camera specified in dataSourceConfig");
        }
    }
	
	/**
	 * Obtain the most recent image from the camera
	 */
	@Override
	public byte[] getImage() throws ImageAcquisitionException{
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
    public byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException{
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
