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
 * This class is partially based on information on the following article & JavaScript demo site, although
 * substantial changes have been made.
 *
 *     https://franklinta.com/2014/09/08/computing-css-matrix3d-transforms/
 *     http://jsfiddle.net/dFrHS/1/
 *
 * Originally, work was done base on this article.
 *    https://medium.com/hal24k-techblog/how-to-track-objects-in-the-real-world-with-tensorflow-sort-and-opencv-a64d9564ccb1
 *
 * Unfortunately, as is often the case with OpenCV & neuralNet references, this is Python-based.  The "raw" OpenCV
 * doesn't necessarily have all the same capabilities defined the same way.  So, liberties have been taken and,
 * undoubtedly, some unnecessary complications are included.  Ultimately, for many of the cases with cameras
 * observing a fairly small area but wanting to plot things on a map (i.e. use GPS (lat, long) coordinates), the
 * Float-level precision was not sufficient.  Thus, this conversion operations using BigDecimal-based coordinates.
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
     * <p>
     * The converter is defined by a set of 4 non-collinear points from the source & destination space.  These sets
     * of points match one another -- i.e. the 4 points (0-3) in the source represent the same point in the
     * image (using a different coordinate space) as the 4 points (0-3) in the destination space.  Thought of another
     * way, these two sets of points calibrate the transformation from one coordinate space to another.
     * <p>
     * Note: If one or the other of the quadrilaterals specified is insufficiently defined (that is, the segments are
     * too close to collinear, no error is given but the results are bizarre.
     *
     * @param source      BigDecimal[][] 2D array of BigDecimal defining the source space.  Must be of length 4
     * @param destination BigDecimal[][] 2D array of BigDecimal defining the target space.  Length 4, where each point corresponds to those in src.
     */
    public CoordinateConverter(BigDecimal[][] source, BigDecimal[][] destination) {
        this.converter = buildConverter(source, destination, false);
    }

    public CoordinateConverter(BigDecimal[][] source, BigDecimal[][] destination, boolean checkCollinearity) {
        this.converter = buildConverter(source, destination, checkCollinearity);
    }

    /**
     * Convert a 2D coordinate according this converter's specification
     * <p>
     * This method converts from source coordinate space to destination using matrix constructed as
     * instance creation. The converter is not tied to any particular type of conversion;
     * it operates based on the 4 non-collinear points which form the basis for this
     * instance of the <class>CoordinateConverter</class>.
     *
     * @param srcCoordsArray Array[2] of BigDecimal that represent the coordinate to be converted.
     * @return Array[2] of BigDecimal representing the result of the conversion.
     */
    public BigDecimal[] convert(BigDecimal[] srcCoordsArray) {
        return project(converter, srcCoordsArray[0], srcCoordsArray[1]);
    }


    /**
     * Check points for collinearity
     * <p>
     * When creating a coordinate converter, we may need to ensure that the converter's
     * quadrilaterals used to specify the various points are, in fact, quadrilaterals.  That is,
     * since we specify the quadrilaterals using 4 points, we need to ensure that the no 3 of
     * the provided points are collinear.
     * <p>
     * We will do this by verifying that the no 2 line segments that share a point have the same
     * (or very close) slope.
     * <p>
     * This variant uses a default value of 0 as zero.
     *
     * @param points BigDecimal[4][2] specifying the points for the 4 points, x & y.
     * @return boolean indicating collinearity
     */
    static public boolean checkCollinearity(BigDecimal[][] points) {
        return checkCollinearity(points, BigDecimal.ZERO);
    }

    /**
     * Check points for collinearity
     * <p>
     * When creating a coorindate converter, we need to ensure that the converter's
     * quadrilaterals used to specify the various points are, in fact, quadrilaterals.  That is,
     * since we specify the quadrilaterals using 4 points, we need to ensure that the no 3 of
     * the provided points are collinear.
     * <p>
     * We will do this by verifying that the no 2 line segments that share a point have the same
     * (or very close) slope.
     *
     * @param points BigDecimal[4][2] specifying the points for the 4 points, x & y.
     * @param eps    BigDecimal indicating what passes for zero comparing the slopes.
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

    /**
     * Compute adjunct (or adjugate) of matrix passed in
     *
     * @param mat BigDecimal[][] input matrix
     * @return
     */
    static private BigDecimal[] adjunct(BigDecimal[] mat) {
        return new BigDecimal[]{
                mat[4].multiply(mat[8]).subtract(mat[5].multiply(mat[7])),
                mat[2].multiply(mat[7]).subtract(mat[1].multiply(mat[8])),
                mat[1].multiply(mat[5]).subtract(mat[2].multiply(mat[4])),
                mat[5].multiply(mat[6]).subtract(mat[3].multiply(mat[8])),
                mat[0].multiply(mat[8]).subtract(mat[2].multiply(mat[6])),
                mat[2].multiply(mat[3]).subtract(mat[0].multiply(mat[5])),
                mat[3].multiply(mat[7]).subtract(mat[4].multiply(mat[6])),
                mat[1].multiply(mat[6]).subtract(mat[0].multiply(mat[7])),
                mat[0].multiply(mat[4]).subtract(mat[1].multiply(mat[3])),
        };
    }

    /**
     * Multiply 2 matrices
     *
     * @param matA BigDecimal[]
     * @param matB BigDecimal[]
     * @return
     */
    static private BigDecimal[] multiplyMatrices3x(BigDecimal[] matA, BigDecimal[] matB) {
        BigDecimal[] c = new BigDecimal[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                BigDecimal cij = new BigDecimal(0);
                for (int k = 0; k < 3; k++) {
                    cij = cij.add(matA[3 * i + k].multiply(matB[3 * k + j]));
                }
                c[3 * i + j] = cij;
            }
        }
        return c;
    }

    /**
     * Multiply Matrix by Vector
     *
     * @param mat    BigDecimal[] Matrix
     * @param vector BigDecimal[] vector
     * @return
     */
    static private BigDecimal[] multiplyMatrixByVector3x(BigDecimal[] mat, BigDecimal[] vector) {
        return new BigDecimal[]{
                mat[0].multiply(vector[0]).add(mat[1].multiply(vector[1])).add(mat[2].multiply(vector[2])),
                mat[3].multiply(vector[0]).add(mat[4].multiply(vector[1])).add(mat[5].multiply(vector[2])),
                mat[6].multiply(vector[0]).add(mat[7].multiply(vector[1])).add(mat[8].multiply(vector[2])),
        };
    }

    /**
     * Convert raw points to matrix
     *
     * @param x1 BigDecimal X coord point 1
     * @param y1 BigDecimal Y coord point 1
     * @param x2 BigDecimal X coord point 2...
     * @param y2 BigDecimal
     * @param x3 BigDecimal
     * @param y3 BigDecimal
     * @param x4 BigDecimal
     * @param y4 BigDecimal
     * @return
     */
    static private BigDecimal[] pointCoordsToMatrix(BigDecimal x1,
                                                    BigDecimal y1,
                                                    BigDecimal x2,
                                                    BigDecimal y2,
                                                    BigDecimal x3,
                                                    BigDecimal y3,
                                                    BigDecimal x4,
                                                    BigDecimal y4) {
        BigDecimal[] m = new BigDecimal[]{
                x1, x2, x3,
                y1, y2, y3,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        };
        BigDecimal[] v = multiplyMatrixByVector3x(adjunct(m), new BigDecimal[]{x4, y4, BigDecimal.ONE});
        return multiplyMatrices3x(m, new BigDecimal[]{
                v[0], BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, v[1], BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, v[2]
        });
    }

    /**
     * Generate 2D projection matrix resuable.
     * <p>
     * Given 4 points in the source coordinate space & 4 in the destination,
     * generate a matix that converts from one coordinate space to the other.
     *
     * @param x1s BigDecimal X coord for source, point 1
     * @param y1s BigDecimal Y coord for source, point 1
     * @param x1d BigDecimal X coord for destination, point 1
     * @param y1d BigDecimal Y coord for source, point 1
     * @param x2s BigDecimal
     * @param y2s BigDecimal
     * @param x2d BigDecimal
     * @param y2d BigDecimal
     * @param x3s BigDecimal
     * @param y3s BigDecimal
     * @param x3d BigDecimal
     * @param y3d BigDecimal
     * @param x4s BigDecimal
     * @param y4s BigDecimal
     * @param x4d BigDecimal
     * @param y4d BigDecimal
     * @return BigDecimal[] reusable projection matrix
     */
    static private BigDecimal[] generate2DProjection(
            BigDecimal x1s, BigDecimal y1s, BigDecimal x1d, BigDecimal y1d,
            BigDecimal x2s, BigDecimal y2s, BigDecimal x2d, BigDecimal y2d,
            BigDecimal x3s, BigDecimal y3s, BigDecimal x3d, BigDecimal y3d,
            BigDecimal x4s, BigDecimal y4s, BigDecimal x4d, BigDecimal y4d
    ) {
        BigDecimal[] s = pointCoordsToMatrix(x1s, y1s, x2s, y2s, x3s, y3s, x4s, y4s);
        BigDecimal[] d = pointCoordsToMatrix(x1d, y1d, x2d, y2d, x3d, y3d, x4d, y4d);
        return multiplyMatrices3x(d, adjunct(s));
    }

    /**
     * Project points in source space to new space using conversion matrix provided
     * @param m BigDecimal[] Conversion matrix (presumably) built using generate2DProjection()
     * @param x BigDecimal X coordinate of source space to be converted
     * @param y BigDecimal Y coordinate of source space to be converted
     * @return BigDecimal[2] representing 2D coordinate in destination space
     */
    static private BigDecimal[] project(BigDecimal[] m, BigDecimal x, BigDecimal y) {
        BigDecimal[] v = multiplyMatrixByVector3x(m, new BigDecimal[]{x, y, BigDecimal.ONE});
        return new BigDecimal[]{v[0].divide(v[2], BigDecimal.ROUND_HALF_UP),
                v[1].divide(v[2], BigDecimal.ROUND_HALF_UP)};
    }

    /**
     * Construct the converter from the lists of (BigDecimal) coordinates.
     * <p>
     * The converter lives for the life of the class.  It's a long-term object
     * that is re-used using the <code>convert()</code> method.
     *
     * @param source            BigDecimal[][] 2D array of BigDecimal defining the source coordinate space
     * @param destination       BigDecimal[][] 2D array of BigDecimal defining the destination coordinate space
     * @param checkCollinearity boolean indicating if the constructor should check source & destination points for collinearity
     * @return BigDecimal[] Matrix holding the the constructed converter
     */
    private BigDecimal[] buildConverter(BigDecimal[][] source, BigDecimal[][] destination, boolean checkCollinearity) {
        if (checkCollinearity) {
            if (checkCollinearity(source)) {
                log.warn("Source points are collinear.  Likely result is inaccuracies in coordinate transformation.");
            }
            if (checkCollinearity(destination)) {
                log.warn("Destination points are collinear.  Likely result is inaccuracies in coordinate transformation.");
            }
        }
        return generate2DProjection(
                source[0][0], source[0][1], destination[0][0], destination[0][1],
                source[1][0], source[1][1], destination[1][0], destination[1][1],
                source[2][0], source[2][1], destination[2][0], destination[2][1],
                source[3][0], source[3][1], destination[3][0], destination[3][1]);
    }
}