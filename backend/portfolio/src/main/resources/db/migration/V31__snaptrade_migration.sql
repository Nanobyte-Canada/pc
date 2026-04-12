-- ============================================================================
-- SnapTrade Migration
-- Migration V31: Replace individual broker integrations with SnapTrade
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ADD SNAPTRADE COLUMNS TO USERS TABLE
-- ----------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN snaptrade_user_id VARCHAR(255);
ALTER TABLE users ADD COLUMN snaptrade_user_secret_encrypted TEXT;
CREATE UNIQUE INDEX idx_users_snaptrade_user_id ON users(snaptrade_user_id);

-- ----------------------------------------------------------------------------
-- DROP SCHEDULER-RELATED TABLES
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS user_broker_prefs CASCADE;
DROP TABLE IF EXISTS broker_oauth_states CASCADE;
DROP TABLE IF EXISTS connection_tokens CASCADE;

-- Drop the triggers for dropped tables
DROP TRIGGER IF EXISTS trg_user_broker_prefs_updated_at ON user_broker_prefs;
DROP TRIGGER IF EXISTS trg_connection_tokens_updated_at ON connection_tokens;

-- ----------------------------------------------------------------------------
-- ADD SNAPTRADE AUTHORIZATION ID TO BROKER_CONNECTIONS
-- ----------------------------------------------------------------------------
ALTER TABLE broker_connections ADD COLUMN snaptrade_authorization_id VARCHAR(255);
CREATE INDEX idx_broker_connections_snaptrade_auth ON broker_connections(snaptrade_authorization_id);

-- Drop the unique constraint that includes broker_id (SnapTrade manages multi-broker)
ALTER TABLE broker_connections DROP CONSTRAINT IF EXISTS uq_broker_connections;

-- Make broker_id nullable (connections now come from SnapTrade, not our broker table)
ALTER TABLE broker_connections ALTER COLUMN broker_id DROP NOT NULL;

-- ----------------------------------------------------------------------------
-- UPDATE POSITION FETCH LOG CONSTRAINT
-- Only MANUAL and INITIAL fetch types remain (no more SCHEDULED, RECONNECT)
-- ----------------------------------------------------------------------------
ALTER TABLE position_fetch_log DROP CONSTRAINT IF EXISTS chk_fetch_type;
ALTER TABLE position_fetch_log ADD CONSTRAINT chk_fetch_type
    CHECK (fetch_type IN ('MANUAL', 'INITIAL'));

-- ----------------------------------------------------------------------------
-- CLEAN UP BROKER SEED DATA
-- Remove individual broker entries; SnapTrade provides brokerage list dynamically
-- ----------------------------------------------------------------------------
DELETE FROM brokers WHERE code IN ('QUESTRADE', 'IBKR', 'WEALTHSIMPLE');

-- ----------------------------------------------------------------------------
-- UPDATE AUDIT LOG COMMENT
-- ----------------------------------------------------------------------------
COMMENT ON TABLE audit_log IS 'Security and compliance audit trail. Event types include:
AUTH_*, PASSWORD_*, EMAIL_*, OAUTH_*, ROLE_*, USER_*,
BROKER_CONNECT, BROKER_DISCONNECT,
BROKER_FETCH_POSITIONS, BROKER_FETCH_ERROR';
