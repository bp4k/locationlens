package net.bp4k.locationlens.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import net.bp4k.locationlens.entity.Review;
import net.bp4k.locationlens.service.ReviewService;

class ReviewControllerTest {
    /** These are dummy UUID that will already be presented from the user  */
    private static final UUID USER_ID = UUID.fromString("a852a1c9-1384-4571-8c53-7eeeb4975329");
    private static final UUID REVIEW_ID =
            UUID.fromString("b852a1c9-1384-4571-8c53-7eeeb4975329");
    private static final String PLACE_ID = "place-123";
    private static final Instant CREATED_AT =
            Instant.parse("2026-01-01T12:00:00Z");

    private ReviewService reviewService; 
    private MockMvc mockMvc; 
    private Review review; 
    
    @BeforeEach 
    void setUp()
    {
        reviewService = mock(ReviewService.class);
        //Mvc may try to construct jwt from request, which can produce "tokenValue cannot be empty" error
        mockMvc = standaloneSetup(new ReviewController(reviewService)).setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
    
        review = new Review(REVIEW_ID, 5, "Great place", CREATED_AT, USER_ID, PLACE_ID);
    }

    @AfterEach 
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
    }


    @Test 
    void submitReviewAcceptsPlaceIdAndUsesAuthenticatedUser() throws Exception
    {
        authenticateAs(USER_ID);
        when(reviewService.submitReview(
                USER_ID, PLACE_ID, 5, "Great place"))
                .thenReturn(review);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "place_id": "place-123",
                                  "rating": 5,
                                  "text": "Great place"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.review_id").value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$.place_id").value(PLACE_ID))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.text").value("Great place"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.user_id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());

        verify(reviewService).submitReview(
                USER_ID, PLACE_ID, 5, "Great place");
    }

    @Test
    void submitReviewAcceptsCamelCasePlaceIdAlias() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.submitReview(USER_ID, PLACE_ID, 4, "Nice"))
                .thenReturn(review);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId": "place-123", "rating": 4, "text": "Nice"}
                                """))
                .andExpect(status().isCreated());

        verify(reviewService).submitReview(USER_ID, PLACE_ID, 4, "Nice");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void submitReviewAcceptsBoundaryRatings(int rating) throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.submitReview(USER_ID, PLACE_ID, rating, "ok"))
                .thenReturn(review);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"place-123\", \"rating\": " + rating + ", \"text\": \"ok\"}"))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1, 100})
    void submitReviewRejectsRatingOutsideOneToFive(int rating) throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"place-123\", \"rating\": " + rating + ", \"text\": \"ok\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void submitReviewRejectsMissingRating() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"place-123\", \"text\": \"ok\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void submitReviewRejectsBlankPlaceId() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"  \", \"rating\": 4, \"text\": \"ok\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void submitReviewRejectsBlankText() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"place-123\", \"rating\": 4, \"text\": \"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void submitReviewRejectsTextLongerThan2000Characters() throws Exception {
        authenticateAs(USER_ID);
        String tooLong = "a".repeat(2001);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"place-123\", \"rating\": 4, \"text\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void submitReviewAcceptsTextOfExactly2000Characters() throws Exception {
        authenticateAs(USER_ID);
        String maxText = "a".repeat(2000);
        when(reviewService.submitReview(USER_ID, PLACE_ID, 4, maxText))
                .thenReturn(review);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place_id\": \"place-123\", \"rating\": 4, \"text\": \"" + maxText + "\"}"))
                .andExpect(status().isCreated());
    }

    /*
        Results: 2026-10-09T15:10:59.985-04:00  WARN 40598 --- [locationlens] [           main] 
        .w.s.m.s.DefaultHandlerExceptionResolver : 
        Resolved [org.springframework.http.converter.HttpMessageNotReadableException: 
        JSON parse error: Unexpected character ('i' (code 105)): 
        was expecting double-quote to start property name]
    */
    @Test
    void submitReviewRejectsMalformedJson() throws Exception {
        authenticateAs(USER_ID);

    
        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void getPlaceReviewsReturnsMatchingReviews() throws Exception {
        when(reviewService.getReviewsForPlace(PLACE_ID))
                .thenReturn(List.of(review));

        mockMvc.perform(get("/api/v1/reviews/place/{placeId}", PLACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].review_id")
                        .value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$[0].place_id").value(PLACE_ID))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].text").value("Great place"))
                .andExpect(jsonPath("$[0].created_at").exists())
                .andExpect(jsonPath("$[0].userId").doesNotExist());

        verify(reviewService).getReviewsForPlace(PLACE_ID);
    }

    @Test
    void getPlaceReviewsReturnsEmptyArrayWhenThereAreNoReviews()
            throws Exception {
        when(reviewService.getReviewsForPlace(PLACE_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reviews/place/{placeId}", PLACE_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getReviewHistoryUsesAuthenticatedUserId() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.getReviewHistory(USER_ID))
                .thenReturn(List.of(review));

        mockMvc.perform(get("/api/v1/reviews/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].review_id")
                        .value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$[0].userId").doesNotExist());

        verify(reviewService).getReviewHistory(USER_ID);
    }

    @Test
    void getReviewHistoryReturnsEmptyArrayWhenThereAreNoReviews()
            throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.getReviewHistory(USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reviews/history"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
   
    
}
