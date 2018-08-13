package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Map;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;

/**
 * Captures images and returns them as jpeg encoded bytes
 * <br>Not part of original code.
 */
public class CameraRetriever implements ImageRetrieverInterface {
	VideoCapture capture;
	
	@Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
	    int camera;
        if (dataSourceConfig.get("camera") instanceof Integer) {
            camera = (Integer) dataSourceConfig.get("camera");
        } else {
            throw new IllegalArgumentException("No camera specified in dataSourceConfig");
        }
	    nu.pattern.OpenCV.loadShared();
        capture = new VideoCapture(camera);
    }
	
	@Override
	public byte[] getImage() {
		// Reading the next video frame from the camera
		Mat matrix = new Mat();

		capture.read(matrix);
	  
	    MatOfByte matOfByte = new MatOfByte();
	    Imgcodecs.imencode(".jpg", matrix, matOfByte);
	    byte [] imageByte = matOfByte.toArray();
	    matOfByte.release();
	    	    
	    return imageByte;
	}
	
	@Override
    public byte[] getImage(Map<String, ?> request) {
        return getImage();
    }
	
	public void close() {
	    capture.release();
	}

    
}
