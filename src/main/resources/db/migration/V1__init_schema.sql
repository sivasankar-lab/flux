-- ============================================================
-- Flux — PostgreSQL Schema
-- Version: V1 — Initial schema
-- ============================================================

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    user_id       VARCHAR(255)  PRIMARY KEY,
    username      VARCHAR(255)  NOT NULL UNIQUE,
    email         VARCHAR(255),
    display_name  VARCHAR(255),
    session_token VARCHAR(255)  UNIQUE,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_username  ON users (LOWER(username));
CREATE INDEX IF NOT EXISTS idx_users_session   ON users (session_token);

-- 2. Interactions
CREATE TABLE IF NOT EXISTS interactions (
    id               BIGSERIAL     PRIMARY KEY,
    user_id          VARCHAR(255)  NOT NULL,
    seed_id          VARCHAR(255),
    interaction_type VARCHAR(50)   NOT NULL,     -- VIEW, LIKE, SKIP, BOOKMARK, LONG_READ
    dwell_time_ms    BIGINT,
    category         VARCHAR(255),
    timestamp        TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    -- Embedded InteractionMetaData fields
    meta_intensity   INTEGER,
    meta_pacing      VARCHAR(50),
    meta_scroll_depth DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_interactions_user_ts   ON interactions (user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_interactions_user_cat  ON interactions (user_id, category);
CREATE INDEX IF NOT EXISTS idx_interactions_type      ON interactions (interaction_type);

-- 3. Interest Profiles (one per user)
CREATE TABLE IF NOT EXISTS interest_profiles (
    user_id                        VARCHAR(255) PRIMARY KEY,
    category_scores                TEXT DEFAULT '{}',   -- JSON: Map<String, Double>
    category_likes                 TEXT DEFAULT '{}',   -- JSON: Map<String, Integer>
    category_skips                 TEXT DEFAULT '{}',   -- JSON: Map<String, Integer>
    category_dwell_ms              TEXT DEFAULT '{}',   -- JSON: Map<String, Long>
    category_interaction_count     TEXT DEFAULT '{}',   -- JSON: Map<String, Integer>
    preferred_pacing               VARCHAR(50)  DEFAULT 'moderate',
    content_length_pref            VARCHAR(50)  DEFAULT 'medium',
    avg_session_depth              INTEGER      DEFAULT 0,
    total_interactions             INTEGER      DEFAULT 0,
    total_likes                    INTEGER      DEFAULT 0,
    total_skips                    INTEGER      DEFAULT 0,
    consecutive_skips              INTEGER      DEFAULT 0,
    last_updated                   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    interaction_count_at_last_update INTEGER    DEFAULT 0
);

-- 4. Pool Posts (shared content pool)
CREATE TABLE IF NOT EXISTS pool_posts (
    post_id                VARCHAR(255)     PRIMARY KEY,
    content                TEXT,
    category               VARCHAR(255),
    tags                   TEXT,                         -- JSON: List<String>
    source                 VARCHAR(50),                  -- SEED, GENERATED
    meta_config            TEXT,                         -- JSON: MetaConfig object
    generation_context     TEXT,                         -- JSON: GenerationContext object
    engagement_score       DOUBLE PRECISION DEFAULT 0.0,
    view_count             INTEGER          DEFAULT 0,
    like_count             INTEGER          DEFAULT 0,
    long_read_count        INTEGER          DEFAULT 0,
    skip_count             INTEGER          DEFAULT 0,
    avg_dwell_ms           BIGINT           DEFAULT 0,
    total_dwell_ms         BIGINT           DEFAULT 0,
    generated_for_interest VARCHAR(255),
    created_at             TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pool_category    ON pool_posts (category);
CREATE INDEX IF NOT EXISTS idx_pool_engagement  ON pool_posts (engagement_score);
CREATE INDEX IF NOT EXISTS idx_pool_created     ON pool_posts (created_at);

-- 5. Wall Posts (per-user feed)
CREATE TABLE IF NOT EXISTS wall_posts (
    id                 BIGSERIAL     PRIMARY KEY,
    user_id            VARCHAR(255)  NOT NULL,
    post_id            VARCHAR(255),
    content            TEXT,
    category           VARCHAR(255),
    source             VARCHAR(50),                     -- SEED, GENERATED
    meta_config        TEXT,                            -- JSON: MetaConfig object
    generation_context TEXT,                            -- JSON: GenerationContext object
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    batch              INTEGER       DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wall_user_batch ON wall_posts (user_id, batch);
