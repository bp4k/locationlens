package net.bp4k.locationlens.controller;

import org.springframework.web.bind.annotation.RestController;

import net.bp4k.locationlens.dto.ReviewRequest;
import net.bp4k.locationlens.entity.Review;
import net.bp4k.locationlens.service.ReviewService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



@RestController 
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    
    private final ReviewService reviewService;
    /**
     * 
     */
     public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public Review submitReview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ReviewRequest submission) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return reviewService.submitReview(
                userId,
                submission.placeId(),
                submission.rating(),
                submission.text());
    }

    @GetMapping("/place/{placeId}")
    public List<Review> getPlaceReviews(@PathVariable("placeId") String placeId) {
        return reviewService.getReviewsForPlace(placeId);
    }

    @GetMapping("/history")
    public List<Review> getUserReviewHistory(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return reviewService.getReviewHistory(userId);
    }


}
