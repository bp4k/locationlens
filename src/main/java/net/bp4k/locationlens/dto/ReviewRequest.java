package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewRequest(
    @JsonProperty("place_id")
    @JsonAlias("placeId")
    String placeId, 
    @JsonProperty("rating")
    Integer rating, 
    @JsonProperty("text")
    String text)
{
    
}
