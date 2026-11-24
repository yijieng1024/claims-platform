package com.example.claims.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request/response bodies. Register has no role or name field on purpose: unknown JSON fields (e.g. an older
 * client's "name") are ignored, and the account name comes from the policy holder name (Q12). */
final class Dtos {

    private Dtos() {
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 30) String policyNumber) {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record UserResponse(UUID id, String name, String email, Role role, String market) {
        static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getMarket());
        }
    }

    /** expiresIn is in seconds, as in OAuth2 token responses. */
    record LoginResponse(String accessToken, long expiresIn, UserResponse user) {
    }
}
