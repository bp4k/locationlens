package net.bp4k.locationlens.entity;

import java.time.Instant; 
import java.util.UUID;


import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;



/**
 * Describes an OIDC user.
 * @author Advaith Menon
 */
@Entity
@Getter
@Setter
@Table(
    name="user",
    uniqueConstraints = {
        @UniqueConstraint(name="uk_user_email",
        columnNames = {"email"})
    }
)
public class User
{
    /**
     * The ID of the User, uses Keycloak's OIDC subject.
     */
    @Id
    @Setter(AccessLevel.NONE)
    private UUID userId; 

    @jakarta.persistence.Column(length = 2000)
    private String email;
    private String displayName; // concatenated given and last name
    private Boolean admin;

    protected User() {}

    public User(UUID userId, String email,
            String displayName, Boolean admin) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.admin = admin;
    }

    public User (UUID uuid, String email, String name) {
        this(uuid, email, name, false);
    }
}

