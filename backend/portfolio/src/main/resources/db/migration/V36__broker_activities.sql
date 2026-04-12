CREATE TABLE broker_activities (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id        BIGINT NOT NULL REFERENCES broker_connections(id) ON DELETE CASCADE,
    external_id          VARCHAR(100),
    type                 VARCHAR(50) NOT NULL,
    symbol               VARCHAR(20),
    description          TEXT,
    quantity             DECIMAL(18, 6),
    price                DECIMAL(18, 6),
    amount               DECIMAL(18, 2) NOT NULL,
    fee                  DECIMAL(18, 4),
    currency             VARCHAR(3) DEFAULT 'CAD',
    trade_date           DATE NOT NULL,
    settlement_date      DATE,
    account_name         VARCHAR(100),
    option_type          VARCHAR(20),
    raw_payload          JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_activity_external UNIQUE (connection_id, external_id)
);

CREATE INDEX idx_activities_conn_date ON broker_activities(connection_id, trade_date DESC);
CREATE INDEX idx_activities_type ON broker_activities(type);
CREATE INDEX idx_activities_symbol ON broker_activities(symbol);
