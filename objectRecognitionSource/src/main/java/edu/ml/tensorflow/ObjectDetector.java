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
import io.vantiq.client.Vantiq;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static edu.ml.tensorflow.Config.MEAN;

/**
 * ObjectDetector class to detect objects using pre-trained models with TensorFlow Java API.
 */
@SuppressWarnings("PMD.TooManyFields")
public class ObjectDetector {
    private final static Logger LOGGER = LoggerFactory.getLogger(ObjectDetector.class);
    private byte[] graph_def;
    private List<String> labels;

    // This will be used to create
    // "year-month-date-hour-minute-seconds"
    private static final  SimpleDateFormat format =
            new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss", Locale.getDefault());
    
    // Getting meta config options for YOLO Processor
    public MetaBasedConfig metaConfigOptions = new MetaBasedConfig();
    private int frameSize;
    
    private ImageUtil imageUtil;
    private int saveRate = 0;
    private Boolean labelImage;
    private int frameCount = 0;
    int fileCount = 0; // Used for saving files with same name
    private float threshold;
    private Vantiq vantiq = null;
    private String sourceName = null;
    private double[] anchorArray;
    private Map<String, Object> metaFileMap;
    
    private Graph yoloGraph;
    private Session yoloSession;
    
    private Graph normalizerGraph;
    private Session normalizerSession;
    private String normalizerInputName;
    private String normalizerOutputName;
    
    public String lastFilename;

    public class ResultHolder {
        public List<Map<String, ?>> results;
        public byte[] image;
    }

