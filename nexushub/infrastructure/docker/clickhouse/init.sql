-- ============================================================
-- NexusHub ClickHouse Analytics Schema
-- ============================================================

-- All events from Kafka land here
CREATE TABLE IF NOT EXISTS nexushub_analytics.events (
    event_id     UUID DEFAULT generateUUIDv4(),
    event_type   String,          -- user.registered, post.created, post.liked, etc.
    user_id      String,
    entity_id    String,          -- post_id, comment_id, etc.
    entity_type  String,          -- POST, COMMENT, USER
    metadata     String,          -- JSON blob
    ip_address   String,
    user_agent   String,
    timestamp    DateTime64(3) DEFAULT now64()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (event_type, user_id, timestamp)
TTL timestamp + INTERVAL 1 YEAR;

-- Materialized view: daily active users
CREATE MATERIALIZED VIEW IF NOT EXISTS nexushub_analytics.daily_active_users
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY date AS
SELECT
    toDate(timestamp) AS date,
    uniqState(user_id) AS unique_users
FROM nexushub_analytics.events
WHERE user_id != ''
GROUP BY date;

-- Materialized view: post stats
CREATE MATERIALIZED VIEW IF NOT EXISTS nexushub_analytics.post_stats
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, entity_id) AS
SELECT
    toDate(timestamp) AS date,
    entity_id AS post_id,
    countIfState(event_type = 'post.viewed') AS views,
    countIfState(event_type = 'post.liked') AS likes,
    countIfState(event_type = 'comment.created') AS comments
FROM nexushub_analytics.events
WHERE entity_type = 'POST'
GROUP BY date, entity_id;

-- Materialized view: AI usage stats
CREATE MATERIALIZED VIEW IF NOT EXISTS nexushub_analytics.ai_usage_stats
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, job_type) AS
SELECT
    toDate(timestamp) AS date,
    JSONExtractString(metadata, 'job_type') AS job_type,
    countState() AS total_requests,
    sumState(toInt64OrDefault(JSONExtractString(metadata, 'tokens_used'), 0)) AS total_tokens
FROM nexushub_analytics.events
WHERE event_type LIKE 'ai.%'
GROUP BY date, job_type;

-- Hourly metrics table for dashboards
CREATE TABLE IF NOT EXISTS nexushub_analytics.hourly_metrics (
    hour         DateTime,
    metric_name  String,
    metric_value Float64,
    dimensions   String    -- JSON
) ENGINE = MergeTree()
ORDER BY (metric_name, hour)
TTL hour + INTERVAL 90 DAY;
