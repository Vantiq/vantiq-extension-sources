
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Date;
import java.util.Map;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

/**
 * Captures images and returns them as jpeg encoded bytes.
 * Unique settings are as follows. Remember to prepend "DS" when using an option in a Query.
 * <ul>
 *      <li>{@code camera}: Required for Config, optional for Query. The index of the camera to read images from. For
 *      Queries, defaults to the camera specified in the Config.
 * </ul>
 * 
 * The timestamp is captured immediately before the image is grabbed from the camera. No other data is included.
 */
public class CameraRetriever extends RetrieverBase implements ImageRetrieverInterface {
    int camera;
    String sourceName;
    
    // Constants for source configuration
    private static final String CAMERA = "camera";
    
    @Override
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {

        sourceName = source.getSourceName();

        // Specify which camera to read
        if (dataSourceConfig.get(CAMERA) instanceof Integer) {
            camera = (Integer) dataSourceConfig.get(CAMERA);
            capture = FFmpegFrameGrabber.createDefault(camera);
        } else {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".configMissingOptions: "  + 
                    "No camera specified in dataSourceConfig");
        }
    }

    /**
     * Obtain the most recent image from the camera
     * @throws FatalImageException  If the IP camera is no longer open
     */
    @Override
    public ImageRetrieverResults getImage() throws ImageAcquisitionException {
        // Used to check how long image retrieving takes
        long after;
        long before = System.currentTimeMillis();

        // Reading the next video frame from the camera
        org.bytedeco.opencv.opencv_core.Mat matrix;
        ImageRetrieverResults results = new ImageRetrieverResults();
        Date captureTime = new Date();

        matrix = grabFrameAsMat();

        byte[] imageByte = convertMatToJpeg(matrix);
        matrix.release();

        results.setImage(imageByte);
        results.setTimestamp(captureTime);

        after = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Image retrieving time for source " + sourceName + ": {}.{} seconds",
                    (after - before) / 1000, String.format("%03d", (after - before) % 1000));
        }
        return results;
    }
    
    /**
     * Obtain the most recent image from the specified camera, or the configured camera if no camera is specified
     */
    @Override
    public ImageRetrieverResults getImage(Map<String, ?> request) throws ImageAcquisitionException {
        FFmpegFrameGrabber cap;
        Object camId;
        ImageRetrieverResults results = new ImageRetrieverResults();

        // Specify which camera to read
        if (request.get("DScamera") instanceof Integer) {
            int cam = (Integer) request.get("DScamera");
            camId = cam;
            try {
                cap = FFmpegFrameGrabber.createDefault(cam);
            } catch (FFmpegFrameGrabber.Exception e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".cameraNotOpened: "
                        + "The camera id: " + camId + " could not be opened for source: " + sourceName + ".", e);
            }
        } else if (capture == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noMainCamera: "
                    + "No camera was requested and no main camera was specified at initialization.");
        } else {
            return getImage();
        }

        // If we got here, then we need to process the image from the specified camera.

        long after;
        long before = System.currentTimeMillis();
        Date captureTime = new Date();

        // Reading the next video frame from the camera
        Mat matrix = grabFrameAsMat(cap);

        byte[] imageByte = convertMatToJpeg(matrix);
        matrix.release();

        results.setImage(imageByte);
        results.setTimestamp(captureTime);

        after = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Image retrieving time for source " + sourceName + ": {}.{} seconds",
                    (after - before) / 1000, String.format("%03d", (after - before) % 1000));
        }
        return results;
    }
    
    public void close() {
        if (capture != null) {
            try {
                capture.release();
            } catch (FFmpegFrameGrabber.Exception e) {
                log.warn("Release exception: ", e);
                // otherwise, ignore
            }
        }
    }
}
