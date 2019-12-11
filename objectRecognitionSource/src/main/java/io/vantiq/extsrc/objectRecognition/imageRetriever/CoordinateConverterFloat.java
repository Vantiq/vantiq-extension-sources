/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.opencv.core.CvType.CV_32FC2;
import static org.opencv.core.CvType.CV_64FC1;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
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
 *
 * This class left in as an example for how to make things operate using the OpenCV version
 * of getPerspectiveWarp().  Will be deleted, but want it on the record.
 */

@SuppressWarnings({"FieldCanBeLocal"})
public class CoordinateConverterFloat {

    @SuppressWarnings({"WeakerAccess"})
    Logger log = LoggerFactory.getLogger(this.getClass());

    private Mat converter = null;
    private Mat src = null;
    private Mat dst = null;


    /**
     * Create a converter instance that will convert points in one image space to another.
     *
     * The converter is defined by a set of 4 non-collinear points from the source & destination space.  These sets
     * of points match one another -- i.e. the 4 points (0-3) in the source represent the same point in the
     * image (using a different coordinate space) as the 4 points (0-3) in the destination space.  Thought of another
     * way, these two sets of points calibrate the transformation from one coordinate space to another.
     *
     * Note: If one or the other of the quadrilaterals specified is insufficiently defined (that is, the segments are
     * too close to collinear, no error is given but the results are bizarre.
     *
     * TODO: Determine proper collinearity test here so that we can throw an error & fail rather than just
     * TODO: getting bizarre results. That is, improve on simple collinearity to determine if the
     * TODO: quadrilateral is separated enough.
     *
     * @param source 2D array of floats defining the source space.  Must be of length 4
     * @param destination 2D array of floats defining the target space.  Length 4, where each point corresponds to those in src.
     */
    public CoordinateConverterFloat(Float[][] source, Float[][] destination) {
        this.converter = buildConverter(source, destination, false);
    }

    public CoordinateConverterFloat(Float[][] source, Float[][] destination, boolean checkCollinearity) {
        this.converter = buildConverter(source, destination, checkCollinearity);
    }


    /**
     * Check points for collinearity
     *
     * When creating a coordinate converter, we need to ensure that the converter's
     * quadrilaterals used to specify the various points are, in fact, quadrilaterals.  That is,
     * since we specify the quadrilaterals using 4 points, we need to ensure that the no 3 of
     * the provided points are collinear.
     *
     * We will do this by verifying that the no 2 line segments that share a point have the same
     * (or very close) slope.
     *
     * This variant uses a default value of .00001 as zero.
     *
     * @param points Float[4][2] specifying the points for the 4 points, x & y.
     * @return boolean indicating collinearity
     */
    static public boolean checkCollinearity(Float[][] points) {
        return checkCollinearity(points, 0.00001f);
    }

    /**
     * Check points for collinearity
     *
     * When creating a coorindate converter, we need to ensure that the converter's
     * quadrilaterals used to specify the various points are, in fact, quadrilaterals.  That is,
     * since we specify the quadrilaterals using 4 points, we need to ensure that the no 3 of
     * the provided points are collinear.
     *
     * We will do this by verifying that the no 2 line segments that share a point have the same
     * (or very close) slope.
     *
     * @param points Float[4][2] specifying the points for the 4 points, x & y.
     * @param eps Float indicating what passes for zero comparing the slopes.
     * @return boolean indicating collinearity
     */
    static public boolean checkCollinearity(Float[][] points, Float eps) {
        // 3 points (p1, p2, p3) are collinear if and only if
        //     abs( (p2.x-p1.x)*(p3.y-p1.y) -
        //          (p3.x-p1.x)*(p2.y-p1.y) ) <= eps
        // Where eps is what passes for zero.  That is, if the slopes
        // of lines sharing a point are the same (or very close), then
        // the lines are collinear.

        Float x12 = points[1][0] - points[0][0];
        Float y12 = points[1][1] - points[0][1];
        Float x13 = points[2][0] - points[0][0];
        Float y13 = points[2][1] - points[0][1];
        Float x14 = points[3][0] - points[0][0];
        Float y14 = points[3][1] - points[0][1];
        Float x23 = points[2][0] - points[1][0];
        Float y23 = points[2][1] - points[1][1];
        Float x24 = points[3][0] - points[1][0];
        Float y24 = points[3][1] - points[1][1];
        // Test each unique triplet.
        // 4 choose 3 = 4 triplets: 123, 124, 134, 234
        return ((Math.abs(x12 * y13 - x13 * y12) < eps) ||
                (Math.abs(x12 * y14 - x14 * y12) < eps) ||
                (Math.abs(x13 * y14 - x14 * y13) < eps) ||
                (Math.abs(x23 * y24 - x24 * y23) < eps));
    }

