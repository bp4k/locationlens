package net.bp4k.locationlens.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManagerFactory;
import net.bp4k.locationlens.controller.BusinessClaimController;
import net.bp4k.locationlens.dto.BusinessClaimRequest;
import net.bp4k.locationlens.entity.BusinessClaim;
import net.bp4k.locationlens.model.ClaimStatus;
import net.bp4k.locationlens.repo.BusinessClaimRepository;

import static org.junit.jupiter.api.Assertions.*;

/** Real JPA transactions and row locking without an external OIDC server. */
@SpringJUnitConfig(BusinessClaimServiceIntegrationTest.DatabaseConfig.class)
class BusinessClaimServiceIntegrationTest {
    private static final String USER_ID = "a852a1c9-1384-4571-8c53-7eeeb4975329";
    private static final String ADMIN_ID = "b952a1c9-1384-4571-8c53-7eeeb4975329";
    private static final String OTHER_ADMIN_ID = "d952a1c9-1384-4571-8c53-7eeeb4975329";

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = BusinessClaimRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = BusinessClaimRepository.class))
    @Import({BusinessClaimService.class, BusinessClaimController.class})
    static class DatabaseConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:claim-tests;DB_CLOSE_DELAY=-1;"
                    + "LOCK_TIMEOUT=5000", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(BusinessClaim.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Autowired private BusinessClaimService service;
    @Autowired private BusinessClaimController controller;
    @Autowired private BusinessClaimRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearClaims() {
        repository.deleteAllInBatch();
    }

    @Test
    void submissionAndPendingQueryReadCommittedDatabaseState() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(USER_ID).build();
        BusinessClaimRequest created = controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1"), jwt);
        BusinessClaim persisted = repository.findById(UUID.fromString(created.id())).orElseThrow();
        assertEquals(UUID.fromString(USER_ID), persisted.getUserId());
        assertEquals("place-1", persisted.getPlaceId());
        assertEquals(ClaimStatus.PENDING, persisted.getStatus());
        assertNotNull(persisted.getCreatedAt());
        assertEquals(List.of(created), service.getPendingClaims());
    }

    @Test
    void decisionsPersistAuditFieldsAndRemoveClaimsFromPendingList() {
        BusinessClaimRequest approved = service.submitClaim(USER_ID, "place-1");
        BusinessClaimRequest rejected = service.submitClaim(USER_ID, "place-2");
        BusinessClaimRequest pending = service.submitClaim(USER_ID, "place-3");
        service.approveClaim(approved.id(), ADMIN_ID);
        service.rejectClaim(rejected.id(), OTHER_ADMIN_ID);
        BusinessClaim savedApproval = repository.findById(UUID.fromString(approved.id())).orElseThrow();
        BusinessClaim savedRejection = repository.findById(UUID.fromString(rejected.id())).orElseThrow();
        assertEquals(ClaimStatus.APPROVED, savedApproval.getStatus());
        assertEquals(UUID.fromString(ADMIN_ID), savedApproval.getReviewedBy());
        assertNotNull(savedApproval.getReviewedAt());
        assertEquals(ClaimStatus.REJECTED, savedRejection.getStatus());
        assertEquals(UUID.fromString(OTHER_ADMIN_ID), savedRejection.getReviewedBy());
        assertNotNull(savedRejection.getReviewedAt());
        assertEquals(List.of(pending), service.getPendingClaims());
    }

    @Test
    void failedTransactionRollsBackSubmission() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.submitClaim(USER_ID, "place-1");
            throw new IllegalStateException("Simulated failure before commit");
        }));
        assertEquals(0, repository.count());
    }

    @Test
    void failedTransactionRollsBackDecisionAndAuditTogether() {
        BusinessClaimRequest created = service.submitClaim(USER_ID, "place-1");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.approveClaim(created.id(), ADMIN_ID);
            throw new IllegalStateException("Simulated failure before commit");
        }));
        BusinessClaim persisted = repository.findById(UUID.fromString(created.id())).orElseThrow();
        assertEquals(ClaimStatus.PENDING, persisted.getStatus());
        assertNull(persisted.getReviewedBy());
        assertNull(persisted.getReviewedAt());
    }

    @Test
    void concurrentAdministratorsCannotOverwriteEachOthersDecision() throws Exception {
        BusinessClaimRequest created = service.submitClaim(USER_ID, "place-1");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpStatus> approval = executor.submit(() -> decideAfter(start, created.id(), true));
            Future<HttpStatus> rejection = executor.submit(() -> decideAfter(start, created.id(), false));
            start.countDown();
            HttpStatus approvalResult = approval.get(10, TimeUnit.SECONDS);
            HttpStatus rejectionResult = rejection.get(10, TimeUnit.SECONDS);
            assertTrue((approvalResult == HttpStatus.NO_CONTENT && rejectionResult == HttpStatus.CONFLICT)
                    || (approvalResult == HttpStatus.CONFLICT && rejectionResult == HttpStatus.NO_CONTENT));
            BusinessClaim persisted = repository.findById(UUID.fromString(created.id())).orElseThrow();
            assertEquals(approvalResult == HttpStatus.NO_CONTENT ? ClaimStatus.APPROVED : ClaimStatus.REJECTED,
                    persisted.getStatus());
            assertEquals(UUID.fromString(approvalResult == HttpStatus.NO_CONTENT ? ADMIN_ID : OTHER_ADMIN_ID),
                    persisted.getReviewedBy());
            assertNotNull(persisted.getReviewedAt());
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpStatus decideAfter(CountDownLatch start, String id, boolean approve) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Start timeout"); }
        try {
            if (approve) { service.approveClaim(id, ADMIN_ID); }
            else { service.rejectClaim(id, OTHER_ADMIN_ID); }
            return HttpStatus.NO_CONTENT;
        } catch (ResponseStatusException ex) {
            return HttpStatus.valueOf(ex.getStatusCode().value());
        }
    }
}
