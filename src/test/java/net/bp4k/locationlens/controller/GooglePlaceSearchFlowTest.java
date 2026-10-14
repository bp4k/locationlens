package net.bp4k.locationlens.controller;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import net.bp4k.locationlens.entity.Place;
import net.bp4k.locationlens.model.PlaceType;
import net.bp4k.locationlens.repo.PlaceRepository;
import net.bp4k.locationlens.service.GooglePlaceSearchClient;
import net.bp4k.locationlens.service.PlaceSearchService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller/service/provider HTTP flow, with no live Google account needed. */
class GooglePlaceSearchFlowTest {
    private static final String PLACE = """
            {"id":"google-park","displayName":{"text":"River Park"},
             "formattedAddress":"Park address","location":{"latitude":0.0,"longitude":-84.3},
             "types":["park","tourist_attraction","point_of_interest"]}
            """;
    private record ProviderRequest(String path, String method, String body, String fields, String key) {}
    private final List<ProviderRequest> requests = new ArrayList<>();
    private final PlaceRepository repository = mock(PlaceRepository.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer provider;
    private MockMvc mvc;
    private String textResponse = "{\"places\":[{\"location\":{\"latitude\":0,\"longitude\":-84.3}}]}";
    private String nearbyResponse = "{\"places\":[" + PLACE + "]}";
    private String detailsResponse = PLACE;
    private int providerStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/places", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            requests.add(new ProviderRequest(path, exchange.getRequestMethod(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("X-Goog-FieldMask"),
                    exchange.getRequestHeaders().getFirst("X-Goog-Api-Key")));
            String body = path.endsWith(":searchText") ? textResponse
                    : path.endsWith(":searchNearby") ? nearbyResponse : detailsResponse;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(providerStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        provider.start();
        mvc = mvc("server-only-key");
    }

    private MockMvc mvc(String key) {
        var google = new GooglePlaceSearchClient(key,
                "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1", mapper);
        return MockMvcBuilders.standaloneSetup(new PlaceController(new PlaceSearchService(repository, google)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() { provider.stop(0); }

    @Test
    void resolvesSubmittedLocationThenSearchesWithinRadiusAndType() throws Exception {
        mvc.perform(get("/api/v1/places/search").param("location_text", "  Atlanta  ")
                        .param("radius_meters", "1200").param("place_type", "PARK"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].place_id").value("google-park"))
                .andExpect(jsonPath("$[0].name").value("River Park"))
                .andExpect(jsonPath("$[0].types[0]").value("PARK"))
                .andExpect(jsonPath("$[0].types[1]").value("ATTRACTION"));
        assertEquals(2, requests.size());
        assertEquals("Atlanta", mapper.readTree(requests.getFirst().body()).get("textQuery").asString());
        assertEquals("places.location", requests.getFirst().fields());
        var nearby = requests.get(1);
        var json = mapper.readTree(nearby.body());
        assertEquals("/v1/places:searchNearby", nearby.path());
        assertEquals("POST", nearby.method());
        assertEquals("server-only-key", nearby.key());
        assertTrue(nearby.fields().contains("places.id"));
        assertEquals(0.0, json.at("/locationRestriction/circle/center/latitude").asDouble());
        assertEquals(1200, json.at("/locationRestriction/circle/radius").asInt());
        assertEquals("park", json.at("/includedTypes/0").asString());
        assertFalse(json.has("excludedTypes"));
    }

    @Test
    void coordinateSearchAcceptsZeroAndSkipsTextLookup() throws Exception {
        mvc.perform(get("/api/v1/places/search").param("latitude", "0").param("longitude", "0")
                .param("radius_meters", "50000")).andExpect(status().isOk());
        assertEquals(1, requests.size());
        var json = mapper.readTree(requests.getFirst().body());
        assertEquals(0.0, json.at("/locationRestriction/circle/center/longitude").asDouble());
        assertEquals(50000, json.at("/locationRestriction/circle/radius").asInt());
        assertFalse(json.has("includedTypes"));
        assertFalse(json.has("excludedTypes"));
    }

    @Test
    void otherFilterExcludesNamedCategories() throws Exception {
        mvc.perform(get("/api/v1/places/search").param("latitude", "0").param("longitude", "0")
                .param("radius_meters", "500").param("place_type", "OTHER")).andExpect(status().isOk());
        var json = mapper.readTree(requests.getFirst().body());
        assertTrue(json.get("excludedTypes").toString().contains("hospital"));
        assertFalse(json.has("includedTypes"));
    }

    @Test
    void selectedResultHasWorkingDetailEndpointAndCategories() throws Exception {
        mvc.perform(get("/api/v1/places/google-park")).andExpect(status().isOk())
                .andExpect(jsonPath("$.place_id").value("google-park"))
                .andExpect(jsonPath("$.address").value("Park address"))
                .andExpect(jsonPath("$.latitude").value(0.0));
        assertEquals("GET", requests.getFirst().method());
        assertEquals("/v1/places/google-park", requests.getFirst().path());
        assertEquals("id,displayName,formattedAddress,location,types", requests.getFirst().fields());
    }

    @Test
    void localPlaceDetailsDoNotRequireGoogle() throws Exception {
        when(repository.findById("local-place")).thenReturn(Optional.of(new Place(
                "local-place", "Local Park", "Address", 0.0, 0.0, Set.of(PlaceType.PARK))));
        mvc("").perform(get("/api/v1/places/local-place")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Local Park"));
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "radius_meters=1000", "location_text=Atlanta&radius_meters=0",
        "location_text=Atlanta&radius_meters=50001", "location_text=Atlanta&radius_meters=1.5",
        "location_text=Atlanta", "latitude=33&radius_meters=1000",
        "latitude=91&longitude=0&radius_meters=1000", "latitude=0&longitude=-181&radius_meters=1000",
        "latitude=NaN&longitude=0&radius_meters=1000", "latitude=0&longitude=Infinity&radius_meters=1000",
        "location_text=Atlanta&latitude=0&longitude=0&radius_meters=1000",
        "location_text=Atlanta&radius_meters=1000&place_type=UNKNOWN"
    })
    void invalidSearchNeverCallsGoogle(String query) throws Exception {
        mvc.perform(get("/api/v1/places/search?" + query)).andExpect(status().isBadRequest());
        assertTrue(requests.isEmpty());
    }

    @Test
    void missingLocationAndEmptyNearbyResultsAreEmptyLists() throws Exception {
        textResponse = "{}";
        mvc.perform(get("/api/v1/places/search?location_text=unknown&radius_meters=1000"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(1, requests.size());
        nearbyResponse = "{}";
        mvc.perform(get("/api/v1/places/search?latitude=0&longitude=0&radius_meters=1000"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void missingKeyIsAnActionableErrorInsteadOfFakeResults() throws Exception {
        mvc("").perform(get("/api/v1/places/search?location_text=Atlanta&radius_meters=1000"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Place search is not configured. Contact the project administrator"));
        assertTrue(requests.isEmpty());
    }

    @Test
    void nonexistentPlaceIs404AndProviderErrorsDoNotLeak() throws Exception {
        providerStatus = 404;
        mvc.perform(get("/api/v1/places/missing")).andExpect(status().isNotFound());
        for (int status : new int[] {400, 401, 403, 429, 500}) {
            providerStatus = status;
            detailsResponse = "server-only-key provider internals";
            mvc.perform(get("/api/v1/places/google-park")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("Place search is temporarily unavailable. Please try again"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "not json", "{}", "{\"places\":42}"})
    void malformedDetailsAre502(String body) throws Exception {
        detailsResponse = body;
        mvc.perform(get("/api/v1/places/google-park")).andExpect(status().isBadGateway());
    }

    @Test
    void invalidProviderCoordinatesAreRejected() throws Exception {
        nearbyResponse = "{\"places\":[" + PLACE.replace("0.0", "91.0") + "]}";
        mvc.perform(get("/api/v1/places/search?latitude=0&longitude=0&radius_meters=1000"))
                .andExpect(status().isBadGateway());
    }
}
