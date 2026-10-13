package net.bp4k.locationlens.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication
    .OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web
    .OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication
    .AuthenticationSuccessHandler;
import org.springframework.security.web.authentication
    .SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import net.bp4k.locationlens.dto.TokenResponse;
import net.bp4k.locationlens.service.AuthenticationService;

/**
 * Decides what happens right after a user finishes logging in with
 * the OIDC identity provider (IDP), based on how that login was
 * triggered.
 *
 * <p>This application supports two different logins that both funnel
 * through the exact same Spring Security {@code oauth2Login()}
 * machinery:
 *
 * <ul>
 *   <li>A browser hitting a session-protected page (e.g.
 *       {@code /secured}) while unauthenticated. Spring Security's
 *       {@code ExceptionTranslationFilter} bounces it to the IDP and,
 *       on return, expects the classic "send the browser back to
 *       where it came from" behavior.</li>
 *   <li>A client that explicitly asked for an API token by visiting
 *       {@code GET /api/v1/login}. Here there is no "page" to return
 *       to - the caller wants a bearer token back as JSON so it can
 *       start calling {@code GET /api/v1/me} and other
 *       {@code /api/v1/**} endpoints.</li>
 * </ul>
 *
 * <p>{@link net.bp4k.locationlens.controller.AuthController#login}
 * flags the second case by stashing a marker attribute in the HTTP
 * session immediately before redirecting to the IDP. This handler
 * looks for that marker: if present, it performs a <em>token
 * relay</em> - it fetches the access token that the IDP itself issued
 * during the login and hands that same token back to the caller,
 * rather than minting a token of its own. (Token relay is a
 * well-established pattern; Spring Cloud Gateway ships a filter with
 * this exact name for the same purpose.) If the marker is absent,
 * this handler delegates to
 * {@link SavedRequestAwareAuthenticationSuccessHandler}, which is the
 * same default behavior {@code oauth2Login()} would have used on its
 * own, so the pre-existing {@code /secured} demo keeps working
 * unmodified.
 *
 * <p>An API-token login has two possible outcomes, chosen by
 * {@code AuthController} via
 * {@link #REDIRECT_TARGET_SESSION_ATTRIBUTE}:
 *
 * <ul>
 *   <li>No redirect target was requested: the token is written
 *       directly to the response body as JSON. This suits a manual
 *       test with curl/Postman, where there is no page to redirect
 *       to.</li>
 *   <li>A (pre-validated) redirect target was requested: the browser
 *       is redirected there with the token appended as query
 *       parameters, e.g.
 *       {@code https://app.example.com/callback?access_token=...
 *       &token_type=...&expires_in=...}. This suits a real frontend
 *       (a React SPA, a native app's custom URL scheme, etc.) that
 *       cannot receive a plain JSON body at the end of a top-level
 *       browser navigation, but can read query parameters off its own
 *       callback route.</li>
 * </ul>
 */
