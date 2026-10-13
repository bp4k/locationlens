package net.bp4k.locationlens.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication
    .OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration
    .ClientRegistration;
import org.springframework.security.oauth2.client.web
    .OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import tools.jackson.databind.json.JsonMapper;

import net.bp4k.locationlens.dto.TokenResponse;
import net.bp4k.locationlens.service.AuthenticationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdpTokenRelaySuccessHandler}, covering its
 * three possible outcomes once an OIDC login succeeds:
 *
 * <ul>
 *   <li>not an API-token login at all (no
 *       {@link IdpTokenRelaySuccessHandler#API_LOGIN_SESSION_ATTRIBUTE}
 *       on the session) - delegates to the default page-redirect
 *       behavior;</li>
 *   <li>an API-token login with no redirect target - writes the
 *       relayed IDP access token as a JSON body;</li>
 *   <li>an API-token login with a (pre-validated, by
 *       {@code AuthController}) redirect target - redirects there
 *       with the token appended as query parameters.</li>
 * </ul>
 *
 * <p>No Spring context is started; the IDP-issued
 * {@link OAuth2AuthorizedClient} is hand-built and the repository
 * that would normally look it up is mocked, so these tests do not
 * need network access to a real identity provider.
 */
class IdpTokenRelaySuccessHandlerTest {

    private static final String REGISTRATION_ID = "common-idp";
    private static final String ACCESS_TOKEN_VALUE = "abc123";
    private static final long TOKEN_LIFETIME_SECONDS = 300;

    @Test
    @DisplayName("a non-API login (e.g. the /secured demo) falls back"
            + " to the default page-redirect behavior")
    void delegatesToPageRedirectHandlerWhenNotApiLogin()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // No API_LOGIN_SESSION_ATTRIBUTE set - simulates a plain
        // oauth2Login() success reached via /secured.
        request.getSession(true);

        IdpTokenRelaySuccessHandler handler = newHandler();
        Authentication authentication = mock(Authentication.class);

        handler.onAuthenticationSuccess(request, response, authentication);

        // SavedRequestAwareAuthenticationSuccessHandler, with nothing
        // saved to return to, falls back to the context root.
        assertEquals("/", response.getRedirectedUrl());
    }

    @Test
    @DisplayName("an API login with no redirect target writes the"
            + " relayed access token as JSON")
    void writesJsonWhenNoRedirectTargetRequested() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        session.setAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE,
                Boolean.TRUE);

        IdpTokenRelaySuccessHandler handler = newHandler();
        OAuth2AuthenticationToken authentication = oidcAuthentication();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals(200, response.getStatus());
        TokenResponse tokenResponse = JsonMapper.builder().build()
                .readValue(response.getContentAsByteArray(),
                        TokenResponse.class);
        assertEquals(ACCESS_TOKEN_VALUE, tokenResponse.accessToken());
        assertEquals("Bearer", tokenResponse.tokenType());
        assertTrue(tokenResponse.expiresIn() > 0
                && tokenResponse.expiresIn() <= TOKEN_LIFETIME_SECONDS);

        // Both markers must be cleared so a later login in the same
        // session is not misclassified.
        assertNull(session.getAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE));
        assertNull(session.getAttribute(IdpTokenRelaySuccessHandler
                .REDIRECT_TARGET_SESSION_ATTRIBUTE));
    }

    @Test
    @DisplayName("every successful login creates or updates the local"
            + " User from the OIDC claims")
    void syncsLocalUserFromOidcClaimsOnEveryLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Non-API login branch - syncing must not depend on which
        // branch onAuthenticationSuccess ultimately takes.
        request.getSession(true);

        AuthenticationService authenticationService =
                mock(AuthenticationService.class);
        IdpTokenRelaySuccessHandler handler =
                newHandler(authenticationService);
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "11111111-1111-1111-1111-111111111111",
                "jane@example.com", "Jane", "Doe");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authenticationService).createOrUpdateUser(
                "11111111-1111-1111-1111-111111111111",
                "jane@example.com", "Jane", "Doe", false);
    }

    @Test
    @DisplayName("a login whose principal is not an OidcUser is not"
            + " synced to the local User table")
    void doesNotSyncWhenPrincipalIsNotAnOidcUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true);

        AuthenticationService authenticationService =
                mock(AuthenticationService.class);
        IdpTokenRelaySuccessHandler handler =
                newHandler(authenticationService);
        // A plain mock Authentication has a null principal by
        // default, which is not an OidcUser.
        Authentication authentication = mock(Authentication.class);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authenticationService, never()).createOrUpdateUser(
                any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("a login whose realm_access.roles includes"
            + " locationlens_admin is synced with admin=true")
    void syncsAdminTrueWhenLocationlensAdminRoleIsPresent()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true);

        AuthenticationService authenticationService =
                mock(AuthenticationService.class);
        IdpTokenRelaySuccessHandler handler =
                newHandler(authenticationService);
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "11111111-1111-1111-1111-111111111111",
                "jane@example.com", "Jane", "Doe",
                List.of("locationlens_admin"));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authenticationService).createOrUpdateUser(
                "11111111-1111-1111-1111-111111111111",
                "jane@example.com", "Jane", "Doe", true);
    }

    @Test
    @DisplayName("a login whose realm_access.roles does not include"
            + " locationlens_admin is synced with admin=false")
    void syncsAdminFalseWhenLocationlensAdminRoleIsAbsent()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true);

        AuthenticationService authenticationService =
                mock(AuthenticationService.class);
        IdpTokenRelaySuccessHandler handler =
                newHandler(authenticationService);
        OAuth2AuthenticationToken authentication = oidcAuthentication(
                "11111111-1111-1111-1111-111111111111",
                "jane@example.com", "Jane", "Doe",
                List.of("some-other-role"));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authenticationService).createOrUpdateUser(
                "11111111-1111-1111-1111-111111111111",
                "jane@example.com", "Jane", "Doe", false);
    }

    @Test
    @DisplayName("a login whose claims have no realm_access at all is"
            + " synced with admin=false, not an exception")
    void syncsAdminFalseWhenRealmAccessClaimIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true);

        AuthenticationService authenticationService =
                mock(AuthenticationService.class);
        IdpTokenRelaySuccessHandler handler =
                newHandler(authenticationService);
        OAuth2AuthenticationToken authentication = oidcAuthentication();
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getClaims()).thenReturn(Map.of());
        when(authentication.getPrincipal()).thenReturn(oidcUser);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(authenticationService).createOrUpdateUser(
                any(), any(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("an API login with a redirect target redirects there"
            + " with the token appended as query parameters")
    void redirectsWithTokenQueryStringWhenTargetRequested()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        session.setAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE,
                Boolean.TRUE);
        String target = "http://example.com/login-redirect";
        session.setAttribute(
                IdpTokenRelaySuccessHandler
                        .REDIRECT_TARGET_SESSION_ATTRIBUTE,
                target);

        IdpTokenRelaySuccessHandler handler = newHandler();
        OAuth2AuthenticationToken authentication = oidcAuthentication();

        handler.onAuthenticationSuccess(request, response, authentication);

        String redirectedUrl = response.getRedirectedUrl();
        assertTrue(redirectedUrl.startsWith(target + "?"),
                "expected redirect to start with " + target + "?"
                        + " but was " + redirectedUrl);
        assertTrue(redirectedUrl.contains(
                "access_token=" + ACCESS_TOKEN_VALUE));
        assertTrue(redirectedUrl.contains("token_type=Bearer"));
        assertTrue(redirectedUrl.matches(
                ".*expires_in=\\d+$"),
                "expected a trailing numeric expires_in but was "
                        + redirectedUrl);

        assertNull(session.getAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE));
        assertNull(session.getAttribute(IdpTokenRelaySuccessHandler
                .REDIRECT_TARGET_SESSION_ATTRIBUTE));
    }

    /**
     * Builds a handler wired to a mocked
     * {@link OAuth2AuthorizedClientRepository} that always returns a
     * fixed, {@value #TOKEN_LIFETIME_SECONDS}-second-lived access
     * token for {@value #REGISTRATION_ID}, and a plain, real Jackson
     * mapper for JSON serialization.
     *
     * @return the handler under test
     */
    private static IdpTokenRelaySuccessHandler newHandler() {
        return newHandler(mock(AuthenticationService.class));
    }

    /**
     * Same as {@link #newHandler()}, but with a caller-supplied
     * {@link AuthenticationService} so tests can verify how it was
     * called.
     *
     * @param authenticationService the service to wire into the
     *                              handler
     * @return the handler under test
     */
    private static IdpTokenRelaySuccessHandler newHandler(
            AuthenticationService authenticationService) {
        OAuth2AuthorizedClientRepository repository =
                mock(OAuth2AuthorizedClientRepository.class);
        when(repository.loadAuthorizedClient(
                anyString(), any(), any()))
                .thenReturn(authorizedClient());
        return new IdpTokenRelaySuccessHandler(
                repository, JsonMapper.builder().build(),
                authenticationService);
    }

    /**
     * @return an {@link OAuth2AuthorizedClient} carrying a fixed
     *         bearer token that expires
     *         {@value #TOKEN_LIFETIME_SECONDS} seconds from now,
     *         standing in for what Spring Security would have
     *         obtained from a real IDP during the code exchange
     */
    private static OAuth2AuthorizedClient authorizedClient() {
        ClientRegistration clientRegistration = ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .clientId("test-client")
                .authorizationGrantType(
                        AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(
                        "{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://idp.example.com/auth")
                .tokenUri("https://idp.example.com/token")
                .build();

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(TOKEN_LIFETIME_SECONDS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, ACCESS_TOKEN_VALUE,
                issuedAt, expiresAt);

        return new OAuth2AuthorizedClient(
                clientRegistration, "test-subject", accessToken);
    }

    /**
     * @return a mocked {@link OAuth2AuthenticationToken} that reports
     *         {@value #REGISTRATION_ID} as its authorized client
     *         registration id, matching {@link #authorizedClient()}
     */
    private static OAuth2AuthenticationToken oidcAuthentication() {
        OAuth2AuthenticationToken authentication =
                mock(OAuth2AuthenticationToken.class);
        when(authentication.getAuthorizedClientRegistrationId())
                .thenReturn(REGISTRATION_ID);
        return authentication;
    }

    /**
     * Same as {@link #oidcAuthentication()}, but with its principal
     * stubbed as an {@link OidcUser} carrying the given standard OIDC
     * claims and no realm roles, for tests that verify the
     * local-user-sync behavior but are not themselves about admin
     * provisioning.
     *
     * @param subject    the {@code sub} claim
     * @param email      the {@code email} claim
     * @param givenName  the {@code given_name} claim
     * @param familyName the {@code family_name} claim
     * @return the authentication token
     */
    private static OAuth2AuthenticationToken oidcAuthentication(
            String subject, String email, String givenName,
            String familyName) {
        return oidcAuthentication(subject, email, givenName, familyName,
                List.of());
    }

    /**
     * Same as {@link #oidcAuthentication(String, String, String,
     * String)}, but also stubs the principal's {@code realm_access
     * .roles} claim to {@code realmRoles}, matching the shape
     * Keycloak gives that claim.
     *
     * @param subject    the {@code sub} claim
     * @param email      the {@code email} claim
     * @param givenName  the {@code given_name} claim
     * @param familyName the {@code family_name} claim
     * @param realmRoles the {@code realm_access.roles} claim's
     *                   contents
     * @return the authentication token
     */
    private static OAuth2AuthenticationToken oidcAuthentication(
            String subject, String email, String givenName,
            String familyName, List<String> realmRoles) {
        OAuth2AuthenticationToken authentication = oidcAuthentication();
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn(subject);
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getGivenName()).thenReturn(givenName);
        when(oidcUser.getFamilyName()).thenReturn(familyName);
        when(oidcUser.getClaims()).thenReturn(
                Map.of("realm_access", Map.of("roles", realmRoles)));
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        return authentication;
    }
}
