CREATE SCHEMA IF NOT EXISTS broker_gateway;

CREATE TABLE broker_gateway.connections (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         BIGINT NOT NULL,
    broker_type     VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    credentials_encrypted TEXT NOT NULL,
    accounts_json   JSONB,
    last_validated_at TIMESTAMPTZ,
    last_refreshed_at TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_broker_type CHECK (broker_type IN ('IBKR', 'QUESTRADE', 'WEALTHSIMPLE')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'ERROR', 'EXPIRED', 'DISCONNECTED'))
);

CREATE INDEX idx_gw_conn_user ON broker_gateway.connections(user_id);
CREATE INDEX idx_gw_conn_user_type ON broker_gateway.connections(user_id, broker_type);
CREATE INDEX idx_gw_conn_status ON broker_gateway.connections(status);
