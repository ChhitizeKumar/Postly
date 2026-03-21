-- ================================================================
-- Postly ClickHouse — Analytics Schema
-- ================================================================

CREATE TABLE IF NOT EXISTS postly_analytics.events (
    event_id    UUID        DEFAULT generateUUIDv4(),
    event_type  String,
    user_id     String,
    entity_id   String,
    entity_type String,
    metadata    String,
    timestamp   DateTime64(3) DEFAULT now64()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (event_type, user_id, timestamp)
