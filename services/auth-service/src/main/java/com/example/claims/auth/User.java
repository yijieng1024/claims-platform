package com.example.claims.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String email;
    private String passwordHash;
    private String fullName;
    @Enumerated(EnumType.STRING)
    private Role role;
    private String market;
    private Instant createdAt;

    protected User() {
    }

    /** Self-registration always creates a claimant; staff accounts are seeded (design section 4). */
    static User newClaimant(String email, String passwordHash, String fullName) {
        var user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.role = Role.CLAIMANT;
        user.createdAt = Instant.now();
        return user;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public String getMarket() { return market; }
}
