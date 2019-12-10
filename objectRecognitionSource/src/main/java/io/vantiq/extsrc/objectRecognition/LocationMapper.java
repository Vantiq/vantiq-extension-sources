/*
 * Copyright (c) 2019 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition;

import io.vantiq.extsrc.objectRecognition.imageRetriever.CoordinateConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationMapper {
    public boolean resultsAsGeoJSON;
    public CoordinateConverter cc;

    LocationMapper(BigDecimal[][] source, BigDecimal[][] destination, boolean convertToGeoJSON) {
        cc = new CoordinateConverter(source, destination);
        resultsAsGeoJSON = convertToGeoJSON;
    }

    LocationMapper(BigDecimal[][] source, BigDecimal[][] destination) {
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
                Float yt = null;
                Float xl = null;
                Float yb = null;
                Float xr = null;
                Object o = box.get("location");
                Map loc;
                if (o instanceof Map) {
                    loc = (Map) o;
                } else {
                    throw new IllegalArgumentException(this.getClass().getName() + ".missingloc: Input missing location.");
                }
                o = loc.get("top");
                if (o instanceof Number) {
                    yt = ((Number) o).floatValue();
                }
                o = loc.get("left");
                if (o instanceof Number) {
                    xl = ((Number) o).floatValue();
                }
                o = loc.get("bottom");
                if (o instanceof Number) {
                    yb = ((Number) o).floatValue();
                }
                o = loc.get("right");
                if (o instanceof Number) {
                    xr = ((Number) o).floatValue();
                }
                if (yt == null || yb == null || xr == null || xl == null) {
                    throw new IllegalArgumentException(this.getClass().getName() +
                            ".missingcoords: Input missing coordinates.");
                }
                BigDecimal[] topLeft = cc.convert(new BigDecimal[]{new BigDecimal(xl), new BigDecimal(yt)});
                BigDecimal[] bottomRight = cc.convert(new BigDecimal[]{new BigDecimal(xr), new BigDecimal(yb)});

                // Now, construct our output map to add to the list...
                outBox.put("confidence", box.get("confidence"));
                outBox.put("label", box.get("label"));
                Map<String, Object> outloc = new HashMap<>();
                if (!resultsAsGeoJSON) {
                    outloc.put("top", topLeft[1].doubleValue());
                    outloc.put("left", topLeft[0].doubleValue());
                    outloc.put("bottom", bottomRight[1].doubleValue());
                    outloc.put("right", bottomRight[0].doubleValue());
                } else {
                    Map<String, Object> gjEnt = new HashMap<>();
                    gjEnt.put("type", "Point");
                    gjEnt.put("coordinates", new Double[]{topLeft[1].doubleValue(), topLeft[0].doubleValue()});
                    outloc.put("topLeft", gjEnt);
                    gjEnt = new HashMap<>();
                    gjEnt.put("type", "Point");
                    gjEnt.put("coordinates", new Double[]{bottomRight[1].doubleValue(), bottomRight[0].doubleValue()});
                    outloc.put("bottomRight", gjEnt);
                }
                outBox.put("location", outloc);
            }
            output.add(outBox);
        }
        return output;
    }
}
