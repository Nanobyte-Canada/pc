-- V6__ingestion_extensions.sql
-- Add ingestion tracking columns to instrument tables

-- Add ingestion tracking columns to stocks
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS exchange_code VARCHAR(20);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS source_last_seen_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS raw_eodhd_payload JSONB;

-- Add ingestion tracking columns to etfs
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS exchange_code VARCHAR(20);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS source_last_seen_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS raw_eodhd_payload JSONB;

-- Add ingestion tracking columns to mutual_funds
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS source_last_seen_at TIMESTAMPTZ;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS raw_eodhd_payload JSONB;

-- Indexes for stale data detection
CREATE INDEX IF NOT EXISTS idx_stocks_source_last_seen ON stocks(source_last_seen_at);
CREATE INDEX IF NOT EXISTS idx_etfs_source_last_seen ON etfs(source_last_seen_at);
CREATE INDEX IF NOT EXISTS idx_mutual_funds_source_last_seen ON mutual_funds(source_last_seen_at);

-- Indexes for active instruments
CREATE INDEX IF NOT EXISTS idx_stocks_is_active ON stocks(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_etfs_is_active ON etfs(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_mutual_funds_is_active ON mutual_funds(is_active) WHERE is_active = TRUE;
