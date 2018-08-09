package edu.ml.tensorflow;

public class Main {
//    private final static String IMAGE = "/image/OpenCVImage.jpg";
	private final static String IMAGE = "OpenCVImage.jpg";

    public static void main(String[] args) {
    	FrameCapture frame = new FrameCapture();
    	final byte[] imageByte = frame.capureSnapShot();
    	
        ObjectDetector objectDetector = new ObjectDetector();
//        objectDetector.detect(IMAGE);
        objectDetector.detect(imageByte, IMAGE);
    }
}
