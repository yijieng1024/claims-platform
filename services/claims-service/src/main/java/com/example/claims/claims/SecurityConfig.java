package com.example.claims.claims;

import com.example.claims.web.UrlRules;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** URL rules only; the resource-server setup itself comes from libs/web-common. */
@Configuration
class SecurityConfig {

    @Bean
    UrlRules urlRules() {
        return auth -> auth
                // service tokens (auth-service) may call /internal/** and nothing else (design section 4)
                .requestMatchers("/internal/**").hasRole("SERVICE")
                .requestMatchers("/api/policies/**").hasRole("CLAIMANT")
                // claims: role per endpoint here; which claim a user may see/change is checked in ClaimService
                .requestMatchers(HttpMethod.POST, "/api/claims").hasRole("CLAIMANT")
                .requestMatchers(HttpMethod.GET, "/api/claims/my").hasRole("CLAIMANT")
                .requestMatchers(HttpMethod.POST, "/api/claims/*/information-requests/*/response").hasRole("CLAIMANT")
                .requestMatchers(HttpMethod.POST, "/api/claims/*/documents").hasRole("CLAIMANT")
                .requestMatchers(HttpMethod.GET, "/api/claims/queue", "/api/claims/assigned").hasRole("OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/claims/**").hasRole("OFFICER") // all other actions
                .requestMatchers(HttpMethod.GET, "/api/claims").hasRole("MANAGER")
                .anyRequest().hasAnyRole("CLAIMANT", "OFFICER", "MANAGER");
    }
}
