package net.bp4k.locationlens.model;

/** Minimal response contract for the draft; database entity implementation is pending. */
public record BusinessClaimRequest(
        String id, String userId, String placeId, ClaimStatus status) {
}
