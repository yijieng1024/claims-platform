package com.example.claims.claims.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of the transactional outbox (design section 7): written PENDING, sent by {@link OutboxPublisher}. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;
    private UUID aggregateId;
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;
    private String status;
    private int attempts;
    private Instant createdAt;
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(UUID eventId, UUID claimId, String eventType, String payloadJson, Instant now) {
        var e = new OutboxEvent();
        e.id = eventId;
        e.aggregateId = claimId;
        e.eventType = eventType;
        e.payload = payloadJson;
        e.status = "PENDING";
        e.createdAt = now;
        return e;
    }

    void markSent(Instant now) {
        status = "SENT";
        sentAt = now;
    }

    void failedAttempt() {
        attempts++;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
}
