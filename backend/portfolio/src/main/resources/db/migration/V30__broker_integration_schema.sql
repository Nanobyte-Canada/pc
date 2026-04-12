-- ============================================================================
-- Broker Integration Schema
-- Migration V30: Brokers, connections, positions, and fetch logging
-- ============================================================================

-- ----------------------------------------------------------------------------
-- BROKERS REFERENCE TABLE
-- ----------------------------------------------------------------------------
CREATE TABLE brokers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    logo_url VARCHAR(500),
    description VARCHAR(500),
    oauth_config JSONB,
    rate_limit_config JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_brokers_code UNIQUE (code),
    CONSTRAINT chk_brokers_auth_type CHECK (auth_type IN ('OAUTH2', 'API_KEY', 'AGGREGATOR')),
    CONSTRAINT chk_brokers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'))
);

COMMENT ON TABLE brokers IS 'Supported brokerage providers';

-- Seed broker data
INSERT INTO brokers (code, name, auth_type, status, description, oauth_config, rate_limit_config) VALUES
    ('QUESTRADE', 'Questrade', 'OAUTH2', 'ACTIVE',
     'Connect your Questrade account to view positions',
     '{
        "authorizationUrl": "https://login.questrade.com/oauth2/authorize",
        "tokenUrl": "https://login.questrade.com/oauth2/token",
        "scopes": ["read_account"],
        "refreshTokenSingleUse": true,
        "note": "Questrade refresh tokens are single-use; new token returned on each refresh"
     }'::jsonb,
     '{"requestsPerSecond": 1, "burstSize": 5}'::jsonb),
    ('IBKR', 'Interactive Brokers', 'OAUTH2', 'ACTIVE',
     'Connect your Interactive Brokers account',
     '{
        "authorizationUrl": "https://www.interactivebrokers.com/oauth2/authorize",
        "tokenUrl": "https://www.interactivebrokers.com/oauth2/token",
        "scopes": ["portfolio"],
        "authMethod": "private_key_jwt",
        "note": "IBKR uses JWT client assertion for token requests"
     }'::jsonb,
     '{"requestsPerSecond": 2, "burstSize": 10}'::jsonb),
    ('WEALTHSIMPLE', 'Wealthsimple', 'AGGREGATOR', 'ACTIVE',
     'Connect via SnapTrade aggregator',
     '{
        "aggregator": "SNAPTRADE",
        "note": "No official API - uses SnapTrade aggregator service"
     }'::jsonb,
     '{"requestsPerSecond": 1, "burstSize": 3}'::jsonb);

-- ----------------------------------------------------------------------------
-- USER BROKER PREFERENCES
-- ----------------------------------------------------------------------------
CREATE TABLE user_broker_prefs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    auto_fetch_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fetch_time_utc TIME DEFAULT '06:00:00',
    notification_on_fetch BOOLEAN NOT NULL DEFAULT FALSE,
    notification_on_error BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_broker_prefs_user UNIQUE (user_id),
    CONSTRAINT fk_user_broker_prefs_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_broker_prefs_auto_fetch
    ON user_broker_prefs(auto_fetch_enabled) WHERE auto_fetch_enabled = TRUE;

COMMENT ON TABLE user_broker_prefs IS 'Per-user settings for broker position fetching';

-- ----------------------------------------------------------------------------
-- BROKER CONNECTIONS (User linked broker accounts)
-- ----------------------------------------------------------------------------
CREATE TABLE broker_connections (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    broker_id BIGINT NOT NULL,
    account_id_external VARCHAR(100),
    account_number VARCHAR(50),
    account_type VARCHAR(50),
    account_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_positions_fetched_at TIMESTAMPTZ,
    positions_count INT DEFAULT 0,
    total_value DECIMAL(18, 2),
    connection_error_code VARCHAR(50),
    connection_error_message VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_broker_connections UNIQUE (user_id, broker_id, account_id_external),
    CONSTRAINT fk_broker_connections_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_broker_connections_broker FOREIGN KEY (broker_id)
        REFERENCES brokers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_broker_connections_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'ERROR', 'DISCONNECTED'))
);

CREATE INDEX idx_broker_connections_user ON broker_connections(user_id);
CREATE INDEX idx_broker_connections_broker ON broker_connections(broker_id);
CREATE INDEX idx_broker_connections_status ON broker_connections(status);
CREATE INDEX idx_broker_connections_user_active
    ON broker_connections(user_id, status) WHERE status = 'ACTIVE';

