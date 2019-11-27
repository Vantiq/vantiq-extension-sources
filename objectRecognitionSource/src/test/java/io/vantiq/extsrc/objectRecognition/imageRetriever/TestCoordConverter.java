package io.vantiq.extsrc.objectRecognition.imageRetriever;

import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.Core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Slf4j
public class TestCoordConverter {

    @BeforeClass
    public static void loadEmUp() {
        System.loadLibrary( Core.NATIVE_LIBRARY_NAME );
    }

    @Test
    public void testIdentity() {

        // Test the simplest of conversions
        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};

        Float[][] tstPts = generateRandomPoints(100);
        Float[][] tstResPts = tstPts.clone();

        performConverterTest(srcPts, dstPts, tstPts, tstResPts);
    }

    @Test
    public void test50Plus() {
        // Test change of base -- still a linear conversion (add 50)

        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {51.0f,51f}, {52f,51f}, {53f,53f}, {54f,53f}};

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

        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {2.0f,2f}, {4f,2f}, {6f,6f}, {8f,6f}};

        Float[][] tstPts = generateRandomPoints(200);
        Float[][] tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = tstPts[i][1] * 2;
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

    }

    @Test
    public void testWarpChange() {
        // Test a conversion that changes the scale of only the x axis

        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {2.0f,1f}, {4f,1f}, {6f,3f}, {8f,3f}};

        Float[][] tstPts = generateRandomPoints(200);
        Float[][] tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0] * 2;
            tstResPts[i][1] = tstPts[i][1];
        }
        performConverterTest(srcPts, dstPts, tstPts, tstResPts);

        // Now, let's do a warp that changes only the y axis
        tstPts = generateRandomPoints(200);
        tstResPts = new Float[tstPts.length][2];
        for (int i = 0; i < tstPts.length; i++) {
            tstResPts[i][0] = tstPts[i][0];
            tstResPts[i][1] = tstPts[i][1] * 10;
        }    }

    protected void performConverterTest(Float[][] srcPts, Float[][] dstPts) {
        performConverterTest(srcPts, dstPts, null, null);
    }

    protected void performConverterTest(Float[][] srcPts, Float[][] dstPts, Float[][] tstSrc, Float [][] tstRes) {

        // First, create a converter calibrated by the src & dst points passed in.
        CoordinateConverter converter = new CoordinateConverter( srcPts, dstPts);

        // verify that that converted correctly converts all the src pts -> dst points

        for (int i = 0; i < srcPts.length; i++ ) {
            Float[] src = srcPts[i];
            Float[] dst = dstPts[i];
            Float[] res = converter.convert(src);
            assertEquals("X coord mismatch", dst[0], res[0]);
            assertEquals("Y coord mismatch", dst[1], res[1]);
        }

        // If our caller has provided other points, verify that these, too, result in correct values

        if (tstSrc != null) {
            assertNotNull(tstRes);
            assertEquals("Src & expected results for test points must be the same length",
                    tstSrc.length, tstRes.length);

            for (int ti = 0; ti < tstSrc.length; ti++ ) {
                Float[] src = tstSrc[ti];
                Float[] dst = tstRes[ti];
                log.debug("Checking conversion of ({}, {})", src[0], src[1]);
                Float[] res = converter.convert(src);
                assertEquals("X coord mismatch in test points index" + ti, dst[0], res[0]);
                assertEquals("Y coord mismatch in test points index" + ti, dst[1], res[1]);
            }
        }
    }

    protected Float[][] generateRandomPoints(int pointCount) {
        Float[][] points = new Float [pointCount][2];
        for (int i = 0; i < pointCount; i++ ) {
            // Generate some random points all over the place to test against...
            Float factor1 = (i % 5 == 0 ? 1f : 10f * ( i % 7 == 0 ? pointCount : 1));
            Float x = (float) (Math.random() * factor1);

            Float factor2 = (i % 2 == 0 ? 1f : 130f * ( i % 3 == 0 ? pointCount : 1));
            Float y = (float) (Math.random() * factor1);
            points[i] = new Float[] { x, y };
        }
        return points;
    }
}
