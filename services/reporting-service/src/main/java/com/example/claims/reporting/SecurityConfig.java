package com.example.claims.reporting;

import com.example.claims.web.UrlRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** URL rules only; the resource-server setup itself comes from libs/web-common. */
@Configuration
class SecurityConfig {

    @Bean
    UrlRules urlRules() {
        return auth -> auth
                .requestMatchers("/api/reports/exposure").hasRole("MANAGER")
                // which market an officer sees is decided in ReportController
                .requestMatchers("/api/reports/**").hasAnyRole("OFFICER", "MANAGER")
                .anyRequest().hasAnyRole("CLAIMANT", "OFFICER", "MANAGER");
    }
}
