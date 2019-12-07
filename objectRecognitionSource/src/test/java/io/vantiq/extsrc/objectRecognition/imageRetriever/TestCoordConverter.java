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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@Slf4j
public class TestCoordConverter {

    static final Float ACCEPTABLE_DELTA = 0.8f;

    @BeforeClass
    public static void loadEmUp() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    // FIXME:  Would be good to get some "real" GPS conversions in unit tests since that's really
    // FIXME: the primary goal here.

    @Test
    public void testIdentity() {

        // Test the simplest of conversions
        Float[][] srcPts = new Float[][]{{1.0f, 1f}, {2f, 1f}, {3f, 3f}, {4f, 3f}};
        Float[][] dstPts = new Float[][]{{1.0f, 1f}, {2f, 1f}, {3f, 3f}, {4f, 3f}};

        Float[][] tstPts = generateRandomPoints(100);
        Float[][] tstResPts = tstPts.clone();

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void test50Plus() {
        // Test change of base -- still a linear conversion (add 50)

        Float[][] srcPts = new Float[][]{
                {3f, 3f},
                {1.0f, 1f},
                {2f, 1f},
                {4f, 3f}};
        Float[][] dstPts = new Float[][]{
                {53f, 53f},
                {51.0f, 51f},
                {52f, 51f},
                {54f, 53f}};

        Float[][] tstPts = generateRandomPoints(100);
        Float[][] tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] + 50;
            tstResPts[i][1] = tstPts[i][1] + 50;
        }

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testScaleChange() {
        // Test a conversion that changes the scale of both the x & y axes.

        Float[][] srcPts = new Float[][]{{1.0f, 1f}, {2f, 1f}, {3f, 3f}, {4f, 3f}};
        Float[][] dstPts = new Float[][]{{2.0f, .002f}, {4f, .002f}, {6f, .006f}, {8f, .006f}};

        Float[][] tstPts = generateRandomPoints(200);
        Float[][] tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = (tstPts[i][1] * 2) / 1000;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

    }

