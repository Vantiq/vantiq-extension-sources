
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import org.bytedeco.opencv.opencv_core.Mat;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

/**
 * Captures images from an IP camera.
 * Unique settings are as follows. Remember to prepend "DS" when using an option in a Query. 
 * <ul>
 *      <li>{@code camera}: Required for Config, optional for Query. The URL of the video stream to read images from. For
 *                      queries, defaults to the camera specified in the Config.
 * </ul>
 * 
 * The timestamp is captured immediately before the image is grabbed from the camera. The additional data is:
 * <ul>
 *      <li>{@code camera}: The URL of the camera that the image was read from.
 * </ul>
 */
public class NetworkStreamRetriever extends RetrieverBase implements ImageRetrieverInterface {
    String         rtspTransport = "tcp";
    Boolean        isRTSP = false;
    Boolean        isPushProtocol = false;

    static boolean openCvLoaded = false;
    
    private String sourceName;

    @Override
    @SuppressWarnings("unchecked")
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        sourceName = source.getSourceName();
        if (dataSourceConfig.get(CAMERA) instanceof String){
            cameraOrFile = (String) dataSourceConfig.get(CAMERA);
            try {
                URI pushCheck = new URI(cameraOrFile);
                if ((!(pushCheck.getScheme().equalsIgnoreCase("http")) && !(pushCheck.getScheme().equalsIgnoreCase("https"))) ||
                        pushCheck.getPath().endsWith("m3u") || pushCheck.getPath().endsWith("m3u8")) {
                    isPushProtocol = true;
                }
                if (pushCheck.getScheme().equalsIgnoreCase("rtsp") ||
                        pushCheck.getScheme().equalsIgnoreCase("rtsps")) {
                    isRTSP = true;
                    // If the camera is an rtsp URL, check for any options
                    if (dataSourceConfig.get(RTSP_CONFIG) instanceof Map) {
                        Map<String, String> rtspConf = (Map<String, String>) dataSourceConfig.get(RTSP_CONFIG);
                        if (rtspConf.get(RTSP_TRANSPORT) instanceof String) {
                            rtspTransport = (String) rtspConf.get(RTSP_TRANSPORT);
                        }
                        // rtsp_flags is for cases where the transport is acting as a server
                        // It does not apply in our connector case.
                    }
                }
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".unknownProtocol: "
                        + "URL specifies unknown protocol, or protocol was improperly formatted. Error Message: ", e);
            }
            
