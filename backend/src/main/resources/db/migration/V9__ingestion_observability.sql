-- V9__ingestion_observability.sql
-- Create tables for ingestion run tracking and error logging

-- Ingestion run tracking (one per nightly/manual execution)
CREATE TABLE ingestion_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_type VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    trigger_source VARCHAR(50),

    CONSTRAINT chk_run_type CHECK (run_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT chk_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL'))
);

-- Ingestion step tracking (one per step per run)
CREATE TABLE ingestion_steps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_name VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    records_processed INTEGER DEFAULT 0,
    records_created INTEGER DEFAULT 0,
    records_updated INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    metadata JSONB,

    CONSTRAINT fk_step_run FOREIGN KEY (run_id) REFERENCES ingestion_runs(id) ON DELETE CASCADE,
    CONSTRAINT chk_step_name CHECK (step_name IN ('EODHD_UNIVERSE', 'OPENFIGI_ENRICH', 'FMP_HOLDINGS')),
    CONSTRAINT chk_step_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

-- Ingestion errors (for retry analysis and debugging)
CREATE TABLE ingestion_errors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    step_id BIGINT NOT NULL,
    error_type VARCHAR(50) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    context JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_error_step FOREIGN KEY (step_id) REFERENCES ingestion_steps(id) ON DELETE CASCADE,
    CONSTRAINT chk_error_type CHECK (error_type IN (
        'API_ERROR', 'PARSE_ERROR', 'DB_ERROR', 'RATE_LIMIT', 'VALIDATION_ERROR', 'AMBIGUOUS_MATCH', 'NOT_FOUND'
    ))
);

-- Indexes for efficient querying
CREATE INDEX idx_ingestion_runs_status ON ingestion_runs(status);
CREATE INDEX idx_ingestion_runs_started ON ingestion_runs(started_at DESC);
CREATE INDEX idx_ingestion_runs_type ON ingestion_runs(run_type);
CREATE INDEX idx_ingestion_steps_run ON ingestion_steps(run_id);
CREATE INDEX idx_ingestion_steps_status ON ingestion_steps(status);
CREATE INDEX idx_ingestion_errors_step ON ingestion_errors(step_id);
CREATE INDEX idx_ingestion_errors_type ON ingestion_errors(error_type);
CREATE INDEX idx_ingestion_errors_created ON ingestion_errors(created_at DESC);
