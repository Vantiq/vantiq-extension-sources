
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

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bytedeco.opencv.opencv_core.Mat;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;

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
            return converterToMat.convertToMat(frame);
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
                        + "Could not obtain frame from camera '" + camera + "'");
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
        }

        if (cap == null) {
            throw new ImageAcquisitionException(this.getClass().getCanonicalName() + ".noMainCamera: " 
                    + "No camera was requested and no main camera was specified at initialization.");
        }

        return getImage();
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

    /**
     * Converts an image into jpeg format and releases the Mat that held the original image
     * @param image The image to convert
     * @return      The bytes of the image in jpeg format, or null if it could not be converted
     */
    byte[] convertMatToJpeg(Mat image) {
        // JPG conversion requires a buffer into which we'll place the jpg.  However, we have to guess at the size
        // beforehand.  To do this, we'll work out the size of the uncompressed image in the passed-in Mat,
        // and use that as our buffer size.  JPG's are compressed, so the results should be smaller...
        Size s = image.size();
        int maxSize = s.height() * s.width();
        byte[] buf = new byte[maxSize];
        BytePointer bytes = new BytePointer(buf);
        log.debug("Image facts: size: h:{}, w: {}, using buffer size (h*w): {}", s.height(), s.width(), maxSize);

        // Translate the image into jpeg, return null if it cannot
        byte[] imageBytes = null;
        if (image.empty()) {
            log.warn("Cannot convert empty image to jpg");
        } else if (imencode(".jpg", image, bytes)) {
            log.debug("bytes stuff: limit: {}, position: {}, capacity: {}", bytes.limit(),
                    bytes.position(), bytes.capacity());
            imageBytes = bytes.getStringBytes();
            log.debug("JPG length is: {}", imageBytes.length);
        } else {
            log.error("Failed to convert image to jpeg");
        }
        image.release();
        return imageBytes;
    }
}
