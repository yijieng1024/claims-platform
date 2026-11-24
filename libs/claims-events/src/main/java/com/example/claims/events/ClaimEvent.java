package com.example.claims.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope of every message on {@code claims.events} (design section 7). The Kafka key is
 * {@code claim.id()}; consumers use {@code eventId} to drop duplicates and {@code claim.version()} to
 * drop out-of-order events.
 */
public record ClaimEvent(UUID eventId, ClaimEventType eventType, Instant occurredAt, Actor actor, ClaimSnapshot claim) {

    /** Who caused the event. {@code role} is the JWT role, e.g. {@code OFFICER}. */
    public record Actor(UUID id, String name, String role) {
    }
}
