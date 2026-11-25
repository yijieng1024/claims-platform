package com.example.claims.web;

import org.springdoc.core.customizers.JavaNullablePropertyCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Q28, two halves: springdoc's own converter writes {@code @Nullable} fields as nullable (off by default,
 * so it's switched on here once rather than by a property in every service), then
 * {@link RequiredUnlessNullable} marks every other field required.
 * <p>
 * Separate from {@link WebCommonAutoConfiguration} and only active when springdoc is: test slices like
 * {@code @WebMvcTest} don't start springdoc, and there's no spec to customise then.
 */
@AutoConfiguration(afterName = "org.springdoc.core.configuration.SpringDocConfiguration")
@ConditionalOnBean(ObjectMapperProvider.class)
public class OpenApiAutoConfiguration {

    @Bean
    JavaNullablePropertyCustomizer javaNullablePropertyCustomizer(ObjectMapperProvider objectMapperProvider) {
        return new JavaNullablePropertyCustomizer(objectMapperProvider);
    }

    @Bean
    RequiredUnlessNullable requiredUnlessNullable() {
        return new RequiredUnlessNullable();
    }
}
