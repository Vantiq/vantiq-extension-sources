package io.vantiq.extsrc.objectRecognition.imageRetriever;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public class TestFileRetriever extends ObjRecTestBase {
    FileRetriever fr;
    ObjectRecognitionCore source;
    
    @BeforeClass
    public static void checkFilesExist() {
        assumeTrue("No video file for test. Should be at " +  new File(VIDEO_LOCATION).getAbsolutePath() + "."
                , new File(VIDEO_LOCATION).exists());
    }
    
    @Before
    public void setup() {
        fr = new FileRetriever();
        source = new ObjectRecognitionCore("src", UNUSED, UNUSED, UNUSED);
    }
    
    @After
    public void tearDown() {
        source.close();
        fr = null;
        source = null;
    }
    
    @Test
    public void testImageReadBasic() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            byte[] data = fr.getImage();
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image " + e.toString());
        }
    }
    
    @Test
    public void testImageReadBasicInvalidLocation() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", "invalidLocation");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            fr.getImage();
            fail("Expected exception when calling with invalid default");
        } catch (ImageAcquisitionException e) {
            // Should create exception
        }
    }
    
    @Test
    public void testImageReadBasicInitiallyInvalidLocation() {
        final String location2 = "src/test/resources/image2";
        try {
            
            try {
                Map<String,String> config = new LinkedHashMap<>();
                config.put("fileLocation", location2);
                fr.setupDataRetrieval(config, source);
            } catch (Exception e) {
                fail("Exception occurred when setting up: " + e.toString());
            }
            try {
                fr.getImage();
                fail("Expected exception when calling with invalid default");
            } catch (ImageAcquisitionException e) {
                // Should create exception
            }
            
            copyFile(IMAGE_LOCATION, location2);
            
            try {
                byte[] data = fr.getImage();
                assert data != null;
                assert data.length > 0;
            } catch (ImageAcquisitionException e) {
                fail("Exception occurred when obtaining image after creating it " + e.toString());
            }
        } finally {
            deleteFile(location2);
        }
    }
    
    @Test
    public void testImageReadQuery() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", "invalidLocation");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            Map<String,String> message = new LinkedHashMap<>();
            message.put("fileLocation", IMAGE_LOCATION);
            byte[] data = fr.getImage(message);
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image " + e.toString());
        }
    }
    
    @Test
    public void testImageReadQueryWithInvalidLocation() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            Map<String,String> message = new LinkedHashMap<>();
            message.put("fileLocation", "invalidLocation");
            fr.getImage(message);
            fail("Expected exception when calling with invalid default");
        } catch (ImageAcquisitionException e) {
            // Should create exception
        }
    }
    
    @Test
    public void testImageReadEmptyQuery() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", IMAGE_LOCATION);
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up: " + e.toString());
        }
        try {
            Map<String,String> message = new LinkedHashMap<>();
            byte[] data = fr.getImage(message);
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image: " + e.toString());
        }
    }
    
    @Test
    public void testVideoBasicRead() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", VIDEO_LOCATION);
            config.put("fileExtension", "mov");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when setting up " + e.toString());
        }
        try {
            byte[] data = fr.getImage();
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining image: " + e.toString());
        }
    }
    
    @Test
    public void testVideoInvalidLocation() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", "invalidLocation");
            config.put("fileExtension", "mov");
            fr.setupDataRetrieval(config, source);
            fail("Expected setup exception on invalid video");
        } catch (Exception e) {
            // Should create exception
        }
    }
    
    @Test
    public void testVideoWithImage() {
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", IMAGE_LOCATION);
            config.put("fileExtension", "mov");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when obtaining image: " + e.toString());
        }
        try {
            byte[] data = fr.getImage();
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when obtaining first image: " + e.toString());
        }
        try {
            fr.getImage();
            fail("Expected exception when images run out");
        } catch (ImageAcquisitionException e) {
            fail("Exception should be fatal");
        } catch (FatalImageException e) {
            // Should create fatal exception, since no more can be read
        }
    }
    
    @Test
    public void testVideoQuery() {
        Map<String,Object> request = new LinkedHashMap<>();
        try {
            Map<String,String> config = new LinkedHashMap<>();
            config.put("fileLocation", IMAGE_LOCATION);
            config.put("fileExtension", "mov");
            fr.setupDataRetrieval(config, source);
        } catch (Exception e) {
            fail("Exception occurred when obtaining image: " + e.toString());
        }
        try {
            request.put("fileExtension", "mov");
            request.put("fileLocation", VIDEO_LOCATION);
            request.put("targetFrame", (double) 3);
            byte[] data = fr.getImage(request);
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when requesting frame 4 of video: " + e.toString());
        }
        try {
            request.put("targetFrame", (double) 1000000000000000.0);
            fr.getImage(request);
            fail("Did not reject frame past end of video");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
        try {
            request.put("targetFrame", (double) -1);
            fr.getImage(request);
            fail("Did not reject negative frame");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
        try {
            request.put("fileLocation", IMAGE_LOCATION);
            request.put("targetFrame", (double) 0);
            byte[] data = fr.getImage(request);
            assert data != null;
            assert data.length > 0;
        } catch (ImageAcquisitionException e) {
            fail("Exception occurred when requesting image: " + e.toString());
        }
        try {
            request.put("targetFrame", (double) 1);
            fr.getImage(request);
            fail("Did not reject non-zero frame for single image");
        } catch (ImageAcquisitionException e) {
            // Expected
        }
        
    }
    
// ================================================= Helper functions =================================================
    
    public void copyFile(String initialLocation, String copyLocation) {
        File f1 = new File(initialLocation);
        File f2 = new File(copyLocation);
        
        try {
            byte[] fileData = Files.readAllBytes(f1.toPath());
            Files.write(f2.toPath(), fileData);
        } catch (Exception e) {
            throw new RuntimeException("Error copying data", e);
        }
    }
}
