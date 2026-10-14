package net.bp4k.locationlens.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.Set;
import java.util.List;
import net.bp4k.locationlens.dto.CriteriaRequest;
import net.bp4k.locationlens.dto.PlaceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.service.PlaceSearchService;

class PlaceControllerTest {
    private PlaceSearchService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(PlaceSearchService.class);
        mvc = standaloneSetup(new PlaceController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void everyCurrentSearchResultHasMatchingDetails() throws Exception {
        var place = new PlaceResponse("provider-place", "Park", "Atlanta", 33.0, -84.0, List.of(PlaceType.PARK));
        when(service.search(new CriteriaRequest("Atlanta", null, null, 1000, null))).thenReturn(List.of(place));
        when(service.getPlaceResponse(place.placeId())).thenReturn(place);
        var mapper = JsonMapper.builder().build();
        var results = mapper.readTree(mvc.perform(get("/api/v1/places/search")
                .param("location_text", "Atlanta").param("radius_meters", "1000"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andReturn().getResponse().getContentAsString());
        for (var result : results) {
            String id = result.get("place_id").asString();
            mvc.perform(get("/api/v1/places/{placeId}", id))
                .andExpect(status().isOk())
                .andExpect(content().json(result.toString()));
        }
    }

    @Test
    void storedPlaceReturnsTheFrontendDtoIncludingTypesAndCoordinates() throws Exception {
        when(service.getPlaceResponse("stored-place")).thenReturn(PlaceResponse.from(new Place("stored-place", "Stored Park",
            "Atlanta", 0.0, -84.5, Set.of(PlaceType.ATTRACTION, PlaceType.PARK))));
        mvc.perform(get("/api/v1/places/stored-place"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"place_id":"stored-place","name":"Stored Park","address":"Atlanta",
                 "latitude":0.0,"longitude":-84.5,"types":["PARK","ATTRACTION"]}
                """));
    }

    @Test
    void detailsReturnUpdatedServiceData() throws Exception {
        when(service.getPlaceResponse("dummy-1")).thenReturn(PlaceResponse.from(new Place("dummy-1", "Updated Park",
            "New address", 33.0, -84.0, Set.of(PlaceType.PARK))));
        mvc.perform(get("/api/v1/places/dummy-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Park"));
    }

    @Test
    void unknownPlaceReturns404RatherThanAnEmpty400Response() throws Exception {
        when(service.getPlaceResponse("missing")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
        mvc.perform(get("/api/v1/places/missing"))
            .andExpect(status().isNotFound());
    }
}
