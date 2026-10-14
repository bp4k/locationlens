package net.bp4k.locationlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;

import net.bp4k.locationlens.controller.*;
import net.bp4k.locationlens.entity.*;
import net.bp4k.locationlens.repo.*;

/** Real controllers, services and JPA; token signature verification is exercised in browser E2E. */
@SpringJUnitConfig(ReviewFlowIntegrationTest.DatabaseConfig.class)
class ReviewFlowIntegrationTest {
    private static final String USER = "a852a1c9-1384-4571-8c53-7eeeb4975329";
    private static final String OTHER = "b852a1c9-1384-4571-8c53-7eeeb4975329";

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ReviewRepository.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
            classes = {ReviewRepository.class, UserRepository.class, PlaceRepository.class}))
    @Import({ReviewService.class, AuthenticationService.class, PlaceSearchService.class})
    static class DatabaseConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:review-flow;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setManagedTypes(PersistenceManagedTypes.of(Review.class.getName(), User.class.getName(), Place.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.globally_quoted_identifiers", "true"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Autowired private AuthenticationService authentication;
    @Autowired private ReviewService reviews;
    @Autowired private PlaceSearchService places;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reviewRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        mvc = standaloneSetup(new ReviewController(reviews, authentication), new PlaceController(places))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private void login(String subject) {
        // Same user provisioning method called by IdpTokenRelaySuccessHandler.
        authentication.createOrUpdateUser(subject, subject + "@example.com", "Test", "User", false);
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test void selectCreateReadHistoryAndDuplicateUseCommittedDatabaseRecords() throws Exception {
        login(USER);
        mvc.perform(get("/api/v1/places/dummy-1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.place_id").value("dummy-1"));
        mvc.perform(get("/api/v1/reviews/place/dummy-1"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        String request = """
            {"place_id":"dummy-1","rating":5,"text":"Lovely park!"}
            """;
        var response = mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user_id").value(USER))
            .andExpect(jsonPath("$.place_id").value("dummy-1"))
            .andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.created_at").exists())
            .andReturn().getResponse().getContentAsString();
        var parsed = JsonMapper.builder().build().readTree(response);
        assertTrue(reviewRepository.findById(UUID.fromString(parsed.get("review_id").asString())).isPresent());
        mvc.perform(get("/api/v1/reviews/place/dummy-1"))
            .andExpect(status().isOk()).andExpect(content().json("[" + response + "]"));
        mvc.perform(get("/api/v1/reviews/history"))
            .andExpect(status().isOk()).andExpect(content().json("[" + response + "]"));
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("You have already reviewed this place"));
        assertEquals(1, reviewRepository.count());
        login(OTHER);
        mvc.perform(get("/api/v1/reviews/history"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/reviews/place/dummy-1"))
            .andExpect(status().isOk()).andExpect(content().json("[" + response + "]"));
    }

    @Test void invalidSubmissionDoesNotPersistAndMissingIdentityIsRejected() throws Exception {
        login(USER);
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
            .content("{\"place_id\":\"dummy-1\",\"rating\":6,\"text\":\"bad\"}"))
            .andExpect(status().isBadRequest());
        assertEquals(0, reviewRepository.count());
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
            .content("{\"place_id\":\"dummy-1\",\"rating\":5,\"text\":\"valid\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reviews/history")).andExpect(status().isUnauthorized());
    }
}
