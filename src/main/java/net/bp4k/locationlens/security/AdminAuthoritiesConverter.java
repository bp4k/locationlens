package net.bp4k.locationlens.security;

import java.util.Collection;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import net.bp4k.locationlens.service.AuthenticationService;

/**
 * Grants {@code ROLE_ADMIN} to a bearer-authenticated request if, and only
 * if, {@link AuthenticationService#isAdmin} says the token's subject is an
 * admin in the database. This is looked up fresh on every request, so the
 * database - not anything embedded in the token - stays the only source of
 * truth for admin status.
 *
 * <p>Wired into {@code apiSecurityFilterChain}'s
 * {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))} configuration in
 * {@code SecurityConfig}, this is what lets any {@code /api/v1/**} endpoint
 * require admin access with a single declarative
 * {@code @PreAuthorize("hasRole('ADMIN')")} annotation, rather than each
 * controller re-implementing its own admin check.
 *
 * @author Advaith Menon
 */
@Component
public class AdminAuthoritiesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final AuthenticationService authenticationService;

    public AdminAuthoritiesConverter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * {@inheritDoc}
     * @param jwt the caller's validated bearer token
     * @return a single-element set containing {@code ROLE_ADMIN} if the
     * token's subject is an admin in the database, otherwise an empty set
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!authenticationService.isAdmin(jwt.getSubject())) {
            return Set.of();
        }
        return Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
