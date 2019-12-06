/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionConfigHandler;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@Slf4j
public class TestYoloQueriesLocationMapper extends NeuralNetTestBase {
    static Vantiq vantiq;
    static VantiqResponse vantiqResponse;
    static ObjectRecognitionCore core;

    static final int CORE_START_TIMEOUT = 10;
    static final int CONNECTION_ATTEMPT_LIMIT = 5;
    static final String COCO_MODEL_VERSION = "1.2";
    static final String LABEL_FILE = "coco-" + COCO_MODEL_VERSION + ".names";
    static final String PB_FILE = "coco-" + COCO_MODEL_VERSION + ".pb";
    static final String META_FILE = "coco-" + COCO_MODEL_VERSION + ".meta";
    static final String OUTPUT_DIR = System.getProperty("buildDir") + "/resources/out";
    static final String IP_CAMERA_ADDRESS = "http://207.192.232.2:8000/mjpg/video.mjpg";
    static final Double ACCEPTABLE_DELTA = 0.0001;

    @BeforeClass
    public static void setup() {
        // Only run test with intended vantiq availability
        assumeTrue(testAuthToken != null && testVantiqServer != null);

        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }

    @AfterClass
    public static void tearDown() {
        if (core != null) {
            core.stop();
            core = null;
        }
        deleteSource(vantiq);
        deleteDirectory(OUTPUT_DIR);
        deleteFilesFromVantiq();
    }

    @After
    public void cleanup() {
        deleteSource(vantiq);
    }

    @Test
    public void testLocationMapperSimple() {
        PointChecker checker = new Plus50Checker();
        performMapperTest(plus50SrcPts, plus50DstPts, false, checker);
    }

    @Test
    public void testLocationMapperSimpleGJ() {
        PointChecker checker = new Plus50GJChecker();
        performMapperTest(plus50SrcPts, plus50DstPts, true, checker);
    }

    @Test
    public void testLocationMapperDoubler() {
        PointChecker checker = new XIdentYTimes2();
        performMapperTest(xIdentYTimes2SrcPts, xIdentYTimes2DstPts, false, checker);
    }

    @Test
    public void testLocationMapperDoublerGJ() {
        PointChecker checker = new XIdentYTimes2GJ();
        performMapperTest(xIdentYTimes2SrcPts, xIdentYTimes2DstPts, true, checker);
    }

    interface PointChecker {
        void check(JsonObject resLoc, JsonObject mappedLoc);
    }

    public void performMapperTest(Double[][] srcPts, Double[][] dstPts, boolean geoJsonResults, PointChecker ptChecker) {
        setupSource(createSourceDef(buildConverterSpec(srcPts, dstPts, geoJsonResults)));
        VantiqResponse result;

        Map<String,Object> params = new LinkedHashMap<String,Object>();
        params.put("operation", "processNextFrame");
        params.put("sendFullResponse", true);

        int imagesProcessed = 0;    // We must process 5 images that have results.
                                    // There may be some that contain nothing of interest
        int imageAttempts = 0;
        while (imagesProcessed < 5 && imageAttempts < 25) {
            imageAttempts += 1;
            result = querySource(params);
            log.debug("Ran query, result: {}", result);

            assertTrue("Bad response status", result.isSuccess());
            int status = result.getStatusCode();
            assertTrue("invalid status", status >= 200 && status < 300);

            // Get the path to the saved image in VANTIQ
            JsonObject responseObj = (JsonObject) result.getBody();

            JsonElement resJson = responseObj.get("results");
            log.debug("results: {}", resJson);

            JsonArray resMap = resJson.getAsJsonArray();
            JsonElement mappedJson = responseObj.get("mappedResults");
            log.debug("mappedResults: {}", mappedJson);
            JsonArray mappedMap = mappedJson.getAsJsonArray();

            assertNotNull("No results", resMap);
            assertNotNull("No mapped results", mappedMap);
            assertEquals("Mismatched result size", resMap.size(), mappedMap.size());
            if (resMap.size() > 0) {
                imagesProcessed += 1;
                assertTrue("No results to check", resMap.size() > 0);

                for (int i = 0; i < resMap.size(); i++) {
                    JsonObject resEntry = resMap.get(i).getAsJsonObject();
                    JsonObject mappedEntry = mappedMap.get(i).getAsJsonObject();
                    assertEquals("Confidence mismatch", resEntry.get("confidence").getAsFloat(),
                            mappedEntry.get("confidence").getAsFloat(), 0f);
                    assertEquals("Label mismatch", resEntry.get("label").getAsString(),
                            mappedEntry.get("label").getAsString());
                    JsonObject resLoc = resEntry.get("location").getAsJsonObject();
                    JsonObject mappedLoc = mappedEntry.get("location").getAsJsonObject();
                    log.debug("resLoc: {}", resLoc);
                    log.debug("mappedLoc: {}", mappedLoc);
                    ptChecker.check(resLoc, mappedLoc);
                }
            }
        }
        assertEquals("(This means the camera we're using is out or that there's nothing interesting going on --" +
                " letting you know) insufficient images with data",
                5, imagesProcessed);
    }

