package com.example.claims.claims;

import com.example.claims.web.UrlRules;
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
                .anyRequest().hasAnyRole("CLAIMANT", "OFFICER", "MANAGER");
    }
}
