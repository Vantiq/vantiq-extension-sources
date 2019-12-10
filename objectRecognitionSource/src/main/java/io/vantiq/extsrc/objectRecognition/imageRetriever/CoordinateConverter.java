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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

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

    private BigDecimal[] converter = null;
    private BigDecimal[] src = null;
    private BigDecimal[] dst = null;
    private MathContext mc = new MathContext(8, RoundingMode.CEILING);


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
    public CoordinateConverter(BigDecimal[][] source, BigDecimal[][] destination) {
        this.converter = buildConverter(source, destination, false);
    }

    public CoordinateConverter(BigDecimal[][] source, BigDecimal[][] destination, boolean checkCollinearity) {
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
    static public boolean checkCollinearity(BigDecimal[][] points) {
        return checkCollinearity(points, new BigDecimal(.00001));
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
    static public boolean checkCollinearity(BigDecimal[][] points, BigDecimal eps) {
        // 3 points (p1, p2, p3) are collinear if and only if
        //     abs( (p2.x-p1.x)*(p3.y-p1.y) -
        //          (p3.x-p1.x)*(p2.y-p1.y) ) <= eps
        // Where eps is what passes for zero.  That is, if the slopes
        // of lines sharing a point are the same (or very close), then
        // the lines are collinear.

        BigDecimal x12 = points[1][0].subtract(points[0][0]);
        BigDecimal y12 = points[1][1].subtract(points[0][1]);
        BigDecimal x13 = points[2][0].subtract(points[0][0]);
        BigDecimal y13 = points[2][1].subtract(points[0][1]);
        BigDecimal x14 = points[3][0].subtract(points[0][0]);
        BigDecimal y14 = points[3][1].subtract(points[0][1]);
        BigDecimal x23 = points[2][0].subtract(points[1][0]);
        BigDecimal y23 = points[2][1].subtract(points[1][1]);
        BigDecimal x24 = points[3][0].subtract(points[1][0]);
        BigDecimal y24 = points[3][1].subtract(points[1][1]);
        // Test each unique triplet.
        // 4 choose 3 = 4 triplets: 123, 124, 134, 234
        return (x12.multiply(y13).subtract(x13.multiply(y12)).abs().compareTo(eps)) < 0 ||
                (x12.multiply(y14).subtract(x14.multiply(y12)).abs().compareTo(eps) < 0) ||
                (x13.multiply(y14).subtract(x14.multiply(y13)).abs().compareTo(eps) < 0 ||
                (x23.multiply(y24).subtract(x24.multiply(y23))).abs().compareTo(eps) < 0);
    }

    BigDecimal[] adj(BigDecimal[] m) { // Compute the adjugate of m
        return new BigDecimal[]{
                m[4].multiply(m[8]).subtract(m[5].multiply(m[7])), m[2].multiply(m[7]).subtract(m[1].multiply(m[8])), m[1].multiply(m[5]).subtract(m[2].multiply(m[4])),
                m[5].multiply(m[6]).subtract(m[3].multiply(m[8])), m[0].multiply(m[8]).subtract(m[2].multiply(m[6])), m[2].multiply(m[3]).subtract(m[0].multiply(m[5])),
                m[3].multiply(m[7]).subtract(m[4].multiply(m[6])), m[1].multiply(m[6]).subtract(m[0].multiply(m[7])), m[0].multiply(m[4]).subtract(m[1].multiply(m[3])),
        };
    }

    BigDecimal[] multmm(BigDecimal[] a, BigDecimal[] b) { // multiply two matrices
        BigDecimal[] c = new BigDecimal[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                BigDecimal cij = new BigDecimal(0);
                for (int k = 0; k < 3; k++) {
                    cij = cij.add(a[3 * i + k].multiply(b[3 * k + j]));
                }
                c[3 * i + j] = cij;
            }
        }
        return c;
    }

    BigDecimal[] multmv(BigDecimal[] m, BigDecimal[] v) { // multiply matrix and vector
        return new BigDecimal[]{
                m[0].multiply(v[0]).add(m[1].multiply(v[1])).add(m[2].multiply(v[2])),
                m[3].multiply(v[0]).add(m[4].multiply(v[1])).add(m[5].multiply(v[2])),
                m[6].multiply(v[0]).add(m[7].multiply(v[1])).add(m[8].multiply(v[2])),
        };
    }

    BigDecimal[] basisToPoints(BigDecimal x1, BigDecimal y1, BigDecimal x2, BigDecimal y2, BigDecimal x3, BigDecimal y3, BigDecimal x4, BigDecimal y4) {
        BigDecimal[] m = new BigDecimal[]{
                x1, x2, x3,
                y1, y2, y3,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        };
        BigDecimal[] v = multmv(adj(m),new BigDecimal[]{x4, y4, BigDecimal.ONE});
        return multmm(m,new BigDecimal[]{
                v[0], BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, v[1], BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, v[2]
        });
    }

    BigDecimal[] general2DProjection(
            BigDecimal x1s, BigDecimal y1s, BigDecimal x1d, BigDecimal y1d,
            BigDecimal x2s, BigDecimal y2s, BigDecimal x2d, BigDecimal y2d,
            BigDecimal x3s, BigDecimal y3s, BigDecimal x3d, BigDecimal y3d,
            BigDecimal x4s, BigDecimal y4s, BigDecimal x4d, BigDecimal y4d
    ) {
        BigDecimal[] s = basisToPoints(x1s, y1s, x2s, y2s, x3s, y3s, x4s, y4s);
        BigDecimal[] d = basisToPoints(x1d, y1d, x2d, y2d, x3d, y3d, x4d, y4d);
        return multmm(d, adj(s));
    }

    BigDecimal[] project(BigDecimal[] m, BigDecimal x, BigDecimal y) {
        BigDecimal[] v = multmv(m, new BigDecimal[]{x, y, BigDecimal.ONE});
        return new BigDecimal[]{v[0].divide(v[2], BigDecimal.ROUND_CEILING), v[1].divide(v[2], BigDecimal.ROUND_CEILING)};
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
    private BigDecimal[] buildConverter(BigDecimal[][] source, BigDecimal [][] destination, boolean checkCollinearity) {
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
    public BigDecimal[] convert(BigDecimal [] srcCoordsArray) {
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