@Component
public class IdpTokenRelaySuccessHandler
        implements AuthenticationSuccessHandler {

    /**
     * HTTP session attribute name used to mark "this login was
     * started by /api/v1/login and should end in a JSON token
     * response, not a page redirect". Public so
     * {@code AuthController}, in a different package, can set it
     * before redirecting to the IDP.
     */
    public static final String API_LOGIN_SESSION_ATTRIBUTE =
            "net.bp4k.locationlens.API_LOGIN_REQUESTED";

    /**
     * HTTP session attribute name used to carry the caller's
     * requested (and already-validated by {@code AuthController})
     * redirect target across the trip to the IDP and back. Its value
     * is a {@link String} URI, or the attribute is simply absent if
     * no {@code target} query parameter was given to
     * {@code /api/v1/login}. Public for the same reason as
     * {@link #API_LOGIN_SESSION_ATTRIBUTE}.
     */
    public static final String REDIRECT_TARGET_SESSION_ATTRIBUTE =
            "net.bp4k.locationlens.API_LOGIN_REDIRECT_TARGET";

    /**
     * The Keycloak realm role that marks a user as an admin. Checked
     * against the {@code realm_access.roles} claim on every login -
     * see {@link #syncLocalUser}. The database's {@code User.admin}
     * column, not this claim, is the source of truth once a request
     * is underway; this is only where that column gets (re)set.
     */
    private static final String LOCATIONLENS_ADMIN_ROLE =
            "locationlens_admin";

    /**
     * Repository Spring Security already maintains for every OAuth2
     * client login; it holds the actual {@link OAuth2AccessToken}
     * (and refresh token, if any) that the IDP issued. We read from
     * it rather than re-deriving or re-requesting a token ourselves.
     */
    private final OAuth2AuthorizedClientRepository authorizedClientRepo;

    /**
     * Used only for the "returned to a page" branch, exactly as
     * {@code oauth2Login()} would use it by default.
     */
    private final AuthenticationSuccessHandler pageRedirectHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

    /** Serializes {@link TokenResponse} to the JSON response body. */
    private final ObjectMapper objectMapper;

    /**
     * Used to create or update the local {@code User} row for whoever
     * just finished logging in, from the claims the IDP returned -
     * see {@link #syncLocalUser}.
     */
    private final AuthenticationService authenticationService;

    /**
     * Creates the handler.
     *
     * @param authorizedClientRepo  the Spring-managed repository that
     *                              holds the IDP-issued access token
     *                              for the current login
     * @param objectMapper          the application's auto-configured
     *                              Jackson mapper, reused here instead
     *                              of constructing a new one
     * @param authenticationService used to create or update the local
     *                              {@code User} row on every
     *                              successful login
     */
    public IdpTokenRelaySuccessHandler(
            OAuth2AuthorizedClientRepository authorizedClientRepo,
            ObjectMapper objectMapper,
            AuthenticationService authenticationService) {
        this.authorizedClientRepo = authorizedClientRepo;
        this.objectMapper = objectMapper;
        this.authenticationService = authenticationService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>First creates or updates the local {@code User} row for the
     * caller (see {@link #syncLocalUser}), regardless of which branch
     * below is taken, then branches on
     * {@link #API_LOGIN_SESSION_ATTRIBUTE} as described in the
     * class-level documentation.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        syncLocalUser(authentication);

        HttpSession session = request.getSession(false);
        boolean isApiLogin = session != null
                && session.getAttribute(API_LOGIN_SESSION_ATTRIBUTE) != null;

        if (!isApiLogin) {
            // Not an API-token login: behave exactly like a stock
            // oauth2Login() setup would, e.g. for the /secured demo.
            pageRedirectHandler.onAuthenticationSuccess(
                    request, response, authentication);
            return;
        }

        // Read the target before removing it - it only needs to
        // survive for this one request.
        String redirectTarget = (String) session.getAttribute(
                REDIRECT_TARGET_SESSION_ATTRIBUTE);

        // Clean up both markers now that we have acted on them, so a
        // later, unrelated login in the same session is not
        // misclassified.
        session.removeAttribute(API_LOGIN_SESSION_ATTRIBUTE);
        session.removeAttribute(REDIRECT_TARGET_SESSION_ATTRIBUTE);

        relayIdpToken(request, response, authentication, redirectTarget);
    }

    /**
     * Creates or updates the local {@code User} row for the caller,
     * using the standard OIDC claims ({@code sub}, {@code email},
     * {@code given_name}, {@code family_name}) that
     * {@code oauth2Login()} already decoded from the IDP's ID token.
     *
     * <p>{@code oauth2Login()} only ever invokes this handler with an
     * {@link OAuth2AuthenticationToken}, whose principal is an
     * {@link OidcUser} as long as the client registration requests
     * the {@code openid} scope (it does - see
     * {@code application.properties}). The {@code instanceof} check
     * is defensive rather than load-bearing: it lets this method do
     * nothing, rather than throw, if that ever stops being true.
     *
     * <p>Also determines admin status from the {@code realm_access
     * .roles} claim and passes it to {@code createOrUpdateUser},
     * which (re)writes it to the database on every login - see
     * {@link #LOCATIONLENS_ADMIN_ROLE}. This claim must be present on
     * the ID token for this to work: Keycloak only includes realm
     * roles in a token when the client's {@code roles} client scope
     * mapper has "Add to ID token" enabled.
     *
     * @param authentication the freshly-authenticated login passed to
     *                       {@link #onAuthenticationSuccess}
     */
    private void syncLocalUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return;
        }

        authenticationService.createOrUpdateUser(
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getGivenName(),
                oidcUser.getFamilyName(),
                hasLocationlensAdminRole(oidcUser));
    }

    /**
     * Checks whether {@code oidcUser}'s {@code realm_access.roles}
     * claim contains {@link #LOCATIONLENS_ADMIN_ROLE}, mirroring the
     * shape Keycloak gives that claim: a top-level object with a
     * {@code roles} array, e.g. {@code {"roles": ["locationlens_admin"]}}.
     *
     * @param oidcUser the OIDC principal to inspect
     * @return whether the admin role is present
     */
    private static boolean hasLocationlensAdminRole(OidcUser oidcUser) {
        Object realmAccess = oidcUser.getClaims().get("realm_access");
        return realmAccess instanceof Map<?, ?> access
                && access.get("roles") instanceof Collection<?> roles
                && roles.contains(LOCATIONLENS_ADMIN_ROLE);
    }

    /**
     * Looks up the access token the IDP issued for this login and
     * either writes it to {@code response} as a {@link TokenResponse}
     * JSON body, or redirects the browser to {@code redirectTarget}
     * with the same information appended as query parameters.
     *
     * @param request        the original HTTP request, needed by
     *                       {@link OAuth2AuthorizedClientRepository}
     *                       to look up the authorized client
     * @param response       the response to write the JSON token
     *                       payload into, or to issue the redirect on
     * @param authentication the freshly-authenticated login; expected
     *                       to be an {@link OAuth2AuthenticationToken}
     *                       since only {@code oauth2Login()} invokes
     *                       this handler
     * @param redirectTarget the caller's already-validated redirect
     *                       URI, or {@code null} if none was
     *                       requested
     * @throws IOException if writing the JSON response body, or
     *                     issuing the redirect, fails
     */
    private void relayIdpToken(HttpServletRequest request,
            HttpServletResponse response, Authentication authentication,
            String redirectTarget) throws IOException {

        OAuth2AuthenticationToken oidcAuthentication =
                (OAuth2AuthenticationToken) authentication;

        // The registration id (e.g. "common-idp") tells the
        // repository which client registration's token to fetch;
        // Spring already knows it from the authentication itself, so
        // there is nothing to hardcode here.
        String registrationId =
                oidcAuthentication.getAuthorizedClientRegistrationId();

        OAuth2AuthorizedClient authorizedClient =
                authorizedClientRepo.loadAuthorizedClient(
                        registrationId, authentication, request);

        OAuth2AccessToken idpAccessToken =
                authorizedClient.getAccessToken();

        long expiresInSeconds = Math.max(0, Instant.now().until(
                idpAccessToken.getExpiresAt(), ChronoUnit.SECONDS));

        TokenResponse tokenResponse = new TokenResponse(
                idpAccessToken.getTokenValue(),
                "Bearer",
                expiresInSeconds);

        if (redirectTarget != null) {
            response.sendRedirect(
                    appendTokenAsQueryString(redirectTarget, tokenResponse));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                tokenResponse);
    }

    /**
     * Builds {@code redirectTarget + "?access_token=...
     * &token_type=...&expires_in=..."}, URL-encoding each value.
     *
     * <p>All three {@link TokenResponse} fields are included so a
     * client parsing this query string learns exactly what it would
     * have learned from the JSON body.
     *
     * @param redirectTarget the base URI to append the query string
     *                       to; assumed to not already contain a
     *                       query string, per its validation in
     *                       {@code AuthController}
     * @param tokenResponse  the token data to encode
     * @return the full redirect URL
     */
    private static String appendTokenAsQueryString(String redirectTarget,
            TokenResponse tokenResponse) {
        String queryString = "access_token="
                + urlEncode(tokenResponse.accessToken())
                + "&token_type=" + urlEncode(tokenResponse.tokenType())
                + "&expires_in=" + tokenResponse.expiresIn();
        return redirectTarget + "?" + queryString;
    }

    /**
     * Percent-encodes {@code value} for safe inclusion in a URL query
     * string component.
     *
     * @param value the raw value to encode
     * @return the encoded value
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
