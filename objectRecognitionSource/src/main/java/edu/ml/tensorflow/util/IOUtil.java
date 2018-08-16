package edu.ml.tensorflow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

/**
 * Util class to read image, graphDef and label files.
 */
public final class IOUtil {
    private final static Logger LOGGER = LoggerFactory.getLogger(IOUtil.class);
    private IOUtil() {}

    /**
     * Reads all the bytes from a file, throws a {@link ServiceException} if it fails.
     * <br>Edited to read from working directory instead of classpath.
     * @param fileName  The file to be read
     * @return          The file's bytes
     */
    public static byte[] readAllBytesOrExit(final String fileName) {
        try {
            File f = new File(fileName);
            return Files.readAllBytes(f.toPath());
        } catch (IOException | NullPointerException ex) {
            LOGGER.error("Failed to read [{}]!", fileName);
            throw new ServiceException("Failed to read [" + fileName + "]!", ex);
        }
    }

    /**
     * Reads all the bytes from a file, throws a {@link ServiceException} if it fails.
     * <br>Edited to read from working directory instead of classpath.
     * @param filename  The file to be read
     * @return          A List containing the lines of the file
     */
    public static List<String> readAllLinesOrExit(final String filename) {
        try {
            File file = new File(filename);
            return Files.readAllLines(file.toPath(), Charset.forName("UTF-8"));
        } catch (IOException | NullPointerException ex) {
            LOGGER.error("Failed to read [{}]!", filename, ex.getMessage());
            throw new ServiceException("Failed to read [" + filename + "]!", ex);
        }
    }
    
    /**
     * Attempts to create the specified directory if it doesn't exist
     * <br>Edited so that it will recursively create parent directories if necessary, instead of assuming the parent
     * exists
     * @param directory
     */
    public static void createDirIfNotExists(final File directory) {
        if (!directory.exists()) {
            createDirIfNotExists(directory.getParentFile()); // Recursively create parents
            directory.mkdir();
        }
}
}
