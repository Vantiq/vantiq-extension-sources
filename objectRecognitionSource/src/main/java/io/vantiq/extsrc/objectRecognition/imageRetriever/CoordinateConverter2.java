/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

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
public class CoordinateConverter2 {

    @SuppressWarnings({"WeakerAccess"})
    Logger log = LoggerFactory.getLogger(this.getClass());

    private double[] converter = null;
    private double[] src = null;
    private double[] dst = null;


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
    public CoordinateConverter2(double[][] source, double[][] destination) {
        this.converter = buildConverter(source, destination, false);
    }

    public CoordinateConverter2(double[][] source, double[][] destination, boolean checkCollinearity) {
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
    static public boolean checkCollinearity(double[][] points) {
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
    static public boolean checkCollinearity(double[][] points, double eps) {
        // 3 points (p1, p2, p3) are collinear if and only if
        //     abs( (p2.x-p1.x)*(p3.y-p1.y) -
        //          (p3.x-p1.x)*(p2.y-p1.y) ) <= eps
        // Where eps is what passes for zero.  That is, if the slopes
        // of lines sharing a point are the same (or very close), then
        // the lines are collinear.

        double x12 = points[1][0] - points[0][0];
        double y12 = points[1][1] - points[0][1];
        double x13 = points[2][0] - points[0][0];
        double y13 = points[2][1] - points[0][1];
        double x14 = points[3][0] - points[0][0];
        double y14 = points[3][1] - points[0][1];
        double x23 = points[2][0] - points[1][0];
        double y23 = points[2][1] - points[1][1];
        double x24 = points[3][0] - points[1][0];
        double y24 = points[3][1] - points[1][1];
        // Test each unique triplet.
        // 4 choose 3 = 4 triplets: 123, 124, 134, 234
        return ((Math.abs(x12 * y13 - x13 * y12) < eps) ||
                (Math.abs(x12 * y14 - x14 * y12) < eps) ||
                (Math.abs(x13 * y14 - x14 * y13) < eps) ||
                (Math.abs(x23 * y24 - x24 * y23) < eps));
    }

    double[] adj(double[] m) { // Compute the adjugate of m
        return new double[]{
                m[4] * m[8] - m[5] * m[7], m[2] * m[7] - m[1] * m[8], m[1] * m[5] - m[2] * m[4],
                m[5] * m[6] - m[3] * m[8], m[0] * m[8] - m[2] * m[6], m[2] * m[3] - m[0] * m[5],
                m[3] * m[7] - m[4] * m[6], m[1] * m[6] - m[0] * m[7], m[0] * m[4] - m[1] * m[3]
        };
    }

    double[] multmm(double[] a, double[] b) { // multiply two matrices
        double[] c = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double cij = 0;
                for (int k = 0; k < 3; k++) {
                    cij += a[3 * i + k] * b[3 * k + j];
                }
                c[3 * i + j] = cij;
            }
        }
        return c;
    }

    double[] multmv(double[] m, double[] v) { // multiply matrix and vector
        return new double[]{
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
        };
    }

    double[] basisToPoints(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        double[] m = new double[]{
                x1, x2, x3,
                y1, y2, y3,
                1, 1, 1
        };
        double[] v = multmv(adj(m),new double[]{x4, y4, 1});
        return multmm(m,new double[]{
                v[0], 0, 0,
                0, v[1], 0,
                0, 0, v[2]
        });
    }

    double[] general2DProjection(
            double x1s, double y1s, double x1d, double y1d,
            double x2s, double y2s, double x2d, double y2d,
            double x3s, double y3s, double x3d, double y3d,
            double x4s, double y4s, double x4d, double y4d
    ) {
        double[] s = basisToPoints(x1s, y1s, x2s, y2s, x3s, y3s, x4s, y4s);
        double[] d = basisToPoints(x1d, y1d, x2d, y2d, x3d, y3d, x4d, y4d);
        return multmm(d, adj(s));
    }

    double[] project(double[] m, double x, double y) {
        double[] v = multmv(m, new double[]{x, y, 1});
        return new double[]{v[0]/v[2], v[1]/v[2]};
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
    private double[] buildConverter(double[][] source, double [][] destination, boolean checkCollinearity) {
        return general2DProjection(
                source[0][0], source[0][1], destination[0][0], destination[0][1],
                source[1][0], source[1][1], destination[1][0], destination[1][1],
                source[2][0], source[2][1], destination[2][0], destination[2][1],
                source[3][0], source[3][1], destination[3][0], destination[3][1]);
    }

//    private Mat matixFromInput(Float[][] flts) {
//        Mat result = new Mat(new Size(1, 4), CV_32FC2);
//        float maxX = 0, minX = 0, maxY = 0, minY = 0;
//
//        for (int i = 0; i < flts.length; i++) {
//            if (i == 0) {
//                maxX = minX = flts[i][0];
//                maxY = minY = flts[i][1];
//
//            } else {
//                if (maxX < flts[i][0]) {
//                    maxX = flts[i][0];
//                }
//                if (minX > flts[i][0]) {
//                    minX = flts[i][0];
//                }
//                if (maxY < flts[i][1]) {
//                    maxY = flts[i][1];
//                }
//                if (minY > flts[i][1]) {
//                    minY = flts[i][1];
//                }
//
//            }
//            result.put(i, 0, flts[i][0], flts[i][1]);
//        }
//
//        dumpMatrix(result, "input Matrix");
//        log.debug("X difference: {}, y difference: {}", maxX - minX, maxY - minY);
//        return result;
//    }

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
    public double[] convert(double [] srcCoordsArray) {
        return project(converter, srcCoordsArray[0], srcCoordsArray[1]);

    }

//    /**
//     * Dump an OpenCV::Mat value for debug purposes
//     * @param m Mat to be dumped
//     * @param name String name of Mat (or other descriptive phrase of value to developer)
//     */
//    private void dumpMatrix(Mat m, String name) {
//        log.debug(name + " description: {} (type: {}, size: {})", m.toString(), m.type(), m.size());
//        log.debug("Contents of {}", name);
//        for (int row = 0; row < m.rows(); row++) {
//            for (int col = 0; col < m.cols(); col++) {
//                log.debug("\trow {}, col {} :: {}", row, col, m.get(row, col));
//            }
//        }
//
//    }
}
