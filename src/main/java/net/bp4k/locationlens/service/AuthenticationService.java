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
     * @param isAdmin Whether the IDP's login just reported the caller as
     * holding the {@code locationlens_admin} realm role. The database is the
     * source of truth for admin status, so this is re-applied on every
     * login - a user who had the role revoked in Keycloak loses admin
     * status in the database the next time they log in, not only when it
     * was first granted.
     */
    @Transactional // creates one big transcation, for clean rollbacks
    public User createOrUpdateUser(String subject,
            String email,
            String givenName,
            String familyName,
            boolean isAdmin) {
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
        user.setAdmin(isAdmin);
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

    /**
     * Checks whether the user identified by {@code subject} is an admin,
     * per the database - the source of truth, kept current by
     * {@link #createOrUpdateUser} on every login. This is the one method
     * every admin-privilege check in this application should call; it
     * never consults a JWT claim directly.
     * @param subject the OIDC subject to look up, as in Keycloak; need not
     * be a valid UUID
     * @return {@code true} if {@code subject} identifies a known user
     * whose {@code admin} column is {@code true}; {@code false} for a
     * malformed/missing subject, an unknown user, or a known non-admin
     */
    public boolean isAdmin(String subject) {
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }

        return userRepository.findById(userId)
                .map(user -> Boolean.TRUE.equals(user.getAdmin()))
                .orElse(false);
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
