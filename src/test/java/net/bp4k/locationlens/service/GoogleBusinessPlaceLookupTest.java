package net.bp4k.locationlens.service;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class GoogleBusinessPlaceLookupTest {
    private HttpServer provider;
    private String baseUrl;
    private int status = 200;
    private String body = "{\"id\":\"canonical-id\",\"businessStatus\":\"OPERATIONAL\"}";
    private String path;
    private String fieldMask;
    private String apiKey;

    @BeforeEach
    void startProvider() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/places", exchange -> {
            path = exchange.getRequestURI().getRawPath();
            fieldMask = exchange.getRequestHeaders().getFirst("X-Goog-FieldMask");
            apiKey = exchange.getRequestHeaders().getFirst("X-Goog-Api-Key");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        provider.start();
        baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopProvider() { provider.stop(0); }

    private GoogleBusinessPlaceLookup lookup(String key) {
        return new GoogleBusinessPlaceLookup(key, baseUrl, JsonMapper.builder().build());
    }

    private void assertStatus(HttpStatus expected) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> lookup("server-only-key").requireBusinessPlace("requested-id"));
        assertEquals(expected, error.getStatusCode());
        assertFalse(error.getReason().contains("server-only-key"));
    }

    @Test
    void requestsOnlyVerificationFieldsAndReturnsCanonicalId() {
        assertEquals("canonical-id", lookup("server-only-key").requireBusinessPlace("provider/a?b"));
        assertEquals("/v1/places/provider%2Fa%3Fb", path);
        assertEquals("id,businessStatus", fieldMask);
        assertEquals("server-only-key", apiKey);
    }

    @Test
    void missingKeyFailsClosedWithoutContactingProvider() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class,
                        () -> lookup("").requireBusinessPlace("place-id")).getStatusCode());
        assertNull(path);
    }

    @Test
    void nonexistentPlaceReturnsNotFound() { status = 404; assertStatus(HttpStatus.NOT_FOUND); }

    @Test
    void placeWithoutBusinessStatusIsNotClaimable() {
        body = "{\"id\":\"geographic-location\"}";
        assertStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void providerFailuresAreNotMisreportedAsMissingBusinesses() {
        for (int failure : new int[] {400, 401, 403, 429, 500, 503}) {
            status = failure;
            body = "provider internals containing server-only-key";
            assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void malformedProviderResponsesFailClosed() {
        for (String invalid : new String[] {"not json", "null", "{}",
                "{\"businessStatus\":\"OPERATIONAL\"}",
                "{\"id\":\"place\",\"businessStatus\":\"UNKNOWN\"}"}) {
            body = invalid;
            assertStatus(HttpStatus.BAD_GATEWAY);
        }
    }

    @Test
    void closedBusinessesStillExistAndRequireAdministratorReview() {
        body = "{\"id\":\"closed-business\",\"businessStatus\":\"CLOSED_PERMANENTLY\"}";
        assertEquals("closed-business", lookup("key").requireBusinessPlace("closed-business"));
    }
}
