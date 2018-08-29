package edu.ml.tensorflow;

/**
 * Configuration file for TensorFlow Java Yolo application
 */
public interface Config {
    // Params used for image processing
    public int SIZE = 416; // Edited so that it can be accessed from anywhere
    float MEAN = 255f;
}
