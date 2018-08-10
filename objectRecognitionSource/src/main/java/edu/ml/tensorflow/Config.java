package edu.ml.tensorflow;

/**
 * Configuration file for TensorFlow Java Yolo application
 */
public interface Config {
    String GRAPH_FILE = "/YOLO/yolo.pb";
    String LABEL_FILE = "/YOLO/yolo.txt";

    // Params used for image processing
    int SIZE = 416;
    float MEAN = 255f;

    // Output directory
    String OUTPUT_DIR = "./out";
}
