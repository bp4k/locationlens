package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bp4k.locationlens.entity.User;

/**
 * JSON response body returned by {@code GET /api/v1/me}.
 *
 * <p>Built from the caller's persisted {@link User} row - see
 * {@link #from} and
 * {@link net.bp4k.locationlens.controller.AuthController#me}. {@code
 * firstName} and {@code lastName} are not stored separately; they are
 * derived from {@link User#getDisplayName()} by a <em>right split</em>
 * on the last space: everything before it is the first name,
 * everything after it is the last name, and the last name is empty if
 * there is no space at all. For example, {@code "Mary Anne Rose"}
 * splits to {@code firstName="Mary Anne"}, {@code lastName="Rose"}.
 *
 * @param firstName the caller's display name up to (not including)
 *                  the last space
 * @param lastName  the caller's display name after the last space, or
 *                  {@code ""} if the display name has no space
 * @param fullName  the caller's full display name, unsplit
 * @param email     the caller's email address
 */
public record MeResponse(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("email") String email) {

    /**
     * Builds a {@link MeResponse} from a persisted {@link User},
     * right-splitting its display name into {@link #firstName} and
     * {@link #lastName} as described in the class Javadoc.
     * @param user the authenticated caller's persisted user row
     * @return the response body
     */
    public static MeResponse from(User user) {
        String displayName = user.getDisplayName();
        int lastSpace = displayName.lastIndexOf(' ');
        String firstName = lastSpace < 0
                ? displayName
                : displayName.substring(0, lastSpace);
        String lastName = lastSpace < 0
                ? ""
                : displayName.substring(lastSpace + 1);

        return new MeResponse(firstName, lastName, displayName, user.getEmail());
    }
}
