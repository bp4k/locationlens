package net.bp4k.locationlens.service;

import org.springframework.stereotype.Service;

import java.util.List;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.repo.PlaceRepository;

/**
 * Implements place search and lookup.
 * @author Advaith Menon
 */
@Service
public class PlaceSearchService {

    private final PlaceRepository placeRepository;

    public PlaceSearchService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * Searches for places matching the given (AND-combined) filters. The
     * latitude/longitude/radius location filter is a group: either all three
     * must be provided, or all three must be {@code null}.
     * @param latitude the latitude of the search origin, or {@code null} to skip the location filter
     * @param longitude the longitude of the search origin, or {@code null} to skip the location filter
     * @param radius the search radius, or {@code null} to skip the location filter
     * @param address text to search for within the place's address, or {@code null} to skip this filter
     * @param type a type the place must have, or {@code null} to skip this filter
     * @return places matching every active filter
     * @throws IllegalArgumentException if only some of latitude, longitude,
     * and radius are provided
     */
    public List<Place> search(Double latitude, Double longitude, Double radius,
            String address, PlaceType type) {
        boolean anyLocationParamSet = latitude != null || longitude != null || radius != null;
        boolean allLocationParamsSet = latitude != null && longitude != null && radius != null;
        if (anyLocationParamSet && !allLocationParamsSet) {
            throw new IllegalArgumentException(
                    "latitude, longitude, and radius must either all be provided or all be null");
        }

        return placeRepository.search(latitude, longitude, radius, address, type);
    }

    /**
     * Gets the details of a place.
     * @param placeId the ID of the place
     * @return the place, or {@code null} if no place with that ID exists
     */
    public Place getPlaceDetails(String placeId) {
        return placeRepository.findById(placeId).orElse(null);
    }
}
