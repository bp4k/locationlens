package net.bp4k.locationlens.model;

/** API response contract; persistence and reviewer auditing live in BusinessClaim. */
public record BusinessClaimRequest(
        String id, String userId, String placeId, ClaimStatus status) {
}
