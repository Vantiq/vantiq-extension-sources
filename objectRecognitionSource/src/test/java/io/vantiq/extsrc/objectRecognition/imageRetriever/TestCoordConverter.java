/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

@Slf4j
public class TestCoordConverter {

    static final BigDecimal ACCEPTABLE_DELTA = new BigDecimal(0.00000001d);
    

    // FIXME:  Would be good to get some "real" GPS conversions in unit tests since that's really
    // FIXME: the primary goal here.

    @Test
    public void testIdentity() {

        // Test the simplest of conversions
        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}};
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}};

        BigDecimal[][] tstPts = generateRandomPoints(100);
        BigDecimal[][] tstResPts = tstPts.clone();

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testFlipY() {

        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}};
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(-1d)},
                {new BigDecimal(2d), new BigDecimal(-1d)},
                {new BigDecimal(3d), new BigDecimal(-3d)},
                {new BigDecimal(4d), new BigDecimal(-3d)}};

        BigDecimal[][] tstPts = generateRandomPoints(100);
        BigDecimal[][] tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = BigDecimal.ZERO.subtract(tstPts[i][1]);
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testFlipX() {

        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}};
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(-1.0d), new BigDecimal(1d)},
                {new BigDecimal(-2d), new BigDecimal(1d)},
                {new BigDecimal(-3d), new BigDecimal(3d)},
                {new BigDecimal(-4d), new BigDecimal(3d)}};

        BigDecimal[][] tstPts = generateRandomPoints(100);
        BigDecimal[][] tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = BigDecimal.ZERO.subtract(tstPts[i][0]);
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testInvertImage() {

        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}};
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(-1.0d), new BigDecimal(-1d)},
                {new BigDecimal(-2d), new BigDecimal(-1d)},
                {new BigDecimal(-3d), new BigDecimal(-3d)},
                {new BigDecimal(-4d), new BigDecimal(-3d)}};

        BigDecimal[][] tstPts = generateRandomPoints(100);
        BigDecimal[][] tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = BigDecimal.ZERO.subtract(tstPts[i][0]);
            tstResPts[i][1] = BigDecimal.ZERO.subtract(tstPts[i][1]);
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void test50Plus() {
        // Test change of base -- still a linear conversion (add 50)

        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(3), new BigDecimal(3)},
                {new BigDecimal(1.0d), new BigDecimal(1)},
                {new BigDecimal(2), new BigDecimal(1)},
                {new BigDecimal(4), new BigDecimal(3)}
        };
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(53d), new BigDecimal(53d)},
                {new BigDecimal(51.0d), new BigDecimal(51d)},
                {new BigDecimal(52d), new BigDecimal(51d)},
                {new BigDecimal(54d), new BigDecimal(53d)}};

        BigDecimal[][] tstPts = generateRandomPoints(100);
        BigDecimal[][] tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0].add(new BigDecimal(50));
            tstResPts[i][1] = tstPts[i][1].add(new BigDecimal(50));
        }

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testScaleChange() {
        // Test a conversion that changes the scale of both the x & y axes.

        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}
        };
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(2.0d), new BigDecimal(.002d)},
                {new BigDecimal(4d), new BigDecimal(.002d)},
                {new BigDecimal(6d), new BigDecimal(.006d)},
                {new BigDecimal(8d), new BigDecimal(.006d)}
        };

        BigDecimal[][] tstPts = generateRandomPoints(200);
        BigDecimal[][] tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0].multiply(new BigDecimal(2));
            tstResPts[i][1] = tstPts[i][1].multiply(new BigDecimal(2))
                    .divide(new BigDecimal(1000),
                            BigDecimal.ROUND_HALF_UP);
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

    }

    @Test
    public void testWarpChange() {
        // Test a conversion that changes the scale of only the x axis

        BigDecimal[][] srcPts = new BigDecimal[][]{
                {new BigDecimal(3), new BigDecimal(3)},
                {new BigDecimal(2), new BigDecimal(1)},
                {new BigDecimal(1.0), new BigDecimal(1)},
                {new BigDecimal(4), new BigDecimal(3)}};
        BigDecimal[][] dstPts = new BigDecimal[][]{
                {new BigDecimal(6d), new BigDecimal(3)},
                {new BigDecimal(4d), new BigDecimal(1d)},
                {new BigDecimal(2.0), new BigDecimal(1d)},
                {new BigDecimal(8d), new BigDecimal(3d)}};

        BigDecimal[][] tstPts = generateRandomPoints(200);
        BigDecimal[][] tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0].multiply(new BigDecimal(2));
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

        srcPts = new BigDecimal[][]{
                {new BigDecimal(1.0), new BigDecimal(1d)},
                {new BigDecimal(2d), new BigDecimal(1d)},
                {new BigDecimal(3d), new BigDecimal(3d)},
                {new BigDecimal(4d), new BigDecimal(3d)}};
        dstPts = new BigDecimal[][]{
                {new BigDecimal(1.0d), new BigDecimal(10d)},
                {new BigDecimal(2d), new BigDecimal(10d)},
                {new BigDecimal(3d), new BigDecimal(30d)},
                {new BigDecimal(4d), new BigDecimal(30d)}};

        // Now, let's do a warp that changes only the y axis
        tstPts = generateRandomPoints(200);
        tstResPts = new BigDecimal[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = tstPts[i][1].multiply(new BigDecimal(10));
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void testGpsConv() {
        // image location --> lat/long
        BigDecimal[][] gpsSrcPts = new BigDecimal[][]{
                {new BigDecimal(1205), new BigDecimal(698d)}, // Corner of dashed line bottom right
                {new BigDecimal(87d), new BigDecimal(328d)}, // Corner of white rumble strip top left
                {new BigDecimal(1200d), new BigDecimal(278d)}, // Third lamppost top right
                {new BigDecimal(36d), new BigDecimal(583d)}, // Corner of rectangular road marking bottom left
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        BigDecimal[][] gpsDstPts = new BigDecimal[][]{
                {new BigDecimal(6.603560), new BigDecimal(52.036730d)}, // Corner of dashed line bottom right
                {new BigDecimal(6.603227), new BigDecimal(52.036181d)}, // Corner of white rumble strip top left
                {new BigDecimal(6.602018), new BigDecimal(52.036769d)}, // Third lamppost top right
                {new BigDecimal(6.603638), new BigDecimal(52.036558d)}, // Corner of rectangular road marking bottom left
        };

        performConverterTest(gpsSrcPts, gpsDstPts);
    }

    @Test
    public void testAlleyCam1Collinearity() {
        BigDecimal[][] alleyCam1SrcPts = new BigDecimal[][]{
                {new BigDecimal(65.d), new BigDecimal(10.d)},
                {new BigDecimal(847.d), new BigDecimal(8.d)},
                {new BigDecimal(850.d), new BigDecimal(694.d)},
                {new BigDecimal(63.d), new BigDecimal(696.d)},
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        BigDecimal[][] alleyCam1DstPts = new BigDecimal[][]{
                {new BigDecimal(-83.74801820260836), new BigDecimal(42.27796103748212)},
                {new BigDecimal(-83.74819690478398), new BigDecimal(42.27796396568019)},
                {new BigDecimal(-83.74813303468181), new BigDecimal(42.78190293489615)},
                {new BigDecimal(-83.74800177407815), new BigDecimal(42.78187357400434)},
        };
        assertFalse("Source Points Collinear", CoordinateConverter.checkCollinearity(alleyCam1SrcPts, new BigDecimal(0.00001d)));
        assertFalse("Destination Points Collinear", CoordinateConverter.checkCollinearity(alleyCam1DstPts, new BigDecimal(0.00001d)));
    }

    // (No data from AlleyCam3 was sent, so can't test it...)

    @Test
    public void testAlleyCam1() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        BigDecimal[][] alleyCam1SrcPts = new BigDecimal[][]{
                {new BigDecimal(65.d), new BigDecimal(10.d)},
                {new BigDecimal(847.d), new BigDecimal(8.d)},
                {new BigDecimal(850.d), new BigDecimal(694.d)},
                {new BigDecimal(63.d), new BigDecimal(696.d)},
        };
            // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        BigDecimal[][] alleyCam1DstPts = new BigDecimal[][]{
                {new BigDecimal(-83.74801820260836), new BigDecimal(42.27796103748212)},
                {new BigDecimal(-83.74819690478398), new BigDecimal(42.27796396568019)},
                {new BigDecimal(-83.74813303468181), new BigDecimal(42.78190293489615)},
                {new BigDecimal(-83.74800177407815), new BigDecimal(42.78187357400434)},
        };

        performAllyCamTest(alleyCam1SrcPts, alleyCam1DstPts);
    }

    @Test
    public void testAlleyCam2() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        BigDecimal[][] alleyCam2SrcPts = new BigDecimal[][]{
                {new BigDecimal(185d), new BigDecimal(6.d)},
                {new BigDecimal(501d), new BigDecimal(8.d)},
                {new BigDecimal(842.d), new BigDecimal(694.d)},
                {new BigDecimal(177.d), new BigDecimal(697.d)},
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        BigDecimal[][] alleyCamDstPts = new BigDecimal[][]{
                {new BigDecimal(-83.74799640966012), new BigDecimal(42.27827320046149)},
                {new BigDecimal(-83.74806581181838), new BigDecimal(42.278274936924966)},
                {new BigDecimal(-83.74806195614292), new BigDecimal(42.27856080155207)},
                {new BigDecimal(-83.74798165751054), new BigDecimal(42.27855723561665)},
        };

        performAllyCamTest(alleyCam2SrcPts, alleyCamDstPts);
    }

    @Test
    public void testAlleyCam4() {
        // image location --> lat/long
        // Coords in order topLeft, topRight, bottomRight, bottomLeft (CW from topLeft)
        BigDecimal[][] alleyCamSrcPts = new BigDecimal[][]{
                {new BigDecimal(486.d), new BigDecimal(30.d)},
                {new BigDecimal(704.d), new BigDecimal(29.d)},
                {new BigDecimal(713.d), new BigDecimal(697.d)},
                {new BigDecimal(153.d), new BigDecimal(699.d)},
        };
        // longitude, latitude == x,y  -- reversed from traditional specification (but like GeoJSON)
        BigDecimal[][] alleyCamDstPts = new BigDecimal[][]{
                {new BigDecimal(-83.7480351164952), new BigDecimal(42.27897774612168)},
                {new BigDecimal(-83.7479613557473), new BigDecimal(42.27897973062916)},
                {new BigDecimal(-83.74795967936666), new BigDecimal(42.27877160508008)},
                {new BigDecimal(-83.74804852754028), new BigDecimal(42.278773837658)},
        };

        performAllyCamTest(alleyCamSrcPts, alleyCamDstPts);
    }

    protected void performAllyCamTest(BigDecimal[][] alleyCamSrcPts, BigDecimal[][] alleyCamDstPts) {

        assertFalse("Source Points Collinear", CoordinateConverter.checkCollinearity(alleyCamSrcPts));
        assertFalse("Destination Points Collinear", CoordinateConverter.checkCollinearity(alleyCamDstPts));

        performConverterTest(alleyCamSrcPts, alleyCamDstPts);
    }

    protected void performConverterTest(BigDecimal[][] srcPts, BigDecimal[][] dstPts) {
        performConverterTest(srcPts, dstPts, null, null);
    }

    protected void performConverterTest(BigDecimal[][] srcPts, BigDecimal[][] dstPts, BigDecimal[][] tstSrc, BigDecimal[][] tstRes) {

        // First, create a converter calibrated by the src & dst points passed in.
        CoordinateConverter converter = new CoordinateConverter(srcPts, dstPts);

        // verify that that converted correctly converts all the src pts -> dst points
        // If tracing, dump all the differences first.

        for (int iter = (log.isTraceEnabled() ? 0 : 1); iter < 2; iter++) {
            for (int i = 0; i < srcPts.length; i++) {
                BigDecimal[] src = srcPts[i];
                BigDecimal[] dst = dstPts[i];
                BigDecimal[] res = converter.convert(src);
                if (iter ==1) {
                    assertTrue("X coord mismatch (" + i + "): expected: " + dst[0] + ", found: " + res[0],
                            dst[0].compareTo(res[0]) == 0);
                    assertTrue("Y coord mismatch (" + i + "): expected: " + dst[1] + ", found: " + res[1],
                            dst[1].compareTo(res[1]) == 0);
                } else {
                    log.trace("Iter: {}:{}, X: dst: {}, res:{}, diff: {}", iter, i, dst[0], res[0], dst[0].subtract(res[0]));
                    log.trace("Iter: {}:{}, Y: dst: {}, res:{}, diff: {}", iter, i, dst[1], res[1], dst[1].subtract(res[1]));
                }
            }
        }

        // If our caller has provided other points, verify that these, too, result in correct values

        if (tstSrc != null) {
            assertNotNull(tstRes);
            assertEquals("Src & expected results for test points must be the same length",
                    tstSrc.length, tstRes.length);

            for (int ti = 0; ti < tstSrc.length; ti++) {
                BigDecimal[] src = tstSrc[ti];
                BigDecimal[] dst = tstRes[ti];
                log.debug("Checking conversion of ({}, {})", src[0], src[1]);
                BigDecimal[] res = converter.convert(src);

                // Crazy test generation sometimes comes up with odd values.  Scale numbers to reasonable
                for (int i = 0; i < 2; i++) {
                    res[i] = res[i].setScale(dst[i].scale(), BigDecimal.ROUND_HALF_UP);
                }
                assertTrue("X coord mismatch in test points index: " + ti + ": expected: " + dst[0] + ", found: " + res[0],
                        dst[0].compareTo(res[0]) == 0);
                assertTrue("Y coord mismatch in test points index: " + ti + ": expected: " + dst[1] + ", found: " + res[1],
                        dst[1].compareTo(res[1]) == 0);
            }
        }
    }

    public static BigDecimal[][] generateRandomPoints(int pointCount) {
        BigDecimal[][] points = new BigDecimal[pointCount][2];
        for (int i = 0; i < pointCount; i++) {
            // Generate some random points all over the place to test against...
            BigDecimal factor1 = new BigDecimal(i % 5 == 0 ? 1d : 10d * (i % 7 == 0 ? pointCount : 1));
            BigDecimal x = factor1.multiply(new BigDecimal(Math.random()));

            BigDecimal factor2 = new BigDecimal(i % 2 == 0 ? 1d : 130d * (i % 3 == 0 ? pointCount : 1));
            // Limit craziness of test comparison for randomly generated values...
            BigDecimal y = factor2.multiply(new BigDecimal(Math.random())).setScale(12, BigDecimal.ROUND_HALF_UP);
            points[i] = new BigDecimal[]{x, y};
        }
        return points;
    }
}
