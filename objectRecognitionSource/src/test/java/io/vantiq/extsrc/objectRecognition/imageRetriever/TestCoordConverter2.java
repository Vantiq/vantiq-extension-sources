/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.Core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@Slf4j
public class TestCoordConverter2 {

    static final double ACCEPTABLE_DELTA = 0.00000001d;

    @BeforeClass
    public static void loadEmUp() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // FIXME:  Would be good to get some "real" GPS conversions in unit tests since that's really
    // FIXME: the primary goal here.

    @Test
    public void testIdentity() {

        // Test the simplest of conversions
        double[][] srcPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        double[][] dstPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};

        double[][] tstPts = generateRandomPoints(100);
        double[][] tstResPts = tstPts.clone();

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

     @Test
    public void testFlipY() {

        // Test the simplest of conversions
        double[][] srcPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        double[][] dstPts = new double[][]{{1.0d, -1d}, {2d, -1d}, {3d, -3d}, {4d, -3d}};

        double[][] tstPts = generateRandomPoints(100);
        double[][] tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = -tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testFlipX() {

        // Test the simplest of conversions
        double[][] srcPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        double[][] dstPts = new double[][]{{-1.0d, 1d}, {-2d, 1d}, {-3d, 3d}, {-4d, 3d}};

        double[][] tstPts = generateRandomPoints(100);
        double[][] tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = -tstPts[i][0];
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testInvertImage() {

        // Test the simplest of conversions
        double[][] srcPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        double[][] dstPts = new double[][]{{-1.0d, -1d}, {-2d, -1d}, {-3d, -3d}, {-4d, -3d}};

        double[][] tstPts = generateRandomPoints(100);
        double[][] tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = -tstPts[i][0];
            tstResPts[i][1] = -tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void test50Plus() {
        // Test change of base -- still a linear conversion (add 50)

        double[][] srcPts = new double[][]{
                {3d, 3d},
                {1.0d, 1d},
                {2d, 1d},
                {4d, 3d}};
        double[][] dstPts = new double[][]{
                {53d, 53d},
                {51.0d, 51d},
                {52d, 51d},
                {54d, 53d}};

        double[][] tstPts = generateRandomPoints(100);
        double[][] tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] + 50;
            tstResPts[i][1] = tstPts[i][1] + 50;
        }

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testScaleChange() {
        // Test a conversion that changes the scale of both the x & y axes.

        double[][] srcPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        double[][] dstPts = new double[][]{{2.0d, .002d}, {4d, .002d}, {6d, .006d}, {8d, .006d}};

        double[][] tstPts = generateRandomPoints(200);
        double[][] tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = (tstPts[i][1] * 2) / 1000;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

    }

    @Test
    public void testWarpChange() {
        // Test a conversion that changes the scale of only the x axis

        double[][] srcPts = new double[][]{
                {3d, 3d},
                {2d, 1d},
                {1.0d, 1d},
                {4d, 3d}};
        double[][] dstPts = new double[][]{
                {6d, 3d},
                {4d, 1d},
                {2.0d, 1d},
                {8d, 3d}};

        double[][] tstPts = generateRandomPoints(200);
        double[][] tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

        srcPts = new double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        dstPts = new double[][]{{1.0d, 10d}, {2d, 10d}, {3d, 30d}, {4d, 30d}};

        // Now, let's do a warp that changes only the y axis
        tstPts = generateRandomPoints(200);
        tstResPts = new double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = tstPts[i][1] * 10;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testGpsConv() {
        // image location --> lat/long
        double[][] gpsSrcPts = new double[][]{
                {1205d, 698d}, // Corner of dashed line bottom right
                {87d, 328d}, // Corner of white rumble strip top left
                {1200d, 278d}, // Third lamppost top right
                {36d, 583d}, // Corner of rectangular road marking bottom left
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        double[][] gpsDstPts = new double[][]{
                {6.603560d, 52.036730d * 1000}, // Corner of dashed line bottom right
                {6.603227d, 52.036181d * 1000}, // Corner of white rumble strip top left
                {6.602018d, 52.036769d * 1000}, // Third lamppost top right
                {6.603638d, 52.036558d * 1000}, // Corner of rectangular road marking bottom left
//                {.003560d, .036730d}, // Corner of dashed line bottom right
//                {.003227d, .036181d}, // Corner of white rumble strip top left
//                {.002018d, .036769d}, // Third lamppost top right
//                {.003638d, .036558d}, // Corner of rectangular road marking bottom left

        };

        performConverterTest(gpsSrcPts, gpsDstPts);
    }

    @Test
    public void testAlleyCam1Collinearity() {
        double[][] alleyCam1SrcPts = new double[][]{
                {65.d, 10.d},
                {847.d, 8.d},
                {850.d, 694.d},
                {63.d, 696.d},
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        double[][] alleyCam1DstPts = new double[][]{
                {-83.74801820260836d, 42.27796103748212d},
                {-83.74819690478398d, 42.27796396568019d},
                {-83.74813303468181d, 42.78190293489615d},
                {-83.74800177407815d, 42.78187357400434d},
//                {-.74801820260836d, .27796103748212d},
//                {-.74819690478398d, .27796396568019d},
//                {-.74813303468181d, .78190293489615d},
//                {-.74800177407815d, .78187357400434d},
        };
        assertFalse("Destination Points Collinear", CoordinateConverter2.checkCollinearity(alleyCam1DstPts, 0.00001d));
    }

    @Test
   // @Ignore // not working yet...  Input data suspect
    public void testAlleyCam1() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        double[][] alleyCam1SrcPts = new double[][]{
                {65.d, 10.d},
                {847.d, 8.d},
                {850.d, 694.d},
                {63.d, 696.d},
        };
            // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        double[][] alleyCam1DstPts = new double[][]{
                {-83.74801820260836d, 42.27796103748212d},
                {-83.74819690478398d, 42.27796396568019d},
                {-83.74813303468181d, 42.78190293489615d},
                {-83.74800177407815d, 42.78187357400434d},
//                {-.74801820260836d, .27796103748212d},
//                {-.74819690478398d, .27796396568019d},
//                {-.74813303468181d, .78190293489615d},
//                {-.74800177407815d, .78187357400434d},
        };

        assertFalse("Source Points Collinear", CoordinateConverter2.checkCollinearity(alleyCam1SrcPts));
        assertFalse("Destination Points Collinear", CoordinateConverter2.checkCollinearity(alleyCam1DstPts, 0.00001d));

        performConverterTest(alleyCam1SrcPts, alleyCam1DstPts);
    }

    protected void performConverterTest(double[][] srcPts, double[][] dstPts) {
        performConverterTest(srcPts, dstPts, null, null);
    }

    protected void performConverterTest(double[][] srcPts, double[][] dstPts, double[][] tstSrc, double[][] tstRes) {

        // First, create a converter calibrated by the src & dst points passed in.
        CoordinateConverter2 converter = new CoordinateConverter2(srcPts, dstPts);

        // verify that that converted correctly converts all the src pts -> dst points

        for (int iter = 0; iter < 2; iter++) {
            for (int i = 0; i < srcPts.length; i++) {
                double[] src = srcPts[i];
                double[] dst = dstPts[i];
                double[] res = converter.convert(src);
                if (iter ==1) {
                    assertEquals("X coord mismatch (" + i + ")", dst[0], res[0], ACCEPTABLE_DELTA);
                    assertEquals("Y coord mismatch (" + i + ")", dst[1], res[1], ACCEPTABLE_DELTA);
                } else {
                    log.debug("Iter: {}:{}, X: dst: {}, res:{}, diff: {}", iter, i, dst[0], res[0], dst[0] - res[0]);
                    log.debug("Iter: {}:{}, Y: dst: {}, res:{}, diff: {}", iter, i, dst[1], res[1], dst[1] - res[1]);
                }
            }
        }

        // If our caller has provided other points, verify that these, too, result in correct values

        if (tstSrc != null) {
            assertNotNull(tstRes);
            assertEquals("Src & expected results for test points must be the same length",
                    tstSrc.length, tstRes.length);

            for (int ti = 0; ti < tstSrc.length; ti++) {
                double[] src = tstSrc[ti];
                double[] dst = tstRes[ti];
                log.debug("Checking conversion of ({}, {})", src[0], src[1]);
                double[] res = converter.convert(src);
                assertEquals("X coord mismatch in test points index: " + ti, dst[0], res[0], ACCEPTABLE_DELTA);
                assertEquals("Y coord mismatch in test points index: " + ti, dst[1], res[1], ACCEPTABLE_DELTA);
            }
        }
    }

    public static double[][] generateRandomPoints(int pointCount) {
        double[][] points = new double[pointCount][2];
        for (int i = 0; i < pointCount; i++) {
            // Generate some random points all over the place to test against...
            double factor1 = (i % 5 == 0 ? 1d : 10d * (i % 7 == 0 ? pointCount : 1));
            double x = (double) (Math.random() * factor1);

            double factor2 = (i % 2 == 0 ? 1d : 130d * (i % 3 == 0 ? pointCount : 1));
            double y = (double) (Math.random() * factor1);
            points[i] = new double[]{x, y};
        }
        return points;
    }
}
