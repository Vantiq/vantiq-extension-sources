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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.ml.tensorflow.Config.GRAPH_FILE;
import static edu.ml.tensorflow.Config.LABEL_FILE;
import static edu.ml.tensorflow.Config.MEAN;
import static edu.ml.tensorflow.Config.SIZE;

/**
 * ObjectDetector class to detect objects using pre-trained models with TensorFlow Java API.
 */
public class ObjectDetector {
    private final static Logger LOGGER = LoggerFactory.getLogger(ObjectDetector.class);
    private byte[] GRAPH_DEF;
    private List<String> LABELS;

    public ObjectDetector() {
        try {
            GRAPH_DEF = IOUtil.readAllBytesOrExit(GRAPH_FILE);
            LABELS = IOUtil.readAllLinesOrExit(LABEL_FILE);
        } catch (ServiceException ex) {
            LOGGER.error("Download one of my graph file to run the program! \n" +
                    "You can find my graphs here: https://drive.google.com/open?id=1GfS1Yle7Xari1tRUEi2EDYedFteAOaoN");
        }
    }

    /**
     * Detect objects on the given image
     * @param imageLocation the location of the image
     */
//    public void detect(byte[] namirTest, final String imageLocation) {
    public void detect(final byte[] image, final String imageName) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = YOLOClassifier.getInstance().classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            
            //Namir's Method
            List<Map> json = returnJSON(recognitions);
            ImageUtil.getInstance().labelImage(image, recognitions, imageName);
        }
    }

    /**
     * Pre-process input. It resize the image and normalize its pixels
     * @param imageBytes Input image
     * @return Tensor<Float> with shape [1][416][416][3]
     */
    private Tensor<Float> normalizeImage(final byte[] imageBytes) {
        try (Graph graph = new Graph()) {
            GraphBuilder graphBuilder = new GraphBuilder(graph);

            final Output<Float> output =
                graphBuilder.div( // Divide each pixels with the MEAN
                    graphBuilder.resizeBilinear( // Resize using bilinear interpolation
                            graphBuilder.expandDims( // Increase the output tensors dimension
                                    graphBuilder.cast( // Cast the output to Float
                                            graphBuilder.decodeJpeg(
                                                    graphBuilder.constant("input", imageBytes), 3),
                                            Float.class),
                                    graphBuilder.constant("make_batch", 0)),
                            graphBuilder.constant("size", new int[]{SIZE, SIZE})),
                    graphBuilder.constant("scale", MEAN));

            try (Session session = new Session(graph)) {
                return session.runner().fetch(output.op().name()).run().get(0).expect(Float.class);
            }
        }
    }

    /**
     * Executes graph on the given preprocessed image
     * @param image preprocessed image
     * @return output tensor returned by tensorFlow
     */
    private float[] executeYOLOGraph(final Tensor<Float> image) {
        try (Graph graph = new Graph()) {
            graph.importGraphDef(GRAPH_DEF);
            try (Session s = new Session(graph);
                Tensor<Float> result = s.runner().feed("input", image).fetch("output").run().get(0).expect(Float.class)) {
                float[] outputTensor = new float[YOLOClassifier.getInstance().getOutputSizeByShape(result)];
                FloatBuffer floatBuffer = FloatBuffer.wrap(outputTensor);
                result.writeTo(floatBuffer);
                return outputTensor;
            }
        }
    }

    /**
     * Prints out the recognize objects and its confidence
     * @param recognitions list of recognitions
     */
    private void printToConsole(final List<Recognition> recognitions) {
        for (Recognition recognition : recognitions) {
            LOGGER.info("Object: {} - confidence: {} - location: {}", 
            		recognition.getTitle(), recognition.getConfidence(), recognition.getLocation());
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
        	
        	LOGGER.info(map.toString());
        }
        
        return jsonRecognitions;
    }
}
