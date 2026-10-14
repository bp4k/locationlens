package net.bp4k.locationlens.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.entity.Place;

/** Public search/detail contract. Coordinates are decimal degrees. */
public record PlaceResponse(
    @JsonProperty("place_id") String placeId,
    @JsonProperty("name") String name,
    @JsonProperty("address") String address,
    @JsonProperty("latitude") Double latitude,
    @JsonProperty("longitude") Double longitude,
    @JsonProperty("types") List<PlaceType> types) {

    public static PlaceResponse from(Place place) {
        return new PlaceResponse(place.getPlaceId(), place.getName(), place.getAddress(),
            place.getLatitude(), place.getLongitude(), place.getTypes().stream().sorted().toList());
    }

    public PlaceResponse {
        types = List.copyOf(types);
    }

}
