package net.bp4k.locationlens.repo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import net.bp4k.locationlens.entity.Review;
import java.util.List;
import java.util.Optional;


/**
 * @author Aaron Channer
 * @implNote JpaRepository<Review,UUID> is the type of data the repo manages and the data type of the entity's db ID
 * You are able to find Review information in the database based on an ID
 * ReviewRepository
 */
public interface ReviewRepository extends JpaRepository<Review, UUID> 
{
    List<Review> findByPlaceId(String placeId);

    List<Review> findByUserId(UUID userId);

    boolean existsByUserIdAndPlaceId(UUID userId, String placeId);
}
