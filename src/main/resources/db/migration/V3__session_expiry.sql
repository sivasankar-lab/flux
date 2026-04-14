-- V3: Add session_created_at column for session token expiry
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_created_at TIMESTAMP WITH TIME ZONE;

-- Backfill: set existing sessions' created_at to last_login (best guess)
UPDATE users SET session_created_at = last_login WHERE session_token IS NOT NULL AND session_created_at IS NULL;
