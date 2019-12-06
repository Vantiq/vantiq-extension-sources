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
import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.Core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Slf4j
public class TestCoordConverter {

    static final Double ACCEPTABLE_DELTA = 0.0001;

    @BeforeClass
    public static void loadEmUp() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // FIXME:  Would be good to get some "real" GPS conversions in unit tests since that's really
    // FIXME: the primary goal here.

    @Test
    public void testIdentity() {

        // Test the simplest of conversions
        Double[][] srcPts = new Double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        Double[][] dstPts = new Double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};

        Double[][] tstPts = generateRandomPoints(100);
        Double[][] tstResPts = tstPts.clone();

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void test50Plus() {
        // Test change of base -- still a linear conversion (add 50)

        Double[][] srcPts = new Double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        Double[][] dstPts = new Double[][]{{51.0d, 51d}, {52d, 51d}, {53d, 53d}, {54d, 53d}};

        Double[][] tstPts = generateRandomPoints(100);
        Double[][] tstResPts = new Double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] + 50;
            tstResPts[i][1] = tstPts[i][1] + 50;
        }

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testScaleChange() {
        // Test a conversion that changes the scale of both the x & y axes.

        Double[][] srcPts = new Double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        Double[][] dstPts = new Double[][]{{2.0d, 2d}, {4d, 2d}, {6d, 6d}, {8d, 6d}};

        Double[][] tstPts = generateRandomPoints(200);
        Double[][] tstResPts = new Double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = tstPts[i][1] * 2;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

    }

    @Test
    public void testWarpChange() {
        // Test a conversion that changes the scale of only the x axis

        Double[][] srcPts = new Double[][]{{1.0d, 1d}, {2.d, 1.d}, {3d, 3d}, {4d, 3d}};
        Double[][] dstPts = new Double[][]{{2.0d, 1d}, {4d, 1d}, {6d, 3d}, {8d, 3d}};

        Double[][] tstPts = generateRandomPoints(200);
        Double[][] tstResPts = new Double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

        srcPts = new Double[][]{{1.0d, 1d}, {2d, 1d}, {3d, 3d}, {4d, 3d}};
        dstPts = new Double[][]{{1.0d, 10d}, {2d, 10d}, {3d, 30d}, {4d, 30d}};

        // Now, let's do a warp that changes only the y axis
        tstPts = generateRandomPoints(200);
        tstResPts = new Double[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = tstPts[i][1] * 10;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    @Ignore // Points taken from online article.  Seem faulty
    public void testGpsConv() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        Double[][] gpsSrcPts = new Double[][]{
                {1200d, 278d}, // Third lamppost top right
                {87d, 328d}, // Corner of white rumble strip top left
                {36d, 583d}, // Corner of rectangular road marking bottom left
                {1205d, 698d} // Corner of dashed line bottom right
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        Double[][] gpsDstPts = new Double[][]{
                {6.602018d, 52.036769d}, // Third lamppost top right
                {6.603227d, 52.036181d}, // Corner of white rumble strip top left
                {6.603638d, 52.036558d}, // Corner of rectangular road marking bottom left
                {6.603560d, 52.036730d}// Corner of dashed line bottom right
        };

        performConverterTest(gpsSrcPts, gpsDstPts);
    }

    @Test
    @Ignore // not working yet...  Input data suspect
    public void testAlleyCam1() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        Double[][] alleyCam1SrcPts = new Double[][]{
                {65.d, 10.d},
                {847.d, 8.d},
                {850.d, 694.d},
                {63.d, 696.d},
        };
            // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        Double[][] alleyCam1DstPts = new Double[][]{
                {-83.74801820260836d, 42.27796103748212d},
                {-83.74819690478398d, 42.27796396568019d},
                {-83.74813303468181d, 42.78190293489615d},
                {-83.74800177407815d, 42.78187357400434d},
        };

        performConverterTest(alleyCam1SrcPts, alleyCam1DstPts);
    }

    protected void performConverterTest(Double[][] srcPts, Double[][] dstPts) {
        performConverterTest(srcPts, dstPts, null, null);
    }

    protected void performConverterTest(Double[][] srcPts, Double[][] dstPts, Double[][] tstSrc, Double[][] tstRes) {

        // First, create a converter calibrated by the src & dst points passed in.
        CoordinateConverter converter = new CoordinateConverter(srcPts, dstPts);

        // verify that that converted correctly converts all the src pts -> dst points

        for (int i = 0; i < srcPts.length; i++) {
            Double[] src = srcPts[i];
            Double[] dst = dstPts[i];
            Double[] res = converter.convert(src);
            assertEquals("X coord mismatch", dst[0], res[0], ACCEPTABLE_DELTA);
            assertEquals("Y coord mismatch", dst[1], res[1], ACCEPTABLE_DELTA);
        }

        // If our caller has provided other points, verify that these, too, result in correct values

        if (tstSrc != null) {
            assertNotNull(tstRes);
            assertEquals("Src & expected results for test points must be the same length",
                    tstSrc.length, tstRes.length);

            for (int ti = 0; ti < tstSrc.length; ti++) {
                Double[] src = tstSrc[ti];
                Double[] dst = tstRes[ti];
                log.debug("Checking conversion of ({}, {})", src[0], src[1]);
                Double[] res = converter.convert(src);
                assertEquals("X coord mismatch in test points index: " + ti, dst[0], res[0], ACCEPTABLE_DELTA);
                assertEquals("Y coord mismatch in test points index: " + ti, dst[1], res[1], ACCEPTABLE_DELTA);
            }
        }
    }

    public static Double[][] generateRandomPoints(int pointCount) {
        Double[][] points = new Double[pointCount][2];
        for (int i = 0; i < pointCount; i++) {
            // Generate some random points all over the place to test against...
            Double factor1 = (i % 5 == 0 ? 1d : 10d * (i % 7 == 0 ? pointCount : 1));
            Double x = (double) (Math.random() * factor1);

            Double factor2 = (i % 2 == 0 ? 1d : 130d * (i % 3 == 0 ? pointCount : 1));
            Double y = (double) (Math.random() * factor1);
            points[i] = new Double[]{x, y};
        }
        return points;
    }
}
