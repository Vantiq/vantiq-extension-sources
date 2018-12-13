package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vantiq.extsrc.objectRecognition.NoSendORCore;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class TestNetworkStreamRetriever {
    NetworkStreamRetriever retriever;
    NoSendORCore source;
    
    final String IP_CAMERA_URL = "http://207.192.232.2:8000/mjpg/video.mjpg";
    
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
        assumeTrue("Could not open requested url", isIpAccessible(IP_CAMERA_URL));
        
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
    
    
// ================================================= Helper functions =================================================
    public boolean isIpAccessible(String url) {
        URL img = null;
        try {
            img = new URL(url);
            InputStream s = img.openStream();
            byte[] b = new byte[256];
            s.read(b);
            String str = new String(b);
            System.out.println(str);
            s.close();
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
