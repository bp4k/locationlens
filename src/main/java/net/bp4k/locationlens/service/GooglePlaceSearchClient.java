package net.bp4k.locationlens.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import net.bp4k.locationlens.dto.CriteriaRequest;
import net.bp4k.locationlens.dto.PlaceResponse;
import net.bp4k.locationlens.model.PlaceType;

/** Places API (New) adapter. Keys and provider error bodies stay on the server. */
@Service
public class GooglePlaceSearchClient {
    private static final String DETAIL_FIELDS = "id,displayName,formattedAddress,location,types";
    private static final String SEARCH_FIELDS =
            "places.id,places.displayName,places.formattedAddress,places.location,places.types";
    private static final Map<PlaceType, List<String>> GOOGLE_TYPES = Map.of(
            PlaceType.RESTAURANT, List.of("restaurant", "cafe", "coffee_shop"),
            PlaceType.BATHROOM, List.of("public_bathroom"),
            PlaceType.PARK, List.of("park", "national_park", "state_park", "dog_park"),
            PlaceType.ATTRACTION, List.of("tourist_attraction", "museum", "aquarium", "zoo", "amusement_park"),
            PlaceType.HOTEL, List.of("hotel", "motel", "resort_hotel", "bed_and_breakfast", "hostel"),
            PlaceType.HOSPITAL, List.of("hospital"));

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public GooglePlaceSearchClient(
            @Value("${net.bp4k.locationlens.places.api-key:}") String apiKey,
            @Value("${net.bp4k.locationlens.places.base-url:https://places.googleapis.com/v1}") String baseUrl,
            ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.mapper = mapper;
    }

    private record Coordinates(Double latitude, Double longitude) {}
    private record DisplayName(String text) {}
    private record ProviderPlace(String id, DisplayName displayName, String formattedAddress,
            Coordinates location, List<String> types) {}
    private record SearchResults(List<ProviderPlace> places) {}

    public List<PlaceResponse> search(CriteriaRequest criteria) {
        criteria.validate();
        Coordinates center;
        if (criteria.latitude() != null) {
            center = new Coordinates(criteria.latitude(), criteria.longitude());
        } else {
            // Resolve the submitted city/address first, then apply the same strict
            // radius in meters used by coordinate searches. No extra Geocoding API.
            SearchResults location = request(URI.create(baseUrl + "/places:searchText"),
                    Map.of("textQuery", criteria.locationText().trim(), "pageSize", 1),
                    "places.location", SearchResults.class);
            if (location.places() == null || location.places().isEmpty()) return List.of();
            if (location.places().getFirst() == null) throw invalidResponse();
            center = location.places().getFirst().location();
            validateCoordinates(center);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locationRestriction", Map.of("circle",
                Map.of("center", center, "radius", criteria.radiusMeters())));
        body.put("maxResultCount", 20);
        body.put("rankPreference", "DISTANCE");
        if (criteria.placeType() == PlaceType.OTHER) {
            body.put("excludedTypes", GOOGLE_TYPES.values().stream().flatMap(List::stream).distinct().sorted().toList());
        } else if (criteria.placeType() != null) {
            body.put("includedTypes", GOOGLE_TYPES.get(criteria.placeType()));
        }
        SearchResults results = request(URI.create(baseUrl + "/places:searchNearby"), body,
                SEARCH_FIELDS, SearchResults.class);
        if (results.places() == null) return List.of();
        return results.places().stream().map(this::toPlaceResponse).toList();
    }

    public PlaceResponse getPlaceDetails(String placeId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("places", placeId).build().encode().toUri();
        return toPlaceResponse(request(uri, null, DETAIL_FIELDS, ProviderPlace.class));
    }

    private PlaceResponse toPlaceResponse(ProviderPlace place) {
        if (place == null || place.id() == null || place.id().isBlank() || place.id().length() > 255
                || place.displayName() == null || place.displayName().text() == null
                || place.displayName().text().isBlank() || place.types() == null
                || place.types().stream().anyMatch(type -> type == null || type.isBlank())) {
            throw invalidResponse();
        }
        validateCoordinates(place.location());
        List<PlaceType> types = new ArrayList<>();
        for (PlaceType type : PlaceType.values()) {
            if (GOOGLE_TYPES.getOrDefault(type, List.of()).stream().anyMatch(place.types()::contains)) {
                types.add(type);
            }
        }
        if (types.isEmpty()) types.add(PlaceType.OTHER);
        return new PlaceResponse(place.id(), place.displayName().text(),
                place.formattedAddress() == null ? "" : place.formattedAddress(),
                place.location().latitude(), place.location().longitude(), types);
    }

    private static void validateCoordinates(Coordinates location) {
        if (location == null || location.latitude() == null || location.longitude() == null
                || !Double.isFinite(location.latitude()) || !Double.isFinite(location.longitude())
                || Math.abs(location.latitude()) > 90 || Math.abs(location.longitude()) > 180) {
            throw invalidResponse();
        }
    }

    private <T> T request(URI uri, Object body, String fields, Class<T> responseType) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Place search is not configured. Contact the project administrator");
        }
        // A text search makes two sequential requests. Each has a four-second
        // deadline so the backend responds before the UI's ten-second deadline.
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4))
                .header("X-Goog-Api-Key", apiKey).header("X-Goog-FieldMask", fields);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found");
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Place search is temporarily unavailable. Please try again");
            }
            try {
                T result = mapper.readValue(response.body(), responseType);
                if (result == null) throw invalidResponse();
                return result;
            } catch (RuntimeException invalid) {
                throw invalidResponse();
            }
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Place search timed out. Please try again");
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to reach place search. Please try again");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Place search interrupted");
        }
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from place search");
    }
}
