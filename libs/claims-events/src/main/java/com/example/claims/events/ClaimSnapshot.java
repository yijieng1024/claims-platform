package com.example.claims.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The whole claim as it is after the event, so a consumer can overwrite its copy instead of replaying
 * changes. Enum-like fields are strings so this module doesn't depend on claims-service types.
 */
public record ClaimSnapshot(
        UUID id,
        String claimNumber,
        long version,
        UUID claimantId,
        String claimantName,
        UUID policyId,
        String policyNumber,
        String type,
        String market,
        String currency,
        LocalDate incidentDate,
        String incidentLocation,
        String description,
        BigDecimal claimedAmount,
        BigDecimal reserveAmount,
        BigDecimal settledAmount,
        String status,
        UUID assignedOfficerId,
        String assignedOfficerName,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {
}
