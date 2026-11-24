package com.example.claims.reporting.events;

import static com.example.claims.reporting.TestClaim.CLAIMANT1;
import static com.example.claims.reporting.TestClaim.OFFICER_MY1;
import static com.example.claims.reporting.TestClaim.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.events.ClaimEvent;
import com.example.claims.reporting.TestClaim;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The consumer through real Kafka and Postgres. Events are produced here with the shared claims-events
 * records (the same JSON the outbox publisher sends; the publisher has its own test in claims-service).
 * <p>
 * To know that an event was handled (or skipped), each test ends with a newer "marker" event for the
 * same claim: same key, same partition, so once the marker is applied everything before it was too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext // stop the listener after this class, while the containers are still up
class ClaimEventConsumerTest {

    // Started once (Ryuk removes them at JVM exit), so they outlive the Spring context; topics are created
    // before the listener starts, with 3 partitions like in compose.
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @ServiceConnection
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka:4.4.0");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
            kafkaContainer.start();
            try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafkaContainer.getBootstrapServers()))) {
                admin.createTopics(List.of(new NewTopic(ClaimEventProjector.TOPIC, 3, (short) 1),
                        new NewTopic(KafkaConfig.DEAD_LETTER_TOPIC, 3, (short) 1))).all().get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static final Instant ASSIGNED_AT = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    MockMvc mvc;

    @Test
    void eventsUpdateTheSummaryAndNotifyTheClaimant() throws Exception {
        var claim = new TestClaim("MY", "MYR", CLAIMANT1, "1200.00");
        send(claim.submitted());
        send(claim.assigned(OFFICER_MY1, ASSIGNED_AT));

        awaitVersion(claim, 1);
        Map<String, Object> row = summary(claim);
        assertThat(row).containsEntry("status", "UNDER_REVIEW").containsEntry("claim_number", claim.number)
                .containsEntry("assigned_officer_id", OFFICER_MY1.id())
                .containsEntry("assigned_officer_name", "Demo Officer MY-1");
        assertThat(((Timestamp) row.get("assigned_at")).toInstant()).isEqualTo(ASSIGNED_AT);

        mvc.perform(get("/api/notifications").with(token(CLAIMANT1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.claimId == '%s')].message".formatted(claim.id)).value(contains(
                        "Your claim %s is now being reviewed by an officer.".formatted(claim.number))));
    }

    @Test
    void theSameEventTwiceGivesOneRowAndOneNotification() {
        var claim = new TestClaim("MY", "MYR", CLAIMANT1, "800.00");
        ClaimEvent assigned;
        send(claim.submitted());
        send(assigned = claim.assigned(OFFICER_MY1, ASSIGNED_AT));
        send(assigned); // redelivery
        send(claim.assessed("500.00")); // marker

        awaitVersion(claim, 2);
        assertThat(count("SELECT count(*) FROM claim_summary WHERE claim_id = ?", claim.id)).isOne();
        assertThat(count("SELECT count(*) FROM notifications WHERE claim_id = ?", claim.id)).isOne();
    }

    @Test
    void anOlderVersionAfterANewerOneIsIgnored() {
        var claim = new TestClaim("MY", "MYR", CLAIMANT1, "900.00");
        ClaimEvent submitted = claim.submitted();
        ClaimEvent assigned = claim.assigned(OFFICER_MY1, ASSIGNED_AT);  // v1
        ClaimEvent assessed = claim.assessed("700.00");                  // v2
        send(submitted);
        send(assessed);
        send(assigned); // arrives late
        send(claim.assessed("750.00")); // marker, v3

        awaitVersion(claim, 3);
        assertThat(summary(claim)).containsEntry("status", "ASSESSED");
        // the late ClaimAssigned changed nothing and notified no one, but its id is recorded as handled
        assertThat(count("SELECT count(*) FROM notifications WHERE claim_id = ?", claim.id)).isZero();
        assertThat(count("SELECT count(*) FROM processed_events WHERE event_id = ?", assigned.eventId())).isOne();
    }

    @Test
    void anOutboxRowWithTheOlderBiggerSnapshotIsStillConsumed() {
        var claim = new TestClaim("MY", "MYR", CLAIMANT1, "100.00");
        // fields the snapshot had before Q18; reporting must ignore them
        var tree = (ObjectNode) json.valueToTree(claim.submitted());
        ((ObjectNode) tree.get("claim")).put("claimantName", "Demo Claimant 1").put("description", "Rear-ended")
                .put("incidentLocation", "Jalan Contoh, KL").put("incidentDate", "2026-10-30")
                .put("policyId", UUID.randomUUID().toString()).putNull("rejectionReason")
                .put("updatedAt", "2026-11-01T09:00:00Z");
        sendRaw(claim.id, json.writeValueAsString(tree));

        awaitVersion(claim, 0);
    }

    @Test
    void anEventThatAlwaysFailsGoesToTheDeadLetterTopicAndTheNextOneIsProcessed() throws Exception {
        var claim = new TestClaim("MY", "MYR", CLAIMANT1, "300.00");
        // valid JSON, but claim_number is NOT NULL in claim_summary: fails on every retry
        ClaimEvent submitted = claim.submitted();
        var bad = (ObjectNode) json.valueToTree(submitted);
        ((ObjectNode) bad.get("claim")).putNull("claimNumber");
        sendRaw(claim.id, json.writeValueAsString(bad));
        send(claim.assigned(OFFICER_MY1, ASSIGNED_AT)); // same key, so the same partition: not blocked

        awaitVersion(claim, 1);
        assertThat(count("SELECT count(*) FROM processed_events WHERE event_id = ?", submitted.eventId())).isZero();

        var dead = new AtomicReference<String>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            var partitions = List.of(0, 1, 2).stream()
                    .map(p -> new TopicPartition(KafkaConfig.DEAD_LETTER_TOPIC, p)).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                for (var r : consumer.poll(Duration.ofMillis(200))) {
                    if (claim.id.toString().equals(r.key())) {
                        dead.set(r.value());
                    }
                }
                return dead.get() != null;
            });
        }
        assertThat(json.readTree(dead.get()).get("eventId").asString()).isEqualTo(submitted.eventId().toString());
    }

    // ---- helpers ----

    private void send(ClaimEvent event) {
        sendRaw(event.claim().id(), json.writeValueAsString(event));
    }

    private void sendRaw(UUID claimId, String payload) {
        try {
            kafka.send(ClaimEventProjector.TOPIC, claimId.toString(), payload).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void awaitVersion(TestClaim claim, long version) {
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            Map<String, Object> row = summary(claim);
            return row != null && ((Long) row.get("claim_version")) == version;
        });
    }

    private Map<String, Object> summary(TestClaim claim) {
        return jdbc.sql("SELECT * FROM claim_summary WHERE claim_id = ?").param(claim.id).query().listOfRows()
                .stream().findFirst().orElse(null);
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param(id).query(Long.class).single();
    }
}
