package com.example.claims.auth;

import com.example.claims.auth.Dtos.LoginResponse;
import com.example.claims.auth.Dtos.RegisterRequest;
import com.example.claims.auth.Dtos.UserResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService {

    static final String BAD_CREDENTIALS = "Invalid email or password";
    /** One message whatever failed (number, email, cancelled), so policy details can't be probed. */
    static final String POLICY_NOT_VERIFIED = "We couldn't verify these policy details.";

    private final UserRepository users;
    private final PolicyCheckClient policyCheck;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration ttl;
    /** Checked against when the email is unknown, so both login failures take the same time. */
    private final String dummyHash;

    AuthService(UserRepository users, PolicyCheckClient policyCheck, PasswordEncoder passwordEncoder,
                JwtEncoder jwtEncoder, @Value("${auth.jwt.issuer}") String issuer,
                @Value("${auth.jwt.ttl}") Duration ttl) {
        this.users = users;
        this.policyCheck = policyCheck;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.ttl = ttl;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            // BCrypt only uses the first 72 bytes and Spring Security rejects longer input
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 bytes");
        }
        String email = normalise(request.email());
        // checked before the policy call, so register can't be used to probe policies of a registered email
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        // 503 if claims-service can't answer; the account name is the policy holder name (Q12)
        String holderName = policyCheck.holderName(request.policyNumber().trim(), email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, POLICY_NOT_VERIFIED));
        var user = User.newClaimant(email, passwordEncoder.encode(request.password()), holderName);
        try {
            return UserResponse.of(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // unique constraint on email: two concurrent registrations
            throw emailTaken();
        }
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
    }

    LoginResponse login(String email, String password) {
        var user = users.findByEmail(normalise(email));
        boolean matches = passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        return new LoginResponse(issueToken(user.get()), ttl.toSeconds(), UserResponse.of(user.get()));
    }

    private String issueToken(User user) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .claim("email", user.getEmail());
        if (user.getMarket() != null) {
            claims.claim("market", user.getMarket()); // left out (reads as null) for non-officers
        }
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
