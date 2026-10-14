package net.bp4k.locationlens.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.dto.PlaceResponse;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.service.PlaceSearchService;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {
    private final PlaceSearchService placeSearchService;

    public PlaceController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    // Search still uses the team's sample catalog until live search is connected.
    // Keep one catalog so every result has a matching detail response.
    private static final List<PlaceResponse> SAMPLE_PLACES = List.of(
        new PlaceResponse("dummy-1", "Piedmont Park", "400 Park Dr NE, Atlanta, GA",
            33.7850, -84.3738, List.of(PlaceType.PARK)),
        new PlaceResponse("dummy-2", "Georgia Aquarium", "225 Baker St NW, Atlanta, GA",
            33.7634, -84.3951, List.of(PlaceType.ATTRACTION)),
        new PlaceResponse("dummy-3", "Ponce City Market", "675 Ponce De Leon Ave NE, Atlanta, GA",
            33.7725, -84.3655, List.of(PlaceType.RESTAURANT)));

    @GetMapping("/search")
    public List<PlaceResponse> search() {
        return SAMPLE_PLACES;
    }

    @GetMapping("/{placeId}")
    public PlaceResponse getPlaceDetails(@PathVariable("placeId") String placeId) {
        var place = placeSearchService.getPlaceDetails(placeId);
        if (place != null) {
            return PlaceResponse.from(place);
        }
        return SAMPLE_PLACES.stream()
            .filter(sample -> sample.placeId().equals(placeId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
    }
}
