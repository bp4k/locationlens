package net.bp4k.locationlens.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.bp4k.locationlens.entity.BusinessClaim;
import net.bp4k.locationlens.model.ClaimStatus;

public interface BusinessClaimRepository extends JpaRepository<BusinessClaim, UUID> {
    List<BusinessClaim> findByStatus(ClaimStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select claim from BusinessClaim claim where claim.claimId = :claimId")
    Optional<BusinessClaim> findByIdForUpdate(@Param("claimId") UUID claimId);
}
