-- ================================================================
-- Postly PostgreSQL — Database + Schema Setup
-- This file runs automatically on first container start
-- ================================================================

-- Create a separate database for each service that needs Postgres
CREATE DATABASE postly_auth;
CREATE DATABASE postly_users;
CREATE DATABASE postly_ai;

-- ----------------------------------------------------------------
-- AUTH DATABASE
-- ----------------------------------------------------------------
\c postly_auth;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id               UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255),
    provider         VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',  -- LOCAL | GOOGLE | GITHUB
    provider_id      VARCHAR(255),
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER | EDITOR | ADMIN
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user  ON refresh_tokens(user_id);

-- ----------------------------------------------------------------
-- USERS DATABASE
-- ----------------------------------------------------------------
\c postly_users;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE user_profiles (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL UNIQUE,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    display_name    VARCHAR(100),
    bio             TEXT,
    avatar_url      VARCHAR(500),
    website_url     VARCHAR(500),
    followers_count INT          NOT NULL DEFAULT 0,
    following_count INT          NOT NULL DEFAULT 0,
    posts_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE follows (
    follower_id  UUID        NOT NULL,
    following_id UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id)
);

CREATE INDEX idx_profiles_username   ON user_profiles(username);
CREATE INDEX idx_follows_follower    ON follows(follower_id);
CREATE INDEX idx_follows_following   ON follows(following_id);

-- ----------------------------------------------------------------
-- AI DATABASE (pgvector for RAG)
-- ----------------------------------------------------------------
\c postly_ai;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id            UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID    NOT NULL,
    document_id   UUID    NOT NULL,
    document_name VARCHAR(255),
    chunk_index   INT     NOT NULL,
    chunk_text    TEXT    NOT NULL,
    embedding     vector(1536),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_jobs (
    id             UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID         NOT NULL,
    job_type       VARCHAR(50)  NOT NULL,   -- IMPROVE | AUTO_TAG | SUMMARIZE | RAG_QUERY
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    input_data     JSONB,
    result_data    JSONB,
    tokens_used    INT          DEFAULT 0,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ
);

CREATE INDEX idx_chunks_user       ON document_chunks(user_id);
CREATE INDEX idx_chunks_document   ON document_chunks(document_id);
CREATE INDEX idx_ai_jobs_user      ON ai_jobs(user_id);
CREATE INDEX idx_ai_jobs_status    ON ai_jobs(status);

-- Vector similarity search index (ivfflat — good for 1M+ vectors)
CREATE INDEX idx_chunks_embedding
    ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
