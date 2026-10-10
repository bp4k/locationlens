package net.bp4k.locationlens.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.bp4k.locationlens.entity.Review;

class ReviewResponseTest {

    @Test
    void fromCopiesPublicFieldsFromReview() {
        UUID reviewId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T12:00:00Z");
        Review review = new Review(reviewId, 3, "Okay", createdAt, UUID.randomUUID(), "place-9");

        ReviewResponse response = ReviewResponse.from(review);

        assertEquals(reviewId, response.reviewId());
        assertEquals("place-9", response.placeId());
        assertEquals(3, response.rating());
        assertEquals("Okay", response.text());
        assertEquals(createdAt, response.createdAt());
    }
}
