package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.vantiq.extsrc.objectRecognition.NoSendORCore;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class TestNetworkStreamRetriever extends ObjRecTestBase {

    static final String RTSP_CAMERA_ADDRESS = "rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4";
    // Alternate purportedly opened, but sometimes apparently broken...
    // "rtsp://demo:demo@ipvmdemo.dyndns.org:5541/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast";

    NetworkStreamRetriever retriever;
    NoSendORCore source;
    
    @Before
    public void setup() {
        source = new NoSendORCore("src", "token", "server", "dir");
        retriever = new NetworkStreamRetriever();
    }
    
    @After
    public void tearDown() {
        retriever.close();
        source.close();
    }
    
    @Test
    public void testIpCamera() {
        // Don't fail if camera's offline...
        findValidCamera();
        
        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("camera", IP_CAMERA_URL);
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Could not setup retriever: " + e.getMessage());
        }
        
        try {
            ImageRetrieverResults imgResults = retriever.getImage();
            assert imgResults != null;
            byte[] data = imgResults.getImage();
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when requesting frame from camera: " + e.toString());
        }
    }

    @Test
    @Ignore("Test camera seems to have disappeared.  Need a more reliable strategy for these things")
    public void testRtspCamera() {
        // Don't fail if camera's offline...
        assumeTrue("Could not open requested url", isIpAccessible(RTSP_CAMERA_ADDRESS));

        try {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("camera", RTSP_CAMERA_ADDRESS);
            retriever.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Could not setup retriever: " + e.getMessage());
        }

        try {
            ImageRetrieverResults imgResults = retriever.getImage();
            assert imgResults != null;
            byte[] data = imgResults.getImage();
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when requesting frame from camera: " + e.toString());
        }
    }
}
