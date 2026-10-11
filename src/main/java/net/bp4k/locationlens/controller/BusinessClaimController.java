package net.bp4k.locationlens.controller;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.model.BusinessClaimRequest;
import net.bp4k.locationlens.service.BusinessClaimService;

/**
 * HTTP draft for business claims. The existing API security chain validates
 * bearer tokens. Identity comes from their subject, never from request JSON.
 * Persistence and claim transitions belong to BusinessClaimService.
 */
@RestController
@RequestMapping("/api/v1/business-claims")
public class BusinessClaimController {
    private final ObjectProvider<BusinessClaimService> serviceProvider;

    public BusinessClaimController(ObjectProvider<BusinessClaimService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    public record SubmitClaimRequest(String placeId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessClaimRequest submitClaim(
            @RequestBody SubmitClaimRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = requireUser(jwt);
        if (request == null || request.placeId() == null || request.placeId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "placeId is required");
        }
        return service().submitClaim(userId, request.placeId());
    }

    @GetMapping("/pending")
    public List<BusinessClaimRequest> getPendingClaims(@AuthenticationPrincipal Jwt jwt) {
        requireAdministrator(jwt);
        return service().getPendingClaims();
    }

    @PostMapping("/{claimId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveClaim(@PathVariable("claimId") String claimId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = requireAdministrator(jwt);
        requireClaimId(claimId);
        service().approveClaim(claimId, adminId);
    }

    @PostMapping("/{claimId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectClaim(@PathVariable("claimId") String claimId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = requireAdministrator(jwt);
        requireClaimId(claimId);
        service().rejectClaim(claimId, adminId);
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return jwt.getSubject();
    }

    /** Draft convention: a Keycloak realm role named "admin" is required. */
    private static String requireAdministrator(Jwt jwt) {
        String userId = requireUser(jwt);
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> access)
                || !(access.get("roles") instanceof Collection<?> roles)
                || !roles.contains("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required");
        }
        return userId;
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
