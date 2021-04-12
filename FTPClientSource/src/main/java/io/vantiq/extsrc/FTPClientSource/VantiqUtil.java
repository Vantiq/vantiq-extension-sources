package io.vantiq.extsrc.FTPClientSource;

import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.client.Vantiq;

public class VantiqUtil {
    private final static Logger LOGGER = LoggerFactory.getLogger(VantiqUtil.class);
    public String outputDir = null; // Added to remember the output dir for each instance
    public Vantiq vantiq = null; // Added to allow image saving with VANTIQ
    public String sourceName = null;
    public Boolean saveImage;
    public int frameSize;
    public Boolean queryResize = false;
    public int longEdge = 0;
    public Boolean uploadAsImage = false;

    final Logger Log;

    public VantiqUtil(Logger log) {
        this.Log = log;
    }

    public Boolean downloadFromVantiq(String fileURL, String localFilename)  {

        try{
        URL url = new URL(fileURL);
        try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream()); 
            FileOutputStream fileOutputStream = new FileOutputStream(localFilename); FileChannel fileChannel = fileOutputStream.getChannel()) {

            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            fileOutputStream.close();
        }
        return true ; 
    }catch (Exception ie){
        LOGGER.error("Download file failed", ie);
        return false;
    }

    }


}
