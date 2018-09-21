package edu.ml.tensorflow;

import edu.ml.tensorflow.classifier.YOLOClassifier;
import edu.ml.tensorflow.model.Recognition;
import edu.ml.tensorflow.util.GraphBuilder;
import edu.ml.tensorflow.util.IOUtil;
import edu.ml.tensorflow.util.ImageUtil;
import edu.ml.tensorflow.util.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import io.vantiq.client.BaseResponseHandler;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import okhttp3.Response;

import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.ml.tensorflow.Config.MEAN;
import static edu.ml.tensorflow.Config.SIZE;

/**
 * ObjectDetector class to detect objects using pre-trained models with TensorFlow Java API.
 */
public class ObjectDetector {
    private final static Logger LOGGER = LoggerFactory.getLogger(ObjectDetector.class);
    private byte[] GRAPH_DEF;
    private List<String> LABELS;
    private final static String SERVER = "https://dev.vantiq.com";  // URL for the VANTIQ server to connect to

    // This will be used to create
    // "year-month-date-hour-minute-seconds"
    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");
    
    private ImageUtil imageUtil;
    private int saveRate = 0;
    private int frameCount = 0;
    private float threshold;
    private Vantiq vantiq = null;
    
    private Graph yoloGraph;
    private Session yoloSession;
    
    private Graph normalizerGraph;
    private Session normalizerSession;
    private String normalizerInputName;
    private String normalizerOutputName;

    /**
     * Initializes the ObjectDetector with the given graph and and labels.
     * <br>Edited to initialize and save the graph for reuse, and allow the files to be specified dynamically.
     * @param graphFile The location of a proto buffer file describing the YOLO net 
     * @param labelFile The location of the labels for the given net.
     * @param saveImage The method in which the image shall be saved, (either in VANTIQ, locally, or both). If null no
     *                  images are saved.
     * @param outputDir The directory to which images will be saved.
     * @param saveRate  The rate at which images will be saved, once per every saveRate frames. Non-positive values are
     *                  functionally equivalent to 1. If outputDir is null does nothing.
     */
    public ObjectDetector(float thresh, String graphFile, String labelFile, String saveImage, String outputDir, int saveRate) {
        try {
            GRAPH_DEF = IOUtil.readAllBytesOrExit(graphFile);
            LABELS = IOUtil.readAllLinesOrExit(labelFile);
            if (saveImage != null) {
                if (!saveImage.equals("vantiq") && !saveImage.equals("both") && !saveImage.equals("local")) {
                    LOGGER.error("The config value for saveImage was invalid. Images will not be saved.");
                } else {
                    if (saveImage.equals("vantiq") || saveImage.equals("both")) {
                        vantiq = new io.vantiq.client.Vantiq(SERVER);
                        vantiq.setAccessToken("Ax6jSjrrwqnaHmZtWhXvVV1ym8ARfpta6wwmuPhy928=");
                    }
                    imageUtil = new ImageUtil(vantiq, outputDir);
                    this.saveRate = saveRate;
                    frameCount = saveRate; // Capture the first image
                }
            }
        } catch (ServiceException ex) {
            throw new IllegalArgumentException("Problem reading files for the yolo graph.", ex);
        }
        
        threshold = thresh;
        
        yoloGraph = createYoloGraph();
        yoloSession = new Session(yoloGraph);
        
        normalizerGraph = createNormalizerGraph();
        normalizerSession = new Session(normalizerGraph);
    }

