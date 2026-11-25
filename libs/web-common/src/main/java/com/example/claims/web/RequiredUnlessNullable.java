package com.example.claims.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Q28: in the OpenAPI spec every property is <b>required unless its field is {@code @Nullable}</b>
 * ({@code org.jspecify.annotations.Nullable}). Without this, springdoc lists only bean-validation
 * {@code @NotNull}/{@code @NotBlank} fields as required, so the generated TypeScript makes every response
 * field optional and the frontend needs {@code !} everywhere.
 * <p>
 * One rule instead of {@code @Schema(requiredMode = REQUIRED)} on ~100 fields: the few nullable fields
 * are marked, the rest is the default. springdoc's Java-nullable customizer (switched on in OpenApiAutoConfiguration) turns
 * {@code @Nullable} into a nullable schema type ({@code ["string", "null"]}); this reads that back.
 * Applies to request bodies too, so optional request fields (e.g. an assessment note) need {@code @Nullable}.
 */
class RequiredUnlessNullable implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
            Map<String, Schema> properties = schema.getProperties();
            if (properties == null) {
                continue;
            }
            List<String> required = properties.entrySet().stream()
                    .filter(e -> !isNullable(e.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            schema.setRequired(required.isEmpty() ? null : required);
        }
    }

    /** OpenAPI 3.1 writes nullable as a "null" type, either directly or as one branch of oneOf/anyOf (for $refs). */
    static boolean isNullable(Schema<?> s) {
        if (Boolean.TRUE.equals(s.getNullable()) || (s.getTypes() != null && s.getTypes().contains("null"))) {
            return true;
        }
        return branches(s).stream().filter(Objects::nonNull).anyMatch(RequiredUnlessNullable::isNullable);
    }

    private static List<Schema> branches(Schema<?> s) {
        if (s.getOneOf() != null) {
            return s.getOneOf();
        }
        return s.getAnyOf() != null ? s.getAnyOf() : List.of();
    }
}
