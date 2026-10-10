package net.bp4k.locationlens.entity;

import java.time.Instant; 
import java.util.UUID;

import org.hibernate.annotations.Check;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter; 



@Entity
@Getter 
@Check(constraints = "rating < 1 OR rating > 5")
public class Review
{
    @Id
    private UUID reviewId; 

    private Integer rating; 
    private String text; 
    private Instant createdAt; 
    private UUID userId; 
    private String placeId; 

    protected Review(){} 

    public Review(UUID reviewId, Integer rating, String text, Instant createdAt, UUID userId, String placeId)
    {
        this.reviewId = reviewId;
        this.rating = rating;
        this.text = text;
        this.createdAt = createdAt;
        this.userId = userId;
        this.placeId = placeId;
    }
}