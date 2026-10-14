package net.bp4k.locationlens.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.model.PlaceType;

/** Search around either a named location or a coordinate pair, in meters. */
public record CriteriaRequest(String locationText, Double latitude, Double longitude,
        Integer radiusMeters, PlaceType placeType) {

    public void validate() {
        boolean text = locationText != null && !locationText.isBlank();
        boolean coordinates = latitude != null || longitude != null;
        if (text == coordinates) {
            invalid("Enter either a location or a latitude/longitude pair");
        }
        if (text && locationText.length() > 500) {
            invalid("Location must be at most 500 characters");
        }
        if (coordinates && (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
            invalid("Enter a valid latitude and longitude together");
        }
        if (radiusMeters == null || radiusMeters < 1 || radiusMeters > 50_000) {
            invalid("Search radius must be a whole number between 1 and 50000 meters");
        }
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
