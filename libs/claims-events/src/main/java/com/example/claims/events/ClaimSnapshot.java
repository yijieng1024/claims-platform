package com.example.claims.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Every claim field reporting needs, as it is after the event, so a consumer can overwrite its copy
 * instead of replaying changes. Exactly the design section 7 list: no description, incident location or
 * claimant name (Q18, personal data stays off Kafka). Enum-like fields are strings so this module
 * doesn't depend on claims-service types.
 * <p>
 * Consumers must ignore unknown JSON fields, so fields can be added without breaking them.
 */
public record ClaimSnapshot(
        UUID id,
        String claimNumber,
        long version,
        String policyNumber,
        String type,
        String market,
        String currency,
        String status,
        UUID claimantId,
        UUID assignedOfficerId,
        String assignedOfficerName,
        BigDecimal claimedAmount,
        BigDecimal reserveAmount,
        BigDecimal settledAmount,
        Instant createdAt,
        Instant assignedAt,
        Instant closedAt) {
}
