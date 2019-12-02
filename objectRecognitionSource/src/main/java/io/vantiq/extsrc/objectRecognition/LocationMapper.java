package io.vantiq.extsrc.objectRecognition;

import io.vantiq.extsrc.objectRecognition.imageRetriever.CoordinateConverter;

public class LocationMapper {
    public boolean resultsAsGeoJSON;
    public CoordinateConverter cc;

    LocationMapper(Float[][] source, Float[][] destination, boolean convertToGeoJSON) {
        cc = new CoordinateConverter(source, destination);
        resultsAsGeoJSON = convertToGeoJSON;
    }

    LocationMapper(Float[][] source, Float[][] destination) {
        cc = new CoordinateConverter(source, destination);
        resultsAsGeoJSON = false;
    }

}
