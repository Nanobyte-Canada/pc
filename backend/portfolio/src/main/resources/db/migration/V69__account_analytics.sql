-- Pre-computed analytics snapshots per broker connection
CREATE TABLE account_analytics (
    id                BIGSERIAL PRIMARY KEY,
    connection_id     BIGINT NOT NULL REFERENCES broker_connections(id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Pre-computed analytics (JSONB)
    sector_exposure   JSONB NOT NULL DEFAULT '{}',
    geography_exposure JSONB NOT NULL DEFAULT '{}',
    risk_profile      JSONB NOT NULL DEFAULT '{}',
    holdings          JSONB NOT NULL DEFAULT '[]',

    -- Scalar metrics
    mer_weighted      DECIMAL(8,4) DEFAULT 0,
    total_value       DECIMAL(18,2) DEFAULT 0,
    coverage_percent  DECIMAL(5,2) DEFAULT 0,
    positions_count   INT DEFAULT 0,

    -- Metadata
    computed_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_account_analytics_connection UNIQUE (connection_id)
);

CREATE INDEX idx_account_analytics_user ON account_analytics(user_id);
