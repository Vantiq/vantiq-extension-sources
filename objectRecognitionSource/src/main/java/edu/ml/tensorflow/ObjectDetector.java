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

    // This will be used to create
    // "year-month-date-hour-minute-seconds"
    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss");
    
    private ImageUtil imageUtil;
    private int saveRate = 0;
    private int frameCount = 0;
    
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
     * @param outputDir The directory to which images will be saved. If null no images are saved.
     * @param saveRate  The rate at which images will be saved, once per every saveRate frames. Non-positive values are
     *                  functionally equivalent to 1. If outputDir is null does nothing.
     */
    public ObjectDetector(String graphFile, String labelFile, String outputDir, int saveRate) {
        try {
            GRAPH_DEF = IOUtil.readAllBytesOrExit(graphFile);
            LABELS = IOUtil.readAllLinesOrExit(labelFile);
            if (outputDir != null) {
                imageUtil = new ImageUtil(outputDir);
                this.saveRate = saveRate;
                frameCount = saveRate; // Capture the first image
            }
        } catch (ServiceException ex) {
            throw new IllegalArgumentException("Problem reading files for the yolo graph.", ex);
        }
        
        yoloGraph = createYoloGraph();
        yoloSession = new Session(yoloGraph);
        
        normalizerGraph = createNormalizerGraph();
        normalizerSession = new Session(normalizerGraph);
    }

    /**
     * Detect objects on the given image
     * @param imageLocation the location of the image
     */
    public List<Map> detect(final byte[] image) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = YOLOClassifier.getInstance().classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            
            // Saves an image every saveRate frames
            if (imageUtil != null && ++frameCount >= saveRate) {
                Date now = new Date();
                String fileName = format.format(now) + ".jpg";
                imageUtil.labelImage(image, recognitions, fileName);
                frameCount = 0;
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
            float[] outputTensor = new float[YOLOClassifier.getInstance().getOutputSizeByShape(result)];
            FloatBuffer floatBuffer = FloatBuffer.wrap(outputTensor);
            result.writeTo(floatBuffer);
            return outputTensor;
        }
    }
    
    /**
     * ADDED BY NAMIR - Used to convert recognitions to JSON
     * @param recognitions
     */
    private List<Map> returnJSON(final List<Recognition> recognitions) {
    	List<Map> jsonRecognitions = new ArrayList<Map>();
        for (Recognition recognition : recognitions) {
            //recognition.getTitle(), recognition.getConfidence(), recognition.getLocation());
        	HashMap map = new HashMap();
        	map.put("label", recognition.getTitle());
        	map.put("confidence", recognition.getConfidence().toString());
        	
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
