package net.bp4k.locationlens.controller;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.dto.BusinessClaimRequest;
import net.bp4k.locationlens.service.AuthenticationService;
import net.bp4k.locationlens.service.BusinessClaimService;

/**
 * HTTP draft for business claims. The existing API security chain validates
 * bearer tokens. Identity always comes from {@link AuthenticationService
 * #requireAuthenticatedUser}, never from request JSON or a controller
 * method parameter. Persistence and claim transitions belong to
 * BusinessClaimService.
 *
 * <p>Admin-only endpoints are marked with
 * {@code @PreAuthorize("hasRole('ADMIN')")}, backed by
 * {@code AdminAuthoritiesConverter}'s database-sourced {@code ROLE_ADMIN}
 * authority - see {@code docs/securing-admin-endpoints.md} - the same
 * mechanism {@code SampleController#adminOnly} uses, rather than this
 * controller parsing a JWT role claim itself.
 */
@RestController
@RequestMapping("/api/v1/business-claims")
public class BusinessClaimController {
    private final ObjectProvider<BusinessClaimService> serviceProvider;
    private final AuthenticationService authenticationService;

    public BusinessClaimController(ObjectProvider<BusinessClaimService> serviceProvider,
            AuthenticationService authenticationService) {
        this.serviceProvider = serviceProvider;
        this.authenticationService = authenticationService;
    }

    public record SubmitClaimRequest(String placeId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessClaimRequest submitClaim(@RequestBody SubmitClaimRequest request) {
        String userId = authenticationService.requireAuthenticatedUser().getUserId().toString();
        if (request == null || request.placeId() == null || request.placeId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "placeId is required");
        }
        return service().submitClaim(userId, request.placeId());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BusinessClaimRequest> getPendingClaims() {
        authenticationService.requireAuthenticatedUser();
        return service().getPendingClaims();
    }

    @PostMapping("/{claimId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void approveClaim(@PathVariable("claimId") String claimId) {
        String adminId = authenticationService.requireAuthenticatedUser().getUserId().toString();
        requireClaimId(claimId);
        service().approveClaim(claimId, adminId);
    }

    @PostMapping("/{claimId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void rejectClaim(@PathVariable("claimId") String claimId) {
        String adminId = authenticationService.requireAuthenticatedUser().getUserId().toString();
        requireClaimId(claimId);
        service().rejectClaim(claimId, adminId);
    }

    private static void requireClaimId(String claimId) {
        if (claimId == null || claimId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claimId is required");
        }
    }

    private BusinessClaimService service() {
        BusinessClaimService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Business claim service is not yet configured");
        }
        return service;
    }
}
