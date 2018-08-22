## Repository Contents

*   [ObjectRecognitionMain](#objRecMain) -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   [ObjectRecognitionCore](#core) -- Controls the connection to a source, the input images, and the output.
*   [ObjectRecognitionConfigHandler](#srcConfig) -- Sets up the neural net and image retriever based on the source's
    configuration document.
*   [NeuralNetInterface](#netInterface) -- An interface that allows other neural nets to be more easily integrated
    without changes to the rest of the code.
    *   [YoloProcessor](#yoloNet) -- An implementation of the [You Only Look Once](https://pjreddie.com/darknet/yolo/)
        (YOLO) object detection software using Java Tensorflow.
*   [ImageRetrieverInterface](#retrieveInterface) -- An interface that allows different image retrieval mechanisms to be
        more easily integrated without changes to the rest of the code.
    *   [CameraRetriever](#cameraRet) -- Retrieves images from a directly connected camera using OpenCV.
    *   [FileRetriever](#fileRet) -- Retrieves images from disk.

## How to Run the Program<a name="objRecMain" id="objRecMain"></a>

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
2.  Run `./gradlew objectRecognitionSource:assemble`.
3.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/distributions`. The zip and tar
    files both contain the same files, so choose whichever you prefer.
4.  Uncompress the file in the location that you would like to install the program.
5.  Run `<install location>/objectRecognitionSource/bin/objectRecognitionSource` with a local server.config file or
    specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit `<install location>/objectRecognitionSource/logConfig/log4j2.xml`. Here is
its [documentation](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger names for each class is
the class's fully qualified class name, e.g. "io.vantiq.extjsdk.ExtensionWebSocketClient".  

To edit the logging for an IDE, change `<repo location>/objectRecognitionSource/src/main/resources/log4j2.xml`. Changes
to this will be included in future distributions produced through gradle.

## Server Config File<a name="serverConfig" id="serverConfig"></a>

The server config file is written as `property=value`, with each property on its
own line.

### Vantiq Options
*   authToken: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   sources: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   targetServer: Optional. The Vantiq server hosting the sources. Defaults to "dev.vantiq.com"

### Local Options
*   modelDirectory: Optional. The directory in which the files for your neural networks will be. Defaults to the current
    working directory.

## Running Inside Your Own Code<a name="core" id="core"></a>

The ObjectRecognitionCore class handles all the source related functionality. If you wish to run object recognition
sources inside your own code, then it is what you will use.

### Adding to your Library

1.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
    *   If you know that you will not use specific classes in the *.neuralNet or *.imageRetriever packages you can
        remove them now.
2.  Run `./gradlew objectRecognitionSource:assemble` or `.\gradlew objectRecognitionSource:assemble` depending on the
    OS.
3.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/libs` and copy
    `objectRecognitionSource.jar` into your project.
4.  Add the dependencies found in `<repo location>/vantiq-extension-sources/objectRecognitionSource/build.gradle` to
    your project.
    *   The gradle build file specifies every dependency that is used only in neural net or image retriever
        implementations. If you removed any classes in step 1, use the comments to identify which dependencies are
        no longer needed.

### Using in Your Code

The ObjectRecognitionCore class has four public functions. The constructor takes in the same arguments passed through
the [server config file](#serverConfig), the only difference being that only a single source is expected. `start()` sets
everything up and tries to connect to the server, returning true if it works and false if it fails. If you want to use
the source purely as defined elsewhere in this document, then these are the only two functions you need.  

If you want to obtain images and send them in addition to the data that the source will send normally, the other two
functions will be needed. `retrieveImage()` attempts to use the source's image retriever to obtain a jpeg encoded image,
returning null if it failed and stopping the Core if it failed unrecoverably. `sendDataFromImage(byte[])` takes the
image and attempts to process it with the source's neural net then send the results to the source. If the image is null
or an error occurs then no data is sent to the source, and if the neural net failed unrecoverably then the Core is
stopped as well.

## Source Configuration Document<a name="srcConfig" id="srcConfig"></a>

The Configuration document looks as below:

    {
        objRecConfig:{,
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

*   pollRate: Required. This indicates how often an image should be captured. A positive number represents the number of
milliseconds between captures. If the specified time is less than the amount of time it takes to process the image then
images will be taken as soon as the previous finishes. If this is set to 0, the next image will be captured as soon as
the previous is sent. If this is set to a negative number, then images will be captured and processed only when a Query
message is received.

### Options Available for Data Source

Most of the options required for dataSource are dependent on the specific implementation of
[ImageRetrieverInterface](#retrieveInterface). The ones that are the same across all implementations
are:
*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of ImageRetrieverInterface, e.g.
        "io.vantiq.extsrc.objectRecognition.imageRetriever.CameraRetriever".
    2.  The short name of one of the standard implementations, i.e. one of "[file](#fileRet)", "[camera](#camRet)",
        or "[default](#defaultRet)".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultRetriever" in
        the `io.vantiq.objectRecognition.imageRetriever` package. This implementation is not provided, and must be
        written by the user.

### Options Available for Neural Net

Most of the options required for neuralNet are dependent on the specific implementation of [NeuralNetInterface](#netInterface). The ones that are the same across all implementations are:
*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of NeuralNetInterface, e.g.
        "io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor".
    2.  The short name of one of the standard implementations, i.e. one of "[yolo](#yoloNet)" or
        "[default](#defaultNet)".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultProcessor" in
        the `io.vantiq.objectRecognition.neuralNet` package. This implementation is not provided, and must be written by
        the user.

## Queries

Options can be specified through the WITH clause for both the neuralNet and dataSource. The options for Queries are
implementation dependent, and the available options for the standard implementations can be found in their respective
sections. Since it is possible for both the data source and the neural net to use options with the same name, the
standard implementations prepend the options with DS and NN for all queries, i.e. if you want to Query using the
`fileLocation` option for a data source you would set the `DSfileLocation` property in the WITH clause. This may or may
not apply for non-standard implementations, though developers are advised to follow this rule for consistency's sake.

## Image Retriever Interface<a name="retrieveInterface" id="retrieveInterface"></a>

This is an interface that returns a jpeg encoded image. Settings may or may not differ for periodic messages versus
Query responses. There are two implementations included in the standard package.

### Default Retriever<a name="defaultRet" id="defaultRet"></a>

This is a user written implementation that acts as the default if no image retriever type is specified. If no such
implementation is included then `type` must be specified for the source to function. It must be named "DefaultRetriever"
and be placed in the `io.vantiq.extsrc.objectRecognition.imageRetriever` package. It can be added either by adding the
implementation to `<repo location>/objectRecognitionSource/src/main/io/vantiq/extsrc/objectRecognition/imageRetriever`
before running `./gradlew assemble` or by inserting the class as a jar into
`<install location>/objectRecognitionSource/lib` and adding it to the `CLASSPATH` in
`<install location>/objectRecognitionSource/bin/objectRecognitionSource` and
`<install location>/objectRecognitionSource/bin/objectRecognitionSource.bat`.


### Camera Retriever<a name="camRet" id="camRet"></a>

This implementation uses OpenCV to capture images from a camera connected directly to a computer. An error is thrown
whenever an image cannot be read successfully. Fatal errors are thrown only when the camera is inaccessible in non-Query
mode.  

The options are as follows. Remember to prepend "DS" when using an option in a Query.
*   camera: Required for Config, optional for Query. The index of the camera to read images from. For queries, defaults
    to the camera specified in the Config.

### File Retriever<a name="fileRet" id="fileRet"></a>

This implementation reads files from the disk using OpenCV for the videos. `fileLocation` must be a valid file at
initialization. The initial image file can be replaced while the source is running, but the video cannot. For Queries,
new files can be specified using the `fileLocation` and `fileExtension` options, and defaults to the initial file
if `fileLocation` is not set. Queried videos can specify which frame of the video to access using the `targetFrame`
option.  

Errors are thrown whenever an image or video frame cannot be read. Fatal errors are thrown only when a video finishes
being read when the source is not setup for to receive Queries.
The options are as follows. Remember to prepend "DS" when using an option in a Query.
*   fileLocation: Required for Config, Optional for Query. The location of the file to be read. For Config
where `fileExtension` is "mov", the file must exist at initialization. If this is not set at Config and the source is
not configured for Queries, then the source will open but the first attempt to retrieve will kill the source. For
Queries, defaults to the configured file or returns an error if there was none.
*   fileExtension: Optional. Config and Query. The type of file it is, "mov" for video files, "img" for image files.
    Defaults to image files.
*   fps: Optional. Config only. Requires `fileExtension` be "mov". How many frames to retrieve for every second in the
    video. Rounds up the result when calculating the number of frames to move each capture. Non-positive numbers
    revert to default. Default is every frame.
*   targetFrame: Optional. Query only. Requires `fileExtension` be "mov". The frame in the video that you would like to
    access, with the first being 0. Exceptions will be thrown if this targets an invalid frame, i.e. negative or
    beyond the video's frame count. Mutually exclusive with targetTime. Defaults to 0.
*   targetTime: Optional. Query only. Requires `fileExtension` be "mov". The second in the video that you would like to
    access, with the first frame being at second 0. Exceptions will be thrown if this targets an invalid frame, i.e.
    negative or beyond the video's frame count. Non-integer values are allowed. Mutually exclusive with targetFrame.
Defaults to 0.

#### Example: Reading an Entire Video Through Queries
If you want to read a video file from a source using Queries, this method will work.
```
var frameResults = []
var frameSkip = 72
var maxCaptures = 100
var myVideoLocation = "movie.mov"
try {
    // We expect the error received when the bounds are overrun to stop the loop,
    // so maxCaptures can be higher than the expected number of captures
    FOR (frame in range(0, maxCaptures - 1)) {
        SELECT * FROM VideoSource AS results WITH 
                        DSfileLocation:myVideoLocation,
                        DSfileExtension:"mov",
                        DStargetFrame:frame*frameSkip
        frameResults.push(results)
        frame += frameSkip
    }
} catch (error) {
    // This indicates to us that the video is done receiving
    if (frameResults.length == 0) {
        // This means nothing was received, indicating that the error was a serious problem
        // and not just the video's end
        // We'll throw the exception again, so that it is received correctly
        exception(error.code, error.message)
    } else if ( !(error.params[0].equals("Requested frame outside valid bounds")) ) {
        // AS OF WRITING (8/17/18), the parameter 0 of the query error is ALWAYS the message
        // from the originating exception, and FileRetriever throws an exception with the message
        // "Requested frame outside valid bounds" when an invalid frame is requested
        // If this changes or becomes unpredictable, use an else instead with a debug() statement
        exception(error.code, error.message)
    }
}
// At this point, frameResults[f] will contain the objects identified in frame f * frameSkip
```

## Neural Net Interface<a name="netInterface" id="netInterface"></a>

This is a user written interface that interprets a jpeg encoded image and returns the results in a List of JSON-friendly
Maps. Settings can be set through configuration or Query messages, and settings may differ between the two.

### Default Processor<a name="defaultNet" id="defaultNet"></a>

This is a user written implementation that acts as the default if no neural net type is specified. If no such
implementation is included then `type` must be specified for the source to function. It must be named "DefaultProcessor"
and be placed in the `io.vantiq.extsrc.objectRecognition.neuralNet` package. It can be added either by adding the
implementation to `<repo location>/objectRecognitionSource/src/main/io/vantiq/extsrc/objectRecognition/neuralNet` before
running `./gradlew assemble` or by inserting the class as a jar into `<install location>/objectRecognitionSource/lib`
and adding it to the `CLASSPATH` in `<install location>/objectRecognitionSource/bin/objectRecognitionSource`
and `<install location>/objectRecognitionSource/bin/objectRecognitionSource.bat`.

### Yolo Processor<a name="yoloNet" id="yoloNet"></a>

This is a TensorFlow implementation of YOLO (You Only Look Once). It returns a List of Maps, each of which has a `label`
stating the type of the object identified, a `confidence` specifying on a scale of 0-1 how confident the neural net is
that the identification is accurate, and a `location` containing the coordinates for the `top`,`left`, `bottom`,
and `right` edges of the bounding box for the object. It can also save images with the bounding boxes drawn. The options
are as follows. Remember to prepend "NN" when using an option in a Query.
*   pbFile: Required. Config only. The .pb file for the model. The model can be trained using [darknet](https://pjreddie.com/darknet/install/) and then translated to tensorflow format using [darkflow](https://github.com/thtrieu/darkflow).
*   labelFile: Required. Config only. The labels for the model.
*   outputDir: Optional. Config and Query. The directory in which the images (object boxes included) will be placed.
    Images will be saved as "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
    where each value will zero-filled if necessary, e.g. "2018-08-14--06-30-22.jpg". For non-Queries, no images will
    be saved if not set. For Queries, either this must be set in the Query, or this must be set in the config and
    fileName must be set in the Query for images to be saved.
*   fileName: Optional. Query only. The name of the file that will be saved. Defaults to
    "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg" if not set.
*   saveRate: Optional. Config only. The rate at which images will be saved, once every n frames captured. Default is
    every frame captured when unset or a non-positive number. Does nothing if outputDir is not set at config.

## Testing

The test will download two large (~200 MB)files before running the test. If you do not want this to occur, do not run
test or build.  

There are some jni errors that will appear during testing that do not indicate test problems or failures. TensorFlow may
display errors that look like `I tensorflow/core/platform/cpu_feature_guard.cc:141] Your CPU supports instructions that
this TensorFlow binary was not compiled to use:`. This merely indicates that there are improvements that could be made
to performance by compiling the code specifically for your computer. If you wish to use these features, see [how to
build TensorFlow Java](https://www.tensorflow.org/install/install_sources#build_the_c_or_java_libraries). Once built,
either replace the `libtensorflow-<version>.jar` in `<install location>/objectRecognitionSource/lib` and run
`<install location>/objectRecognitionSource/scripts/replaceJni <the built .so/.dll file>`; or add the locations of the
new files into the `CLASSPATH` variable found in `<install location>/objectRecognitionSource/bin/objectRecognitionSource`
and `<install location>/objectRecognitionSource/bin/objectRecognitionSource.bat` and remove the `libtensorflow*` files
in `<install location>/objectRecognitionSource/lib`.  

OpenCV will also display errors that look like `warning: Error opening file
(/build/opencv/modules/videoio/src/cap_ffmpeg_impl.hpp:856)` and `warning: invalidLocation
(/build/opencv/modules/videoio/src/cap_ffmpeg_impl.hpp:857)`. These are expected, as the tests need to ensure correct
behavior when the file cannot be found.

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

This program uses several licensed libraries.  

TensorFlow, okhttp3, Apache commons, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j and the [openpnp](https://github.com/openpnp/opencv) distribution of OpenCV used by this library are licensed
under the [MIT License](https://opensource.org/licenses/MIT).  

OpenCV is licensed under the [BSD 3-clause license](https://opencv.org/license.html).  

The TensorFlow implementation of YOLO found in the edu.ml.* packages uses the
[WTFPL](https://github.com/szaza/tensorflow-example-java/blob/master/LICENSE) public license. A few changes were made
to [the original library](https://github.com/szaza/tensorflow-example-java), mostly removing unneeded files and
functions, and changing the program to perform better when sending images consecutively. All changes are documented,
and most if not all are in ObjectDetector and IOUtil.