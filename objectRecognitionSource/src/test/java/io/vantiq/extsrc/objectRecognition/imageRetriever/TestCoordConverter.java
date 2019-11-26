package io.vantiq.extsrc.objectRecognition.imageRetriever;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.Core;

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

        performConverterTest(srcPts, dstPts, null);
    }

    @Test
    public void test50Plus() {
        // Test change of base -- still a linear conversion (add 50)

        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {51.0f,51f}, {52f,51f}, {53f,53f}, {54f,53f}};

        performConverterTest(srcPts, dstPts, null);
    }

    @Test
    public void testScaleChange() {
        // Test a conversion that changes the scale of both the x & y axes.

        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {2.0f,2f}, {4f,2f}, {6f,6f}, {8f,6f}};

        performConverterTest(srcPts, dstPts, null);
    }

    @Test
    public void testWarpChange() {
        // Test a conversion that changes the scale of only the x axes.

        Float[][] srcPts = new Float[][] { {1.0f,1f}, {2f,1f}, {3f,3f}, {4f,3f}};
        Float[][] dstPts = new Float[][] { {2.0f,1f}, {4f,1f}, {6f,3f}, {8f,3f}};

        performConverterTest(srcPts, dstPts, null);
    }

    protected void performConverterTest(Float[][] srcPts, Float[][] dstPts, Float[][] tstPts) {

        // First, create a converter calibrated by the src & dst points passed in.
        CoordinateConverter converter = new CoordinateConverter( srcPts, dstPts);

        // verify that that converted correctly converts all the src pts -> dst points

        for (int i = 0; i < srcPts.length; i++ ) {
            Float[] src = srcPts[i];
            Float[] dst = dstPts[i];
            Float[] res = converter.convert(src);
            assert res[0].equals(dst[0]);
            assert res[1].equals(dst[1]);
        }

        // If our caller has provided other points, verify that these, too, result in correct values
        if (tstPts != null) {

        }


    }
}
