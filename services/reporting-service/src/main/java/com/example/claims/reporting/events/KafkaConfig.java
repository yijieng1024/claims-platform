package com.example.claims.reporting.events;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

/** Poison messages (design section 7): retried a few times, then moved to the dead-letter topic. */
@Configuration
class KafkaConfig {

    static final String DEAD_LETTER_TOPIC = "claims.events.DLT";

    /** Boot puts this on the listener container. Retries take 0.5 + 1 + 2 s, then the record goes to the DLT. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
        // same partition number: the DLT has the same 3 partitions, so per-claim order is kept there too
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        var backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class); // unreadable JSON won't parse on retry either
        return handler;
    }
}
