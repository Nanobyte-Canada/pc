CREATE TABLE broker_balance_snapshots (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id   BIGINT NOT NULL REFERENCES broker_connections(id) ON DELETE CASCADE,
    total_value     DECIMAL(18, 2),
    cash            JSONB,
    currency        VARCHAR(3) DEFAULT 'CAD',
    as_of_date      DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_balance_conn_date UNIQUE (connection_id, as_of_date)
);

CREATE INDEX idx_balance_conn_date ON broker_balance_snapshots(connection_id, as_of_date DESC);

ALTER TABLE broker_connections
    ADD COLUMN last_activities_fetched_at TIMESTAMPTZ,
    ADD COLUMN last_balance_fetched_at TIMESTAMPTZ;
