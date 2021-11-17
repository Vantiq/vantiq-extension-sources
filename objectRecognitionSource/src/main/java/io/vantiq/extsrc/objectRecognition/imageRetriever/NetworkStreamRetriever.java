
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

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_java;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

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
public class NetworkStreamRetriever implements ImageRetrieverInterface {
    FFmpegFrameGrabber capture;
    String         camera;
    String         rtspTransport = "tcp";
    Boolean        isRTSP = false;
    Logger         log = LoggerFactory.getLogger(this.getClass().getCanonicalName());
    Boolean        isPushProtocol = false;

    static boolean openCvLoaded = false;
    
    private String sourceName;

    @Override
    @SuppressWarnings("unchecked")
    public void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception {
        sourceName = source.getSourceName();
        if (!openCvLoaded) {
            try {
                Loader.load(opencv_java.class);
                openCvLoaded = true;
            } catch (Throwable t) {
                throw new Exception(this.getClass().getCanonicalName() + ".opencvDependency"
                        + ": Could not load OpenCv for NetworkStreamRetriever."
                        + "This is most likely due to a missing .dll/.so/.dylib. Please ensure that the environment "
                        + "variable 'OPENCV_LOC' is set to the directory containing '" + Core.NATIVE_LIBRARY_NAME
                        + "' and any other library requested by the attached error", t);
            }
        }
        if (dataSourceConfig.get(CAMERA) instanceof String){
            camera = (String) dataSourceConfig.get(CAMERA);
            try {
                URI pushCheck = new URI(camera);
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

        // This av_log_set_level() call  turns off a boatload of warnings reading some types of cameras.
        // Fix for the actual issue (which isn't a functional one) hasn't been found, apparently.]
        // See https://github.com/bytedeco/javacv/issues/780 for more information.

        av_log_set_level(AV_LOG_ERROR);

        try {
            if (capture != null) {
                capture.close();
                capture.release();
                capture = null;
            }
            capture = new FFmpegFrameGrabber(camera);

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
                    + "Unable to create or start FrameGrabber for camera '" + camera + "'", e);
        }
    }

    protected Mat grabFrameAsMat() throws ImageAcquisitionException {
        if (isPushProtocol) {
            log.debug("Camera is via push protocol -- restarting...");
            try {
                capture.restart();
                log.debug("Capture restarted, Format: {}, codec: {} ({}), Vid Meta: {}",
                        capture.getFormat(), capture.getVideoCodecName(), capture.getVideoCodec(), capture.getVideoMetadata().toString());
            } catch (Exception e) {
                throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".errorStopStart: "
                        + "Could not stop/start '" + camera + "'", e);
            }
        }
        try {
            Frame frame = null;
            int tryCount = 0;
            while (frame == null && tryCount < 100) {
                frame = capture.grabImage();
                tryCount += 1;
                if (frame != null) {
                    if (!frame.getTypes().contains(Frame.Type.VIDEO)) {
                        log.debug("Found non-video frame: {}", frame.getTypes());
                        continue;
                    }
                }
            }

            OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
            return converterToMat.convertToOrgOpenCvCoreMat(frame);
        } catch (Exception e) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".badImageGrab: "
                    + "Could not obtain frame from camera '" + camera + "': " + e.toString(), e);
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
        Mat matrix = new Mat();
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
                        + "Could not obtain frame from camera '" + camera + "'");
            }
        }
      
        MatOfByte matOfByte = new MatOfByte();
        // Translate the image into jpeg, error out if it cannot
        if (!Imgcodecs.imencode(".jpg", matrix, matOfByte)) {
            matOfByte.release();
            matrix.release();
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".mainCameraConversionError: " 
                    + "Could not convert the frame from camera '" + camera + "' into a jpeg image");
        }
        byte [] imageByte = matOfByte.toArray();
        matOfByte.release();
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
        VideoCapture cap;
        Object camId;
        ImageRetrieverResults results = new ImageRetrieverResults();
        Date captureTime;
        
        if (request.get("DScamera") instanceof String) {
            String cam = (String) request.get("DScamera");
            camId = cam;
            cap = new VideoCapture(cam);
        } else if (capture == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noMainCamera: " 
                    + "No camera was requested and no main camera was specified at initialization.");
        } else  {// if (capture.isOpened()){
            return getImage();
        }

        return getImage();
        // FIXME -- sort this all out & fix it up with new structure
//        if (!cap.isOpened()) {
//            cap.release();
//            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraUnreadable: "
//                    + "Could not open camera '" + camId + "'");
//        }
//        Mat mat = new Mat();
//
//        captureTime = new Date();
//        cap.read(mat);
//        if (mat.empty()) {
//            cap.release();
//            mat.release();
//            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraReadError: "
//                    + "Could not obtain frame from camera '" + camId + "'");
//        }
//        MatOfByte matOfByte = new MatOfByte();
//        // Translate the image into jpeg, error out if it cannot
//        if (!Imgcodecs.imencode(".jpg", mat, matOfByte)) {
//            matOfByte.release();
//            mat.release();
//            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".queryCameraConversionError: "
//                    + "Could not convert the frame from camera '" + camera + "' into a jpeg image");
//        }
//        byte [] imageByte = matOfByte.toArray();
//        matOfByte.release();
//        mat.release();
//        cap.release();
//
//        results.setImage(imageByte);
//        results.setTimestamp(captureTime);
//        return results;
    }
    
    public void diagnoseConnection() throws ImageAcquisitionException {
        try {
            URI URITest = new URI(camera);
            if (URITest.getScheme().equalsIgnoreCase("http") || URITest.getScheme().equalsIgnoreCase("https")) {
                try {
                  URL urlProtocolTest = new URL((String) camera);
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
                    + "Unable to release framegrabber for cammera : " + camera, e);

        }
    }
}
