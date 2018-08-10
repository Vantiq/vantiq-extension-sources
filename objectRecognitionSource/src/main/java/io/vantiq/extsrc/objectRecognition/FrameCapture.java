package io.vantiq.extsrc.objectRecognition;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

/**
 * Captures images and returns them as jpeg encoded bytes
 * <br>Not part of original code.
 */
public class FrameCapture {
	Mat matrix = null;
	VideoCapture capture;
	
	public FrameCapture(int camera) {
	    nu.pattern.OpenCV.loadShared();
	    capture = new VideoCapture(camera);
    }
	
	public byte[] captureSnapShot() {
		// Reading the next video frame from the camera
		Mat matrix = new Mat();
		capture.read(matrix);

		// If camera is opened
		if(capture.isOpened()) {
			
			// If there is next video frame	 
			if (capture.read(matrix)) {    
				this.matrix = matrix; 
			}
		}
	  
	    MatOfByte matOfByte = new MatOfByte();
	    Imgcodecs.imencode(".jpg", matrix, matOfByte);
	    byte [] imageByte = matOfByte.toArray();
	    matOfByte.release();
	    	    
	    return imageByte;
	}
	
	public void close() {
	    capture.release();
	}
	
}