    @Test
    public void testWarpChange() {
        // Test a conversion that changes the scale of only the x axis

        Float[][] srcPts = new Float[][]{
                {3f, 3f},
                {2f, 1f},
                {1.0f, 1f},
                {4f, 3f}};
        Float[][] dstPts = new Float[][]{
                {6f, 3f},
                {4f, 1f},
                {2.0f, 1f},
                {8f, 3f}};

        Float[][] tstPts = generateRandomPoints(200);
        Float[][] tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

        srcPts = new Float[][]{{1.0f, 1f}, {2f, 1f}, {3f, 3f}, {4f, 3f}};
        dstPts = new Float[][]{{1.0f, 10f}, {2f, 10f}, {3f, 30f}, {4f, 30f}};

        // Now, let's do a warp that changes only the y axis
        tstPts = generateRandomPoints(200);
        tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = tstPts[i][1] * 10;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testGpsConv() {
        // image location --> lat/long
        Float[][] gpsSrcPts = new Float[][]{
                {1205f, 698f}, // Corner of dashed line bottom right
                {87f, 328f}, // Corner of white rumble strip top left
                {1200f, 278f}, // Third lamppost top right
                {36f, 583f}, // Corner of rectangular road marking bottom left
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        Float[][] gpsDstPts = new Float[][]{
//                {6.603560f, 52.036730f * 1000}, // Corner of dashed line bottom right
//                {6.603227f, 52.036181f * 1000}, // Corner of white rumble strip top left
//                {6.602018f, 52.036769f * 1000}, // Third lamppost top right
//                {6.603638f, 52.036558f * 1000}, // Corner of rectangular road marking bottom left
                {.003560f, .036730f}, // Corner of dashed line bottom right
                {.003227f, .036181f}, // Corner of white rumble strip top left
                {.002018f, .036769f}, // Third lamppost top right
                {.003638f, .036558f}, // Corner of rectangular road marking bottom left

        };

        performConverterTest(gpsSrcPts, gpsDstPts);
    }

    @Test
    public void testAlleyCam1Collinearity() {
        Float[][] alleyCam1SrcPts = new Float[][]{
                {65.f, 10.f},
                {847.f, 8.f},
                {850.f, 694.f},
                {63.f, 696.f},
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        Float[][] alleyCam1DstPts = new Float[][]{
                {-83.74801820260836f, 42.27796103748212f},
                {-83.74819690478398f, 42.27796396568019f},
                {-83.74813303468181f, 42.78190293489615f},
                {-83.74800177407815f, 42.78187357400434f},
//                {-.74801820260836f, .27796103748212f},
//                {-.74819690478398f, .27796396568019f},
//                {-.74813303468181f, .78190293489615f},
//                {-.74800177407815f, .78187357400434f},
        };
        assertFalse("Destination Points Collinear", CoordinateConverter.checkCollinearity(alleyCam1DstPts, 0.00001f));
    }

    @Test
   // @Ignore // not working yet...  Input data suspect
    public void testAlleyCam1() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        Float[][] alleyCam1SrcPts = new Float[][]{
                {65.f, 10.f},
                {847.f, 8.f},
                {850.f, 694.f},
                {63.f, 696.f},
        };
            // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        Float[][] alleyCam1DstPts = new Float[][]{
//                {-83.74801820260836f, 42.27796103748212f},
//                {-83.74819690478398f, 42.27796396568019f},
//                {-83.74813303468181f, 42.78190293489615f},
//                {-83.74800177407815f, 42.78187357400434f},
                {-.74801820260836f, .27796103748212f},
                {-.74819690478398f, .27796396568019f},
                {-.74813303468181f, .78190293489615f},
                {-.74800177407815f, .78187357400434f},
        };

        assertFalse("Source Points Collinear", CoordinateConverter.checkCollinearity(alleyCam1SrcPts));
        assertFalse("Destination Points Collinear", CoordinateConverter.checkCollinearity(alleyCam1DstPts, 0.00001f));

        performConverterTest(alleyCam1SrcPts, alleyCam1DstPts);
    }

    protected void performConverterTest(Float[][] srcPts, Float[][] dstPts) {
        performConverterTest(srcPts, dstPts, null, null);
    }

    protected void performConverterTest(Float[][] srcPts, Float[][] dstPts, Float[][] tstSrc, Float[][] tstRes) {

        // First, create a converter calibrated by the src & dst points passed in.
        CoordinateConverter converter = new CoordinateConverter(srcPts, dstPts);

        // verify that that converted correctly converts all the src pts -> dst points

        for (int iter = 0; iter < 2; iter++) {
            for (int i = 0; i < srcPts.length; i++) {
                Float[] src = srcPts[i];
                Float[] dst = dstPts[i];
                Float[] res = converter.convert(src);
                if (iter ==1) {
                    assertEquals("X coord mismatch", dst[0], res[0], ACCEPTABLE_DELTA);
                    assertEquals("Y coord mismatch", dst[1], res[1], ACCEPTABLE_DELTA);
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
                Float[] src = tstSrc[ti];
                Float[] dst = tstRes[ti];
                log.debug("Checking conversion of ({}, {})", src[0], src[1]);
                Float[] res = converter.convert(src);
                assertEquals("X coord mismatch in test points index: " + ti, dst[0], res[0], ACCEPTABLE_DELTA);
                assertEquals("Y coord mismatch in test points index: " + ti, dst[1], res[1], ACCEPTABLE_DELTA);
            }
        }
    }

    public static Float[][] generateRandomPoints(int pointCount) {
        Float[][] points = new Float[pointCount][2];
        for (int i = 0; i < pointCount; i++) {
            // Generate some random points all over the place to test against...
            Float factor1 = (i % 5 == 0 ? 1f : 10f * (i % 7 == 0 ? pointCount : 1));
            Float x = (float) (Math.random() * factor1);

            Float factor2 = (i % 2 == 0 ? 1f : 130f * (i % 3 == 0 ? pointCount : 1));
            Float y = (float) (Math.random() * factor1);
            points[i] = new Float[]{x, y};
        }
        return points;
    }
}
