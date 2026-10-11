package net.bp4k.locationlens.controller;

import org.springframework.web.bind.annotation.RestController;

import net.bp4k.locationlens.dto.ReviewRequest;
import net.bp4k.locationlens.dto.ReviewResponse;
import net.bp4k.locationlens.entity.Review;
import net.bp4k.locationlens.exception.InvalidTokenException;
import net.bp4k.locationlens.service.ReviewService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
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
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse submitReview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Validated ReviewRequest submission) {
        UUID userId = currentUserId(jwt);
        Review saved = reviewService.submitReview(
                userId,
                submission.placeId(),
                submission.rating(),
                submission.text());
        return ReviewResponse.from(saved);
    }

    @GetMapping("/place/{placeId}")
    public List<ReviewResponse> getPlaceReviews(@PathVariable("placeId") String placeId) {
        return reviewService.getReviewsForPlace(placeId).stream().map(ReviewResponse::from).toList();
    }

    @GetMapping("/history")
    public List<ReviewResponse> getUserReviewHistory(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserId(jwt);
        return reviewService.getReviewHistory(userId).stream().map(ReviewResponse::from).toList();
    }

    private UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Token subject is not a valid user id");
        }
    }
}
