package net.bp4k.locationlens.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bp4k.locationlens.model.PlaceType;

/** Public place contract for future search/detail endpoints. Coordinates are decimal degrees. */
public record PlaceResponse(
    @JsonProperty("place_id") String placeId,
    @JsonProperty("name") String name,
    @JsonProperty("address") String address,
    @JsonProperty("latitude") Double latitude,
    @JsonProperty("longitude") Double longitude,
    @JsonProperty("types") List<PlaceType> types) {

    public PlaceResponse {
        types = List.copyOf(types);
    }
}
