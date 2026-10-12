package net.bp4k.locationlens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import net.bp4k.locationlens.entity.User;
import net.bp4k.locationlens.exception.InvalidTokenException;
import net.bp4k.locationlens.repo.UserRepository;

class AuthenticationServiceTest {

    private static final UUID USER_ID = UUID.fromString("a852a1c9-1384-4571-8c53-7eeeb4975329");
    private static final String SUBJECT = USER_ID.toString();
    private static final String EMAIL = "ada@example.com";

    private UserRepository userRepository;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AuthenticationService(userRepository);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- createOrUpdateUser ------------------------------------------------

    @Test
    void createOrUpdateUserCreatesNewUserWhenSubjectIsUnknown() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        User result = service.createOrUpdateUser(SUBJECT, EMAIL, "Ada", "Lovelace");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertSame(saved, result);
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(EMAIL, saved.getEmail());
        assertEquals("Ada Lovelace", saved.getDisplayName());
    }

    @Test
    void createOrUpdateUserUpdatesExistingUserInsteadOfCreatingOne() {
        User existing = new User(USER_ID, "old@example.com", "Old Name");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        User result = service.createOrUpdateUser(SUBJECT, EMAIL, "Ada", "Lovelace");

        verify(userRepository).save(existing);
        assertSame(existing, result);
        assertEquals(USER_ID, result.getUserId());
        assertEquals(EMAIL, result.getEmail());
        assertEquals("Ada Lovelace", result.getDisplayName());
    }

    @Test
    void createOrUpdateUserTrimsDisplayNameWhenFamilyNameIsMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        User result = service.createOrUpdateUser(SUBJECT, EMAIL, "Ada", null);

        assertEquals("Ada", result.getDisplayName());
    }

    @Test
    void createOrUpdateUserTrimsDisplayNameWhenGivenNameIsMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        User result = service.createOrUpdateUser(SUBJECT, EMAIL, null, "Lovelace");

        assertEquals("Lovelace", result.getDisplayName());
    }

    @Test
    void createOrUpdateUserGivesEmptyDisplayNameWhenBothNamesMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        User result = service.createOrUpdateUser(SUBJECT, EMAIL, null, null);

        assertEquals("", result.getDisplayName());
    }

    @Test
    void createOrUpdateUserRejectsSubjectThatIsNotAUuid() {
        assertThrows(InvalidTokenException.class,
                () -> service.createOrUpdateUser("not-a-uuid", EMAIL, "Ada", "Lovelace"));

        verifyNoInteractions(userRepository);
    }

    @Test
    void createOrUpdateUserRejectsNullSubject() {
        assertThrows(InvalidTokenException.class,
                () -> service.createOrUpdateUser(null, EMAIL, "Ada", "Lovelace"));

        verify(userRepository, never()).save(any(User.class));
    }

    // ---- requireAuthenticatedUser -----------------------------------------

    @Test
    void requireAuthenticatedUserReturnsUserMatchingTokenSubject() {
        User existing = new User(USER_ID, EMAIL, "Ada Lovelace");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        authenticateWithSubject(SUBJECT);

        assertSame(existing, service.requireAuthenticatedUser());
    }

    @Test
    void requireAuthenticatedUserThrowsWhenNobodyIsAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThrows(InvalidTokenException.class, () -> service.requireAuthenticatedUser());
        verifyNoInteractions(userRepository);
    }

    @Test
    void requireAuthenticatedUserThrowsWhenPrincipalIsNotAJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "pw"));

        assertThrows(InvalidTokenException.class, () -> service.requireAuthenticatedUser());
        verifyNoInteractions(userRepository);
    }

    @Test
    void requireAuthenticatedUserThrowsWhenUserHasNeverLoggedIn() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        authenticateWithSubject(SUBJECT);

        assertThrows(InvalidTokenException.class, () -> service.requireAuthenticatedUser());
    }

    @Test
    void requireAuthenticatedUserThrowsWhenSubjectIsNotAUuid() {
        authenticateWithSubject("not-a-uuid");

        assertThrows(InvalidTokenException.class, () -> service.requireAuthenticatedUser());
        verifyNoInteractions(userRepository);
    }

    private static void authenticateWithSubject(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
