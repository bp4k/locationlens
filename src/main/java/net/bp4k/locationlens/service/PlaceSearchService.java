package net.bp4k.locationlens.service;

import org.springframework.stereotype.Service;

import java.util.List;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.repo.PlaceRepository;
import net.bp4k.locationlens.dto.CriteriaRequest;
import net.bp4k.locationlens.dto.PlaceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements place search and lookup.
 * @author Advaith Menon
 */
@Service
public class PlaceSearchService {

    private final PlaceRepository placeRepository;
    private final GooglePlaceSearchClient googlePlaces;

    public PlaceSearchService(PlaceRepository placeRepository, GooglePlaceSearchClient googlePlaces) {
        this.placeRepository = placeRepository;
        this.googlePlaces = googlePlaces;
    }

    /** The public search uses live places, even when the local catalog is empty. */
    public List<PlaceResponse> search(CriteriaRequest criteria) {
        criteria.validate();
        return googlePlaces.search(criteria);
    }

    /** Local entries retain their details; Google IDs are resolved by the provider. */
    @Transactional(readOnly = true)
    public PlaceResponse getPlaceResponse(String placeId) {
        if (placeId == null || placeId.isBlank() || placeId.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid place ID");
        }
        Place local = getPlaceDetails(placeId);
        return local != null ? PlaceResponse.from(local) : googlePlaces.getPlaceDetails(placeId);
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