            openCapture();

        } else {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".configMissingOptions: "  + 
                    "No camera specified in dataSourceConfig");
        }
    }

    protected void openCapture() throws ImageAcquisitionException {

        try {
            if (capture != null) {
                capture.close();
                capture.release();
                capture = null;
            }
            capture = new FFmpegFrameGrabber(cameraOrFile);

            if (isRTSP) {
                // Depending upon where the caller is running, the UDP transport for the actual video often employed
                // by RTSP sub-transports may or may not actually get through. In order to avoid this, we'll ask
                // the underlying transport to use TCP for the transport. We have not (yet) encountered an environment
                // where this is an issue.  Suggested by https://github.com/bytedeco/javacv/issues/1022.

                // More information on other options (that may be available) can be found here:
                // http://underpop.online.fr/f/ffmpeg/help/rtsp.htm.gz

                capture.setOption("rtsp_transport", rtspTransport);

                log.debug("Capture opened, Format: {}, codec: {}, Vid Options: {}",
                        capture.getFormat(), capture.getVideoCodecName(), capture.getOptions().toString());
            }
            capture.start();
            log.debug("Capture started, Format: {}, codec: {} ({}), Vid Meta: {}",
                    capture.getFormat(), capture.getVideoCodecName(), capture.getVideoCodec(), capture.getVideoMetadata().toString());

        } catch (Exception e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noGrabber: "
                    + "Unable to create or start FrameGrabber for camera '" + cameraOrFile + "'", e);
        }
    }

    /**
     * Reset framegrabber stream when each read requires the camera to be reset.
     *
     * Used for the network retriever where to get the "latest" frame, one
     * must often close & reopen the network camera.
     *
     * @param cap  FFMpegFrameGrabber to reset (if necessary)
     */
    @Override
    protected void resetForPush(FFmpegFrameGrabber cap) throws ImageAcquisitionException {
        if (isPushProtocol) {
            log.debug("Camera is via push protocol -- restarting...");
            try {
                cap.restart();
                log.debug("Capture restarted, Format: {}, codec: {} ({}), Vid Meta: {}",
                        capture.getFormat(), capture.getVideoCodecName(), capture.getVideoCodec(), capture.getVideoMetadata().toString());
            } catch (Exception e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".errorStopStart: "
                        + "Could not stop/start '" + cameraOrFile + "'", e);
            }
        }    }
    
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
        Mat matrix;
        ImageRetrieverResults results = new ImageRetrieverResults();
        Date captureTime = new Date();

        matrix = grabFrameAsMat();

        if (matrix == null || matrix.empty()) {
            if (matrix != null) {
                matrix.release();
            }
            // Check connection to URL first
            diagnoseConnection();

            openCapture();
            matrix = grabFrameAsMat();
            if (matrix.empty()) {
                matrix.release();
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraReadError2: "
                        + "Could not obtain frame from camera '" + cameraOrFile + "'");
            }
        }

        byte [] imageByte = convertMatToJpeg(matrix);
        matrix.release();
        
        results.setImage(imageByte);
        results.setTimestamp(captureTime);
        
        after = System.currentTimeMillis();
        log.debug("Image retrieving time for source " + sourceName + ": {}.{} seconds"
                , (after - before) / 1000, String.format("%03d", (after - before) % 1000));
        
        return results;
    }
    
    /**
     * Obtain the most recent image from the specified camera, or the configured camera if no camera is specified
     */
    @Override
    public ImageRetrieverResults getImage(Map<String, ?> request) throws ImageAcquisitionException {
        FFmpegFrameGrabber cap = null;
        ImageRetrieverResults results = new ImageRetrieverResults();

        if (request.get("DScamera") instanceof String) {
            String cam = (String) request.get("DScamera");
            cap = new FFmpegFrameGrabber(cam);
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

        byte [] imageByte = convertMatToJpeg(matrix);
        matrix.release();

        results.setImage(imageByte);
        results.setTimestamp(captureTime);

        after = System.currentTimeMillis();
        log.debug("Image retrieving time for source " + sourceName + ": {}.{} seconds"
                , (after - before) / 1000, String.format("%03d", (after - before) % 1000));

        return results;
    }
    
    public void diagnoseConnection() throws ImageAcquisitionException {
        try {
            URI URITest = new URI(cameraOrFile);
            if (URITest.getScheme().equalsIgnoreCase("http") || URITest.getScheme().equalsIgnoreCase("https")) {
                try {
                  URL urlProtocolTest = new URL((String) cameraOrFile);
                  InputStream urlReadTest = urlProtocolTest.openStream();
              } catch (MalformedURLException e) {
                  throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".unknownProtocol: "
                          + "URL specifies unknown protocol, or protocol was improperly formatted. Error Message: ", e);
              } catch (java.io.IOException e) {
                  throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".badRead: "
                          + "URL was unable to be read. Error Message: ", e);
              }
            } else {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".badRead: "
                        + "URL was unable to be read");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".unknownProtocol: "
                    + "URL specifies unknown protocol, or protocol was improperly formatted. Error Message: ", e);
        }
    }
    
    public void close() {
        try {
            if (capture != null) {
                capture.release();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(this.getClass().getCanonicalName() + ".badRelease: "
                    + "Unable to release framegrabber for cammera : " + cameraOrFile, e);

        }
    }
}
