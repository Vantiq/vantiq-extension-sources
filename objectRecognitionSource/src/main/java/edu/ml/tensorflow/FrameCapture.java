package edu.ml.tensorflow;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

public class FrameCapture {
	Mat matrix = null;
	VideoCapture capture;
	
	public FrameCapture(int camera) {
	    nu.pattern.OpenCV.loadShared();
	    capture = new VideoCapture(camera);
    }
	
	public byte[] capureSnapShot() {
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
	  
		// Release camera
	    capture.release();
	    
	    MatOfByte matOfByte = new MatOfByte();
	    Imgcodecs.imencode(".jpg", matrix, matOfByte);
	    byte [] imageByte = matOfByte.toArray();
	    	    
	    return imageByte;
	}
	
	public void close() {
	    capture.release();
	}
	
}
