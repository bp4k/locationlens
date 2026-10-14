package net.bp4k.locationlens.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/** Google Places (New) details lookup. No key or provider error body reaches clients. */
@Service
public class GoogleBusinessPlaceLookup implements BusinessPlaceLookup {
    private static final Set<String> BUSINESS_STATUSES = Set.of(
            "OPERATIONAL", "CLOSED_TEMPORARILY", "CLOSED_PERMANENTLY", "FUTURE_OPENING");
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public GoogleBusinessPlaceLookup(
            @Value("${net.bp4k.locationlens.places.api-key:}") String apiKey,
            @Value("${net.bp4k.locationlens.places.base-url:https://places.googleapis.com/v1}") String baseUrl,
            ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.mapper = mapper;
    }

    private record PlaceDetails(String id, String businessStatus) {}

    @Override
    public String requireBusinessPlace(String placeId) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Business place verification is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("places", placeId).build().encode().toUri())
                .timeout(Duration.ofSeconds(10))
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "id,businessStatus")
                .GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected business place was not found");
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to verify the selected business right now. Please try again");
            }
            PlaceDetails place;
            try {
                place = mapper.readValue(response.body(), PlaceDetails.class);
            } catch (RuntimeException error) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid place verification response");
            }
            if (place == null || place.id() == null || place.id().isBlank() || place.id().length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid place verification response");
            }
            if (place.businessStatus() == null || place.businessStatus().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The selected place is not identified as a business location");
            }
            if (!BUSINESS_STATUSES.contains(place.businessStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid business status response");
            }
            // Existence is verified here, not ownership or eligibility for approval.
            // Closed/future-opening businesses still exist; administrators decide claims.
            return place.id();
        } catch (java.net.http.HttpTimeoutException error) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Business place verification timed out. Please try again");
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to reach business place verification. Please try again");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Business place verification interrupted");
        }
    }
}
