-- V17__sa_ingestion_steps.sql
-- Update ingestion infrastructure to support SeekingAlpha enrichment steps

-- Update step_name constraint to allow SA steps
ALTER TABLE ingestion_steps DROP CONSTRAINT IF EXISTS chk_step_name;
ALTER TABLE ingestion_steps ADD CONSTRAINT chk_step_name
    CHECK (step_name IN (
        'EODHD_UNIVERSE',
        'SA_STOCK_ENRICHMENT',
        'SA_ETF_ENRICHMENT',
        'SA_MUTUAL_FUND_ENRICHMENT'
    ));

-- Update error_type constraint to include SA-specific errors
ALTER TABLE ingestion_errors DROP CONSTRAINT IF EXISTS chk_error_type;
ALTER TABLE ingestion_errors ADD CONSTRAINT chk_error_type
    CHECK (error_type IN (
        'API_ERROR', 'PARSE_ERROR', 'DB_ERROR', 'RATE_LIMIT',
        'VALIDATION_ERROR', 'AMBIGUOUS_MATCH', 'NOT_FOUND',
        'DUPLICATE_ISIN', 'GICS_RESOLUTION', 'HOLDINGS_RESOLUTION',
        'CIRCUIT_BREAKER_OPEN', 'TIMEOUT'
    ));

-- Add SeekingAlpha data source if not exists
INSERT INTO data_sources (code, name, description, is_active) VALUES
    ('SEEKING_ALPHA', 'Seeking Alpha', 'SeekingAlpha API for enrichment data', TRUE)
ON CONFLICT (code) DO NOTHING;
