package net.bp4k.locationlens.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bp4k.locationlens.entity.Review;

public record ReviewResponse(
    @JsonProperty("review_id") UUID reviewId,
    @JsonProperty("place_id") String placeId,
    @JsonProperty("rating") Integer rating,
    @JsonProperty("text") String text,
    @JsonProperty("created_at") Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
            review.getReviewId(),
            review.getPlaceId(),
            review.getRating(),
            review.getText(),
            review.getCreatedAt());
    }
}