COMMENT ON TABLE broker_connections IS 'User connections to brokerage accounts';

-- ----------------------------------------------------------------------------
-- CONNECTION TOKENS (Encrypted OAuth/API tokens)
-- ----------------------------------------------------------------------------
CREATE TABLE connection_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT,
    token_type VARCHAR(50) DEFAULT 'Bearer',
    scope VARCHAR(500),
    api_server_url VARCHAR(255),
    expires_at TIMESTAMPTZ,
    last_refreshed_at TIMESTAMPTZ,
    refresh_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_connection_tokens UNIQUE (connection_id),
    CONSTRAINT fk_connection_tokens_conn FOREIGN KEY (connection_id)
        REFERENCES broker_connections(id) ON DELETE CASCADE
);

CREATE INDEX idx_connection_tokens_expires
    ON connection_tokens(expires_at) WHERE expires_at IS NOT NULL;

COMMENT ON TABLE connection_tokens IS 'Encrypted OAuth tokens for broker connections';
COMMENT ON COLUMN connection_tokens.access_token_encrypted IS 'AES-256-GCM encrypted access token';
COMMENT ON COLUMN connection_tokens.api_server_url IS 'Questrade: dynamic API server returned with token';

-- ----------------------------------------------------------------------------
-- BROKER POSITIONS (User holdings from brokers)
-- ----------------------------------------------------------------------------
CREATE TABLE broker_positions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    symbol_id_external VARCHAR(50),
    instrument_id BIGINT,
    instrument_type VARCHAR(20),
    security_name VARCHAR(255),
    quantity DECIMAL(18, 6) NOT NULL,
    average_cost DECIMAL(18, 6),
    current_price DECIMAL(18, 6),
    current_value DECIMAL(18, 2),
    day_pnl DECIMAL(18, 2),
    total_pnl DECIMAL(18, 2),
    total_pnl_percent DECIMAL(10, 4),
    currency VARCHAR(3) DEFAULT 'CAD',
    as_of_date DATE NOT NULL,
    as_of_timestamp TIMESTAMPTZ,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_broker_positions_conn FOREIGN KEY (connection_id)
        REFERENCES broker_connections(id) ON DELETE CASCADE,
    CONSTRAINT fk_broker_positions_stock FOREIGN KEY (instrument_id)
        REFERENCES stocks(id) ON DELETE SET NULL,
    CONSTRAINT chk_broker_positions_type
        CHECK (instrument_type IS NULL OR instrument_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'OPTION', 'BOND', 'CASH', 'OTHER'))
);

CREATE INDEX idx_broker_positions_conn ON broker_positions(connection_id);
CREATE INDEX idx_broker_positions_current
    ON broker_positions(connection_id, is_current) WHERE is_current = TRUE;
CREATE INDEX idx_broker_positions_date ON broker_positions(as_of_date);
CREATE INDEX idx_broker_positions_symbol ON broker_positions(symbol);
CREATE INDEX idx_broker_positions_instrument ON broker_positions(instrument_id) WHERE instrument_id IS NOT NULL;

COMMENT ON TABLE broker_positions IS 'Positions fetched from broker accounts';
COMMENT ON COLUMN broker_positions.is_current IS 'TRUE for latest fetch, FALSE for historical';
COMMENT ON COLUMN broker_positions.raw_payload IS 'Original broker response for this position';

-- ----------------------------------------------------------------------------
-- POSITION FETCH LOG (Audit trail for fetch operations)
-- ----------------------------------------------------------------------------
CREATE TABLE position_fetch_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    fetch_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    duration_ms INT,
    positions_count INT,
    total_value DECIMAL(18, 2),
    error_code VARCHAR(50),
    error_message TEXT,
    raw_response JSONB,
    retry_count INT DEFAULT 0,
    triggered_by VARCHAR(50),

    CONSTRAINT fk_position_fetch_log_conn FOREIGN KEY (connection_id)
        REFERENCES broker_connections(id) ON DELETE CASCADE,
    CONSTRAINT fk_position_fetch_log_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_fetch_type CHECK (fetch_type IN ('MANUAL', 'SCHEDULED', 'INITIAL', 'RECONNECT')),
    CONSTRAINT chk_fetch_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED', 'PARTIAL', 'CANCELLED'))
);

