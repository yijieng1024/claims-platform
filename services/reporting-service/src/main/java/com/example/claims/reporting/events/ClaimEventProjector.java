package com.example.claims.reporting.events;

import com.example.claims.events.ClaimEvent;
import com.example.claims.events.ClaimSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies {@code claims.events} to the read model (design section 7, "Consumer"), one event per DB
 * transaction: duplicate event ids are skipped entirely; otherwise the summary row is overwritten with
 * the snapshot if it is newer than the stored version, and a notification is added where the event
 * needs one, whatever its version (Q22).
 * <p>
 * The listener commits the Kafka offset only after this method returns, i.e. after the DB commit
 * ({@code ack-mode: record}). A crash in between re-delivers the event, and the event id check drops it.
 * Failures are retried, then sent to the dead-letter topic ({@link KafkaConfig}).
 */
@Component
@Transactional
public class ClaimEventProjector {

    static final String TOPIC = "claims.events";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ClaimEventProjector(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = TOPIC, groupId = "reporting-service")
    void onMessage(String payload) {
        apply(json.readValue(payload, ClaimEvent.class));
    }

    public void apply(ClaimEvent event) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        // Recording the id first is the race-free form of "already processed?"; it only commits together
        // with the changes below, and is kept even when the version check skips the event.
        int firstTime = jdbc.sql("INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(event.eventId(), now).update();
        if (firstTime == 0) {
            return;
        }
        ClaimSnapshot c = event.claim();
        // the version check only guards the summary: an older event doesn't overwrite it
        jdbc.sql("""
                        INSERT INTO claim_summary (claim_id, claim_number, type, market, currency, status, claimant_id,
                            assigned_officer_id, assigned_officer_name, claimed_amount, reserve_amount, settled_amount,
                            submitted_at, assigned_at, closed_at, claim_version, last_event_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (claim_id) DO UPDATE SET
                            claim_number = EXCLUDED.claim_number, type = EXCLUDED.type, market = EXCLUDED.market,
                            currency = EXCLUDED.currency, status = EXCLUDED.status, claimant_id = EXCLUDED.claimant_id,
                            assigned_officer_id = EXCLUDED.assigned_officer_id,
                            assigned_officer_name = EXCLUDED.assigned_officer_name,
                            claimed_amount = EXCLUDED.claimed_amount, reserve_amount = EXCLUDED.reserve_amount,
                            settled_amount = EXCLUDED.settled_amount, submitted_at = EXCLUDED.submitted_at,
                            assigned_at = EXCLUDED.assigned_at, closed_at = EXCLUDED.closed_at,
                            claim_version = EXCLUDED.claim_version, last_event_at = EXCLUDED.last_event_at,
                            updated_at = EXCLUDED.updated_at
                        WHERE claim_summary.claim_version < EXCLUDED.claim_version""")
                .params(c.id(), c.claimNumber(), c.type(), c.market(), c.currency(), c.status(), c.claimantId(),
                        c.assignedOfficerId(), c.assignedOfficerName(), c.claimedAmount(), c.reserveAmount(),
                        c.settledAmount(), utc(c.createdAt()), utc(c.assignedAt()), utc(c.closedAt()), c.version(),
                        utc(event.occurredAt()), now)
                .update();
        // ...but notifications are per event (Q22), so one arriving out of order still notifies
        notifyFor(event, now);
    }

    private void notifyFor(ClaimEvent event, OffsetDateTime now) {
        ClaimSnapshot c = event.claim();
        String number = c.claimNumber();
        Notification n = switch (event.eventType()) {
            case ClaimAssigned -> new Notification(c.claimantId(), "CLAIM_ASSIGNED",
                    "Your claim %s is now being reviewed by an officer.".formatted(number));
            case InformationRequested -> new Notification(c.claimantId(), "INFO_REQUESTED",
                    "More information is needed for claim %s.".formatted(number));
            case InformationProvided -> new Notification(c.assignedOfficerId(), "INFO_PROVIDED",
                    "The claimant has responded on claim %s.".formatted(number));
            case ClaimSettled -> new Notification(c.claimantId(), "CLAIM_SETTLED",
                    "Claim %s has been settled: %s %s".formatted(number, c.currency(), money(c.settledAmount())));
            case ClaimRejected -> new Notification(c.claimantId(), "CLAIM_REJECTED",
                    "Claim %s has been rejected.".formatted(number));
            case ClaimSubmitted, ClaimAssessed -> null;
        };
        if (n != null) {
            jdbc.sql("""
                            INSERT INTO notifications (id, user_id, claim_id, claim_number, type, message, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)""")
                    .params(UUID.randomUUID(), n.userId(), c.id(), number, n.type(), n.message(), now).update();
        }
    }

    private record Notification(UUID userId, String type, String message) {
    }

    /** 3800 becomes "3,800.00". */
    private static String money(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,.2f", amount);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
