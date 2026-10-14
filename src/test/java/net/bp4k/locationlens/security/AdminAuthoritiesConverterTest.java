package net.bp4k.locationlens.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import net.bp4k.locationlens.service.AuthenticationService;

class AdminAuthoritiesConverterTest {

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final AdminAuthoritiesConverter converter =
            new AdminAuthoritiesConverter(authenticationService);

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }

    @Test
    void grantsRoleAdminWhenAuthenticationServiceSaysSubjectIsAdmin() {
        when(authenticationService.isAdmin("subject-1")).thenReturn(true);

        Collection<GrantedAuthority> authorities = converter.convert(jwtWithSubject("subject-1"));

        assertEquals(1, authorities.size());
        assertTrue(authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void grantsNoAuthoritiesWhenAuthenticationServiceSaysSubjectIsNotAdmin() {
        when(authenticationService.isAdmin("subject-1")).thenReturn(false);

        Collection<GrantedAuthority> authorities = converter.convert(jwtWithSubject("subject-1"));

        assertTrue(authorities.isEmpty());
    }
}