    /**
     * Initializes the ObjectDetector with the given graph and and labels.
     * <br>Edited to initialize and save the graph for reuse, and allow the files to be specified dynamically.
     * @param thresh        The threshold of confidence used by the Yolo Neural Network when deciding whether to save 
     *                      a recognition. 
     * @param graphFile     The location of a proto buffer file describing the YOLO net 
     * @param labelFile     (DEPRECATED) The location of the labels for the given net.
     * @param metaFile      The location of the meta file used to retrieve anchors and labels if a labelFile is not provided.
     * @param anchorArray   The list of anchor pairs used by the YOLOClassifier to label recognitions.
     * @param imageUtil     The instance of the ImageUtil class used to save images. Either initialized, or set to null.
     * @param labelImage    The boolean flag signifying if images should be saved with or without bounding boxes. If true,
     *                      the frames will be saved with bounding boxes, and vice versa.     
     * @param saveRate      The rate at which images will be saved, once per every saveRate frames. Non-positive values are
     *                      functionally equivalent to 1. If outputDir is null does nothing.
     * @param vantiq        The Vantiq variable used to connect to the VANTIQ SDK. Either authenticated, or set to null.
     * @param sourceName    The name of the VANTIQ Source
     */
    @SuppressWarnings({"checkstyle.ParameterNumberCheck", "PMD.CognitiveComplexity", "PMD.ExcessiveParameterList"})
    public ObjectDetector(float thresh, String graphFile, String labelFile, String metaFile, double[] anchorArray,
                          ImageUtil imageUtil, Boolean labelImage, int saveRate,
                          Vantiq vantiq, String sourceName) {
        try {
            graph_def = IOUtil.readAllBytesOrExit(graphFile);
            // Parse meta file if it exists, and get all general information we need
            if (metaFile != null) {
                parseMetaFile(metaFile);
                int frameHeight = (int) ((Map<String, Object>) metaFileMap.get("net")).get("height");
                int frameWidth = (int) ((Map<String, Object>) metaFileMap.get("net")).get("width");
                // Check that meta file has appropriate frame size, and that user has not overwritten frame size in Config
                if (frameHeight == frameWidth && frameHeight % 32 == 0 && metaConfigOptions.useMetaIfAvailable) {
                    // Set config value to the .meta file's frame size
                    metaConfigOptions.frameSize = frameHeight;
                }
            }
            this.frameSize = metaConfigOptions.frameSize;

            // If label file exists, use it. Otherwise, use the meta file's labels.
            if (labelFile != null) {
                labels = IOUtil.readAllLinesOrExit(labelFile);
            } else if (metaFile != null) {
                labels = (List<String>) metaFileMap.get("labels");
            }
            // If anchor config option was used, override the anchors. Otherwise, use meta file anchors if they exist.
            // If neither exist, then default anchor values will be used.
            if (anchorArray != null) {
                this.anchorArray = anchorArray;
            } else if (metaFile != null) {
                ArrayList anchorList = (ArrayList) metaFileMap.get("anchors");
                if (anchorList != null) {
                    this.anchorArray = new double[anchorList.size()];
                    for (int i = 0; i < anchorList.size(); i++) {
                        if (anchorList.get(i) instanceof Integer) {
                            this.anchorArray[i] = (double) ((Integer) anchorList.get(i));
                        } else {
                            this.anchorArray[i] = (double) anchorList.get(i);
                        }
                    }
                }
            }
            this.imageUtil = imageUtil;
            imageUtil.frameSize = this.frameSize;
            this.vantiq = vantiq;
            this.labelImage = labelImage;
            this.sourceName = sourceName;
            if (imageUtil.saveImage) {
                this.saveRate = saveRate;
                frameCount = saveRate;
            }
        } catch (ServiceException ex) {
            throw new IllegalArgumentException(ex.getLocalizedMessage(), ex);
        } catch (JsonParseException | JsonMappingException ex) {
            throw new IllegalArgumentException("Problem reading the meta file.", ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Problem reading one of the model files.", ex);
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
     * @param image     The image in jpeg format
     * @param timestamp The timestamp corresponding to when the frame was captured. Used to name the image if it is being saved
     * @return ResultHolder containing a List of Maps, each of which has a {@code label} stating the type
     *                      of the object identified, a {@code confidence} specifying on a scale of 0-1 how confident
     *                      the neural net is that the identification is accurate, and a {@code location} containing
     *                      the coordinates for the {@code top},{@code left}, {@code bottom}, and {@code right} edges
     *                      of the bounding box for the object; and the bufferedImage (possibly labeled) that may
     *                      was returned.
     */
    public ResultHolder detect(final byte[] image, Date timestamp) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = YOLOClassifier.getInstance(threshold, anchorArray, frameSize).classifyImage(executeYOLOGraph(normalizedImage), labels);
            BufferedImage buffImage = ImageUtil.createImageFromBytes(image);
            
            // Saves an image every saveRate frames
            if (imageUtil.saveImage && ++frameCount >= saveRate) {
                String fileName = format.format(timestamp);
                if (lastFilename != null && lastFilename.contains(fileName)) {
                    fileName = fileName + "(" + ++fileCount + ").jpg";
                } else {
                    fileName = fileName + ".jpg";
                    fileCount = 0;
                }
                lastFilename = fileName;
                if (labelImage) {
                    buffImage = imageUtil.labelImage(buffImage, recognitions);
                }
                imageUtil.saveImage(buffImage, fileName);
                frameCount = 0;
            } else if (labelImage) {
                buffImage = imageUtil.labelImage(buffImage, recognitions);
                lastFilename = null;
            } else {
                lastFilename = null;
            }
            return returnJSON(recognitions, buffImage);
        }
    }
    
    /**
     * Detect objects on the given image
     * <br>Edited to return the results as a map and conditionally save the image
     * @param image         The image in jpeg format
     * @param outputDir     The directory to which the image should be written, (under current working directory unless
     *                      otherwise specified). If saveImage is null, no image is saved.
     * @param fileName      The name of the file to which the image is saved, with ".jpg" appended if necessary. If
     *                      {@code outputDir} is null and no default exists, no image is saved. If {@code fileName} is null
     *                      and {@code outputDir} is non-null, then the file is saved as
     *                      "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
     * @param vantiq        The Vantiq variable used to connect to the VANTIQ SDK. Either authenticated, or set to null.
     * @param uploadAsImage The boolean flag used to specify if images should be uploaded to VANTIQ as
     *                      Documents or VANTIQ Images
     * @param localLabelImage Boolean indicating whether this query should produce labels, overriding
     *                      the connector setting.
     * @return              ResultHolder containing a List of Maps, each of which has a {@code label} stating the type
     *                      of the object identified, a {@code confidence} specifying on a scale of 0-1 how confident
     *                      the neural net is that the identification is accurate, and a {@code location} containing
     *                      the coordinates for the {@code top},{@code left}, {@code bottom}, and {@code right} edges
     *                      of the bounding box for the object; and the bufferedImage (possibly labeled) that may
     *                      was returned.
     */
    public ResultHolder detect(final byte[] image, String outputDir, String fileName, Vantiq vantiq,
                               boolean uploadAsImage, boolean localLabelImage) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = YOLOClassifier.getInstance(threshold, anchorArray, frameSize).classifyImage(executeYOLOGraph(normalizedImage), labels);
            BufferedImage buffImage = ImageUtil.createImageFromBytes(image);
            
            // Saves an image if requested
            if (outputDir != null || vantiq != null || this.imageUtil.saveImage) {
                ImageUtil imageUtil = new ImageUtil();
                imageUtil.outputDir = outputDir;
                imageUtil.vantiq = vantiq;
                imageUtil.sourceName = sourceName;
                imageUtil.frameSize = frameSize;
                imageUtil.uploadAsImage = uploadAsImage;
                lastFilename = fileName;
                if (labelImage || localLabelImage) {
                    buffImage = imageUtil.labelImage(buffImage, recognitions);
                }
                imageUtil.saveImage(buffImage, fileName);
            } else if (labelImage || localLabelImage) {
                buffImage = imageUtil.labelImage(buffImage, recognitions);
                lastFilename = null;
            } else {
                lastFilename = null;
            }
            return returnJSON(recognitions, buffImage);
        }
    }
    
    /**
     * Parses the provided meta file, and converts it into a map which can be used
     * to retrieve the anchors and/or labels if necessary.
     * @param   metaFile    The location of the meta file to parse.
     * @throws JsonMappingException 
     * @throws JsonParseException 
     * @throws  IOException 
     * 
     */
    private void parseMetaFile(String metaFile) throws JsonParseException, JsonMappingException, IOException {
        byte[] metaFileData = Files.readAllBytes(Paths.get(metaFile));
        this.metaFileMap = new HashMap<String, Object>();
        ObjectMapper objectMapper = new ObjectMapper();
        this.metaFileMap = objectMapper.readValue(metaFileData, HashMap.class);
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
        g.importGraphDef(graph_def);
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
                            graphBuilder.constant("size", new int[]{frameSize, frameSize})),
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
            float[] outputTensor = new float[YOLOClassifier.getInstance(threshold, anchorArray, frameSize).getOutputSizeByShape(result)];
            FloatBuffer floatBuffer = FloatBuffer.wrap(outputTensor);
            result.writeTo(floatBuffer);
            return outputTensor;
        }
    }
    
    /**
     * ADDED BY NAMIR - Used to convert recognitions to JSON
     * @param recognitions
     * @return ResultHolder including the list of recognitions & the resulting image.
     */
    private ResultHolder returnJSON(final List<Recognition> recognitions, BufferedImage buffImage) {
        List<Map<String, ?>> jsonRecognitions = new ArrayList<>();
        for (Recognition recognition : recognitions) {
        	HashMap map = new HashMap();
        	map.put("label", recognition.getTitle());
        	map.put("confidence", recognition.getConfidence());
        	
        	float scaleX = (float) buffImage.getWidth() / (float) frameSize;
        	float scaleY = (float) buffImage.getHeight() / (float) frameSize;
        	
        	HashMap location = new HashMap();
        	location.put("left", recognition.getScaledLocation(scaleX, scaleY).getLeft());
            location.put("top", recognition.getScaledLocation(scaleX, scaleY).getTop());
            location.put("right", recognition.getScaledLocation(scaleX, scaleY).getRight());
            location.put("bottom", recognition.getScaledLocation(scaleX, scaleY).getBottom());
            location.put("centerX", recognition.getScaledLocation(scaleX, scaleY).getCenterX());
            location.put("centerY", recognition.getScaledLocation(scaleX, scaleY).getCenterY());

            map.put("location", location);
        	
        	jsonRecognitions.add(map);
        	
        	LOGGER.info("{}", map);
        }

        ResultHolder res = new ResultHolder();
        res.results = jsonRecognitions;
        res.image = ImageUtil.getBytesForImage(buffImage);
        
        return res;
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
        YOLOClassifier.getInstance(threshold, anchorArray, frameSize).close();
    }
}
