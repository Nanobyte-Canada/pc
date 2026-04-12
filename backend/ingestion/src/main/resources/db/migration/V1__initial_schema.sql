-- Exchanges reference table
CREATE TABLE exchanges (
    id SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    country VARCHAR(100),
    currency VARCHAR(3),
    operating_mic VARCHAR(10),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Instruments (one row per instrument globally)
CREATE TABLE instruments (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    name VARCHAR(500) NOT NULL,
    instrument_type VARCHAR(20) NOT NULL,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    currency VARCHAR(3),
    country VARCHAR(3),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_instruments_isin UNIQUE (isin)
);

CREATE INDEX idx_instruments_ticker ON instruments (ticker);
CREATE INDEX idx_instruments_type ON instruments (instrument_type);
CREATE INDEX idx_instruments_status ON instruments (status);

-- Many-to-many: instruments <-> exchanges
CREATE TABLE instrument_exchanges (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    exchange_id INT NOT NULL REFERENCES exchanges(id) ON DELETE CASCADE,
    local_ticker VARCHAR(20),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_instrument_exchange UNIQUE (instrument_id, exchange_id)
);

CREATE INDEX idx_ie_instrument ON instrument_exchanges (instrument_id);
CREATE INDEX idx_ie_exchange ON instrument_exchanges (exchange_id);

-- Raw data from providers (latest only, overwritten)
CREATE TABLE provider_raw_data (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    raw_payload JSONB NOT NULL,
    payload_hash VARCHAR(64),
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_raw UNIQUE (instrument_id, provider, data_type)
);

CREATE INDEX idx_prd_instrument ON provider_raw_data (instrument_id);
CREATE INDEX idx_prd_provider_type ON provider_raw_data (provider, data_type);

-- Provider configuration and quota tracking
CREATE TABLE provider_config (
    id SERIAL PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INT NOT NULL DEFAULT 0,
    daily_quota INT,
    requests_used_today INT NOT NULL DEFAULT 0,
    last_quota_reset DATE,
    config_json JSONB
);

-- Ingestion run tracking
CREATE TABLE ingestion_runs (
    id BIGSERIAL PRIMARY KEY,
    run_type VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    trigger_source VARCHAR(100)
);

-- Ingestion step tracking
CREATE TABLE ingestion_steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES ingestion_runs(id) ON DELETE CASCADE,
    step_name VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    records_processed INT NOT NULL DEFAULT 0,
    records_created INT NOT NULL DEFAULT 0,
    records_updated INT NOT NULL DEFAULT 0,
    records_failed INT NOT NULL DEFAULT 0,
    metadata JSONB
);

CREATE INDEX idx_steps_run ON ingestion_steps (run_id);

-- Ingestion error tracking
CREATE TABLE ingestion_errors (
    id BIGSERIAL PRIMARY KEY,
    step_id BIGINT NOT NULL REFERENCES ingestion_steps(id) ON DELETE CASCADE,
    error_type VARCHAR(30) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    context JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_errors_step ON ingestion_errors (step_id);
CREATE INDEX idx_errors_type ON ingestion_errors (error_type);

-- Seed EODHD provider config
INSERT INTO provider_config (provider_name, enabled, priority, daily_quota, requests_used_today, last_quota_reset)
VALUES ('EODHD', true, 1, 100000, 0, CURRENT_DATE);
