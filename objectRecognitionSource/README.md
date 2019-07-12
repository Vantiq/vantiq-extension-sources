## Overview

The following documentation outlines how to incorporate an Object Recognition Source as part of your VANTIQ project. This allows a user to store and process data with VANTIQ, all of which is collected by analyzing images/videos using any Tensorflow-compatible Neural Network. 

This implementation of the Object Recognition Source includes built-in functionality for the YOLO Processor, though any Tensorflow-compatible neural network can be used by implementing the NeuralNetInterface. Additionally, this implementation includes functionality to retrieve four different types of images:

*   Camera Retriever - used to retrieve images from a serially-connected camera.
*   Network Stream Retriever - used to retrieve images from a network-connected camera.
*   File Retriever - used to retrieve images and videos from disk.
*   FTP Retriever - used to retrieve images trhough FTP, FTPS, and SFTP.

Again, other types of images and videos can be processed by implementing the ImageRetrieverInterface.

**IMPORTANT:** Please see the [Model Files](#modelFiles) describing the way the model information is presented.

## Prerequisites

**IMPORTANT:** Read the [Testing](#testing) section before building this project.

An understanding of the VANTIQ Extension Source SDK is assumed. Please read the Extension Source README.md for more information.

The user must define the Object Recognition Source implementation in VANTIQ. For an example of the definition, please see the *objRecImpl.json* file located in the *src/test/resources* directory.

Additionally, an example VANTIQ project named *objRecExample.zip* can be found in the *src/test/resources* directory.

*   It should be noted that this example uses the yolo.pb and coco.names files that are downloaded as part of running the tests associated with the project.
*   Please see the [Model Files](#modelFiles) section for information about locating these files.

## Repository Contents

*   [ObjectRecognitionMain](#objRecMain) -- The main function for the program. Connects to sources as specified in a
    configuration file.
*   [ObjectRecognitionCore](#core) -- Controls the connection to a source, the input images, and the output.
*   [ObjectRecognitionConfigHandler](#srcConfig) -- Sets up the neural net and image retriever based on the source's
    configuration document.
*   [NeuralNetResults](#msgFormat) -- A class that holds the data passed back by neural net implementations.
*   [NeuralNetInterface](#netInterface) -- An interface that allows other neural nets to be more easily integrated
    without changes to the rest of the code.
    *   [YOLO Processor](#yoloNet) -- An implementation of the [You Only Look Once](https://pjreddie.com/darknet/yolo/)
        (YOLO) object detection software using Java Tensorflow.
    * [YOLO Models Available](#modelFiles)
    * [No Processor](#noNet) -- An implementation of the interface used just to **save** images. This implementation does 
    **no** image processing.
*   [ImageRetrieverResults](#msgFormat) -- A class that holds the data passed back by image retriever implementations.
*   [ImageRetrieverInterface](#retrieveInterface) -- An interface that allows different image retrieval mechanisms to be
        more easily integrated without changes to the rest of the code.
    *   [CameraRetriever](#camRet) -- Retrieves images from a directly connected camera using OpenCV.
    *   [NetworkStreamRetriever](#netRet) -- Retrieves images from an IP camera.
    *   [FileRetriever](#fileRet) -- Retrieves images and videos from disk.
    *   [FtpRetriever](#ftpRet) -- Retrieves images through FTP, FTPS, and SFTP.

## How to Run the Program<a name="objRecMain" id="objRecMain"></a>

1.  If you intend to use any of the implementations that require OpenCV, you must install OpenCV version 4.1.0 for
Java. The [official site](https://opencv.org/releases/) and [this more in-depth tutorial](https://opencv-java-tutorials.readthedocs.io/en/latest/01-installing-opencv-for-java.html) describe how to install it. Once it's installed, copy 
the jar and (lib)opencv_java410.dll/.so/.dylib to a single folder, then set the environment variable OPENCV_LOC to that 
folder. Some features may depend on additional .dll/.so/.dylib files, such as FFmpeg for IP cameras.
    *   **NOTE:** If you are using a Windows or Linux machine, you may need to add the `opencv_ffmpeg410_64.dll` or 
    `opencv_ffmpeg410_64.so` file, respectively, to the OPENCV_LOC folder. This file is located in the same directory as the 
    other OpenCV .jar/.dll/.so/.dylib files. (Use `opencv_ffmpeg410.dll` or `opencv_ffmpeg410.so` for 32 bit version.)
        *   A typical indicator that you will need to add this file is if OpenCV cannot open video streams/files.
    *   The implementations dependent on OpenCV are FileRetriever, NetworkStreamRetriever, and CameraRetriever.
2.  Clone this repository (vantiq-extension-sources) and navigate into `<repo location>/vantiq-extension-sources`.
3.  Run `./gradlew objectRecognitionSource:assemble`.
4.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/distributions`. The zip and tar
    files both contain the same files, so choose whichever you prefer.
5.  Uncompress the file in the location that you would like to install the program.
6.  Run `<install location>/objectRecognitionSource/bin/objectRecognitionSource` with a local server.config file or
    specifying the [server config file](#serverConfig) as the first argument.

## Logging
To change the logging settings, edit `<install location>/objectRecognitionSource/logConfig/log4j2.xml`. Here is
its [documentation](https://logging.apache.org/log4j/2.x/manual/configuration.html). The logger names for each class is
the class's fully qualified class name, e.g. "io.vantiq.extjsdk.ExtensionWebSocketClient".  

To edit the logging for an IDE, change `<repo location>/objectRecognitionSource/src/main/resources/log4j2.xml`. Changes
to this will be included in future distributions produced through gradle.

## Server Config File<a name="serverConfig" id="serverConfig"></a>

The server config file is written as `property=value`, with each property on its
own line. The following is an example of a valid server.config file:

```
authToken=vadfEarscQadfagdfjrKt7Fj1xjfjzBxliahO9adliiH-Dj--gNM=
sources=Camera1
targetServer=https://dev.vantiq.com/
modelDirectory=<path_to_your_pb_file>
```

### Vantiq Options
*   authToken: Required. The authentication token to connect with. These can be obtained from the namespace admin.
*   sources: Required. A comma separated list of the sources to which you wish to connect. Any whitespace will be
    removed when read.
*   targetServer: Required. The Vantiq server hosting the sources.

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
    *   FileRetriever, NetworkStreamRetriever, and CameraRetriever all use OpenCV, which is not in the gradle
        dependencies. If they are not removed you must install OpenCV version 4.1.0, set the environment variable
        OPENCV_LOC to the folder containing the jar, and ensure that when running your code the jar is in the classpath
        and (lib)opencv_java410.dll/.so/.dylib is in a folder in `java.library.path`. You may see a warning about not 
        having (lib)opencv_java410.dll/.so/.dylib in the correct directory when compiling the objectRecognition jar,
        but this can be safely ignored since you will be setting the path yourself. Instructions for installing
        OpenCV can be found at the [official site](https://opencv.org/releases/) or [this more in-depth tutorial](https://opencv-java-tutorials.readthedocs.io/en/latest/01-installing-opencv-for-java.html).
2.  Run `./gradlew objectRecognitionSource:assemble` or `.\gradlew objectRecognitionSource:assemble` depending on the
    OS.
3.  Navigate to `<repo location>/vantiq-extension-sources/objectRecognitionSource/build/libs` and copy
    `objectRecognitionSource.jar` into your project.
4.  Add the dependencies found in `<repo location>/vantiq-extension-sources/objectRecognitionSource/build.gradle` to
    your project.
    *   The gradle build file specifies every dependency that is used only in neural net or image retriever
        implementations. If you removed any classes in step 1, use the comments to identify which dependencies are
        no longer needed. OpenCV is automatically ignored if all its dependents are removed, so it can be ignored.

### Using in Your Own Code

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

## <a name="srcConfig" id="srcConfig"></a>Source Configuration Document

The Configuration document may look similar to the following example:

```
    {
       "objRecConfig": {
          "general": {
             "pollTime": 3000,
             "maxRunningThreads": 5,
             "maxQueuedTasks": 10
          },
          "dataSource": {
             "camera": "http://166.155.71.82:8080/mjpg/video.mjpg",
             "type": "network"
          },
          "neuralNet": {
             "metaFile": "coco-1.1.meta",
             "pbFile": "coco-1.1.pb",
             "type": "yolo",
             "threshold": 0.2,
             "saveImage": "both",
             "saveRate": 1,
             "outputDir": "imageOut",
             "savedResolution": {
                "longEdge": 400
             },
             "cropBeforeAnalysis": {
                "x": 50,
                "y": 100,
                "width": 600,
                "height": 400
             }
          }
       }
    }
```

### Options Available for General
At least one of these options must be set for the source to function

*   pollTime: This indicates how often an image should be captured. A positive number represents the number of
milliseconds between captures. If the specified time is less than the amount of time it takes to process the image then
images will be taken as soon as the previous finishes. If this is set to 0, the next image will be captured as soon as
the previous is sent. 
    *   (**NOTE:** Previously named "pollRate". For a limited amount of time, both "pollTime" and "pollRate"
    will be valid General Config options, but in the future only "pollTime" will be supported.)
*   allowQueries: This option allows Queries to be received when set to `true'
*   maxRunningThreads: Optional. Only used if `pollTime` has been specified. The maximum number of threads running at any 
given point for polling requests from the specific VANTIQ source. Must be a positive integer. Default value is 10.
*   maxQueuedTask: Optional. Only used if `pollTime` has been specified. The maximum number of queued tasks at any given point 
for polling requests from the specific VANTIQ source. Must be a positive integer. Default value is 20.
    *   **NOTE:** The default behavior of the Object Recognition Source is to process the captured frames in parallel. If you 
    would like the source to process frames sequentially, then `maxRunningThreads` and `maxQueuedTasks` must both be set to 1.

### Options Available for Data Source

Most of the options required for dataSource are dependent on the specific implementation of
[ImageRetrieverInterface](#retrieveInterface). The ones that are the same across all implementations
are:

*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of ImageRetrieverInterface, e.g.
        "io.vantiq.extsrc.objectRecognition.imageRetriever.CameraRetriever".
    2.  The short name of one of the standard implementations, i.e. one of "[file](#fileRet)", "[camera](#camRet)",
        "[ftp](#ftpRet)", "[network](#netRet)", or "[default](#defaultRet)".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultRetriever" in
        the `io.vantiq.objectRecognition.imageRetriever` package. This implementation is not provided, and must be
        written by the user.

### <a name="neuralNetInterface" id="neuralNetInterface"></a>Options Available for Neural Net

Most of the options required for neuralNet are dependent on the specific implementation of
[NeuralNetInterface](#netInterface). For an example of neural net specific configurations, please look at the [YOLO Processor 
configuration options](#yoloNet). The ones that are the same across all implementations are:
*   type: Optional. Can be one of three situations
    1.  The fully qualified class name of an implementation of NeuralNetInterface, e.g.
        "io.vantiq.extsrc.objectRecognition.neuralNet.YoloProcessor".
    2.  The short name of one of the standard implementations, i.e. one of "[yolo](#yoloNet)", "[none](#noNet)" or
        "[default](#defaultNet)".
    3.  Empty, in which case the program will try to find an implementation with the name "DefaultProcessor" in
        the `io.vantiq.objectRecognition.neuralNet` package. This implementation is not provided, and must be written by
        the user.
*   threshold: Optional. Threshold is used to decide if the Neural Net's result is a valid one, by comparing the resulting confidence of the recognition against the threshold value. A high threshold will lead to fewer results, all with a higher confidence. A low threshold will lead to more results, some of which having a lower confidence. Threshold defaults to 0.5 if not specified, or if invalid. There are two ways to specify this value:
    1.  The value can be a number between 0 and 1 (i.e. 0.4, or 0.2, etc...)
    2.  The value can be a number between 0 and 100 (i.e. 40, or 20, etc...)
*   cropBeforeAnalysis: Optional. Used to crop the retrieved images before they are processed by the Neural Net. This option 
*must* contain the following values:
    *   **x**: The top left x-coordinate used to crop the image.
    *   **y**: The top left y-coordinate used to crop the image.
    *   **width**: The width of the cropped image, (starting at the x-coordinate specified above).
    *   **height**: The height of the cropped image, (starting at the y-coordinate specified above).
*   saveImage: Optional. The value can be one of the following three options:
    1.  "local"     - This will save images to the disk (outputDir must be specified in order for this to work).
    2.  "vantiq"    - This will save images as documents in VANTIQ. No images will be saved locally even if outputDir is specified.
    3.  "both"      - This will save the images both to the disk and as documents in VANTIQ. (outputDir must be specified in order to save locally)
    
**NOTE:** All of the following options are relevant only if the "saveImage" option has been set.

*   outputDir: Optional. The directory in which images will be saved locally. Images will only be saved locally if saveImage 
is set to be either "local" or "both".
*   saveRate: Optional. The rate at which images will be saved (i.e. "saveRate": 3 - This will save every 3rd image that is 
captured). If not specified, the value will default to 1 which saves every captured image.
*   labelImage: Optional. If set to "true", images will be saved with bounding boxes and labels. If set to "false", or if not 
set at all, the images will be saved with no bounding boxes or labels.
*   savedResolution: Optional. A map containing options for adjusting the resolution of the saved images.
    * *Options for savedResolution:*
    1. longEdge: Optional. This sets the maximum long edge dimension for saved images. Must be a non-negative integer that is 
    smaller than the long edge of the image to be saved. This value will become the new long edge dimension, and the short 
    edge will be scaled down to maintain the same aspect ratio as the original image.
        *   **NOTE:** We do not support enlarging images. This feature can be used *only* to resize images to smaller 
        dimensions. If the longEdge value is larger than the image's longest dimension, then the image will be saved without 
        any changes.

## Messages from the Source<a name="msgFormat" id="msgFormat"></a>

Messages from the source are JSON objects in the following format:

```
{
    filename: <the name of the file matching the following pattern: objectRecognition/sourceName/timestamp.jpg>
    sourceName: <the name of your VANTIQ Source>,
    results: [<object found>, <object found>],
    timestamp: <milliseconds since Jan 1 1970 00:00:00, a.k.a standard Unix time>,
    dataSource: {
        <additional data sent back by data source>
    },
    neuralNet: {
        <additional data sent back by neural net>
    }
}
```

The contents and ordering of the objects in `results` are dependent on the implementation of the neural net, but they
are guaranteed to be JSON objects. The contents of `dataSource` and `neuralNet` are dependent on the implementation of
each. The timestamp is not required. Note that the timestamp is immediately usable as the VAIL DateTime type.

*   **NOTE:** The "filename" field will only be present if the "saveImage" config option has been set.

## Queries

Options can be specified through the WITH clause for both the neuralNet and dataSource. The options for Queries are
implementation dependent, and the available options for the standard implementations can be found in their respective
sections. Since it is possible for both the data source and the neural net to use options with the same name, the
standard implementations prepend the options with DS and NN for all queries, i.e. if you want to Query using the
`fileLocation` option for a data source you would set the `DSfileLocation` property in the WITH clause. This may or may
not apply for non-standard implementations, though developers are advised to follow this rule for consistency's sake.  
The SELECT statement that created the Query will only receive an array of JSON objects representing the objects 
identified, in the format used by the neural [net implementation](#netInterface).  

Options available for all Queries (not prepended by anything) are:

*   sendFullResponse: Optional. Specifies that this request should send back data in the same format as a notification
    instead of only the objects recognized. Note that the data will be the sole occupant of a 1-element array when
    received, instead of being immediately available as a JSON object. This is because Query results are mandated
    to be arrays. Default is false.
    
Unique query capabilities available for the YOLO Processor are as follows:

*   **NOTE:** All YOLO Processor queries should have an "operation" parameter specified. If this parameter is not specified, 
it will be set to the default value, "processNextFrame".

*   **Upload images to VANTIQ:**
    *   The user can specify an image or a set of images specified by their date & time to be uploaded to VANTIQ as a 
    document. The images will be those that are saved in the output directory, which is defined in the source configuration.
    *   Parameters:
        *   "operation": Required. Must be set to "upload".
        *   *You should specify exactly one of the following two values. If neither is specified, a query error will be 
        returned. If both values are specified, the imageName value will be used.*
            *   "imageName": A string value representing the name of the file to be uploaded.
            *   "imageDate": A list containing two strings, a start date and an end date. All locally saved images falling 
            between the start and end date, (inclusive), will be uploaded. Dates must be formatted in the following 
            manner: "yyyy-MM-dd--HH-mm-ss".
                *   To select all images *before* or *after* a certain date, the "-" value can be used as one of the date 
                strings in the list. For example, the following would save all dates *before* the given date: 
                    *   \["-", yourEndDate\]
                *   To select *all files named with the date-naming convention* in the output directory, one must use the 
                following value for imageDate: 
                    *   \["-", "-"\]
        *   "savedResolution": Optional. This value can be set in the same way as it is set in the source configuration. If it 
        is defined here as a query parameter, it will override the value set in the source configuration, otherwise the source 
        configuration value will be used. The setting cannot be larger than that provided in the source configuration (since 
        that defines how the images are saved).
    
*   **Delete locally saved images:**
    *   The user can specify an image or a set of images specified by their date & time to be deleted. The images will be 
    those that are saved in the output directory, which is defined in the source configuration.
    *   Parameters:
        *   "operation": Required. Must be set to "delete".
        *   *You should specify exactly one of the following two values. If neither is specified, a query error will be 
        returned. If both values are specified, the imageName value will be used.*
            *   "imageName": A string value representing the name of the file to be deleted.
            *   "imageDate": A list containing two strings, a start date and an end date. All locally saved images falling 
            between the start and end date, (inclusive), will be deleted. Dates must be formatted in the following 
            manner: "yyyy-MM-dd--HH-mm-ss".
                *   To select all images *before* or *after* a certain date, the "-" value can be used as one of the date 
                strings in the list. For example, the following would save all dates *before* the given date: 
                    *   \["-", yourEndDate\] 
                *   To select *all files named with the date-naming convention* in the output directory, one must use the 
                following value for imageDate: 
                    *   \["-", "-"\]

*   **Process a single frame from the camera defined in the source configuration:**
    *   Parameters:
        *   "operation": Required. Must be set to "processNextFrame".
        *   "NNsaveImage": Optional. This value can be set exactly like the "saveImage" value in the source configuration.
        *   "NNoutputDir": Optional. This value can be set exactly like the "outputDir" value in the source configuration.
        *   "NNfileName": Optional. A string representing the unique name used to save the file. If not specified, the file 
        will be named using the standard <yyyy-MM-dd--HH-mm-ss.jpg> value.
        *   "cropBeforeAnalysis": Optional. This value can be set exactly like the "cropBeforeAnalysis" value in the source 
        configuration. If no cropBeforeAnalysis value is set as a query parameter, the source configuration's 
        cropBeforeAnalysis value will be used.
    
**EXAMPLE QUERIES**:

*   Upload Query using imageName:
```
SELECT * FROM SOURCE Camera1 AS results WITH
    	operation:"upload",
    	imageName:"2019-02-08--10-33-36.jpg",
    	savedResolution: {longEdge:600}
```

*   Upload Query using imageDate:
```
SELECT * FROM SOURCE Camera1 AS results WITH
    	operation:"upload",
    	imageDate:["2019-02-08--10-33-36", "2019-02-08--12-45-18"]
```

*   Delete Query using imageName:
```
SELECT * FROM SOURCE Camera1 AS results WITH
    	operation:"delete",
    	imageName:"2019-02-08--10-33-36.jpg"
```

*   Delete Query using imageDate to delete everything after a certain date:
```
SELECT * FROM SOURCE Camera1 AS results WITH
    	operation:"delete",
    	imageDate:["2019-02-08--10-33-36", "-"]
```

*   Delete Query using imageDate to delete all images:
```
SELECT * FROM SOURCE Camera1 AS results WITH
    	operation:"delete",
    	imageDate:["-", "-"]
```

*   Process Next Frame Query:
```
SELECT * FROM SOURCE Camera1 AS results WITH
        operation:"processNextFrame",
    	NNsaveImage:"local",
    	NNoutputDir:"testDir",
    	NNfileName:"testFile"
```

*   Process Next Frame Query, with preCrop set:
```
SELECT * FROM SOURCE Camera1 AS results WITH
        operation:"processNextFrame",
    	NNsaveImage:"local",
    	NNoutputDir:"testDir",
    	NNfileName:"testFile",
        cropBeforeAnalysis: {
            x: 50,
            y: 100,
            width: 600,
            height: 400
        }
```

*   Process Next Frame Query without specifying operation *(not recommended)*:
```
SELECT * FROM SOURCE Camera1 AS results WITH
    	NNsaveImage:"local",
    	NNoutputDir:"testDir",
    	NNfileName:"testFile"
```

### Error Messages

Query errors originating from the source will always have the code be the FQCN with a small descriptor attached, and
the message will include the exception causing it and the request that spawned it. The error's parameters will be, in
order, the message of the java exception thrown, the java exception thrown, and a JSON object containing the properties
of the query that caused the error.  

The messages of exceptions thrown by the standard implementations of NeuralNets and ImageRetrievers will always be in
the form "&lt;FQCN of the class that spawned the error&gt;.&lt;mini descriptor&gt;: &lt;longer description&gt;".

## Image Retriever Interface<a name="retrieveInterface" id="retrieveInterface"></a>

This is an interface that returns a jpeg encoded image, a timestamp (optional), and a Map containing any data that a
source may need. Settings may or may not differ for periodic messages versus Query responses. There are four
implementations included in the standard package.

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

*   camera: Required for Config, optional for Query. The index of the camera to read images from. 
For queries, defaults to the camera specified in the Config.

The timestamp is captured immediately before the image is grabbed from the camera. No other data is included.
    
### Network Stream Retriever<a name="netRet" id="netRet"></a>

This implementation uses OpenCV to capture live frames from a camera connected to a network address. Errors are thrown
whenever an image cannot be read successfully, whenever the provided URL uses an unsupported protocol, whenever the URL
was unable to be opened, or whenever the URL does not represent a video stream. Fatal errors are thrown only when the
camera is inaccessible in non-Query mode, (i.e. if the video stream has ended).  

The options are as follows. Remember to prepend "DS" when using an option in a Query.

*   camera: Required for Config, optional for Query. The URL of the camera to read images from. For queries, defaults
    to the camera specified in the Config.

The timestamp is captured immediately before the image is grabbed from the camera. The additional data is:

*   camera: The URL of the camera that the image was read from.

### File Retriever<a name="fileRet" id="fileRet"></a>

This implementation reads files from the disk using OpenCV for the videos. `fileLocation` must be a valid file at
initialization. The initial image file can be replaced while the source is running, but the video cannot. For Queries,
new files can be specified using the `fileLocation` and `fileExtension` options, and defaults to the initial file
if `fileLocation` is not set. Queried videos can specify which frame of the video to access using the `targetFrame`
option.  

Errors are thrown whenever an image or video frame cannot be read. Fatal errors are thrown only when a video finishes
being read when the source is setup for constant polling.
The options are as follows. Remember to prepend "DS" when using an option in a Query.

*   fileLocation: Optional. Config and Query. The location of the file to be read. For Config where
    `fileExtension` is "mov", the file must exist at initialization. If this option is not set at Config and the source
    is configured for polling, then the source will open but the first attempt to retrieve will kill the source. For
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

No timestamp is captured. The additional data is:
*   file: The path of the file read.
*   frame: Which frame of the file this represents. Only included when `fileExtension` is set to "mov".

#### Example: Reading an Entire Video Through Queries
If you want to read a video file from a source using Queries, this method will work.

```
var frameResults = []
var frameSkip = 72
var maxCaptures = 100
var myVideoLocation = "movie.mov"
PROCEDURE queryAndSaveVideoData(video String)

try {
    // We expect the error received when the bounds are overrun to stop the loop,
    // so we needn't worry about the while condition
    FOR (frame in range(0, maxCaptures)) {
        SELECT * FROM SOURCE Camera3 AS results WITH 
                        //sendFullResponse = true,
                        // Uncomment previous line if you want the metadata for each, not just the objects
                        DSfileLocation:myVideoLocation,
                        DSfileExtension:"mov",
                        DStargetFrame:frame * frameSkip,
    }
} catch (error) {
    if ( !(typeOf(error.params[0]) == "String" 
            && error.params[0].startsWith("io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever.invalidTargetFrame")) ) {
        // The parameter 0 of the query error is ALWAYS the message
        // from the originating exception if the exception comes from the source.
        // FileRetriever throws an exception whose message
        // starts with:
        // "io.vantiq.extsrc.objectRecognition.imageRetriever.FileRetriever.invalidTargetFrame"
        // when a frame past the end of the video is requested
        // If this changes or becomes unpredictable, use a log.debug() statement instead of this if statement
        exception(error.code, error.message)
    }
}
// At this point, frameResults[f] will contain the objects identified in frame f * frameSkip
```

### FTP Retriever<a name="ftpRet" id="ftpRet"></a>

This implementation can read files from FTP, FTPS, and SFTP servers. Not all options available for each protocol are
implemented. Only Queries are allowed, for which `file` is necessary. The server created at initialization will be used
if no new options are set for the server and `noDefault` was not set to `true` for the configuration. If some but not
all options are set for a Query, then the values from the initial configuration are used.  

Errors are thrown whenever an image or video frame cannot be read. Fatal errors are thrown only when the initial server
cannot be created.  

The options are as follows. Remember to prepend "DS" when using an option in a Query.

*   noDefault: Optional. Config only. When true, no default server is created and no default settings are saved. This
    means that when true all options without defaults are required for Queries. When false or unset, *all other
    Configuration settings without default values are required*.
*   server: Optional. Config and Query. The URL of the server to connect to. It is preferred if the URL is only the
    domain name, e.g. "site.name.com", though the source will try to obtain the domain name from a URL if necessary.
*   username: Optional. Config and Query. The username for the given server.
*   password: Optional. Config and Query. The password for the given server.
*   conType: Optional. Config and Query. The type of connection you would like, one of "FTP", "FTPS", or "SFTP". Case
    sensitivity does not matter. Defaults to "FTP".
*   implicit: Optional. Config and Query. For FTPS only. Whether to connect using implicit security. Defaults to false
    (i.e. explicit mode).
*   protocol: Optional. Config and Query. For FTPS only. Which security mechanism to use. Typically either "SSL"
    or "TLS". Default to "TLS".
*   file: Required. Query only. The path of the target file.

The timestamp is captured immediately before the copy request is sent. The additional data is:
*   file: The path of the file read.
*   server: The domain name of the server which the file was read from.

## Neural Net Interface<a name="netInterface" id="netInterface"></a>

This is an interface that should interpret a jpeg encoded image and return the results in a List of Maps and any
other data that the source may need. Settings can be set through configuration or Query messages, and settings may
differ between the two.

### Default Processor<a name="defaultNet" id="defaultNet"></a>

This is a user written implementation that acts as the default if no neural net type is specified. If no such
implementation is included then `type` must be specified for the source to function. It must be named "DefaultProcessor"
and be placed in the `io.vantiq.extsrc.objectRecognition.neuralNet` package. It can be added either by adding the
implementation to `<repo location>/objectRecognitionSource/src/main/io/vantiq/extsrc/objectRecognition/neuralNet` before
running `./gradlew assemble` or by inserting the class as a jar into `<install location>/objectRecognitionSource/lib`
and adding it to the `CLASSPATH` in `<install location>/objectRecognitionSource/bin/objectRecognitionSource`
and `<install location>/objectRecognitionSource/bin/objectRecognitionSource.bat`.

### <a name="yoloNet" id="yoloNet"></a>YOLO Processor

This is a TensorFlow implementation of YOLO (You Only Look Once). The identified objects have a `label`
stating the type of the object identified, a `confidence` specifying on a scale of 0-1 how confident the neural net is
that the identification is accurate, and a `location` containing the coordinates for the `top`,`left`, `bottom`,
and `right` edges of the bounding box for the object. It can also save images with the bounding boxes drawn.  

The standard implementation expects a net trained on 416x416 images, and automatically resizes images to those 
dimensions. If a `.meta` file is provided, then the input frame size stored in that file, ("height"/"width" fields), will be 
used. To override the default or `.meta` file frame size, the user can change `edu.ml.tensorflow.Config.FRAME_SIZE` to the 
desired dimension. This will change the dimensions of the image sent to the neural net. The dimensions will still be a square, 
as is required by this implementation of the YOLO Processor.

The options are as follows. Remember to prepend "NN" when using an option in a Query.

*   pbFile: Required. Config only. The .pb file for the model. The model can be trained using
    [darknet](https://pjreddie.com/darknet/install/) and then translated to tensorflow format using
    [darkflow](https://github.com/thtrieu/darkflow).
*   metaFile: Required unless labelFile was supplied. Config only. A .meta file generated alongside the .pb file, that contains both the anchors and labels associated with the .pb file.
*   labelFile: **DEPRECATED**. Required if no metaFile was supplied. Config only. This file contains the labels for the model. If both a labelFile and metaFile have been supplied, the labels from the labelFile will be used.
*   anchors: Optional, but encouraged if a different model is used and no metaFile was provided. This value is closely tied to the model (as specified in the `neuralNet.pbFile`, `neuralNet.metaFile` and `neuralNet.labelFile` configuration parameters). The `anchors` are constructed from the training data, specifying the most likely rectangles that contain objects. These are, in turn, used to define the bounding boxes for objects discovered. If not specified, the default value will be used. The default value corresponds to the correct `anchors` value for the model that is used in the build. If you use a different model, you are encouraged to supply the appropriate `anchor` values. 
    * Note that the anchors value for a particular model can be found in model's `.meta` file.
    * These anchor values will override the anchors from a metaFile if one is provided.
    * Example: `"anchors": [0.57273, 0.677385, 1.87446, 2.06253, 3.33843, 5.47434, 7.88282, 3.52778, 9.77052, 9.16828]`
*   outputDir: Optional. Config and Query. The directory in which the images (object boxes included) will be placed.
    Images will be saved as "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
    where each value will zero-filled if necessary, e.g. "2018-08-14--06-30-22.jpg".
*   fileName: Optional. Query only. The name of the file that will be saved. Defaults to
    "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg" if not set.
*   saveRate: Optional. Config only. The rate at which images will be saved, once every n frames captured. Default is
    every frame captured when unset or a non-positive number. Does nothing if outputDir is not set at config.

No additional data is sent.

#### <a name="modelFiles" id="modelFiles"></a>YOLO Model File(s) used in the Tests 

Generally speaking, the model files are large.  The protocol buffer ('.pb') file used in the tests is around 200 megabytes.  Consequently, we download these as part of the build, but they are not stored as part of the Git project.

The files we use are as follows.

* COCO -- COCO is a large dataset from Microsoft with 80 object categories.
  * Version 1.1 -- the primary difference over version 1.1 is the providing of the `.meta` file. However, the file has been regenerated so we view it as a new version.
     * `coco-1.1.pb` -- the protocol buffer file (see [YOLO Processor](#yoloNet))
     * `coco-1.1.meta` -- the associated meta file
     * `coco-1.1.names` -- the (soon to be deprecated) label file
 * Version 1.0
     * `coco-1.0.pb` -- Same purpose as above for version 1.0
     * `coco-1.0.names`
* YOLO (deprecated) -- this is the same as COCO version 1.0, with less-well-chosen names
  * `yolo.pb` -- the protocol buffer file -- the same contents as `coco-1.0.pb`
  * `coco.names` -- the label file -- same contents as `coco-1.0.names`

We have moved to new names as they are clearer about what they do.  Operationally, things will remain the same.

You are free to use other YOLO-based models (or other neural nets if you provide your [own neural net processor via the `NeuralNetInterface`](#neuralNetInterface). While we have not tested with them, anything that uses a 416x416 image (that is, downsized to that before neural net processing) is likely to work.  We cannot test with all models. In the future, we may relax the 416x416 restriction by using information from the `.meta` file.  See the [YOLO Processor](#yoloNet) section for information on producing your own models.

These files are now fetched from an Amazon S3-based maven repository, and will be downloaded into your gradle cache. For use in the tests, they are copied to `objectRecognitionSource/build/models`. If you want to use them for a project, you are encouraged to copy them to a stable location (the build hierarchy is destroyed when tests are run as part of normal gradle processing). If those files are missing, you can replace them by running the following tasks in gradle.

* ```./gradlew :objectRecognitionSource:copyModels``` -- this will copy the current models (COCO v1.1) into ```build/models```
* ```./gradlew :objectRecognitionSource:copyOldCocoModels``` -- this will copy the COCO v1.0 models into ```build/models```
* ```./gradlew :objectRecognitionSource:copyOldYoloModels``` -- this will copy the Yolo/coco.names files ```build/models```

(Please replace `./gradlew` with `.\gradlew` as appropriate for your Operating System.)

Previously, `yolo.pb` file was downloaded as part of the build, but the `coco.names` file was stored in Git.  That file is still in git (but likely to be removed soon).  We now obtain all the model files from the same place -- so none of this information will be in Git.  Keeping the information together is a safer way to go about things.

### <a name="noNet" id="noNet"></a>No Processor

The purpose of this implementation of the Neural Net Interface is to save images without doing any neural net processing. This allows a 
user to capture frames from their video or image source, and save them with VANTIQ, locally, or both. There is **no 
processing** done to the captured frames, they are saved as captured from the camera.

The Extension Source can be setup for polling, for queries, or for both.

*   outputDir: Optional. Config and Query. The directory in which the images (object boxes included) will be placed.
    Images will be saved as "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg"
    where each value will zero-filled if necessary, e.g. "2018-08-14--06-30-22.jpg". If pollTime is set under 1000 (1 
    second), images with the same name, (*i.e.* captured in the same second), will be followed by a count in parentheses, e.g. 
    "2018-08-14--06-30-22(1).jpg", "2018-08-14--06-30-22(2).jpg".
*   fileName: Optional. Query only. The name of the file that will be saved. Defaults to
    "&lt;year&gt;-&lt;month&gt;-&lt;day&gt;--&lt;hour&gt;-&lt;minute&gt;-&lt;second&gt;.jpg" if not set. As stated above, 
    redundant filenames will be followed by a count in parentheses.
*   saveRate: Optional. Config only. The rate at which images will be saved, once every n frames captured. Default is
    every frame captured when unset or a non-positive number. Does nothing if outputDir is not set at config.

## Testing<a name="testing" id="testing"></a>
    
In order to properly run the tests, you must first add the VANTIQ server you wish to connect to and corresponding auth token to your gradle.properties file in the ~/.gradle directory as follows:

``` 
    TestAuthToken=<yourAuthToken>
    TestVantiqServer=<desiredVantiqServer>
```

The test will download two large (~200 MB) files before running the test. If you do not want this to occur, do not run
test or build.  

There are some jni errors that will appear during testing that do not indicate test problems or failures. TensorFlow may
display errors that look like `I tensorflow/core/platform/cpu_feature_guard.cc:141] Your CPU supports instructions that
this TensorFlow binary was not compiled to use:`. This merely indicates that there are improvements that could be made
to performance by compiling the code specifically for your computer. If you wish to use these features, see [how to
build TensorFlow Java](https://www.tensorflow.org/install/install_sources#build_the_c_or_java_libraries). Once built,
set the environment variable `TENSORFLOW_JNI` to the folder containing all the .dll/.so/.dylib files, and either replace
the `libtensorflow-<version>.jar` in `<install location>/objectRecognitionSource/lib` with the built jar or add the jar
into the `CLASSPATH` variable found in `<install location>/objectRecognitionSource/bin/objectRecognitionSource`
and `<install location>/objectRecognitionSource/bin/objectRecognitionSource.bat` and remove the `libtensorflow*` files
in `<install location>/objectRecognitionSource/lib`.  

OpenCV will also display errors that look like `warning: Error opening file
(/build/opencv/modules/videoio/src/cap_ffmpeg_impl.hpp:856)` and `warning: invalidLocation
(/build/opencv/modules/videoio/src/cap_ffmpeg_impl.hpp:857)`. These are expected, as the tests need to ensure correct
behavior when the file cannot be found.

Some sets of tests will create a Source named "UnlikelyToExistTestObjectRecognitionSource" in the VANTIQ Namespace associated 
with the provided "TestAuthToken". Please make sure that there is no other VANTIQ Source with the same name in that Namespace.

## Licensing
The source code uses the [MIT License](https://opensource.org/licenses/MIT).  

This program uses several licensed libraries.  

TensorFlow, okhttp3, Apache commons, log4j, and jackson-databind are licensed under
[Apache Version 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).  

slf4j and is licensed under the [MIT License](https://opensource.org/licenses/MIT).  

The TensorFlow implementation of YOLO found in the edu.ml.* packages uses the
[WTFPL](https://github.com/szaza/tensorflow-example-java/blob/master/LICENSE) public license. A few changes were made
to [the original library](https://github.com/szaza/tensorflow-example-java), mostly removing unneeded files and
functions, and changing the program to perform better when sending images consecutively. Changed and added functions
are documented, and most if not all are in ObjectDetector and IOUtil.  

JSch is licensed under a [BSD-style license](http://www.jcraft.com/jsch/LICENSE.txt).

OpenCV is licensed under the [BSD 3-clause license](https://opencv.org/license.html). OpenCV typically uses third party
components that may have stricter licenses than BSD3 and other licenses in this project. It is the responsibility of the
user of this library to ensure that they meet all licensing requirements of components in or used by their OpenCV build.
