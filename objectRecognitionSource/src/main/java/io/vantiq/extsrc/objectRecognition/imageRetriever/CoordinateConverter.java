/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;


import static org.opencv.core.Core.DECOMP_LU;
import static org.opencv.core.CvType.CV_64F;

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
 *
 * This class is based on the following article:
 *    https://medium.com/hal24k-techblog/how-to-track-objects-in-the-real-world-with-tensorflow-sort-and-opencv-a64d9564ccb1
 *
 * Unfortunately, as is often the case with OpenCV & neuralNet references, this is Python-based.  The "raw" OpenCV
 * doesn't necessarily have all the same capabilities defined the same way.  So, liberties have been taken and,
 * undoubtedly, some unnecessary complications are included.  This is due, primarily, to the author's
 * mis-/lackOf- complete understanding of Python and its attendant packages.
 */

@SuppressWarnings({"FieldCanBeLocal"})
public class CoordinateConverter {

    @SuppressWarnings({"WeakerAccess"})
    Logger log = LoggerFactory.getLogger(this.getClass());

    private Mat converter = null;
    private MatOfPoint2f src = null;
    private MatOfPoint2f dst = null;


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
    public CoordinateConverter(Double[][] source, Double[][] destination) {
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
    private Mat buildConverter(Double[][] source, Double[][] destination) {

        src = ptsFromFlts(source);
        dst = ptsFromFlts(destination);

        if (log.isTraceEnabled()) {
            dumpMatrix(src, "src");
            dumpMatrix(dst, "dst");
        }

        // DECOMP_LU (Gaussian elimination with the optimal pivot element chosen) is the default.
        // We'll use the "long form" here should we need to add an option to override this in the future.

        Mat cnvtr = Imgproc.getPerspectiveTransform(src, dst, DECOMP_LU);
        if (log.isTraceEnabled()) {
            dumpMatrix(cnvtr, "Converter base");
        }

        return cnvtr;
    }

    private MatOfPoint2f ptsFromFlts(Double[][] flts) {
        MatOfPoint2f result =  new MatOfPoint2f();
        Point[] pts = new Point[flts.length];
        for (int i = 0; i < flts.length; i++ ) {
            Point p = new Point(flts[i][0], flts[i][1]);
            pts[i] = p;
        }
        result.fromArray(pts);
        return result;
    }

    /**
     * Convert a 2D coordinate according this converter's specification
     *
     * This method using OpenCV's matrix math & the previously generated perspectiveTransform
     * to convert a pair of points from the source space to the destination space.  The converter
     * is not tied to any particular type of conversion;  it operates based on the 4 non-collinear
     * points which form the basis for this instance of the <class>CoordinateConverter</class>.
     *
     * @param srcCoordsArray Array[2] of Floats that represent the coordinate to be converted.
     * @return Array[2] of Floats representing the result of the conversion.
     */
    public Double[] convert(Double[] srcCoordsArray) {
        // (Object) cast to force log.debug() to understand that it's not a varargs thing....
        log.debug("convert({}) called...", (Object) srcCoordsArray);

        // Construct a matrix (OpenCV Mat) representation of our input coordinates for conversion.
        // Note that we construct the transpose of what would normally be used for the coordinates
        // since the conversion operation involves (matrix) multiplication of our converter
        // with the transpose of the coordinates.  Since that's all we ever do with them & things are small,
        // we'll just create the transpose manually & use that directly.
        //
        // Calling this out so that readers understand why the matrix is created this way.

        Mat coords = new Mat(3,1, CV_64F);
        coords.put(0, 0, srcCoordsArray[0]);
        coords.put(1, 0, srcCoordsArray[1]);
        coords.put(2, 0, 1);   // Fill in an identity value to ease matrix math
        if (log.isTraceEnabled()) {
            dumpMatrix(coords, "coords");   // Dump out for debug purposes
        }

        // Construct a Mat to hold the results of the transform
        Mat resultCoords = new Mat();

        // Here, it's a bit tricky.
        // Here, we're going to perform a matrix multiply of the converter with
        // the (transpose of) our coordinates.  As noted above, the coordinates (coords) were created
        // transposed, so there's no extra work here.  Also, as noted in the class constructor, the types
        // in use here need to match, so we use a the coordinateConverter that's been converted to the types
        // used for our coordinates.
        //
        // To perform this multiply, we'll use the Generalized Matrix Multiplication (gemm) method.
        // Core.gemm(src1, src2, alpha, src3, beta, dst) performs the operation as:
        //     dst = alpha*src1.t()*src2 + beta*src3.t()
        // in python speak, which is to say that the result is the product of the (alphas * src1.transpose()) times
        // src2, to which is added the product of beta * src3.transpose();
        //
        // In our case, we don't need any weighted version, so the alpha parameter (#3) is 1.  Moreover, we
        // need nothing added to the results, so beta is zero, and the src3 parameter is an empty matrix.
        // Despite beta being zero, we must pass in an empty src3 matrix (parameter #4) that would've been
        // used in association with the beta parameter to adjust the result.
        //
        // The result of this operation appear in our resultCoords matrix, from which we extract the converter
        // coordinates.
        Core.gemm(converter, coords, 1, new Mat(), 0, resultCoords);

        if (log.isTraceEnabled()) {
            dumpMatrix(resultCoords, "resultCoords");
        }

        // Convert result matrix into a simple array of coordinates.
        Double[] result = new Double[] {(double) resultCoords.get(0,0)[0], (double) resultCoords.get(1,0)[0]};
        log.debug("convert({}) --> {}", srcCoordsArray, result);

        return result;
    }

    /**
     * Dump an OpenCV::Mat value for debug purposes
     * @param m Mat to be dumped
     * @param name String name of Mat (or other descriptive phrase of value to developer)
     */
    private void dumpMatrix(Mat m, String name) {
        log.debug(name + " description: {} (type: {}, size: {})", m.toString(), m.type(), m.size());
        log.debug("Contents of {}", name);
        for (int row = 0; row < m.rows(); row++) {
            for (int col = 0; col < m.cols(); col++) {
                log.debug("\trow {}, col {} :: {}", row, col, m.get(row, col));
            }
        }

    }
}
