package com.nexushub.notificationservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexushub.notificationservice.model.Notification;
import com.nexushub.notificationservice.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationDispatcher dispatcher;

    @KafkaListener(topics = "post-events", groupId = "notification-service-group")
    public void handlePostEvents(JsonNode event) {
        String eventType = event.path("eventType").asText();
        log.info("Processing notification for event: {}", eventType);

        switch (eventType) {
            case "post.liked" -> {
                String postAuthorId = event.path("authorId").asText();
                String actorId     = event.path("actorId").asText();

                // Don't notify yourself
                if (postAuthorId.equals(actorId)) return;

                dispatcher.dispatch(Notification.builder()
                    .id(UUID.randomUUID().toString())
                    .recipientId(postAuthorId)
                    .actorId(actorId)
                    .type(Notification.Type.POST_LIKED)
                    .title("Someone liked your post")
                    .message("Your post \"" + event.path("title").asText() + "\" received a like")
                    .link("/post/" + event.path("slug").asText())
                    .build());
            }
            case "comment.created" -> {
                dispatcher.dispatch(Notification.builder()
                    .id(UUID.randomUUID().toString())
                    .recipientId(event.path("postAuthorId").asText())
                    .actorId(event.path("actorId").asText())
                    .type(Notification.Type.COMMENT)
                    .title("New comment on your post")
                    .message("Someone commented on \"" + event.path("postTitle").asText() + "\"")
                    .link("/post/" + event.path("slug").asText())
                    .build());
            }
        }
    }

    @KafkaListener(topics = "user-events", groupId = "notification-service-group")
    public void handleUserEvents(JsonNode event) {
        String eventType = event.path("eventType").asText();

        if ("user.followed".equals(eventType)) {
            dispatcher.dispatch(Notification.builder()
                .id(UUID.randomUUID().toString())
                .recipientId(event.path("followingId").asText())
                .actorId(event.path("followerId").asText())
                .type(Notification.Type.NEW_FOLLOWER)
                .title("New follower")
                .message(event.path("followerUsername").asText() + " started following you")
                .link("/u/" + event.path("followerUsername").asText())
                .build());
        }
    }

    @KafkaListener(topics = "ai-job-results", groupId = "notification-service-group")
    public void handleAiJobResults(JsonNode event) {
        String status = event.path("status").asText();

        if ("COMPLETED".equals(status)) {
            dispatcher.dispatch(Notification.builder()
                .id(UUID.randomUUID().toString())
                .recipientId(event.path("userId").asText())
                .type(Notification.Type.AI_JOB_COMPLETE)
                .title("AI task complete")
                .message("Your AI job \"" + event.path("jobType").asText() + "\" is ready")
                .link("/dashboard")
                .build());
        }
    }
}
