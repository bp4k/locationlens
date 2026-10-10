package net.bp4k.locationlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import net.bp4k.locationlens.entity.Review;
import net.bp4k.locationlens.exception.DuplicateReviewException;
import net.bp4k.locationlens.repo.ReviewRepository;

class ReviewServiceTest {

    private static final UUID USER_ID = UUID.fromString("a852a1c9-1384-4571-8c53-7eeeb4975329");
    private static final String PLACE_ID = "place-123";

    private ReviewRepository reviewRepository;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        reviewService = new ReviewService(reviewRepository);
    }

    @Test
    void submitReviewBuildsReviewWithServerGeneratedIdAndTimestamp() {
        when(reviewRepository.existsByUserIdAndPlaceId(USER_ID, PLACE_ID)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Instant before = Instant.now();

        Review result = reviewService.submitReview(USER_ID, PLACE_ID, 4, "Nice");

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(captor.capture());
        Review saved = captor.getValue();
        assertSame(saved, result);
        assertNotNull(saved.getReviewId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(PLACE_ID, saved.getPlaceId());
        assertEquals(4, saved.getRating());
        assertEquals("Nice", saved.getText());
        assertTrue(!saved.getCreatedAt().isBefore(before));
        assertTrue(!saved.getCreatedAt().isAfter(Instant.now()));
    }

    @Test
    void submitReviewGeneratesDifferentIdsForEachReview() {
        when(reviewRepository.existsByUserIdAndPlaceId(any(), any())).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Review first = reviewService.submitReview(USER_ID, "place-a", 5, "a");
        Review second = reviewService.submitReview(USER_ID, "place-b", 5, "b");

        assertTrue(!first.getReviewId().equals(second.getReviewId()));
    }

    @Test
    void submitReviewThrowsWhenUserAlreadyReviewedPlace() {
        when(reviewRepository.existsByUserIdAndPlaceId(USER_ID, PLACE_ID)).thenReturn(true);

        DuplicateReviewException ex = assertThrows(DuplicateReviewException.class,
                () -> reviewService.submitReview(USER_ID, PLACE_ID, 4, "Nice"));

        assertEquals("You have already reviewed this place", ex.getMessage());
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitReviewTranslatesConstraintViolationIntoDuplicateReview() {
        when(reviewRepository.existsByUserIdAndPlaceId(USER_ID, PLACE_ID)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("uk_review_user_place"));

        DuplicateReviewException ex = assertThrows(DuplicateReviewException.class,
                () -> reviewService.submitReview(USER_ID, PLACE_ID, 4, "Nice"));

        assertEquals("You have already reviewed this place", ex.getMessage());
    }

    @Test
    void getReviewsForPlaceDelegatesToRepository() {
        List<Review> reviews = List.of(
                new Review(UUID.randomUUID(), 5, "a", Instant.now(), USER_ID, PLACE_ID));
        when(reviewRepository.findByPlaceId(PLACE_ID)).thenReturn(reviews);

        assertSame(reviews, reviewService.getReviewsForPlace(PLACE_ID));
    }

    @Test
    void getReviewHistoryDelegatesToRepository() {
        List<Review> reviews = List.of(
                new Review(UUID.randomUUID(), 5, "a", Instant.now(), USER_ID, PLACE_ID));
        when(reviewRepository.findByUserId(USER_ID)).thenReturn(reviews);

        assertSame(reviews, reviewService.getReviewHistory(USER_ID));
    }
}
