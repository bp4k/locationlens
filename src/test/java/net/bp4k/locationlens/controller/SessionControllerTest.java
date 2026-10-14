package net.bp4k.locationlens.controller;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.server.ResponseStatusException;

import net.bp4k.locationlens.config.FrontendOrigins;
import net.bp4k.locationlens.config.SecurityConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionControllerTest {
    private static final String ORIGIN = "http://localhost:5173";
    private final FrontendOrigins origins = new FrontendOrigins(ORIGIN + "/login-redirect");
    private final OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
    private final SessionController controller = new SessionController(manager, origins);

    private static OAuth2AuthenticationToken authentication() {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "user-id"), "sub");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "common-idp");
    }

    private static MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication()));
        return session;
    }

    private static ClientRegistration registration(String tokenUri) {
        return ClientRegistration.withRegistrationId("common-idp").clientId("client")
                .clientSecret("server-only-client-secret").clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost/authorize").tokenUri(tokenUri).build();
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void relaysOnlyTheAccessTokenFromAnAuthenticatedSession() throws Exception {
        var client = new OAuth2AuthorizedClient(registration("http://localhost/token"), "user-id",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "renewed-token", Instant.now(), Instant.now().plusSeconds(300)),
                new OAuth2RefreshToken("server-only-refresh", Instant.now()));
        when(manager.authorize(any())).thenReturn(client);
        mvc().perform(post("/api/v1/session/refresh").header("Origin", ORIGIN).session(session()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token").value("renewed-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber()).andExpect(jsonPath("$.refresh_token").doesNotExist());
        verify(manager).authorize(argThat(request -> request.getClientRegistrationId().equals("common-idp")
                && request.getPrincipal() instanceof OAuth2AuthenticationToken));
    }

    @Test
    void anonymousOrLostSessionReturns401WithoutCreatingASession() throws Exception {
        mvc().perform(post("/api/v1/session/refresh").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized());
        mvc().perform(post("/api/v1/session/refresh").header("Origin", ORIGIN).session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(manager);
    }

    @Test
    void untrustedMissingAndNearMatchOriginsCannotReceiveSessionTokens() throws Exception {
        for (String origin : new String[] {"http://evil.example", ORIGIN + ".evil.example", "http://localhost:5174", "null"}) {
            mvc().perform(post("/api/v1/session/refresh").header("Origin", origin).session(session()))
                    .andExpect(status().isForbidden());
        }
        mvc().perform(post("/api/v1/session/refresh").session(session())).andExpect(status().isForbidden());
        verifyNoInteractions(manager);
    }

    @Test
    void revokedRefreshTokenReturns401WithoutExposingProviderDetails() throws Exception {
        when(manager.authorize(any())).thenThrow(new OAuth2AuthorizationException(new OAuth2Error(
                "invalid_grant", "server-only-client-secret", null)));
        mvc().perform(post("/api/v1/session/refresh").header("Origin", ORIGIN).session(session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your sign-in session expired. Please sign in again"));
    }

    @Test
    void providerOutageIsNotMisreportedAsAnExpiredSession() throws Exception {
        when(manager.authorize(any())).thenThrow(new OAuth2AuthorizationException(new OAuth2Error(
                "server_error", "provider internals", null)));
        mvc().perform(post("/api/v1/session/refresh").header("Origin", ORIGIN).session(session()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Unable to renew your sign-in session. Please try again"));
    }

    @Test
    void anExpiredUnrenewableTokenIsNeverRelayed() throws Exception {
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration("http://localhost/token"), "user-id",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired-token", Instant.now().minusSeconds(400), Instant.now().minusSeconds(100))));
        mvc().perform(post("/api/v1/session/refresh").header("Origin", ORIGIN).session(session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheConfiguredFrontendHasCredentialedCorsAccess() throws Exception {
        var cors = new CorsFilter(new SecurityConfig().corsConfigurationSource(origins));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(cors).build();
        mvc.perform(options("/api/v1/session/refresh").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/v1/session/refresh").header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test
    void originsAreDerivedOnlyFromAllowedHttpCallbacks() {
        var allowed = new FrontendOrigins("https://app.example:443/callback, http://localhost:5173/login-redirect,"
                + "native-app://login, invalid, https://user:pass@evil.example/callback");
        assertEquals(List.of("https://app.example", ORIGIN), allowed.values());
        assertFalse(allowed.allows(null));
        assertFalse(allowed.allows(ORIGIN + "/"));
    }

    @Test
    void springRefreshesExpiredProviderTokenAndStoresRotatedRefreshToken() throws Exception {
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String[] submitted = {null};
        provider.createContext("/token", exchange -> {
            submitted[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = """
                    {"access_token":"new-provider-token","token_type":"Bearer","expires_in":300,
                     "refresh_token":"rotated-server-only-refresh"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        provider.start();
        try {
            var registration = registration("http://127.0.0.1:" + provider.getAddress().getPort() + "/token");
            var clients = new HttpSessionOAuth2AuthorizedClientRepository();
            var configuredManager = new SecurityConfig().authorizedClientManager(
                    new InMemoryClientRegistrationRepository(registration), clients);
            var request = new MockHttpServletRequest();
            request.addHeader("Origin", ORIGIN);
            request.setSession(session());
            var response = new MockHttpServletResponse();
            var authentication = authentication();
            clients.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, authentication.getName(),
                    new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "old-provider-token", Instant.now().minusSeconds(600), Instant.now().minusSeconds(300)),
                    new OAuth2RefreshToken("server-only-refresh", Instant.now().minusSeconds(600))), authentication, request, response);
            var token = new SessionController(configuredManager, origins).refresh(request, response);
            assertEquals("new-provider-token", token.accessToken());
            assertTrue(submitted[0].contains("grant_type=refresh_token"));
            assertTrue(submitted[0].contains("refresh_token=server-only-refresh"));
            OAuth2AuthorizedClient saved = clients.loadAuthorizedClient("common-idp", authentication, request);
            assertEquals("rotated-server-only-refresh", saved.getRefreshToken().getTokenValue());
        } finally { provider.stop(0); }
    }
}
