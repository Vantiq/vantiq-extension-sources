package edu.ml.tensorflow;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

public class FrameCapture {
	Mat matrix = null;
	
	// Create image name
	public static final String file = 
			"/Users/namirfawaz/Documents/Everything_Else/tensorflow-example-java/build/resources/main/image/OpenCVImage.jpg";
	
	public byte[] capureSnapShot() {
		// Loading the OpenCV core library
		nu.pattern.OpenCV.loadShared();
		
		// Instantiating the VideoCapture class (Camera 0 == Webcam)
		VideoCapture capture = new VideoCapture(0);
	
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
	
}
