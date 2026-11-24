package com.example.claims.web;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Each service declares one bean of this type with its own URL rules. Swagger UI, the API docs and
 * /error are already permitted by {@link WebCommonAutoConfiguration}; the rules must end with
 * {@code anyRequest()}.
 * <p>
 * Deliberately not a {@code Customizer}: Spring Security applies {@code Customizer} beans to
 * {@code HttpSecurity} on its own, which would run these rules before the shared ones.
 */
@FunctionalInterface
public interface UrlRules {

    void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth);
}
