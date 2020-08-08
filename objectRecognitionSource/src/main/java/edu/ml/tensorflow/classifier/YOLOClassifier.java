package edu.ml.tensorflow.classifier;

import edu.ml.tensorflow.model.BoundingBox;
import edu.ml.tensorflow.model.BoxPosition;
import edu.ml.tensorflow.model.Recognition;
import edu.ml.tensorflow.util.math.ArgMax;
import edu.ml.tensorflow.util.math.SoftMax;
import org.apache.commons.math3.analysis.function.Sigmoid;
import org.tensorflow.Tensor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * YOLOClassifier class implemented in Java by using the TensorFlow Java API
 * I also used this class in my android sample application here: https://github.com/szaza/android-yolo-v2
 */
public class YOLOClassifier {
    private final static float OVERLAP_THRESHOLD = 0.5f;
    private final static int MAX_RECOGNIZED_CLASSES = 24;
    public final static int NUMBER_OF_BOUNDING_BOX = 5;
    
    // Default anchor values used to properly label recognitions on original image
    private final static double[] ANCHORS_FOR_PROVIDED_MODEL = {0.57273, 0.677385, 1.87446, 2.06253, 3.33843, 5.47434, 7.88282, 3.52778, 9.77052, 9.16828};
    
    // anchors[] is an array of NUMBER_OF_BOUNDING_BOX pairs of numbers (2 * NUMBER_OF_BOUNDING_BOX numbers), specifying
    // the most common shapes of objects in the training data (within the YOLO 13x13 grid). It is used to construct the
    // bounding boxes for detected objects.
    private static double anchors[] = ANCHORS_FOR_PROVIDED_MODEL;
    
    private static float threshold;
    private static YOLOClassifier classifier;
    private static int gridSize;

    private YOLOClassifier() {}

    public static YOLOClassifier getInstance(float thresh, double[] anchorArray, int frameSize) {
        if (classifier == null) {
            classifier = new YOLOClassifier();
            threshold = thresh;
            gridSize = frameSize / 32;
            if (anchorArray != null) {
                anchors = anchorArray;
            }
        }
        
        return  classifier;
    }

    /**
     * Gets the number of classes based on the tensor shape
     *
     * @param result - the tensorflow output
     * @return the number of classes
     */
    public int getOutputSizeByShape(Tensor<Float> result) {
        return (int) (result.shape()[3] * Math.pow(gridSize,2));
    }

    /**
     * It classifies the object/objects on the image
     *
     * @param tensorFlowOutput output from the TensorFlow, it is a 13x13x((num_class +1) * 5) tensor
     * 125 = (numClass +  Tx, Ty, Tw, Th, To) * 5 - cause we have 5 boxes per each cell
     * @param labels a string vector with the labels
     * @return a list of recognition objects
     */
    public List<Recognition> classifyImage(final float[] tensorFlowOutput, final List<String> labels) {
        int numClass = (int) (tensorFlowOutput.length / (Math.pow(gridSize,2) * NUMBER_OF_BOUNDING_BOX) - 5);
        BoundingBox[][][] boundingBoxPerCell = new BoundingBox[gridSize][gridSize][NUMBER_OF_BOUNDING_BOX];
        PriorityQueue<Recognition> priorityQueue = new PriorityQueue(MAX_RECOGNIZED_CLASSES, new RecognitionComparator());

        int offset = 0;
        for (int cy=0; cy<gridSize; cy++) {        // gridSize * gridSize cells
            for (int cx=0; cx<gridSize; cx++) {
                for (int b=0; b<NUMBER_OF_BOUNDING_BOX; b++) {   // 5 bounding boxes per each cell
                    boundingBoxPerCell[cx][cy][b] = getModel(tensorFlowOutput, cx, cy, b, numClass, offset);
                    calculateTopPredictions(boundingBoxPerCell[cx][cy][b], priorityQueue, labels);
                    offset = offset + numClass + 5;
                }
            }
        }

        return getRecognition(priorityQueue);
    }

