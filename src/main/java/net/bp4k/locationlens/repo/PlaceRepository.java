package net.bp4k.locationlens.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;

/**
 * DAO for Places.
 * @author Advaith Menon
 */
public interface PlaceRepository extends JpaRepository<Place, String> {

    /**
     * Finds places within a given distance of a point, using the same
     * squared-distance comparison as {@link #search}:
     * {@code (lat - latitude)^2 + (lon - longitude)^2 <= radius^2}.
     * @param latitude the latitude of the search origin
     * @param longitude the longitude of the search origin
     * @param radius the search radius, in the same units as the latitude/longitude values
     * @return places within the given radius of the origin
     */
    @Query("SELECT p FROM Place p WHERE "
            + "POWER(p.latitude - :latitude, 2) + POWER(p.longitude - :longitude, 2) <= POWER(:radius, 2)")
    List<Place> findWithinRadius(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radius") Double radius);

    /**
     * Finds places whose name contains the given text, case-insensitively.
     * @param name the text to search for within place names
     * @return places whose name contains {@code name}
     */
    List<Place> findByNameContainingIgnoreCase(String name);

    /**
     * Finds places matching the (AND-combined) conditions below. Each group of
     * parameters is ignored entirely when every parameter within it is {@code null}:
     * <ul>
     *   <li>{@code latitude}, {@code longitude}, {@code radius} &mdash; a location/radius
     *       filter, applied as {@code (lat - latitude)^2 + (lon - longitude)^2 <= radius^2}</li>
     *   <li>{@code address} &mdash; a case-sensitive substring match against the place's address</li>
     *   <li>{@code type} &mdash; places that carry this type among their {@link PlaceType}s</li>
     * </ul>
     * This method does not validate that a group's parameters are either all set or
     * all {@code null} (e.g. {@code radius} set without {@code latitude}/{@code longitude}) &mdash;
     * that validation is caller business logic and does not belong in a repository.
     * Callers must perform it before invoking this method.
     * @param latitude the latitude of the search origin, or {@code null} to skip the location filter
     * @param longitude the longitude of the search origin, or {@code null} to skip the location filter
     * @param radius the search radius, or {@code null} to skip the location filter
     * @param address text to search for within the place's address, or {@code null} to skip this filter
     * @param type a type the place must have, or {@code null} to skip this filter
     * @return places matching every active filter
     */
    @Query("SELECT p FROM Place p WHERE "
            + "((:latitude IS NULL AND :longitude IS NULL AND :radius IS NULL) "
            + "  OR (POWER(p.latitude - :latitude, 2) + POWER(p.longitude - :longitude, 2) <= POWER(:radius, 2))) "
            + "AND (:address IS NULL OR p.address LIKE CONCAT('%', :address, '%')) "
            + "AND (:type IS NULL OR :type MEMBER OF p.types)")
    List<Place> search(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radius") Double radius,
            @Param("address") String address,
            @Param("type") PlaceType type);
}
