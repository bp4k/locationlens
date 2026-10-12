package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
/*
export interface SearchCriteriaModel {
  locationText?: string;
  latitude?: number;
  longitude?: number;
  radiusMeters: number;
  placeType?: PlaceTypeModel;
}
*/

public record CriteriaRequest
(
  @JsonProperty("locationText")
  String locationText,
  @JsonProperty("latitude")
  Double latitude,
  @JsonProperty("longitude")
  Double  longitude,
  @JsonProperty("radiusMeters")
  Integer radiusMeters,
  @JsonProperty("placeType")
  String[] placeType
) {
    
}
