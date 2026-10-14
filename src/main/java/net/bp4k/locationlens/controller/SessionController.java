package net.bp4k.locationlens.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;

import net.bp4k.locationlens.config.FrontendOrigins;
import net.bp4k.locationlens.dto.TokenResponse;

/** Renews access tokens from an existing OIDC session; refresh tokens stay server-side. */
@RestController
public class SessionController {
    private final OAuth2AuthorizedClientManager clientManager;
    private final FrontendOrigins frontendOrigins;

    public SessionController(OAuth2AuthorizedClientManager clientManager, FrontendOrigins frontendOrigins) {
        this.clientManager = clientManager;
        this.frontendOrigins = frontendOrigins;
    }

    @PostMapping("/api/v1/session/refresh")
    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        // This cookie-authenticated endpoint is inside the stateless API chain.
        // Require an exact trusted Origin on every POST for CSRF protection,
        // in addition to its narrow credentialed CORS policy.
        if (!frontendOrigins.allows(request.getHeader("Origin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted session request origin");
        }
        HttpSession session = request.getSession(false);
        Object context = session == null ? null
                : session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(context instanceof SecurityContext securityContext)
                || !(securityContext.getAuthentication() instanceof OAuth2AuthenticationToken authentication)
                || !authentication.isAuthenticated()) {
            throw expired();
        }
        OAuth2AuthorizedClient client;
        // Serialize concurrent refreshes using a rotating refresh token for this
        // session. API bearer requests remain independent and stateless.
        synchronized (session) {
            try {
                client = clientManager.authorize(OAuth2AuthorizeRequest
                        .withClientRegistrationId(authentication.getAuthorizedClientRegistrationId())
                        .principal(authentication)
                        .attribute(HttpServletRequest.class.getName(), request)
                        .attribute(HttpServletResponse.class.getName(), response).build());
            } catch (OAuth2AuthorizationException invalidSession) {
                String code = invalidSession.getError().getErrorCode();
                if ("invalid_grant".equals(code) || "invalid_token".equals(code)) throw expired();
                throw unavailable();
            } catch (RestClientException unavailableProvider) {
                throw unavailable();
            }
        }
        if (client == null || client.getAccessToken().getExpiresAt() == null) throw expired();
        long expiresIn = Instant.now().until(client.getAccessToken().getExpiresAt(), ChronoUnit.SECONDS);
        if (expiresIn <= 0) throw expired();
        return new TokenResponse(client.getAccessToken().getTokenValue(), "Bearer", expiresIn);
    }

    private static ResponseStatusException expired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your sign-in session expired. Please sign in again");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to renew your sign-in session. Please try again");
    }
}
