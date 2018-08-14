## Repository Contents

*   [ObjectRecognitionMain](#objRecMain) -- The main function for the program. Connects to sources as specified in a configuration file.
*   [ObjectRecognitionCore] -- Controls the connection to a source, the input images, and the output.
*   [ObjectRecognitionConfigHandler] -- Sets up the neural net and image retriever based on the source's configuration document.
*   [ObjectRecognitionQueryHandler] -- Sends data back in response to a query, if the source is configured to handle queries.
*   [NeuralNetInterface] -- An interface that allows other neural nets to be more easily integrated without changes to the rest of the code.
    *   [YoloProcessor] -- An implementation of the [You Only Look Once](https://pjreddie.com/darknet/yolo/) (YOLO) object detection software using Java Tensorflow.
    *   [DarkflowProcessor] -- An implementation of YOLO using the [Darkflow](https://github.com/thtrieu/darkflow) Python implementation.
*   [ImageRetrieverInterface](#retrieveInterface) -- An interface that allows different image retrieval mechanisms to be more easily integrated without changes to the rest of the code.
    *   [CameraRetriever] -- Retrieves images from a directly connected camera using OpenCV.
    *   [FileRetriever] -- Retrieves images from disk.

## How to Run the Program<a name="objRecMain" id="objRecMain"></a>

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew objectRecognitionSource:assembleble` or `.\gradlew objectRecognitionSource:assembleble` depending on the OS.
3.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/distributions`. The zip and tar files both contain the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
5.  Run either `<install location>/objectRecognitionSource/bin/objectRecognitionSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

To change the logging settings, edit `<install location>/udpSource/logback.xml`. Here is its [documentation](https://logback.qos.ch/manual/configuration.html). The logger names for each class is the class's fully qualified class name, e.g. "io.vantiq.extjsdk.ExtensionWebSocketClient".

## Server Config File<a name="serverConfig" id="serverConfig"></a>

The server config file is written as `property=value`, with each property on its own line.

### Vantiq Options
*   authToken: Required. The authentication token to connect with. These can be obtained from the namespace admin.*   sources: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be removed when read.*   targetServer: Optional. The Vantiq server hosting the sources. Defaults to "dev.vantiq.com"

### Local Options*   modelDirectory: Optional. The directory in which the files for your neural networks will be. Defaults to the working directory.

## Source Configuration Document<a name="udpConfig" id="udpConfig"></a>

The Configuration document looks as below:

    {
        extSrcConfig:{
           type: "objectRecognition",
            general: {
                <general options>
            },
            dataSource: {
                <data source options>
            },
            neuralNet: {
                <neural net options>
            }
        }
    }

### Options Available for General

*   pollRate: Required. This indicates how often an image should be captured. A positive number represents the number of milliseconds between captures. If the specified time is less than the amount of time it takes to process the image then images will be taken as soon as the previous finishes. If this is set to 0, the next image will be captured as soon as the previous is sent. If this is set to a negative number, then images will be captured and processed only when a Query message is received.

### Options Available for Data Source

Most of the options required for dataSource are dependent on the specific implementation of [ImageRetrieverInterface](#retrieveInterface). The ones that are the same across all implementations are:
*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of ImageRetrieverInterface, e.g. "io.vantiq.extsrc.objectRecognition.imageRetriever.CameraRetriever".
    2.  The short name of one of the standard implementations, i.e. one of "[file](#fileRet)", "[camera](#camRet)", or "[default]".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultRetriever" in the `io.vantiq.objectRecognition.imageRetriever` package. This implementation is not provided, and must be written by the user.

### Options Available for Neural Net

Most of the options required for neuralNet are dependent on the specific implementation of [NeuralNetInterface]. The ones that are the same across all implementations are:
*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of NeuralNetInterface, e.g. "io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor".
    2.  The short name of one of the standard implementations, i.e. one of "[yolo]", "[darkflow]", or "[default]".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultProcessor" in the `io.vantiq.objectRecognition.neuralNet` package. This implementation is not provided, and must be written by the user.

## Image Retriever Interface<a name="retrieveInterface" id="retrieveInterface"></a>

This is an interface that returns a jpeg encoded image. Settings may or may not differ for periodic messages versus Query responses. There are two implementations included in the standard package.

### Default Retriever

This is a user written implementation that acts as the default if no image retriever type is specified. If no such implementation is included then type must be specified for the source to function. It must be named "DefaultRetriever" and be placed in the `io.vantiq.extsrc.objectRecognition.imageRetriever` package.

### Camera Retriever<a name="camRet" id="camRet"></a>

This implementation uses OpenCV to capture images from a camera connected directly to a computer. There are no options for Query messages, it is completely setup upon configuration. It has the following options:
*   camera: The index of the camera that will capture images, starting at 0.

### File Retriever<a name="fileRet" id="fileRet"></a>

This implementation reads files from the disk. The options for Query messages are identical to the options for configuration, except that the configuration file will be used whenever a Query appears with no file specified. The options are:
*   fileLocation: The location of the file to be read. The file does not need to exist, but attempts to access a non-existent file will send an empty message in response to Queries and no message for periodic requests.

## Neural Net Interface<a name="netInterface" id="netInterface"></a>

This is a user written interface that interprets a jpeg encoded image and returns the results in a List of JSON-friendly Maps. Settings are set purely through the configuration document.

### Default Retriever

This is a user written implementation that acts as the default if no neural net type is specified. If no such implementation is included then type must be specified for the source to function. It must be named "DefaultProcessor" and be placed in the `io.vantiq.extsrc.objectRecognition.neuralNet` package.

### Yolo Processor

This is a TensorFlow implementation of YOLO. It returns a List of Maps, each of which has a `label` stating the type of the object identifiead, a `confidence` specifying on a scale of 0-1 how confident the neural net is that the identification is accurate, and a `location` containing the coordinates for the `top`,`left`, `bottom`, and `right` edges of the bounding box for the object.