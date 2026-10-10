package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
    @JsonProperty("place_id")
    @JsonAlias("placeId")
    @NotBlank
    String placeId, 
    @JsonProperty("rating")
    @NotNull @Min(1) @Max(5)
    Integer rating, 

    @JsonProperty("text")
    @NotBlank @Size(max = 2000)
    String text)
{
    
}
