package net.bp4k.locationlens.entity;

import java.time.Instant; 
import java.util.UUID;


import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter; 



@Entity
@Getter 
@Table(
    name="review",
    check = @CheckConstraint (
        name="ck_review_rating_range",
        constraint = "rating >= 1 AND rating <= 5"
    ),
    indexes = {
        @Index(name="idx_review_place_id", columnList = "place_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name="uk_review_user_place", columnNames = {"user_id", "place_id"})
    }
)
public class Review
{
    @Id
    private UUID reviewId; 

    private Integer rating; 
    @jakarta.persistence.Column(length = 2000)
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