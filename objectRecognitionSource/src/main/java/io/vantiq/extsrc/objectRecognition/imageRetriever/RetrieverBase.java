/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */


package io.vantiq.extsrc.objectRecognition.imageRetriever;

import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;

public class RetrieverBase {
    FFmpegFrameGrabber capture;
    String cameraOrFile;
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    RetrieverBase() {
        // This av_log_set_level() call  turns off a boatload of warnings reading some types of cameras.
        // Fix for the actual issue (which isn't a functional one) hasn't been found, apparently.]
        // See https://github.com/bytedeco/javacv/issues/780 for more information.

        av_log_set_level(AV_LOG_ERROR);
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

    /**
     * Using the standard frame grabber, grab a frame and return it as a Mat.
     *
     * @return Mat containing the next frame from the device in question
     * @throws ImageAcquisitionException
     */
    protected Mat grabFrameAsMat() throws ImageAcquisitionException {
        return grabFrameAsMat(capture);
    }

    /**
     * Using the specified frame grabber, grab a frame and return it as a Mat.
     *
     * @return Mat containing the next frame from the device in question
     * @throws ImageAcquisitionException
     */
    protected Mat grabFrameAsMat(FFmpegFrameGrabber cap) throws ImageAcquisitionException {
        resetForPush(cap);

        try {
            Frame frame = null;
            int tryCount = 0;
            while (frame == null && tryCount < 100) {
                frame = cap.grabImage();
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
                    + "Could not obtain frame from camera '" + cameraOrFile + "': " + e.toString(), e);
        }
    }

    /**
     * Reset framegrabber stream when each read requires the camera to be reset.
     * Overridden where required.
     *
     * @param cap FFMpegFrameGrabber to reset as required
     */
    protected void resetForPush(FFmpegFrameGrabber cap) throws ImageAcquisitionException {
        // Base case is to do nothing.  This can be overridden for push protocol cases where a "rewind" is necessary.
        // At the time of this writing, used only for the network retriever where to get the "latest" frame, one
        // must often close & reopen the network camera.
    }
}
