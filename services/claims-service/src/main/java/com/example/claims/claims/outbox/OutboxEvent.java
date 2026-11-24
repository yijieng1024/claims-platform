package com.example.claims.claims.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of the transactional outbox (design section 7): written PENDING, sent to Kafka later (brief 4). */
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
}
