-- ============================================================
-- Flux — V2: Application Settings (encrypted API keys, toggles)
-- ============================================================

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(255) PRIMARY KEY,
    setting_value TEXT,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
