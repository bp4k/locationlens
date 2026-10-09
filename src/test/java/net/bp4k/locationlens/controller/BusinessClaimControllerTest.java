package net.bp4k.locationlens.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import net.bp4k.locationlens.model.BusinessClaimRequest;
import net.bp4k.locationlens.model.ClaimStatus;
import net.bp4k.locationlens.service.BusinessClaimService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessClaimControllerTest {
    private final BusinessClaimService service = mock(BusinessClaimService.class);
    private final BusinessClaimController controller = controllerFor(service);

    @SuppressWarnings("unchecked")
    private static BusinessClaimController controllerFor(BusinessClaimService service) {
        ObjectProvider<BusinessClaimService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return new BusinessClaimController(provider);
    }

    private static Jwt token(String subject, Object realmAccess) {
        return Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject(subject).claim("realm_access", realmAccess).build();
    }

    private static Jwt user() {
        return token("user-123", Map.of("roles", List.of("user")));
    }

    private static Jwt admin() {
        return token("admin-456", Map.of("roles", List.of("admin")));
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    private MockMvc mvc(Jwt jwt) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }
                    public Object resolveArgument(MethodParameter parameter,
                            ModelAndViewContainer container, NativeWebRequest request,
                            WebDataBinderFactory factory) {
                        return jwt;
                    }
                }).build();
    }

    @Test
    void httpSubmissionBindsJsonAndReturnsCreatedClaim() throws Exception {
        when(service.submitClaim("user-123", "place-1")).thenReturn(
                new BusinessClaimRequest("claim-1", "user-123", "place-1", ClaimStatus.PENDING));
        mvc(user()).perform(post("/api/v1/business-claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-1\",\"userId\":\"spoofed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(service).submitClaim("user-123", "place-1");
    }

    @Test
    void httpAdminRoutesReturnListAndNoContent() throws Exception {
        when(service.getPendingClaims()).thenReturn(List.of());
        MockMvc mvc = mvc(admin());
        mvc.perform(get("/api/v1/business-claims/pending"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/v1/business-claims/claim-1/approve"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/v1/business-claims/claim-2/reject"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).approveClaim("claim-1", "admin-456");
        verify(service).rejectClaim("claim-2", "admin-456");
    }

    @Test
    void httpRejectsMissingBodyAndOrdinaryAdminAccess() throws Exception {
        MockMvc mvc = mvc(user());
        mvc.perform(post("/api/v1/business-claims").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/business-claims").contentType(MediaType.APPLICATION_JSON)
                .content("{}")) .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/business-claims/pending")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/business-claims/claim-1/approve")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/business-claims/claim-1/reject")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void submissionUsesAuthenticatedSubjectAndReturnsSavedClaim() {
        BusinessClaimRequest saved = new BusinessClaimRequest(
                "claim-1", "user-123", "place-1", ClaimStatus.PENDING);
        when(service.submitClaim("user-123", "place-1")).thenReturn(saved);
        assertSame(saved, controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1"), user()));
        verify(service).submitClaim("user-123", "place-1");
    }

    @Test
    void rejectsInvalidSubmissionBeforeCallingService() {
        for (String placeId : new String[] {null, "", "   "}) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> controller.submitClaim(
                    new BusinessClaimController.SubmitClaimRequest(placeId), user()));
        }
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.submitClaim(null, user()));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnauthenticatedCalls() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1"), null));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.getPendingClaims(null));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.approveClaim("claim-1", null));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.rejectClaim("claim-1", null));
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryUserCannotListOrReviewClaims() {
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.getPendingClaims(user()));
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.approveClaim("claim-1", user()));
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.rejectClaim("claim-1", user()));
        verifyNoInteractions(service);
    }

    @Test
    void absentOrMalformedRoleClaimsDenyAccess() {
        for (Object realmAccess : List.of("admin", Map.of(), Map.of("roles", "admin"))) {
            assertStatus(HttpStatus.FORBIDDEN,
                    () -> controller.getPendingClaims(token("user-123", realmAccess)));
        }
        Jwt noRoles = Jwt.withTokenValue("test").header("alg", "RS256").subject("user-123").build();
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.getPendingClaims(noRoles));
        verifyNoInteractions(service);
    }

    @Test
    void administratorListsAndReviewsUsingOwnSubject() {
        List<BusinessClaimRequest> pending = List.of(new BusinessClaimRequest(
                "claim-1", "user-123", "place-1", ClaimStatus.PENDING));
        when(service.getPendingClaims()).thenReturn(pending);
        assertSame(pending, controller.getPendingClaims(admin()));
        controller.approveClaim("claim-1", admin());
        controller.rejectClaim("claim-2", admin());
        verify(service).approveClaim("claim-1", "admin-456");
        verify(service).rejectClaim("claim-2", "admin-456");
    }

    @Test
    void rejectsBlankClaimIds() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.approveClaim(" ", admin()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.rejectClaim(null, admin()));
        verifyNoInteractions(service);
    }

    @Test
    void missingServiceDoesNotReportSuccess() {
        BusinessClaimController draft = controllerFor(null);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> draft.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1"), user()));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> draft.getPendingClaims(admin()));
    }

    @Test
    void failedWritesAndReviewErrorsPropagate() {
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        when(service.submitClaim("user-123", "place-1")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> controller.submitClaim(
                new BusinessClaimController.SubmitClaimRequest("place-1"), user())));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(service)
                .approveClaim("missing", "admin-456");
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.approveClaim("missing", admin()));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT)).when(service)
                .rejectClaim("reviewed", "admin-456");
        assertStatus(HttpStatus.CONFLICT, () -> controller.rejectClaim("reviewed", admin()));
    }
}
