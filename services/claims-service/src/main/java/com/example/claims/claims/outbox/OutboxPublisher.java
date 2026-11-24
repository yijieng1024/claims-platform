package com.example.claims.claims.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends PENDING outbox rows to {@code claims.events} (design section 7, "Outbox flow"). Key = claim id,
 * value = the stored JSON, header {@code eventType}.
 * <p>
 * Each row waits for Kafka's acknowledgement before the next is sent. On the first failure the batch
 * stops: that row's {@code attempts} goes up and it stays PENDING, and later rows are not sent past it,
 * so per-claim order is never broken. Rows sent earlier in the batch are committed as SENT.
 * <p>
 * A crash after Kafka acknowledged but before the commit re-sends the row: at-least-once delivery, the
 * consumer drops duplicates by event id.
 */
@Component
class OutboxPublisher {

    static final String TOPIC = "claims.events";
    private static final int BATCH_SIZE = 50;
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final Duration sendTimeout;

    OutboxPublisher(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
            @Value("${claims.outbox.publisher.enabled:true}") boolean enabled,
            @Value("${claims.outbox.publisher.send-timeout:15s}") Duration sendTimeout) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
        this.enabled = enabled;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.publisher.interval-ms:1000}")
    void tick() {
        if (enabled) {
            publishBatch();
        }
    }

    /** One batch in one transaction; the row locks are held until it commits. */
    void publishBatch() {
        tx.executeWithoutResult(status -> {
            for (OutboxEvent event : outbox.lockOldestPending(BATCH_SIZE)) {
                try {
                    send(event);
                    event.markSent(Instant.now().truncatedTo(ChronoUnit.MICROS));
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    event.failedAttempt();
                    log.warn("Outbox: sending event {} ({}) for claim {} failed (attempts: {}); stopping this batch: {}",
                            event.getId(), event.getEventType(), event.getAggregateId(), event.getAttempts(),
                            ex.toString());
                    return;
                }
            }
        });
    }

    private void send(OutboxEvent event) throws Exception {
        var record = new ProducerRecord<>(TOPIC, event.getAggregateId().toString(), event.getPayload());
        record.headers().add("eventType", event.getEventType().getBytes(UTF_8));
        kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** A log line instead of an Actuator health check: Actuator isn't in the build yet. */
    @Scheduled(initialDelay = 60_000, fixedRate = 60_000)
    void logPendingCount() {
        log.info("Outbox: {} PENDING event(s)", outbox.countByStatus("PENDING"));
    }
}
