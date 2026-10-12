package net.bp4k.locationlens.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import net.bp4k.locationlens.model.ClaimStatus;

/** Persistent request and its administrator decision; the API uses a separate record. */
@Entity
@Getter
@Table(name = "business_claim", indexes = {
        @Index(name = "idx_business_claim_status", columnList = "status")
})
public class BusinessClaim {
    @Id
    private UUID claimId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String placeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClaimStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private UUID reviewedBy;
    private Instant reviewedAt;

    protected BusinessClaim() {}

    public BusinessClaim(UUID claimId, UUID userId, String placeId, Instant createdAt) {
        this.claimId = Objects.requireNonNull(claimId);
        this.userId = Objects.requireNonNull(userId);
        this.placeId = Objects.requireNonNull(placeId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.status = ClaimStatus.PENDING;
    }

    public void approve(UUID adminId) {
        decide(ClaimStatus.APPROVED, adminId);
    }

    public void reject(UUID adminId) {
        decide(ClaimStatus.REJECTED, adminId);
    }

    private void decide(ClaimStatus decision, UUID adminId) {
        if (status != ClaimStatus.PENDING) {
            throw new IllegalStateException("Claim has already been reviewed");
        }
        UUID reviewer = Objects.requireNonNull(adminId);
        status = decision;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
    }
}
