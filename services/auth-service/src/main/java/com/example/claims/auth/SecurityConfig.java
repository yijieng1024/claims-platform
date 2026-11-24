package com.example.claims.auth;

import com.example.claims.web.UrlRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * URL rules only; the filter chain and ProblemDetail errors come from libs/web-common. Tokens are
 * verified with the local public key ({@link JwtKeyConfig#jwtDecoder}), not a JWKS fetch.
 */
@Configuration
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UrlRules urlRules() {
        return auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                // user roles only: our own SERVICE token is meant for claims-service, not this API
                .anyRequest().hasAnyRole("CLAIMANT", "OFFICER", "MANAGER");
    }
}
