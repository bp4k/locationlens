package net.bp4k.locationlens.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import net.bp4k.locationlens.entity.Review;
import net.bp4k.locationlens.repo.ReviewRepository;

@Service 
public class ReviewService {
    
    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository)
    {
        this.reviewRepository = reviewRepository;
    }

    public Review submitReview(UUID userId, String placeId, Integer rating, String text)
    {
        Review review = new Review(UUID.randomUUID(), rating, text, Instant.now(), userId, placeId);
        return reviewRepository.save(review);
    }

    public List<Review> getReviewsForPlace(String placeId){
        return reviewRepository.findByPlaceId(placeId);
    }

     public List<Review>getReviewHistory(UUID userId) {
        return reviewRepository.findByUserId(userId);
    }
}
