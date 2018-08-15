package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.nio.file.Files;

import io.vantiq.extjsdk.ExtjsdkTestBase;

public class ObjRecTestBase extends ExtjsdkTestBase{
    public static final String UNUSED = "unused";
    
    public static final String IMAGE_LOCATION = "src/test/resources/sampleImage.jpg";
    public static final String VIDEO_LOCATION = "src/test/resources/sampleVideo.mov";
    
    public static void deleteFile(String fileName) {
        File f = new File(fileName);
        
        try {
            Files.deleteIfExists(f.toPath());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting file " + f.getAbsolutePath(), e);
        }
    }
    
    public static void deleteDirectory(String directoryName) {
        File d = new File(directoryName);
        
        try {
            File[] subFiles = d.listFiles();
            for (File f : subFiles) {
                Files.delete(f.toPath());
            }
            Files.deleteIfExists(d.toPath());
        } catch (Exception e) {
            throw new RuntimeException("Error deleting directory " + d.getAbsolutePath(), e);
        }
    }
}
