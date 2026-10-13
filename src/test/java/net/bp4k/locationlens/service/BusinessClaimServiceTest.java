package net.bp4k.locationlens.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.entity.BusinessClaim;
import net.bp4k.locationlens.model.BusinessClaimRequest;
import net.bp4k.locationlens.model.ClaimStatus;
import net.bp4k.locationlens.repo.BusinessClaimRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessClaimServiceTest {
    private static final UUID USER_ID = UUID.fromString("a852a1c9-1384-4571-8c53-7eeeb4975329");
    private static final UUID ADMIN_ID = UUID.fromString("b952a1c9-1384-4571-8c53-7eeeb4975329");
    private static final UUID CLAIM_ID = UUID.fromString("c952a1c9-1384-4571-8c53-7eeeb4975329");
    private BusinessClaimRepository repository;
    private BusinessClaimService service;
    private BusinessPlaceLookup placeLookup;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessClaimRepository.class);
        placeLookup = mock(BusinessPlaceLookup.class);
        when(placeLookup.requireBusinessPlace(any())).thenAnswer(call -> call.getArgument(0));
        service = new BusinessClaimService(repository, placeLookup);
    }

    private static BusinessClaim pending() {
        return new BusinessClaim(CLAIM_ID, USER_ID, "place-1", Instant.now());
    }

    private static void assertStatus(HttpStatus status, Runnable operation) {
        assertEquals(status, assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }

    @Test
    void submissionPersistsPendingClaimWithServerIdAndTimestamp() {
        Instant before = Instant.now();
        when(repository.saveAndFlush(any(BusinessClaim.class))).thenAnswer(call -> {
            BusinessClaim claim = call.getArgument(0);
            assertNotNull(claim.getClaimId());
            assertEquals(USER_ID, claim.getUserId());
            assertEquals("place-1", claim.getPlaceId());
            assertEquals(ClaimStatus.PENDING, claim.getStatus());
            assertFalse(claim.getCreatedAt().isBefore(before));
            assertFalse(claim.getCreatedAt().isAfter(Instant.now()));
            assertNull(claim.getReviewedBy());
            assertNull(claim.getReviewedAt());
            return claim;
        });

        BusinessClaimRequest result = service.submitClaim(USER_ID.toString(), "place-1");
        assertNotNull(UUID.fromString(result.id()));
        assertEquals(USER_ID.toString(), result.userId());
        assertEquals("place-1", result.placeId());
        assertEquals(ClaimStatus.PENDING, result.status());
        verify(repository).saveAndFlush(any(BusinessClaim.class));
    }

    @Test
    void submissionsReceiveDistinctIds() {
        when(repository.saveAndFlush(any(BusinessClaim.class))).thenAnswer(call -> call.getArgument(0));
        assertNotEquals(service.submitClaim(USER_ID.toString(), "place-1").id(),
                service.submitClaim(USER_ID.toString(), "place-2").id());
    }

    @Test
    void invalidPlaceIdsNeverReachDatabase() {
        for (String placeId : new String[] {null, "", " ", "a".repeat(256)}) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.submitClaim(USER_ID.toString(), placeId));
        }
        verifyNoInteractions(repository);
        verifyNoInteractions(placeLookup);
    }

    @Test
    void nonexistentBusinessNeverCreatesAClaim() {
        when(placeLookup.requireBusinessPlace("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.submitClaim(USER_ID.toString(), "missing"));
        verifyNoInteractions(repository);
    }

    @Test
    void unavailableLookupNeverCreatesAClaim() {
        when(placeLookup.requireBusinessPlace("place-1"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable"));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.submitClaim(USER_ID.toString(), "place-1"));
        verifyNoInteractions(repository);
    }

    @Test
    void storesTheCanonicalVerifiedPlaceId() {
        when(placeLookup.requireBusinessPlace("old-id")).thenReturn("canonical-id");
        when(repository.saveAndFlush(any(BusinessClaim.class))).thenAnswer(call -> call.getArgument(0));
        assertEquals("canonical-id", service.submitClaim(USER_ID.toString(), "old-id").placeId());
    }

    @Test
    void invalidUserSubjectsAreUnauthorized() {
        for (String userId : new String[] {null, "", "user-123", "1-1-1-1-1"}) {
            assertStatus(HttpStatus.UNAUTHORIZED, () -> service.submitClaim(userId, "place-1"));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void pendingListMapsPersistedClaimsWithoutChangingResponseShape() {
        when(repository.findByStatus(ClaimStatus.PENDING)).thenReturn(List.of(pending()));
        assertEquals(List.of(new BusinessClaimRequest(CLAIM_ID.toString(), USER_ID.toString(),
                "place-1", ClaimStatus.PENDING)), service.getPendingClaims());
        verify(repository).findByStatus(ClaimStatus.PENDING);
    }

    @Test
    void pendingListCanBeEmpty() {
        when(repository.findByStatus(ClaimStatus.PENDING)).thenReturn(List.of());
        assertTrue(service.getPendingClaims().isEmpty());
    }

    @Test
    void approvalLocksAndPersistsDecisionWithReviewer() {
        BusinessClaim claim = pending();
        when(repository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        service.approveClaim(CLAIM_ID.toString(), ADMIN_ID.toString());
        assertEquals(ClaimStatus.APPROVED, claim.getStatus());
        assertEquals(ADMIN_ID, claim.getReviewedBy());
        assertNotNull(claim.getReviewedAt());
        verify(repository).saveAndFlush(claim);
    }

    @Test
    void rejectionLocksAndPersistsDecisionWithReviewer() {
        BusinessClaim claim = pending();
        when(repository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        service.rejectClaim(CLAIM_ID.toString(), ADMIN_ID.toString());
        assertEquals(ClaimStatus.REJECTED, claim.getStatus());
        assertEquals(ADMIN_ID, claim.getReviewedBy());
        assertNotNull(claim.getReviewedAt());
        verify(repository).saveAndFlush(claim);
    }

    @Test
    void missingClaimsReturnNotFoundForBothDecisions() {
        when(repository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.approveClaim(CLAIM_ID.toString(), ADMIN_ID.toString()));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.rejectClaim(CLAIM_ID.toString(), ADMIN_ID.toString()));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void neitherDecisionCanOverwriteAnAlreadyReviewedClaim() {
        for (boolean approved : new boolean[] {true, false}) {
            BusinessClaim claim = pending();
            if (approved) { claim.approve(ADMIN_ID); } else { claim.reject(ADMIN_ID); }
            Instant reviewedAt = claim.getReviewedAt();
            ClaimStatus status = claim.getStatus();
            when(repository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
            assertStatus(HttpStatus.CONFLICT, () -> service.approveClaim(CLAIM_ID.toString(), ADMIN_ID.toString()));
            assertStatus(HttpStatus.CONFLICT, () -> service.rejectClaim(CLAIM_ID.toString(), ADMIN_ID.toString()));
            assertEquals(status, claim.getStatus());
            assertEquals(reviewedAt, claim.getReviewedAt());
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void malformedClaimIdsAndReviewerSubjectsAreRejectedBeforeLookup() {
        for (String id : new String[] {null, "", "bad-id", "1-1-1-1-1"}) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.approveClaim(id, ADMIN_ID.toString()));
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.rejectClaim(id, ADMIN_ID.toString()));
            assertStatus(HttpStatus.UNAUTHORIZED, () -> service.approveClaim(CLAIM_ID.toString(), id));
            assertStatus(HttpStatus.UNAUTHORIZED, () -> service.rejectClaim(CLAIM_ID.toString(), id));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void databaseFailuresDoNotProduceSuccess() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Database unavailable");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.submitClaim(USER_ID.toString(), "place-1")));
        when(repository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(pending()));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.approveClaim(CLAIM_ID.toString(), ADMIN_ID.toString())));
        when(repository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(pending()));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.rejectClaim(CLAIM_ID.toString(), ADMIN_ID.toString())));
    }
}
