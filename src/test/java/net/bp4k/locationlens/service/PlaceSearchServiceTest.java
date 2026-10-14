package net.bp4k.locationlens.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.repo.PlaceRepository;

class PlaceSearchServiceTest {

    private static final String PLACE_ID = "place-123";

    private PlaceRepository placeRepository;
    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        placeSearchService = new PlaceSearchService(placeRepository);
    }

    @Test
    void searchDelegatesToRepositoryWhenAllFiltersAreNull() {
        List<Place> places = List.of(new Place(PLACE_ID, "Cafe", "1 Main St",
                0.0, 0.0, Set.of(PlaceType.RESTAURANT)));
        when(placeRepository.search(null, null, null, null, null)).thenReturn(places);

        List<Place> result = placeSearchService.search(null, null, null, null, null);

        assertSame(places, result);
    }

    @Test
    void searchDelegatesToRepositoryWhenOnlyAddressAndTypeAreSet() {
        List<Place> places = List.of(new Place(PLACE_ID, "Hotel", "1 Main St, Atlanta",
                0.0, 0.0, Set.of(PlaceType.HOTEL)));
        when(placeRepository.search(null, null, null, "Atlanta", PlaceType.HOTEL))
                .thenReturn(places);

        List<Place> result = placeSearchService.search(null, null, null, "Atlanta", PlaceType.HOTEL);

        assertSame(places, result);
    }

    @Test
    void searchDelegatesToRepositoryWhenAllFiltersAreSet() {
        List<Place> places = List.of(new Place(PLACE_ID, "Park", "1 Main St, Atlanta",
                23.0, 12.0, Set.of(PlaceType.PARK)));
        when(placeRepository.search(23.0, 12.0, 4.0, "Atlanta", PlaceType.PARK))
                .thenReturn(places);

        List<Place> result = placeSearchService.search(23.0, 12.0, 4.0, "Atlanta", PlaceType.PARK);

        assertSame(places, result);
    }

    /**
     * Every way to set exactly one or exactly two of latitude/longitude/radius,
     * leaving the rest {@code null} &mdash; none of these are a valid group state.
     */
    static Stream<Arguments> partiallySetLocationGroups() {
        return Stream.of(
                Arguments.of(23.0, null, null),
                Arguments.of(null, 12.0, null),
                Arguments.of(null, null, 4.0),
                Arguments.of(23.0, 12.0, null),
                Arguments.of(23.0, null, 4.0),
                Arguments.of(null, 12.0, 4.0));
    }

    @ParameterizedTest
    @MethodSource("partiallySetLocationGroups")
    void searchThrowsWhenLocationGroupIsOnlyPartiallySet(Double latitude, Double longitude, Double radius) {
        assertThrows(IllegalArgumentException.class,
                () -> placeSearchService.search(latitude, longitude, radius, null, null));

        verify(placeRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void searchThrowsWhenOnlyRadiusIsSetRegardlessOfOtherFilters() {
        assertThrows(IllegalArgumentException.class,
                () -> placeSearchService.search(null, null, 4.0, "Atlanta", PlaceType.HOTEL));

        verify(placeRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void getPlaceDetailsReturnsThePlaceWhenItExists() {
        Place place = new Place(PLACE_ID, "Cafe", "1 Main St", 0.0, 0.0, Set.of(PlaceType.RESTAURANT));
        when(placeRepository.findById(PLACE_ID)).thenReturn(Optional.of(place));

        assertSame(place, placeSearchService.getPlaceDetails(PLACE_ID));
    }

    @Test
    void getPlaceDetailsReturnsNullWhenThePlaceDoesNotExist() {
        when(placeRepository.findById(PLACE_ID)).thenReturn(Optional.empty());

        assertNull(placeSearchService.getPlaceDetails(PLACE_ID));
    }
}
