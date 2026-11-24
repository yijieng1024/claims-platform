package com.example.claims.claims.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The scheduled publisher against real Postgres and Kafka; Kafka "goes down" by pausing its container. */
@SpringBootTest(properties = {
        "claims.outbox.publisher.enabled=true",
        "claims.outbox.publisher.interval-ms=200",
        "claims.outbox.publisher.send-timeout=3s",
        "spring.kafka.producer.properties.max.block.ms=1000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=2000"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext // stop the scheduler and producer after this class, while the containers are still up
class OutboxPublisherTest {

    // Started once, not via @Container: JUnit would stop them before Spring closes the context, and the
    // still-running scheduler would then wait 30 s for a DB connection. Ryuk removes them at JVM exit.
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.4.0");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
            kafka.start();
        }
    }

    @Autowired
    JdbcClient jdbc;

    @Test
    void rowsStayPendingWhileKafkaIsDownThenGoOutInOrder() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(OutboxPublisher.TOPIC, 1, (short) 1))).all().get();
        }
        String claimId = UUID.randomUUID().toString();
        List<String> types = List.of("ClaimSubmitted", "ClaimAssigned", "ClaimAssessed");

        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            for (int i = 0; i < types.size(); i++) {
                jdbc.sql("""
                                INSERT INTO outbox_events (id, aggregate_id, event_type, payload, status, created_at)
                                VALUES (gen_random_uuid(), ?::uuid, ?, ?::jsonb, 'PENDING', now() + make_interval(secs => ?))""")
                        .params(claimId, types.get(i), "{\"n\":" + i + "}", i).update();
            }
            // the first row keeps failing; the batch stops there, so the later rows are never tried
            await().atMost(Duration.ofSeconds(30)).until(() -> attempts().get(0) > 0);
            assertThat(statuses()).containsOnly("PENDING");
            assertThat(attempts().subList(1, 3)).containsOnly(0);
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        await().atMost(Duration.ofSeconds(60)).until(() -> statuses().stream().allMatch("SENT"::equals));
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE sent_at IS NULL").query(Long.class).single())
                .isZero();

        List<String> received = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            var partition = new TopicPartition(OutboxPublisher.TOPIC, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                for (var r : consumer.poll(Duration.ofMillis(200))) {
                    assertThat(r.key()).isEqualTo(claimId);
                    received.add(new String(r.headers().lastHeader("eventType").value(), UTF_8) + " " + r.value());
                }
                return received.stream().distinct().count() >= 3;
            });
        }
        // in order. distinct(): a request the paused broker had buffered may still land after the producer
        // gave up on it, so a duplicate of the first event is allowed (at-least-once; consumers dedupe)
        assertThat(received.stream().distinct()).containsExactly(
                "ClaimSubmitted {\"n\": 0}", "ClaimAssigned {\"n\": 1}", "ClaimAssessed {\"n\": 2}");
    }

    private List<String> statuses() {
        return jdbc.sql("SELECT status FROM outbox_events ORDER BY created_at").query(String.class).list();
    }

    private List<Integer> attempts() {
        return jdbc.sql("SELECT attempts FROM outbox_events ORDER BY created_at").query(Integer.class).list();
    }
}
