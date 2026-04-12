-- Portfolio snapshots for performance tracking
CREATE TABLE portfolio_snapshots (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    snapshot_date   DATE NOT NULL,
    total_value     DECIMAL(18, 2) NOT NULL,
    positions       JSONB NOT NULL,
    cash            JSONB NOT NULL,
    accuracy        DECIMAL(5, 2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_snapshot_group_date UNIQUE (group_id, snapshot_date)
);

CREATE INDEX idx_portfolio_snapshots_group_id ON portfolio_snapshots(group_id);
CREATE INDEX idx_portfolio_snapshots_date ON portfolio_snapshots(snapshot_date);

-- Benchmark returns for comparison
CREATE TABLE benchmark_returns (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol          VARCHAR(20) NOT NULL,
    return_date     DATE NOT NULL,
    close_price     DECIMAL(18, 6) NOT NULL,
    daily_return    DECIMAL(12, 8),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_benchmark_symbol_date UNIQUE (symbol, return_date)
);

CREATE INDEX idx_benchmark_returns_symbol ON benchmark_returns(symbol);
CREATE INDEX idx_benchmark_returns_date ON benchmark_returns(return_date);

-- Portfolio cash flows for MWR calculation
CREATE TABLE portfolio_cash_flows (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES portfolio_groups(id) ON DELETE CASCADE,
    flow_date       DATE NOT NULL,
    amount          DECIMAL(18, 2) NOT NULL,
    flow_type       VARCHAR(20) NOT NULL CHECK (flow_type IN ('CONTRIBUTION', 'WITHDRAWAL', 'DIVIDEND')),
    currency        VARCHAR(3) NOT NULL DEFAULT 'CAD',
    source          VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portfolio_cash_flows_group_id ON portfolio_cash_flows(group_id);
CREATE INDEX idx_portfolio_cash_flows_date ON portfolio_cash_flows(flow_date);
