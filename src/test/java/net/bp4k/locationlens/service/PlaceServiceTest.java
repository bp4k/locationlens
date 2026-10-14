package net.bp4k.locationlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.exception.DuplicatePlaceException;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.repo.PlaceRepository;

class PlaceServiceTest {

    private PlaceRepository placeRepository;
    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        placeService = new PlaceService(placeRepository);
    }

    @Test
    void addPlaceSavesAndReturnsTheNewPlace() {
        when(placeRepository.existsById("hello")).thenReturn(false);
        when(placeRepository.findByNameAndAddressAndLatitudeAndLongitude(
                "Cafe", "1 Main St", 1.0, 1.0))
                .thenReturn(List.of());
        when(placeRepository.saveAndFlush(any(Place.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Place result = placeService.addPlace("hello", "Cafe", "1 Main St", 1.0, 1.0,
                Set.of(PlaceType.BATHROOM));

        assertEquals("hello", result.getPlaceId());
        assertEquals("Cafe", result.getName());
        assertEquals(Set.of(PlaceType.BATHROOM), result.getTypes());
    }

    @Test
    void addPlaceThrowsWhenPlaceIdAlreadyExists() {
        when(placeRepository.existsById("hello")).thenReturn(true);

        DuplicatePlaceException ex = assertThrows(DuplicatePlaceException.class,
                () -> placeService.addPlace("hello", "Different name", "Different address",
                        99.0, 99.0, Set.of(PlaceType.PARK)));

        assertEquals("A place with ID 'hello' already exists", ex.getMessage());
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void addPlaceThrowsWhenAttributesAlreadyExistUnderADifferentPlaceId() {
        Place existing = new Place("1", "Rest Stop", "1 Main St", 1.0, 1.0,
                Set.of(PlaceType.BATHROOM));
        when(placeRepository.existsById("2")).thenReturn(false);
        when(placeRepository.findByNameAndAddressAndLatitudeAndLongitude(
                "Rest Stop", "1 Main St", 1.0, 1.0))
                .thenReturn(List.of(existing));

        DuplicatePlaceException ex = assertThrows(DuplicatePlaceException.class,
                () -> placeService.addPlace("2", "Rest Stop", "1 Main St", 1.0, 1.0,
                        Set.of(PlaceType.BATHROOM)));

        assertEquals("A place with the same name, address, location, and types already exists",
                ex.getMessage());
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void addPlaceAllowsSameNameAddressAndLocationWhenTypesDiffer() {
        Place existing = new Place("1", "Rest Stop", "1 Main St", 1.0, 1.0,
                Set.of(PlaceType.BATHROOM));
        when(placeRepository.existsById("2")).thenReturn(false);
        when(placeRepository.findByNameAndAddressAndLatitudeAndLongitude(
                "Rest Stop", "1 Main St", 1.0, 1.0))
                .thenReturn(List.of(existing));
        when(placeRepository.saveAndFlush(any(Place.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Place result = placeService.addPlace("2", "Rest Stop", "1 Main St", 1.0, 1.0,
                Set.of(PlaceType.PARK));

        assertEquals("2", result.getPlaceId());
    }

    @Test
    void addPlaceTranslatesConstraintViolationIntoDuplicatePlace() {
        when(placeRepository.existsById("hello")).thenReturn(false);
        when(placeRepository.findByNameAndAddressAndLatitudeAndLongitude(
                "Cafe", "1 Main St", 1.0, 1.0))
                .thenReturn(List.of());
        when(placeRepository.saveAndFlush(any(Place.class)))
                .thenThrow(new DataIntegrityViolationException("pk_place"));

        DuplicatePlaceException ex = assertThrows(DuplicatePlaceException.class,
                () -> placeService.addPlace("hello", "Cafe", "1 Main St", 1.0, 1.0,
                        Set.of(PlaceType.BATHROOM)));

        assertSame(DuplicatePlaceException.class, ex.getClass());
        assertEquals("A place with ID 'hello' already exists", ex.getMessage());
    }
}
