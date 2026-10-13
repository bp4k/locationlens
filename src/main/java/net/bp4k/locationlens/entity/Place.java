package net.bp4k.locationlens.entity;

import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import net.bp4k.locationlens.model.PlaceType;

/**
 * Describes a place that can be reviewed, such as a restaurant or park.
 * @author Advaith Menon
 */
@Entity
@Getter
@Setter
@Table(name = "place")
public class Place
{
    /**
     * The unique identifier of the Place.
     */
    @Id
    @Setter(AccessLevel.NONE)
    private String placeId;

    /** The display name of the place. */
    private String name;

    /** The street address of the place. */
    private String address;

    /** The latitude of the place's location. */
    private Double latitude;

    /** The longitude of the place's location. */
    private Double longitude;

    /** The categories the place belongs to. */
    @ElementCollection
    @CollectionTable(name = "place_types", joinColumns = @JoinColumn(name = "place_id"))
    @Enumerated(EnumType.STRING)
    private Set<PlaceType> types;

    protected Place() {}

    public Place(String placeId, String name, String address,
            Double latitude, Double longitude, Set<PlaceType> types) {
        this.placeId = placeId;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.types = types;
    }
}
