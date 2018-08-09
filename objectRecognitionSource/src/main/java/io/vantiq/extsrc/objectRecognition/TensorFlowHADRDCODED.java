package io.vantiq.extsrc.objectRecognition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jep.Jep;
import jep.JepException;
import jep.NDArray;
//import org.tensorflow.Graph;
//import org.tensorflow.Tensor;

// TODO Replace with generalized version
// This code is simply testing to figure out how to use TensorFlow
// Using https://github.com/tensorflow/tensorflow/blob/r1.9/tensorflow/java/src/main/java/org/tensorflow/examples/LabelImage.java
// as a basis

public class TensorFlowHADRDCODED {
    static Logger log = LoggerFactory.getLogger(TensorFlowHADRDCODED.class);
    static Runtime runtime = Runtime.getRuntime();
    static ObjectMapper mapper = new ObjectMapper();
    
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.library.path"));
        nu.pattern.OpenCV.loadShared();
        
        jepMain(args);
        //runtimeMain(args);
        //tensorFlowMain(args);
        
        // Trying 
    }
    
    public static void runtimeMain(String[] args) {
        //try {
            String modelName = "cfg/yolo.cfg";
            String weightsLocation = "yolov2.weights";
            double threshold = 0.1;
            String imageLocation = "./index.jpg";
            
            /*Process p1 = runtime.exec("python ../../darkflow-master/flow");
            BufferedReader resultsStream1 = new BufferedReader(new InputStreamReader(p1.getInputStream()));
            BufferedReader errorStream1 = new BufferedReader(new InputStreamReader(p1.getErrorStream()));
            while (!resultsStream1.ready() && !errorStream1.ready()) ;
            System.out.println(resultsStream1.readLine());
            System.out.println(errorStream1.readLine());*/
            
            /*Process p = runtime.exec("python flow --model " + modelName + " --load " + weightsLocation + 
                   " --demo " + imageLocation + " --json");*/
            //
            /*ProcessBuilder = pb.
            Process p = runtime.exec("python flow --model " + modelName + 
                    " --load " + weightsLocation + 
                    " --demo " + imageLocation + " --json");
            BufferedReader resultsStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while (true) {
            while (!resultsStream.ready()&& !errorStream.ready()) ;
            System.out.println(resultsStream.readLine());
            System.out.println("ERROR:" + errorStream.readLine());
            }*/
            /*runtime.exec("python");
            runtime.exec("from darkflow.net.build import TFNet");
            runtime.exec("import cv2");
            // This is for darknet version. use pbLoad and metaLoad for tensorflow documents
            runtime.exec("options = {\"model\":\"" + modelName + "\", "
                    + "\"load\":\"" + weightsLocation + "\", "
                    + "\"threshold\":" + threshold + "}");
            runtime.exec("tfnet = TFNet(options)");
            runtime.exec("img = cv2.imread(\"" + imageLocation + "\")");
            runtime.exec("result = tfnet.return_predict(img)");
            Process p = runtime.exec("print(result)");
            BufferedReader resultsStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
            System.out.println(resultsStream);
            
            
            runtime.exec("exit()");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            try {
                runtime.exec("exit()");
            } catch (IOException e2) {
                // TODO
            }
        }*/
    }
    
    public static void jepMain(String[] args) {
        String modelName = "models/cfg/yolo.cfg";
        String weightsLocation = "models/yolov2.weights";
        double threshold = 0.1;
        String imageLocation = "./index.jpg";

        VideoCapture capture = new VideoCapture();
        capture.open(0, 0);
        Mat mat = new Mat();
        while (!capture.isOpened()) ;
        capture.read(mat);
        capture.release();
        
        String darkflowLocation = "../../darkflow-master";
        try (Jep jep = new Jep()) {
            jep.eval("import sys");
            
            String s = (String)jep.getValue("sys.version");
            System.out.println(s);
            
            // Trying to quickly convert stuff
            byte[] b = new byte[mat.channels()*mat.rows()*mat.cols()];
            mat.get(0, 0, b);
            NDArray<byte[]> ndBytes= new NDArray(b, mat.rows(),mat.cols() , mat.channels());
            mat.release();
            jep.set("img", ndBytes);
            jep.eval("img = img.astype('uint8')");
            
            // jep.eval("sys.path.insert(0,'" + darkflowLocation + "')");
            jep.eval("from darkflow.net.build import TFNet");
         // This is for darknet data. use pbLoad and metaLoad for tensorflow documents
            jep.eval("options = {\"model\":\"" + modelName + "\", "
                    + "\"load\":\"" + weightsLocation + "\", "
                    + "\"threshold\":" + threshold + "}");
            jep.eval("tfnet = TFNet(options)");
            jep.eval("result = tfnet.return_predict(img)");
            Object bytes = jep.getValue("result"); // gets a List
            System.out.println("Result = \n"  + bytes.toString() + "\n\n");
            jep.eval("exit()");
            //List<Object> results = mapper.readValue(bytes, List.class);
            //System.out.println(results);
        } catch (JepException e) {
            log.error("Jep failed", e);
        } //catch (IOException e) {
            // TODO Auto-generated catch block
            //log.error("Mapper", e);
        //}
        
    }
    
    public static void tensorFlowMain(String[] args) {
        String modelDir = "directory";
        String image = "img.jpg";
        
        
        
        byte[] graphDef;
        
        try {
            File graphDefFile = new File("directory/model.pb");
            graphDef = Files.readAllBytes(graphDefFile.toPath());
        } catch (IOException e) {
            log.error("Could not read expected file", e);
            return;
        }// Read in data as bytes from a .pb file
        List<String> labels;// read in labels
        // read in image as bytes (should be jpg)
        
        // Should be able to run the yolo config directly with tensorFlow
    }
    
    /*public static Tensor<Float> tensorFromImage(byte[] image) {
        try (Graph g = new Graph()) {
            // TODO GraphBuilder is an internal class built for LabelImage
            //GraphBuilder b = new GraphBuilder(g);
            // OperationBuilder b = g.opBuilder(type, name)
            
            // Model specific setup;
            
            //final Output<>
        }
        return null;
    }*/
}
