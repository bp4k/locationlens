package net.bp4k.locationlens.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;

/**
 * @author Advaith Menon
 */
@DataJpaTest
class PlaceRepositoryTest {

    @Autowired
    private PlaceRepository placeRepository;

    private Place center;
    private Place onBoundary;
    private Place justOutside;
    private Place farAway;

    @BeforeEach
    void setUp() {
        // Centered at the origin, radius 5, using the classic 3-4-5 right triangle
        // so the boundary case lands on an exact value rather than a rounding edge.
        center = placeRepository.save(new Place(
                "center", "Origin Cafe", "1 Main St, Atlanta", 0.0, 0.0,
                Set.of(PlaceType.RESTAURANT)));
        onBoundary = placeRepository.save(new Place(
                "on-boundary", "Boundary Diner", "2 Main St, Atlanta", 3.0, 4.0,
                Set.of(PlaceType.RESTAURANT)));
        justOutside = placeRepository.save(new Place(
                "just-outside", "Outside Park", "3 Oak St, Macon", 3.0, 4.01,
                Set.of(PlaceType.PARK)));
        farAway = placeRepository.save(new Place(
                "far-away", "Far Away Hotel", "9 Pine St, Savannah", 100.0, 100.0,
                Set.of(PlaceType.HOTEL)));
    }

    @Test
    void findWithinRadiusIncludesTheCenterPoint() {
        List<Place> results = placeRepository.findWithinRadius(0.0, 0.0, 5.0);

        assertThat(results).contains(center);
    }

    @Test
    void findWithinRadiusIncludesPointsExactlyOnTheBoundary() {
        List<Place> results = placeRepository.findWithinRadius(0.0, 0.0, 5.0);

        assertThat(results).contains(onBoundary);
    }

    @Test
    void findWithinRadiusExcludesPointsJustOutsideTheBoundary() {
        List<Place> results = placeRepository.findWithinRadius(0.0, 0.0, 5.0);

        assertThat(results).doesNotContain(justOutside);
    }

    @Test
    void findWithinRadiusExcludesDistantPoints() {
        List<Place> results = placeRepository.findWithinRadius(0.0, 0.0, 5.0);

        assertThat(results).doesNotContain(farAway);
    }

    @Test
    void findWithinRadiusWithZeroRadiusOnlyMatchesTheExactPoint() {
        List<Place> results = placeRepository.findWithinRadius(0.0, 0.0, 0.0);

        assertThat(results).containsExactly(center);
    }

    @Test
    void searchWithAllFiltersNullReturnsEveryPlace() {
        List<Place> results = placeRepository.search(null, null, null, null, null);

        assertThat(results).containsExactlyInAnyOrder(center, onBoundary, justOutside, farAway);
    }

    @Test
    void searchIgnoresLocationGroupWhenAllThreeParametersAreNull() {
        List<Place> results = placeRepository.search(null, null, null, "Atlanta", null);

        assertThat(results).containsExactlyInAnyOrder(center, onBoundary);
    }

    @Test
    void searchAppliesLocationGroupWhenAllThreeParametersAreSet() {
        List<Place> results = placeRepository.search(0.0, 0.0, 5.0, null, null);

        assertThat(results).containsExactlyInAnyOrder(center, onBoundary);
    }

    @Test
    void searchCombinesLocationAndAddressFilters() {
        List<Place> results = placeRepository.search(0.0, 0.0, 5.0, "Atlanta", null);

        assertThat(results).containsExactlyInAnyOrder(center, onBoundary);
    }

    @Test
    void searchExcludesLocationMatchesThatFailTheAddressFilter() {
        List<Place> results = placeRepository.search(0.0, 0.0, 5.0, "Savannah", null);

        assertThat(results).isEmpty();
    }

    @Test
    void searchFiltersByTypeMembership() {
        List<Place> results = placeRepository.search(null, null, null, null, PlaceType.PARK);

        assertThat(results).containsExactly(justOutside);
    }

    @Test
    void searchCombinesAllThreeGroups() {
        List<Place> results = placeRepository.search(0.0, 0.0, 5.0, "Atlanta", PlaceType.RESTAURANT);

        assertThat(results).containsExactlyInAnyOrder(center, onBoundary);
    }

    @Test
    void searchCombinedAllThreeGroupsExcludesMismatchedType() {
        List<Place> results = placeRepository.search(0.0, 0.0, 5.0, "Atlanta", PlaceType.PARK);

        assertThat(results).isEmpty();
    }
}
