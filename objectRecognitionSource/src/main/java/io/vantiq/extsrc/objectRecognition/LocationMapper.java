/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import io.vantiq.extsrc.objectRecognition.imageRetriever.CoordinateConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationMapper {
    public boolean resultsAsGeoJSON;
    public CoordinateConverter cc;

    LocationMapper(Double[][] source, Double[][] destination, boolean convertToGeoJSON) {
        cc = new CoordinateConverter(source, destination);
        resultsAsGeoJSON = convertToGeoJSON;
    }

    LocationMapper(Double[][] source, Double[][] destination) {
        cc = new CoordinateConverter(source, destination);
        resultsAsGeoJSON = false;
    }

    /**
     * Convert results from image coordinates to mapped coords using defined converter.
     * If this mapper's been configured to return results as GeoJSON, top + left coordinates
     * will become a topLeft GeoJSON, and bottom + right + right will become bottomRight.
     *
     * Example input:
     *     [
     *         {confidence:0.8445639,
     *          location:{
     *              top:255.70024,
     *              left:121.859344,
     *              bottom:372.2343,
     *              right:350.1204
     *            },
     *          label: "keyboard"
     *         },
     *         {confidence:0.7974271,
     *          location:{
     *              top:91.255974,
     *              left:164.41359,
     *              bottom:275.69666,
     *              right:350.50714
     *           },
     *          label:"tvmonitor"
     *          }
     *      ]
     *
     * @return copy of the input with appropriate conversions applied
     */
    List<Map<String, ?>> mapResults(List<Map<String, ?>> input) {

        // For each element in list, extract top + left & bottom + right coordinates.  These distinct
        // values represent the y, x (respectively) coordinates on the image.  We will convert these pairs
        // using the converter, then produce the output as required.

        List<Map<String, ?>> output = new ArrayList<>();
        for (Map box : input) {
            Map<String, Object> outBox = new HashMap<>();
            if (!box.isEmpty()) {
                // Tests sometimes send empty data...
                Double yt = null;
                Double xl = null;
                Double yb = null;
                Double xr = null;
                Object o = box.get("location");
                Map loc;
                if (o instanceof Map) {
                    loc = (Map) o;
                } else {
                    throw new IllegalArgumentException(this.getClass().getName() + ".missingloc: Input missing location.");
                }
                o = loc.get("top");
                if (o instanceof Number) {
                    yt = ((Number) o).doubleValue();
                }
                o = loc.get("left");
                if (o instanceof Number) {
                    xl = ((Number) o).doubleValue();
                }
                o = loc.get("bottom");
                if (o instanceof Number) {
                    yb = ((Number) o).doubleValue();
                }
                o = loc.get("right");
                if (o instanceof Number) {
                    xr = ((Number) o).doubleValue();
                }
                if (yt == null || yb == null || xr == null || xl == null) {
                    throw new IllegalArgumentException(this.getClass().getName() +
                            ".missingcoords: Input missing coordinates.");
                }
                Double[] topLeft = cc.convert(new Double[]{xl, yt});
                Double[] bottomRight = cc.convert(new Double[]{xr, yb});

                // Now, construct our output map to add to the list...
                outBox.put("confidence", box.get("confidence"));
                outBox.put("label", box.get("label"));
                Map<String, Object> outloc = new HashMap<>();
                if (!resultsAsGeoJSON) {
                    outloc.put("top", topLeft[1]);
                    outloc.put("left", topLeft[0]);
                    outloc.put("bottom", bottomRight[1]);
                    outloc.put("right", bottomRight[0]);
                } else {
                    Map<String, Object> gjEnt = new HashMap<>();
                    gjEnt.put("type", "Point");
                    gjEnt.put("coordinates", new Double[]{topLeft[1], topLeft[0]});
                    outloc.put("topLeft", gjEnt);
                    gjEnt = new HashMap<>();
                    gjEnt.put("type", "Point");
                    gjEnt.put("coordinates", new Double[]{bottomRight[1], bottomRight[0]});
                    outloc.put("bottomRight", gjEnt);
                }
                outBox.put("location", outloc);
            }
            output.add(outBox);
        }
        return output;
    }

}
