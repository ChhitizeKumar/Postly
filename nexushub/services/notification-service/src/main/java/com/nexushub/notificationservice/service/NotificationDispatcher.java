package com.nexushub.notificationservice.service;

import com.nexushub.notificationservice.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final SimpMessagingTemplate webSocketTemplate;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;

    private static final String NOTIFICATION_QUEUE_PREFIX = "/queue/notifications/";
    private static final String UNREAD_COUNT_KEY_PREFIX   = "notifications:unread:";

    /**
     * Main dispatch method - sends via all available channels
     * 1. WebSocket (instant, if user is online)
     * 2. Redis (persist for offline users)
     * 3. Email (if user preference enables it)
     */
    public void dispatch(Notification notification) {
        log.info("Dispatching {} notification to user: {}",
                 notification.getType(), notification.getRecipientId());

        // 1. Real-time WebSocket push (if connected)
        sendViaWebSocket(notification);

        // 2. Persist to Redis (available when user polls or reconnects)
        persistToRedis(notification);

        // 3. Async email (non-blocking, only for high-priority types)
        if (shouldSendEmail(notification.getType())) {
            emailService.sendNotificationEmail(notification)
                        .subscribe(
                            v -> log.debug("Email sent for notification: {}", notification.getId()),
                            e -> log.error("Email send failed: {}", e.getMessage())
                        );
        }
    }

    private void sendViaWebSocket(Notification notification) {
        try {
            String destination = NOTIFICATION_QUEUE_PREFIX + notification.getRecipientId();
            webSocketTemplate.convertAndSend(destination, notification);
            log.debug("WebSocket push sent to: {}", destination);
        } catch (Exception e) {
            // User not connected - that's fine, they'll get it from Redis on reconnect
            log.debug("WebSocket unavailable for user: {}", notification.getRecipientId());
        }
    }

    private void persistToRedis(Notification notification) {
        String listKey    = "notifications:list:" + notification.getRecipientId();
        String unreadKey  = UNREAD_COUNT_KEY_PREFIX + notification.getRecipientId();

        // Push to the front of the list (newest first), keep last 100
        redisTemplate.opsForList()
                .leftPush(listKey, notification)
                .then(redisTemplate.opsForList().trim(listKey, 0, 99))
                .then(redisTemplate.opsForValue().increment(unreadKey))
                .subscribe(
                    count -> log.debug("Notification persisted, unread count: {}", count),
                    e    -> log.error("Redis persist failed: {}", e.getMessage())
                );
    }

    private boolean shouldSendEmail(Notification.Type type) {
        return switch (type) {
            case NEW_FOLLOWER, POST_LIKED -> false;  // too frequent - no email
            case COMMENT, MENTION, AI_JOB_COMPLETE -> true;
        };
    }
}