    /**
     * Detect objects on the given image
     * <br>Edited to return the results as a map and conditionally save the image
     * @param image The image in jpeg format
     * @return      A List of Maps, each of which has a {@code label} stating the type of the object identified,
     *              a {@code confidence} specifying on a scale of 0-1 how confident the neural net is that the
     *              identification is accurate, and a {@code location} containing the coordinates for the
     *              {@code top},{@code left}, {@code bottom}, and {@code right} edges of the bounding box for the object.
     */
    public List<Map<String, ?>> detect(final byte[] image) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            Date now = new Date(); // Saves the time before
            List<Recognition> recognitions = YOLOClassifier.getInstance(threshold).classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            
            // Saves an image every saveRate frames
            if (imageUtil != null && ++frameCount >= saveRate) {
                String fileName = format.format(now) + ".jpg";
                imageUtil.labelImage(image, recognitions, fileName);
                frameCount = 0;
            }
            //Namir's Method
            return returnJSON(recognitions);
        }
    }
    
    /**
     * Detect objects on the given image
     * <br>Edited to return the results as a map and conditionally save the image
     * @param image     The image in jpeg format
     * @param outputDir The directory to which the image should be written. If saveImage is null, no image is 
     *                  saved. If null and saveImage is non-null, it uses the default directory "images"
     * @param saveImage The method in which the image shall be saved, (either in VANTIQ, locally, or both). If null and
     *                  filename is null, no image is saved. If null and fileName is non-null, images will be saved.
     * @param fileName  The name of the file to which the image is saved, with ".jpg" appended if necessary. If
     *                  {@code outputDir} is null and no default exists, no image is saved. If {@code fileName} is null
     *                  and {@code outputDir} is non-null, then the file is saved as
     *                  "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
     * @return          A List of Maps, each of which has a {@code label} stating the type of the object identified, a
     *                  {@code confidence} specifying on a scale of 0-1 how confident the neural net is that the
     *                  identification is accurate, and a {@code location} containing the coordinates for the
     *                  {@code top},{@code left}, {@code bottom}, and {@code right} edges of the bounding box for
     *                  the object.
     */
    public List<Map<String, ?>> detect(final byte[] image, String saveImage, String outputDir, String fileName) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            Date now = new Date(); // Saves the time before
            List<Recognition> recognitions = YOLOClassifier.getInstance(threshold).classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            
            // Saves an image if requested
            if (saveImage != null || (fileName != null && this.imageUtil != null)) {
                ImageUtil imageUtil = this.imageUtil;
                Vantiq vantiq = null;
                if (saveImage != null) {
                    if (!saveImage.equals("vantiq") || !saveImage.equals("both") || !saveImage.equals("local")) {
                        LOGGER.error("The config value for saveImage was invalid. Images will not be saved.");
                    } else {
                        if (saveImage.equals("vantiq") || saveImage.equals("both")) {
                            vantiq = new io.vantiq.client.Vantiq(SERVER);
                            vantiq.setAccessToken("Ax6jSjrrwqnaHmZtWhXvVV1ym8ARfpta6wwmuPhy928=");
                        }
                        imageUtil = new ImageUtil(vantiq, outputDir);
                    }
                }
                if (fileName == null) {
                    fileName = format.format(now) + ".jpg";
                } else if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
                    fileName += ".jpg";
                }
                
                imageUtil.labelImage(image, recognitions, fileName);
            }
            //Namir's Method
            return returnJSON(recognitions);
        }
    }

    /**
     * Pre-process input. It resize the image and normalize its pixels
     * <br>Edited so that it reuses the Session rather than creating a new one.
     * @param imageBytes Input image
     * @return Tensor<Float> with shape [1][416][416][3]
     */
    private Tensor<Float> normalizeImage(final byte[] imageBytes) {
        try (Tensor<String> input = Tensor.create(imageBytes, String.class)) {
            return normalizerSession.runner()
                    .feed(normalizerInputName, input)
                    .fetch(normalizerOutputName)
                    .run().get(0).expect(Float.class);
        }
    }

    /**
     * Creates a Graph that contains the specified neural net.
     * <br>Not part of original code.
     * @return  A graph created using the neural net specified through the constructor
     */
    private Graph createYoloGraph() {
        Graph g = new Graph();
        g.importGraphDef(GRAPH_DEF);
        return g;
    }
    
    /**
     * Creates a Graph that will normalize images.
     * <br>Not part of original code. However, the production of the graph is taken directly from the original code
     * and transplanted here in order to save time on reruns.
     * @return  A graph that will normalize an image
     */
    private Graph createNormalizerGraph() {
        Graph g = new Graph();
        
        GraphBuilder graphBuilder = new GraphBuilder(g); 
        final Output<Float> output =
                graphBuilder.div( // Divide each pixels with the MEAN
                    graphBuilder.resizeBilinear( // Resize using bilinear interpolation
                            graphBuilder.expandDims( // Increase the output tensors dimension
                                    graphBuilder.cast( // Cast the output to Float
                                            graphBuilder.decodeJpeg(
                                                    graphBuilder.constant("input", new byte[0]), 3),
                                            Float.class),
                                    graphBuilder.constant("make_batch", 0)),
                            graphBuilder.constant("size", new int[]{SIZE, SIZE})),
                    graphBuilder.constant("scale", MEAN));
        normalizerInputName = "input";
        normalizerOutputName = output.op().name();
        return g;
    }

    /**
     * Executes graph on the given preprocessed image. 
     * <br>Edited forom orignal code to reduce runtime for repeated calls.
     * @param image preprocessed image
     * @return output tensor returned by tensorFlow
     */
    private float[] executeYOLOGraph(final Tensor<Float> image) {
        // Reusing the same session reduces runtime significantly (by ~13x on the developer's computer)
        try(Tensor<Float> result =
                yoloSession.runner().feed("input", 0, image).fetch("output").run().get(0).expect(Float.class)) {
            float[] outputTensor = new float[YOLOClassifier.getInstance(threshold).getOutputSizeByShape(result)];
            FloatBuffer floatBuffer = FloatBuffer.wrap(outputTensor);
            result.writeTo(floatBuffer);
            return outputTensor;
        }
    }
    
    /**
     * ADDED BY NAMIR - Used to convert recognitions to JSON
     * @param recognitions
     */
    private List<Map<String, ?>> returnJSON(final List<Recognition> recognitions) {
        List<Map<String, ?>> jsonRecognitions = new ArrayList<>();
        for (Recognition recognition : recognitions) {
            //recognition.getTitle(), recognition.getConfidence(), recognition.getLocation());
        	HashMap map = new HashMap();
        	map.put("label", recognition.getTitle());
        	map.put("confidence", recognition.getConfidence());
        	
        	HashMap location = new HashMap();
        	location.put("left", recognition.getLocation().getLeft());
        	location.put("top", recognition.getLocation().getTop());
        	location.put("right", recognition.getLocation().getRight());
        	location.put("bottom", recognition.getLocation().getBottom());
        	map.put("location", location);
        	
        	jsonRecognitions.add(map);
        	
        	LOGGER.info("{}", map);
        }
        
        return jsonRecognitions;
    }
    
    /**
     * Closes all the resources in use by the ObjectDetector
     * <br>Not part of original code.
     */
    public void close() {
        // ORDER MATTERS. The session must close before the graph otherwise it hangs indefinitely
        if (yoloSession != null) {
            yoloSession.close();
            yoloSession = null;
        }
        if (yoloGraph != null) {
            yoloGraph.close();
            yoloGraph = null;
        }
        
        if (normalizerSession != null) {
            normalizerSession.close();
            normalizerSession = null;
        }
        if (normalizerGraph != null) {
            normalizerGraph.close();
            normalizerGraph = null;
        }
    }

    /**
     * Makes sure to close everything if/when this object is garbage collected
     * <br>Not part of original code.
     */
    @Override
    protected void finalize() {
        close();
    }
}
