package net.bp4k.locationlens.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class SampleController {
    @GetMapping("/")
    public String home() {
        return "Unauthenticated";
    }

    @GetMapping("/secured")
    public String secure() {
        return "Authenticated";
    }

    /**
     * Example of the declarative way to mark an admin-only endpoint -
     * see {@code docs/securing-admin-endpoints.md}. Unauthenticated
     * callers never reach this method ({@code apiSecurityFilterChain}
     * rejects them first with {@code 401}); an authenticated
     * non-admin caller is rejected here with {@code 403} by
     * {@code @PreAuthorize}, enforced using the {@code ROLE_ADMIN}
     * authority that {@code AdminAuthoritiesConverter} derives from
     * the database on every request.
     * @return a fixed string, once the caller is confirmed to be an
     * admin
     */
    @GetMapping("/api/v1/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "I am an Admin";
    }
}
