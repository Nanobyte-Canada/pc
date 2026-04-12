-- V13__seekingalpha_enrichment_tracking.sql
-- Add SeekingAlpha enrichment tracking columns to securities tables

-- Create enrichment status enum type
DO $$ BEGIN
    CREATE TYPE sa_enrichment_status AS ENUM (
        'PENDING',           -- Not yet attempted
        'SUCCESS',           -- Enriched successfully
        'FAILED_RETRYABLE',  -- 429/5xx/timeout - will retry
        'FAILED_PERMANENT',  -- 404/400 - will not retry
        'STALE'              -- Success but outdated (> 30 days)
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- ============================================
-- STOCKS: Add SA enrichment columns
-- ============================================
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_last_attempt_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_last_success_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_error_code VARCHAR(20);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_error_message TEXT;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_retry_count INTEGER DEFAULT 0;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_raw_payload JSONB;

-- SA-specific stock fields
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_slug VARCHAR(50);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_company_name VARCHAR(255);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_followers_count INTEGER;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_equity_type VARCHAR(50);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_business_description TEXT;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_employees INTEGER;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_year_founded INTEGER;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_city VARCHAR(100);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_state VARCHAR(50);
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS sa_webpage VARCHAR(255);

-- ============================================
-- ETFS: Add SA enrichment columns
-- ============================================
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_last_attempt_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_last_success_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_error_code VARCHAR(20);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_error_message TEXT;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_retry_count INTEGER DEFAULT 0;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_raw_symbols_payload JSONB;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_raw_symbol_data_payload JSONB;

-- SA-specific ETF fields
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_slug VARCHAR(50);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_legal_name VARCHAR(255);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_long_desc TEXT;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_benchmark VARCHAR(255);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_morningstar_category VARCHAR(100);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_investment_strategy TEXT;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_fund_objective TEXT;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_is_leveraged BOOLEAN;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_is_inverse BOOLEAN;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_invest_style VARCHAR(100);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_net_assets DECIMAL(18,2);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_month_end_nav DECIMAL(18,6);
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_holdings_as_of_date DATE;
ALTER TABLE etfs ADD COLUMN IF NOT EXISTS sa_num_holdings INTEGER;

-- ============================================
-- MUTUAL_FUNDS: Add SA enrichment columns
-- ============================================
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_last_attempt_at TIMESTAMPTZ;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_last_success_at TIMESTAMPTZ;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_error_code VARCHAR(20);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_error_message TEXT;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_retry_count INTEGER DEFAULT 0;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_raw_symbols_payload JSONB;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_raw_symbol_data_payload JSONB;

-- SA-specific MF fields (same as ETF)
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_slug VARCHAR(50);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_legal_name VARCHAR(255);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_long_desc TEXT;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_benchmark VARCHAR(255);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_morningstar_category VARCHAR(100);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_investment_strategy TEXT;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_fund_objective TEXT;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_is_leveraged BOOLEAN;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_invest_style VARCHAR(100);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_net_assets DECIMAL(18,2);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_month_end_nav DECIMAL(18,6);
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_holdings_as_of_date DATE;
ALTER TABLE mutual_funds ADD COLUMN IF NOT EXISTS sa_num_holdings INTEGER;

-- ============================================
-- INDEXES for enrichment queries
-- ============================================
CREATE INDEX IF NOT EXISTS idx_stocks_sa_status_pending ON stocks(sa_enrichment_status)
    WHERE sa_enrichment_status IN ('PENDING', 'FAILED_RETRYABLE', 'STALE');
CREATE INDEX IF NOT EXISTS idx_etfs_sa_status_pending ON etfs(sa_enrichment_status)
    WHERE sa_enrichment_status IN ('PENDING', 'FAILED_RETRYABLE', 'STALE');
CREATE INDEX IF NOT EXISTS idx_mf_sa_status_pending ON mutual_funds(sa_enrichment_status)
    WHERE sa_enrichment_status IN ('PENDING', 'FAILED_RETRYABLE', 'STALE');

-- Retry backoff indexes
CREATE INDEX IF NOT EXISTS idx_stocks_sa_retry ON stocks(sa_retry_count, sa_last_attempt_at)
    WHERE sa_enrichment_status = 'FAILED_RETRYABLE';
CREATE INDEX IF NOT EXISTS idx_etfs_sa_retry ON etfs(sa_retry_count, sa_last_attempt_at)
    WHERE sa_enrichment_status = 'FAILED_RETRYABLE';
CREATE INDEX IF NOT EXISTS idx_mf_sa_retry ON mutual_funds(sa_retry_count, sa_last_attempt_at)
    WHERE sa_enrichment_status = 'FAILED_RETRYABLE';

-- Add check constraints for enrichment status
ALTER TABLE stocks ADD CONSTRAINT chk_stocks_sa_enrichment_status
    CHECK (sa_enrichment_status IN ('PENDING', 'SUCCESS', 'FAILED_RETRYABLE', 'FAILED_PERMANENT', 'STALE'));
ALTER TABLE etfs ADD CONSTRAINT chk_etfs_sa_enrichment_status
    CHECK (sa_enrichment_status IN ('PENDING', 'SUCCESS', 'FAILED_RETRYABLE', 'FAILED_PERMANENT', 'STALE'));
ALTER TABLE mutual_funds ADD CONSTRAINT chk_mf_sa_enrichment_status
    CHECK (sa_enrichment_status IN ('PENDING', 'SUCCESS', 'FAILED_RETRYABLE', 'FAILED_PERMANENT', 'STALE'));