    /**
     * Construct the converter from the lists of floats.
     *
     * The converter lives for the life of the class.  It's a long-term object
     * that is re-used using the <code>convert()</code> method.
     *
     * @param source 2D array of floats defining the source coordinate space
     * @param destination 2D array of floats defining the destination coordinate space
     * @param checkCollinearity boolean indicating if the constructor should check source & destination points for collinearity
     * @return Mat holding the the constructed converter
     */
    private Mat buildConverter(Float[][] source, Float[][] destination, boolean checkCollinearity) {

        if (checkCollinearity(source)) {
            if (checkCollinearity) {
                throw new IllegalArgumentException("Source points provided are collinear.");
            } else {
                log.warn("Source points are collinear (or close) which may cause problems in conversion");
            }
        } else if (checkCollinearity(destination)) {
            if (checkCollinearity) {
                throw new IllegalArgumentException("Destination points provided are collinear.");
            } else {
                log.warn("Destination points are collinear (or close) which may cause problems in conversion");
            }
        }
        src = matrixFromInput(source);
        dst = matrixFromInput(destination);

        if (log.isTraceEnabled()) {
            dumpMatrix(src, "src");
            dumpMatrix(dst, "dst");
        }

        // DECOMP_LU (Gaussian elimination with the optimal pivot element chosen) is the default.
        // We'll use the "long form" here should we need to add an option to override this in the future.

        Mat cnvtr = Imgproc.getPerspectiveTransform(src, dst, Core.DECOMP_LU);
        if (log.isTraceEnabled()) {
            dumpMatrix(cnvtr, "Converter base");
        }

        return cnvtr;
    }

    private Mat matrixFromInput(Float[][] flts) {
        Mat result = new Mat(new Size(1, 4), CV_32FC2);
        float maxX = 0, minX = 0, maxY = 0, minY = 0;

        for (int i = 0; i < flts.length; i++) {
            if (i == 0) {
                maxX = minX = flts[i][0];
                maxY = minY = flts[i][1];

            } else {
                if (maxX < flts[i][0]) {
                    maxX = flts[i][0];
                }
                if (minX > flts[i][0]) {
                    minX = flts[i][0];
                }
                if (maxY < flts[i][1]) {
                    maxY = flts[i][1];
                }
                if (minY > flts[i][1]) {
                    minY = flts[i][1];
                }

            }
            result.put(i, 0, flts[i][0], flts[i][1]);
        }

        if (log.isTraceEnabled()) {
            dumpMatrix(result, "input Matrix");
        }
        log.debug("X difference: {}, y difference: {}", maxX - minX, maxY - minY);
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
    public Float[] convert(Float[] srcCoordsArray) {
        // (Object) cast to force log.debug() to understand that it's not a varargs thing....
        log.debug("convert({}) called...", (Object) srcCoordsArray);

        // Construct a matrix (OpenCV Mat) representation of our input coordinates for conversion.
        // Note that we construct the transpose of what would normally be used for the coordinates
        // since the conversion operation involves (matrix) multiplication of our converter
        // with the transpose of the coordinates.  Since that's all we ever do with them & things are small,
        // we'll just create the transpose manually & use that directly.
        //
        // Calling this out so that readers understand why the matrix is created this way.

        Mat coords = new Mat(3,1, CV_64FC1);
        coords.put(0, 0, srcCoordsArray[0]);
        coords.put(1, 0, srcCoordsArray[1]);
        coords.put(2, 0, 1d);   // Fill in an identity value to ease matrix math
        if (log.isTraceEnabled()) {
            dumpMatrix(coords, "coords");   // Dump out for debug purposes
        }

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

        // Construct a Mat to hold the results of the transform
        Mat resultCoords = new Mat();
        // Do the math...
        Core.gemm(converter, coords, 1, Mat.zeros(converter.size(), converter.type()), 0, resultCoords);

        if (log.isTraceEnabled()) {
            dumpMatrix(resultCoords, "resultCoords");
        }

        // Convert result matrix into a simple array of coordinates.
        Float[] result = new Float[] {(float) resultCoords.get(0,0)[0], (float) resultCoords.get(1,0)[0]};
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
