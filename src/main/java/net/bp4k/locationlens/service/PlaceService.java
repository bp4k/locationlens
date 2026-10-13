package net.bp4k.locationlens.service;

import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.exception.DuplicatePlaceException;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.repo.PlaceRepository;

/**
 * Implements place creation.
 * @author Advaith Menon
 */
@Service
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * Adds a new place.
     * @param placeId the unique ID of the place
     * @param name the place's name
     * @param address the place's address
     * @param latitude the place's latitude
     * @param longitude the place's longitude
     * @param types the place's categories
     * @return the saved place
     * @throws DuplicatePlaceException if a place with {@code placeId} already
     * exists, or if a place with the same name, address, latitude, longitude,
     * and types already exists
     */
    public Place addPlace(String placeId, String name, String address,
            Double latitude, Double longitude, Set<PlaceType> types) {
        if (placeRepository.existsById(placeId)) {
            throw new DuplicatePlaceException(
                    "A place with ID '" + placeId + "' already exists");
        }

        boolean duplicateAttributes = placeRepository
                .findByNameAndAddressAndLatitudeAndLongitude(name, address, latitude, longitude)
                .stream()
                .anyMatch(existing -> existing.getTypes().equals(types));
        if (duplicateAttributes) {
            throw new DuplicatePlaceException(
                    "A place with the same name, address, location, and types already exists");
        }

        Place place = new Place(placeId, name, address, latitude, longitude, types);
        try {
            return placeRepository.saveAndFlush(place);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicatePlaceException(
                    "A place with ID '" + placeId + "' already exists");
        }
    }
}
