package com.example.claims.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ClaimEventJsonTest {

    final JsonMapper json = JsonMapper.builder().build();

    @Test
    void envelopeHasTheDesignShapeAndRoundTrips() {
        var now = Instant.parse("2026-11-24T10:15:00Z");
        var claim = new ClaimSnapshot(UUID.randomUUID(), "CLM-2026-000001", 3, UUID.randomUUID(), "Demo Claimant 1",
                UUID.randomUUID(), "POL-MY-MTR-0001", "MOTOR", "MY", "MYR", LocalDate.parse("2026-11-01"), "KL",
                "Rear-ended", new BigDecimal("1200.50"), new BigDecimal("1000.00"), null, "ASSESSED",
                UUID.randomUUID(), "Officer Lee", null, now, now, null);
        var event = new ClaimEvent(UUID.randomUUID(), ClaimEventType.ClaimAssessed, now,
                new ClaimEvent.Actor(UUID.randomUUID(), "Officer Lee", "OFFICER"), claim);

        String text = json.writeValueAsString(event);
        JsonNode tree = json.readTree(text);

        assertThat(tree.propertyNames()).containsExactly("eventId", "eventType", "occurredAt", "actor", "claim");
        assertThat(tree.get("eventType").asString()).isEqualTo("ClaimAssessed");
        assertThat(tree.get("occurredAt").asString()).isEqualTo("2026-11-24T10:15:00Z");
        assertThat(tree.get("actor").propertyNames()).containsExactly("id", "name", "role");
        assertThat(tree.at("/claim/version").asLong()).isEqualTo(3);
        assertThat(tree.at("/claim/incidentDate").asString()).isEqualTo("2026-11-01");
        assertThat(json.readValue(text, ClaimEvent.class)).isEqualTo(event);
    }
}
