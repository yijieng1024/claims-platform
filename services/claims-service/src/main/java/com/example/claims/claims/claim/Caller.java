package com.example.claims.claims.claim;

import com.example.claims.events.ClaimEvent.Actor;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The logged-in user, from the verified JWT (design section 4). {@code market} is set for officers only. */
record Caller(UUID id, String name, String role, String email, String market) {

    static final String CLAIMANT = "CLAIMANT";
    static final String OFFICER = "OFFICER";
    static final String MANAGER = "MANAGER";

    static Caller of(Jwt jwt) {
        return new Caller(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("role"), jwt.getClaimAsString("email"), jwt.getClaimAsString("market"));
    }

    Actor actor() {
        return new Actor(id, name, role);
    }
}
