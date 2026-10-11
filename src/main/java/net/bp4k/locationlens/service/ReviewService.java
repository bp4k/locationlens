package net.bp4k.locationlens.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import net.bp4k.locationlens.entity.Review;
import net.bp4k.locationlens.exception.DuplicateReviewException;
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
        if(reviewRepository.existsByUserIdAndPlaceId(userId, placeId)) { throw new DuplicateReviewException("You have already reviewed this place");}
        Review review = new Review(UUID.randomUUID(), rating, text, Instant.now(), userId, placeId);
        try {
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e)
        {
            throw new DuplicateReviewException("You have already reviewed this place");
        }
    }

    public List<Review> getReviewsForPlace(String placeId){
        return reviewRepository.findByPlaceId(placeId);
    }

     public List<Review>getReviewHistory(UUID userId) {
        return reviewRepository.findByUserId(userId);
    }
}
