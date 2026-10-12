package net.bp4k.locationlens.service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.bp4k.locationlens.entity.User;
import net.bp4k.locationlens.repo.UserRepository;
import net.bp4k.locationlens.exception.InvalidTokenException;

/**
 * Implements the Authenticaiton Service.
 * @author Advaith Menon
 */
@Service
public class AuthenticationService {
    private final UserRepository userRepository;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates or updates a user in the database.
     * @param subject The OIDC subject as in Keycloak
     * @param email The email of the user (assuming Keycloak takes care of
     * uniqueness)
     * @param givenName The user's first name
     * @param familyName The user's last name
     */
    @Transactional // creates one big transcation, for clean rollbacks
    public User createOrUpdateUser(String subject,
            String email,
            String givenName,
            String familyName) {
        // create the display name by concatenating given and family name
        String displayName = (
                (givenName == null ? "" : givenName)
                + " "
                + (familyName == null ? "" : familyName)).trim();


        // assuems subject is a UUID
        User user = userRepository.findById(strToUUID(subject))
            .orElseGet(() -> new User(strToUUID(subject), email,
                        displayName));

        user.setEmail(email);
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    /**
     * Get the current user.
     */
    public User requireAuthenticatedUser() {
        Authentication auth = SecurityContextHolder
            .getContext()
            .getAuthentication();
        if (auth == null
                || !(auth.getPrincipal() instanceof Jwt)) {
            throw new InvalidTokenException("User is not logged in");
        }

        // Get the JWT subject
        Jwt jwt = (Jwt) auth.getPrincipal();

        // Get the user's information from the DB
        return userRepository.findById(
                strToUUID(jwt.getSubject()))
                .orElseThrow(() -> 
                    new InvalidTokenException("token has no user"));
    }

    private static UUID strToUUID(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Tok subject is not a valid "
                    + "UUID");
        }
    }
}
