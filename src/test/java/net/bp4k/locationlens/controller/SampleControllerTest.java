package net.bp4k.locationlens.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link SampleController#adminOnly}, verifying that
 * {@code @PreAuthorize("hasRole('ADMIN')")} is enforced - see
 * {@code docs/securing-admin-endpoints.md}. The proxy built here enforces
 * method security exactly as {@code @EnableMethodSecurity} does in the
 * running application, without needing a full Spring context; a plain
 * direct call to {@link SampleController#adminOnly} would not enforce the
 * annotation at all, since it is inert without this (or Spring's own) AOP
 * proxying.
 */
class SampleControllerTest {

    private final SampleController controller = securedProxy(new SampleController());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static SampleController securedProxy(SampleController target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (SampleController) factory.getProxy();
    }

    @Test
    void adminOnlyReturnsTheExpectedStringForAnAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN"));

        assertEquals("I am an Admin", controller.adminOnly());
    }

    @Test
    void adminOnlyDeniesAnAuthenticatedNonAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", "n/a"));

        assertThrows(AccessDeniedException.class, controller::adminOnly);
    }

    @Test
    void adminOnlyDeniesAnUnauthenticatedCaller() {
        // In production this case never reaches method security at all -
        // apiSecurityFilterChain's .anyRequest().authenticated() already
        // rejects it with 401 before dispatch. This test exercises the
        // method-security layer in isolation, where a missing
        // Authentication surfaces as this exception rather than
        // AccessDeniedException.
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, controller::adminOnly);
    }
}
