package net.bp4k.locationlens.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.bp4k.locationlens.dto.CriteriaRequest;
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

    @GetMapping("/search")
    public List<PlaceResponse> search(
            @RequestParam(name = "location_text", required = false) String locationText,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(name = "radius_meters") Integer radiusMeters,
            @RequestParam(name = "place_type", required = false) PlaceType placeType) {
        return placeSearchService.search(new CriteriaRequest(
                locationText, latitude, longitude, radiusMeters, placeType));
    }

    @GetMapping("/{placeId}")
    public PlaceResponse getPlaceDetails(@PathVariable String placeId) {
        return placeSearchService.getPlaceResponse(placeId);
    }
}
