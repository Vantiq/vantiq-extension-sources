package io.vantiq.extsrc.FTPClientSource;

import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import okhttp3.Response;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class VantiqUtil {
    private static final  Logger LOGGER = LoggerFactory.getLogger(VantiqUtil.class);
    public String outputDir = null; // Added to remember the output dir for each instance
    public Vantiq vantiq = null; // Added to allow image saving with VANTIQ
    public String sourceName = null;
    public Boolean saveImage;
    public int frameSize;
    public Boolean queryResize = false;
    public int longEdge = 0;
    public Boolean uploadAsImage = false;

    // Used to upload image to VANTIQ as VANTIQ Image
    static final String IMAGE_RESOURCE_PATH = "/resources/images";
    static final String DOCUMENT_RESOURCE_PATH = "/resources/documents";

    public VantiqUtil(Logger log) {
    }

    public VantiqUtil(Logger log, String server, String authToken) {
        vantiq = new io.vantiq.client.Vantiq(server);
        vantiq.setAccessToken(authToken);
    }

    public Boolean downloadFromVantiq(String fileURL, String localFilename) {

        try {

            URL url = new URL(fileURL);
            try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(localFilename);
                    FileChannel fileChannel = fileOutputStream.getChannel()) {

                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                fileOutputStream.close();
            }
            return true;
        } catch (Exception ie) {
            LOGGER.error("Download file failed", ie);
            return false;
        }

    }

    /**
     * A method used to delete locally saved images.
     * 
     * @param imgFile The file to be deleted.
     */
    public void deleteImage(File imgFile) {
        if (imgFile.delete()) {
            LOGGER.trace("File was successfully deleted.");
        } else {
            LOGGER.error("Failed to delete file");
        }
    }

    /**
     * A helper method called by uploadImage that uses VANTIQ SDK to upload the
     * image.
     * 
     * @param fileToUpload File to be uploaded.
     * @param target       The name of the file to be uploaded.
     */
    public Boolean uploadToVantiq(File fileToUpload, String target) {
        // Create the response handler for either upload option
        BaseResponseHandler responseHandler = new BaseResponseHandler() {
            @Override
            public void onSuccess(Object body, Response response) {
                super.onSuccess(body, response);
                LOGGER.trace("Content Location = " + this.getBodyAsJsonObject().get("content"));

                if (outputDir == null) {
                    deleteImage(fileToUpload);
                }
            }

            @Override
            public void onError(List<VantiqError> errors, Response response) {
                super.onError(errors, response);
                LOGGER.error("Errors uploading image with VANTIQ SDK: " + errors);
            }
        };

        // Check if we should upload as a document, or image
        if (uploadAsImage) {
            VantiqResponse vr = vantiq.upload(fileToUpload,
                    "image/jpeg",
                    // "objectRecognition/" + sourceName + '/' + target,
                    target,
                    DOCUMENT_RESOURCE_PATH);

            if (vr.getStatusCode() != 200) {
                LOGGER.error("Errors uploading image with VANTIQ SDK: " + vr.toString());

            }
            return vr.getStatusCode() == 200;
            /*
             * vantiq.upload(fileToUpload,
             * "image/jpeg",
             * // "objectRecognition/" + sourceName + '/' + target,
             * target,
             * IMAGE_RESOURCE_PATH,
             * responseHandler);
             */
        } else {
            vantiq.upload(fileToUpload,
                    "image/jpeg",
                    target,
                    // "objectRecognition/" + sourceName + '/' + target,
                    responseHandler);
        }
        return true;
    }
}