    public static VantiqResponse querySource(Map<String,Object> params) {
        return vantiq.query(SOURCE_NAME, params);
    }

    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
        if (insertResponse.isSuccess()) {
            core = new ObjectRecognitionCore(SOURCE_NAME, testAuthToken, testVantiqServer, MODEL_DIRECTORY);;
            core.start(CORE_START_TIMEOUT);
        }
    }

    public static Map<String, Object> buildConverterSpec(Double[][] srcPts, Double[][] dstPts, boolean resultsAsGeoJSON) {
        assertEquals("Bad source point list",
                (long) ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES, srcPts.length);
        assertEquals("Bad destination point list",
                (long) ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES, dstPts.length);

        Map<String, Object> mapper = new HashMap<>();
        List<Map> imgCoords = new ArrayList<>();
        List<Map> mappedCoords = new ArrayList<>();

        mapper.put(ObjectRecognitionConfigHandler.IMAGE_COORDINATES, imgCoords);
        mapper.put(ObjectRecognitionConfigHandler.MAPPED_COORDINATES, mappedCoords);
        mapper.put(ObjectRecognitionConfigHandler.RESULTS_AS_GEOJSON, resultsAsGeoJSON);

        // Now, let's construct some reasonable coordinate lists to validate setup.

        for (int i = 0; i < ObjectRecognitionConfigHandler.REQUIRED_MAPPING_COORDINATES; i++) {
            Map<String, Object> aCoord = new HashMap<>();

            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X, srcPts[i][0]);
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_Y, srcPts[i][1]);
            imgCoords.add(aCoord);

            aCoord = new HashMap<>();
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_X, dstPts[i][0]);
            aCoord.put(ObjectRecognitionConfigHandler.COORDINATE_Y, dstPts[i][1]);
            mappedCoords.add(aCoord);
        }
        log.debug("Constructed Mapper: {}", mapper);
        return mapper;
    }

    public static Map<String,Object> createSourceDef(Map<String, Object> imgConvSpec) {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
        Map<String,Object> sourceConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> objRecConfig = new LinkedHashMap<String,Object>();
        Map<String,Object> dataSource = new LinkedHashMap<String,Object>();
        Map<String,Object> general = new LinkedHashMap<String,Object>();
        Map<String,Object> neuralNet = new LinkedHashMap<String,Object>();

        // Setting up dataSource config options
        dataSource.put("camera", IP_CAMERA_ADDRESS);
        dataSource.put("type", "network");

        // Setting up general config options
        general.put("allowQueries", true);

        // Setting up neuralNet config options
        neuralNet.put("pbFile", PB_FILE);
        neuralNet.put("metaFile", META_FILE);
        neuralNet.put("type", "yolo");
        neuralNet.put("saveImage", "local");
        neuralNet.put("outputDir", OUTPUT_DIR);

        // Placing dataSource, general, and neuralNet config options in "objRecConfig"
        objRecConfig.put("dataSource", dataSource);
        objRecConfig.put("general", general);
        objRecConfig.put("neuralNet", neuralNet);

        if (imgConvSpec != null) {
            Map<String, Object> postProcessor = new HashMap<>();
            postProcessor.put(ObjectRecognitionConfigHandler.LOCATION_MAPPER, imgConvSpec);
            objRecConfig.put(ObjectRecognitionConfigHandler.POST_PROCESSOR, postProcessor);
        }

        // Putting objRecConfig in the source configuration
        sourceConfig.put("objRecConfig", objRecConfig);

        // Setting up the source definition
        sourceDef.put("config", sourceConfig);
        sourceDef.put("name", SOURCE_NAME);
        sourceDef.put("type", "ObjectRecognition");
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        log.debug("Source def'n: {}", sourceDef);
        return sourceDef;
    }

    public static void deleteFilesFromVantiq() {
        vantiq.delete(VANTIQ_DOCUMENTS, new HashMap());
        vantiq.delete(VANTIQ_IMAGES, new HashMap());
    }

    // The following are various mapper specifications & checkers

    abstract class BasePointChecker implements PointChecker {
        void checkNull(String[] labels, JsonObject target, String targetName) {
            for (String label : labels) {
                assertNotNull("No " + label + " -- " + targetName, target.get(label));
            }
        }

        abstract public void check(JsonObject resLoc, JsonObject mappedLoc);
    }

    // X + 55, y+50 is the conversion
    static Double[][] plus50SrcPts = new Double[][] { {1.0d,1d}, {2d,1d}, {3d,3d}, {4d,3d}};
    static Double[][] plus50DstPts = new Double[][] { {56.0d,51d}, {57d,51d}, {58d,53d}, {59d,53d}};

    class Plus50Checker extends BasePointChecker {

        public void check(JsonObject resLoc, JsonObject mappedLoc) {
            checkNull(new String[] {"top", "left", "bottom", "right"}, resLoc, "result");
            checkNull(new String[] {"top", "left", "bottom", "right"}, mappedLoc, "mapped");

            assertEquals("mismatch top", resLoc.get("top").getAsDouble() + 50d,
                    mappedLoc.get("top").getAsDouble(), ACCEPTABLE_DELTA);
            assertEquals("mismatch bottom", resLoc.get("bottom").getAsDouble() + 50d,
                    mappedLoc.get("bottom").getAsDouble(), ACCEPTABLE_DELTA);
            assertEquals("mismatch left", resLoc.get("left").getAsDouble() + 55d,
                    mappedLoc.get("left").getAsDouble(), ACCEPTABLE_DELTA);
            assertEquals("mismatch right", resLoc.get("right").getAsDouble() + 55d,
                    mappedLoc.get("right").getAsDouble(), ACCEPTABLE_DELTA);
        }
    }

    /**
     * Check that doing the same plus50 work but converting to GeoJson works as expected...
     */
    class Plus50GJChecker extends BasePointChecker {

        public void check(JsonObject resLoc, JsonObject mappedLoc) {
            checkNull(new String[] {"top", "left", "bottom", "right"}, resLoc, "result");
            checkNull(new String[] {"topLeft", "bottomRight"}, mappedLoc, "mapped");

            JsonObject mappedTl= mappedLoc.get("topLeft").getAsJsonObject();
            JsonObject mappedBr = mappedLoc.get("bottomRight").getAsJsonObject();
            checkNull(new String[] {"type", "coordinates"}, mappedTl, "topLeft");
            checkNull(new String[] {"type", "coordinates"}, mappedBr, "bottomRight");

            assertEquals("Mapped top left isn't a point", "Point", mappedTl.get("type").getAsString());
            assertEquals("Mapped bottom right isn't a point", "Point", mappedBr.get("type").getAsString());

            // Remember that in GeoJSON, coordinates come as [y, x].  So top (y) is first array element...
            assertEquals("mismatch top", resLoc.get("top").getAsDouble() + 50d,
                    mappedTl.get("coordinates").getAsJsonArray().get(0).getAsDouble(),
                    ACCEPTABLE_DELTA);
            assertEquals("mismatch bottom", resLoc.get("bottom").getAsDouble() + 50d,
                    mappedBr.get("coordinates").getAsJsonArray().get(0).getAsDouble(),
                    ACCEPTABLE_DELTA);

            assertEquals("mismatch left", resLoc.get("left").getAsDouble() + 55d,
                    mappedTl.get("coordinates").getAsJsonArray().get(1).getAsDouble(),
                    ACCEPTABLE_DELTA);
            assertEquals("mismatch right", resLoc.get("right").getAsDouble() + 55d,
                    mappedBr.get("coordinates").getAsJsonArray().get(1).getAsDouble(),
                    ACCEPTABLE_DELTA);
        }
    }

    // X unchanged, y doubled is the conversion
    static Double[][] xIdentYTimes2SrcPts = new Double[][] { {1.0d,1d}, {2d,1d}, {3d,3d}, {4d,3d}};
    static Double[][] xIdentYTimes2DstPts = new Double[][] { {1.0d,2d}, {2d,2d}, {3d,6d}, {4d,6d}};

    class XIdentYTimes2 extends BasePointChecker {
        public void check(JsonObject resLoc, JsonObject mappedLoc) {
            checkNull(new String[] {"top", "left", "bottom", "right"}, resLoc, "result");
            checkNull(new String[] {"top", "left", "bottom", "right"}, mappedLoc, "mapped");

            assertEquals("mismatch top", resLoc.get("top").getAsDouble() * 2d,
                    mappedLoc.get("top").getAsDouble(), ACCEPTABLE_DELTA);
            assertEquals("mismatch bottom", resLoc.get("bottom").getAsDouble() * 2d,
                    mappedLoc.get("bottom").getAsDouble(), ACCEPTABLE_DELTA);
            assertEquals("mismatch left", resLoc.get("left").getAsDouble(),
                    mappedLoc.get("left").getAsDouble(), ACCEPTABLE_DELTA);
            assertEquals("mismatch right", resLoc.get("right").getAsDouble(),
                    mappedLoc.get("right").getAsDouble(), ACCEPTABLE_DELTA);
        }
    }

    class XIdentYTimes2GJ extends BasePointChecker {

        public void check(JsonObject resLoc, JsonObject mappedLoc) {
            checkNull(new String[] {"top", "left", "bottom", "right"}, resLoc, "result");
            checkNull(new String[] {"topLeft", "bottomRight"}, mappedLoc, "mapped");

            JsonObject mappedTl= mappedLoc.get("topLeft").getAsJsonObject();
            JsonObject mappedBr = mappedLoc.get("bottomRight").getAsJsonObject();
            checkNull(new String[] {"type", "coordinates"}, mappedTl, "topLeft");
            checkNull(new String[] {"type", "coordinates"}, mappedBr, "bottomRight");

            assertEquals("Mapped top left isn't a point", "Point", mappedTl.get("type").getAsString());
            assertEquals("Mapped bottom right isn't a point", "Point", mappedBr.get("type").getAsString());

            // Remember that in GeoJSON, coordinates come as [y, x].  So top (y) is first array element...
            assertEquals("mismatch top", resLoc.get("top").getAsDouble() * 2d,
                    mappedTl.get("coordinates").getAsJsonArray().get(0).getAsDouble(),
                    ACCEPTABLE_DELTA);
            assertEquals("mismatch bottom", resLoc.get("bottom").getAsDouble() * 2d,
                    mappedBr.get("coordinates").getAsJsonArray().get(0).getAsDouble(),
                    ACCEPTABLE_DELTA);

            assertEquals("mismatch left", resLoc.get("left").getAsDouble(),
                    mappedTl.get("coordinates").getAsJsonArray().get(1).getAsDouble(),
                    ACCEPTABLE_DELTA);
            assertEquals("mismatch right", resLoc.get("right").getAsDouble(),
                    mappedBr.get("coordinates").getAsJsonArray().get(1).getAsDouble(),
                    ACCEPTABLE_DELTA);
        }
    }
}
