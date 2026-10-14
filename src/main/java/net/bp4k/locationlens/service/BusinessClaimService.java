package net.bp4k.locationlens.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.dto.BusinessClaimRequest;
import net.bp4k.locationlens.entity.BusinessClaim;
import net.bp4k.locationlens.model.ClaimStatus;
import net.bp4k.locationlens.repo.BusinessClaimRepository;

/**
 * Persists claims and atomically reviews pending requests.
 * The controller authenticates callers and checks the administrator role.
 * User/admin IDs are Keycloak subject UUIDs, as in User and Review. Storing
 * subjects directly follows ReviewService and does not require a local user
 * record to have been provisioned. The provider verifies the business exists
 * before a claim is saved; administrators still verify ownership.
 */
@Service
@Transactional
public class BusinessClaimService {
    private final BusinessClaimRepository claimRepository;
    private final BusinessPlaceLookup placeLookup;

    public BusinessClaimService(BusinessClaimRepository claimRepository, BusinessPlaceLookup placeLookup) {
        this.claimRepository = claimRepository;
        this.placeLookup = placeLookup;
    }

    public BusinessClaimRequest submitClaim(String userId, String placeId) {
        UUID subject = subjectId(userId);
        if (placeId == null || placeId.isBlank() || placeId.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "placeId must contain between 1 and 255 characters");
        }
        String verifiedPlaceId = placeLookup.requireBusinessPlace(placeId);
        BusinessClaim claim = new BusinessClaim(UUID.randomUUID(), subject, verifiedPlaceId, Instant.now());
        return response(claimRepository.saveAndFlush(claim));
    }

    @Transactional(readOnly = true)
    public List<BusinessClaimRequest> getPendingClaims() {
        return claimRepository.findByStatus(ClaimStatus.PENDING).stream()
                .map(BusinessClaimService::response).toList();
    }

    public void approveClaim(String claimId, String adminId) {
        reviewClaim(claimId, adminId, true);
    }

    public void rejectClaim(String claimId, String adminId) {
        reviewClaim(claimId, adminId, false);
    }

    private void reviewClaim(String claimId, String adminId, boolean approve) {
        UUID reviewer = subjectId(adminId);
        UUID id = parseId(claimId, HttpStatus.BAD_REQUEST, "claimId must be a UUID");
        // Hold the row lock through commit so competing approve/reject calls
        // see the first decision and cannot silently replace it.
        BusinessClaim claim = claimRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Claim has already been reviewed");
        }
        if (approve) {
            claim.approve(reviewer);
        } else {
            claim.reject(reviewer);
        }
        claimRepository.saveAndFlush(claim);
    }

    private static UUID subjectId(String value) {
        return parseId(value, HttpStatus.UNAUTHORIZED, "Token subject must be a UUID");
    }

    private static UUID parseId(String value, HttpStatus status, String message) {
        try {
            UUID id = UUID.fromString(value);
            // UUID.fromString accepts shortened UUID groups; require the full
            // canonical form used by Keycloak and generated claim IDs.
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(status, message);
        }
    }

    private static BusinessClaimRequest response(BusinessClaim claim) {
        return new BusinessClaimRequest(claim.getClaimId().toString(),
                claim.getUserId().toString(), claim.getPlaceId(), claim.getStatus());
    }
}
