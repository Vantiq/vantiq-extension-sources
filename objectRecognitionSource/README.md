## Repository Contents

*   [ObjectRecognitionMain](#objRecMain) -- The main function for the program. Connects to sources as specified in a configuration file.
*   [ObjectRecognitionCore](#core) -- Controls the connection to a source, the input images, and the output.
*   [ObjectRecognitionConfigHandler](#srcConfig) -- Sets up the neural net and image retriever based on the source's configuration document.
*   ObjectRecognitionQueryHandler -- Sends data back in response to a query, if the source is configured to handle queries.
*   [NeuralNetInterface](#netInterface) -- An interface that allows other neural nets to be more easily integrated without changes to the rest of the code.
    *   [YoloProcessor](#yoloNet) -- An implementation of the [You Only Look Once](https://pjreddie.com/darknet/yolo/) (YOLO) object detection software using Java Tensorflow.
*   [ImageRetrieverInterface](#retrieveInterface) -- An interface that allows different image retrieval mechanisms to be more easily integrated without changes to the rest of the code.
    *   [CameraRetriever](#cameraRet) -- Retrieves images from a directly connected camera using OpenCV.
    *   [FileRetriever](#fileRet) -- Retrieves images from disk.

## How to Run the Program<a name="objRecMain" id="objRecMain"></a>

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew objectRecognitionSource:assemble` or `.\gradlew objectRecognitionSource:assemble` depending on the OS.
3.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/distributions`. The zip and tar files both contain the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
5.  Run either `<install location>/objectRecognitionSource/bin/objectRecognitionSource` with a local server.config file or specifying the [server config file](#serverConfig) as the first argument.

To change the logging settings, edit `<install location>/udpSource/logback.xml`. Here is its [documentation](https://logback.qos.ch/manual/configuration.html). The logger names for each class is the class's fully qualified class name, e.g. "io.vantiq.extjsdk.ExtensionWebSocketClient".

## Server Config File<a name="serverConfig" id="serverConfig"></a>

The server config file is written as `property=value`, with each property on its own line.

### Vantiq Options
*   authToken: Required. The authentication token to connect with. These can be obtained from the namespace admin.*   sources: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be removed when read.*   targetServer: Optional. The Vantiq server hosting the sources. Defaults to "dev.vantiq.com"

### Local Options*   modelDirectory: Optional. The directory in which the files for your neural networks will be. Defaults to the working directory.

## Running Inside Your Own Code<a name="core" id="core"></a>

The ObjectRecognitionCore class handles all the source related functionality. If you wish to run object recognition sources inside your own code, then it is what you will use.

### Adding to your Library

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
    *   If you know that you will not use specific classes in the *.neuralNet or *.imageRetriever packagesyou can remove them now.
2.  Run `./gradlew objectRecognitionSource:assemble` or `.\gradlew objectRecognitionSource:assemble` depending on the OS.
3.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/libs` and copy `objectRecognitionSource.jar` into your project.
4.  Add the dependencies found in `<repo location>/vantiq-extension-sources/objectRecognitionSource/build.gradle` to your project.
    *   The gradle build file specifies every dependency that is used only in neural net or image retriever implementations. If you removed any classes in step 1, use the comments to identify which dependencies are no longer needed.

### Using in Your Code

The ObjectRecognitionCore class has four public functions. The constructor takes in the same arguments passed through the [server config file](#serverConfig), the only difference being that only a single source is expected. `start()` sets everything up and tries to connect to the server, returning true if it works and false if it fails. If you want to use the source purely as defined elsewhere in this document, then these are the only two functions you need.
If you want to obtain images and send them in addition to the data that the source will send normally, the other two functions will be needed. `retrieveImage()` attempts to use the source's image retriever to obtain a jpeg encoded image, returning null if it failed and stopping the Core if it failed unrecoverably. `retrieveImage(Map)` acts identically except that options can be specified depending on the image retriever implementation. `sendDataFromImage(byte[])` takes the image and attempts to process it with the source's neural net then send the results to the source. If the image is null or an error occurs then no data is sent to the source, and if neural net failed unrecoverably then the Core is stopped as well.

## Source Configuration Document<a name="srcConfig" id="srcConfig"></a>

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
    2.  The short name of one of the standard implementations, i.e. one of "[file](#fileRet)", "[camera](#camRet)", or "[default](#defaultRet)".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultRetriever" in the `io.vantiq.objectRecognition.imageRetriever` package. This implementation is not provided, and must be written by the user.

### Options Available for Neural Net

Most of the options required for neuralNet are dependent on the specific implementation of [NeuralNetInterface](#netInterface). The ones that are the same across all implementations are:
*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of NeuralNetInterface, e.g. "io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor".
    2.  The short name of one of the standard implementations, i.e. one of "[yolo](#yoloNet)" or "[default](#defaultNet)".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultProcessor" in the `io.vantiq.objectRecognition.neuralNet` package. This implementation is not provided, and must be written by the user.

## Image Retriever Interface<a name="retrieveInterface" id="retrieveInterface"></a>

This is an interface that returns a jpeg encoded image. Settings may or may not differ for periodic messages versus Query responses. There are two implementations included in the standard package.

### Default Retriever<a name="defaultRet" id="defaultRet"></a>

This is a user written implementation that acts as the default if no image retriever type is specified. If no such implementation is included then type must be specified for the source to function. It must be named "DefaultRetriever" and be placed in the `io.vantiq.extsrc.objectRecognition.imageRetriever` package.

### Camera Retriever<a name="camRet" id="camRet"></a>

This implementation uses OpenCV to capture images from a camera connected directly to a computer. There are no options for Query messages, it is completely setup upon configuration. It has the following options:
*   camera: The index of the camera that will capture images, starting at 0.

### File Retriever<a name="fileRet" id="fileRet"></a>

This implementation reads files from the disk. The options for Query messages are identical to the options for configuration, except that the configuration file will be used whenever a Query appears with no file specified. The options are:
*   fileLocation: The location of the file to be read. The file does not need to exist, but attempts to access a non-existent file will send an empty message in response to Queries and no message for periodic requests.

## Neural Net Interface<a name="netInterface" id="netInterface"></a>

This is a user written interface that interprets a jpeg encoded image and returns the results in a List of JSON-friendly Maps. Settings are set purely through the configuration document.

### Default Retriever<a name="defaultNet" id="defaultNet"></a>

This is a user written implementation that acts as the default if no neural net type is specified. If no such implementation is included then type must be specified for the source to function. It must be named "DefaultProcessor" and be placed in the `io.vantiq.extsrc.objectRecognition.neuralNet` package.

### Yolo Processor<a name="yoloNet" id="yoloNet"></a>

This is a TensorFlow implementation of YOLO. It returns a List of Maps, each of which has a `label` stating the type of the object identifiead, a `confidence` specifying on a scale of 0-1 how confident the neural net is that the identification is accurate, and a `location` containing the coordinates for the `top`,`left`, `bottom`, and `right` edges of the bounding box for the object.

