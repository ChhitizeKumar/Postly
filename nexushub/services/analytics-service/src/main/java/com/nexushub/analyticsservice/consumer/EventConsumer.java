package com.nexushub.analyticsservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final JdbcTemplate clickHouseJdbc;

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO nexushub_analytics.events
            (event_type, user_id, entity_id, entity_type, metadata, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    /**
     * Consume all post events (created, liked, viewed, deleted)
     */
    @KafkaListener(
        topics = "post-events",
        groupId = "analytics-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePostEvents(
            @Payload JsonNode event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("Consuming post event from topic={} partition={} offset={}", topic, partition, offset);

        try {
            insertEvent(
                event.path("eventType").asText(),
                event.path("actorId").asText(""),
                event.path("postId").asText(""),
                "POST",
                event.toString()
            );
        } catch (Exception e) {
            log.error("Failed to insert post event into ClickHouse: {}", e.getMessage());
        }
    }

    /**
     * Consume user events (registered, updated, followed)
     */
    @KafkaListener(
        topics = "user-events",
        groupId = "analytics-service-group"
    )
    public void consumeUserEvents(@Payload JsonNode event) {
        try {
            insertEvent(
                event.path("eventType").asText(),
                event.path("userId").asText(""),
                event.path("userId").asText(""),
                "USER",
                event.toString()
            );
        } catch (Exception e) {
            log.error("Failed to insert user event: {}", e.getMessage());
        }
    }

    /**
     * Consume AI job events (completed, failed)
     */
    @KafkaListener(
        topics = "ai-job-results",
        groupId = "analytics-service-group"
    )
    public void consumeAiJobEvents(@Payload JsonNode event) {
        try {
            insertEvent(
                "ai." + event.path("status").asText("unknown").toLowerCase(),
                event.path("userId").asText(""),
                event.path("jobId").asText(""),
                "AI_JOB",
                event.toString()
            );
        } catch (Exception e) {
            log.error("Failed to insert AI job event: {}", e.getMessage());
        }
    }

    /**
     * Consume audit log events from all services
     */
    @KafkaListener(
        topics = "audit-log",
        groupId = "analytics-service-group"
    )
    public void consumeAuditEvents(@Payload JsonNode event) {
        try {
            insertEvent(
                event.path("action").asText(),
                event.path("userId").asText(""),
                event.path("entityId").asText(""),
                event.path("entityType").asText("UNKNOWN"),
                event.toString()
            );
        } catch (Exception e) {
            log.error("Failed to insert audit event: {}", e.getMessage());
        }
    }

    private void insertEvent(String eventType, String userId, String entityId,
                              String entityType, String metadata) {
        clickHouseJdbc.update(
            INSERT_EVENT_SQL,
            eventType,
            userId,
            entityId,
            entityType,
            metadata,
            Instant.now().toString()
        );
        log.debug("Inserted event type={} userId={}", eventType, userId);
    }
}
