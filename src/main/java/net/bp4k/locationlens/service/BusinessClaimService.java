package net.bp4k.locationlens.service;

import java.util.List;
import net.bp4k.locationlens.model.BusinessClaimRequest;

/**
 * Integration contract for the controller draft; supply a Spring service bean.
 * User/admin IDs are OIDC subjects; resolve them to database users as needed.
 * Implementations must persist submissions before returning, validate places,
 * and atomically review only pending claims. Map missing claims to 404 and
 * already reviewed claims to 409 (for example with ResponseStatusException).
 */
public interface BusinessClaimService {
    BusinessClaimRequest submitClaim(String userId, String placeId);
    List<BusinessClaimRequest> getPendingClaims();
    void approveClaim(String claimId, String adminId);
    void rejectClaim(String claimId, String adminId);
}