CREATE INDEX idx_position_fetch_log_conn ON position_fetch_log(connection_id);
CREATE INDEX idx_position_fetch_log_user ON position_fetch_log(user_id);
CREATE INDEX idx_position_fetch_log_status ON position_fetch_log(status);
CREATE INDEX idx_position_fetch_log_started ON position_fetch_log(started_at);
CREATE INDEX idx_position_fetch_log_type_status ON position_fetch_log(fetch_type, status);

COMMENT ON TABLE position_fetch_log IS 'Audit log for position fetch operations';

-- ----------------------------------------------------------------------------
-- BROKER OAUTH STATES (CSRF protection for broker OAuth flows)
-- ----------------------------------------------------------------------------
CREATE TABLE broker_oauth_states (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    state_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    broker_id BIGINT NOT NULL,
    redirect_uri VARCHAR(500),
    code_verifier VARCHAR(128),
    nonce VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_broker_oauth_states_hash UNIQUE (state_hash),
    CONSTRAINT fk_broker_oauth_states_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_broker_oauth_states_broker FOREIGN KEY (broker_id)
        REFERENCES brokers(id) ON DELETE CASCADE
);

CREATE INDEX idx_broker_oauth_states_hash ON broker_oauth_states(state_hash);
CREATE INDEX idx_broker_oauth_states_expires ON broker_oauth_states(expires_at);
CREATE INDEX idx_broker_oauth_states_user ON broker_oauth_states(user_id);

COMMENT ON TABLE broker_oauth_states IS 'OAuth2 state tokens for broker connection CSRF protection';
COMMENT ON COLUMN broker_oauth_states.code_verifier IS 'PKCE code verifier for enhanced security';

-- ----------------------------------------------------------------------------
-- AGGREGATED POSITIONS VIEW (for portfolio analysis)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_aggregated_positions AS
SELECT
    bp.symbol,
    bp.security_name,
    bp.instrument_type,
    bp.currency,
    SUM(bp.quantity) as total_quantity,
    SUM(bp.current_value) as total_value,
    AVG(bp.average_cost) as weighted_avg_cost,
    SUM(bp.total_pnl) as total_pnl,
    CASE
        WHEN SUM(bp.current_value - COALESCE(bp.total_pnl, 0)) > 0
        THEN (SUM(bp.total_pnl) / SUM(bp.current_value - COALESCE(bp.total_pnl, 0))) * 100
        ELSE 0
    END as total_pnl_percent,
    COUNT(DISTINCT bc.id) as account_count,
    COUNT(DISTINCT bc.broker_id) as broker_count,
    bc.user_id
FROM broker_positions bp
JOIN broker_connections bc ON bp.connection_id = bc.id
WHERE bp.is_current = TRUE
  AND bc.status = 'ACTIVE'
GROUP BY bp.symbol, bp.security_name, bp.instrument_type, bp.currency, bc.user_id;

COMMENT ON VIEW v_aggregated_positions IS 'Aggregated positions across all brokers for a user';

-- ----------------------------------------------------------------------------
-- TRIGGERS FOR UPDATED_AT
-- ----------------------------------------------------------------------------
CREATE TRIGGER trg_brokers_updated_at
    BEFORE UPDATE ON brokers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_user_broker_prefs_updated_at
    BEFORE UPDATE ON user_broker_prefs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_broker_connections_updated_at
    BEFORE UPDATE ON broker_connections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_connection_tokens_updated_at
    BEFORE UPDATE ON connection_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- ADD BROKER AUDIT EVENT TYPES
-- ----------------------------------------------------------------------------
COMMENT ON TABLE audit_log IS 'Security and compliance audit trail. Event types include:
AUTH_*, PASSWORD_*, EMAIL_*, OAUTH_*, ROLE_*, USER_*,
BROKER_CONNECT, BROKER_DISCONNECT, BROKER_RECONNECT,
BROKER_TOKEN_REFRESH, BROKER_TOKEN_REVOKE,
BROKER_FETCH_POSITIONS, BROKER_FETCH_ERROR, BROKER_FETCH_SCHEDULED,
BROKER_PREFS_UPDATE';
