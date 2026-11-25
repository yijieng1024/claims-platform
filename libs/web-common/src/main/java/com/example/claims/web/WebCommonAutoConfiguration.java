package com.example.claims.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Shared by every service (design section 12): stateless JWT resource server, {@code role} claim mapped
 * to {@code ROLE_*}, and every error rendered as ProblemDetail.
 * <p>
 * Tokens are verified by whatever {@code JwtDecoder} the service has: Boot's from
 * {@code spring.security.oauth2.resourceserver.jwt.*} (JWKS fetched on first use, {@code iss} checked
 * via issuer-uri), or auth-service's own local-key decoder.
 * <p>
 * Ordered before Boot's error and security auto-configuration so their defaults back off.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"})
public class WebCommonAutoConfiguration {

    @Bean
    ProblemDetailsAdvice problemDetailsAdvice() {
        return new ProblemDetailsAdvice();
    }

    @Bean
    ErrorPageController errorPageController() {
        return new ErrorPageController();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UrlRules urlRules,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        // hand 401/403 to ProblemDetailsAdvice so they are ProblemDetail like every other error
        AuthenticationEntryPoint unauthorized = (req, res, ex) -> resolver.resolveException(req, res, null, ex);
        AccessDeniedHandler forbidden = (req, res, ex) -> resolver.resolveException(req, res, null, ex);
        http
                .csrf(csrf -> csrf.disable()) // stateless bearer tokens, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // /actuator/health: compose healthchecks (UP/DOWN only, no details)
                    auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error", "/actuator/health").permitAll();
                    urlRules.configure(auth);
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter()))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
        return http.build();
    }

    /** {"role": "OFFICER"} becomes the authority ROLE_OFFICER. */
    private static JwtAuthenticationConverter roleClaimConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