    private BoundingBox getModel(final float[] tensorFlowOutput, int cx, int cy, int b, int numClass, int offset) {
        BoundingBox model = new BoundingBox();
        Sigmoid sigmoid = new Sigmoid();
        model.setX((cx + sigmoid.value(tensorFlowOutput[offset])) * 32);
        model.setY((cy + sigmoid.value(tensorFlowOutput[offset + 1])) * 32);
        model.setWidth(Math.exp(tensorFlowOutput[offset + 2]) * anchors[2 * b] * 32);
        model.setHeight(Math.exp(tensorFlowOutput[offset + 3]) * anchors[2 * b + 1] * 32);
        model.setConfidence(sigmoid.value(tensorFlowOutput[offset + 4]));

        model.setClasses(new double[numClass]);

        for (int probIndex=0; probIndex<numClass; probIndex++) {
            model.getClasses()[probIndex] = tensorFlowOutput[probIndex + offset + 5];
        }

        return model;
    }

    private void calculateTopPredictions(final BoundingBox boundingBox, final PriorityQueue<Recognition> predictionQueue,
                                         final List<String> labels) {
        for (int i=0; i<boundingBox.getClasses().length; i++) {
            ArgMax.Result argMax = new ArgMax(new SoftMax(boundingBox.getClasses()).getValue()).getResult();
            double confidenceInClass = argMax.getMaxValue() * boundingBox.getConfidence();
            if (confidenceInClass > threshold) {
                predictionQueue.add(new Recognition(argMax.getIndex(), labels.get(argMax.getIndex()), (float) confidenceInClass,
                        /* BoxPosition() now takes center points */
                        new BoxPosition((float) boundingBox.getX(),
                                (float) boundingBox.getY(),
                                (float) boundingBox.getWidth(),
                                (float) boundingBox.getHeight())));
            }
        }
    }

    private List<Recognition> getRecognition(final PriorityQueue<Recognition> priorityQueue) {
        List<Recognition> recognitions = new ArrayList();

        if (!priorityQueue.isEmpty()) {
            // Best recognition
            Recognition bestRecognition = priorityQueue.poll();
            recognitions.add(bestRecognition);
            
            while (!priorityQueue.isEmpty()) {
                Recognition recognition = priorityQueue.poll();
                boolean overlaps = false;
                for (Recognition previousRecognition : recognitions) {
                    overlaps = overlaps || (getIntersectionProportion(previousRecognition.getLocation(),
                            recognition.getLocation()) > OVERLAP_THRESHOLD);
                }

                if (!overlaps) {
                    recognitions.add(recognition);
                }
            }
        }

        return recognitions;
    }

    private float getIntersectionProportion(BoxPosition primaryShape, BoxPosition secondaryShape) {
        if (overlaps(primaryShape, secondaryShape)) {
            float intersectionSurface = Math.max(0, Math.min(primaryShape.getRight(), secondaryShape.getRight()) - Math.max(primaryShape.getLeft(), secondaryShape.getLeft())) *
                    Math.max(0, Math.min(primaryShape.getBottom(), secondaryShape.getBottom()) - Math.max(primaryShape.getTop(), secondaryShape.getTop()));

            float surfacePrimary = Math.abs(primaryShape.getRight() - primaryShape.getLeft()) * Math.abs(primaryShape.getBottom() - primaryShape.getTop());

            return intersectionSurface / surfacePrimary;
        }

        return 0f;

    }

    private boolean overlaps(BoxPosition primary, BoxPosition secondary) {
        return primary.getLeft() < secondary.getRight() && primary.getRight() > secondary.getLeft()
                && primary.getTop() < secondary.getBottom() && primary.getBottom() > secondary.getTop();
    }

    // Intentionally reversed to put high confidence at the head of the queue.
    private class RecognitionComparator implements Comparator<Recognition> {
        @Override
        public int compare(final Recognition recognition1, final Recognition recognition2) {
            return Float.compare(recognition2.getConfidence(), recognition1.getConfidence());
        }
    }
    
    public void close() {
        classifier = null;
    }
}

