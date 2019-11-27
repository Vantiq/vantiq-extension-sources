package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.opencv.core.Core.DECOMP_LU;
import static org.opencv.core.CvType.CV_32F;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Construct a coordinate converter based on the 4 non-collinear points provided.  This converter
 * is reusable using the <code>convert()</code> call.  To create a new converter, create a new instance of this
 * class.
 */

public class CoordinateConverter {

    Logger log = LoggerFactory.getLogger(this.getClass());

    private Mat converter = null;
    private MatOfPoint2f src = null;    // Src saved for doc/debug
    private MatOfPoint2f dst = null;    // ditto


    /**
     * Create a converter instance that will convert points in one image space to another.
     *
     * The converter is defined by a set of 4 non-collinear points from the source & destination space.  These sets
     * of points match one another -- i.e. the 4 points (0-3) in the source represent the same point in the
     * image (using a different coordinate space) as the 4 points (0-3) in the destination space.  Thought of another
     * way, these two sets of points calibrate the transformation from one coordinate space to another.
     *
     * @param source 2D array of floats defining the source space.  Must be of length 4
     * @param destination 2D array of floats defining the target space.  Length 4, where each point corresponds to those in src.
     */
    public CoordinateConverter(Float source[][], Float destination[][]) {
        this.converter = buildConverter(source, destination);
    }

    /**
     * Construct the converter from the lists of floats.
     *
     * The converter lives for the life of the class.  It's a long-term object
     * that is re-used using the <code>convert()</code> method.
     *
     * @param source 2D array of floats defining the source coordinate space
     * @param destination 2D array of floats defining the destination coordinate space
     * @return Mat holding the the constructed converter
     */
    private Mat buildConverter(Float source[][], Float destination[][]) {

        Point srcPts[] = ptsFromFlts(source);
        Point dstPts[] = ptsFromFlts(destination);

        src = new MatOfPoint2f();
        dst = new MatOfPoint2f();

        src.fromArray(srcPts);
        dst.fromArray(dstPts);
        dumpMatrix(src, "src");
        dumpMatrix(dst, "dst");

        // DECOMP_LU (Gaussian elimination with the optimal pivot element chosen) is the default.
        // We'll use the "long form" here should we need to add an option to override this in the future.

        Mat cnvtr = Imgproc.getPerspectiveTransform(src, dst, DECOMP_LU);
        dumpMatrix(cnvtr, "Converter base");
        return cnvtr;
    }

    private Point[] ptsFromFlts(Float flts[][]) {
        Point pts[] = new Point[flts.length];
        for (int i = 0; i < flts.length; i++ ) {
            Point p = new Point(flts[i][0], flts[i][1]);
            pts[i] = p;
        }
        return pts;
    }

    public Float[] convert(Float imgFlts[]) {

//        Point imgPts[] = ptsFromFlts(imgFlts);
//        for (int i = 0; i < imgPts.length; i++ ) {
//            log.error("Input point {} is {}", i, imgPts[i]);
//        }
//        float imgfloats[] = new float[3];
//        int i = 0;
//        for (Point p :imgPts) {
//            log.error("Convert: Point is {}", p.toString());
//            imgfloats[i++] = 1.0f;
//            imgfloats[i++] = (float) p.x;
//            imgfloats[i++] = (float) p.y;
//        }
        log.debug("Converter: {}/{}::{}", converter.channels(), converter.depth(), converter.toString());
        Mat imgMat = new Mat(1,3, CV_32F);
        imgMat.put(0, 0, imgFlts[0]);
        imgMat.put(0, 1, imgFlts[1]);
        imgMat.put(0, 2, 1f);   // Fill in an identity channel

        // imgMat.put(0, imgfloats.length, imgfloats);
        log.debug("imgMat at creation: {} ({})", imgMat.toString(), imgMat.channels());
        dumpMatrix(imgMat, "imgMat");
        // imgMat.fromArray(imgPts);
        imgMat.reshape(1, 1);
        log.debug("imgMat after fill: {} ({})", imgMat.toString(), imgMat.channels());

        log.debug("Converter type: {}, size: {}", converter.type(), converter.size().toString());
//        Mat ones = Mat.ones(imgMat.size(), imgMat.type());
//        Mat cvtOnes = new MatOfPoint2f();
//        imgMat.convertTo(cvtOnes, imgMat.type());
//
//        log.error("Ones type: {}, size: {}", ones.type(), ones.size().toString());
//        log.error("cvtOnes type: {}, size: {}", cvtOnes.type(), cvtOnes.size().toString());
//
//        Mat imgMat1 = new Mat();
//        Core.hconcat(Arrays.asList(imgMat, cvtOnes), imgMat1);
//        // imgMat.push_back(cvtOnes);
//        log.error("imgMat1 type: {}, size: {}, ... {}", imgMat1.type(), imgMat1.size().toString(), imgMat1.toString());

        Mat imgAsConv = new Mat(0, 0, converter.type());
        log.debug("imgConv.type: {}", imgAsConv.type());
        dumpMatrix(imgAsConv, "imgAsConv");
        log.debug("Channels: imgMat: {} vs. converter: {}", imgMat.channels(), converter.channels());
        // imgMat.convertTo(imgAsConv, 6);
        Core.transpose(imgMat, imgAsConv);
        log.debug("After convert: imgConv.type: {} :: {}", imgAsConv.type(), imgAsConv.toString());
        dumpMatrix(imgAsConv, "imgAsConv (transposed imgMat)");

        //Mat converted = converter.mul(imgMat);
        Mat converted = new Mat();
        log.debug("imgAsConv x converter:  type: {} x {}", imgAsConv.type(), converter.type());

        Mat c = new Mat(0,0,imgAsConv.type());
        converter.convertTo(c, imgAsConv.type());
        dumpMatrix(c, "c -- converted converter");
//        log.error("C's type: {} ({}/{}), imgAsConv: {} ({}, {})", c.toString(), c.type(), c.channels(),
//                imgAsConv.toString(), imgAsConv.type(), imgAsConv.channels());
        log.debug("c's type: {} ({}/{}), imgAsConv: {} ({}, {})", c.toString(), c.type(), c.channels(),
                imgAsConv.toString(), imgAsConv.type(), imgAsConv.channels());
        Core.gemm(c, imgAsConv, 1, new Mat(), 0, converted);

        log.debug("Converted {} into  {}", imgFlts, converted.toString());
        dumpMatrix(converted, "converted");

        return new Float[] {(float) converted.get(0,0)[0], (float) converted.get(1,0)[0]};
    }

    private void dumpMatrix(Mat m, String name) {
        log.debug("Contents of {}", name);
        for (int row = 0; row < m.rows(); row++) {
            for (int col = 0; col < m.cols(); col++) {
                log.debug("\trow {}, col {} :: {}", row, col, m.get(row, col));
            }
        }

    }
}
