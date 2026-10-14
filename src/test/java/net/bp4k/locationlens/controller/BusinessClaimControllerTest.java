package net.bp4k.locationlens.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import net.bp4k.locationlens.entity.User;
import net.bp4k.locationlens.exception.InvalidTokenException;
import net.bp4k.locationlens.model.BusinessClaimRequest;
import net.bp4k.locationlens.model.ClaimStatus;
import net.bp4k.locationlens.service.AuthenticationService;
import net.bp4k.locationlens.service.BusinessClaimService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessClaimControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    private final BusinessClaimService service = mock(BusinessClaimService.class);
    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final BusinessClaimController controller = controllerFor(service, authenticationService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static BusinessClaimController controllerFor(BusinessClaimService service,
            AuthenticationService authenticationService) {
        return controllerFor(service, authenticationService, false);
    }

    @SuppressWarnings("unchecked")
    private static BusinessClaimController controllerFor(BusinessClaimService service,
            AuthenticationService authenticationService, boolean missingService) {
        ObjectProvider<BusinessClaimService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(missingService ? null : service);
        return new BusinessClaimController(provider, authenticationService);
    }

    private static User adminUser() {
        return new User(ADMIN_ID, "admin@example.com", "Admin", true);
    }

    private static User ordinaryUser() {
        return new User(USER_ID, "user@example.com", "User", false);
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    /**
     * Wraps {@code target} in a proxy that enforces {@code @PreAuthorize}
     * exactly as {@code @EnableMethodSecurity} would in the running
     * application, without needing a full Spring context. Used only by
     * tests that exercise the authorization decision itself; tests of
     * validation or success paths call {@link #controller} directly,
     * since direct calls do not go through this proxy and therefore
     * never enforce {@code @PreAuthorize}.
     */
    private static BusinessClaimController securedProxy(BusinessClaimController target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (BusinessClaimController) factory.getProxy();
    }

    private static void authenticateAsOrdinaryUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", "n/a"));
    }

    private static void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN"));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void verificationFailureIncludesAnActionableMessage() throws Exception {
        when(service.submitClaim("user-123", "missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Selected business place was not found"));
        mvc(user()).perform(post("/api/v1/business-claims")
                .contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Selected business place was not found"));
    }

    @Test
    void httpSubmissionBindsJsonAndReturnsCreatedClaim() throws Exception {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        when(service.submitClaim(USER_ID.toString(), "place-1")).thenReturn(
                new BusinessClaimRequest("claim-1", USER_ID.toString(), "place-1", ClaimStatus.PENDING));

        mvc().perform(post("/api/v1/business-claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-1\",\"userId\":\"spoofed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(service).submitClaim(USER_ID.toString(), "place-1");
    }

    @Test
    void httpAdminRoutesReturnListAndNoContent() throws Exception {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(adminUser());
        when(service.getPendingClaims()).thenReturn(List.of());
        MockMvc mvc = mvc();
        mvc.perform(get("/api/v1/business-claims/pending"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/v1/business-claims/claim-1/approve"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/v1/business-claims/claim-2/reject"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).approveClaim("claim-1", ADMIN_ID.toString());
        verify(service).rejectClaim("claim-2", ADMIN_ID.toString());
    }

    @Test
    void httpRejectsMissingOrBlankSubmissionBody() throws Exception {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        MockMvc mvc = mvc();
        mvc.perform(post("/api/v1/business-claims").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/business-claims").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void submissionUsesAuthenticatedSubjectAndReturnsSavedClaim() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        BusinessClaimRequest saved = new BusinessClaimRequest(
                "claim-1", USER_ID.toString(), "place-1", ClaimStatus.PENDING);
        when(service.submitClaim(USER_ID.toString(), "place-1")).thenReturn(saved);
        assertSame(saved, controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1")));
        verify(service).submitClaim(USER_ID.toString(), "place-1");
    }

    @Test
    void rejectsInvalidSubmissionBeforeCallingService() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        for (String placeId : new String[] {null, "", "   "}) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> controller.submitClaim(
                    new BusinessClaimController.SubmitClaimRequest(placeId)));
        }
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.submitClaim(null));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnauthenticatedCalls() {
        when(authenticationService.requireAuthenticatedUser())
                .thenThrow(new InvalidTokenException("User is not logged in"));

        assertThrows(InvalidTokenException.class, () -> controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1")));
        assertThrows(InvalidTokenException.class, controller::getPendingClaims);
        assertThrows(InvalidTokenException.class, () -> controller.approveClaim("claim-1"));
        assertThrows(InvalidTokenException.class, () -> controller.rejectClaim("claim-1"));
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryUserCannotListOrReviewClaims() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        BusinessClaimController proxy = securedProxy(controller);
        authenticateAsOrdinaryUser();

        assertThrows(AccessDeniedException.class, proxy::getPendingClaims);
        assertThrows(AccessDeniedException.class, () -> proxy.approveClaim("claim-1"));
        assertThrows(AccessDeniedException.class, () -> proxy.rejectClaim("claim-1"));
        verifyNoInteractions(service);
    }

    @Test
    void administratorListsAndReviewsUsingOwnSubject() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(adminUser());
        List<BusinessClaimRequest> pending = List.of(new BusinessClaimRequest(
                "claim-1", USER_ID.toString(), "place-1", ClaimStatus.PENDING));
        when(service.getPendingClaims()).thenReturn(pending);
        assertSame(pending, controller.getPendingClaims());
        controller.approveClaim("claim-1");
        controller.rejectClaim("claim-2");
        verify(service).approveClaim("claim-1", ADMIN_ID.toString());
        verify(service).rejectClaim("claim-2", ADMIN_ID.toString());
    }

    @Test
    void administratorAccessIsGrantedThroughTheSecuredProxy() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(adminUser());
        when(service.getPendingClaims()).thenReturn(List.of());
        BusinessClaimController proxy = securedProxy(controller);
        authenticateAsAdmin();

        assertEquals(List.of(), proxy.getPendingClaims());
        proxy.approveClaim("claim-1");
        proxy.rejectClaim("claim-2");
        verify(service).approveClaim("claim-1", ADMIN_ID.toString());
        verify(service).rejectClaim("claim-2", ADMIN_ID.toString());
    }

    @Test
    void rejectsBlankClaimIds() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(adminUser());
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.approveClaim(" "));
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.rejectClaim(null));
        verifyNoInteractions(service);
    }

    @Test
    void missingServiceDoesNotReportSuccess() {
        BusinessClaimController draft = controllerFor(null, authenticationService, true);
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> draft.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1")));
        when(authenticationService.requireAuthenticatedUser()).thenReturn(adminUser());
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, draft::getPendingClaims);
    }

    @Test
    void failedWritesAndReviewErrorsPropagate() {
        when(authenticationService.requireAuthenticatedUser()).thenReturn(ordinaryUser());
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        when(service.submitClaim(USER_ID.toString(), "place-1")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1"))));

        when(authenticationService.requireAuthenticatedUser()).thenReturn(adminUser());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(service)
                .approveClaim("missing", ADMIN_ID.toString());
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.approveClaim("missing"));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT)).when(service)
                .rejectClaim("reviewed", ADMIN_ID.toString());
        assertStatus(HttpStatus.CONFLICT, () -> controller.rejectClaim("reviewed"));
    }
}
