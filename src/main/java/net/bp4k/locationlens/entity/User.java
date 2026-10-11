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



/**
 * Describes an OIDC user.
 * @author Advaith Menon
 */
@Entity
@Getter
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
    private UUID userId; 

    @jakarta.persistence.Column(length = 2000)
    private String email;
    private String displayName; // concatenated given and last name

    protected User() {}

    public User(UUID uuid, String mail, String name) {
        userId = uuid;
        email = mail;
        displayName = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

